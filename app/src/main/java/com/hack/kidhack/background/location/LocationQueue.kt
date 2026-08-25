package com.hack.kidhack.background.location

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.remote.dto.LocationDto
import com.hack.kidhack.domain.model.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Offline location queue without Room/KSP (AGP 9). Persists as JSON in app files.
 */
class LocationQueue(context: Context) {
    private val file = File(context.filesDir, "location_queue.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance

    suspend fun enqueue(point: LocationPoint) = mutex.withLock {
        val items = readLocked().toMutableList()
        items += LocationDto(point.lat, point.lng, point.recordedAt, point.label)
        writeLocked(items)
    }

    suspend fun drain(): List<LocationPoint> = mutex.withLock {
        val items = readLocked()
        writeLocked(emptyList())
        items.map { LocationPoint(it.lat, it.lng, it.recordedAt, it.label) }
    }

    suspend fun restore(points: List<LocationPoint>) = mutex.withLock {
        if (points.isEmpty()) return@withLock
        val items = readLocked().toMutableList()
        items += points.map { LocationDto(it.lat, it.lng, it.recordedAt, it.label) }
        writeLocked(items)
    }

    private suspend fun readLocked(): List<LocationDto> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(LocationDto.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeLocked(items: List<LocationDto>) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(ListSerializer(LocationDto.serializer()), items))
    }
}
