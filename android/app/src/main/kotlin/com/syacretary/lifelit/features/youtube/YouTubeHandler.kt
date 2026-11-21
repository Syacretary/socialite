package com.syacretary.lifelit.features.youtube

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AccessibilityUtils
import com.syacretary.lifelit.core.AppFeatureHandler
import com.syacretary.lifelit.core.FeedBounds

class YouTubeHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf("com.google.android.youtube")

    private var lastNavTimestamp = 0L
    private val NAV_COOLDOWN_MS = 1200L
    private val handler = Handler(Looper.getMainLooper())

    override fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>
    ): Boolean {
        if (!rules.contains("disable_shorts")) return false

        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return false

        if (isShortsActive(root, event)) {
            lastNavTimestamp = now
            redirectToHome(service, root)
            return true
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
        if (rules.contains("disable_shorts") && isShortsActive(root, null)) {
            redirectToHome(service, root)
            return
        }

        if (rules.contains("disable_home")) {
            if (isInSearchMode(root)) {
                scheduleHide(150L)
                return
            }

            if (isHomeTab(root)) {
                val bounds = calculateFeedBounds(service, root)
                overlayManager.showFeedBlockOverlay(
                    appName = "YouTube",
                    title = "Beranda YouTube Dibatasi",
                    subtitle = "Gunakan bilah pencarian di atas untuk menonton video yang Anda cari.",
                    isDark = true,
                    customTopPx = bounds.topPx,
                    customHeightPx = bounds.heightPx
                )
                return
            }
            scheduleHide(350L)
            return
        }

        hideImmediate()
    }

    private fun isShortsActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val desc = event.contentDescription?.toString() ?: ""
            val text = event.text.joinToString()
            if (desc.contains("Shorts", ignoreCase = true) || text.contains("Shorts", ignoreCase = true)) {
                return true
            }
        }

        val shortsViewIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_view",
            "com.google.android.youtube:id/reel_player_page_item"
        )
        for (id in shortsViewIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }

        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                val text = child.text?.toString() ?: ""
                if (desc.contains("Shorts", ignoreCase = true) || text.contains("Shorts", ignoreCase = true)) {
                    if (child.isSelected) return true
                }
            }
        }

        val cls = event?.className?.toString() ?: ""
        return cls.contains("ReelWatchFragment", ignoreCase = true) || cls.contains("ShortsPlayer", ignoreCase = true)
    }

    private fun redirectToHome(service: AccessibilityService, root: AccessibilityNodeInfo?) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (root != null) {
            clickHomeTab(root)
        }
        handler.postDelayed({
            val newRoot = service.rootInActiveWindow
            if (newRoot != null) {
                clickHomeTab(newRoot)
            }
        }, 180L)
    }

    private fun clickHomeTab(root: AccessibilityNodeInfo): Boolean {
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                val text = child.text?.toString() ?: ""
                if (desc.contains("Beranda", ignoreCase = true) || desc.contains("Home", ignoreCase = true) ||
                    text.contains("Beranda", ignoreCase = true) || text.contains("Home", ignoreCase = true)) {
                    if (AccessibilityUtils.clickNodeOrDescendant(child)) return true
                }
            }
        }

        for (label in listOf("Beranda", "Home")) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (AccessibilityUtils.clickNodeOrDescendant(node)) return true
                }
            }
        }
        return false
    }

    private fun isInSearchMode(root: AccessibilityNodeInfo): Boolean {
        // 1. Search input field or clear button is present on screen
        if (!root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_edit_text").isNullOrEmpty()) return true
        if (!root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_clear_button").isNullOrEmpty()) return true

        // 2. Search filter button (only present on search results page)
        if (!root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/filter_button").isNullOrEmpty()) return true

        // 3. Toolbar back navigation button while YouTube logo is absent
        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/toolbar")
        if (!toolbarNodes.isNullOrEmpty()) {
            val toolbar = toolbarNodes[0]
            var hasBackButton = false
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                if (desc.contains("Navigate up", ignoreCase = true) ||
                    desc.contains("Kembali", ignoreCase = true) ||
                    desc.contains("Back", ignoreCase = true)) {
                    hasBackButton = true
                    break
                }
            }
            if (hasBackButton) {
                if (root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/youtube_logo").isNullOrEmpty()) {
                    return true
                }
            }
        }

        return false
    }

    private fun calculateFeedBounds(service: AccessibilityService, root: AccessibilityNodeInfo): FeedBounds {
        val dm = service.resources.displayMetrics
        val screenHeight = dm.heightPixels
        var topPx = 0
        var bottomPx = screenHeight

        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/toolbar")
        if (!toolbarNodes.isNullOrEmpty()) {
            val rect = Rect()
            toolbarNodes[0].getBoundsInScreen(rect)
            if (rect.bottom > 0) topPx = rect.bottom
        } else {
            val searchNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/menu_item_search")
            if (!searchNodes.isNullOrEmpty()) {
                val rect = Rect()
                searchNodes[0].getBoundsInScreen(rect)
                if (rect.bottom > 0) topPx = rect.bottom + (4 * dm.density).toInt()
            }
        }

        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val rect = Rect()
            pivotNodes[0].getBoundsInScreen(rect)
            if (rect.top > 0) bottomPx = rect.top
        }

        if (topPx == 0) topPx = (96 * dm.density).toInt()
        if (bottomPx == screenHeight) bottomPx = screenHeight - (56 * dm.density).toInt()

        val heightPx = (bottomPx - topPx).coerceAtLeast((100 * dm.density).toInt())
        return FeedBounds(topPx, heightPx)
    }

    private fun isHomeTab(root: AccessibilityNodeInfo): Boolean {
        // If actively typing a query or viewing search results -> NOT home
        if (isInSearchMode(root)) return false

        // If fullscreen Shorts player is active -> NOT home
        if (!root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_watch_fragment_root").isNullOrEmpty()) return false

        // Check if user is actively watching a video player fullscreen/expanded without navbar
        val playerView = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/player_fragment_container")
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        val hasLogo = !root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/youtube_logo").isNullOrEmpty()

        if (!playerView.isNullOrEmpty() && pivotNodes.isNullOrEmpty() && !hasLogo) {
            // Fullscreen video playback in progress
            return false
        }

        // Check bottom navigation bar (pivot_bar)
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            var otherTabSelected = false
            var homeTabSelected = false

            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                val text = child.text?.toString() ?: ""
                val isSelected = child.isSelected ||
                        desc.contains("dipilih", ignoreCase = true) ||
                        desc.contains("selected", ignoreCase = true)

                val isHome = desc.contains("Beranda", ignoreCase = true) ||
                        desc.contains("Home", ignoreCase = true) ||
                        text.contains("Beranda", ignoreCase = true) ||
                        text.contains("Home", ignoreCase = true) ||
                        (i == 0)

                if (isSelected) {
                    if (isHome) {
                        homeTabSelected = true
                    } else {
                        otherTabSelected = true
                    }
                }
            }

            if (otherTabSelected) return false
            if (homeTabSelected) return true

            // Pivot bar is present and no other tab is explicitly selected:
            // Home is the default active state in YouTube!
            return true
        }

        // Secondary confirmation: presence of YouTube logo in top toolbar
        if (hasLogo) return true

        return false
    }
}
