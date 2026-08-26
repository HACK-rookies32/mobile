package com.hack.kidhack.feature.parent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.repository.InquiryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class ContactPane { GRID, INQUIRY_LIST, INQUIRY_CREATE, INQUIRY_DETAIL, FAQ }

data class ContactUiState(
    val pane: ContactPane = ContactPane.GRID,
    val inquiries: List<Inquiry> = emptyList(),
    val selectedInquiry: Inquiry? = null,
    val title: String = "",
    val body: String = "",
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val showLinkDialog: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class ContactViewModel(
    private val inquiryRepository: InquiryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    fun open(pane: ContactPane) {
        _state.update { it.copy(pane = pane, error = null) }
        if (pane == ContactPane.INQUIRY_LIST) loadInquiries()
    }

    fun openDetail(inquiry: Inquiry) {
        _state.update { it.copy(pane = ContactPane.INQUIRY_DETAIL, selectedInquiry = inquiry) }
    }

    private fun loadInquiries() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { inquiryRepository.list() }
                .onSuccess { list -> _state.update { it.copy(inquiries = list, loading = false) } }
                .onFailure { t -> _state.update { it.copy(error = t.message, loading = false) } }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }

    fun addImage(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(imageUrls = it.imageUrls + uri.toString()) }
    }

    fun removeImage(url: String) {
        _state.update { it.copy(imageUrls = it.imageUrls - url) }
    }

    fun toggleLinkDialog(show: Boolean) = _state.update { it.copy(showLinkDialog = show) }

    fun addLink(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(links = it.links + url, showLinkDialog = false) }
    }

    fun removeLink(url: String) {
        _state.update { it.copy(links = it.links - url) }
    }

    fun submit() {
        if (_state.value.title.isBlank() || _state.value.body.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                inquiryRepository.create(
                    _state.value.title,
                    _state.value.body,
                    _state.value.imageUrls,
                    _state.value.links
                )
            }.onSuccess {
                _state.update { it.copy(loading = false, title = "", body = "", imageUrls = emptyList(), links = emptyList()) }
                open(ContactPane.INQUIRY_LIST)
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentContactScreen(
    onBack: () -> Unit = {},
    viewModel: ContactViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    var linkInput by remember { mutableStateOf("") }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addImage(uri)
    }

    ParentScreenScaffold(
        header = {
            val title = when (state.pane) {
                ContactPane.INQUIRY_LIST -> "1:1 문의 내역"
                ContactPane.INQUIRY_CREATE -> "1:1 문의하기"
                ContactPane.INQUIRY_DETAIL -> "문의 상세"
                ContactPane.FAQ -> stringResource(R.string.parent_contact_faq)
                else -> stringResource(R.string.parent_contact_title)
            }
            ParentHeaderBar(title) {
                if (state.pane == ContactPane.GRID) {
                    IconButton(onClick = onBack) { Text("✕", color = Color.White) }
                } else {
                    Text(
                        "←",
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                when (state.pane) {
                                    ContactPane.INQUIRY_DETAIL -> viewModel.open(ContactPane.INQUIRY_LIST)
                                    ContactPane.INQUIRY_CREATE -> viewModel.open(ContactPane.INQUIRY_LIST)
                                    else -> viewModel.open(ContactPane.GRID)
                                }
                            }
                            .padding(8.dp),
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        },
    ) {
        when (state.pane) {
            ContactPane.GRID -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContactTile("📋", "1:1 문의 게시판", "문의 내역 및 답변 확인", Modifier.weight(1f)) {
                        viewModel.open(ContactPane.INQUIRY_LIST)
                    }
                    ContactTile("📧", stringResource(R.string.parent_contact_email), stringResource(R.string.parent_contact_email_desc), Modifier.weight(1f)) {
                        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@rookies.example.com"))
                        runCatching { context.startActivity(email) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContactTile("📞", stringResource(R.string.parent_contact_phone), stringResource(R.string.parent_contact_phone_desc), Modifier.weight(1f)) {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:15880000"))
                        runCatching { context.startActivity(dial) }
                    }
                    ContactTile("❓", stringResource(R.string.parent_contact_faq), stringResource(R.string.parent_contact_faq_desc), Modifier.weight(1f)) {
                        viewModel.open(ContactPane.FAQ)
                    }
                }
            }
            ContactPane.INQUIRY_LIST -> {
                KidButton("+ 새로운 문의하기", { viewModel.open(ContactPane.INQUIRY_CREATE) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                if (state.inquiries.isEmpty() && !state.loading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("문의 내역이 없습니다.", color = colors.muted)
                    }
                }
                state.inquiries.forEach { inquiry ->
                    KidCard(Modifier.clickable { viewModel.openDetail(inquiry) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inquiry.title, fontWeight = FontBold, fontSize = 14.sp)
                                Text(inquiry.createdAt, color = colors.muted, fontSize = 12.sp)
                            }
                            val isAnswered = inquiry.status == "ANSWERED"
                            Text(
                                if (isAnswered) "답변완료" else "답변대기",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAnswered) colors.safe.copy(0.1f) else colors.surface)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (isAnswered) colors.safe else colors.muted,
                                fontWeight = FontBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            ContactPane.INQUIRY_CREATE -> {
                KidTextField(state.title, viewModel::onTitle, "문의 제목", placeholder = "제목을 입력하세요")
                KidTextField(
                    value = state.body,
                    onValueChange = viewModel::onBody,
                    label = "문의 내용",
                    placeholder = "문의하실 내용을 상세히 적어주세요",
                    singleLine = false,
                    minLines = 6
                )
                
                Spacer(Modifier.height(16.dp))
                Text("첨부파일", fontWeight = FontBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface).clickable { imageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) { Text("📷") }
                    }
                    items(state.imageUrls) { url ->
                        Box(Modifier.size(60.dp)) {
                            AsyncImage(
                                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                            )
                            IconButton({ viewModel.removeImage(url) }, Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                Text("✕", color = Color.White, fontSize = 10.sp, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape))
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("링크", Modifier.weight(1f), fontSize = 13.sp)
                    Text("추가", Modifier.clickable { viewModel.toggleLinkDialog(true) }, color = colors.brand, fontSize = 13.sp, fontWeight = FontBold)
                }
                state.links.forEach { link ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔗", fontSize = 12.sp)
                        Text(link, Modifier.weight(1f).padding(horizontal = 4.dp), fontSize = 12.sp, maxLines = 1)
                        Text("삭제", Modifier.clickable { viewModel.removeLink(link) }, color = colors.danger, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
                KidButton("문의 제출하기", viewModel::submit, Modifier.fillMaxWidth(), enabled = !state.loading)
            }
            ContactPane.INQUIRY_DETAIL -> {
                state.selectedInquiry?.let { inquiry ->
                    KidCard {
                        Text(inquiry.title, fontWeight = FontBold, fontSize = 16.sp)
                        Text(inquiry.createdAt, color = colors.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(inquiry.body, fontSize = 14.sp)
                        
                        if (inquiry.imageUrls.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(inquiry.imageUrls) { url ->
                                    AsyncImage(model = url, contentDescription = null, Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)))
                                }
                            }
                        }
                    }
                    
                    Text("관리자 답변", Modifier.padding(vertical = 12.dp), fontWeight = FontBold, fontSize = 14.sp)
                    if (inquiry.answers.isEmpty()) {
                        KidCard { Text("답변을 기다리는 중입니다.", color = colors.muted, fontSize = 13.sp) }
                    }
                    inquiry.answers.forEach { ans ->
                        KidCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(ans.author, fontWeight = FontBold, fontSize = 13.sp, color = colors.brand)
                                    Spacer(Modifier.width(8.dp))
                                    Text(ans.createdAt, color = colors.muted, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(ans.body, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            ContactPane.FAQ -> {
                KidCard {
                    Text(stringResource(R.string.parent_contact_faq_q1), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.parent_contact_faq_a1), color = colors.muted, fontSize = 13.sp)
                }
                KidCard {
                    Text(stringResource(R.string.parent_contact_faq_q2), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.parent_contact_faq_a2), color = colors.muted, fontSize = 13.sp)
                }
                KidButton("←", { viewModel.open(ContactPane.GRID) }, Modifier.fillMaxWidth(), com.hack.kidhack.core.designsystem.component.KidButtonVariant.Ghost)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (state.showLinkDialog) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleLinkDialog(false) },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("링크 추가", fontWeight = FontBold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                KidTextField(linkInput, { linkInput = it }, "URL", placeholder = "https://...")
                Spacer(Modifier.height(24.dp))
                KidButton("추가", {
                    viewModel.addLink(linkInput)
                    linkInput = ""
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ContactTile(icon: String, label: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    KidCard(modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text, textAlign = TextAlign.Center)
            Text(desc, color = colors.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
