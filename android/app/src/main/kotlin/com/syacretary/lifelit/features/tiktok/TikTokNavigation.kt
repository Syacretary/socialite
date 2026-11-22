package com.syacretary.lifelit.features.tiktok

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.core.AccessibilityUtils

enum class TikTokTab {
    HOME,
    FRIENDS,
    CREATE,
    INBOX,
    PROFILE,
    UNKNOWN
}

/**
 * TikTok Navigation and Tab Detection Helper.
 * Handles bottom navigation, top feed tabs (For You vs Following),
 * and DM / Profile screen detection across TikTok variants.
 */
object TikTokNavigation {

    var lastDMTimestamp: Long = 0L
        private set

    fun recordDMAccess() {
        lastDMTimestamp = System.currentTimeMillis()
    }

    private fun isTabSelected(node: AccessibilityNodeInfo?): Boolean {
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
     * Identifies the currently active bottom navigation tab in TikTok.
     */
    fun getActiveTab(root: AccessibilityNodeInfo): TikTokTab {
        // 1. Check bottom navigation tabs
        val bottomTabs = listOf(
            listOf("Beranda", "Home") to TikTokTab.HOME,
            listOf("Teman", "Friends", "Now") to TikTokTab.FRIENDS,
            listOf("Kotak Masuk", "Inbox", "Pesan", "Notifications") to TikTokTab.INBOX,
            listOf("Profil", "Profile", "Saya", "Me") to TikTokTab.PROFILE
        )

        for ((keywords, tab) in bottomTabs) {
            for (kw in keywords) {
                val nodes = root.findAccessibilityNodeInfosByText(kw)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (isTabSelected(node) || isTabSelected(node.parent)) {
                            return tab
                        }
                    }
                }
            }
        }

        // 2. Direct screen content verification
        if (isInsideDM(root)) return TikTokTab.INBOX
        if (isInsideProfile(root)) return TikTokTab.PROFILE

        return TikTokTab.UNKNOWN
    }

    /**
     * Checks if user is inside TikTok's Inbox / DM messaging screen.
     */
    fun isInsideDM(root: AccessibilityNodeInfo): Boolean {
        val dmViewIds = listOf(
            "com.zhiliaoapp.musically:id/chat_list",
            "com.zhiliaoapp.musically:id/chat_room",
            "com.zhiliaoapp.musically:id/message_list",
            "com.zhiliaoapp.musically:id/text_message_edit",
            "com.zhiliaoapp.musically:id/dm_container",
            "com.ss.android.ugc.trill:id/chat_list",
            "com.ss.android.ugc.trill:id/chat_room",
            "com.ss.android.ugc.trill:id/message_list",
            "com.ss.android.ugc.trill:id/text_message_edit",
            "com.ss.android.ugc.trill:id/dm_container"
        )
        for (id in dmViewIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) {
                recordDMAccess()
                return true
            }
        }

        val dmTexts = listOf("Permintaan pesan", "Message requests", "Obrolan", "Chat", "Kirim pesan")
        for (text in dmTexts) {
            if (!root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()) {
                recordDMAccess()
                return true
            }
        }
        return false
    }

    /**
     * Checks if user is inside TikTok's user profile page.
     */
    fun isInsideProfile(root: AccessibilityNodeInfo): Boolean {
        val profileViewIds = listOf(
            "com.zhiliaoapp.musically:id/profile_header",
            "com.zhiliaoapp.musically:id/user_name",
            "com.zhiliaoapp.musically:id/edit_profile",
            "com.zhiliaoapp.musically:id/bio_text",
            "com.ss.android.ugc.trill:id/profile_header",
            "com.ss.android.ugc.trill:id/user_name",
            "com.ss.android.ugc.trill:id/edit_profile",
            "com.ss.android.ugc.trill:id/bio_text"
        )
        for (id in profileViewIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }

        val profileTexts = listOf("Edit profil", "Edit profile", "Bagikan profil", "Share profile")
        for (text in profileTexts) {
            if (!root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()) return true
        }
        return false
    }

    /**
     * Navigates to TikTok Inbox tab.
     */
    fun clickInbox(root: AccessibilityNodeInfo): Boolean {
        val inboxKeywords = listOf("Kotak Masuk", "Inbox", "Pesan", "Notifications")
        for (kw in inboxKeywords) {
            val node = AccessibilityUtils.findClickableNode(root, listOf(kw))
            if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                recordDMAccess()
                return true
            }
            val textNodes = root.findAccessibilityNodeInfosByText(kw)
            if (!textNodes.isNullOrEmpty()) {
                for (n in textNodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) {
                        recordDMAccess()
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Navigates to TikTok Profile tab.
     */
    fun clickProfile(root: AccessibilityNodeInfo): Boolean {
        val profileKeywords = listOf("Profil", "Profile", "Saya", "Me")
        for (kw in profileKeywords) {
            val node = AccessibilityUtils.findClickableNode(root, listOf(kw))
            if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val textNodes = root.findAccessibilityNodeInfosByText(kw)
            if (!textNodes.isNullOrEmpty()) {
                for (n in textNodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    /**
     * Detects if the "For You" / "Untuk Anda" tab is selected on Home feed.
     */
    fun isForYouTabSelected(root: AccessibilityNodeInfo): Boolean {
        val fypKeywords = listOf("Untuk Anda", "For You", "FYP")
        for (kw in fypKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (isTabSelected(node) || isTabSelected(node.parent)) return true
                }
            }
        }
        return false
    }

    /**
     * Switches from FYP to "Mengikuti" / "Following" tab.
     */
    fun clickFollowingTab(root: AccessibilityNodeInfo): Boolean {
        val followingKeywords = listOf("Mengikuti", "Following", "Teman", "Friends")
        for (kw in followingKeywords) {
            val node = AccessibilityUtils.findClickableNode(root, listOf(kw))
            if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val textNodes = root.findAccessibilityNodeInfosByText(kw)
            if (!textNodes.isNullOrEmpty()) {
                for (n in textNodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(n)) return true
                }
            }
        }
        return false
    }

    /**
     * Checks if a Story is currently open.
     */
    fun isStoryActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        val storyIds = listOf("story_viewer", "story_view", "story_container", "story_progress_bar")
        if (AccessibilityUtils.findNodeByViewId(root, storyIds)) return true

        val cls = event?.className?.toString() ?: ""
        return cls.contains("Story", ignoreCase = true)
    }
}
