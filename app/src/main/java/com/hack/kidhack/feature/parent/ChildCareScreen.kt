package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ChildWatchStatus
import com.hack.kidhack.background.alert.ParentChildWatcher
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.core.common.todayRange
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.permissionRequester
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildCareUiState(
    val children: List<ChildProfile> = emptyList(),
    /**
     * 자녀 id → 지금 상태. 자녀 목록보다 늦게 도착하므로 그 사이에는 비어 있다.
     *
     * <p>타일 **차례는 이 값으로 바뀌지 않는다.** 격자에서 칸이 움직이면 늘 누르던 자리에
     * 다른 아이가 와 있어서, 부모가 다른 아이 설정을 열게 된다.
     */
    val statuses: Map<String, ChildWatchStatus> = emptyMap(),
    /**
     * 자녀 id → 오늘 사용량. 상태와 마찬가지로 목록보다 늦게 도착한다.
     *
     * <p>없는 동안에는 막대를 그리지 않는다. 0% 로 그려 두면 「아직 모른다」가 「하나도 안
     * 썼다」로 읽힌다.
     */
    val usages: Map<String, ChildDayUsage> = emptyMap(),
    /**
     * 고른 자녀가 **아직 안 걸어 둔** 설정들. 목록은 값을 적지 않고 이 빈자리만 알려 준다.
     *
     * <p>「5시간」인지 「3시간」인지는 들어가면 나온다. 목록에서 부모가 놓치는 것은 값이
     * 아니라 **아예 손대지 않은 항목**이라, 그것만 짚는다.
     */
    val setup: ChildSetup = ChildSetup(),
    val selectedChildId: String? = null,
) {
    /** 화면 제목에 쓸 이름. 아직 못 불러왔으면 빈 값. */
    val selectedChildName: String
        get() = children.firstOrNull { it.id == selectedChildId }?.name.orEmpty()
}

/**
 * 자녀 한 명의 오늘 사용량.
 *
 * @param usedSeconds 오늘 쓴 시간. 앱별 집계를 합한 값이다.
 * @param limitMinutes 하루 약속(분). null 이면 약속을 걸어 두지 않았다 — 그때는 견줄 기준이
 *   없으므로 막대를 그리지 않고 쓴 시간만 적는다.
 */
data class ChildDayUsage(val usedSeconds: Int, val limitMinutes: Int?)

/**
 * 자녀별 설정이 채워져 있는지.
 *
 * <p>값이 **null 이면 「모른다」**다. 아직 못 불러왔거나 조회가 실패한 것을 「설정 안 함」
 * 으로 적으면, 멀쩡히 걸어 둔 부모에게 거짓말이 된다. 모를 때는 아무 말도 하지 않는다.
 */
data class ChildSetup(
    val screenTime: Boolean? = null,
    val appControl: Boolean? = null,
    val harmful: Boolean? = null,
    val places: Boolean? = null,
)

/**
 * 자녀 고르기와 자녀별 설정 입구가 함께 쓰는 상태.
 *
 * <p>여기서 고른 자녀가 그 뒤로 들어가는 모든 화면의 대상이 된다. 각 설정 화면은
 * 저마다 선택된 자녀를 다시 읽으므로, 이 화면이 따로 넘겨 줄 것은 없다.
 */
class ChildCareViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val alertSync: ServerAlertSync,
    private val childWatcher: ParentChildWatcher,
    private val appUsageRepository: AppUsageRepository,
    private val appControlRepository: AppControlRepository,
    private val blocklistRepository: BlocklistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildCareUiState())
    val state: StateFlow<ChildCareUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val loaded = runCatching {
                val children = childRepository.children()
                children to selectedChildRepository.resolveId(children)
            }.getOrNull()
            if (loaded != null) {
                _state.update {
                    it.copy(children = loaded.first, selectedChildId = loaded.second)
                }
            }
            // 부모가 앱을 열 때 서버에 쌓인 알림을 당겨 오고, 자녀 상태를 지금 판정한다.
            // 15분 주기 워커를 기다리면 방금 벌어진 일을 한참 뒤에야 알게 된다.
            // 전에는 대시보드가 첫 화면이라 거기서 했는데, 이제 이 화면이 첫 화면이다.
            runCatching { alertSync.sync() }
            // 판정에 드는 요청은 워처가 이미 보낸다. 방금 불러온 목록을 넘겨 같은 조회를
            // 두 번 하지 않는다. 목록을 못 받았으면 워처가 스스로 부르게 둔다(null).
            val known = loaded?.first ?: _state.value.children.takeIf { it.isNotEmpty() }
            runCatching { childWatcher.evaluateAll(known) }
                // 판정이 실패했다고 이미 그린 상태를 지우지는 않는다. 마지막으로 안 것을
                // 그대로 두는 편이 「모름」으로 되돌아가는 것보다 낫다.
                .onSuccess { statuses -> _state.update { it.copy(statuses = statuses) } }
            loadUsage(known.orEmpty())
            loadSetup()
        }
    }

    /**
     * 고른 자녀가 안 걸어 둔 설정을 알아 온다.
     *
     * <p>고른 자녀 **한 명만** 본다. 목록 화면은 이 값을 쓰지 않으므로 자녀 수만큼 부르면
     * 쓰지도 않을 요청이 늘어난다.
     *
     * <p>안전 구역은 워처가 판정하며 이미 세어 두었고([ChildWatchStatus.fenceCount]), 하루
     * 약속도 [loadUsage] 가 받아 온 값에 들어 있다. 그래서 여기서 새로 묻는 것은 앱 통제와
     * 유해사이트 둘뿐이고, 개수가 아니라 **비었는지만** 본다.
     */
    private suspend fun loadSetup() {
        val childId = _state.value.selectedChildId ?: return
        val fenceCount = _state.value.statuses[childId]?.fenceCount
        val screenTime = _state.value.usages[childId]?.limitMinutes?.let { it > 0 }
        val appControl = runCatching { appControlRepository.items(childId).isNotEmpty() }.getOrNull()
        val harmful = runCatching { blocklistRepository.get(childId).isNotEmpty() }.getOrNull()
        _state.update {
            it.copy(
                setup = ChildSetup(
                    screenTime = screenTime,
                    appControl = appControl,
                    harmful = harmful,
                    places = fenceCount?.let { count -> count > 0 },
                ),
            )
        }
    }

    /**
     * 자녀별 오늘 사용량과 하루 약속.
     *
     * <p>위치와 달리 워처가 판정에 쓰지 않는 값이라 여기서 따로 부른다. 자녀 한 명당 두
     * 번(사용량·약속)이므로 **자녀끼리는 나란히** 보낸다 — 차례로 부르면 자녀가 셋일 때
     * 여섯 번을 줄 세우게 된다.
     *
     * <p>한 명이 실패해도 나머지는 그린다. 실패한 자녀는 결과에서 빠지고, 화면은 그 줄의
     * 막대를 그리지 않는다.
     */
    private suspend fun loadUsage(children: List<ChildProfile>) {
        if (children.isEmpty()) return
        val (from, to) = todayRange()
        val loaded = coroutineScope {
            children.map { child ->
                async {
                    runCatching {
                        val used = appUsageRepository.list(child.id, "APP", from, to)
                            .sumOf { it.durationSec }
                        // 약속은 부가 정보다. 못 받아 와도 쓴 시간은 보여 준다.
                        val limit = runCatching {
                            appControlRepository.screenTimeLimit(child.id)
                        }.getOrNull()
                        child.id to ChildDayUsage(used, limit)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (loaded.isNotEmpty()) _state.update { it.copy(usages = it.usages + loaded) }
    }

    /**
     * 자녀를 고른다.
     *
     * <p>[onSelected] 는 저장이 끝난 뒤에 부른다. 곧바로 넘어가면 다음 화면이 아직 이전
     * 자녀를 읽어서, 방금 고른 아이가 아닌 설정이 열린다.
     */
    fun selectChild(childId: String, onSelected: () -> Unit = {}) {
        viewModelScope.launch {
            selectedChildRepository.select(childId)
            load()
            onSelected()
        }
    }
}

/**
 * 자녀 고르기. 자녀관리 탭의 첫 화면이다.
 *
 * <p>부모 설정은 거의 전부 자녀 한 명 기준이다. 전에는 화면 위 칩으로 자녀를 바꿨는데,
 * 칩은 눈에 잘 띄지 않아 다른 아이 설정을 건드리기 쉬웠다. 누구의 설정을 여는지 먼저
 * 고르게 하면 그 실수가 생기지 않는다.
 */
@Composable
fun ParentChildrenScreen(
    onOpenChild: () -> Unit = {},
    onAddChild: () -> Unit = {},
    viewModel: ChildCareViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val permissions = permissionRequester()

    // 안전 구역 이탈·유해 사이트 차단을 푸시로 받으려면 알림 권한이 필요하다(안드로이드 13+).
    // 부모가 앱을 열면 처음 닿는 화면이 여기라 여기서 묻는다. 이미 거절해서 시스템
    // 대화상자가 뜨지 않는 상태면 조용히 넘어간다.
    LaunchedEffect(Unit) {
        val permission = KidhackPermissions.notifications ?: return@LaunchedEffect
        val activity = KidhackPermissions.findActivity(context) ?: return@LaunchedEffect
        if (!KidhackPermissions.canShowSystemDialog(activity, permission)) return@LaunchedEffect
        permissions.requestOne(permission) { granted ->
            if (!granted) KidhackPermissions.recordDeny(context, permission)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.parent_childcare_title),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 21.sp,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.parent_childcare_pick_child),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            color = colors.muted,
            fontSize = 13.sp,
        )

        // 남은 자리를 다 차지해서 줄들이 화면 한가운데 놓이게 한다. 제목 바로 아래에
        // 붙여 두면 아래쪽이 텅 비어 화면이 미완성처럼 보인다.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.children.forEach { child ->
                    ChildRow(
                        child = child,
                        status = state.statuses[child.id],
                        usage = state.usages[child.id],
                    ) { viewModel.selectChild(child.id, onOpenChild) }
                }
                AddChildRow(onClick = onAddChild)
            }
        }
    }
}

