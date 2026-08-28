package com.hack.kidhack.di

import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.background.alert.ParentAlertBus
import com.hack.kidhack.background.alert.ParentAlertNotifier
import com.hack.kidhack.background.alert.ParentAlertSync
import com.hack.kidhack.background.alert.ParentChildWatcher
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.background.push.PushMessageRouter
import com.hack.kidhack.background.push.PushTokenRegistrar
<<<<<<< HEAD
import com.hack.kidhack.background.geofence.GeofenceRegistrar
=======
>>>>>>> 3c24953b345a846397da744d9d2a9170b7a27e57
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backgroundModule = module {
    single { ChildCommandBus() }
    single { ChildDeviceStatus(androidContext()) }
    single { AppUsageCollector(androidContext(), get(), get()) }
    single { ScreenTimeWatcher(get(), androidContext()) }
    single { ChildCommandNotifier(androidContext()) }
    single { PushTokenRegistrar(get(), get(), get()) }
    single { PushMessageRouter(androidContext(), get(), get(), get(), get(), get(), get()) }
<<<<<<< HEAD
    single { GpsStatusMonitor(androidContext(), get(), get()) }
    single { GeofenceRegistrar(androidContext(), get(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get(), get()) }
=======
    single { GpsStatusMonitor(androidContext(), get(), get(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get()) }
>>>>>>> 3c24953b345a846397da744d9d2a9170b7a27e57
    single { ParentAlertBus() }
    single { ParentAlertNotifier(androidContext()) }
    single { ParentAlertSync(get(), get(), get(), get(), get()) }
    single { ParentChildWatcher(get(), get(), get(), get(), get(), get(), get()) }
    single { ParentPlatformController(androidContext(), get()) }
}
