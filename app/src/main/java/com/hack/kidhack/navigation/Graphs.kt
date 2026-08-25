package com.hack.kidhack.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.forgot.ForgotScreen
import com.hack.kidhack.feature.auth.login.LoginScreen
import com.hack.kidhack.feature.auth.register.RegisterScreen
import com.hack.kidhack.feature.child.camera.ChildCameraScreen
import com.hack.kidhack.feature.child.chat.ChildChatScreen
import com.hack.kidhack.feature.child.home.ChildHomeScreen
import com.hack.kidhack.feature.child.mypage.ChildMypageScreen
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingScreen
import com.hack.kidhack.feature.child.onboarding.ChildStartRoute
import com.hack.kidhack.feature.child.screenshot.ChildScreenshotScreen
import com.hack.kidhack.feature.child.services.ChildServicesScreen
import com.hack.kidhack.feature.parent.ParentAppControlScreen
import com.hack.kidhack.feature.parent.ParentChatScreen
import com.hack.kidhack.feature.parent.ParentChildProfileScreen
import com.hack.kidhack.feature.parent.ParentCommunityDetailScreen
import com.hack.kidhack.feature.parent.ParentCommunityScreen
import com.hack.kidhack.feature.parent.ParentCommunityWriteScreen
import com.hack.kidhack.feature.parent.ParentContactScreen
import com.hack.kidhack.feature.parent.ParentDashboardScreen
import com.hack.kidhack.feature.parent.ParentGeofenceScreen
import com.hack.kidhack.feature.parent.ParentHarmfulScreen
import com.hack.kidhack.feature.parent.ParentLocationScreen
import com.hack.kidhack.feature.parent.ParentMypageScreen
import com.hack.kidhack.feature.parent.ParentNoticeScreen
import com.hack.kidhack.feature.parent.ParentNotificationsScreen
import com.hack.kidhack.feature.parent.ParentPairingScreen
import com.hack.kidhack.feature.parent.ParentPayScreen
import com.hack.kidhack.feature.parent.ParentScreenshotScreen

fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    onAuthed: (Session) -> Unit,
) {
    composable(Route.Login.path) {
        LoginScreen(
            onLoggedIn = onAuthed,
            onRegister = { navController.navigate(Route.Register.path) },
            onForgot = { navController.navigate(Route.Forgot.path) },
        )
    }
    composable(Route.Register.path) {
        RegisterScreen(
            onRegistered = onAuthed,
            onLogin = { navController.popBackStack() },
        )
    }
    composable(Route.Forgot.path) {
        ForgotScreen(onLogin = { navController.popBackStack() })
    }
}

