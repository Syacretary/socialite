package com.syacretary.lifelit.features.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AccessibilityUtils
import com.syacretary.lifelit.core.AppFeatureHandler

class TikTokHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    private var lastNavTimestamp = 0L
    private val NAV_COOLDOWN_MS = 1200L

    override fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>
    ): Boolean {
        if (rules.contains("hide_stories") && isStory(root, event)) {
            val now = System.currentTimeMillis()
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                AccessibilityUtils.navigateToHome(service, service, "com.zhiliaoapp.musically")
                return true
            }
        }
        return false
    }

    override fun evaluateState(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        rules: Set<String>,
        overlayManager: LifelitOverlayManager,
        scheduleHide: (delayMs: Long) -> Unit,
        hideImmediate: () -> Unit
    ) {
        if (rules.contains("dm_profile_only")) {
            val inInbox = AccessibilityUtils.isNodeSelected(root, listOf("Kotak Masuk", "Inbox"))
            val inProfile = AccessibilityUtils.isNodeSelected(root, listOf("Profil", "Profile"))
            if (!inInbox && !inProfile) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val inboxNode = AccessibilityUtils.findClickableNode(root, listOf("Kotak Masuk", "Inbox"))
                    if (inboxNode != null) inboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else AccessibilityUtils.navigateToHome(service, service, "com.zhiliaoapp.musically")
                }
                return
            }
        }

        if (rules.contains("disable_fyp")) {
            if (AccessibilityUtils.isNodeSelected(root, listOf("Untuk Anda", "For You"))) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    val followingTab = AccessibilityUtils.findClickableNode(root, listOf("Mengikuti", "Following", "Friends"))
                    followingTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return
            }
        }

        if (rules.contains("hide_explore_feeds")) {
            val inExplore = AccessibilityUtils.isNodeSelected(root, listOf("Temukan", "Explore", "Pencarian"))
            val searching = AccessibilityUtils.isSearchActive(root, listOf("search_input", "search_box", "search_bar"))
            if (inExplore && !searching) {
                overlayManager.showFeedBlockOverlay(
                    appName = "TikTok",
                    title = "Eksplorasi Feed Dibatasi",
                    subtitle = "Gunakan bilah pencarian untuk konten yang ingin dicari.",
                    isDark = true
                )
                return
            } else if (inExplore && searching) {
                scheduleHide(200L)
                return
            }
        }

        scheduleHide(350L)
    }

    private fun isStory(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        if (AccessibilityUtils.findNodeByViewId(root, listOf("story_viewer", "story_view", "story_container"))) return true
        return event?.className?.toString()?.contains("Story", ignoreCase = true) == true
    }
}
