package com.hack.kidhack.feature.child.mypage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.feature.common.TermsBody

/**
 * 마이페이지 › 이용약관 (자녀).
 *
 * 본문은 부모 화면과 공유한다([TermsBody]). 여기서는 자녀 스캐폴드만 씌운다.
 */
@Composable
fun ChildTermsScreen(onBack: () -> Unit = {}) {
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
                text = stringResource(R.string.child_terms_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
        },
    ) {
        TermsBody()
    }
}
