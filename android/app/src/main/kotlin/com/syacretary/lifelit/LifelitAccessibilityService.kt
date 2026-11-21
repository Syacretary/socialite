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
 * LifelitAccessibilityService — v3 (Stable State Machine)
 *
 * Root causes of flickering overlay fixed in this revision:
 *
 * CAUSE 1 — TYPE_WINDOW_CONTENT_CHANGED floods the handler
 *   Autoplay video, RecyclerView scroll animations, thumbnail loading — all fire
 *   TYPE_WINDOW_CONTENT_CHANGED dozens of times per second.
 *   The old code re-evaluated "am I on Home?" on EVERY event (every 200ms), and
 *   during video transitions the Home tab's isSelected state flickers between
 *   true/false, causing the overlay to flash on and off.
 *
 *   FIX: Split event handling by type:
 *     - TYPE_WINDOW_STATE_CHANGED  → "navigation intent" events (tab switch, back, intent)
 *                                    These drive overlay show/hide decisions
 *     - TYPE_WINDOW_CONTENT_CHANGED → "content update" events (autoplay, scroll, loading)
 *                                    These are IGNORED for overlay decisions
 *     - TYPE_WINDOWS_CHANGED       → foreground app switch only
 *
 * CAUSE 2 — hideFeedBlockOverlay() called on every content event
 *   The final `else → hideFeedBlockOverlay()` path at the end of handleYouTube()
 *   fires whenever the page check temporarily returns false (e.g. during autoplay
 *   transition). This immediately hides the overlay.
 *
 *   FIX: Overlay hide is ONLY triggered by explicit navigation events:
 *     - User navigates away from home tab (new WINDOW_STATE_CHANGED from non-home activity)
 *     - User leaves the managed app entirely
 *   Content-change events never hide the overlay.
 *
 * CAUSE 3 — No minimum overlay lifetime
 *   Overlay could be shown and hidden within 200ms. Added OVERLAY_MIN_SHOW_MS (1500ms)
 *   minimum lifetime before the overlay can be dismissed.
 *
 * CAUSE 4 — Confirmation counter missing
 *   A single "not on home" reading was enough to hide the overlay. Now requires
 *   HIDE_CONFIRM_COUNT (2) consecutive "not on home" reads from WINDOW_STATE events
 *   before hiding.
 */
