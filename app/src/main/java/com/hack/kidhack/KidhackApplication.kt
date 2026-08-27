package com.hack.kidhack

import android.app.Application
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.data.local.ParentAlertStore
import com.hack.kidhack.data.local.ProfileScopeStore
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.hack.kidhack.di.appModule
import com.hack.kidhack.di.backgroundModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KidhackApplication : Application() {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
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
            // 로컬 기록은 **계정이 바뀔 때만** 비운다.
            // 로그아웃마다 지우면 같은 자녀가 다시 로그인했을 때 최근 알림·대화가 사라진다.
            val chatStore = koin.get<ChatMessageStore>()
            val alertStore = koin.get<ChildAlertStore>()
            val photoStore = koin.get<ChildPhotoStore>()
            val shotStore = koin.get<CapturedShotStore>()
            val parentAlertStore = koin.get<ParentAlertStore>()
            val profileScope = koin.get<ProfileScopeStore>()
            koin.get<SessionRepository>().session.collect { session ->
                val userId = session?.user?.id ?: return@collect
                if (profileScope.switchTo(userId)) {
                    chatStore.clear()
                    alertStore.clear()
                    photoStore.clear()
                    shotStore.clear()
                    parentAlertStore.clear()
                }
            }
        }
    }
}
