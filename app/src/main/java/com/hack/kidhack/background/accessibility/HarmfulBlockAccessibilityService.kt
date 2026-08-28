package com.hack.kidhack.background.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import com.hack.kidhack.R
<<<<<<< HEAD
<<<<<<< HEAD
=======
import com.hack.kidhack.background.ChildAlertUploadWorker
>>>>>>> 28ecca4 (알림 기능 수정 2차)
=======
import com.hack.kidhack.background.ChildAlertUploadWorker
>>>>>>> 3c24953b345a846397da744d9d2a9170b7a27e57
import com.hack.kidhack.core.common.HarmfulKeywordMatcher
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Instant

/**
 * 접근성 기반 유해 페이지·키워드 차단 (REQ-M-11).
 *
 * 판정 순서는 블랙리스트 적중 → 화이트리스트 예외다. 적중하면
 *  1. 홈으로 내보내 **실제로 페이지에서 빠져나오고**
 *  2. 오버레이(권한 없으면 토스트)로 자녀에게 알린 뒤
 *  3. `POST /harmful-access/report` 로 보호자에게 보고한다.
 *
 * 검사 대상은 이벤트 텍스트 + 화면 노드 트리다. 크롬 계열 브라우저는 주소창이
 * 별도 노드라 이벤트 텍스트만 봐서는 URL 을 놓친다.
 */
class HarmfulBlockAccessibilityService : AccessibilityService() {

    private val blocklistRepository: BlocklistRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val alertStore: ChildAlertStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var items: List<BlocklistItem> = emptyList()

    @Volatile
    private var blocklistLoadedAt: Long = 0L

    private var lastMatch: String? = null
    private var lastMatchAt: Long = 0L

    @Volatile
    private var lastScanAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { refreshBlocklist(force = true) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 검사 대상이 아닌 앱은 즉시 버린다.
        // 특히 우리 앱은 차단 키워드 목록 자체를 화면에 보여 주므로(「성인·음란물 차단」 등)
        // 제외하지 않으면 자기 UI 를 유해 콘텐츠로 오탐해 스스로를 튕겨 낸다.
        val source = event.packageName?.toString().orEmpty()
        if (source.isBlank() || isExempt(source)) return

        // typeWindowContentChanged 는 초당 수십 번 올 수 있다. 전체 노드 트리 스캔은 비싸므로
        // 최소 간격을 둔다.
        val nowScan = System.currentTimeMillis()
        if (nowScan - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = nowScan

        // 보호자가 방금 추가한 키워드도 곧 반영되도록 주기적으로 다시 읽는다.
        if (System.currentTimeMillis() - blocklistLoadedAt > BLOCKLIST_TTL_MS) {
            scope.launch { refreshBlocklist(force = false) }
        }
        val snapshot = items
        if (snapshot.isEmpty()) return

        val haystack = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            append(collectWindowText())
        }
        if (haystack.isBlank()) return

        val matched = shouldBlock(haystack, snapshot) ?: return
        val now = System.currentTimeMillis()
        if (matched == lastMatch && now - lastMatchAt < THROTTLE_MS) return
        lastMatch = matched
        lastMatchAt = now

        // 알리기만 하면 자녀는 그대로 페이지에 머문다. 실제로 빠져나오게 한다.
        //
        // BACK 은 신뢰할 수 없다. 새 탭으로 연 페이지는 돌아갈 히스토리가 없고,
        // 열려 있는 다이얼로그(쿠키 배너 등)가 먼저 먹어 버리며, 히스토리에 따라
        // 같은 유해 페이지로 되돌아갈 수도 있다. HOME 은 상황과 무관하게 확실히 벗어난다.
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlock(matched)
        scope.launch {
            // 홈 탭 「최근 알림」에 뜨도록 기기에도 남긴다.
            // 서버 보고만 하면 서버가 알림을 되돌려 줄 때까지 자녀 화면에는 아무것도 안 보인다.
            alertStore.record(
                type = ChildAlertStore.TYPE_HARMFUL_BLOCK,
                title = "유해 사이트 접속이 차단됐어요",
                body = matched,
                dedupeKey = ChildAlertStore.TYPE_HARMFUL_BLOCK + "-" + matched,
            )
            runCatching {
                blocklistRepository.reportHarmfulAccess(matched, Instant.now().toString())
            }
            // 보호자가 다음 폴링에서 바로 받도록 방금 남긴 기록을 즉시 올린다.
            ChildAlertUploadWorker.enqueueNow(applicationContext)
        }
    }

    /**
     * 자기 자신과 시스템 셸은 검사하지 않는다.
     *
     * 런처·시스템 UI·설정 화면의 텍스트까지 훑으면 오탐이 쏟아지고,
     * 정작 막아야 할 웹/앱 콘텐츠와 무관하다.
     */
    private fun isExempt(packageName: String): Boolean =
        packageName == applicationContext.packageName || packageName in EXEMPT_PACKAGES

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 현재 창의 노드 트리를 훑어 주소창·본문 텍스트를 모은다. */
    private fun collectWindowText(): String {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return ""
        val builder = StringBuilder()
        collectInto(root, builder, 0)
        return builder.toString()
    }

    private fun collectInto(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > MAX_NODE_DEPTH || out.length > MAX_TEXT_LENGTH) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectInto(node.getChild(i), out, depth + 1)
        }
    }

    private suspend fun refreshBlocklist(force: Boolean) {
        if (!force && System.currentTimeMillis() - blocklistLoadedAt <= BLOCKLIST_TTL_MS) return
        blocklistLoadedAt = System.currentTimeMillis()
        val childId = sessionRepository.childId()
        if (childId.isBlank()) return
        runCatching { blocklistRepository.get(childId) }
            .onSuccess { items = it }
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
        view.postDelayed({ runCatching { wm.removeView(view) } }, OVERLAY_MS)
    }

    companion object {
        private const val THROTTLE_MS = 10_000L
        private const val SCAN_INTERVAL_MS = 800L

        private val EXEMPT_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.google.android.inputmethod.latin",
            "com.android.permissioncontroller",
        )
        private const val BLOCKLIST_TTL_MS = 5 * 60 * 1000L
        private const val OVERLAY_MS = 2_500L
        private const val MAX_NODE_DEPTH = 12
        private const val MAX_TEXT_LENGTH = 4_000

        /**
         * 판정 순서는 블랙리스트 적중 → 화이트리스트 예외다.
         *
         * 단순 `contains` 로 보면 영단어 키워드가 오탐을 낸다(`meth` → `method`).
         * 매칭 규칙은 [HarmfulKeywordMatcher] 가 갖고 있다.
         */
        fun shouldBlock(text: String, items: List<BlocklistItem>): String? {
            val black = items.filter { it.listType.equals("BLACK", ignoreCase = true) }
            val white = items.filter { it.listType.equals("WHITE", ignoreCase = true) }
            val hit = black.firstOrNull { HarmfulKeywordMatcher.matches(text, it.keyword) }
                ?: return null
            if (white.any { HarmfulKeywordMatcher.matches(text, it.keyword) }) {
                return null
            }
            return hit.keyword
        }
    }
}
