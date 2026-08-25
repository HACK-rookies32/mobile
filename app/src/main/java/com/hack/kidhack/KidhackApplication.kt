package com.hack.kidhack

import android.app.Application
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.di.appModule
import com.hack.kidhack.di.backgroundModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KidhackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@KidhackApplication)
            modules(appModule, backgroundModule)
        }
        GlobalContext.get().get<ChildPlatformController>().bind()
    }
}
