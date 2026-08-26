package com.hack.kidhack.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.context.GlobalContext

@Composable
fun KidhackNavHost() {
    val koin = GlobalContext.get()
    val sessionRepository = koin.get<SessionRepository>()
    val sessionEvents = koin.get<SessionEvents>()
    val commandBus = koin.get<ChildCommandBus>()
    val navController = rememberNavController()
    val colors = LocalKidhackColors.current

    fun goAuthed(session: Session) {
        val dest = if (session.user.role == Role.PARENT) Route.ParentGraph.path else Route.ChildGraph.path
        navController.navigate(dest) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // 부모 요청 알림 탭 → 해당 화면으로 이동. 화면 캡처는 알림이 동의 액티비티로 바로 보내므로
    // 여기서는 카메라 촬영 요청만 처리한다.
    LaunchedEffect(Unit) {
        commandBus.pending.collect { command ->
            when (command.type) {
                RemoteCommandType.CAMERA_SHOT -> {
                    navController.navigate(Route.ChildCameraRequest.create(command.requestId)) {
                        launchSingleTop = true
                    }
                    commandBus.consume()
                }
                else -> commandBus.consume()
            }
        }
    }

    LaunchedEffect(Unit) {
        sessionEvents.logout.collect {
            navController.navigate(Route.Login.path) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Splash.path,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        composable(Route.Splash.path) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.brand),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            }
            LaunchedEffect(Unit) {
                val session = sessionRepository.current()
                when (session?.user?.role) {
                    Role.PARENT -> navController.navigate(Route.ParentGraph.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                    Role.CHILD -> navController.navigate(Route.ChildGraph.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                    null -> navController.navigate(Route.Login.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                }
            }
        }
        authGraph(navController, ::goAuthed)
        parentGraph(navController)
        childGraph(navController)
    }
}
