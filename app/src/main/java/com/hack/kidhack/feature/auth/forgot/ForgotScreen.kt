package com.hack.kidhack.feature.auth.forgot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.auth.AuthScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun ForgotScreen(
    onLogin: () -> Unit,
    viewModel: ForgotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    AuthScaffold(
        header = {
            Text("계정 찾기", color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
            Text("아이디 찾기와 비밀번호 재설정", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        },
    ) {
        KidCard {
            Text("📧  아이디 찾기", color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
            Text("가입하신 이름과 휴대폰 번호로 아이디를 찾아드려요", color = colors.muted, fontSize = 12.sp)
        }
        KidTextField(state.findName, viewModel::onFindName, "이름", placeholder = "홍길동")
        KidTextField(
            state.findPhone,
            viewModel::onFindPhone,
            "휴대폰 번호",
            placeholder = "010-0000-0000",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        KidButton(
            text = if (state.findLoading) "찾는 중..." else "아이디 찾기",
            onClick = viewModel::findId,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.findLoading,
        )
        if (state.findInfo != null) {
            Text(state.findInfo.orEmpty(), color = colors.safe, fontSize = 13.sp)
        }
        if (state.findError != null) {
            Text(state.findError.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))

        KidCard {
            Text("🔑  비밀번호 재설정", color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
            Text("아이디와 인증 코드로 새 비밀번호를 설정해요", color = colors.muted, fontSize = 12.sp)
        }
        KidTextField(state.resetLoginId, viewModel::onResetLoginId, "아이디", placeholder = "아이디를 입력하세요")
        KidTextField(state.verifyCode, viewModel::onVerifyCode, "인증 코드", placeholder = "인증 코드")
        KidTextField(
            state.newPassword,
            viewModel::onNewPassword,
            "새 비밀번호",
            placeholder = "8자 이상",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        KidTextField(
            state.newPasswordConfirm,
            viewModel::onNewPasswordConfirm,
            "새 비밀번호 확인",
            placeholder = "비밀번호를 다시 입력하세요",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        KidButton(
            text = if (state.resetLoading) "처리 중..." else "비밀번호 재설정",
            onClick = viewModel::resetPassword,
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Secondary,
            enabled = !state.resetLoading,
        )
        if (state.resetInfo != null) {
            Text(state.resetInfo.orEmpty(), color = colors.safe, fontSize = 13.sp)
        }
        if (state.resetError != null) {
            Text(state.resetError.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }

        Text(
            text = "← 로그인으로 돌아가기",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogin),
            color = colors.brand,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
