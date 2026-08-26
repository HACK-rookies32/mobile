package com.hack.kidhack.di

import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backgroundModule = module {
    single { ChildCommandBus() }
    single { ChildDeviceStatus(androidContext()) }
    single { AppUsageCollector(androidContext(), get(), get()) }
    single { ScreenTimeWatcher(get()) }
    single { ChildCommandNotifier(androidContext()) }
    single { GpsStatusMonitor(androidContext(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get()) }
}
