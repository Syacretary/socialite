package com.syacretary.lifelit.features.instagram

import android.content.Context
import android.graphics.Rect
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

    /**
     * Determines active tab by checking the isSelected flag on tab_bar children.
     * Guaranteed reliable across all Android devices because Instagram does not offset
     * off-screen tabs to negative coordinates.
     */
    fun getActiveTab(root: AccessibilityNodeInfo): InstagramTab {
        val tabBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBar.isNullOrEmpty()) {
            val bar = tabBar[0]
            for (childIdx in 0 until bar.childCount) {
                val child = bar.getChild(childIdx) ?: continue
                if (isTabSelected(child)) {
                    val resId = child.viewIdResourceName ?: ""
                    return when {
                        resId.contains("feed_tab") || childIdx == 0 -> InstagramTab.HOME
                        resId.contains("clips_tab") || childIdx == 1 -> InstagramTab.REELS
                        resId.contains("direct_tab") || childIdx == 2 -> InstagramTab.DM
                        resId.contains("search_tab") || childIdx == 3 -> InstagramTab.SEARCH
                        resId.contains("profile_tab") || childIdx == 4 -> InstagramTab.PROFILE
                        else -> InstagramTab.UNKNOWN
                    }
                }
            }
        }

        // Fallback: search individual tab IDs directly
        val tabIds = listOf(
            "com.instagram.android:id/feed_tab" to InstagramTab.HOME,
            "com.instagram.android:id/clips_tab" to InstagramTab.REELS,
            "com.instagram.android:id/direct_tab" to InstagramTab.DM,
            "com.instagram.android:id/search_tab" to InstagramTab.SEARCH,
            "com.instagram.android:id/profile_tab" to InstagramTab.PROFILE
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
        // In Home tab, action_bar_inbox_button or main_feed_action_bar is present in top bar
        if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/main_feed_action_bar").isNullOrEmpty()) {
            return InstagramTab.HOME
        }
        if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_inbox_button").isNullOrEmpty()) {
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

    fun isSearchOrExploreActive(context: Context, root: AccessibilityNodeInfo): Boolean {
        // Guard: if confirmed on Home Feed, Search is NOT active
        if (!root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/main_feed_action_bar").isNullOrEmpty()) {
            return false
        }
        val searchEditIds = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/search_edit_text",
            "com.instagram.android:id/action_bar_search_layout",
            "com.instagram.android:id/search_src_text"
        )
        for (id in searchEditIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n) && (n.isFocused || n.isEditable)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun clickDM(root: AccessibilityNodeInfo): Boolean {
        // 1. Direct tab by ID in bottom navbar
        val directTabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_tab")
        if (!directTabNodes.isNullOrEmpty()) {
            for (n in directTabNodes) {
                if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
            }
        }

        // 2. Child in tab_bar
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val resId = child.viewIdResourceName ?: ""
                if (resId.contains("direct_tab")) {
                    if (AccessibilityUtils.clickNodeOrDescendant(child)) return true
                }
            }
            if (tabBar.childCount >= 3) {
                val dmTab = tabBar.getChild(2)
                if (AccessibilityUtils.clickNodeOrDescendant(dmTab)) return true
            }
        }

        // 3. Action bar inbox button
        val dmIds = listOf(
            "com.instagram.android:id/action_bar_inbox_button",
            "com.instagram.android:id/action_bar_inbox_icon",
            "com.instagram.android:id/inbox_button"
        )
        for (id in dmIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }

        // 4. Text labels
        val dmLabels = listOf("Pesan", "Direct", "Messenger", "Inbox", "Chats")
        for (label in dmLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    fun clickProfile(root: AccessibilityNodeInfo): Boolean {
        // 1. profile_tab by ID
        val profileNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/profile_tab")
        if (!profileNodes.isNullOrEmpty()) {
            for (n in profileNodes) {
                if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
            }
        }

        // 2. tab_bar child
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val resId = child.viewIdResourceName ?: ""
                if (resId.contains("profile_tab")) {
                    if (AccessibilityUtils.clickNodeOrDescendant(child)) return true
                }
            }
            val count = tabBar.childCount
            if (count > 0) {
                val profileTab = tabBar.getChild(count - 1)
                if (AccessibilityUtils.clickNodeOrDescendant(profileTab)) return true
            }
        }

        // 3. Text search
        val profileLabels = listOf("Profil", "Profile")
        for (label in profileLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    fun clickHome(root: AccessibilityNodeInfo): Boolean {
        val feedNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (!feedNodes.isNullOrEmpty()) {
            for (n in feedNodes) {
                if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
            }
        }

        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            if (tabBar.childCount > 0) {
                val homeTab = tabBar.getChild(0)
                if (AccessibilityUtils.clickNodeOrDescendant(homeTab)) return true
            }
        }

        val homeLabels = listOf("Beranda", "Home")
        for (label in homeLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    fun clickSearch(root: AccessibilityNodeInfo): Boolean {
        val searchNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab")
        if (!searchNodes.isNullOrEmpty()) {
            for (n in searchNodes) {
                if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
            }
        }

        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val resId = child.viewIdResourceName ?: ""
                if (resId.contains("search_tab")) {
                    if (AccessibilityUtils.clickNodeOrDescendant(child)) return true
                }
            }
            if (tabBar.childCount >= 4) {
                val searchTab = tabBar.getChild(3)
                if (AccessibilityUtils.clickNodeOrDescendant(searchTab)) return true
            }
        }
        return false
    }

    fun redirectToAllowedScreen(root: AccessibilityNodeInfo, rules: Set<String>): Boolean {
        val dmOnly = rules.contains("dm_profile_only")
        val homeBlocked = rules.contains("disable_home") || dmOnly

        return if (dmOnly || homeBlocked) {
            if (!clickDM(root)) clickProfile(root) else true
        } else {
            clickHome(root)
        }
    }
}
