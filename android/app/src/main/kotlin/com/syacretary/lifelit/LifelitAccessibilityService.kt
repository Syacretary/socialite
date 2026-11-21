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

class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTimestamp = 0L

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES = "active_feature_rules"

        // Map: appId (e.g. "instagram") -> Set of blocked feature IDs (e.g. {"disable_reels", "disable_feeds"})
        private val activeRules = mutableMapOf<String, MutableSet<String>>()

        fun isServiceRunning(): Boolean = instance != null

        fun syncRules(context: Context, rules: Map<String, List<String>>) {
            activeRules.clear()
            rules.forEach { (appId, featureList) ->
                activeRules[appId] = featureList.toMutableSet()
            }

            // Persist to SharedPreferences so service retains rules even if restarted by OS
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val flattened = rules.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }
            editor.putString(KEY_RULES, flattened)
            editor.apply()
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
        overlayManager?.hideFeedBlockOverlay()
        overlayManager = null
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        overlayManager?.hideFeedBlockOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Debounce frequent content changes (at most once every 250ms per action)
        val now = System.currentTimeMillis()

        when {
            packageName.contains("youtube") -> {
                handleYouTube(event)
            }
            packageName.contains("instagram") -> {
                if (now - lastActionTimestamp > 250) {
                    handleInstagram(event)
                }
            }
            packageName.contains("zhiliaoapp.musically") || packageName.contains("trill") -> {
                if (now - lastActionTimestamp > 250) {
                    handleTikTok(event)
                }
            }
            else -> {
                // User switched away from target apps -> hide any active feed overlays
                overlayManager?.hideFeedBlockOverlay()
            }
        }
    }

    // =========================================================================
    // 1. YOUTUBE SMART FEATURE BLOCKING
    // =========================================================================
    private fun handleYouTube(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val youtubeRules = activeRules["youtube"] ?: emptySet()

        // Feature 1: Disable Shorts (Matikan Shorts)
        if (youtubeRules.contains("disable_shorts")) {
            val isShortsActive = findNodeByTextOrDescOrId(
                rootNode,
                listOf("Shorts", "shorts_player", "reel_player", "shorts_video_player")
            )

            if (isShortsActive) {
                overlayManager?.hideFeedBlockOverlay()
                lastActionTimestamp = System.currentTimeMillis()
                // Redirect user away from addictive Shorts loop
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // Feature 2: Disable Home Feed / Search Only Mode (Matikan Beranda / Search Only)
        if (youtubeRules.contains("disable_home")) {
            val isHomeTabSelected = isTabSelected(rootNode, listOf("Beranda", "Home"))
            val isSearchActive = isSearchPageOpen(rootNode, listOf("Cari", "Search", "search_edit_text"))

            if (isHomeTabSelected && !isSearchActive) {
                // Show adaptive overlay over the video feed while leaving search & navbar interactive
                overlayManager?.showFeedBlockOverlay(
                    appName = "YouTube",
                    title = "Beranda YouTube Dibatasi",
                    subtitle = "Gunakan bilah pencarian di atas untuk menonton video yang Anda cari.",
                    isDark = true
                )
            } else {
                overlayManager?.hideFeedBlockOverlay()
            }
        } else {
            overlayManager?.hideFeedBlockOverlay()
        }
    }

    // =========================================================================
    // 2. INSTAGRAM SMART FEATURE BLOCKING
    // =========================================================================
    private fun handleInstagram(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val igRules = activeRules["instagram"] ?: emptySet()
        if (igRules.isEmpty()) {
            overlayManager?.hideFeedBlockOverlay()
            return
        }

        // Feature: Matikan Reels & Reels Teman
        val isReelsViewer = findNodeByTextOrDescOrId(
            rootNode,
            listOf("clips_viewer", "reels_viewer", "Reels", "reel_player", "reels_video_container")
        )

        if (isReelsViewer) {
            if (igRules.contains("disable_reels") || igRules.contains("dm_profile_only") || igRules.contains("disable_friend_reels")) {
                overlayManager?.hideFeedBlockOverlay()
                lastActionTimestamp = System.currentTimeMillis()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // Feature: Sembunyikan Stories
        if (igRules.contains("hide_stories") || igRules.contains("dm_profile_only")) {
            val isStoryViewer = findNodeByTextOrDescOrId(
                rootNode,
                listOf("reel_viewer", "stories_viewer", "viewer_media_view_root", "story_viewer_fragment")
            )
            if (isStoryViewer) {
                lastActionTimestamp = System.currentTimeMillis()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // Feature: DM & Profile Only (Strict Focus Mode)
        if (igRules.contains("dm_profile_only")) {
            val isDirectActive = findNodeByTextOrDescOrId(
                rootNode,
                listOf("Direct", "Pesan", "direct_inbox", "thread_fragment", "direct_v2")
            )
            val isProfileActive = findNodeByTextOrDescOrId(
                rootNode,
                listOf("Profil", "Profile", "profile_tab", "self_profile")
            )

            // If user is neither in DM nor Profile, navigate to DM
            if (!isDirectActive && !isProfileActive) {
                lastActionTimestamp = System.currentTimeMillis()
                // Adaptive redirect: try clicking direct button or open direct intent
                val directNode = findNodeByTextOrDescOrIdReturnNode(rootNode, listOf("Direct", "Pesan", "direct"))
                if (directNode != null && directNode.isClickable) {
                    directNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }
        }

        // Feature: Matikan Feeds (Adaptive Overlay over the scrollable feed)
        if (igRules.contains("disable_feeds") || igRules.contains("disable_home")) {
            val isMainFeedActive = isTabSelected(rootNode, listOf("Beranda", "Home", "feed_tab"))

            if (isMainFeedActive) {
                overlayManager?.showFeedBlockOverlay(
                    appName = "Instagram",
                    title = "Feeds Instagram Dibatasi",
                    subtitle = "DM & Halaman Profil tetap dapat diakses normal.",
                    isDark = true
                )
            } else {
                overlayManager?.hideFeedBlockOverlay()
            }
        } else {
            overlayManager?.hideFeedBlockOverlay()
        }
    }

    // =========================================================================
    // 3. TIKTOK SMART FEATURE BLOCKING
    // =========================================================================
    private fun handleTikTok(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val ttRules = activeRules["tiktok"] ?: emptySet()
        if (ttRules.isEmpty()) {
            overlayManager?.hideFeedBlockOverlay()
            return
        }

        // Feature: DM & Profile Only
        if (ttRules.contains("dm_profile_only")) {
            val isInboxActive = isTabSelected(rootNode, listOf("Kotak Masuk", "Inbox"))
            val isProfileActive = isTabSelected(rootNode, listOf("Profil", "Profile"))

            if (!isInboxActive && !isProfileActive) {
                lastActionTimestamp = System.currentTimeMillis()
                // Attempt to click Inbox tab
                val inboxNode = findNodeByTextOrDescOrIdReturnNode(rootNode, listOf("Kotak Masuk", "Inbox"))
                if (inboxNode != null && inboxNode.isClickable) {
                    inboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }
        }

        // Feature: Matikan FYP (Auto-switch to Following / Mengikuti)
        if (ttRules.contains("disable_fyp")) {
            val isFypSelected = isTabSelected(rootNode, listOf("Untuk Anda", "For You"))
            if (isFypSelected) {
                lastActionTimestamp = System.currentTimeMillis()
                val followingTab = findNodeByTextOrDescOrIdReturnNode(rootNode, listOf("Mengikuti", "Following"))
                if (followingTab != null && followingTab.isClickable) {
                    followingTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }

        // Feature: Matikan Video Pendek (Auto-skip short vertical videos)
        if (ttRules.contains("disable_short_feed")) {
            val isMainVideoFeed = findNodeByTextOrDescOrId(rootNode, listOf("viewpager", "vertical_viewpager", "feed_player"))
            if (isMainVideoFeed) {
                // Perform quick swipe up gesture to skip past short video
                performSwipeUpGesture()
                lastActionTimestamp = System.currentTimeMillis()
            }
        }

        // Feature: Sembunyikan Stories
        if (ttRules.contains("hide_stories")) {
            val isStoryViewer = findNodeByTextOrDescOrId(rootNode, listOf("story_viewer", "story_view"))
            if (isStoryViewer) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                lastActionTimestamp = System.currentTimeMillis()
                return
            }
        }

        // Feature: Sembunyikan Feeds di Explore
        if (ttRules.contains("hide_explore_feeds")) {
            val isExploreActive = isTabSelected(rootNode, listOf("Temukan", "Explore", "Pencarian"))
            val isSearchEditing = isSearchPageOpen(rootNode, listOf("search_input", "search_box"))

            if (isExploreActive && !isSearchEditing) {
                overlayManager?.showFeedBlockOverlay(
                    appName = "TikTok",
                    title = "Eksplorasi Feed Dibatasi",
                    subtitle = "Gunakan bilah pencarian langsung untuk konten terarah.",
                    isDark = true
                )
            } else {
                overlayManager?.hideFeedBlockOverlay()
            }
        } else {
            overlayManager?.hideFeedBlockOverlay()
        }
    }

    // =========================================================================
    // ACCESSIBILITY NODE UTILITIES
    // =========================================================================

    private fun findNodeByTextOrDescOrId(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) ||
                desc.contains(kw, ignoreCase = true) ||
                viewId.contains(kw, ignoreCase = true)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (findNodeByTextOrDescOrId(node.getChild(i), keywords)) {
                return true
            }
        }
        return false
    }

    private fun findNodeByTextOrDescOrIdReturnNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) ||
                desc.contains(kw, ignoreCase = true) ||
                viewId.contains(kw, ignoreCase = true)) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val found = findNodeByTextOrDescOrIdReturnNode(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    private fun isTabSelected(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        for (kw in keywords) {
            if ((text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)) && node.isSelected) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (isTabSelected(node.getChild(i), keywords)) {
                return true
            }
        }
        return false
    }

    private fun isSearchPageOpen(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        for (kw in keywords) {
            if ((viewId.contains(kw, ignoreCase = true) || text.contains(kw, ignoreCase = true)) && node.isFocused) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (isSearchPageOpen(node.getChild(i), keywords)) {
                return true
            }
        }
        return false
    }

    private fun performSwipeUpGesture() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2
        val startY = height * 0.75f
        val endY = height * 0.25f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 150)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
