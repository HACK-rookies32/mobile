package com.hack.kidhack

import android.app.Application
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.push.PushTokenRegistrar
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.data.local.ProfileScopeStore
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.hack.kidhack.di.appModule
import com.hack.kidhack.di.backgroundModule
import com.hack.kidhack.security.DefenseChecker
import com.hack.kidhack.security.DefenseState
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KidhackApplication : Application() {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // [보안 방어] 가장 이른 시점에 네이티브 방어 검사를 돌린다. 여기서 libdefense.so 가
        // 로드되고, 루팅/Frida/디버거/무결성 결과가 DefenseState 에 담긴다. Application 은
        // 화면이 없어 다이얼로그를 못 띄우므로, 실제 경고·종료는 MainActivity 루트의
        // StartupDefenseGate 가 이 결과를 읽어 처리한다. (검사 시점 ↔ 표시 시점 분리)
        DefenseState.startupResult = DefenseChecker.runAllChecks(this)
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@KidhackApplication)
            modules(appModule, backgroundModule)
        }
        // ChildPlatformController 를 꺼내는 순간 SessionRepository(DataStore 디스크 읽기)와
        // OkHttp/Retrofit 이 함께 생성된다. 메인 스레드에서 하면 첫 프레임이 그만큼 늦어져
        // 실행 직후 검은 화면이 길어지므로 백그라운드로 넘긴다.
        startupScope.launch {
            val koin = GlobalContext.get()
            koin.get<ChildPlatformController>().bind()
            // 부모 세션은 15분 주기로 서버 알림을 긁어와 보관함에 넣고 푸시로 띄운다.
            koin.get<ParentPlatformController>().bind()
            // FCM 토큰을 로그인한 계정에 묶는다. 세션이 생기면 등록, 사라지면 해제.
            val pushRegistrar = koin.get<PushTokenRegistrar>()
            // 채팅과 사진은 계정이 바뀔 때 비운다.
            // 알림 보관함(ChildAlertStore/ServerAlertStore)은 계정별 파일로 나뉘어 있어
            // 여기서 지우지 않는다. 지우면 부모와 자녀를 오갈 때마다 최근 알림이 비워진다.
            val chatStore = koin.get<ChatMessageStore>()
            val photoStore = koin.get<ChildPhotoStore>()
            val shotStore = koin.get<CapturedShotStore>()
            val profileScope = koin.get<ProfileScopeStore>()
            var pushBoundTo: String? = null
            koin.get<SessionRepository>().session.collect { session ->
                val userId = session?.user?.id
                if (userId == null) {
                    if (pushBoundTo != null) { pushRegistrar.onSignedOut(); pushBoundTo = null }
                    return@collect
                }
                if (pushBoundTo != userId) { pushRegistrar.onSignedIn(); pushBoundTo = userId }
                if (profileScope.switchTo(userId)) {
                    chatStore.clear()
                    photoStore.clear()
                    shotStore.clear()
                }
            }
        }
    }
}
