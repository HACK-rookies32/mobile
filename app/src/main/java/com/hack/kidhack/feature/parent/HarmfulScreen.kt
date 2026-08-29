package com.hack.kidhack.feature.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.common.HarmfulCategories
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.local.HarmfulSettingsStore
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class HarmfulCategory(
    val id: String,
    val labelRes: Int,
)

/**
 * 화면에 보이는 4개 카테고리.
 *
 * 실제 차단 키워드 묶음은 [HarmfulCategories] 가 갖고 있다. 예전에는 여기에 카테고리당
 * 키워드가 하나씩(gambling.com, 성인 …)만 박혀 있어서, 토글을 켜도 그 한 단어가
 * 화면에 그대로 찍힌 페이지가 아니면 아무것도 걸리지 않았다.
 */
private val HarmfulBundles = listOf(
    HarmfulCategory(HarmfulCategories.ADULT, R.string.parent_harmful_adult),
    HarmfulCategory(HarmfulCategories.GAMBLING, R.string.parent_harmful_gambling),
    HarmfulCategory(HarmfulCategories.VIOLENCE, R.string.parent_harmful_violence),
    HarmfulCategory(HarmfulCategories.DRUGS, R.string.parent_harmful_drugs),
)

data class HarmfulUiState(
    val items: List<BlocklistItem> = emptyList(),
    val autoOn: Boolean = true,
    val keyword: String = "",
    val loading: Boolean = true,
    /** 서버에 반영하는 중. 끝나기 전에 다른 토글을 눌러 순서가 꼬이는 걸 막는다. */
    val saving: Boolean = false,
    val error: String? = null,
    /** 오류는 아니지만 알려 줘야 하는 것(예: 카테고리에 이미 들어 있는 키워드). */
    val notice: String? = null,
) {
    // 서버 모델에 목록 종류가 없다. 규칙은 전부 차단 패턴이다.
    val blacklist: List<BlocklistItem>
        get() = items

    /**
     * 부모가 직접 추가한 키워드만. 카테고리 묶음 키워드(수십 개)는 토글이 대신 보여 주므로
     * 목록에 그대로 쏟아 내지 않는다.
     */
    val customKeywords: List<BlocklistItem>
        get() = blacklist.filter { HarmfulCategories.categoryOf(it.pattern) == null }

    /**
     * 묶음 키워드가 **하나라도** 블랙리스트에 있으면 켜진 것으로 본다.
     *
     * 「전부 있어야 ON」으로 보면 키워드 하나만 전송이 실패해도 토글이 꺼진 것처럼 보이고,
     * 다시 누르면 이미 있는 키워드를 또 넣으려 해서 상태가 영영 맞지 않는다.
     */
    fun categoryOn(categoryId: String): Boolean {
        val bundle = HarmfulCategories.keywords(categoryId)
        return blacklist.any { item -> bundle.any { it.equals(item.pattern, ignoreCase = true) } }
    }
}

class HarmfulViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val blocklistRepository: BlocklistRepository,
    private val settingsStore: HarmfulSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HarmfulUiState())
    val state: StateFlow<HarmfulUiState> = _state.asStateFlow()

    /** 토글·추가 처리에도 쓰이므로 화면 진입 시 한 번 정해 두고 들고 있는다. */
    private var childId: String? = null

    init { load() }

    fun onKeyword(v: String) = _state.update { it.copy(keyword = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children())
                childId = id
                if (id == null) {
                    emptyList<BlocklistItem>() to true
                } else {
                    blocklistRepository.get(id) to settingsStore.autoOn(id)
                }
            }.onSuccess { (items, autoOn) ->
                _state.update {
                    it.copy(
                        loading = false,
                        items = items,
                        autoOn = autoOn,
                        // 자녀가 없으면 목록이 빈 것과 구분이 안 된다. 왜 저장이 안 되는지 알려 준다.
                        error = if (childId == null) NO_CHILD else null,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage(LOAD_FAILED)) }
            }
        }
    }

    /**
     * 「키워드 기반 차단」 마스터 토글.
     *
     * 서버에 대응하는 필드가 없어 예전에는 화면 상태로만 존재했다(나갔다 오면 초기화).
     * 이제 자녀별로 저장하고, 끄면 카테고리 키워드를 실제로 블랙리스트에서 내린다.
     * 직접 추가한 키워드는 건드리지 않는다.
     */
    fun onAuto(value: Boolean) {
        _state.update { it.copy(autoOn = value, notice = null) }
        viewModelScope.launch {
            val id = requireChildId() ?: return@launch
            settingsStore.setAutoOn(id, value)
            if (value) {
                val remembered = settingsStore.rememberedCategories(id)
                    .ifEmpty { HarmfulBundles.map { it.id } }
                applyKeywords(id, remembered.flatMap { HarmfulCategories.keywords(it) }, enabled = true)
            } else {
                val on = HarmfulBundles.map { it.id }.filter { _state.value.categoryOn(it) }
                settingsStore.rememberCategories(id, on)
                applyKeywords(id, on.flatMap { HarmfulCategories.keywords(it) }, enabled = false)
            }
        }
    }

    fun toggleCategory(categoryId: String, enabled: Boolean) {
        val keywords = HarmfulCategories.keywords(categoryId)
        if (keywords.isEmpty()) return
        viewModelScope.launch {
            val id = requireChildId() ?: return@launch
            applyKeywords(id, keywords, enabled)
        }
    }

    /**
     * 차단 키워드 추가.
     *
     * 예전에는 자녀 id 를 못 구하면 조용히 코루틴이 끝나서, 입력한 키워드가 목록에도 안 뜨고
     * 오류도 안 뜨는 상태가 됐다(자녀가 아직 연결되지 않은 계정에서 항상 그랬다).
     */
    fun addKeyword() {
        val keyword = _state.value.keyword.trim()
        if (keyword.isBlank()) return
        viewModelScope.launch {
            val id = requireChildId() ?: return@launch
            if (_state.value.blacklist.any { it.pattern.equals(keyword, ignoreCase = true) }) {
                _state.update { it.copy(keyword = "", error = null, notice = DUPLICATE) }
                return@launch
            }
            // 카테고리 묶음에 이미 들어 있는 단어는 「차단 키워드」 목록에 따로 뜨지 않는다.
            // 말없이 사라진 것처럼 보이지 않게 어느 쪽으로 갔는지 알려 준다.
            val covered = HarmfulCategories.categoryOf(keyword) != null
            _state.update { it.copy(keyword = "", notice = null) }
            applyKeywords(id, listOf(keyword), enabled = true)
            if (covered && _state.value.error == null) {
                _state.update { it.copy(notice = COVERED_BY_CATEGORY) }
            }
        }
    }

    fun removeKeyword(item: BlocklistItem) {
        viewModelScope.launch {
            val id = requireChildId() ?: return@launch
            applyKeywords(id, listOf(item.pattern), enabled = false)
        }
    }

    /**
     * 키워드 묶음을 블랙리스트에 반영한다.
     *
     * 화면은 먼저 바꿔 놓고(토글이 즉시 반응해야 한다) 서버에는 **바뀐 것만** 보낸다.
     * 하나가 실패해도 나머지는 계속 시도하고, 마지막에 서버 목록으로 다시 맞춘 뒤
     * 요청대로 반영됐는지 확인한다. 2xx 를 받고도 목록에 없으면 조용히 버려진 것이다.
     */
    private suspend fun applyKeywords(id: String, keywords: List<String>, enabled: Boolean) {
        if (keywords.isEmpty()) return
        val before = _state.value.items
        _state.update {
            it.copy(items = it.items.withKeywords(keywords, enabled), saving = true, error = null)
        }

        var failure: Throwable? = null
        keywords.forEach { keyword ->
            val existing = before.firstOrNull {
                it.pattern.equals(keyword, ignoreCase = true)
            }
            val call = when {
                enabled && existing == null -> runCatching { blocklistRepository.add(id, keyword) }
                !enabled && existing != null -> runCatching { blocklistRepository.remove(existing.id) }
                else -> null
            }
            if (failure == null) failure = call?.exceptionOrNull()
        }

        val server = runCatching { blocklistRepository.get(id) }.getOrNull()
        val notPersisted = server != null && keywords.any { keyword ->
            val present = server.any {
                it.pattern.equals(keyword, ignoreCase = true)
            }
            present != enabled
        }
        val problem = failure
        _state.update { current ->
            current.copy(
                saving = false,
                // 목록 조회만 실패했으면 낙관적 반영을 유지한다(쓰기는 성공했을 수 있다).
                items = server ?: current.items,
                error = when {
                    problem != null -> problem.userMessage(SAVE_FAILED)
                    notPersisted -> NOT_PERSISTED
                    else -> null
                },
            )
        }
    }

    private fun List<BlocklistItem>.withKeywords(
        keywords: List<String>,
        enabled: Boolean,
    ): List<BlocklistItem> {
        val stored = this
        return if (enabled) {
            val missing = keywords.filter { keyword ->
                stored.none {
                    it.pattern.equals(keyword, ignoreCase = true)
                }
            }
            stored + missing.map { BlocklistItem(id = it, pattern = it) }
        } else {
            stored.filterNot { item ->
                keywords.any { it.equals(item.pattern, ignoreCase = true) }
            }
        }
    }

    /** 화면 진입 시 자녀 목록을 못 받았을 수 있으므로 쓰기 직전에 한 번 더 시도한다. */
    private suspend fun requireChildId(): String? {
        childId?.let { return it }
        val resolved = runCatching {
            selectedChildRepository.resolveId(childRepository.children())
        }.getOrNull()
        childId = resolved
        if (resolved == null) _state.update { it.copy(error = NO_CHILD) }
        return resolved
    }

    private companion object {
        const val NO_CHILD = "연결된 자녀가 없어 차단 설정을 저장할 수 없어요. 먼저 자녀를 연결해 주세요."
        const val LOAD_FAILED = "차단 목록을 불러오지 못했어요"
        const val SAVE_FAILED = "차단 설정을 저장하지 못했어요"
        const val NOT_PERSISTED = "서버에 저장되지 않았어요. 잠시 후 다시 시도해 주세요."
        const val DUPLICATE = "이미 추가된 키워드예요"
        const val COVERED_BY_CATEGORY = "차단 카테고리에 포함된 키워드예요. 해당 카테고리가 켜집니다."
    }
}

