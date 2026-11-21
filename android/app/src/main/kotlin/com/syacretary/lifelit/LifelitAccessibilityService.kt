package com.syacretary.lifelit

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.syacretary.lifelit.core.AppFeatureHandler
import com.syacretary.lifelit.features.instagram.InstagramHandler
import com.syacretary.lifelit.features.tiktok.TikTokHandler
import com.syacretary.lifelit.features.twitter.TwitterHandler
import com.syacretary.lifelit.features.youtube.YouTubeHandler

/**
 * LifelitAccessibilityService
 *
 * Lightweight, modular accessibility dispatcher.
 * Coordinates feature handlers (Instagram, YouTube, TikTok, Twitter)
 * with zero-flicker debounced evaluation and robust lifecycle management.
 */
class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private var pendingHideRunnable: Runnable? = null
    private var pendingEvalRunnable: Runnable? = null

    private lateinit var featureHandlers: List<AppFeatureHandler>

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"

        private val activeRules = mutableMapOf<String, MutableSet<String>>()

        /**
         * Transient system packages that must NOT cause overlays to hide.
         * Includes heads-up notification popups, quick settings, volume sliders,
         * ColorOS/Realme Smart Sidebar, Samsung Edge Panel, system dialogs, and keyboards.
         */
        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.oplus.smartsidebar",
            "com.coloros.smartsidebar",
            "com.oplus.sidebar",
            "com.coloros.floatlet",
            "com.oplus.screenshot",
            "com.coloros.screenrecorder",
            "com.oplus.safecenter",
            "com.oplus.sublauncher",
            "com.oplus.gesture",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.sidebarservice",
            "com.miui.freeform",
            "com.miui.notification",
            "com.huawei.android.internal.app",
            "android",
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "com.samsung.android.honeyboard"
        )

        fun isServiceRunning(): Boolean = instance != null

        fun syncRules(context: Context, rules: Map<String, List<String>>) {
            activeRules.clear()
            rules.forEach { (appId, featureList) ->
                activeRules[appId] = featureList.toMutableSet()
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val flattened = rules.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }
            prefs.edit().putString(KEY_RULES, flattened).apply()
        }

        fun loadRulesFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RULES, null) ?: return
            activeRules.clear()
            raw.split(";").forEach { item ->
                val parts = item.split(":")
                if (parts.size == 2) {
                    val appId = parts[0]
                    val feats = parts[1].split(",").filter { it.isNotBlank() }
                    activeRules[appId] = feats.toMutableSet()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = LifelitOverlayManager(this)
        featureHandlers = listOf(
            InstagramHandler(),
            YouTubeHandler(),
            TikTokHandler(),
            TwitterHandler()
        )
        loadRulesFromPrefs(this)
    }

    override fun onDestroy() {
        cancelPendingHide()
        cancelPendingEval()
        overlayManager?.hideFeedBlockOverlay()
        overlayManager = null
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        cancelPendingHide()
        cancelPendingEval()
        overlayManager?.hideFeedBlockOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // 1. Ignore our own overlay window
        if (pkg.contains("com.syacretary.lifelit")) return

        // 2. Ignore transient system overlays (smart edge/sidebar, heads-up notifications, volume slider, keyboard).
        // This prevents users from dismissing the overlay by triggering smart edge panels or notifications.
        val isTransient = TRANSIENT_SYSTEM_PACKAGES.any { pkg.contains(it, ignoreCase = true) }
        if (isTransient) {
            return
        }

        val handler = findHandlerForPackage(pkg)

        // 3. If genuinely exiting to another user app (e.g. Launcher, Settings, WhatsApp), hide overlay
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (handler == null) {
                hideOverlayImmediate()
                return
            }
        }

        if (handler == null) return

        // 4. Fast redirect check for Shorts / Reels / Stories / Home
        val root = rootInActiveWindow
        if (root != null) {
            val rules = getRulesForPackage(pkg)
            if (rules.isNotEmpty()) {
                val handled = handler.onFastRedirect(this, root, event, rules)
                if (handled) {
                    hideOverlayImmediate()
                    return
                }
            }
        }

        // 5. Debounced layout and state evaluation
        scheduleEvaluation(100L)
    }

    private fun findHandlerForPackage(pkg: String): AppFeatureHandler? {
        if (!::featureHandlers.isInitialized) return null
        return featureHandlers.firstOrNull { h ->
            h.supportedPackages.any { pkg.contains(it) || it.contains(pkg) }
        }
    }

    private fun getRulesForPackage(pkg: String): Set<String> {
        val appKey = when {
            pkg.contains("instagram") -> "instagram"
            pkg.contains("youtube") -> "youtube"
            pkg.contains("musically") || pkg.contains("trill") -> "tiktok"
            pkg.contains("twitter") -> "twitter"
            else -> ""
        }
        return activeRules[appKey] ?: emptySet()
    }

    private fun scheduleEvaluation(delayMs: Long) {
        cancelPendingEval()
        val r = Runnable {
            evaluateCurrentState()
        }
        pendingEvalRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelPendingEval() {
        pendingEvalRunnable?.let {
            handler.removeCallbacks(it)
            pendingEvalRunnable = null
        }
    }

    private fun cancelPendingHide() {
        pendingHideRunnable?.let {
            handler.removeCallbacks(it)
            pendingHideRunnable = null
        }
    }

    private fun scheduleHideOverlay(delayMs: Long = 350L) {
        if (pendingHideRunnable != null) return
        val r = Runnable {
            overlayManager?.hideFeedBlockOverlay()
            pendingHideRunnable = null
        }
        pendingHideRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun hideOverlayImmediate() {
        cancelPendingHide()
        cancelPendingEval()
        overlayManager?.hideFeedBlockOverlay()
    }

    private fun evaluateCurrentState() {
        val root = getActiveManagedRoot() ?: return
        val activePkg = root.packageName?.toString() ?: return
        val handler = findHandlerForPackage(activePkg) ?: run {
            hideOverlayImmediate()
            return
        }

        val rules = getRulesForPackage(activePkg)
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        val om = overlayManager ?: return
        handler.evaluateState(
            service = this,
            root = root,
            rules = rules,
            overlayManager = om,
            scheduleHide = { delayMs -> scheduleHideOverlay(delayMs) },
            hideImmediate = { hideOverlayImmediate() }
        )
    }

    /**
     * Resolves the active root node for managed apps while remaining immune
     * to transient system windows (smart sidebar, floating tools, notification popups).
     */
    private fun getActiveManagedRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""

        // If the focused window belongs to a managed app, use it directly
        if (findHandlerForPackage(pkg) != null) {
            return root
        }

        // If the focused window is a transient system element (smart edge, heads-up notification, etc.)
        if (TRANSIENT_SYSTEM_PACKAGES.any { pkg.contains(it, ignoreCase = true) }) {
            try {
                val windowList = windows
                for (w in windowList) {
                    val wRoot = w.root ?: continue
                    val wPkg = wRoot.packageName?.toString() ?: ""
                    if (findHandlerForPackage(wPkg) != null) {
                        return wRoot
                    }
                }
            } catch (e: Exception) {
                return null
            }
            // Transient window is active and managed app is underneath: return null so overlay remains intact!
            return null
        }

        return root
    }
}
