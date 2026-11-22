package com.syacretary.lifelit.features.tiktok

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        // 1. Check exact resource IDs from verified TikTok hierarchy
        val idTabs = listOf(
            listOf("com.ss.android.ugc.trill:id/oqe", "com.zhiliaoapp.musically:id/oqe") to TikTokTab.INBOX,
            listOf("com.ss.android.ugc.trill:id/oqf", "com.zhiliaoapp.musically:id/oqf") to TikTokTab.PROFILE,
            listOf("com.ss.android.ugc.trill:id/oqd", "com.zhiliaoapp.musically:id/oqd") to TikTokTab.HOME,
            listOf("com.ss.android.ugc.trill:id/etc", "com.zhiliaoapp.musically:id/etc") to TikTokTab.FRIENDS
        )
        for ((ids, tab) in idTabs) {
            for (id in ids) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (isTabSelected(node)) return tab
                    }
                }
            }
        }

        // 2. Check bottom navigation tabs by text and content-desc
        val bottomTabs = listOf(
            listOf("Kotak Masuk", "Inbox", "Pesan", "Notifications") to TikTokTab.INBOX,
            listOf("Profil", "Profile", "Saya", "Me") to TikTokTab.PROFILE,
            listOf("Beranda", "Home") to TikTokTab.HOME,
            listOf("Teman", "Friends", "Now", "Shop") to TikTokTab.FRIENDS
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

        // 3. Direct screen content verification
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

    private fun hostPackage(root: AccessibilityNodeInfo): String {
        return root.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: "com.ss.android.ugc.trill"
    }

    private fun schemeFor(packageName: String): String {
        return if (packageName == "com.zhiliaoapp.musically") "snssdk1233" else "snssdk1180"
    }

    /**
     * Opens a TikTok screen inside the app that is already in front.
     * Confirmed on the installed trill build: tiktok://inbox selects Inbox,
     * tiktok://user/homepage selects Profile.
     */
    private fun openUri(context: Context, packageName: String, uri: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openScreen(context: Context, root: AccessibilityNodeInfo, path: String): Boolean {
        val pkg = hostPackage(root)
        if (openUri(context, pkg, "tiktok://$path")) return true
        return openUri(context, pkg, "${schemeFor(pkg)}://$path")
    }

    private fun clickViewId(root: AccessibilityNodeInfo, viewIds: List<String>): Boolean {
        for (viewId in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: continue
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        return false
    }

    /**
     * Navigates to TikTok Inbox tab.
     */
    fun goToInbox(context: Context, root: AccessibilityNodeInfo): Boolean {
        if (openScreen(context, root, "inbox")) {
            recordDMAccess()
            return true
        }
        val clicked = clickViewId(
            root,
            listOf("com.ss.android.ugc.trill:id/oqe", "com.zhiliaoapp.musically:id/oqe")
        )
        if (clicked) recordDMAccess()
        return clicked
    }

    /**
     * Navigates to TikTok Profile tab.
     */
    fun goToProfile(context: Context, root: AccessibilityNodeInfo): Boolean {
        if (openScreen(context, root, "user/homepage")) return true
        return clickViewId(
            root,
            listOf("com.ss.android.ugc.trill:id/oqf", "com.zhiliaoapp.musically:id/oqf")
        )
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
     * Detects if the "Following" / "Mengikuti" tab is selected on Home feed.
     */
    fun isFollowingTabSelected(root: AccessibilityNodeInfo): Boolean {
        val followingKeywords = listOf("Mengikuti", "Following", "Friends", "Teman")
        for (kw in followingKeywords) {
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
     * Switches from FYP to the Following chip. No registered URI selects that chip
     * on the installed build, so this clicks the chip itself and never a parent post.
     */
    fun goToFollowing(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("Mengikuti", "Following")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label) ?: continue
            for (node in nodes) {
                val own = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
                if (!own.contains(label, ignoreCase = true)) continue
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                val parent = node.parent ?: continue
                val parentLabel = (parent.text?.toString() ?: "") + (parent.contentDescription?.toString() ?: "")
                if (parent.isClickable && parentLabel.contains(label, ignoreCase = true) &&
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
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
