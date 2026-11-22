package com.syacretary.lifelit.features.tiktok

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.FeedBounds

/**
 * TikTok Explore / Search Feed Feature.
 * Shows overlay covering explore recommendations below the search bar,
 * keeping the search input completely visible and functional.
 */
object TikTokExploreFeature {

    fun isSearchActive(root: AccessibilityNodeInfo): Boolean {
        val searchViewIds = listOf(
            "com.zhiliaoapp.musically:id/search_edit_text",
            "com.zhiliaoapp.musically:id/et_search_kw",
            "com.zhiliaoapp.musically:id/search_input",
            "com.ss.android.ugc.trill:id/search_edit_text",
            "com.ss.android.ugc.trill:id/et_search_kw",
            "com.ss.android.ugc.trill:id/search_input"
        )
        for (id in searchViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isFocused || n.isEditable) return true
                }
            }
        }
        return false
    }

    fun isExploreScreen(root: AccessibilityNodeInfo): Boolean {
        val searchViewIds = listOf(
            "com.zhiliaoapp.musically:id/search_edit_text",
            "com.zhiliaoapp.musically:id/et_search_kw",
            "com.ss.android.ugc.trill:id/search_edit_text",
            "com.ss.android.ugc.trill:id/et_search_kw"
        )
        for (id in searchViewIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }

        val exploreLabels = listOf("Temukan", "Explore", "Pencarian", "Search")
        for (label in exploreLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) return true
        }
        return false
    }

    fun calculateExploreBounds(context: Context, root: AccessibilityNodeInfo): FeedBounds {
        val dm = context.resources.displayMetrics
        val screenHeight = dm.heightPixels
        var topPx = 0
        var bottomPx = screenHeight

        // 1. Search box bottom
        val searchIds = listOf(
            "com.zhiliaoapp.musically:id/search_bar_container",
            "com.zhiliaoapp.musically:id/search_edit_text",
            "com.zhiliaoapp.musically:id/et_search_kw",
            "com.ss.android.ugc.trill:id/search_bar_container",
            "com.ss.android.ugc.trill:id/search_edit_text",
            "com.ss.android.ugc.trill:id/et_search_kw"
        )
        for (id in searchIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val rect = Rect()
                nodes[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) {
                    topPx = rect.bottom + (4 * dm.density).toInt()
                    break
                }
            }
        }

        // 2. Bottom navigation bar top
        val navIds = listOf(
            "com.zhiliaoapp.musically:id/bottom_navigation_bar",
            "com.zhiliaoapp.musically:id/tab_layout",
            "com.ss.android.ugc.trill:id/bottom_navigation_bar",
            "com.ss.android.ugc.trill:id/tab_layout"
        )
        for (id in navIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val rect = Rect()
                nodes[0].getBoundsInScreen(rect)
                if (rect.top > topPx) {
                    bottomPx = rect.top
                    break
                }
            }
        }

        if (topPx == 0) topPx = (96 * dm.density).toInt()
        if (bottomPx == screenHeight) bottomPx = screenHeight - (56 * dm.density).toInt()

        val heightPx = (bottomPx - topPx).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(topPx, heightPx)
    }

    fun showExploreFeedOverlay(overlayManager: LifelitOverlayManager, bounds: FeedBounds) {
        overlayManager.showFeedBlockOverlay(
            appName = "TikTok",
            title = "Feeds Explore TikTok Dibatasi",
            subtitle = "Gunakan bilah pencarian di atas untuk mencari konten yang diinginkan.",
            isDark = true,
            customTopPx = bounds.topPx,
            customHeightPx = bounds.heightPx
        )
    }
}
