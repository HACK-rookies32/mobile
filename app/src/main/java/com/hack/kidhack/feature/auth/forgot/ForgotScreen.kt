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

/**
 * 계정 찾기 화면.
 *
 * 서버가 **이메일 인증코드 2단계**(발송 → 검증)를 요구해서 두 섹션 모두 같은 구조다.
 * 코드를 받기 전에는 검증 입력이 보이지 않는다.
 */
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
            Text(
                "가입하신 이름과 이메일로 인증코드를 보내 드려요. 자녀 계정은 부모님 이메일을 넣어 주세요",
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        KidTextField(state.findName, viewModel::onFindName, "이름", placeholder = "홍길동")
        KidTextField(
            state.findEmail,
            viewModel::onFindEmail,
            "이메일",
            placeholder = "parent01@test.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        KidButton(
            text = if (state.findSendLoading) "보내는 중..." else "인증코드 받기",
            onClick = viewModel::sendFindIdCode,
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Secondary,
            enabled = !state.findSendLoading,
        )
        if (state.findCodeSent) {
            KidTextField(
                state.findCode,
                viewModel::onFindCode,
                "인증 코드",
                placeholder = "6자리 숫자",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            KidButton(
                text = if (state.findLoading) "확인 중..." else "아이디 찾기",
                onClick = viewModel::verifyFindId,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.findLoading,
            )
        }
        if (state.findInfo != null) {
            Text(state.findInfo.orEmpty(), color = colors.safe, fontSize = 13.sp)
        }
        if (state.findError != null) {
            Text(state.findError.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))

        KidCard {
            Text("🔑  비밀번호 재설정", color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
            Text("아이디와 이메일로 인증코드를 받은 뒤 새 비밀번호를 설정해요", color = colors.muted, fontSize = 12.sp)
        }
        KidTextField(state.resetLoginId, viewModel::onResetLoginId, "아이디", placeholder = "아이디를 입력하세요")
        KidTextField(
            state.resetEmail,
            viewModel::onResetEmail,
            "이메일",
            placeholder = "parent01@test.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        KidButton(
            text = if (state.resetSendLoading) "보내는 중..." else "인증코드 받기",
            onClick = viewModel::sendResetPasswordCode,
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Secondary,
            enabled = !state.resetSendLoading,
        )
        if (state.resetCodeSent) {
            KidTextField(
                state.verifyCode,
                viewModel::onVerifyCode,
                "인증 코드",
                placeholder = "6자리 숫자",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
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
                enabled = !state.resetLoading,
            )
        }
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
