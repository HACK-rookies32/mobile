package com.hack.kidhack.feature.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.AuthScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoggedIn: (Session) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    LaunchedEffect(state.session) {
        state.session?.let(onLoggedIn)
    }

    AuthScaffold(
        header = {
            Text("ROOKIES", color = Color.White.copy(alpha = 0.7f), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("안녕하세요 👋", color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 30.sp)
            Text("우리 아이 안전, 루키즈가 지켜드려요", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(4.dp),
        ) {
            RoleChip("🛡️", "부모님", state.role == Role.PARENT, Modifier.weight(1f)) { viewModel.onRole(Role.PARENT) }
            RoleChip("😊", "자녀", state.role == Role.CHILD, Modifier.weight(1f)) { viewModel.onRole(Role.CHILD) }
        }
        KidTextField(
            value = state.loginId,
            onValueChange = viewModel::onLoginId,
            label = "아이디",
            placeholder = "아이디를 입력하세요",
        )
        KidTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = "비밀번호",
            placeholder = "비밀번호를 입력하세요",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text(
            text = "비밀번호를 잊으셨나요?",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onForgot),
            color = colors.brand,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
        if (state.error != null) {
            Text(state.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }
        KidButton(
            text = if (state.loading) "로그인 중..." else "로그인",
            onClick = viewModel::login,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(colors.border))
            Text("  또는  ", color = colors.muted, fontSize = 12.sp, fontWeight = FontBold)
            Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        }
        Text(
            text = "계정이 없으신가요? 회원가입",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRegister),
            color = colors.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "회원가입",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRegister),
            color = colors.brand,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoleChip(
    emoji: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.brand else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$emoji  $label",
            color = if (selected) Color.White else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
        )
    }
}