/**
 * 자녀 한 명을 나타내는 가로 줄.
 *
 * <p>전에는 정사각 칸이었다. 칸이 좁아 상태를 11자에서 잘라야 했고, 그 탓에 구역 밖일 때
 * 시각을 버렸다. 가로로 눕히면 「안전 구역 밖 · 12분 전」이 한 줄에 들어가고, 남는 폭에
 * 오늘 사용량 막대까지 들어간다.
 *
 * <p>부모가 앱을 여는 이유는 두 가지다 — 어디 있나, 얼마나 썼나. 한 줄이 둘 다 답한다.
 * 얼굴 옆 점은 위치가 지금도 들어오는지를 말한다.
 */
@Composable
private fun ChildRow(
    child: ChildProfile,
    status: ChildWatchStatus?,
    usage: ChildDayUsage?,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.brandLight),
                contentAlignment = Alignment.Center,
            ) {
                val photo = child.profileImageUrl
                if (photo.isNullOrBlank()) {
                    Text(text = "👦", fontSize = 28.sp)
                } else {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            // 위치가 지금도 들어오는지. 흰 테두리를 둘러야 어떤 사진 위에서도 점으로 보인다.
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(if (status?.connected == true) colors.safe else DotOffline),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = child.name,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                val attention = status?.outsideFence == true
                Text(
                    text = rowStatus(status),
                    modifier = Modifier.weight(1f, fill = false),
                    fontFamily = KidhackFontFamily,
                    // 색만으로 알리면 색을 구별하기 어려운 부모에게는 없는 것과 같다. 구역
                    // 밖일 때는 글자 굵기로도 함께 말한다.
                    fontWeight = if (attention) FontBold else FontSemibold,
                    fontSize = 11.5.sp,
                    color = if (attention) colors.brandDark else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(9.dp))
            UsageBar(usage)
        }

        Text(text = "›", color = colors.muted, fontSize = 20.sp)
    }
}

