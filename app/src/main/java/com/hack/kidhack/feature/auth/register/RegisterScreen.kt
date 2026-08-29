package com.hack.kidhack.feature.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidDateField
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.common.PhoneNumberVisualTransformation
import com.hack.kidhack.core.designsystem.component.StepProgressBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.AuthScaffold
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingViewModel
import com.hack.kidhack.feature.child.onboarding.ChildPermissionWizard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RegisterScreen(
    onRegistered: (Session) -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val role = state.role
    val permissionVm: ChildOnboardingViewModel = koinViewModel { parametersOf(false) }

    val onPermissionsStep = role == Role.CHILD && state.step == 3 && state.session == null
    val onDoneStep = state.session != null && state.step == state.doneStep

    LaunchedEffect(state.error, state.session, state.step) {
        if (onPermissionsStep && state.error != null && !state.loading) {
            permissionVm.resetFinished()
        }
    }

    if (onPermissionsStep) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChildPermissionWizard(
                onFinished = { viewModel.submit() },
                titleRes = R.string.child_register_permissions_title,
                subtitleRes = R.string.child_register_permissions_subtitle,
                continueRes = R.string.child_register_permissions_continue,
                persistDone = false,
                viewModel = permissionVm,
            )
            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (state.error != null) {
                Text(
                    text = state.error.orEmpty(),
                    color = colors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
        return
    }

    AuthScaffold(
        header = {
            Text("회원가입", color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
            Text("루키즈 가족이 되어보세요", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            if (role != null) {
                Spacer(Modifier.height(16.dp))
                val total = if (role == Role.CHILD) 4 else 3
                val current = if (role == Role.CHILD) {
                    (state.step - 1).coerceIn(0, total - 1)
                } else {
                    state.step.coerceAtMost(total - 1)
                }
                StepProgressBar(totalSteps = total, currentStep = current)
            }
        },
    ) {
        when {
            state.step == 0 -> RoleStep(
                onParent = { viewModel.selectRole(Role.PARENT) },
                onChild = { viewModel.selectRole(Role.CHILD) },
                onLogin = onLogin,
            )
            state.step == 1 && role != null -> InfoStep(
                badge = if (role == Role.PARENT) "🛡️ 부모님으로 가입" else "😊 자녀로 가입",
                nicknameLabel = "닉네임",
                nicknamePlaceholder = if (role == Role.PARENT) "홍길동" else "나의 이름",
                emailRequired = role != Role.CHILD,
                state = state,
                viewModel = viewModel,
            )
            state.step == 2 -> TermsStep(state = state, viewModel = viewModel)
            onDoneStep -> DoneStep(
                session = state.session!!,
                loading = state.loading,
                onStart = { viewModel.startApp(onRegistered) },
            )
        }
        // 에러는 각 단계의 버튼 바로 위(StepError)에서 보여 준다.
        // 예전에는 여기, 즉 버튼보다 아래에 그려서 폼이 길면 화면 밖으로 밀려 보이지 않았다.
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
    state: RegisterUiState,
    viewModel: RegisterViewModel,
) {
    val colors = LocalKidhackColors.current
    Text(badge, color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
    KidTextField(state.loginId, viewModel::onLoginId, "아이디", placeholder = "로그인에 사용할 아이디")
    KidTextField(state.realName, viewModel::onRealName, "이름", placeholder = "이름을 입력하세요")
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
        visualTransformation = PhoneNumberVisualTransformation(),
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
    // 부모 연결은 가입이 아니라 로그인 후 페어링 코드로 한다(POST /pairing/connect).
    // 예전에는 여기서 「부모님 회원 번호」를 필수로 받았는데, 그 값은 부모 계정의 DB id 라
    // 자녀가 알 방법이 없었다. 서버도 signup 의 parentId 는 선택으로 두고 검증하지 않는다.
    StepError(state.error)
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
    KidCard {
        TermsCheckRow(
            checked = state.agreedAll,
            label = "모든 약관에 동의합니다",
            emphasized = true,
            onCheckedChange = viewModel::onAgreedAll,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.border)
        TermsCheckRow(
            checked = state.agreedAge,
            label = "[필수] 만 14세 이상입니다.",
            onCheckedChange = viewModel::onAgreedAge,
        )
        TermsCheckRow(
            checked = state.agreedTerms,
            label = "[필수] 서비스 이용약관 동의",
            onCheckedChange = viewModel::onAgreedTerms,
        )
        TermsCheckRow(
            checked = state.agreedPrivacy,
            label = "[필수] 개인정보 수집 및 이용 동의",
            onCheckedChange = viewModel::onAgreedPrivacy,
        )
        TermsCheckRow(
            checked = state.agreedLocation,
            label = "[필수] 위치기반 서비스 이용약관 동의",
            onCheckedChange = viewModel::onAgreedLocation,
        )
        TermsCheckRow(
            checked = state.agreedPush,
            label = "[필수] PUSH 알림 수신 동의",
            onCheckedChange = viewModel::onAgreedPush,
        )
        TermsCheckRow(
            checked = state.agreedMarketing,
            label = "[선택] 마케팅 활용 동의",
            onCheckedChange = viewModel::onAgreedMarketing,
        )
    }
    val isChild = state.role == Role.CHILD
    StepError(state.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        KidButton(
            text = when {
                state.loading -> "처리 중..."
                isChild -> "다음 →"
                else -> "가입하기"
            },
            onClick = viewModel::nextFromTerms,
            modifier = Modifier.weight(1f),
            enabled = !state.loading,
        )
    }
}

@Composable
private fun TermsCheckRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    emphasized: Boolean = false,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.brand),
        )
        Text(
            text = label,
            color = if (emphasized) colors.text else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = if (emphasized) FontBold else FontSemibold,
            fontSize = if (emphasized) 15.sp else 12.sp,
        )
    }
}

@Composable
private fun DoneStep(session: Session, loading: Boolean, onStart: () -> Unit) {
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
        Text("아이디  ${session.user.loginId}", color = colors.muted, fontSize = 12.sp)
        if (session.user.name.isNotBlank()) {
            Text("이름  ${session.user.name}", color = colors.muted, fontSize = 12.sp)
        }
    }
    KidButton(
        text = when {
            loading -> "로그인 중..."
            parent -> "부모님 앱 시작하기 →"
            else -> "자녀 앱 시작하기 →"
        },
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
    )
}

/** 단계별 에러 문구. 버튼 바로 위에 두어 스크롤 없이 보이게 한다. */
@Composable
private fun StepError(error: String?) {
    if (error == null) return
    Text(error, color = LocalKidhackColors.current.danger, fontSize = 13.sp)
}
