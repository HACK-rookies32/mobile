package com.hack.kidhack.feature.child.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.navigation.Route
import org.koin.core.context.GlobalContext

@Composable
fun ChildStartRoute(navController: NavHostController) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.brand),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
    LaunchedEffect(Unit) {
        val store = GlobalContext.get().get<ChildOnboardingStore>()
        val ready = store.isDone() && KidhackPermissions.hasAllRequiredForChildControl(context)
        val dest = if (ready) Route.ChildHome.path else Route.ChildOnboarding.path
        navController.navigate(dest) {
            popUpTo(Route.ChildStart.path) { inclusive = true }
        }
    }
}

@Composable
fun ChildOnboardingScreen(
    onFinished: () -> Unit,
) {
    ChildPermissionWizard(
        onFinished = onFinished,
        persistDone = true,
    )
}