/**
 * 오늘 사용량 막대.
 *
 * <p>아직 못 받아 왔으면 회색 자리만 둔다. 0% 막대로 그리면 「아직 모른다」가 「하나도 안
 * 썼다」로 읽히고, 자리를 아예 비우면 값이 도착할 때 줄 높이가 튄다.
 */
@Composable
private fun UsageBar(usage: ChildDayUsage?) {
    val colors = LocalKidhackColors.current
    if (usage == null) {
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(11.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.surface),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.parent_childcare_row_loading),
            color = colors.muted.copy(alpha = 0.6f),
            fontSize = 10.5.sp,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
        )
        return
    }

    val limitMinutes = usage.limitMinutes
    // 약속을 안 걸어 뒀으면 견줄 기준이 없다. 막대 없이 쓴 시간만 적는다 — 기준 없는
    // 막대는 얼마나 남았는지 말하는 것처럼 보이지만 실제로는 아무것도 말하지 않는다.
    if (limitMinutes == null || limitMinutes <= 0) {
        Text(
            text = stringResource(
                R.string.parent_childcare_row_used_only,
                usageLabel(usage.usedSeconds),
            ),
            color = colors.muted,
            fontSize = 10.5.sp,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
        )
        return
    }

    val usedMinutes = usage.usedSeconds / SECONDS_PER_MINUTE
    val over = usedMinutes >= limitMinutes
    val ratio = (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(colors.brandLight),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio)
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (over) colors.danger else colors.brand),
        )
    }
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(
                R.string.parent_childcare_row_used,
                usageLabel(usage.usedSeconds),
            ),
            color = if (over) colors.danger else colors.muted,
            fontSize = 10.5.sp,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
        )
        Text(
            text = if (over) {
                stringResource(R.string.parent_childcare_row_promise_over)
            } else {
                stringResource(
                    R.string.parent_childcare_row_promise,
                    usageLabel(limitMinutes * SECONDS_PER_MINUTE),
                )
            },
            color = colors.muted,
            fontSize = 10.5.sp,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
        )
    }
}

/** 「1시간 22분」. 1분이 안 되면 초로 — 분으로 자르면 쓴 앱이 있는데 0분으로 보인다. */
private fun usageLabel(totalSeconds: Int): String {
    if (totalSeconds < SECONDS_PER_MINUTE) return totalSeconds.toString() + "초"
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR.toInt()
    val rest = minutes % MINUTES_PER_HOUR.toInt()
    return when {
        hours > 0 && rest > 0 -> hours.toString() + "시간 " + rest + "분"
        hours > 0 -> hours.toString() + "시간"
        else -> minutes.toString() + "분"
    }
}

private const val SECONDS_PER_MINUTE = 60

/**
 * 이름 옆에 붙는 지금 상태.
 *
 * <p>가로 줄이라 정사각 칸과 달리 「어디 · 언제」를 끝까지 담을 수 있다. 구역 밖일 때도
 * 시각을 함께 말한다 — 12분 전과 두 시간 전은 부모가 할 일이 다르다.
 */
@Composable
private fun rowStatus(status: ChildWatchStatus?): String {
    val age = status?.ageMinutes ?: return stringResource(R.string.parent_childcare_tile_no_location)
    if (status.stale) return stringResource(R.string.parent_childcare_tile_disconnected)
    if (status.outsideFence) {
        return stringResource(
            R.string.parent_childcare_tile_place,
            stringResource(R.string.parent_childcare_tile_outside),
            ageText(age),
        )
    }
    val place = status.placeName?.takeIf { it.isNotBlank() }
    // 안전 구역을 하나도 등록하지 않았으면 말할 수 있는 것은 시각뿐이다.
    return if (place == null) ageText(age)
    else stringResource(R.string.parent_childcare_tile_place, place, ageText(age))
}

