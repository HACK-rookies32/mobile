package com.hack.kidhack.feature.auth.register

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.hack.kidhack.core.designsystem.component.KidDateField
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.component.StepProgressBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.AuthScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun RegisterScreen(
    onRegistered: (Session) -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val role = state.role

    AuthScaffold(
        header = {
            Text("회원가입", color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
            Text("루키즈 가족이 되어보세요", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            if (role != null) {
                Spacer(Modifier.height(16.dp))
                StepProgressBar(totalSteps = 3, currentStep = state.step.coerceAtMost(2))
            }
        },
    ) {
        when {
            state.step == 0 -> RoleStep(onParent = { viewModel.selectRole(Role.PARENT) }, onChild = { viewModel.selectRole(Role.CHILD) }, onLogin = onLogin)
            state.step == 1 && role != null -> InfoStep(
                badge = if (role == Role.PARENT) "🛡️ 부모님으로 가입" else "😊 자녀로 가입",
                nicknameLabel = if (role == Role.PARENT) "닉네임 (선택)" else "닉네임 (선택)",
                nicknamePlaceholder = if (role == Role.PARENT) "홍길동" else "나의 이름",
                emailRequired = role != Role.CHILD,
                showParentId = role == Role.CHILD,
                state = state,
                viewModel = viewModel,
            )
            state.step == 2 -> TermsStep(state = state, viewModel = viewModel)
            state.step == 3 && state.session != null -> DoneStep(session = state.session!!, onStart = { onRegistered(state.session!!) })
        }
        if (state.error != null) {
            Text(state.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RoleStep(onParent: () -> Unit, onChild: () -> Unit, onLogin: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("어떤 분이세요?", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp, color = colors.text)
        Text("역할에 맞게 앱이 설정돼요", color = colors.muted, fontSize = 14.sp)
    }
    RoleCard("🛡️", "부모님", "자녀 위치·앱·유해사이트 모니터링,\n채팅, 루키즈 페이 등을 사용해요", onParent)
    RoleCard("😊", "자녀", "부모님과 채팅, 내 보호 서비스 현황\n확인, 카메라 사용 등을 해요", onChild)
    Text(
        "이미 계정이 있으신가요? 로그인",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLogin),
        textAlign = TextAlign.Center,
        color = colors.muted,
        fontSize = 14.sp,
    )
}

@Composable
private fun RoleCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 28.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 18.sp, color = colors.text)
            Text(desc, color = colors.muted, fontSize = 12.sp)
        }
        Text("›", color = colors.border, fontSize = 20.sp)
    }
}

@Composable
private fun InfoStep(
    badge: String,
    nicknameLabel: String,
    nicknamePlaceholder: String,
    emailRequired: Boolean,
    showParentId: Boolean,
    state: RegisterUiState,
    viewModel: RegisterViewModel,
) {
    val colors = LocalKidhackColors.current
    Text(badge, color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
    KidTextField(state.username, viewModel::onUsername, "아이디", placeholder = "로그인에 사용할 아이디")
    KidTextField(
        state.password,
        viewModel::onPassword,
        "비밀번호",
        placeholder = "8자 이상",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    KidTextField(
        state.passwordConfirm,
        viewModel::onPasswordConfirm,
        "비밀번호 확인",
        placeholder = "비밀번호를 다시 입력하세요",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    KidTextField(state.nickname, viewModel::onNickname, nicknameLabel, placeholder = nicknamePlaceholder)
    KidTextField(
        state.phoneNumber,
        viewModel::onPhoneNumber,
        "휴대폰 번호",
        placeholder = "010-0000-0000",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    KidTextField(
        state.email,
        viewModel::onEmail,
        if (emailRequired) "이메일" else "이메일 (선택)",
        placeholder = "example@email.com",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    KidDateField(
        value = state.birthDate,
        onValueChange = viewModel::onBirthDate,
        label = "생년월일",
        placeholder = "달력에서 선택하세요",
    )
    KidTextField(state.address, viewModel::onAddress, "주소 (선택)", placeholder = "서울시 강남구")
    if (showParentId) {
        KidCard {
            Text("👪  부모님 연결", color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
            Text("부모님이 가입할 때 발급된 회원 번호를 입력해요", color = colors.muted, fontSize = 12.sp)
        }
        KidTextField(
            state.parentId,
            viewModel::onParentId,
            "부모님 회원 번호",
            placeholder = "예: 1001",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        KidButton("다음 →", viewModel::nextFromInfo, Modifier.weight(1f))
    }
}

@Composable
private fun TermsStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    val colors = LocalKidhackColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("약관 동의", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp, color = colors.text)
        Text("가입을 완료하려면 약관에 동의해 주세요", color = colors.muted, fontSize = 14.sp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable { viewModel.onAgreed(!state.agreed) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.agreed,
            onCheckedChange = viewModel::onAgreed,
            colors = CheckboxDefaults.colors(checkedColor = colors.brand),
        )
        Text("[필수] 서비스 이용약관 및 개인정보처리방침에 동의합니다", color = colors.muted, fontSize = 12.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        KidButton(
            text = if (state.loading) "처리 중..." else "가입하기",
            onClick = viewModel::submit,
            modifier = Modifier.weight(1f),
            enabled = !state.loading,
        )
    }
}

@Composable
private fun DoneStep(session: Session, onStart: () -> Unit) {
    val colors = LocalKidhackColors.current
    val parent = session.user.role == Role.PARENT
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (parent) "🛡️" else "😊", fontSize = 40.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("가입 완료! 🎉", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
        Text(
            if (parent) "루키즈 부모님 앱을 시작할게요" else "루키즈 자녀 앱을 시작할게요",
            color = colors.muted,
            fontSize = 14.sp,
        )
    }
    KidCard {
        Text(if (parent) "부모님 계정" else "자녀 계정", fontWeight = FontBold)
        Text(session.user.loginId, color = colors.muted, fontSize = 12.sp)
    }
    KidButton(
        text = if (parent) "부모님 앱 시작하기 →" else "자녀 앱 시작하기 →",
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
    )
}
