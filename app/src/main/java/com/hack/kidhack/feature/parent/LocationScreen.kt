package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.view.MotionEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationEventType
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.LocationEventRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class LocationUiState(
    val history: List<LocationPoint> = emptyList(),
    /** 지도에 반경 원으로 겹쳐 그릴 등록 장소들. */
    val places: List<Geofence> = emptyList(),
    /** 등록 장소 진입/이탈 기록. 화면 맨 아래 "최근 출입 기록"에 쓴다. */
    val events: List<LocationEvent> = emptyList(),
    val realtimeEnabled: Boolean = false,
    /** "오늘 이동 경로 확인" 토글. 켜면 지도에 동선을 선으로 잇는다. */
    val showRoute: Boolean = false,
    val hasChildren: Boolean = true,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 동선은 최신순이라 첫 항목이 현재 위치다(실시간 ON이면 방금 좌표). */
    val current: LocationPoint? get() = history.firstOrNull()
}

class LocationViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val locationEventRepository: LocationEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    /** 실시간 토글에 쓸 현재 선택된 자녀. refresh()에서만 갱신한다. */
    private var childId: String? = null

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                childId = id
                if (id == null) {
                    _state.update { it.copy(loading = false, hasChildren = false) }
                    return@runCatching
                }
                val history = locationRepository.history(id)
                // 장소·출입 기록은 부가 정보라 실패해도 위치 표시는 그대로 간다.
                val places = runCatching { placeRepository.list(id) }.getOrDefault(emptyList())
                val events = runCatching { locationEventRepository.events(id) }.getOrDefault(emptyList())
                // 스위치 초기값을 서버에서 가져온다. 예전에는 false 로 시작해서, 실제로
                // 추적이 켜져 있어도 화면에서는 꺼진 것처럼 보였다.
                val realtime = runCatching { locationRepository.realtimeEnabled(id) }
                    .getOrDefault(_state.value.realtimeEnabled)
                _state.update {
                    it.copy(
                        loading = false,
                        hasChildren = true,
                        history = history,
                        places = places,
                        events = events,
                        realtimeEnabled = realtime,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun toggleRoute() = _state.update { it.copy(showRoute = !it.showRoute) }

    fun setRealtime(enabled: Boolean) {
        val id = childId ?: return
        viewModelScope.launch {
            runCatching { locationRepository.toggleRealtime(id, enabled) }
                .onSuccess { applied -> _state.update { it.copy(realtimeEnabled = applied, error = null) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

/** 작은 지도와 전체화면 지도가 같은 내용을 그리도록 한 곳에 모아둔다. */
@Composable
@GoogleMapComposable
private fun LocationMapContent(
    childPos: LatLng?,
    childTitle: String,
    places: List<Geofence>,
    routePoints: List<LatLng>,
) {
    val colors = LocalKidhackColors.current
    if (childPos != null) {
        // 아이 실시간 위치는 빨강, 등록 장소는 파랑으로 구분한다.
        Marker(
            state = rememberMarkerState(key = "child_${childPos.latitude}_${childPos.longitude}", position = childPos),
            title = childTitle,
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        )
    }
    SavedPlacesOverlay(places)
    if (routePoints.size >= 2) {
        Polyline(points = routePoints, color = colors.brand, width = 6f)
    }
}

/** 이탈만 이동으로 보고, 나머지(ENTER·과거 ARRIVED)는 도착으로 묶는다. */
private fun isExitEvent(type: LocationEventType): Boolean = type == LocationEventType.EXIT

private fun eventIcon(type: LocationEventType): String = if (isExitEvent(type)) "🚶" else "🏠"

/** "2026-08-25T17:05:00" -> "17:05". 파싱 실패하면 기존처럼 뒤 5글자. */
private fun hourMinute(raw: String): String =
    runCatching { java.time.LocalDateTime.parse(raw).toLocalTime().toString().take(5) }
        .getOrDefault(raw.takeLast(5))

@Composable
fun ParentLocationScreen(
    onOpenGeofence: () -> Unit = {},
    onOpenPairing: () -> Unit = {},
    viewModel: LocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    // 서버가 준 updatedAt이 실제 갱신 시각이다. 없으면 기존처럼 recordedAt.
    val isCurrentLive = state.current?.let { it.updatedAt ?: it.recordedAt }?.let {
        runCatching {
            val instant = java.time.LocalDateTime.parse(it).atZone(java.time.ZoneId.systemDefault()).toInstant()
            java.time.Duration.between(instant, java.time.Instant.now()).toMinutes() < 10
        }.getOrDefault(true)
    } ?: false

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        BrandGradientHeader {
            ParentHeaderBar(stringResource(R.string.parent_location_title))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-16).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                .verticalScroll(rememberScrollState()),
        ) {
            // 자녀 현재 위치(좌표 문자열 -> LatLng). 없으면 서울 시청 기본값.
            val defaultPos = LatLng(37.5665, 126.9780)
            val childPos = state.current?.let { c ->
                val lat = c.lat.toDoubleOrNull()
                val lng = c.lng.toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            }
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(childPos ?: defaultPos, 15f)
            }
            LaunchedEffect(childPos) {
                if (childPos != null) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(childPos, 16f)
                }
            }
            val childTitle = state.current?.label ?: stringResource(R.string.parent_location_current)
            // history는 최신순이라 뒤집어야 과거 -> 현재 순서로 선이 그려진다.
            val routePoints = if (state.showRoute) {
                state.history
                    .mapNotNull { p ->
                        p.lat.toDoubleOrNull()?.let { lat -> p.lng.toDoubleOrNull()?.let { lng -> LatLng(lat, lng) } }
                    }
                    .reversed()
            } else {
                emptyList()
            }
            var mapExpanded by remember { mutableStateOf(false) }
            // 전체화면 지도는 별도 카메라 상태를 쓴다. 하나를 두 GoogleMap이 공유하면 maps-compose가 예외를 던진다.
            val fullCameraState = rememberCameraPositionState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(224.dp)
                    // 지도 위에서 손가락을 움직이는 동안에는 상위 verticalScroll이 제스처를 가져가지 못하게 막는다.
                    // DOWN/UP은 그대로 흘려보내야 onMapClick·마커 탭이 살아있다.
                    .pointerInteropFilter { event -> event.action == MotionEvent.ACTION_MOVE },
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    LocationMapContent(childPos, childTitle, state.places, routePoints)
                }
                MapChipButton(
                    label = stringResource(R.string.parent_map_fullscreen),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    fullCameraState.position = cameraPositionState.position
                    mapExpanded = true
                }
            }
            if (mapExpanded) {
                val closeFullscreen = {
                    // 전체화면에서 옮겨둔 시야를 작은 지도에도 이어준다.
                    cameraPositionState.position = fullCameraState.position
                    mapExpanded = false
                }
                Dialog(
                    onDismissRequest = closeFullscreen,
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(Modifier.fillMaxSize().background(colors.surface)) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = fullCameraState,
                        ) {
                            LocationMapContent(childPos, childTitle, state.places, routePoints)
                        }
                        MapChipButton(
                            label = stringResource(R.string.parent_map_close),
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = closeFullscreen,
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ParentLoadingOrError(state.loading && state.current == null, state.error) { viewModel.refresh() }

                if (state.hasChildren) {
                    KidCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.parent_location_realtime_toggle),
                                modifier = Modifier.weight(1f),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 14.sp,
                                color = colors.text,
                            )
                            KidSwitch(state.realtimeEnabled, viewModel::setRealtime)
                        }
                    }
                }

                val current = state.current
                if (!state.loading && !state.hasChildren) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.parent_dashboard_empty_child),
                                color = colors.muted,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            KidButton(
                                text = stringResource(R.string.parent_dashboard_empty_pair_action),
                                onClick = onOpenPairing
                            )
                        }
                    }
                } else if (current != null) {
                    KidCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center,
                            ) { Text("📍") }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.parent_location_current), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                                Text(
                                    current.label ?: stringResource(R.string.parent_dash),
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontSemibold,
                                    fontSize = 12.sp,
                                    color = colors.muted,
                                )
                            }
                            // TODO: Backend Integration - 실시간 여부를 판단하는 기준(Threshold) 협의 필요
                            Text(
                                if (isCurrentLive) stringResource(R.string.parent_location_live) else stringResource(R.string.parent_location_recent),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentLive) Color(0xFFF0FDF4) else colors.border)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (isCurrentLive) colors.safe else colors.muted,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    KidButton(
                        text = if (state.showRoute) stringResource(R.string.parent_location_route_hide)
                        else stringResource(R.string.parent_location_route_show),
                        onClick = viewModel::toggleRoute,
                        modifier = Modifier.fillMaxWidth(),
                        variant = KidButtonVariant.Secondary,
                        // 이을 점이 둘은 있어야 선이 된다.
                        enabled = state.history.size >= 2,
                    )
                }
                // 「지정 장소 관리」는 자녀 위치가 아직 없어도 열려 있어야 한다.
                // 예전에는 current != null 블록 안에 있어서, 자녀가 위치를 한 번도 올리지
                // 않으면 장소를 등록할 방법이 아예 없었다 — 오히려 그때 미리 집·학교를
                // 넣어 두는 게 자연스럽다.
                if (state.hasChildren) {
                    KidButton(
                        text = stringResource(R.string.parent_geofence_manage),
                        onClick = onOpenGeofence,
                        modifier = Modifier.fillMaxWidth(),
                        variant = KidButtonVariant.Secondary,
                    )
                }
                if (state.events.isNotEmpty()) {
                    KidCard {
                        Text(stringResource(R.string.parent_geofence_events), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Spacer(Modifier.height(8.dp))
                        state.events.take(5).forEach { event ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(eventIcon(event.eventType), fontSize = 16.sp)
                                Spacer(Modifier.size(12.dp))
                                val placeName = state.places.find { it.id == event.placeId }?.name
                                    ?: stringResource(R.string.parent_geofence_event_unknown_place)
                                Text(
                                    stringResource(
                                        if (isExitEvent(event.eventType)) R.string.parent_geofence_event_exit
                                        else R.string.parent_geofence_event_entered,
                                        placeName,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 13.sp,
                                    color = colors.text,
                                )
                                Text(hourMinute(event.occurredAt), color = colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
