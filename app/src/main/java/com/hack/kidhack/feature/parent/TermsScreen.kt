package com.hack.kidhack.feature.parent

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hack.kidhack.R
import com.hack.kidhack.feature.common.TermsBody

/**
 * 마이페이지 › 이용약관 (부모).
 *
 * 본문은 자녀 화면과 공유한다([TermsBody]). 여기서는 부모 스캐폴드만 씌운다.
 */
@Composable
fun ParentTermsScreen(onBack: () -> Unit = {}) {
    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_terms_title), onBack) },
    ) {
        TermsBody()
    }
}
