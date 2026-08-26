package com.hack.kidhack.feature.child.mypage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 마이페이지 › 프로필 수정.
 *
 * 저장에 성공하면 곧바로 마이페이지로 돌아간다. 마이페이지는 복귀할 때 다시 읽으므로
 * 바뀐 이름·이메일이 바로 반영된다.
 */
@Composable
fun ChildProfileEditScreen(
    onBack: () -> Unit = {},
    viewModel: ChildProfileEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_profile_back),
                modifier = Modifier.clickable(onClick = onBack),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_profile_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_profile_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        KidCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("😊", fontSize = 32.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.child_profile_photo_note),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            KidTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = stringResource(R.string.child_profile_name),
                placeholder = stringResource(R.string.child_profile_name_hint),
                enabled = !state.loading && !state.saving,
                isError = state.nameError != null,
                supportingText = state.nameError,
            )
            Spacer(Modifier.height(12.dp))
            KidTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.child_profile_phone),
                placeholder = stringResource(R.string.child_profile_phone_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !state.loading && !state.saving,
            )
            Spacer(Modifier.height(12.dp))
            KidTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.child_profile_email),
                placeholder = stringResource(R.string.child_profile_email_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !state.loading && !state.saving,
                isError = state.emailError != null,
                supportingText = state.emailError ?: stringResource(R.string.child_profile_email_optional),
            )
        }

        KidButton(
            text = stringResource(
                if (state.saving) R.string.child_profile_saving else R.string.child_profile_save,
            ),
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
        )
        KidButton(
            text = stringResource(R.string.child_profile_cancel),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Ghost,
        )
        Spacer(Modifier.height(8.dp))
    }
}