class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Timestamp of last nav redirect (Shorts → Home, etc.) */
    private var lastNavTimestamp = 0L

    /** Timestamp when current overlay was shown (for minimum lifetime check) */
    private var overlayShownAt = 0L

    /** Consecutive "not blocked page" readings from navigation events */
    private var hideConfirmCount = 0

    /** Current foreground app package */
    private var currentForegroundPackage: String = ""

    /** Last known confirmed page state per app — only updated from WINDOW_STATE events */
    private var youtubePageState: YoutubePageState = YoutubePageState.UNKNOWN
    private var instagramPageState: InstagramPageState = InstagramPageState.UNKNOWN

    private enum class YoutubePageState { UNKNOWN, HOME, SHORTS, WATCH, SEARCH, OTHER }
    private enum class InstagramPageState { UNKNOWN, HOME_FEED, REELS, STORIES, DM, PROFILE, OTHER }

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"

        private const val NAV_COOLDOWN_MS    = 700L
        private const val OVERLAY_MIN_SHOW_MS = 1500L  // overlay stays up at least 1.5s
        private const val HIDE_CONFIRM_COUNT  = 2       // need 2 nav-events saying "not home" to hide

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

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = LifelitOverlayManager(this)
        loadRulesFromPrefs(this)
    }

    override fun onDestroy() {
        overlayManager?.hideFeedBlockOverlay()
        overlayManager = null
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        overlayManager?.hideFeedBlockOverlay()
    }

    // =========================================================================
    // Main dispatcher — splits by event TYPE first
    // =========================================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own overlay window to prevent feedback loops
        if (pkg.contains("com.syacretary.lifelit")) return

        when (event.eventType) {

            // ── Window state changed: real navigation (tab switch, back, intent launch) ──
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                onWindowStateChanged(pkg, event)
            }

            // ── Windows changed: app foreground switch (to launcher, settings, etc.) ──
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                onWindowsChanged(pkg)
            }

            // ── Content changed: autoplay, scroll, thumbnail load → IGNORE for overlay decisions ──
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // We only use content events for REDIRECT actions (Shorts/Reels/FYP)
                // which need to be responsive. Never show/hide overlay from content events.
                onContentChangedForRedirectOnly(pkg, event)
            }

            else -> { /* ignore all other event types */ }
        }
    }

    // =========================================================================
    // TYPE_WINDOWS_CHANGED — foreground app switch
    // =========================================================================

    private fun onWindowsChanged(pkg: String) {
        if (pkg == currentForegroundPackage) return
        currentForegroundPackage = pkg

        val isManagedApp = MANAGED_PACKAGES.any { pkg.contains(it) || it.contains(pkg) }
        if (!isManagedApp) {
            // User went to launcher, Settings, or another app → hide overlay immediately
            hideOverlayNow()
            youtubePageState = YoutubePageState.UNKNOWN
            instagramPageState = InstagramPageState.UNKNOWN
        }
    }

    // =========================================================================
    // TYPE_WINDOW_STATE_CHANGED — navigation events drive show/hide decisions
    // =========================================================================

    private fun onWindowStateChanged(pkg: String, event: AccessibilityEvent) {
        currentForegroundPackage = pkg

        val isManagedApp = MANAGED_PACKAGES.any { pkg.contains(it) || it.contains(pkg) }
        if (!isManagedApp) {
            hideOverlayNow()
            return
        }

        // Route to per-app navigation handler
        when {
            pkg.contains("youtube")  -> handleYouTubeNavigation(event)
            pkg.contains("instagram") -> handleInstagramNavigation(event)
            pkg.contains("zhiliaoapp.musically") || pkg.contains("ugc.trill") -> handleTikTokNavigation(event)
            pkg.contains("twitter")  -> handleTwitterNavigation(event)
        }
    }

    // =========================================================================
    // TYPE_WINDOW_CONTENT_CHANGED — only for redirect actions, NOT overlay decisions
    // =========================================================================

    private fun onContentChangedForRedirectOnly(pkg: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return

        when {
            pkg.contains("youtube") -> {
                val rules = activeRules["youtube"] ?: return
                if (rules.contains("disable_shorts")) {
                    val root = rootInActiveWindow ?: return
                    if (isYouTubeShorts(root, event)) {
                        lastNavTimestamp = now
                        overlayManager?.hideFeedBlockOverlay()
                        navigateToHome("com.google.android.youtube")
                    }
                }
            }
            pkg.contains("instagram") -> {
                val rules = activeRules["instagram"] ?: return
                val root = rootInActiveWindow ?: return
                if ((rules.contains("disable_reels") || rules.contains("dm_profile_only") || rules.contains("disable_friend_reels"))
                    && isInstagramReels(root, event)) {
                    lastNavTimestamp = now
                    overlayManager?.hideFeedBlockOverlay()
                    navigateToHome("com.instagram.android")
                } else if ((rules.contains("hide_stories") || rules.contains("dm_profile_only"))
                    && isInstagramStories(root, event)) {
                    lastNavTimestamp = now
                    overlayManager?.hideFeedBlockOverlay()
                    navigateToHome("com.instagram.android")
                }
            }
            pkg.contains("zhiliaoapp.musically") || pkg.contains("ugc.trill") -> {
                val rules = activeRules["tiktok"] ?: return
                val root = rootInActiveWindow ?: return
                if (rules.contains("hide_stories") && isTikTokStory(root, event)) {
                    lastNavTimestamp = now
                    navigateToHome("com.zhiliaoapp.musically")
                }
            }
        }
    }

    // =========================================================================
    // 1. YOUTUBE — Navigation handler (WINDOW_STATE only)
    // =========================================================================

    private fun handleYouTubeNavigation(event: AccessibilityEvent) {
        val root  = rootInActiveWindow ?: return
        val rules = activeRules["youtube"] ?: emptySet()

        // ── Detect page from navigation event ────────────────────────────────
        val newState = detectYouTubePage(root, event)
        val prevState = youtubePageState
        youtubePageState = newState

        // ── Shorts: immediate redirect on nav event ───────────────────────────
        if (rules.contains("disable_shorts") && newState == YoutubePageState.SHORTS) {
            val now = System.currentTimeMillis()
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                overlayManager?.hideFeedBlockOverlay()
                navigateToHome("com.google.android.youtube")
            }
            return
        }

        // ── Home/Feed overlay logic ───────────────────────────────────────────
        if (rules.contains("disable_home")) {
            when (newState) {
                YoutubePageState.HOME -> {
                    // Confirmed on Home tab → show overlay, reset hide counter
                    hideConfirmCount = 0
                    showOverlay(
                        appName  = "YouTube",
                        title    = "Beranda YouTube Dibatasi",
                        subtitle = "Gunakan bilah pencarian di atas untuk menemukan video."
                    )
                }
                YoutubePageState.SEARCH -> {
                    // User opened search → hide overlay so they can type
                    hideConfirmCount = 0
                    hideOverlayNow()
                }
                YoutubePageState.UNKNOWN -> {
                    // Unknown state during transition — keep current overlay state, don't flicker
                }
                else -> {
                    // Navigated away from Home (Watch, Other, etc.)
                    hideConfirmCount++
                    if (hideConfirmCount >= HIDE_CONFIRM_COUNT) {
                        hideConfirmCount = 0
                        hideOverlayRespectingMinTime()
                    }
                }
            }
        } else {
            // Rule not active → ensure overlay is gone
            hideOverlayNow()
        }
    }

    private fun detectYouTubePage(root: AccessibilityNodeInfo, event: AccessibilityEvent): YoutubePageState {
        val className = event.className?.toString() ?: ""

        // Shorts signals
        val shortsIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_root"
        )
        for (id in shortsIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return YoutubePageState.SHORTS
        }
        if (className.contains("Shorts", ignoreCase = true) ||
            className.contains("ShortsPlayer", ignoreCase = true)) return YoutubePageState.SHORTS
        if (findNodeByViewId(root, listOf("reel_watch", "shorts_bottom"))) return YoutubePageState.SHORTS

        // Shorts tab selected in nav bar
        val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsNodes.isNullOrEmpty()) {
            for (n in shortsNodes) {
                if (n.isSelected || n.isChecked) return YoutubePageState.SHORTS
                val p = n.parent
                if (p != null && (p.isSelected || p.isChecked)) return YoutubePageState.SHORTS
            }
        }

        // Watch page (video player)
        val watchIds = listOf(
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/player_control_play_pause_replay_button"
        )
        for (id in watchIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return YoutubePageState.WATCH
        }

        // Search active
        val searchIds = listOf("search_edit_text", "search_bar_view", "query")
        for (id in searchIds) {
            val results = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id")
            if (!results.isNullOrEmpty()) {
                for (n in results) {
                    if (n.isFocused || n.isEditable) return YoutubePageState.SEARCH
                }
            }
        }

        // Home tab selected in bottom nav
        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return YoutubePageState.HOME
                    val p = n.parent
                    if (p != null && (p.isSelected || p.isChecked)) return YoutubePageState.HOME
                }
            }
        }

        // If the className says HomeActivity/MainActivity and nothing else matched
        if (className.contains("Home", ignoreCase = true) ||
            className.contains("Shell", ignoreCase = true)) {
            return YoutubePageState.HOME
        }

        return YoutubePageState.UNKNOWN
    }

    // =========================================================================
    // 2. INSTAGRAM — Navigation handler
    // =========================================================================

    private fun handleInstagramNavigation(event: AccessibilityEvent) {
        val root  = rootInActiveWindow ?: return
        val rules = activeRules["instagram"] ?: emptySet()
        if (rules.isEmpty()) { hideOverlayNow(); return }

        val now = System.currentTimeMillis()
        val newState = detectInstagramPage(root, event)
        instagramPageState = newState

        // Reels redirect
        if ((rules.contains("disable_reels") || rules.contains("dm_profile_only") || rules.contains("disable_friend_reels"))
            && newState == InstagramPageState.REELS) {
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                overlayManager?.hideFeedBlockOverlay()
                navigateToHome("com.instagram.android")
            }
            return
        }

        // Stories redirect
        if ((rules.contains("hide_stories") || rules.contains("dm_profile_only"))
            && newState == InstagramPageState.STORIES) {
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                overlayManager?.hideFeedBlockOverlay()
                navigateToHome("com.instagram.android")
            }
            return
        }

        // DM & Profile Only
        if (rules.contains("dm_profile_only")) {
            if (newState != InstagramPageState.DM && newState != InstagramPageState.PROFILE
                && newState != InstagramPageState.UNKNOWN) {
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
            when (newState) {
                InstagramPageState.HOME_FEED -> {
                    hideConfirmCount = 0
                    showOverlay(
                        appName  = "Instagram",
                        title    = "Feeds Instagram Dibatasi",
                        subtitle = "DM & halaman Profil tetap dapat diakses normal."
                    )
                }
                InstagramPageState.UNKNOWN -> { /* keep current, don't flicker */ }
                else -> {
                    hideConfirmCount++
                    if (hideConfirmCount >= HIDE_CONFIRM_COUNT) {
                        hideConfirmCount = 0
                        hideOverlayRespectingMinTime()
                    }
                }
            }
        } else {
            hideOverlayNow()
        }
    }

    private fun detectInstagramPage(root: AccessibilityNodeInfo, event: AccessibilityEvent): InstagramPageState {
        val cls = event.className?.toString() ?: ""

        // Reels
        val reelIds = listOf("clips_viewer_view_pager", "reel_viewer_root", "reels_video_container", "clips_swipe_refresh")
        for (id in reelIds) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty())
                return InstagramPageState.REELS
        }
        if (cls.contains("Reel", ignoreCase = true) || cls.contains("Clips", ignoreCase = true))
            return InstagramPageState.REELS

        // Stories
        val storyIds = listOf("viewer_media_view_root", "story_viewer_fragment")
        for (id in storyIds) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty())
                return InstagramPageState.STORIES
        }
        if (cls.contains("Story", ignoreCase = true)) return InstagramPageState.STORIES

        // DM
        val dmIds = listOf("direct_inbox_container", "thread_list_fragment_container", "direct_v2_message_thread_view")
        for (id in dmIds) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty())
                return InstagramPageState.DM
        }

        // Profile
        val profileIds = listOf("profile_tab", "self_profile_name_and_bio")
        for (id in profileIds) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty())
                return InstagramPageState.PROFILE
        }

        // Home feed
        val homeFeedIds = listOf("feed_tab", "row_feed_photo_imageview")
        for (id in homeFeedIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id")
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return InstagramPageState.HOME_FEED
                }
                // If feed items exist, we're likely on feed
                return InstagramPageState.HOME_FEED
            }
        }
        if (cls.contains("MainActivity", ignoreCase = true)) return InstagramPageState.HOME_FEED

        return InstagramPageState.UNKNOWN
    }

    // =========================================================================
    // 3. TIKTOK — Navigation handler
    // =========================================================================

    private fun handleTikTokNavigation(event: AccessibilityEvent) {
        val root  = rootInActiveWindow ?: return
        val rules = activeRules["tiktok"] ?: emptySet()
        if (rules.isEmpty()) { hideOverlayNow(); return }

        val now = System.currentTimeMillis()

        // DM & Profile Only
        if (rules.contains("dm_profile_only")) {
            val inInbox   = isNodeSelected(root, listOf("Kotak Masuk", "Inbox"))
            val inProfile = isNodeSelected(root, listOf("Profil", "Profile"))
            if (!inInbox && !inProfile) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val inboxNode = findClickableNode(root, listOf("Kotak Masuk", "Inbox"))
                    if (inboxNode != null) inboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else navigateToHome("com.zhiliaoapp.musically")
                }
                return
            }
        }

        // Disable FYP
        if (rules.contains("disable_fyp")) {
            if (isNodeSelected(root, listOf("Untuk Anda", "For You"))) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val followingTab = findClickableNode(root, listOf("Mengikuti", "Following", "Friends"))
                    followingTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return
            }
        }

        // Explore overlay
        if (rules.contains("hide_explore_feeds")) {
            val inExplore = isNodeSelected(root, listOf("Temukan", "Explore", "Pencarian"))
            val searching = isSearchActive(root, listOf("search_input", "search_box", "search_bar"))
            if (inExplore && !searching) {
                showOverlay("TikTok", "Eksplorasi Feed Dibatasi", "Gunakan bilah pencarian untuk konten yang ingin dicari.")
                return
            } else if (inExplore && searching) {
                hideOverlayNow()
                return
            }
        }

        hideOverlayRespectingMinTime()
    }

    // =========================================================================
    // 4. TWITTER / X — Navigation handler
    // =========================================================================

    private fun handleTwitterNavigation(event: AccessibilityEvent) {
        val root  = rootInActiveWindow ?: return
        val rules = activeRules["twitter"] ?: emptySet()
        if (rules.isEmpty()) { hideOverlayNow(); return }

        val now = System.currentTimeMillis()

        if (rules.contains("disable_for_you_feed") && isTwitterForYouTab(root)) {
            showOverlay("X (Twitter)", "Tab 'Untuk Anda' Dibatasi", "Beralih ke tab 'Mengikuti' untuk melihat linimasa.")
            return
        }

        if (rules.contains("disable_media_endless_scroll") && isTwitterMediaViewer(root, event)) {
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }

        if (rules.contains("hide_trending") && isTwitterTrending(root)) {
            showOverlay("X (Twitter)", "Trending Topics Disembunyikan", "Gunakan pencarian untuk menemukan topik secara langsung.")
            return
        }

        hideOverlayRespectingMinTime()
    }

    // =========================================================================
    // Overlay helpers with stable state management
    // =========================================================================

    private fun showOverlay(appName: String, title: String, subtitle: String) {
        if (overlayManager?.isShowing == false) {
            overlayShownAt = System.currentTimeMillis()
        }
        overlayManager?.showFeedBlockOverlay(appName = appName, title = title, subtitle = subtitle, isDark = true)
        hideConfirmCount = 0
    }

    /** Hides immediately — for app-exit events, search-open, explicit navigation away */
    private fun hideOverlayNow() {
        hideConfirmCount = 0
        overlayShownAt = 0L
        overlayManager?.hideFeedBlockOverlay()
    }

    /**
     * Hides only if the overlay has been showing for at least OVERLAY_MIN_SHOW_MS.
     * This prevents flicker when the page detection momentarily returns a wrong state
     * (e.g., during autoplay thumbnail updates on the home feed).
     */
    private fun hideOverlayRespectingMinTime() {
        val showing = overlayManager?.isShowing ?: false
        if (!showing) return
        val elapsed = System.currentTimeMillis() - overlayShownAt
        if (elapsed >= OVERLAY_MIN_SHOW_MS) {
            overlayShownAt = 0L
            overlayManager?.hideFeedBlockOverlay()
        }
        // If not enough time has passed, do nothing — overlay stays up
    }

    // =========================================================================
    // Per-app page detection utilities
    // =========================================================================

    private fun isYouTubeShorts(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val shortsIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_root"
        )
        for (id in shortsIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        if (cls.contains("Shorts", ignoreCase = true) || cls.contains("ShortsPlayer", ignoreCase = true)) return true
        if (findNodeByViewId(root, listOf("reel_watch", "shorts_bottom"))) return true
        val shortsBarNode = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsBarNode.isNullOrEmpty()) {
            for (n in shortsBarNode) {
                if (n.isSelected || n.isChecked) return true
                val p = n.parent; if (p != null && (p.isSelected || p.isChecked)) return true
            }
        }
        return false
    }

    private fun isInstagramReels(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val ids = listOf("clips_viewer_view_pager", "reel_viewer_root", "reels_video_container", "clips_swipe_refresh")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        return cls.contains("Reel", ignoreCase = true) || cls.contains("Clips", ignoreCase = true)
    }

    private fun isInstagramStories(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val ids = listOf("viewer_media_view_root", "story_viewer_fragment")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id").isNullOrEmpty()) return true
        }
        return event.className?.toString()?.contains("Story", ignoreCase = true) == true
    }

    private fun isTikTokStory(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        if (findNodeByViewId(root, listOf("story_viewer", "story_view", "story_container"))) return true
        return event.className?.toString()?.contains("Story", ignoreCase = true) == true
    }

    private fun isTwitterForYouTab(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("For you", "Untuk Anda", "For You")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                    val p = n.parent; if (p != null && (p.isSelected || p.isChecked)) return true
                }
            }
        }
        return false
    }

    private fun isTwitterMediaViewer(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val ids = listOf("com.twitter.android:id/gallery_pager", "com.twitter.android:id/media_viewer")
        for (id in ids) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        return cls.contains("Gallery", ignoreCase = true) || cls.contains("MediaViewer", ignoreCase = true)
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
    // Navigation Helper
    // =========================================================================

    private fun navigateToHome(packageName: String) {
        try {
            val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                applicationContext.startActivity(launchIntent)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // =========================================================================
    // Accessibility Node Utilities
    // =========================================================================

    private fun isNodeSelected(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)) {
                if (node.isSelected || node.isChecked) return true
                val p = node.parent; if (p != null && (p.isSelected || p.isChecked)) return true
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

    private fun performSwipeUpGesture() {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.75f)
            lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.25f)
        }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
            .build(), null, null)
    }
}
