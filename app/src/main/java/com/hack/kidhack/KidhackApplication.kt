package com.hack.kidhack

import android.app.Application
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.domain.repository.SessionEvents
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
            // 로그아웃하면 다음 계정에 이전 대화가 남지 않도록 캐시를 비운다.
            val chatStore = koin.get<ChatMessageStore>()
            val alertStore = koin.get<ChildAlertStore>()
            val photoStore = koin.get<ChildPhotoStore>()
            koin.get<SessionEvents>().logout.collect {
                chatStore.clear()
                alertStore.clear()
                photoStore.clear()
            }
        }
    }
}