fun NavGraphBuilder.parentGraph(navController: NavHostController) {
    navigation(startDestination = Route.ParentDashboard.path, route = Route.ParentGraph.path) {
        parentTab(Route.ParentDashboard.path, navController) {
            ParentDashboardScreen(
                onOpenNotifications = { navController.navigate(Route.ParentNotifications.path) },
                onOpenAppControl = { navController.navigate(Route.ParentAppControl.path) },
                onOpenPairing = { navController.navigate(Route.ParentPairing.path) },
                onOpenProfile = { navController.navigate(Route.ParentChildProfile.path) },
            )
        }
        parentTab(Route.ParentLocation.path, navController) {
            ParentLocationScreen(onOpenGeofence = { navController.navigate(Route.ParentGeofence.path) })
        }
        parentTab(Route.ParentChat.path, navController) { ParentChatScreen() }
        parentTab(Route.ParentNotifications.path, navController) { ParentNotificationsScreen() }
        parentTab(Route.ParentMypage.path, navController) {
            ParentMypageScreen(
                onOpenNotifications = { navController.navigate(Route.ParentNotifications.path) },
                onOpenAppControl = { navController.navigate(Route.ParentAppControl.path) },
                onOpenScreenshot = { navController.navigate(Route.ParentScreenshot.path) },
                onOpenProfile = { navController.navigate(Route.ParentChildProfile.path) },
                onOpenHarmful = { navController.navigate(Route.ParentHarmful.path) },
                onOpenPairing = { navController.navigate(Route.ParentPairing.path) },
                onOpenCommunity = { navController.navigate(Route.ParentCommunity.path) },
                onOpenNotice = { navController.navigate(Route.ParentNotice.path) },
                onOpenContact = { navController.navigate(Route.ParentContact.path) },
                onOpenPay = { navController.navigate(Route.ParentPay.path) },
            )
        }
        composable(Route.ParentAppControl.path) { ParentShell(navController) { ParentAppControlScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentScreenshot.path) { ParentShell(navController) { ParentScreenshotScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentChildProfile.path) { ParentShell(navController) { ParentChildProfileScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentHarmful.path) { ParentShell(navController) { ParentHarmfulScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentPairing.path) {
            ParentShell(navController) {
                ParentPairingScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(Route.ParentDashboard.path) {
                            popUpTo(Route.ParentGraph.path)
                        }
                    },
                )
            }
        }
        composable(Route.ParentCommunity.path) {
            ParentShell(navController) {
                ParentCommunityScreen(
                    onOpenPost = { navController.navigate(Route.ParentCommunityDetail.create(it)) },
                    onWrite = { navController.navigate(Route.ParentCommunityWrite.path) },
                )
            }
        }
        composable(Route.ParentCommunityDetail.path) { entry ->
            val postId = entry.arguments?.getString("postId").orEmpty()
            ParentShell(navController) {
                ParentCommunityDetailScreen(postId = postId, onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ParentCommunityWrite.path) {
            ParentShell(navController) {
                ParentCommunityWriteScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(Route.ParentNotice.path) { ParentShell(navController) { ParentNoticeScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentContact.path) { ParentShell(navController) { ParentContactScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentPay.path) { ParentShell(navController) { ParentPayScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentGeofence.path) { ParentShell(navController) { ParentGeofenceScreen(onBack = { navController.popBackStack() }) } }
    }
}

fun NavGraphBuilder.childGraph(navController: NavHostController) {
    navigation(startDestination = Route.ChildStart.path, route = Route.ChildGraph.path) {
        composable(Route.ChildStart.path) { ChildStartRoute(navController) }
        composable(Route.ChildOnboarding.path) {
            ChildOnboardingScreen(
                onFinished = {
                    navController.navigate(Route.ChildHome.path) {
                        popUpTo(Route.ChildOnboarding.path) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        childTab(Route.ChildHome.path, navController) {
            ChildHomeScreen(
                onChat = { navController.navigateChildTab(Route.ChildChat.path) },
                onServices = { navController.navigateChildTab(Route.ChildServices.path) },
                onMypage = { navController.navigateChildTab(Route.ChildMypage.path) },
                onScreenshot = { navController.navigate(Route.ChildScreenshot.path) },
            )
        }
        childTab(Route.ChildChat.path, navController) { ChildChatScreen() }
        childTab(Route.ChildServices.path, navController) { ChildServicesScreen() }
        childTab(Route.ChildCamera.path, navController) { ChildCameraScreen() }
        childTab(Route.ChildMypage.path, navController) {
            ChildMypageScreen(
                onServices = { navController.navigateChildTab(Route.ChildServices.path) },
                onScreenshot = { navController.navigate(Route.ChildScreenshot.path) },
                onCamera = { navController.navigateChildTab(Route.ChildCamera.path) },
            )
        }
        composable(Route.ChildScreenshot.path) {
            ChildShell(navController) {
                ChildScreenshotScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateChildTab(route: String) {
    navigate(route) {
        popUpTo(Route.ChildGraph.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.parentTab(
    route: String,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    composable(route) { ParentShell(navController, content) }
}

private fun NavGraphBuilder.childTab(
    route: String,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    composable(route) { ChildShell(navController, content) }
}

@Composable
private fun ParentShell(navController: NavHostController, content: @Composable () -> Unit) {
    TabShell(
        navController = navController,
        tabs = ParentTabs,
        graphRoute = Route.ParentGraph.path,
        content = content,
    )
}

@Composable
private fun ChildShell(navController: NavHostController, content: @Composable () -> Unit) {
    TabShell(
        navController = navController,
        tabs = ChildTabs,
        graphRoute = Route.ChildGraph.path,
        content = content,
    )
}

@Composable
private fun TabShell(
    navController: NavHostController,
    tabs: List<TabItem>,
    graphRoute: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBar = tabs.any { it.route.path == currentRoute }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = colors.card, contentColor = colors.muted) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route.path
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route.path) {
                                    popUpTo(graphRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.emoji, fontSize = 18.sp) },
                            label = {
                                Text(
                                    tab.label,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 10.sp,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.brand,
                                selectedTextColor = colors.brand,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                                indicatorColor = colors.brandLight,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}
