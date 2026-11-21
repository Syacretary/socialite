package com.syacretary.lifelit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * LifelitAccessibilityService
 *
 * Core intelligence layer for Lifelit feature-blocking.
 *
 * ── Bug fixes & improvements in this revision ──────────────────────────────
 * 1. OVERLAY PERSISTS AFTER APP EXIT
 *    Previously the `else` branch in onAccessibilityEvent hid the overlay only
 *    when a non-target package triggered an event. But if the user exits YouTube
 *    without triggering any accessibility event from another app (e.g. goes to
 *    launcher), the overlay stayed visible.
 *    FIX: Track `currentForegroundPackage` via TYPE_WINDOW_STATE_CHANGED and
 *    TYPE_WINDOWS_CHANGED. When the foreground package is no longer a monitored
 *    social app, hide the overlay immediately — even if no content-change event
 *    fires from the new package.
 *
 * 2. OVERLAY BLOCKS ALL TOUCH INPUT INCLUDING SAFE AREAS
 *    FIX in LifelitOverlayManager: Added FLAG_NOT_TOUCH_MODAL so only the opaque
 *    feed-area child absorbs touches; transparent top/bottom margins pass touches
 *    through to search bar and nav bar.
 *
 * 3. SHORTS (& REELS/FYP) NAVIGATE HOME INSTEAD OF CLOSING THE APP
 *    Previously performGlobalAction(GLOBAL_ACTION_BACK) was called which could
 *    close the entire app if the Shorts player was the first screen.
 *    FIX: navigateToHome(pkg) sends an explicit Intent to relaunch the app's
 *    home activity instead. GLOBAL_ACTION_BACK is now only used as a last-resort
 *    fallback for story viewers where HOME makes less sense.
 *
 * 4. YOUTUBE SHORTS DETECTION (more robust IDs)
 *    Added proper resource-ID checks:
 *    com.google.android.youtube:id/reel_watch_fragment_root
 *    com.google.android.youtube:id/reel_recycler
 *    plus class-name fragment heuristic ("ShortsPlayerFragment", "ReelFragment").
 *
 * 5. DEBOUNCE IMPROVED
 *    Per-action debounce raised to 400 ms so rapid event floods don't repeat
 *    navigations. Navigation actions have their own separate cooldown.
 * ───────────────────────────────────────────────────────────────────────────
 */
