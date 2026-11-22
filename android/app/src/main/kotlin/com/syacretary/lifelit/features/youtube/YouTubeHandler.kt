package com.syacretary.lifelit.features.youtube

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AppFeatureHandler
import com.syacretary.lifelit.core.FeedBounds

class YouTubeHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf("com.google.android.youtube")

    private var lastNavTimestamp = 0L
    private val NAV_COOLDOWN_MS = 300L

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
            // Instant redirect on Shorts — no cooldown wait, no overlay delay
            lastNavTimestamp = System.currentTimeMillis()
            redirectToHome(service, root)
            return
        }

        if (rules.contains("disable_home")) {
            // Overlay is open/hidden when in Search, Profile/Account, or Upload/Create
            if (isInSearchMode(root) || isProfileOrAccountActive(root) || isUploadOrCreationActive(root)) {
                scheduleHide(40L)
                return
            }

            // Overlay is closed/shown when on Beranda/Home from ANY previous page
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
            scheduleHide(80L)
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

    /**
     * Opens the Home tab by the https host YouTube registers for its main feed.
     * Confirmed on-device: this selects the Home pivot and does not press Back.
     */
    private fun redirectToHome(service: AccessibilityService, root: AccessibilityNodeInfo?) {
        if (openYouTubeHome(service)) return
        if (root != null) clickHomeTab(root)
    }

    private fun openYouTubeHome(service: AccessibilityService): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            service.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun clickHomeTab(root: AccessibilityNodeInfo): Boolean {
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
            ?: return false
        val pivot = pivotNodes.firstOrNull() ?: return false
        for (i in 0 until pivot.childCount) {
            val child = pivot.getChild(i) ?: continue
            val desc = child.contentDescription?.toString() ?: ""
            val text = child.text?.toString() ?: ""
            val isHome = desc.contains("Beranda", ignoreCase = true) ||
                desc.contains("Home", ignoreCase = true) ||
                text.contains("Beranda", ignoreCase = true) ||
                text.contains("Home", ignoreCase = true)
            if (!isHome) continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            for (j in 0 until child.childCount) {
                val grandchild = child.getChild(j) ?: continue
                if (grandchild.isClickable && grandchild.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
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

    private fun isProfileOrAccountActive(root: AccessibilityNodeInfo): Boolean {
        // 1. Pivot bar: You / Anda / Koleksi / Library tab selected
        val pivotNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!pivotNodes.isNullOrEmpty()) {
            val pivot = pivotNodes[0]
            for (i in 0 until pivot.childCount) {
                val child = pivot.getChild(i) ?: continue
                val desc = child.contentDescription?.toString() ?: ""
                val text = child.text?.toString() ?: ""
                val isSelected = child.isSelected ||
                        desc.contains("dipilih", ignoreCase = true) ||
                        desc.contains("selected", ignoreCase = true)

                if (isSelected) {
                    val isProfileTab = desc.contains("Anda", ignoreCase = true) ||
                            desc.contains("You", ignoreCase = true) ||
                            desc.contains("Koleksi", ignoreCase = true) ||
                            desc.contains("Library", ignoreCase = true) ||
                            desc.contains("Akun", ignoreCase = true) ||
                            desc.contains("Account", ignoreCase = true) ||
                            text.contains("Anda", ignoreCase = true) ||
                            text.contains("You", ignoreCase = true)
                    if (isProfileTab) return true
                }
            }
        }

        // 2. Account / Channel / Settings View IDs
        val profileIds = listOf(
            "com.google.android.youtube:id/channel_header",
            "com.google.android.youtube:id/account_switcher",
            "com.google.android.youtube:id/account_layout",
            "com.google.android.youtube:id/user_mention_header",
            "com.google.android.youtube:id/profile_editor",
            "com.google.android.youtube:id/settings_fragment",
            "com.google.android.youtube:id/channel_banner",
            "com.google.android.youtube:id/avatar_layout",
            "com.google.android.youtube:id/profile_header"
        )
        for (id in profileIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }

        // 3. Profile / Account specific text indicators
        val profileLabels = listOf(
            "Ganti akun", "Switch account",
            "Channel Anda", "Your channel",
            "Lihat channel", "View channel",
            "Kelola Akun", "Manage your Google Account",
            "Setelan", "Settings"
        )
        for (label in profileLabels) {
            if (!root.findAccessibilityNodeInfosByText(label).isNullOrEmpty()) return true
        }

        return false
    }

    private fun isUploadOrCreationActive(root: AccessibilityNodeInfo): Boolean {
        // 1. Creation Bottom Sheet or Views
        val uploadIds = listOf(
            "com.google.android.youtube:id/creation_bottom_sheet",
            "com.google.android.youtube:id/bottom_sheet_scroll_view",
            "com.google.android.youtube:id/reel_camera_container",
            "com.google.android.youtube:id/shorts_camera",
            "com.google.android.youtube:id/camera_view",
            "com.google.android.youtube:id/video_picker",
            "com.google.android.youtube:id/gallery",
            "com.google.android.youtube:id/upload_activity",
            "com.google.android.youtube:id/edit_video_fragment",
            "com.google.android.youtube:id/upload_fragment",
            "com.google.android.youtube:id/creation_entry_points",
            "com.google.android.youtube:id/record_button"
        )
        for (id in uploadIds) {
            if (!root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()) return true
        }

        // 2. Specific upload / creation text indicators
        val uploadTexts = listOf(
            "Upload video", "Upload a video",
            "Buat Video Shorts", "Create a Short",
            "Live streaming", "Go live",
            "Buat postingan", "Create a post",
            "Tambahkan detail", "Add details",
            "Pilih video", "Select video",
            "Pilih foto", "Pilih audio"
        )
        for (text in uploadTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) return true
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

        // If in Profile / Account or Upload / Create -> NOT home (keep overlay open/hidden)
        if (isProfileOrAccountActive(root) || isUploadOrCreationActive(root)) return false

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
