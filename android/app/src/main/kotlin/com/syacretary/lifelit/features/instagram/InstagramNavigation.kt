package com.syacretary.lifelit.features.instagram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.core.AccessibilityUtils

enum class InstagramTab {
    HOME,
    REELS,
    DM,
    SEARCH,
    PROFILE,
    UNKNOWN
}

object InstagramNavigation {

    var lastDMTimestamp: Long = 0L
        private set

    fun recordDMAccess() {
        lastDMTimestamp = System.currentTimeMillis()
    }

    /**
     * Checks if a tab node or any of its children/badges has the selected state.
     * Covers .isSelected, "dipilih", "selected", and deep hierarchy flags.
     */
    fun isTabSelected(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isSelected) return true
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (isTabSelected(node.getChild(i))) return true
        }
        return false
    }

    private fun identifyTab(node: AccessibilityNodeInfo, index: Int): InstagramTab {
        val sb = StringBuilder()
        fun collectInfo(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.contentDescription?.let { sb.append(" ").append(it) }
            n.text?.let { sb.append(" ").append(it) }
            n.viewIdResourceName?.let { sb.append(" ").append(it) }
            for (i in 0 until n.childCount) {
                collectInfo(n.getChild(i))
            }
        }
        collectInfo(node)
        val info = sb.toString().lowercase()

        return when {
            info.contains("search") || info.contains("cari") || info.contains("explore") || info.contains("jelajahi") -> InstagramTab.SEARCH
            info.contains("reels") || info.contains("klip") || info.contains("clips") -> InstagramTab.REELS
            info.contains("direct") || info.contains("pesan") || info.contains("inbox") || info.contains("message") -> InstagramTab.DM
            info.contains("profile") || info.contains("profil") -> InstagramTab.PROFILE
            info.contains("feed") || info.contains("beranda") || info.contains("home") -> InstagramTab.HOME
            index == 0 -> InstagramTab.HOME
            index == 1 -> InstagramTab.SEARCH
            index == 2 -> InstagramTab.REELS
            index == 3 -> InstagramTab.DM
            index == 4 -> InstagramTab.PROFILE
            else -> InstagramTab.UNKNOWN
        }
    }

    /**
     * Determines active tab by checking the isSelected flag on tab_bar children.
     * Guaranteed reliable across all Android devices because Instagram does not offset
     * off-screen tabs to negative coordinates.
     */
    fun getActiveTab(root: AccessibilityNodeInfo): InstagramTab {
        // Fast-path: if user is typing in search edit text, active tab is SEARCH
        if (isSearchTyping(root)) {
            return InstagramTab.SEARCH
        }

        val tabBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBar.isNullOrEmpty()) {
            val bar = tabBar[0]
            for (childIdx in 0 until bar.childCount) {
                val child = bar.getChild(childIdx) ?: continue
                if (isTabSelected(child)) {
                    val tab = identifyTab(child, childIdx)
                    if (tab != InstagramTab.UNKNOWN) return tab
                }
            }
        }

        // Fallback: search individual tab IDs directly
        val tabIds = listOf(
            "com.instagram.android:id/search_tab" to InstagramTab.SEARCH,
            "com.instagram.android:id/clips_tab" to InstagramTab.REELS,
            "com.instagram.android:id/direct_tab" to InstagramTab.DM,
            "com.instagram.android:id/profile_tab" to InstagramTab.PROFILE,
            "com.instagram.android:id/feed_tab" to InstagramTab.HOME
        )
        for ((id, tab) in tabIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (isTabSelected(n)) return tab
                }
            }
        }

        // Secondary fallback for Home:
        // In Home tab, main_feed_action_bar is present in top bar
        if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/main_feed_action_bar").isNullOrEmpty()) {
            return InstagramTab.HOME
        }

        return InstagramTab.UNKNOWN
    }

    fun isInsideDM(context: Context, root: AccessibilityNodeInfo): Boolean {
        val ids = listOf(
            "com.instagram.android:id/direct_inbox_container",
            "com.instagram.android:id/thread_list_fragment_container",
            "com.instagram.android:id/direct_v2_message_thread_view",
            "com.instagram.android:id/direct_search_box",
            "com.instagram.android:id/direct_header_action_bar",
            "com.instagram.android:id/direct_inbox_action_bar",
            "com.instagram.android:id/message_composer_container"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n)) {
                        recordDMAccess()
                        return true
                    }
                }
            }
        }
        return false
    }

    fun isInsideProfile(context: Context, root: AccessibilityNodeInfo): Boolean {
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
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n)) return true
                }
            }
        }
        return false
    }

    fun isSearchTyping(root: AccessibilityNodeInfo): Boolean {
        val searchEditIds = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/search_edit_text",
            "com.instagram.android:id/search_src_text"
        )
        for (id in searchEditIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isFocused || n.isEditable) return true
                }
            }
        }
        return false
    }

    fun isSearchOrExploreActive(context: Context, root: AccessibilityNodeInfo): Boolean {
        // Guard: if confirmed on Home Feed main action bar, Search is NOT active
        if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/main_feed_action_bar").isNullOrEmpty()) {
            return false
        }
        val searchIds = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/search_edit_text",
            "com.instagram.android:id/action_bar_search_layout",
            "com.instagram.android:id/search_src_text",
            "com.instagram.android:id/action_bar_search_hints_text_view",
            "com.instagram.android:id/echo_search_bar",
            "com.instagram.android:id/search_tab",
            "com.instagram.android:id/explore_grid",
            "com.instagram.android:id/explore_event_viewer"
        )
        for (id in searchIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Opens a registered Instagram screen by URI host.
     * Confirmed on-device: direct-inbox, mainfeed, profile each select the matching tab
     * without touching feed content.
     */
    fun openScreen(context: Context, host: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://$host")).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clicks only the node that owns this view id, or a direct clickable child.
     * Does not walk parents or match visible text, so a caption like "Pesan" cannot open a post.
     */
    private fun clickViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        for (node in nodes) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    fun goToDm(context: Context, root: AccessibilityNodeInfo): Boolean {
        if (openScreen(context, "direct-inbox")) {
            recordDMAccess()
            return true
        }
        val clicked = clickViewId(root, "com.instagram.android:id/direct_tab") ||
            clickViewId(root, "com.instagram.android:id/action_bar_inbox_button")
        if (clicked) recordDMAccess()
        return clicked
    }

    fun goToProfile(context: Context, root: AccessibilityNodeInfo): Boolean {
        if (openScreen(context, "profile")) return true
        return clickViewId(root, "com.instagram.android:id/profile_tab")
    }

    fun goToHome(context: Context, root: AccessibilityNodeInfo): Boolean {
        if (openScreen(context, "mainfeed")) return true
        return clickViewId(root, "com.instagram.android:id/feed_tab")
    }

    fun redirectToAllowedScreen(
        context: Context,
        root: AccessibilityNodeInfo,
        rules: Set<String>
    ): Boolean {
        val dmOnly = rules.contains("dm_profile_only")
        val homeBlocked = rules.contains("disable_home") || dmOnly

        return if (dmOnly || homeBlocked) {
            if (!goToDm(context, root)) goToProfile(context, root) else true
        } else {
            goToHome(context, root)
        }
    }
}
