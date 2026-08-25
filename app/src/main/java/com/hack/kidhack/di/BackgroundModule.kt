package com.hack.kidhack.di

import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.geofence.GeofenceRegistrar
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.location.LocationQueue
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backgroundModule = module {
    single { LocationQueue(androidContext()) }
    single { GpsStatusMonitor(androidContext(), get()) }
    single { GeofenceRegistrar(androidContext(), get(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get(), get()) }
}
