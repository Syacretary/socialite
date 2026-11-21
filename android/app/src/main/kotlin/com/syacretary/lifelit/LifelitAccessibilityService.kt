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
    private var lastDMTimestamp = 0L
    private var currentForegroundPackage: String = ""

    private var pendingHideRunnable: Runnable? = null
    private var pendingEvalRunnable: Runnable? = null

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"

        private const val NAV_COOLDOWN_MS = 1200L

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

            val reelsBlocked = rules.contains("disable_reels") || rules.contains("dm_profile_only")
            val friendReelsBlocked = rules.contains("disable_friend_reels") || rules.contains("dm_profile_only")

            // 1. Reels Blocking (Reels tab, Explore feed, Beranda post, or Reel link sent by friends in DM)
            if (reelsBlocked) {
                if (isInstagramReelsActive(root, event)) {
                    val isNavBarReels = isInstagramReelsNavbarActive(root)
                    val isFromDM = System.currentTimeMillis() - lastDMTimestamp < 2500L

                    // If it was opened from DM chat and friendReels is NOT blocked, allow it!
                    if (isFromDM && !friendReelsBlocked && !isNavBarReels) {
                        // Reel from friend in DM: permitted
                    } else {
                        lastNavTimestamp = now
                        hideOverlayImmediate()
                        if (isNavBarReels) {
                            redirectToInstagramAllowedScreen(root, rules)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        return
                    }
                }
            }

            // 2. Stories Blocking
            if (rules.contains("hide_stories") || rules.contains("dm_profile_only")) {
                if (isInstagramStoriesActive(root, event)) {
                    lastNavTimestamp = now
                    hideOverlayImmediate()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
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
            // Check if user is in search mode (typing a query, viewing suggestions, or viewing search results)
            val isSearchMode = isYouTubeInSearchMode(root)
            if (isSearchMode) {
                // User is actively searching or viewing search results -> NEVER block search results!
                scheduleHideOverlay(150L)
                return
            }

            val isHome = isYouTubeHomeTab(root)

            // If user is on Home tab and not searching, show overlay
            if (isHome) {
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
     * Determines whether YouTube is in Search Mode (typing a query,
     * viewing search suggestions, or viewing search results).
     *
     * Key indicators of Search Mode:
     * 1. search_edit_text is present (contains the query on the search results page).
     * 2. search_clear_button or filter_button is present.
     * 3. Back / Up button is present in the toolbar (Home feed NEVER has a back button).
     * 4. youtube_logo is absent (Search results replaces the logo with the search bar).
     */
    private fun isYouTubeInSearchMode(root: AccessibilityNodeInfo): Boolean {
        // 1. Check if search input field is in the view tree
        val searchEditNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_edit_text")
        if (!searchEditNodes.isNullOrEmpty()) return true

        val searchClearNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_clear_button")
        if (!searchClearNodes.isNullOrEmpty()) return true

        // 2. Check for search filter button or search results container
        val filterNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/filter_button")
        if (!filterNodes.isNullOrEmpty()) return true

        // 3. Check for Back navigation button in top bar while YouTube logo is absent
        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/toolbar")
        if (!toolbarNodes.isNullOrEmpty()) {
            val toolbar = toolbarNodes[0]
            var hasBackButton = false
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                if (desc.contains("Navigate up", ignoreCase = true) ||
                    desc.contains("Kembali", ignoreCase = true) ||
                    desc.contains("Back", ignoreCase = true)) {
                    hasBackButton = true
                    break
                }
            }
            if (hasBackButton) {
                val logoNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/youtube_logo")
                if (logoNodes.isNullOrEmpty()) {
                    return true
                }
            }
        }

        // 4. Check for query view
        val queryNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/query")
        if (!queryNodes.isNullOrEmpty()) return true

        return false
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
        // If in search mode (typing or viewing search results), definitely not Home feed
        if (isYouTubeInSearchMode(root)) return false

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
    //
    // Instagram tab semantics (from live device ADB dump):
    //   feed_tab    = Beranda (Home: posts from people you follow)
    //   clips_tab   = Reels
    //   direct_tab  = DM (Messages)
    //   search_tab  = Explore/Search (discover grid + search bar)
    //   profile_tab = Profile
    //
    // IMPORTANT: Instagram does NOT offset off-screen tabs to negative coordinates.
    // All tab content lives at 0,0-1080,2130 regardless of which tab is active.
    // Therefore, the ONLY reliable way to know which tab is active is:
    //   getActiveInstagramTab(root) which reads the .isSelected flag on tab_bar children.
    //
    // Rule semantics (as clarified by user):
    //   disable_home  = Matikan Beranda   => redirect away from Beranda tab (NO overlay, just navigate)
    //   disable_feeds = Matikan Feeds     => on Search/Explore tab, show overlay BELOW search bar
    //                                        (search bar stays accessible, only explore grid covered)
    //   dm_profile_only                   => only DM + Profile allowed; everything else blocked
    //
    private fun evaluateInstagram(root: AccessibilityNodeInfo) {
        val rules = activeRules["instagram"] ?: emptySet()
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        val dmOnly = rules.contains("dm_profile_only")
        val homeBlocked = rules.contains("disable_home") || dmOnly
        val feedsBlocked = rules.contains("disable_feeds") || dmOnly

        // 1. DM & Profile are ALWAYS permitted!
        val activeTab = getActiveInstagramTab(root)

        if (activeTab == "dm" || activeTab == "profile") {
            scheduleHideOverlay(150L)
            return
        }
        // Also handle sub-pages inside DM or Profile (e.g. inside a conversation)
        if (isInstagramDM(root) || isInstagramProfile(root)) {
            scheduleHideOverlay(150L)
            return
        }

        // 2. Beranda / Home Feed tab
        if (activeTab == "home") {
            if (homeBlocked) {
                // REDIRECT ONLY - no overlay! Beranda completely blocked.
                // User must be taken to DM or Profile, NOT shown an overlay.
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    if (dmOnly) {
                        // dm_profile_only: go to DM first, then Profile
                        if (!clickInstagramDM(root)) clickInstagramProfile(root)
                    } else {
                        // disable_home: go to Profile, fallback to DM
                        if (!clickInstagramProfile(root)) clickInstagramDM(root)
                    }
                }
                scheduleHideOverlay(150L)
                return
            }
            // Home not blocked: hide overlay
            scheduleHideOverlay(150L)
            return
        }

        // 3. Search / Explore tab
        if (activeTab == "search") {
            if (dmOnly) {
                // dm_profile_only: redirect away from Search entirely
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    if (!clickInstagramDM(root)) clickInstagramProfile(root)
                }
                cancelPendingHide()
                val bounds = calculateInstagramFeedBounds(root)
                overlayManager?.showFeedBlockOverlay(
                    appName = "Instagram",
                    title = "Mode DM & Profil Aktif",
                    subtitle = "Akses hanya diizinkan untuk percakapan DM dan Profil.",
                    isDark = true,
                    customTopPx = bounds.topPx,
                    customHeightPx = bounds.heightPx
                )
                return
            }
            if (feedsBlocked) {
                // disable_feeds: show overlay ONLY over the explore content grid.
                // The search bar (explore_action_bar, ~143px tall) must remain accessible.
                cancelPendingHide()
                val bounds = calculateInstagramExploreBounds(root)
                overlayManager?.showFeedBlockOverlay(
                    appName = "Instagram",
                    title = "Feeds Instagram Dibatasi",
                    subtitle = "Gunakan kolom pencarian di atas untuk mencari konten.",
                    isDark = true,
                    customTopPx = bounds.topPx,
                    customHeightPx = bounds.heightPx
                )
                return
            }
            // Search/Explore not blocked
            scheduleHideOverlay(150L)
            return
        }

        // 4. Reels tab: handled by checkFastRedirects; here just hide overlay
        if (activeTab == "reels") {
            scheduleHideOverlay(150L)
            return
        }

        // 5. Unknown tab or sub-page: permit
        scheduleHideOverlay(200L)
    }

    /**
     * Returns which of Instagram's 5 bottom nav tabs is currently selected.
     * Uses the actual .isSelected flag on tab children - the ONLY reliable signal
     * since Instagram does NOT offset off-screen tab content to negative coordinates.
     *
     * Returns: "home", "reels", "dm", "search", "profile", or "unknown"
     */
    private fun getActiveInstagramTab(root: AccessibilityNodeInfo): String {
        val tabBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBar.isNullOrEmpty()) {
            for (childIdx in 0 until tabBar[0].childCount) {
                val child = tabBar[0].getChild(childIdx) ?: continue
                if (child.isSelected) {
                    val resId = child.viewIdResourceName ?: ""
                    return when {
                        resId.contains("feed_tab") -> "home"
                        resId.contains("clips_tab") -> "reels"
                        resId.contains("direct_tab") -> "dm"
                        resId.contains("search_tab") -> "search"
                        resId.contains("profile_tab") -> "profile"
                        else -> "unknown"
                    }
                }
            }
        }
        // Fallback: check individual tab IDs
        val tabIds = mapOf(
            "com.instagram.android:id/feed_tab" to "home",
            "com.instagram.android:id/clips_tab" to "reels",
            "com.instagram.android:id/direct_tab" to "dm",
            "com.instagram.android:id/search_tab" to "search",
            "com.instagram.android:id/profile_tab" to "profile"
        )
        for ((id, name) in tabIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected) return name
                }
            }
        }
        return "unknown"
    }

    private fun calculateInstagramFeedBounds(root: AccessibilityNodeInfo): FeedBounds {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels
        var topPx = 0
        var bottomPx = screenHeight

        // Use layout_container_main_panel which is confirmed at 0,107-1080,2359
        val panelNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/layout_container_main_panel")
        if (!panelNodes.isNullOrEmpty()) {
            val rect = Rect()
            panelNodes[0].getBoundsInScreen(rect)
            if (rect.top >= 0) topPx = rect.top
        }

        // tab_bar is confirmed at 0,2130-1080,2252 - overlay should end exactly at tab_bar top
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val rect = Rect()
            tabBars[0].getBoundsInScreen(rect)
            if (rect.top > topPx) bottomPx = rect.top
        } else {
            // Fallback: swipeable_tab_view_pager confirmed at 0,0-1080,2130
            val pagerNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/swipeable_tab_view_pager")
            if (!pagerNodes.isNullOrEmpty()) {
                val rect = Rect()
                pagerNodes[0].getBoundsInScreen(rect)
                if (rect.bottom > topPx) bottomPx = rect.bottom
            }
        }

        // Safe Fallbacks using real device values: status bar ~107px, tab bar top ~2130px
        if (topPx == 0) topPx = (dm.heightPixels * 0.045f).toInt()
        if (bottomPx == screenHeight) bottomPx = (dm.heightPixels * 0.887f).toInt()

        val heightPx = (bottomPx - topPx).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(topPx, heightPx)
    }

    /**
     * Calculates overlay bounds for the Search/Explore tab.
     * The search bar (explore_action_bar, confirmed 143px tall) MUST remain accessible.
     * The overlay starts BELOW the search bar and extends to the tab_bar top.
     */
    private fun calculateInstagramExploreBounds(root: AccessibilityNodeInfo): FeedBounds {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels
        var searchBarBottom = 0
        var bottomPx = screenHeight

        // Find the explore action bar's bottom edge (the search bar)
        val exploreActionBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/explore_action_bar")
        if (!exploreActionBar.isNullOrEmpty()) {
            val rect = Rect()
            exploreActionBar[0].getBoundsInScreen(rect)
            if (rect.bottom > 0) searchBarBottom = rect.bottom
        }

        // Also try the main_feed_action_bar as fallback
        if (searchBarBottom == 0) {
            val feedActionBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/main_feed_action_bar")
            if (!feedActionBar.isNullOrEmpty()) {
                val rect = Rect()
                feedActionBar[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) searchBarBottom = rect.bottom
            }
        }

        // tab_bar bottom boundary
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val rect = Rect()
            tabBars[0].getBoundsInScreen(rect)
            if (rect.top > searchBarBottom) bottomPx = rect.top
        }

        // Fallback: explore_action_bar is confirmed 143px tall at y=0 on this device
        // layout_container_main_panel starts at 107px, so action bar ends at 107+143=250px
        if (searchBarBottom == 0) {
            val panelNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/layout_container_main_panel")
            if (!panelNodes.isNullOrEmpty()) {
                val rect = Rect()
                panelNodes[0].getBoundsInScreen(rect)
                // Search bar is ~143px tall within the content area
                searchBarBottom = rect.top + (143 * dm.density).toInt()
            } else {
                searchBarBottom = (dm.heightPixels * 0.104f).toInt() // ~250px on 2400px screen
            }
        }

        if (bottomPx == screenHeight) bottomPx = (dm.heightPixels * 0.887f).toInt()

        val heightPx = (bottomPx - searchBarBottom).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(searchBarBottom, heightPx)
    }

    private fun isNodeVisibleOnScreen(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val dm = resources.displayMetrics
        return rect.width() > 100 && rect.height() > 100 && rect.left >= 0 && rect.left < dm.widthPixels && rect.right > 0
    }

    /**
     * Determines whether Instagram is currently displaying the Home Feed.
     * Uses verified view IDs from live device view hierarchy dump.
     * Key insight: in ViewPager2, only the ACTIVE tab's views have on-screen coordinates.
     * Off-screen tabs have negative left coordinates (e.g. -1080,0-0,2130).
     */
    private fun isInstagramHomeFeed(root: AccessibilityNodeInfo): Boolean {
        if (isInstagramDM(root)) return false
        if (isInstagramProfile(root)) return false
        if (isInstagramSearchOrExplore(root)) return false

        // If Reels navbar tab is selected, we are definitely NOT on Home Feed
        if (isInstagramReelsNavbarActive(root)) return false

        // Check feed_tab selected state (confirmed ID: com.instagram.android:id/feed_tab)
        val feedTabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (!feedTabNodes.isNullOrEmpty()) {
            for (n in feedTabNodes) {
                if (!isNodeVisibleOnScreen(n)) continue
                // If feed_tab is explicitly NOT selected but another tab IS, return false
                if (n.isSelected || n.isChecked) return true
            }
        }

        // Check if on-screen Home Feed views are present (they would be off-screen if another tab is active)
        // sticky_header_list is the outer scroll host that wraps the main feed RecyclerView
        val stickyList = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/sticky_header_list")
        if (!stickyList.isNullOrEmpty()) {
            for (n in stickyList) {
                if (isNodeVisibleOnScreen(n)) return true
            }
        }

        val refreshable = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/refreshable_container")
        if (!refreshable.isNullOrEmpty()) {
            for (n in refreshable) {
                if (isNodeVisibleOnScreen(n)) return true
            }
        }

        // Check for MainFeedRecyclerView (android:id/list) on screen
        val listNodes = root.findAccessibilityNodeInfosByViewId("android:id/list")
        if (!listNodes.isNullOrEmpty()) {
            for (n in listNodes) {
                val cls = n.className?.toString() ?: ""
                if (cls.contains("MainFeedRecyclerView", ignoreCase = true)) {
                    if (isNodeVisibleOnScreen(n)) return true
                }
            }
        }

        return false
    }

    /**
     * Detects Search / Explore mode in Instagram.
     */
    private fun isInstagramSearchOrExplore(root: AccessibilityNodeInfo): Boolean {
        val searchEditIds = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/search_edit_text",
            "com.instagram.android:id/action_bar_search_layout",
            "com.instagram.android:id/search_src_text",
            "com.instagram.android:id/explore_recycler_view"
        )
        for (id in searchEditIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isNodeVisibleOnScreen(n)) return true
                }
            }
        }
        return false
    }

    fun isInstagramReelsActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        // Guard: If we are confirmed on Profile or DM, it is NEVER Reels!
        if (isInstagramDM(root)) return false
        if (isInstagramProfile(root)) return false

        // 1. Explicit click event on Reels
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val desc = event.contentDescription?.toString() ?: ""
            val text = event.text?.joinToString() ?: ""
            if (desc.contains("Reels", ignoreCase = true) || text.contains("Reels", ignoreCase = true)) {
                return true
            }
        }

        // 2. Fullscreen Reels viewer views visible on screen
        val reelViewIds = listOf(
            "com.instagram.android:id/root_clips_layout",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_swipe_refresh_container",
            "com.instagram.android:id/clips_viewer_view_pager"
        )
        for (id in reelViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isNodeVisibleOnScreen(n)) return true
                }
            }
        }

        // 3. Modal Clips Activity
        val cls = event?.className?.toString() ?: ""
        if (cls.contains("ClipsViewerActivity", ignoreCase = true) || cls.contains("ClipsViewerFragment", ignoreCase = true)) {
            return true
        }

        return false
    }

    fun isInstagramStoriesActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        val storyViewerIds = listOf(
            "com.instagram.android:id/viewer_media_view_root",
            "com.instagram.android:id/story_viewer_fragment",
            "com.instagram.android:id/reel_viewer_image_view",
            "com.instagram.android:id/reel_viewer_root"
        )
        for (id in storyViewerIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isNodeVisibleOnScreen(n)) return true
                }
            }
        }
        val cls = event?.className?.toString() ?: ""
        return cls.contains("StoryViewerActivity", ignoreCase = true) || cls.contains("ReelViewerActivity", ignoreCase = true)
    }

    private fun isInstagramDM(root: AccessibilityNodeInfo): Boolean {
        val ids = listOf(
            "com.instagram.android:id/direct_inbox_container",
            "com.instagram.android:id/thread_list_fragment_container",
            "com.instagram.android:id/direct_v2_message_thread_view",
            "com.instagram.android:id/direct_search_box",
            "com.instagram.android:id/direct_header_action_bar",
            "com.instagram.android:id/message_composer_container"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isNodeVisibleOnScreen(n)) {
                        lastDMTimestamp = System.currentTimeMillis()
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isInstagramProfile(root: AccessibilityNodeInfo): Boolean {
        val profileContentIds = listOf(
            "com.instagram.android:id/self_profile_name_and_bio",
            "com.instagram.android:id/edit_profile_button",
            "com.instagram.android:id/profile_viewpager",
            "com.instagram.android:id/profile_header_container",
            "com.instagram.android:id/user_profile_header"
        )
        for (id in profileContentIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isNodeVisibleOnScreen(n)) return true
                }
            }
        }
        return false
    }

    /**
     * Checks if the Reels (clips) tab in the bottom nav bar is currently the SELECTED tab.
     * Uses the verified view ID 'clips_tab' and checks the node's isSelected / isChecked state.
     * This distinguishes the Reels navbar tab from a Reel opened as a modal overlay from DM.
     */
    private fun isInstagramReelsNavbarActive(root: AccessibilityNodeInfo): Boolean {
        // Primary: check clips_tab selected state (confirmed ID from live view hierarchy)
        val clipsTabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
        if (!clipsTabNodes.isNullOrEmpty()) {
            for (n in clipsTabNodes) {
                if (!isNodeVisibleOnScreen(n)) continue
                if (n.isSelected || n.isChecked) return true
                val desc = n.contentDescription?.toString() ?: ""
                if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return true
            }
        }

        // Fallback: check tab_bar children - clips tab is 2nd child (index 1)
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            if (tabBar.childCount > 1) {
                val clipsChild = tabBar.getChild(1)
                if (clipsChild != null) {
                    if (clipsChild.isSelected || clipsChild.isChecked) return true
                    val desc = clipsChild.contentDescription?.toString() ?: ""
                    if (desc.contains("Reels", ignoreCase = true) &&
                        (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true))) return true
                }
            }
        }
        return false
    }

    /**
     * Handles Reels blocking smartly:
     * - If opened from the bottom navbar (Reels tab): Click the allowed tab directly WITHOUT pressing BACK.
     *   (Pressing BACK in the navbar tab host causes Instagram to bounce to Beranda).
     * - If opened as a fullscreen modal player (e.g. from DM link): Press BACK to dismiss modal and return to chat.
     */
    private fun handleInstagramReelsBlocked(root: AccessibilityNodeInfo, rules: Set<String>) {
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            redirectToInstagramAllowedScreen(root, rules)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /**
     * Adaptive fallback navigation for Instagram:
     * Routes smoothly depending on active rule priority.
     */
    private fun redirectToInstagramAllowedScreen(root: AccessibilityNodeInfo, rules: Set<String>) {
        val dmOnly = rules.contains("dm_profile_only")
        val homeBlocked = rules.contains("disable_home") || dmOnly
        val feedsBlocked = rules.contains("disable_feeds") || homeBlocked

        if (dmOnly) {
            // In dm_profile_only mode, navigate to Profile tab directly
            if (!clickInstagramProfile(root)) {
                clickInstagramDM(root)
            }
        } else if (homeBlocked) {
            // Home is blocked: navigate to Profile (or Search)
            if (!clickInstagramProfile(root)) {
                clickInstagramSearch(root)
            }
        } else if (feedsBlocked) {
            // Feed is blocked: navigate to Profile if possible, else Beranda with overlay
            if (!clickInstagramProfile(root)) {
                clickInstagramHome(root)
            }
        } else {
            // Feeds & Home are allowed: navigate to Beranda
            clickInstagramHome(root)
        }
    }

    private fun clickNodeOrDescendant(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val parent = node.parent
        if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return false
    }

    private fun clickInstagramDM(root: AccessibilityNodeInfo): Boolean {
        val dmIds = listOf(
            "com.instagram.android:id/action_bar_inbox_button",
            "com.instagram.android:id/action_bar_inbox_icon",
            "com.instagram.android:id/inbox_button"
        )
        for (id in dmIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (clickNodeOrDescendant(n)) return true
                }
            }
        }

        val dmLabels = listOf("Pesan", "Direct", "Messenger", "Inbox")
        for (label in dmLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    private fun clickInstagramProfile(root: AccessibilityNodeInfo): Boolean {
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            val count = tabBar.childCount
            if (count > 0) {
                val profileTab = tabBar.getChild(count - 1)
                if (clickNodeOrDescendant(profileTab)) return true
            }
        }

        val profileNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/profile_tab")
        if (!profileNodes.isNullOrEmpty()) {
            for (n in profileNodes) {
                if (clickNodeOrDescendant(n)) return true
            }
        }

        val profileLabels = listOf("Profil", "Profile")
        for (label in profileLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    private fun clickInstagramHome(root: AccessibilityNodeInfo): Boolean {
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            if (tabBar.childCount > 0) {
                val homeTab = tabBar.getChild(0)
                if (clickNodeOrDescendant(homeTab)) return true
            }
        }

        val feedNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (!feedNodes.isNullOrEmpty()) {
            for (n in feedNodes) {
                if (clickNodeOrDescendant(n)) return true
            }
        }

        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    private fun clickInstagramSearch(root: AccessibilityNodeInfo): Boolean {
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            if (tabBar.childCount > 1) {
                val searchTab = tabBar.getChild(1)
                if (clickNodeOrDescendant(searchTab)) return true
            }
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