/** 「4분 전」. 기기 시계가 앞서 있으면 음수가 나오므로 그것도 「방금」으로 본다. */
@Composable
private fun ageText(minutes: Long): String = when {
    minutes < 1 -> stringResource(R.string.parent_childcare_age_now)
    minutes < MINUTES_PER_HOUR -> stringResource(R.string.parent_childcare_age_min, minutes.toInt())
    minutes < MINUTES_PER_DAY ->
        stringResource(R.string.parent_childcare_age_hour, (minutes / MINUTES_PER_HOUR).toInt())
    else -> stringResource(R.string.parent_childcare_age_day, (minutes / MINUTES_PER_DAY).toInt())
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 60L * 24

/** 위치가 끊겼을 때의 점. 경고가 아니라 「모른다」라서 색상(hue)을 주지 않는다. */
private val DotOffline = Color(0xFFC2C6CC)

/** 자녀를 더 잇는 줄. 자녀 줄과 폭은 같지만 비워 두어 눈에 덜 걸리게 한다. */
@Composable
private fun AddChildRow(onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.parent_childcare_add_child_row),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            color = colors.muted,
        )
    }
}

/** 한 줄이 담는 것. 화면 본문에서 목록만 읽히도록 항목을 값으로 뺐다. */
private data class CareItem(
    @DrawableRes val icon: Int,
    val titleRes: Int,
    /**
     * 이 설정이 채워져 있는지. null 이면 모르거나 「설정」이라는 개념이 없는 항목이다
     * (사용 리포트처럼 보기만 하는 것). false 일 때만 줄 오른쪽에 「설정 안 함」이 붙는다.
     */
    val configured: Boolean? = null,
    val onClick: () -> Unit,
)

/** 묶음 하나. 레퍼런스처럼 작은 회색 이름 아래 카드 한 장으로 묶인다. */
private data class CareGroup(val titleRes: Int, val items: List<CareItem>)

// 부모 화면 전체가 같은 색을 써야 같은 뜻으로 읽히므로 패키지 안에서 함께 쓴다.
//
// 자녀 설정 목록은 더 이상 이 색들을 쓰지 않는다. 줄마다 색을 달리 주면 색이 뜻을 나르는
// 것처럼 보이는데 실제로는 아무것도 가리키지 않아서, 자동으로 채운 화면처럼 읽혔다.
// 지금은 아이콘 칸을 브랜드 한 가지 톤으로 통일한다. 지갑 화면은 이 색들을 계속 쓴다.
internal val TintOrange = Color(0xFFFFE8D2)
internal val TintBlue = Color(0xFFDCEBFF)
internal val TintGreen = Color(0xFFD8F3E3)
internal val TintPurple = Color(0xFFE8E1FF)
internal val TintPink = Color(0xFFFFE0EC)
internal val TintYellow = Color(0xFFFFF0CC)

/**
 * 자녀 한 명의 설정 입구.
 *
 * <p>전에는 사용량은 대시보드에, 통제·캡처·유해사이트는 마이페이지 밑에 흩어져 있었다.
 * 왜 「마이」에 자녀 설정이 있는지 설명할 수 없었고, 찾으려면 두 곳을 뒤져야 했다.
 *
 * <p>「사용 관리」와 「안전 케어」로 나눈 기준은 **부모가 무엇을 걱정하는가**다. 얼마나
 * 오래·무엇을 쓰는지는 사용 관리, 무엇을 보고 어디에 있는지는 안전 케어다. 기능이
 * 늘어나도 이 질문으로 자리를 정할 수 있다.
 *
 * <p>한 줄에 설명을 달지 않는다. 입구 화면에서 필요한 것은 "무엇이 있는지" 한눈에 훑는
 * 일이라, 설명이 붙으면 줄 높이가 두 배가 되어 한 화면에 절반밖에 담기지 않는다.
 */
