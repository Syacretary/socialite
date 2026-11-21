package com.syacretary.lifelit.features.instagram

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.FeedBounds

object InstagramFeedsFeature {

    /**
     * Calculates overlay bounds for Search/Explore tab:
     * Overlay begins strictly BELOW the search bar and ends at the tab_bar top.
     * Keeps search bar and bottom tabs completely uncovered for native touch interaction.
     */
    fun calculateExploreBounds(context: Context, root: AccessibilityNodeInfo): FeedBounds {
        val dm = context.resources.displayMetrics
        val screenHeight = dm.heightPixels
        var searchBarBottom = 0
        var bottomPx = screenHeight

        // 1. Explore action bar
        val exploreActionBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/explore_action_bar")
        if (!exploreActionBar.isNullOrEmpty()) {
            val rect = Rect()
            exploreActionBar[0].getBoundsInScreen(rect)
            if (rect.bottom > 0) searchBarBottom = rect.bottom
        }

        // 2. Action bar container
        if (searchBarBottom == 0) {
            val container = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/explore_action_bar_container")
            if (!container.isNullOrEmpty()) {
                val rect = Rect()
                container[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) searchBarBottom = rect.bottom
            }
        }

        // 3. Search edit text
        if (searchBarBottom == 0) {
            val searchBox = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_search_edit_text")
            if (!searchBox.isNullOrEmpty()) {
                val rect = Rect()
                searchBox[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) searchBarBottom = rect.bottom + (10 * dm.density).toInt()
            }
        }

        // 4. Fallback based on main panel top + standard action bar height (56dp)
        if (searchBarBottom == 0) {
            val panelNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/layout_container_main_panel")
            if (!panelNodes.isNullOrEmpty()) {
                val rect = Rect()
                panelNodes[0].getBoundsInScreen(rect)
                searchBarBottom = rect.top + (56 * dm.density).toInt().coerceAtLeast(143)
            } else {
                searchBarBottom = (dm.heightPixels * 0.105f).toInt()
            }
        }

        // tab_bar top boundary
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabBars.isNullOrEmpty()) {
            val rect = Rect()
            tabBars[0].getBoundsInScreen(rect)
            if (rect.top > searchBarBottom) bottomPx = rect.top
        }

        if (bottomPx == screenHeight) bottomPx = (dm.heightPixels * 0.887f).toInt()

        val heightPx = (bottomPx - searchBarBottom).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(searchBarBottom, heightPx)
    }

    fun showExploreFeedOverlay(overlayManager: LifelitOverlayManager, bounds: FeedBounds) {
        overlayManager.showFeedBlockOverlay(
            appName = "Instagram",
            title = "Feeds Instagram Dibatasi",
            subtitle = "Gunakan kolom pencarian di atas untuk mencari teman atau profil.",
            isDark = true,
            customTopPx = bounds.topPx,
            customHeightPx = bounds.heightPx
        )
    }
}
