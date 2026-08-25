package com.hack.kidhack.background.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import com.hack.kidhack.R
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Instant

/**
 * Accessibility-based keyword/URL matcher (REQ-M-11).
 *
 * Apply order: blacklist hit, then whitelist override. Overlay if
 * [Settings.canDrawOverlays], otherwise a Toast. Reports via
 * `POST /harmful-access/report`.
 */
class HarmfulBlockAccessibilityService : AccessibilityService() {

    private val blocklistRepository: BlocklistRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var items: List<BlocklistItem> = emptyList()
    private var lastMatch: String? = null
    private var lastMatchAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { refreshBlocklist() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val blob = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            event.source?.text?.let { append(it) }
        }
        if (blob.isBlank()) return
        val matched = shouldBlock(blob, items) ?: return
        val now = System.currentTimeMillis()
        if (matched == lastMatch && now - lastMatchAt < THROTTLE_MS) return
        lastMatch = matched
        lastMatchAt = now
        showBlock(matched)
        scope.launch {
            runCatching {
                blocklistRepository.reportHarmfulAccess(matched, Instant.now().toString())
            }
        }
    }

    override fun onInterrupt() = Unit

    private suspend fun refreshBlocklist() {
        val childId = sessionRepository.current()?.user?.id ?: return
        items = runCatching { blocklistRepository.get(childId) }.getOrDefault(emptyList())
    }

    private fun showBlock(keyword: String) {
        val message = getString(R.string.harmful_block_toast, keyword)
        if (Settings.canDrawOverlays(this)) {
            showOverlay(message)
        } else {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showOverlay(message: String) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        val view = TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(32, 48, 32, 48)
            setBackgroundColor(0xCCDC2626.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        runCatching { wm.addView(view, params) }
        view.postDelayed({ runCatching { wm.removeView(view) } }, 2500)
    }

    companion object {
        private const val THROTTLE_MS = 10_000L

        fun shouldBlock(text: String, items: List<BlocklistItem>): String? {
            val black = items.filter { it.listType.equals("BLACK", ignoreCase = true) }
            val white = items.filter { it.listType.equals("WHITE", ignoreCase = true) }
            val hit = black.firstOrNull { it.keyword.isNotBlank() && text.contains(it.keyword, ignoreCase = true) }
                ?: return null
            if (white.any { it.keyword.isNotBlank() && text.contains(it.keyword, ignoreCase = true) }) {
                return null
            }
            return hit.keyword
        }
    }
}
