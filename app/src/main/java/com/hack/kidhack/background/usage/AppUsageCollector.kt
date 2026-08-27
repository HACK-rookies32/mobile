package com.hack.kidhack.background.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 오늘 하루의 앱 사용량을 **기기에서 직접** 읽는다.
 *
 * 홈·마이페이지의 스크린타임과 서비스 현황의 앱 사용 모니터링이 서로 다른 값을 보이면 안 되므로,
 * 세 화면 모두 이 클래스 하나만 호출한다. 스크린타임 = [todayUsage] 합계라는 정의다.
 *
 * 「사용정보 접근」 권한이 없으면 OS 가 빈 목록을 주므로, 그때만 서버(목 모드에서는 더미) 값으로
 * 대체해 화면이 텅 비지 않게 한다. 어느 쪽을 쓰고 있는지는 [Result.fromDevice] 로 알려 준다.
 */
class AppUsageCollector(
    private val context: Context,
    private val appUsageRepository: AppUsageRepository,
    private val sessionRepository: SessionRepository,
) {
    data class Result(
        val usages: List<AppUsage> = emptyList(),
        /** true 면 이 기기에서 실측한 값, false 면 서버/더미 대체값. */
        val fromDevice: Boolean = false,
    ) {
        val totalSeconds: Int get() = usages.sumOf { it.durationSec }
        val totalMinutes: Int get() = totalSeconds / 60
    }

    suspend fun todayUsage(limit: Int = MAX_APPS): Result = withContext(Dispatchers.IO) {
        val local = readDeviceUsage(limit)
        if (local.isNotEmpty()) return@withContext Result(local, fromDevice = true)

        val fallback = runCatching {
            appUsageRepository.list(sessionRepository.childId(), "APP")
        }.getOrDefault(emptyList())
        Result(fallback.sortedByDescending { it.durationSec }.take(limit), fromDevice = false)
    }

    /**
     * `queryAndAggregateUsageStats` 는 패키지별 합계를 준다.
     * 런처에서 열 수 있는 앱만 남겨 시스템 서비스·오버레이 패키지를 걸러 낸다.
     */
    private fun readDeviceUsage(limit: Int): List<AppUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val packageManager = context.packageManager
        val end = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryAndAggregateUsageStats(startOfToday(), end)
        }.getOrNull().orEmpty()

        return stats.values
            .asSequence()
            .filter { it.totalTimeInForeground >= MIN_FOREGROUND_MS }
            .mapNotNull { stat ->
                val info = packageManager.launchableAppInfo(stat.packageName) ?: return@mapNotNull null
                AppUsage(
                    appName = packageManager.getApplicationLabel(info).toString(),
                    durationSec = (stat.totalTimeInForeground / 1000L).toInt(),
                    category = info.categoryLabel(),
                )
            }
            .sortedByDescending { it.durationSec }
            .take(limit)
            .toList()
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun PackageManager.launchableAppInfo(packageName: String): ApplicationInfo? {
        if (getLaunchIntentForPackage(packageName) == null) return null
        return runCatching { getApplicationInfo(packageName, 0) }.getOrNull()
    }

    private fun ApplicationInfo.categoryLabel(): String? =
        ApplicationInfo.getCategoryTitle(context, category)?.toString()

    private companion object {
        const val MAX_APPS = 8

        /** 1초도 안 쓴 앱까지 나열하면 목록이 지저분해진다. */
        const val MIN_FOREGROUND_MS = 1_000L
    }
}
