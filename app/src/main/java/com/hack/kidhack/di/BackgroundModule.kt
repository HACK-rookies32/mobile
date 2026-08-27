package com.hack.kidhack.di

import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.background.alert.ParentAlertBus
import com.hack.kidhack.background.alert.ParentAlertNotifier
import com.hack.kidhack.background.alert.ParentAlertSync
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.background.geofence.GeofenceRegistrar
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.location.LocationQueue
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backgroundModule = module {
    single { LocationQueue(androidContext()) }
    single { ChildCommandBus() }
    single { ChildDeviceStatus(androidContext()) }
    single { AppUsageCollector(androidContext(), get(), get()) }
    single { ScreenTimeWatcher(get()) }
    single { ChildCommandNotifier(androidContext()) }
    single { GpsStatusMonitor(androidContext(), get()) }
    single { GeofenceRegistrar(androidContext(), get(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get(), get()) }
    single { ParentAlertBus() }
    single { ParentAlertNotifier(androidContext()) }
    single { ParentAlertSync(get(), get(), get(), get(), get()) }
    single { ParentPlatformController(androidContext(), get()) }
}
