package com.syacretary.lifelit.features.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AppFeatureHandler

/**
 * Modular TikTok Feature Orchestrator.
 * Harmonizes DM & Profile Only, FYP restriction, Story blocker,
 * and Explore Feed restriction below search bar.
 */
class TikTokHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go"
    )

    private var lastNavTimestamp = 0L
    private val NAV_COOLDOWN_MS = 1400L

    override fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>,
        overlayManager: LifelitOverlayManager?
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return false

        val dmOnly = rules.contains("dm_profile_only")
        val fypBlocked = rules.contains("disable_fyp") || dmOnly
        val storiesBlocked = rules.contains("hide_stories") || dmOnly

        // 1. Stories Blocking
        if (storiesBlocked && TikTokNavigation.isStoryActive(root, event)) {
            lastNavTimestamp = now
            overlayManager?.showMindfulShakeAlert(
                title = "Stories Dibatasi",
                message = "Berhenti sejenak. Kembalikan fokusmu ke hal yang bermakna."
            )
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return true
        }

        // 2. DM & Profile Only: Redirect immediately away from Home/Feeds to Inbox
        if (dmOnly) {
            val activeTab = TikTokNavigation.getActiveTab(root)
            if (activeTab != TikTokTab.INBOX && activeTab != TikTokTab.PROFILE) {
                lastNavTimestamp = now
                overlayManager?.showMindfulShakeAlert(
                    title = "TikTok Dibatasi",
                    message = "Hanya DM dan Profil yang diizinkan. Sadari autopilot jempolmu."
                )
                val redirected = if (!TikTokNavigation.goToInbox(service, root)) {
                    TikTokNavigation.goToProfile(service, root)
                } else {
                    true
                }
                if (redirected) return true
            }
        }

        // 3. FYP Blocking: Instantly switch to Following tab
        if (fypBlocked && !dmOnly) {
            val activeTab = TikTokNavigation.getActiveTab(root)
            if (activeTab == TikTokTab.HOME) {
                lastNavTimestamp = now
                overlayManager?.showMindfulShakeAlert(
                    title = "FYP TikTok Dibatasi",
                    message = "Jebakan scrolling tanpa akhir terdeteksi. Tarik napas dan kendalikan kembali perhatianmu."
                )
                val switched = TikTokNavigation.goToFollowing(root)
                if (switched) return true
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
        val dmOnly = rules.contains("dm_profile_only")
        val exploreBlocked = rules.contains("hide_explore_feeds") || dmOnly
        val fypBlocked = rules.contains("disable_fyp") || dmOnly
        val shortFeedBlocked = rules.contains("disable_short_feed") || dmOnly

        val activeTab = TikTokNavigation.getActiveTab(root)

        // 1. Inbox / DM and Profile are ALWAYS allowed and never covered
        if (activeTab == TikTokTab.INBOX || activeTab == TikTokTab.PROFILE) {
            scheduleHide(150L)
            return
        }
        if (TikTokNavigation.isInsideDM(root) || TikTokNavigation.isInsideProfile(root)) {
            scheduleHide(150L)
            return
        }

        // 2. DM & Profile Only Master Toggle
        if (dmOnly) {
            val now = System.currentTimeMillis()
            if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                lastNavTimestamp = now
                if (!TikTokNavigation.goToInbox(service, root)) {
                    TikTokNavigation.goToProfile(service, root)
                }
            }
            hideImmediate()
            return
        }

        // 3. Explore / Search screen
        if (exploreBlocked && TikTokExploreFeature.isExploreScreen(root)) {
            if (TikTokExploreFeature.isSearchActive(root)) {
                // User is actively searching / typing query -> Hide overlay smoothly
                scheduleHide(150L)
                return
            }
            // User is on explore feed -> Show overlay below search bar
            val bounds = TikTokExploreFeature.calculateExploreBounds(service, root)
            TikTokExploreFeature.showExploreFeedOverlay(overlayManager, bounds)
            return
        }

        // 4. FYP / Home Feed Tab
        if (activeTab == TikTokTab.HOME) {
            if (fypBlocked && TikTokNavigation.isForYouTabSelected(root)) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    TikTokNavigation.goToFollowing(root)
                }
                scheduleHide(200L)
                return
            }

            if (shortFeedBlocked && !TikTokNavigation.isFollowingTabSelected(root)) {
                overlayManager.showFeedBlockOverlay(
                    appName = "TikTok",
                    title = "Video Pendek TikTok Dibatasi",
                    subtitle = "Gunakan tab Mengikuti atau Inbox untuk tetap terhubung.",
                    isDark = true
                )
                return
            }
        }

        scheduleHide(250L)
    }
}
