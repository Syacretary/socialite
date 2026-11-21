package com.syacretary.lifelit

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * LifelitAccessibilityService
 *
 * Provides real-time smart feature blocking (Shorts, Reels, FYP, and Feeds)
 * with dynamic screen-adaptive bounds measurement, instant Shorts redirect
 * back to Beranda, and zero-flicker debouncing.
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

        private const val NAV_COOLDOWN_MS = 500L

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
            if (rules.contains("disable_shorts")) {
                if (isYouTubeShortsActive(root, event)) {
                    redirectToYouTubeHome(root)
                    return
                }
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
    // YouTube Shorts Redirect to Home Implementation
    // =========================================================================

    /**
     * Instantly brings user back to Beranda whenever Shorts is entered:
     * 1. Closes fullscreen Shorts player via BACK action if open.
     * 2. Programmatically clicks the "Beranda" / "Home" tab in the bottom pivot_bar.
     */
    private fun redirectToYouTubeHome(root: AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return
        lastNavTimestamp = now

        // 1. If in full-screen Shorts player, press BACK to dismiss it immediately
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 2. Click the Home tab in the navbar directly if available
        if (root != null) {
            clickYouTubeHomeTab(root)
        }

        // 3. Fallback delayed click to Home tab once back action settles
        handler.postDelayed({
            val newRoot = rootInActiveWindow
            if (newRoot != null) {
                clickYouTubeHomeTab(newRoot)
            }
        }, 180L)
    }

    private fun clickYouTubeHomeTab(root: AccessibilityNodeInfo): Boolean {
        // 1. Check in pivot_bar
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                val text = child.text?.toString() ?: ""
                if (desc.contains("Beranda", ignoreCase = true) || desc.contains("Home", ignoreCase = true) ||
                    text.contains("Beranda", ignoreCase = true) || text.contains("Home", ignoreCase = true)) {
                    if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    for (j in 0 until child.childCount) {
                        val sub = child.getChild(j) ?: continue
                        if (sub.isClickable && sub.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                }
            }
        }

        // 2. Global search for Home / Beranda
        for (label in listOf("Beranda", "Home")) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    val parent = node.parent
                    if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isYouTubeShortsActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        // 1. User clicked on a view representing Shorts (either bottom navbar tab or carousel item)
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val desc = event.contentDescription?.toString() ?: ""
            val text = event.text?.joinToString() ?: ""
            if (desc.contains("Shorts", ignoreCase = true) || text.contains("Shorts", ignoreCase = true)) {
                return true
            }
        }

        // 2. Dedicated Shorts full-screen player is active
        val reelWatchNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_watch_fragment_root")
        if (!reelWatchNodes.isNullOrEmpty()) return true

        val reelPlayerNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page_container")
        if (!reelPlayerNodes.isNullOrEmpty()) return true

        // 3. Shorts tab in bottom navigation bar is currently selected
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                if (desc.contains("Shorts", ignoreCase = true)) {
                    if (child.isSelected || child.isChecked) return true
                    if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return true
                    for (j in 0 until child.childCount) {
                        val sub = child.getChild(j) ?: continue
                        if (sub.isSelected || sub.isChecked) return true
                    }
                }
            }
        }

        // 4. Fallback: text search for Shorts with selected state
        val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsNodes.isNullOrEmpty()) {
            for (n in shortsNodes) {
                val desc = n.contentDescription?.toString() ?: ""
                if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return true
                if (n.isSelected || n.isChecked) return true
                val p = n.parent
                if (p != null && (p.isSelected || p.isChecked)) return true
            }
        }

        // 5. Event class name check
        val cls = event?.className?.toString() ?: ""
        if (cls.contains("ReelWatchFragment", ignoreCase = true) || cls.contains("ShortsPlayer", ignoreCase = true)) {
            return true
        }

        return false
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

        // If Shorts blocking is on and Shorts is active, redirect immediately
        if (rules.contains("disable_shorts") && isYouTubeShortsActive(root, null)) {
            redirectToYouTubeHome(root)
            return
        }

        if (rules.contains("disable_home")) {
            val isHome = isYouTubeHomeTab(root)
            val isSearching = isSearchActive(root, listOf("search_edit_text", "search_bar", "query"))

            // If user is on Home tab and not searching, show overlay
            if (isHome && !isSearching) {
                cancelPendingHide()

                // Calculate exact pixel bounds from on-screen node hierarchy
                val bounds = calculateYouTubeFeedBounds(root)

                overlayManager?.showFeedBlockOverlay(
                    appName = "YouTube",
                    title = "Beranda YouTube Dibatasi",
                    subtitle = "Gunakan bilah pencarian di atas untuk menonton video yang Anda cari.",
                    isDark = true,
                    customTopPx = bounds.topPx,
                    customHeightPx = bounds.heightPx
                )
            } else {
                scheduleHideOverlay(350L)
            }
        } else {
            hideOverlayImmediate()
        }
    }

    /**
     * Measures the exact on-screen top and bottom bounds of the YouTube feed area
     * using the bottom of the toolbar and the top of the pivot_bar navigation bar.
     */
    private data class FeedBounds(val topPx: Int, val heightPx: Int)

    private fun calculateYouTubeFeedBounds(root: AccessibilityNodeInfo): FeedBounds {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels
        var topPx = 0
        var bottomPx = screenHeight

        // 1. Top Bound: Bottom of Toolbar / Action Bar (search bar area)
        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/toolbar")
        if (!toolbarNodes.isNullOrEmpty()) {
            val rect = Rect()
            toolbarNodes[0].getBoundsInScreen(rect)
            if (rect.bottom > 0) {
                topPx = rect.bottom
            }
        } else {
            val searchNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/menu_item_search")
            if (!searchNodes.isNullOrEmpty()) {
                val rect = Rect()
                searchNodes[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) {
                    topPx = rect.bottom + (4 * dm.density).toInt()
                }
            }
        }

        // 2. Bottom Bound: Top of Pivot Bar (bottom navigation bar)
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val rect = Rect()
            pivotNodes[0].getBoundsInScreen(rect)
            if (rect.top > 0) {
                bottomPx = rect.top
            }
        } else {
            val navNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/bottom_navigation_bar")
            if (!navNodes.isNullOrEmpty()) {
                val rect = Rect()
                navNodes[0].getBoundsInScreen(rect)
                if (rect.top > 0) {
                    bottomPx = rect.top
                }
            }
        }

        // Fallbacks if node detection hasn't initialized yet
        if (topPx == 0) {
            topPx = (96 * dm.density).toInt() // realistic status bar + toolbar
        }
        if (bottomPx == screenHeight) {
            bottomPx = screenHeight - (56 * dm.density).toInt()
        }

        val heightPx = (bottomPx - topPx).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(topPx, heightPx)
    }

    private fun isYouTubeHomeTab(root: AccessibilityNodeInfo): Boolean {
        // If Shorts full-screen player is active, definitely not Home
        val reelWatchNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_watch_fragment_root")
        if (!reelWatchNodes.isNullOrEmpty()) return false

        // Check if other tabs are selected in pivot_bar
        val otherTabLabels = listOf("Subscriptions", "Subskripsi", "You", "Koleksi", "Akun")
        for (label in otherTabLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    val desc = n.contentDescription?.toString() ?: ""
                    if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return false
                    if (n.isSelected || n.isChecked) return false
                    val p = n.parent
                    if (p != null && (p.isSelected || p.isChecked)) return false
                }
            }
        }

        // Check if Shorts tab is selected
        val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsNodes.isNullOrEmpty()) {
            for (n in shortsNodes) {
                val desc = n.contentDescription?.toString() ?: ""
                if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return false
                if (n.isSelected || n.isChecked) return false
                val p = n.parent
                if (p != null && (p.isSelected || p.isChecked)) return false
            }
        }

        // Check if Home tab is selected
        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    val desc = n.contentDescription?.toString() ?: ""
                    if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return true
                    if (n.isSelected || n.isChecked) return true
                    val p = n.parent
                    if (p != null && (p.isSelected || p.isChecked)) return true
                }
            }
        }

        // Fallback: If pivot_bar is present and Shorts/Subscriptions/You is NOT selected, default to Home
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        return !pivotNodes.isNullOrEmpty()
    }

    // ── 2. Instagram Evaluator ───────────────────────────────────────────────
    private fun evaluateInstagram(root: AccessibilityNodeInfo) {
        val rules = activeRules["instagram"] ?: emptySet()
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

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

        if (rules.contains("disable_feeds") || rules.contains("disable_home")) {
            val isHome = isInstagramHome(root)
            if (isHome) {
                cancelPendingHide()
                val bounds = calculateInstagramFeedBounds(root)
                overlayManager?.showFeedBlockOverlay(
                    appName = "Instagram",
                    title = "Feeds Instagram Dibatasi",
                    subtitle = "DM & halaman Profil tetap dapat diakses normal.",
                    isDark = true,
                    customTopPx = bounds.topPx,
                    customHeightPx = bounds.heightPx
                )
            } else {
                scheduleHideOverlay(350L)
            }
        } else {
            hideOverlayImmediate()
        }
    }

    private fun calculateInstagramFeedBounds(root: AccessibilityNodeInfo): FeedBounds {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels
        var topPx = 0
        var bottomPx = screenHeight

        val actionBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_container")
        if (!actionBars.isNullOrEmpty()) {
            val rect = Rect()
            actionBars[0].getBoundsInScreen(rect)
            if (rect.bottom > 0) topPx = rect.bottom
        }

        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val rect = Rect()
            tabBars[0].getBoundsInScreen(rect)
            if (rect.top > 0) bottomPx = rect.top
        }

        if (topPx == 0) topPx = (90 * dm.density).toInt()
        if (bottomPx == screenHeight) bottomPx = screenHeight - (56 * dm.density).toInt()

        val heightPx = (bottomPx - topPx).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(topPx, heightPx)
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