class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Last time a redirect/navigation was fired (ms) */
    private var lastNavTimestamp = 0L

    /** Last time an overlay show/hide decision was evaluated (ms) */
    private var lastOverlayEvalTimestamp = 0L

    /**
     * The package we believe is currently in the foreground.
     * Updated on every TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED event.
     */
    private var currentForegroundPackage: String = ""

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"

        // Nav cooldown: don't fire two redirects within 600 ms
        private const val NAV_COOLDOWN_MS    = 600L
        // Overlay eval cooldown: re-evaluate at most every 200 ms
        private const val OVERLAY_EVAL_MS    = 200L

        /**
         * Packages that are "social apps" managed by Lifelit.
         * We hide the overlay as soon as the foreground package is NOT in this set.
         */
        private val MANAGED_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.twitter.android"
        )

        // Map: appId → Set of blocked feature IDs
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
    // Main event dispatcher
    // =========================================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // ── Step 1: Track foreground package ─────────────────────────────────
        // TYPE_WINDOW_STATE_CHANGED fires reliably when a new activity/window
        // takes focus (app switch, back navigation, intent launch).
        // TYPE_WINDOWS_CHANGED covers multi-window & launcher scenarios.
        val isAppSwitchEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                               event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        if (isAppSwitchEvent) {
            if (pkg != currentForegroundPackage) {
                currentForegroundPackage = pkg
                // If we just left all monitored apps → hide overlay immediately
                if (!MANAGED_PACKAGES.any { pkg.contains(it) || it.contains(pkg) }) {
                    // Also ignore our own package triggering events (overlay window)
                    if (!pkg.contains("com.syacretary.lifelit")) {
                        overlayManager?.hideFeedBlockOverlay()
                        return
                    }
                }
            }
        }

        // Ignore events from our own overlay window to prevent feedback loops
        if (pkg.contains("com.syacretary.lifelit")) return

        // ── Step 2: Route to per-app handler ─────────────────────────────────
        val now = System.currentTimeMillis()
        when {
            pkg.contains("youtube") -> {
                if (now - lastOverlayEvalTimestamp > OVERLAY_EVAL_MS) {
                    lastOverlayEvalTimestamp = now
                    handleYouTube(event)
                }
            }
            pkg.contains("instagram") -> {
                if (now - lastOverlayEvalTimestamp > OVERLAY_EVAL_MS) {
                    lastOverlayEvalTimestamp = now
                    handleInstagram(event)
                }
            }
            pkg.contains("zhiliaoapp.musically") || pkg.contains("ugc.trill") -> {
                if (now - lastOverlayEvalTimestamp > OVERLAY_EVAL_MS) {
                    lastOverlayEvalTimestamp = now
                    handleTikTok(event)
                }
            }
            pkg.contains("twitter") -> {
                if (now - lastOverlayEvalTimestamp > OVERLAY_EVAL_MS) {
                    lastOverlayEvalTimestamp = now
                    handleTwitter(event)
                }
            }
            else -> {
                // Non-managed app in foreground → hide any active overlay
                overlayManager?.hideFeedBlockOverlay()
            }
        }
    }

    // =========================================================================
    // 1. YOUTUBE
    // =========================================================================

    private fun handleYouTube(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val rules = activeRules["youtube"] ?: emptySet()

        // ── Shorts blocking ───────────────────────────────────────────────────
        if (rules.contains("disable_shorts")) {
            if (isYouTubeShorts(rootNode, event)) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    overlayManager?.hideFeedBlockOverlay()
                    // Navigate to YouTube Home instead of closing the app
                    navigateToHome("com.google.android.youtube")
                    return
                }
            }
        }

        // ── Home/Feed blocking (Search-Only mode) ─────────────────────────────
        if (rules.contains("disable_home")) {
            val onHome   = isYouTubeHomeTab(rootNode)
            val onSearch = isSearchActive(rootNode, listOf("search_edit_text", "search_bar", "query"))

            if (onHome && !onSearch) {
                overlayManager?.showFeedBlockOverlay(
                    appName  = "YouTube",
                    title    = "Beranda YouTube Dibatasi",
                    subtitle = "Gunakan bilah pencarian di atas untuk menemukan video yang ingin ditonton.",
                    isDark   = true
                )
                return
            }
        }

        // If neither rule matched → hide overlay
        overlayManager?.hideFeedBlockOverlay()
    }

    /**
     * Detect YouTube Shorts using multiple signals:
     * 1. Known resource IDs (reel_watch_fragment_root, reel_recycler)
     * 2. Fragment class name heuristics
     * 3. Event class name from TYPE_WINDOW_STATE_CHANGED
     * 4. Text/content description fallback ("Shorts")
     */
    private fun isYouTubeShorts(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        // Signal 1: Resource IDs (most reliable when present)
        val shortRootIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_root"
        )
        for (id in shortRootIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) return true
        }

        // Signal 2: Fragment class name in window-state event
        val className = event.className?.toString() ?: ""
        if (className.contains("Shorts", ignoreCase = true) ||
            className.contains("Reel", ignoreCase = true)   ||
            className.contains("ShortsPlayer", ignoreCase = true)) {
            return true
        }

        // Signal 3: Structural text ("Shorts" label visible, Shorts tab highlighted)
        if (findNodeByViewId(root, listOf("reel_watch", "shorts_bottom"))) return true

        // Signal 4: Broad text match (least reliable — only as last resort)
        val shortsBarNode = root.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsBarNode.isNullOrEmpty()) {
            for (n in shortsBarNode) {
                if (n.isSelected || n.isChecked) return true
            }
        }

        return false
    }

    private fun isYouTubeHomeTab(root: AccessibilityNodeInfo): Boolean {
        // Check the bottom nav "Home" tab is selected
        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                    // Parent may carry isSelected
                    val parent = n.parent
                    if (parent != null && (parent.isSelected || parent.isChecked)) return true
                }
            }
        }
        return false
    }

    // =========================================================================
    // 2. INSTAGRAM
    // =========================================================================

    private fun handleInstagram(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val rules = activeRules["instagram"] ?: emptySet()
        if (rules.isEmpty()) {
            overlayManager?.hideFeedBlockOverlay()
            return
        }

        val now = System.currentTimeMillis()

        // ── Reels detection (clips_viewer, ReelViewerFragment, reel_player) ──
        if (rules.contains("disable_reels") ||
            rules.contains("dm_profile_only") ||
            rules.contains("disable_friend_reels")) {

            if (isInstagramReels(rootNode, event)) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    overlayManager?.hideFeedBlockOverlay()
                    // Go to Instagram Home/DM instead of closing app
                    navigateToHome("com.instagram.android")
                }
                return
            }
        }

        // ── Stories detection ─────────────────────────────────────────────────
        if (rules.contains("hide_stories") || rules.contains("dm_profile_only")) {
            if (isInstagramStories(rootNode, event)) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    overlayManager?.hideFeedBlockOverlay()
                    navigateToHome("com.instagram.android")
                }
                return
            }
        }

        // ── DM & Profile Only strict mode ─────────────────────────────────────
        if (rules.contains("dm_profile_only")) {
            val inDm      = isInstagramDM(rootNode)
            val inProfile = isInstagramProfile(rootNode)

            if (!inDm && !inProfile) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    // Try tapping DM icon; fallback to home intent
                    val dmNode = findClickableNode(rootNode, listOf("Direct", "Pesan", "direct_inbox"))
                    if (dmNode != null) {
                        dmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        navigateToHome("com.instagram.android")
                    }
                }
                return
            }
        }

        // ── Feed/Home overlay ─────────────────────────────────────────────────
        if (rules.contains("disable_feeds") || rules.contains("disable_home")) {
            if (isInstagramHome(rootNode)) {
                overlayManager?.showFeedBlockOverlay(
                    appName  = "Instagram",
                    title    = "Feeds Instagram Dibatasi",
                    subtitle = "DM & halaman Profil tetap dapat diakses normal.",
                    isDark   = true
                )
                return
            }
        }

        overlayManager?.hideFeedBlockOverlay()
    }

    private fun isInstagramReels(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val reelsIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/reels_video_container",
            "com.instagram.android:id/clips_swipe_refresh"
        )
        for (id in reelsIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        return cls.contains("Reel", ignoreCase = true) || cls.contains("Clips", ignoreCase = true)
    }

    private fun isInstagramStories(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val storyIds = listOf(
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/viewer_media_view_root",
            "com.instagram.android:id/story_viewer_fragment"
        )
        for (id in storyIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        return cls.contains("Story", ignoreCase = true)
    }

    private fun isInstagramHome(root: AccessibilityNodeInfo): Boolean {
        // The main feed RecyclerView is present and home tab is selected
        val feedIds = listOf(
            "com.instagram.android:id/feed_tab",
            "com.instagram.android:id/tab_icon",
            "com.instagram.android:id/row_feed_photo_imageview"
        )
        for (id in feedIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                }
            }
        }
        // Fallback: Home tab text selected
        return isNodeSelected(root, listOf("Beranda", "Home"))
    }

    private fun isInstagramDM(root: AccessibilityNodeInfo): Boolean {
        val dmIds = listOf(
            "com.instagram.android:id/direct_inbox_container",
            "com.instagram.android:id/thread_list_fragment_container",
            "com.instagram.android:id/direct_v2_message_thread_view"
        )
        for (id in dmIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }

    private fun isInstagramProfile(root: AccessibilityNodeInfo): Boolean {
        val profileIds = listOf(
            "com.instagram.android:id/profile_tab",
            "com.instagram.android:id/self_profile_name_and_bio"
        )
        for (id in profileIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }

    // =========================================================================
    // 3. TIKTOK
    // =========================================================================

    private fun handleTikTok(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val rules = activeRules["tiktok"] ?: emptySet()
        if (rules.isEmpty()) {
            overlayManager?.hideFeedBlockOverlay()
            return
        }

        val now = System.currentTimeMillis()

        // ── DM & Profile Only ─────────────────────────────────────────────────
        if (rules.contains("dm_profile_only")) {
            val inInbox   = isNodeSelected(rootNode, listOf("Kotak Masuk", "Inbox"))
            val inProfile = isNodeSelected(rootNode, listOf("Profil", "Profile"))

            if (!inInbox && !inProfile) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val inboxNode = findClickableNode(rootNode, listOf("Kotak Masuk", "Inbox"))
                    if (inboxNode != null) {
                        inboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        navigateToHome("com.zhiliaoapp.musically")
                    }
                }
                return
            }
        }

        // ── Disable FYP (switch to Following) ────────────────────────────────
        if (rules.contains("disable_fyp")) {
            if (isNodeSelected(rootNode, listOf("Untuk Anda", "For You"))) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val followingTab = findClickableNode(rootNode, listOf("Mengikuti", "Following", "Friends"))
                    followingTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return
            }
        }

        // ── Hide Stories ──────────────────────────────────────────────────────
        if (rules.contains("hide_stories")) {
            if (isTikTokStory(rootNode, event)) {
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    navigateToHome("com.zhiliaoapp.musically")
                }
                return
            }
        }

        // ── Explore/Feed overlay ──────────────────────────────────────────────
        if (rules.contains("hide_explore_feeds")) {
            val inExplore = isNodeSelected(rootNode, listOf("Temukan", "Explore", "Pencarian"))
            val searching = isSearchActive(rootNode, listOf("search_input", "search_box", "search_bar"))

            if (inExplore && !searching) {
                overlayManager?.showFeedBlockOverlay(
                    appName  = "TikTok",
                    title    = "Eksplorasi Feed Dibatasi",
                    subtitle = "Gunakan bilah pencarian untuk konten yang ingin dicari.",
                    isDark   = true
                )
                return
            }
        }

        overlayManager?.hideFeedBlockOverlay()
    }

    private fun isTikTokStory(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val storyIds = listOf("story_viewer", "story_view", "story_container")
        if (findNodeByViewId(root, storyIds)) return true
        val cls = event.className?.toString() ?: ""
        return cls.contains("Story", ignoreCase = true)
    }

    // =========================================================================
    // 4. TWITTER / X
    // =========================================================================

    private fun handleTwitter(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val rules = activeRules["twitter"] ?: emptySet()
        if (rules.isEmpty()) {
            overlayManager?.hideFeedBlockOverlay()
            return
        }

        // ── Hide "For You" feed (show only "Following") ───────────────────────
        if (rules.contains("disable_for_you_feed")) {
            if (isTwitterForYouTab(rootNode)) {
                overlayManager?.showFeedBlockOverlay(
                    appName  = "X (Twitter)",
                    title    = "Tab 'Untuk Anda' Dibatasi",
                    subtitle = "Beralih ke tab 'Mengikuti' untuk melihat linimasa.",
                    isDark   = true
                )
                return
            }
        }

        // ── Endless scroll in Media Viewer ────────────────────────────────────
        if (rules.contains("disable_media_endless_scroll")) {
            if (isTwitterMediaViewer(rootNode, event)) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    // Block swipe-based scrolling in media viewer by going back
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }
        }

        // ── Trending Topics overlay ───────────────────────────────────────────
        if (rules.contains("hide_trending")) {
            if (isTwitterTrending(rootNode)) {
                overlayManager?.showFeedBlockOverlay(
                    appName  = "X (Twitter)",
                    title    = "Trending Topics Disembunyikan",
                    subtitle = "Gunakan pencarian untuk menemukan topik secara langsung.",
                    isDark   = true
                )
                return
            }
        }

        overlayManager?.hideFeedBlockOverlay()
    }

    private fun isTwitterForYouTab(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("For you", "Untuk Anda", "For You")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected || n.isChecked) return true
                    val parent = n.parent
                    if (parent != null && (parent.isSelected || parent.isChecked)) return true
                }
            }
        }
        return false
    }

    private fun isTwitterMediaViewer(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val mediaIds = listOf(
            "com.twitter.android:id/gallery_pager",
            "com.twitter.android:id/media_viewer"
        )
        for (id in mediaIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        val cls = event.className?.toString() ?: ""
        return cls.contains("Gallery", ignoreCase = true) || cls.contains("MediaViewer", ignoreCase = true)
    }

    private fun isTwitterTrending(root: AccessibilityNodeInfo): Boolean {
        // Trending / Explore tab with trend list visible
        val exploreLabels = listOf("Explore", "Eksplorasi", "Search")
        if (!isNodeSelected(root, exploreLabels)) return false
        // Check that trend items are present (not just the search bar)
        val trendIds = listOf(
            "com.twitter.android:id/trend_name",
            "com.twitter.android:id/explore_trending_section"
        )
        for (id in trendIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }

    // =========================================================================
    // Navigation Helper: go to app Home instead of closing the app
    // =========================================================================

    /**
     * Relaunches the target app's main/home activity. This is safer than
     * GLOBAL_ACTION_BACK which can close the app if no back-stack exists
     * (e.g. the user entered Shorts directly from a notification or cold start).
     */
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
                // Fallback: press back once (may not reach home but better than nothing)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // =========================================================================
    // Accessibility Node Utilities
    // =========================================================================

    /**
     * Returns true if any node matching one of [keywords] in text, contentDescription,
     * or viewIdResourceName has isSelected or isChecked == true.
     */
    private fun isNodeSelected(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text  = node.text?.toString() ?: ""
        val desc  = node.contentDescription?.toString() ?: ""

        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)) {
                if (node.isSelected || node.isChecked) return true
                val parent = node.parent
                if (parent != null && (parent.isSelected || parent.isChecked)) return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isNodeSelected(node.getChild(i), keywords)) return true
        }
        return false
    }

    /**
     * Returns true if any node's viewIdResourceName contains one of [idFragments].
     * Cheaper than findAccessibilityNodeInfosByViewId when you need partial match.
     */
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

    /**
     * Returns the first clickable node whose text, contentDescription, or
     * viewIdResourceName contains one of [keywords].
     */
    private fun findClickableNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text  = node.text?.toString() ?: ""
        val desc  = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) ||
                desc.contains(kw, ignoreCase = true) ||
                viewId.contains(kw, ignoreCase = true)) {
                if (node.isClickable) return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findClickableNode(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    /**
     * Returns true if a focused search/text-input field containing one of [keywords]
     * in its viewId or text is currently focused (user is actively searching).
     */
    private fun isSearchActive(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        val text   = node.text?.toString() ?: ""
        for (kw in keywords) {
            if ((viewId.contains(kw, ignoreCase = true) || text.contains(kw, ignoreCase = true)) &&
                (node.isFocused || node.isEditable)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isSearchActive(node.getChild(i), keywords)) return true
        }
        return false
    }

    private fun performSwipeUpGesture() {
        val dm    = resources.displayMetrics
        val w     = dm.widthPixels.toFloat()
        val h     = dm.heightPixels.toFloat()
        val path  = Path().apply {
            moveTo(w / 2, h * 0.75f)
            lineTo(w / 2, h * 0.25f)
        }
        val stroke  = GestureDescription.StrokeDescription(path, 0, 150)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
