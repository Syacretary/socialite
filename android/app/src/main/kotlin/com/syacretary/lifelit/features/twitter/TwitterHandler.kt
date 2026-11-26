package com.syacretary.lifelit.features.twitter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AccessibilityUtils
import com.syacretary.lifelit.core.AppFeatureHandler

class TwitterHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf("com.twitter.android")

    override fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>,
        overlayManager: LifelitOverlayManager?
    ): Boolean {
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
        if (rules.contains("disable_for_you_feed") && isForYouTab(root)) {
            overlayManager.showFeedBlockOverlay(
                appName = "X (Twitter)",
                title = "Tab 'Untuk Anda' Dibatasi",
                subtitle = "Beralih ke tab 'Mengikuti' untuk melihat linimasa.",
                isDark = true
            )
            return
        }

        if (rules.contains("hide_trending") && isTrending(root)) {
            overlayManager.showFeedBlockOverlay(
                appName = "X (Twitter)",
                title = "Trending Topics Disembunyikan",
                subtitle = "Gunakan pencarian untuk menemukan topik secara langsung.",
                isDark = true
            )
            return
        }

        scheduleHide(350L)
    }

    private fun isForYouTab(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("For you", "Untuk Anda", "For You")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isSelected) return true
                    val p = n.parent
                    if (p != null && p.isSelected) return true
                }
            }
        }
        return false
    }

    private fun isTrending(root: AccessibilityNodeInfo): Boolean {
        if (!AccessibilityUtils.isNodeSelected(root, listOf("Explore", "Eksplorasi", "Search"))) return false
        val trendIds = listOf("com.twitter.android:id/trend_name", "com.twitter.android:id/explore_trending_section")
        for (id in trendIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }
        return false
    }
}