@Composable
fun ParentChildCareScreen(
    onBack: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenUsageDetail: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    onOpenAppControl: () -> Unit = {},
    onOpenScreenshot: () -> Unit = {},
    onOpenHarmful: () -> Unit = {},
    onOpenGeofence: () -> Unit = {},
    viewModel: ChildCareViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val usageGroups = listOf(
        CareGroup(
            R.string.parent_childcare_group_status,
            listOf(
                CareItem(R.drawable.ic_report, R.string.parent_childcare_report, onClick = onOpenReport),
                CareItem(R.drawable.ic_apps, R.string.parent_childcare_usage_detail, onClick = onOpenUsageDetail),
            ),
        ),
        CareGroup(
            R.string.parent_childcare_group_promise,
            listOf(
                CareItem(
                    R.drawable.ic_clock,
                    R.string.parent_childcare_screentime,
                    state.setup.screenTime,
                    onOpenScreenTime,
                ),
                CareItem(
                    R.drawable.ic_lock,
                    R.string.parent_childcare_appcontrol,
                    state.setup.appControl,
                    onOpenAppControl,
                ),
            ),
        ),
        CareGroup(
            R.string.parent_childcare_group_screen,
            listOf(
                CareItem(R.drawable.ic_capture, R.string.parent_childcare_screenshot, onClick = onOpenScreenshot),
            ),
        ),
    )
    val safetyGroups = listOf(
        CareGroup(
            R.string.parent_childcare_group_online,
            listOf(CareItem(
                    R.drawable.ic_shield,
                    R.string.parent_childcare_harmful,
                    state.setup.harmful,
                    onOpenHarmful,
                )),
        ),
        CareGroup(
            R.string.parent_childcare_group_place,
            listOf(CareItem(
                    R.drawable.ic_place,
                    R.string.parent_childcare_geofence,
                    state.setup.places,
                    onOpenGeofence,
                )),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                color = colors.text,
                fontSize = 26.sp,
            )
            Text(
                text = state.selectedChildName,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 21.sp,
                color = colors.text,
            )
        }

        CareTabs(
            titles = listOf(
                stringResource(R.string.parent_childcare_tab_usage),
                stringResource(R.string.parent_childcare_tab_safety),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        Column(Modifier.verticalScroll(rememberScrollState())) {
            (if (tab == 0) usageGroups else safetyGroups).forEach { group ->
                Text(
                    text = stringResource(group.titleRes),
                    modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                    color = colors.muted,
                    fontSize = 12.sp,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                ) {
                    group.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = colors.border.copy(alpha = 0.6f),
                            )
                        }
                        CareRow(item)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 두 갈래 탭.
 *
 * <p>화면 폭을 반씩 나눠 갖는 대신 글자 폭만큼만 차지하고 왼쪽에 붙는다. 탭이 둘뿐이라
 * 폭을 채우면 버튼처럼 보이고, 아래 카드 목록보다 무거워진다.
 */
@Composable
private fun CareTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Column(
                // 밑줄이 글자 폭에 맞아야 한다. 그냥 두면 밑줄이 남은 폭을 다 먹어서
                // 두 번째 탭이 화면 밖으로 밀린다.
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    color = if (active) colors.text else colors.muted,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (active) colors.brand else Color.Transparent),
                )
            }
        }
    }
}

/** 설정 한 줄. 파스텔 칸에 담긴 아이콘 하나로 어느 줄인지 바로 짚이게 한다. */
@Composable
private fun CareRow(item: CareItem) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.icon),
                // 아이콘 옆에 이름이 그대로 있다. 읽어 줄 것을 두 번 말하지 않는다.
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = colors.brandDark,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(item.titleRes),
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        // 값을 적지 않고 빈자리만 알린다. 잘 걸어 둔 줄까지 글자를 붙이면 여섯 줄이 모두
        // 오른쪽에 무언가를 달고 있어 목록이 빽빽해진다.
        if (item.configured == false) {
            Text(
                text = stringResource(R.string.parent_childcare_not_set),
                modifier = Modifier.padding(end = 8.dp),
                color = colors.muted.copy(alpha = 0.75f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }
        Text(text = "›", color = colors.muted, fontSize = 18.sp)
    }
}
