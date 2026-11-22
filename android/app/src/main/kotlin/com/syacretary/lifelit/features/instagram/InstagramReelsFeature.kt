package com.syacretary.lifelit.features.instagram

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.core.AccessibilityUtils

object InstagramReelsFeature {

    private val REEL_VIEW_IDS = listOf(
        "com.instagram.android:id/root_clips_layout",
        "com.instagram.android:id/clips_viewer_container",
        "com.instagram.android:id/clips_swipe_refresh_container",
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_linear_layout_container"
    )

    fun isReelsActive(context: Context, root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        // Guard: if confirmed on Profile or DM, it is never Reels
        if (InstagramNavigation.isInsideDM(context, root)) return false
        if (InstagramNavigation.isInsideProfile(context, root)) return false

        // 1. Explicit click event on Reels
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val desc = event.contentDescription?.toString() ?: ""
            val text = event.text.joinToString()
            if (desc.contains("Reels", ignoreCase = true) || text.contains("Reels", ignoreCase = true)) {
                return true
            }
        }

        // 2. Fullscreen Reels viewer views visible on screen
        for (id in REEL_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n)) return true
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

    fun isNavbarReelsActive(context: Context, root: AccessibilityNodeInfo): Boolean {
        val clipsTabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
        if (!clipsTabNodes.isNullOrEmpty()) {
            for (n in clipsTabNodes) {
                if (!AccessibilityUtils.isNodeVisibleOnScreen(context, n)) continue
                if (n.isSelected) return true
                val desc = n.contentDescription?.toString() ?: ""
                if (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true)) return true
            }
        }

        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val tabBar = tabBars[0]
            if (tabBar.childCount > 1) {
                val clipsChild = tabBar.getChild(1)
                if (clipsChild != null) {
                    if (clipsChild.isSelected) return true
                    val desc = clipsChild.contentDescription?.toString() ?: ""
                    if (desc.contains("Reels", ignoreCase = true) &&
                        (desc.contains("dipilih", ignoreCase = true) || desc.contains("selected", ignoreCase = true))) return true
                }
            }
        }
        return false
    }

    fun handleReelsBlocked(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        rules: Set<String>,
        isNavBarReels: Boolean
    ) {
        if (isNavBarReels) {
            InstagramNavigation.redirectToAllowedScreen(service, root, rules)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
