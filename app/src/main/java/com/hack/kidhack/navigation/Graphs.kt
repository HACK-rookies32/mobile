package com.hack.kidhack.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.forgot.ForgotScreen
import com.hack.kidhack.feature.auth.login.LoginScreen
import com.hack.kidhack.feature.auth.register.RegisterScreen
import com.hack.kidhack.feature.child.camera.ChildCameraScreen
import com.hack.kidhack.feature.child.ChildPairingScreen
import com.hack.kidhack.feature.child.chat.ChildChatScreen
import com.hack.kidhack.feature.child.home.ChildHomeScreen
import com.hack.kidhack.feature.child.mypage.ChildMypageScreen
import com.hack.kidhack.feature.child.mypage.ChildProfileEditScreen
import com.hack.kidhack.feature.child.mypage.ChildTermsScreen
import com.hack.kidhack.feature.child.mypage.ChildNoticeScreen
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingScreen
import com.hack.kidhack.feature.child.pay.ChildPayScreen
import com.hack.kidhack.feature.child.onboarding.ChildStartRoute
import com.hack.kidhack.feature.child.screenshot.ChildScreenshotScreen
import com.hack.kidhack.feature.child.services.ChildServicesScreen
import com.hack.kidhack.feature.parent.ParentAppControlScreen
import com.hack.kidhack.feature.parent.ParentAppUsageDetailScreen
import com.hack.kidhack.feature.parent.ParentCardsScreen
import com.hack.kidhack.feature.parent.ParentChatScreen
import com.hack.kidhack.feature.parent.ParentChildProfileScreen
import com.hack.kidhack.feature.parent.ParentCommunityDetailScreen
import com.hack.kidhack.feature.parent.ParentCommunityScreen
import com.hack.kidhack.feature.parent.ParentCommunityWriteScreen
import com.hack.kidhack.feature.parent.ParentContactScreen
import com.hack.kidhack.feature.parent.ParentDashboardScreen
import com.hack.kidhack.feature.parent.ParentEditProfileScreen
import com.hack.kidhack.feature.parent.ParentGeofenceScreen
import com.hack.kidhack.feature.parent.ParentHarmfulScreen
import com.hack.kidhack.feature.parent.ParentLocationScreen
import com.hack.kidhack.feature.parent.ParentMypageScreen
import com.hack.kidhack.feature.parent.ParentNoticeScreen
import com.hack.kidhack.feature.parent.ParentNotificationsScreen
import com.hack.kidhack.feature.parent.ParentPairingScreen
import com.hack.kidhack.feature.parent.ParentPayScreen
import com.hack.kidhack.feature.parent.ParentCameraRequestScreen
import com.hack.kidhack.feature.parent.ParentScreenshotScreen
import com.hack.kidhack.feature.parent.ParentTermsScreen

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
                onOpenUsageDetail = { navController.navigate(Route.ParentAppUsageDetail.path) },
                onOpenPairing = { navController.navigate(Route.ParentPairing.path) },
                onOpenProfile = { navController.navigate(Route.ParentChildProfile.path) },
            )
        }
        parentTab(Route.ParentLocation.path, navController) {
            ParentLocationScreen(onOpenGeofence = { navController.navigate(Route.ParentGeofence.path) })
        }
        parentTab(Route.ParentChat.path, navController) { ParentChatScreen() }
        parentTab(Route.ParentNotifications.path, navController) { 
            ParentNotificationsScreen(onBack = { navController.popBackStack() }) 
        }
        parentTab(Route.ParentMypage.path, navController) {
            ParentMypageScreen(
                onOpenEditProfile = { navController.navigate(Route.ParentEditProfile.path) },
                onOpenNotifications = { navController.navigate(Route.ParentNotifications.path) },
                onOpenAppControl = { navController.navigate(Route.ParentAppControl.path) },
                onOpenScreenshot = { navController.navigate(Route.ParentScreenshot.path) },
                onOpenCamera = { navController.navigate(Route.ParentCameraRequest.path) },
                onOpenProfile = { navController.navigate(Route.ParentChildProfile.path) },
                onOpenHarmful = { navController.navigate(Route.ParentHarmful.path) },
                onOpenPairing = { navController.navigate(Route.ParentPairing.path) },
                onOpenCommunity = { navController.navigate(Route.ParentCommunity.path) },
                onOpenNotice = { navController.navigate(Route.ParentNotice.path) },
                onOpenContact = { navController.navigate(Route.ParentContact.path) },
                onOpenPay = { navController.navigate(Route.ParentPay.path) },
                onOpenCards = { navController.navigate(Route.ParentCards.path) },
                onOpenTerms = { navController.navigate(Route.ParentTerms.path) },
            )
        }
        composable(Route.ParentEditProfile.path) { ParentShell(navController) { ParentEditProfileScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentAppControl.path) { ParentShell(navController) { ParentAppControlScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentScreenshot.path) { ParentShell(navController) { ParentScreenshotScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentCameraRequest.path) { ParentShell(navController) { ParentCameraRequestScreen(onBack = { navController.popBackStack() }) } }
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
        composable(Route.ParentTerms.path) { ParentShell(navController) { ParentTermsScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentPay.path) {
            ParentShell(navController) {
                ParentPayScreen(
                    onBack = { navController.popBackStack() },
                    onRegisterCard = { navController.navigate(Route.ParentCards.path) },
                )
            }
        }
        composable(Route.ParentCards.path) { ParentShell(navController) { ParentCardsScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentGeofence.path) { ParentShell(navController) { ParentGeofenceScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentAppUsageDetail.path) { ParentShell(navController) { ParentAppUsageDetailScreen(onBack = { navController.popBackStack() }) } }
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
                onCamera = { navController.navigateChildTab(Route.ChildCamera.path) },
            )
        }
        childTab(Route.ChildChat.path, navController) { ChildChatScreen() }
        childTab(Route.ChildServices.path, navController) { ChildServicesScreen() }
        composable(
            route = Route.ChildCameraRequest.path,
            arguments = listOf(
                navArgument(ARG_REQUEST_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_OPEN_GALLERY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            ChildShell(navController) {
                ChildCameraScreen(
                    requestId = entry.arguments?.getString(ARG_REQUEST_ID),
                    openGallery = entry.arguments?.getBoolean(ARG_OPEN_GALLERY) == true,
                )
            }
        }
        childTab(Route.ChildMypage.path, navController) {
            ChildMypageScreen(
                onServices = { navController.navigateChildTab(Route.ChildServices.path) },
                onLinkParent = { navController.navigate(Route.ChildPairing.path) },
                onScreenshot = { navController.navigate(Route.ChildScreenshot.path) },
                onCamera = { navController.navigateChildGallery() },
                onPay = { navController.navigate(Route.ChildPay.path) },
                onEditProfile = { navController.navigate(Route.ChildProfileEdit.path) },
                onTerms = { navController.navigate(Route.ChildTerms.path) },
                onNotice = { navController.navigate(Route.ChildNotice.path) },
            )
        }
        composable(Route.ChildScreenshot.path) {
            ChildShell(navController) {
                ChildScreenshotScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildPairing.path) {
            ChildShell(navController) {
                ChildPairingScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildPay.path) {
            ChildShell(navController) {
                ChildPayScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildProfileEdit.path) {
            ChildShell(navController) {
                ChildProfileEditScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildTerms.path) {
            ChildShell(navController) {
                ChildTermsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildNotice.path) {
            ChildShell(navController) {
                ChildNoticeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

internal const val ARG_REQUEST_ID = "requestId"
internal const val ARG_OPEN_GALLERY = "gallery"

/**
 * 마이페이지 「카메라 갤러리」 → 카메라 탭의 갤러리 화면.
 *
 * 일반 navigate 로 쌓으면 마이페이지 탭 스택이 `[마이페이지, 갤러리]` 가 되고,
 * 하단 「마이」를 눌렀을 때 그 스택이 통째로 복원돼 갤러리로 되돌아간다.
 * 그래서 탭 전환과 같은 방식으로 이동한다. 다만 `gallery=true` 인자를 살려야 하므로
 * 이전 카메라 상태는 복원하지 않는다.
 */
private fun NavHostController.navigateChildGallery() {
    navigate(Route.ChildCameraRequest.gallery()) {
        popUpTo(Route.ChildGraph.path) { saveState = true }
        launchSingleTop = true
        restoreState = false
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
    // 카메라 탭은 `child/camera?requestId={requestId}` 패턴으로 등록돼 있어 쿼리부를 떼고 비교한다.
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore('?')
    val showBar = tabs.any { it.route.path == currentRoute }
    Scaffold(
        // 상단 인셋은 BrandGradientHeader가 statusBarsPadding()으로 직접 처리한다.
        // 여기서 함께 넘기면 상태바 높이가 두 번 쌓여 헤더 제목이 아래로 밀린다.
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = colors.card, contentColor = colors.muted) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route.path
                        // 카메라 탭만 예외: 마이페이지 「카메라 갤러리」로 들어간 적이 있으면
                        // 저장된 상태(gallery=true)가 복원돼 탭을 눌러도 갤러리가 뜬다.
                        // 탭은 언제나 카메라로 시작해야 하므로 인자를 명시하고 복원을 끈다.
                        val isCameraTab = tab.route.path == Route.ChildCamera.path
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = if (isCameraTab) {
                                    Route.ChildCameraRequest.camera()
                                } else {
                                    tab.route.path
                                }
                                navController.navigate(target) {
                                    popUpTo(graphRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = !isCameraTab
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
