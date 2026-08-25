package com.hack.kidhack.feature.parent

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel

enum class ContactPane { GRID, CHAT, FAQ }

data class ContactUiState(
    val pane: ContactPane = ContactPane.GRID,
    val title: String = "",
    val body: String = "",
    val submitted: Boolean = false,
)

class ContactViewModel : ViewModel() {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()
    fun open(pane: ContactPane) = _state.update { it.copy(pane = pane, submitted = false) }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }
    fun submit() {
        if (_state.value.title.isBlank() || _state.value.body.isBlank()) return
        _state.update { it.copy(submitted = true, title = "", body = "") }
    }
}

@Composable
fun ParentContactScreen(
    onBack: () -> Unit = {},
    viewModel: ContactViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_contact_title), onBack) },
    ) {
        when (state.pane) {
            ContactPane.GRID -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContactTile("💬", stringResource(R.string.parent_contact_chat), stringResource(R.string.parent_contact_chat_desc), Modifier.weight(1f)) {
                        viewModel.open(ContactPane.CHAT)
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
            ContactPane.CHAT -> {
                KidTextField(state.title, viewModel::onTitle, stringResource(R.string.parent_contact_inquiry_title))
                KidTextField(state.body, viewModel::onBody, stringResource(R.string.parent_contact_inquiry_body))
                KidButton(stringResource(R.string.parent_contact_submit), viewModel::submit, Modifier.fillMaxWidth())
                if (state.submitted) {
                    Text(stringResource(R.string.parent_contact_submitted), color = colors.safe, fontSize = 13.sp)
                }
                KidButton("←", { viewModel.open(ContactPane.GRID) }, Modifier.fillMaxWidth(), com.hack.kidhack.core.designsystem.component.KidButtonVariant.Ghost)
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
