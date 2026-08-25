package com.hack.kidhack.feature.parent

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
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
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
    val keywords: List<String>,
)

private val HarmfulBundles = listOf(
    HarmfulCategory("adult", R.string.parent_harmful_adult, listOf("성인")),
    HarmfulCategory("gambling", R.string.parent_harmful_gambling, listOf("gambling.com")),
    HarmfulCategory("violence", R.string.parent_harmful_violence, listOf("폭력")),
    HarmfulCategory("drugs", R.string.parent_harmful_drugs, listOf("마약")),
)

data class HarmfulUiState(
    val items: List<BlocklistItem> = emptyList(),
    val autoOn: Boolean = true,
    val keyword: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class HarmfulViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val blocklistRepository: BlocklistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HarmfulUiState())
    val state: StateFlow<HarmfulUiState> = _state.asStateFlow()

    init { load() }

    fun onKeyword(v: String) = _state.update { it.copy(keyword = v) }
    fun onAuto(v: Boolean) = _state.update { it.copy(autoOn = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching emptyList()
                blocklistRepository.get(id)
            }.onSuccess { items ->
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun toggleCategory(category: HarmfulCategory, enabled: Boolean) {
        viewModelScope.launch {
            val id = selectedChildRepository.current() ?: return@launch
            runCatching {
                if (enabled) {
                    category.keywords.forEach { kw ->
                        if (_state.value.items.none { it.keyword.equals(kw, true) && it.listType == "BLACK" }) {
                            blocklistRepository.add(id, "BLACK", kw)
                        }
                    }
                } else {
                    _state.value.items.filter { it.listType == "BLACK" && it.keyword in category.keywords }.forEach {
                        blocklistRepository.remove(id, it.id)
                    }
                }
                blocklistRepository.get(id)
            }.onSuccess { items ->
                _state.update { it.copy(items = items, error = null) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun addKeyword() {
        val kw = _state.value.keyword.trim()
        if (kw.isBlank()) return
        viewModelScope.launch {
            val id = selectedChildRepository.current() ?: return@launch
            runCatching {
                blocklistRepository.add(id, "BLACK", kw)
                blocklistRepository.get(id)
            }.onSuccess { items ->
                _state.update { it.copy(items = items, keyword = "") }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun categoryOn(category: HarmfulCategory): Boolean =
        category.keywords.all { kw -> _state.value.items.any { it.listType == "BLACK" && it.keyword.equals(kw, true) } }
}

@Composable
fun ParentHarmfulScreen(
    onBack: () -> Unit = {},
    viewModel: HarmfulViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val black = state.items.filter { it.listType == "BLACK" }

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
                KidSwitch(state.autoOn, viewModel::onAuto, trackColor = colors.danger)
            }
            Text(stringResource(R.string.parent_harmful_auto_desc), color = colors.danger, fontSize = 12.sp)
        }
        KidCard {
            Text(stringResource(R.string.parent_harmful_categories), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            HarmfulBundles.forEachIndexed { i, cat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(cat.labelRes), modifier = Modifier.weight(1f), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    KidSwitch(viewModel.categoryOn(cat), { viewModel.toggleCategory(cat, it) }, trackColor = colors.danger)
                }
                if (i < HarmfulBundles.lastIndex) HorizontalDivider(color = colors.border)
            }
        }
        KidCard {
            Text(stringResource(R.string.parent_harmful_keyword), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.keyword, viewModel::onKeyword, stringResource(R.string.parent_harmful_keyword_hint))
            KidButton(stringResource(R.string.parent_harmful_keyword_add), viewModel::addKeyword, Modifier.fillMaxWidth())
        }
        if (black.isNotEmpty()) {
            KidCard {
                Text(stringResource(R.string.parent_harmful_keywords), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                black.forEach { item ->
                    Text("• ${item.keyword}", modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = colors.text)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
