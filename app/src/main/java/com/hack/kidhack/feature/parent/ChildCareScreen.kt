package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildCareUiState(
    val children: List<Pair<String, String>> = emptyList(),
    val selectedChildId: String? = null,
) {
    /** 화면 제목에 쓸 이름. 아직 못 불러왔으면 빈 값. */
    val selectedChildName: String
        get() = children.firstOrNull { it.first == selectedChildId }?.second.orEmpty()
}

/**
 * 자녀 고르기와 자녀별 설정 입구가 함께 쓰는 상태.
 *
 * <p>여기서 고른 자녀가 그 뒤로 들어가는 모든 화면의 대상이 된다. 각 설정 화면은
 * 저마다 선택된 자녀를 다시 읽으므로, 이 화면이 따로 넘겨 줄 것은 없다.
 */
class ChildCareViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildCareUiState())
    val state: StateFlow<ChildCareUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching {
                val children = childRepository.children()
                children to selectedChildRepository.resolveId(children)
            }.onSuccess { (children, id) ->
                _state.update {
                    it.copy(children = children.map { c -> c.id to c.name }, selectedChildId = id)
                }
            }
        }
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

/** 위는 연주황, 아래로 갈수록 흰색. 자녀관리의 두 화면이 같은 바탕을 쓴다. */
private fun warmBackground(top: Color, bottom: Color): Brush =
    Brush.verticalGradient(0f to top, 0.55f to bottom, 1f to bottom)

/**
 * 자녀 고르기. 자녀관리 탭의 첫 화면이다.
 *
 * <p>부모 설정은 거의 전부 자녀 한 명 기준이다. 전에는 화면 위 칩으로 자녀를 바꿨는데,
 * 칩은 눈에 잘 띄지 않아 다른 아이 설정을 건드리기 쉬웠다. 누구의 설정을 여는지 먼저
 * 고르게 하면 그 실수가 생기지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentChildrenScreen(
    onOpenChild: () -> Unit = {},
    onAddChild: () -> Unit = {},
    viewModel: ChildCareViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground(colors.brandLight, colors.card))
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

        // 남은 자리를 다 차지해서 칸들이 화면 한가운데 놓이게 한다. 제목 바로 아래에
        // 붙여 두면 아래쪽이 텅 비어 화면이 미완성처럼 보인다.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 가운데로 모아 한 줄에 둘씩. 하나면 가운데 하나, 셋이면 아래 줄로 접힌다.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                state.children.forEach { (id, name) ->
                    ChildTile(name = name) { viewModel.selectChild(id, onOpenChild) }
                }
                AddChildTile(onClick = onAddChild)
            }
        }
    }
}

/**
 * 자녀 한 명을 나타내는 정사각 칸.
 *
 * <p>목록 줄로 늘어놓으면 자녀가 설정 항목처럼 보인다. 정사각형으로 세워 두면 "누구"를
 * 고르는 화면이라는 것이 먼저 읽힌다.
 */
@Composable
private fun ChildTile(name: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .width(TILE_SIZE)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "👦", fontSize = 32.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = name,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 15.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 자녀를 더 잇는 칸. 크기는 같지만 비워 두어 자녀 칸보다 눈에 덜 걸리게 한다. */
@Composable
private fun AddChildTile(onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .width(TILE_SIZE)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "+", color = colors.muted, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.parent_childcare_add_child),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            color = colors.muted,
        )
    }
}

/** 칸 한 변. 두 개가 나란히 서고도 좌우 여백이 남는 크기. */
private val TILE_SIZE = 152.dp

/** 한 줄이 담는 것. 화면 본문에서 목록만 읽히도록 항목을 값으로 뺐다. */
private data class CareItem(
    val icon: String,
    val tint: Color,
    val titleRes: Int,
    val onClick: () -> Unit,
)

/** 묶음 하나. 레퍼런스처럼 작은 회색 이름 아래 카드 한 장으로 묶인다. */
private data class CareGroup(val titleRes: Int, val items: List<CareItem>)

// 항목을 구별하는 파스텔 배경. 글자만 늘어놓으면 어느 줄이 무엇인지 매번 읽어야 한다.
private val TintOrange = Color(0xFFFFE8D2)
private val TintBlue = Color(0xFFDCEBFF)
private val TintGreen = Color(0xFFD8F3E3)
private val TintPurple = Color(0xFFE8E1FF)
private val TintPink = Color(0xFFFFE0EC)
private val TintYellow = Color(0xFFFFF0CC)

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
                CareItem("📊", TintOrange, R.string.parent_childcare_report, onOpenReport),
                CareItem("📱", TintBlue, R.string.parent_childcare_usage_detail, onOpenUsageDetail),
            ),
        ),
        CareGroup(
            R.string.parent_childcare_group_promise,
            listOf(
                CareItem("⏰", TintYellow, R.string.parent_childcare_screentime, onOpenScreenTime),
                CareItem("🔒", TintPurple, R.string.parent_childcare_appcontrol, onOpenAppControl),
            ),
        ),
        CareGroup(
            R.string.parent_childcare_group_screen,
            listOf(
                CareItem("📸", TintPink, R.string.parent_childcare_screenshot, onOpenScreenshot),
            ),
        ),
    )
    val safetyGroups = listOf(
        CareGroup(
            R.string.parent_childcare_group_online,
            listOf(CareItem("🚫", TintPink, R.string.parent_childcare_harmful, onOpenHarmful)),
        ),
        CareGroup(
            R.string.parent_childcare_group_place,
            listOf(CareItem("📍", TintGreen, R.string.parent_childcare_geofence, onOpenGeofence)),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground(colors.brandLight, colors.card))
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
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(item.tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = item.icon, fontSize = 15.sp)
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
        Text(text = "›", color = colors.muted, fontSize = 18.sp)
    }
}
