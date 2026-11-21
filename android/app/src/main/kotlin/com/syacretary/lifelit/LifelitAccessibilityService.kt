package com.syacretary.lifelit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * LifelitAccessibilityService
 *
 * Provides real-time smart feature blocking (Shorts, Reels, FYP, and Feeds)
 * with robust hysteresis and zero-flicker debouncing.
 */
class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastNavTimestamp = 0L
    private var currentForegroundPackage: String = ""

    private var pendingHideRunnable: Runnable? = null
    private var pendingEvalRunnable: Runnable? = null

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"

        private const val NAV_COOLDOWN_MS = 600L

        private val MANAGED_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.twitter.android"
        )

        private val activeRules = mutableMapOf<String, MutableSet<String>>()

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

        // Ignore our own overlay window
        if (pkg.contains("com.syacretary.lifelit")) return

        // 1. App exit check on TYPE_WINDOW_STATE_CHANGED
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isManagedApp = MANAGED_PACKAGES.any { pkg.contains(it) || it.contains(pkg) }
            if (!isManagedApp) {
                // User genuinely navigated to an external app (launcher, settings, chat, etc.)
                currentForegroundPackage = pkg
                hideOverlayImmediate()
                return
            }
            currentForegroundPackage = pkg
        }

        // 2. Ignore background/unrelated events
        if (!MANAGED_PACKAGES.any { pkg.contains(it) || it.contains(pkg) }) {
            return
        }

        // 3. Fast redirect check for Shorts / Reels / FYP
        checkFastRedirects(pkg, event)

        // 4. Debounced evaluation for feed overlay
        scheduleEvaluation(100L)
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

    // =========================================================================
    // Fast Redirects: Shorts, Reels, Stories
    // =========================================================================

    private fun checkFastRedirects(pkg: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return

        if (pkg.contains("youtube")) {
            val rules = activeRules["youtube"] ?: return
            if (rules.contains("disable_shorts") && isYouTubeShorts(root, event)) {
                lastNavTimestamp = now
                hideOverlayImmediate()
                navigateToHome("com.google.android.youtube")
            }
        } else if (pkg.contains("instagram")) {
            val rules = activeRules["instagram"] ?: return
            if ((rules.contains("disable_reels") || rules.contains("dm_profile_only") || rules.contains("disable_friend_reels"))
                && isInstagramReels(root, event)) {
                lastNavTimestamp = now
                hideOverlayImmediate()
                navigateToHome("com.instagram.android")
            } else if ((rules.contains("hide_stories") || rules.contains("dm_profile_only"))
                && isInstagramStories(root, event)) {
                lastNavTimestamp = now
                hideOverlayImmediate()
                navigateToHome("com.instagram.android")
            }
        } else if (pkg.contains("zhiliaoapp.musically") || pkg.contains("ugc.trill")) {
            val rules = activeRules["tiktok"] ?: return
            if (rules.contains("hide_stories") && isTikTokStory(root, event)) {
                lastNavTimestamp = now
                hideOverlayImmediate()
                navigateToHome("com.zhiliaoapp.musically")
            }
        }
    }

    // =========================================================================
    // Debounced State Evaluation (Home Feed vs Search vs Other Tabs)
    // =========================================================================

    private fun evaluateCurrentState() {
        val root = rootInActiveWindow ?: return
        val activePkg = root.packageName?.toString() ?: return

        when {
            activePkg.contains("youtube") -> evaluateYouTube(root)
            activePkg.contains("instagram") -> evaluateInstagram(root)
            activePkg.contains("zhiliaoapp.musically") || activePkg.contains("ugc.trill") -> evaluateTikTok(root)
            activePkg.contains("twitter") -> evaluateTwitter(root)
            else -> {
                hideOverlayImmediate()
            }
        }
    }

    // ── 1. YouTube Evaluator ─────────────────────────────────────────────────
    private fun evaluateYouTube(root: AccessibilityNodeInfo) {
        val rules = activeRules["youtube"] ?: emptySet()

        if (rules.contains("disable_home")) {
            val isHome = isYouTubeHomeTab(root)
            val isSearching = isSearchActive(root, listOf("search_edit_text", "search_bar", "query"))
            val isWatching = isYouTubeWatchPage(root)

            if (isHome && !isSearching && !isWatching) {
                cancelPendingHide()
                overlayManager?.showFeedBlockOverlay(
                    appName = "YouTube",
                    title = "Beranda YouTube Dibatasi",
                    subtitle = "Gunakan bilah pencarian di atas untuk menonton video yang Anda cari.",
                    isDark = true
                )
            } else {
                scheduleHideOverlay(350L)
            }
        } else {
            hideOverlayImmediate()
        }
    }

    private fun isYouTubeHomeTab(root: AccessibilityNodeInfo): Boolean {
        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                    val p = n.parent
                    if (p != null && (p.isSelected || p.isChecked)) return true
                }
            }
        }
        return false
    }

    private fun isYouTubeWatchPage(root: AccessibilityNodeInfo): Boolean {
        val watchIds = listOf(
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/watch_panel",
            "com.google.android.youtube:id/player_fragment_container",
            "com.google.android.youtube:id/player_control_play_pause_replay_button"
        )
        for (id in watchIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }

    private fun isYouTubeShorts(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        val shortsIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_root"
        )
        for (id in shortsIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event?.className?.toString() ?: ""
        if (cls.contains("Shorts", ignoreCase = true) || cls.contains("ShortsPlayer", ignoreCase = true)) return true
        if (findNodeByViewId(root, listOf("reel_watch", "shorts_bottom"))) return true
        val shortsBarNode = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsBarNode.isNullOrEmpty()) {
            for (n in shortsBarNode) {
                if (n.isSelected || n.isChecked) return true
                val p = n.parent
                if (p != null && (p.isSelected || p.isChecked)) return true
            }
        }
        return false
    }

    // ── 2. Instagram Evaluator ───────────────────────────────────────────────
    private fun evaluateInstagram(root: AccessibilityNodeInfo) {
        val rules = activeRules["instagram"] ?: emptySet()
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        // Strict DM & Profile Only mode
        if (rules.contains("dm_profile_only")) {
            val inDm = isInstagramDM(root)
            val inProfile = isInstagramProfile(root)
            if (!inDm && !inProfile) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val dmNode = findClickableNode(root, listOf("Direct", "Pesan", "direct_inbox"))
                    if (dmNode != null) dmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else navigateToHome("com.instagram.android")
                }
                return
            }
        }

        // Feed overlay
        if (rules.contains("disable_feeds") || rules.contains("disable_home")) {
            val isHome = isInstagramHome(root)
            if (isHome) {
                cancelPendingHide()
                overlayManager?.showFeedBlockOverlay(
                    appName = "Instagram",
                    title = "Feeds Instagram Dibatasi",
                    subtitle = "DM & halaman Profil tetap dapat diakses normal.",
                    isDark = true
                )
            } else {
                scheduleHideOverlay(350L)
            }
        } else {
            hideOverlayImmediate()
        }
    }

    private fun isInstagramHome(root: AccessibilityNodeInfo): Boolean {
        val feedIds = listOf("feed_tab", "row_feed_photo_imageview")
        for (id in feedIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id")
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                }
                return true
            }
        }
        return isNodeSelected(root, listOf("Beranda", "Home"))
    }

    private fun isInstagramReels(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        val ids = listOf("clips_viewer_view_pager", "reel_viewer_root", "reels_video_container", "clips_swipe_refresh")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        val cls = event?.className?.toString() ?: ""
        return cls.contains("Reel", ignoreCase = true) || cls.contains("Clips", ignoreCase = true)
    }

    private fun isInstagramStories(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        val ids = listOf("viewer_media_view_root", "story_viewer_fragment")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        return event?.className?.toString()?.contains("Story", ignoreCase = true) == true
    }

    private fun isInstagramDM(root: AccessibilityNodeInfo): Boolean {
        val ids = listOf("direct_inbox_container", "thread_list_fragment_container", "direct_v2_message_thread_view")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        return false
    }

    private fun isInstagramProfile(root: AccessibilityNodeInfo): Boolean {
        val ids = listOf("profile_tab", "self_profile_name_and_bio")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        return false
    }

    // ── 3. TikTok Evaluator ──────────────────────────────────────────────────
    private fun evaluateTikTok(root: AccessibilityNodeInfo) {
        val rules = activeRules["tiktok"] ?: emptySet()
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        if (rules.contains("dm_profile_only")) {
            val inInbox = isNodeSelected(root, listOf("Kotak Masuk", "Inbox"))
            val inProfile = isNodeSelected(root, listOf("Profil", "Profile"))
            if (!inInbox && !inProfile) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val inboxNode = findClickableNode(root, listOf("Kotak Masuk", "Inbox"))
                    if (inboxNode != null) inboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else navigateToHome("com.zhiliaoapp.musically")
                }
                return
            }
        }

        if (rules.contains("disable_fyp")) {
            if (isNodeSelected(root, listOf("Untuk Anda", "For You"))) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val followingTab = findClickableNode(root, listOf("Mengikuti", "Following", "Friends"))
                    followingTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return
            }
        }

        if (rules.contains("hide_explore_feeds")) {
            val inExplore = isNodeSelected(root, listOf("Temukan", "Explore", "Pencarian"))
            val searching = isSearchActive(root, listOf("search_input", "search_box", "search_bar"))
            if (inExplore && !searching) {
                cancelPendingHide()
                overlayManager?.showFeedBlockOverlay(
                    appName = "TikTok",
                    title = "Eksplorasi Feed Dibatasi",
                    subtitle = "Gunakan bilah pencarian untuk konten yang ingin dicari.",
                    isDark = true
                )
                return
            } else if (inExplore && searching) {
                scheduleHideOverlay(200L)
                return
            }
        }

        scheduleHideOverlay(350L)
    }

    private fun isTikTokStory(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        if (findNodeByViewId(root, listOf("story_viewer", "story_view", "story_container"))) return true
        return event?.className?.toString()?.contains("Story", ignoreCase = true) == true
    }

    // ── 4. Twitter Evaluator ─────────────────────────────────────────────────
    private fun evaluateTwitter(root: AccessibilityNodeInfo) {
        val rules = activeRules["twitter"] ?: emptySet()
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        if (rules.contains("disable_for_you_feed") && isTwitterForYouTab(root)) {
            cancelPendingHide()
            overlayManager?.showFeedBlockOverlay(
                appName = "X (Twitter)",
                title = "Tab 'Untuk Anda' Dibatasi",
                subtitle = "Beralih ke tab 'Mengikuti' untuk melihat linimasa.",
                isDark = true
            )
            return
        }

        if (rules.contains("hide_trending") && isTwitterTrending(root)) {
            cancelPendingHide()
            overlayManager?.showFeedBlockOverlay(
                appName = "X (Twitter)",
                title = "Trending Topics Disembunyikan",
                subtitle = "Gunakan pencarian untuk menemukan topik secara langsung.",
                isDark = true
            )
            return
        }

        scheduleHideOverlay(350L)
    }

    private fun isTwitterForYouTab(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("For you", "Untuk Anda", "For You")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                    val p = n.parent
                    if (p != null && (p.isSelected || p.isChecked)) return true
                }
            }
        }
        return false
    }

    private fun isTwitterTrending(root: AccessibilityNodeInfo): Boolean {
        if (!isNodeSelected(root, listOf("Explore", "Eksplorasi", "Search"))) return false
        val trendIds = listOf("com.twitter.android:id/trend_name", "com.twitter.android:id/explore_trending_section")
        for (id in trendIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }

    // =========================================================================
    // Navigation Helpers
    // =========================================================================

    private fun navigateToHome(packageName: String) {
        try {
            val pm = applicationContext.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                applicationContext.startActivity(launchIntent)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isNodeSelected(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)) {
                if (node.isSelected || node.isChecked) return true
                val p = node.parent
                if (p != null && (p.isSelected || p.isChecked)) return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isNodeSelected(node.getChild(i), keywords)) return true
        }
        return false
    }

    private fun findNodeByViewId(node: AccessibilityNodeInfo?, idFragments: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        for (frag in idFragments) {
            if (viewId.contains(frag, ignoreCase = true)) return true
        }
        for (i in 0 until node.childCount) {
            if (findNodeByViewId(node.getChild(i), idFragments)) return true
        }
        return false
    }

    private fun findClickableNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text   = node.text?.toString() ?: ""
        val desc   = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        for (kw in keywords) {
            if ((text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true) ||
                 viewId.contains(kw, ignoreCase = true)) && node.isClickable) return node
        }
        for (i in 0 until node.childCount) {
            val found = findClickableNode(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    private fun isSearchActive(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        val text   = node.text?.toString() ?: ""
        for (kw in keywords) {
            if ((viewId.contains(kw, ignoreCase = true) || text.contains(kw, ignoreCase = true)) &&
                (node.isFocused || node.isEditable)) return true
        }
        for (i in 0 until node.childCount) {
            if (isSearchActive(node.getChild(i), keywords)) return true
        }
        return false
    }
}