@Composable
fun ParentHarmfulScreen(
    onBack: () -> Unit = {},
    viewModel: HarmfulViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val custom = state.customKeywords

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_harmful_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)
        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚫", fontSize = 18.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.parent_harmful_auto_title),
                    modifier = Modifier.weight(1f),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.danger,
                )
                KidSwitch(
                    state.autoOn,
                    viewModel::onAuto,
                    enabled = !state.saving,
                    trackColor = colors.danger,
                )
            }
            Text(stringResource(R.string.parent_harmful_auto_desc), color = colors.danger, fontSize = 12.sp)
        }
        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.parent_harmful_categories),
                    modifier = Modifier.weight(1f),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                // 저장은 키워드 단위 API 호출이라 즉시 끝나지 않는다. 진행 중임을 알려 준다.
                if (state.saving) {
                    Text(stringResource(R.string.parent_harmful_saving), fontSize = 12.sp, color = colors.muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            HarmfulBundles.forEachIndexed { i, cat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(cat.labelRes),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = if (state.autoOn) colors.text else colors.muted,
                        )
                        Text(
                            stringResource(
                                R.string.parent_harmful_category_count,
                                HarmfulCategories.keywords(cat.id).size,
                            ),
                            fontSize = 11.sp,
                            color = colors.muted,
                        )
                    }
                    KidSwitch(
                        state.categoryOn(cat.id),
                        { viewModel.toggleCategory(cat.id, it) },
                        // 마스터가 꺼져 있으면 카테고리 키워드가 이미 내려간 상태다.
                        enabled = state.autoOn && !state.saving,
                        trackColor = colors.danger,
                    )
                }
                if (i < HarmfulBundles.lastIndex) HorizontalDivider(color = colors.border)
            }
        }
        KidCard {
            Text(stringResource(R.string.parent_harmful_keyword), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.keyword, viewModel::onKeyword, stringResource(R.string.parent_harmful_keyword_hint))
            KidButton(
                stringResource(R.string.parent_harmful_keyword_add),
                viewModel::addKeyword,
                Modifier.fillMaxWidth(),
                enabled = !state.saving,
            )
            state.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = colors.muted)
            }
        }
        if (custom.isNotEmpty()) {
            KidCard {
                Text(stringResource(R.string.parent_harmful_keywords), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                custom.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "• ${item.pattern}",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = colors.text,
                        )
                        Text(
                            stringResource(R.string.parent_harmful_keyword_remove),
                            modifier = Modifier
                                .clickable(enabled = !state.saving) { viewModel.removeKeyword(item) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontBold,
                            color = colors.danger,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
