package com.hack.kidhack.feature.parent

// TODO: PlaceRequest에 notifyOnArrival 필드가 생기면 UI 복구 (docs/spec-gaps.md 참고)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.view.MotionEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.PlaceType
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class GeofenceUiState(
    val items: List<Geofence> = emptyList(),
    val name: String = "",
    val radius: String = "200",
    val notify: Boolean = true,
    /** 사용자가 지도에서 고른 저장 좌표. 고르기 전에는 null이고, null이면 저장을 막는다. */
    val pickedLat: Double? = null,
    val pickedLng: Double? = null,
    /** 지도 카메라 초기 위치 힌트(자녀 최근 좌표). 저장값이 아니다. */
    val hintLat: Double? = null,
    val hintLng: Double? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** 서울시청. 자녀 동선이 아직 없을 때 등록 좌표 기본값. */
private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LNG = 126.9780

class GeofenceViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeofenceUiState())
    val state: StateFlow<GeofenceUiState> = _state.asStateFlow()

    init { refresh() }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onRadius(v: String) = _state.update { it.copy(radius = v.filter { ch -> ch.isDigit() }) }
    fun onNotify(v: Boolean) = _state.update { it.copy(notify = v) }
    fun onMapPicked(lat: Double, lng: Double) =
        _state.update { it.copy(pickedLat = lat, pickedLng = lng, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children())
                if (id == null) {
                    _state.update { it.copy(loading = false, items = emptyList()) }
                    return@launch
                }
                // 자녀 최근 좌표는 지도 카메라를 띄울 위치일 뿐, 등록되는 좌표가 아니다.
                val hint = runCatching { locationRepository.history(id).firstOrNull() }.getOrNull()
                val places = placeRepository.list(id)
                _state.update {
                    it.copy(
                        loading = false,
                        items = places,
                        hintLat = hint?.lat?.toDoubleOrNull(),
                        hintLng = hint?.lng?.toDoubleOrNull(),
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun add() {
        val name = _state.value.name.trim()
        val radius = _state.value.radius.toIntOrNull() ?: return
        if (name.isBlank()) return
        val lat = _state.value.pickedLat
        val lng = _state.value.pickedLng
        if (lat == null || lng == null) {
            _state.update { it.copy(error = "지도에서 위치를 선택해주세요") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children) ?: return@runCatching
                placeRepository.create(
                    id,
                    Geofence(
                        id = "",
                        name = name,
                        lat = lat.toString(),
                        lng = lng.toString(),
                        radius = radius,
                        // 명세에 대응 필드가 없어 서버로 못 보낸다. state.notify는 UI 복구용으로 남겨둔다.
                        notifyOnArrival = true,
                        // 진입(도착)과 이탈(이동) 알림을 모두 받으려면 GEOFENCE여야 한다.
                        type = PlaceType.GEOFENCE,
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(name = "", pickedLat = null, pickedLng = null) }
                refresh()
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun delete(geofenceId: String) {
        viewModelScope.launch {
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching
                placeRepository.delete(id, geofenceId)
            }.onSuccess {
                _state.update { it.copy(error = null) }
                refresh()
            }.onFailure { t ->
                // 명세: 400 INVALID_PARAMETER - "본인 자녀의 장소만 삭제할 수 있습니다." 등
                _state.update { it.copy(error = t.message) }
            }
        }
    }
}

/** 고른 위치의 드래그 가능한 마커. 작은 지도와 전체화면이 같은 로직을 쓴다. */
@Composable
@GoogleMapComposable
private fun PickedPlaceMarker(picked: LatLng, onPicked: (Double, Double) -> Unit) {
    val markerState = remember { MarkerState(position = picked) }
    LaunchedEffect(picked) {
        if (markerState.position != picked) markerState.position = picked
    }
    // 드래그로 옮긴 위치도 같은 경로로 상태에 반영한다(첫 값은 이미 picked라 버린다).
    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }
            .drop(1)
            .collect { onPicked(it.latitude, it.longitude) }
    }
    // 이미 등록된 장소(파랑)와 섞이지 않도록 아직 저장 안 된 이 마커만 초록으로 둔다.
    Marker(
        state = markerState,
        draggable = true,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
    )
}

@Composable
fun ParentGeofenceScreen(
    onBack: () -> Unit = {},
    viewModel: GeofenceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_geofence_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::refresh)
        if (state.items.isEmpty() && !state.loading) {
            Text(stringResource(R.string.parent_geofence_empty), color = colors.muted, fontSize = 13.sp)
        }
        state.items.forEach { item ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 20.sp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Text(
                            stringResource(R.string.parent_geofence_radius_unit, item.radius),
                            color = colors.muted,
                            fontSize = 12.sp,
                        )
                    }
                    KidButton(
                        stringResource(R.string.parent_geofence_delete),
                        { viewModel.delete(item.id) },
                        variant = KidButtonVariant.Danger,
                    )
                }
            }
        }
        KidCard {
            Text(stringResource(R.string.parent_geofence_add), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.name, viewModel::onName, stringResource(R.string.parent_geofence_name), placeholder = stringResource(R.string.parent_geofence_name_hint))
            Spacer(Modifier.height(12.dp))
            val picked = state.pickedLat?.let { lat -> state.pickedLng?.let { lng -> LatLng(lat, lng) } }
            // 자녀 좌표 > 이미 등록한 장소 > 서울시청 순으로 지도를 띄운다.
            // 자녀가 아직 위치를 안 올렸다고 서울시청을 비추면, 등록해 둔 장소가 화면
            // 밖에 있어 지도에 아무것도 없는 것처럼 보인다.
            val camera = state.hintLat?.let { lat -> state.hintLng?.let { lng -> LatLng(lat, lng) } }
                ?: state.items.firstNotNullOfOrNull { it.latLngOrNull() }
                ?: LatLng(DEFAULT_LAT, DEFAULT_LNG)
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(camera, 15f)
            }
            // 자녀 좌표·장소 목록이 늦게 도착해도 아직 아무 곳도 안 골랐다면 그쪽으로 옮긴다.
            LaunchedEffect(camera) {
                if (picked == null) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(camera, 15f)
                }
            }
            var mapExpanded by remember { mutableStateOf(false) }
            // 전체화면 지도는 별도 카메라 상태를 쓴다. 하나를 두 GoogleMap이 공유하면 maps-compose가 예외를 던진다.
            val fullCameraState = rememberCameraPositionState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    // 지도 위에서 손가락을 움직이는 동안에는 상위 verticalScroll이 제스처를 가져가지 못하게 막는다.
                    // DOWN/UP은 그대로 흘려보내야 onMapClick·마커 탭이 살아있다.
                    .pointerInteropFilter { event -> event.action == MotionEvent.ACTION_MOVE },
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng -> viewModel.onMapPicked(latLng.latitude, latLng.longitude) },
                ) {
                    SavedPlacesOverlay(state.items)
                    if (picked != null) PickedPlaceMarker(picked, viewModel::onMapPicked)
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
                        // 전체화면에서도 탭·드래그로 위치를 고를 수 있다.
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = fullCameraState,
                            onMapClick = { latLng -> viewModel.onMapPicked(latLng.latitude, latLng.longitude) },
                        ) {
                            SavedPlacesOverlay(state.items)
                            if (picked != null) PickedPlaceMarker(picked, viewModel::onMapPicked)
                        }
                        MapChipButton(
                            label = stringResource(R.string.parent_map_close),
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = closeFullscreen,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.parent_geofence_pick_hint),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            KidTextField(
                state.radius,
                viewModel::onRadius,
                stringResource(R.string.parent_geofence_radius),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(12.dp))
            KidButton(stringResource(R.string.parent_geofence_save), viewModel::add, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}
