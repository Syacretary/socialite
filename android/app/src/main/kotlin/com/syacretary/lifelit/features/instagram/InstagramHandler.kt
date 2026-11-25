package com.syacretary.lifelit.features.instagram

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager
import com.syacretary.lifelit.core.AppFeatureHandler

/**
 * Modular Instagram Feature Orchestrator.
 * Harmonizes all individual features (Home, Feeds, Reels, Stories, DM/Profile)
 * so they complement each other cleanly without conflicts.
 */
class InstagramHandler : AppFeatureHandler {

    override val supportedPackages: Set<String> = setOf("com.instagram.android")

    private var lastNavTimestamp = 0L
    private val NAV_COOLDOWN_MS = 1400L

    override fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastNavTimestamp < NAV_COOLDOWN_MS) return false

        // 0. Search & Explore guard: NEVER redirect when user is in Search, typing, or Explore
        if (InstagramNavigation.isSearchOrExploreActive(service, root) || InstagramNavigation.isSearchTyping(root)) {
            return false
        }

        val activeTab = InstagramNavigation.getActiveTab(root)
        if (activeTab == InstagramTab.SEARCH || activeTab == InstagramTab.DM || activeTab == InstagramTab.PROFILE) {
            return false
        }

        val dmOnly = rules.contains("dm_profile_only")
        val homeBlocked = rules.contains("disable_home") || dmOnly
        val reelsBlocked = rules.contains("disable_reels") || dmOnly
        val friendReelsBlocked = rules.contains("disable_friend_reels") || dmOnly
        val storiesBlocked = rules.contains("hide_stories") || dmOnly

        // 1. Stories Blocking
        if (storiesBlocked && InstagramStoriesFeature.isStoryActive(service, root, event)) {
            lastNavTimestamp = now
            InstagramStoriesFeature.blockStory(service)
            return true
        }

        // 2. Reels Blocking
        if (reelsBlocked && InstagramReelsFeature.isReelsActive(service, root, event)) {
            val isNavBarReels = InstagramReelsFeature.isNavbarReelsActive(service, root)
            val isFromDM = (now - InstagramNavigation.lastDMTimestamp) < 2500L

            // If received from DM and friend reels are allowed, permit playback
            if (isFromDM && !friendReelsBlocked && !isNavBarReels) {
                return false
            }

            lastNavTimestamp = now
            InstagramReelsFeature.handleReelsBlocked(service, root, rules, isNavBarReels)
            return true
        }

        // 3. Fast Home Redirect (Instant redirect away from Beranda to DM without waiting)
        if (homeBlocked) {
            val activeTab = InstagramNavigation.getActiveTab(root)
            if (activeTab == InstagramTab.HOME) {
                lastNavTimestamp = now
                val clicked = InstagramHomeFeature.handleHomeBlocked(service, root)
                if (clicked) return true
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
        val homeBlocked = rules.contains("disable_home") || dmOnly
        val feedsBlocked = rules.contains("disable_feeds") || dmOnly

        val activeTab = InstagramNavigation.getActiveTab(root)

        // 1. DM & Profile are ALWAYS permitted
        if (activeTab == InstagramTab.DM || activeTab == InstagramTab.PROFILE) {
            scheduleHide(150L)
            return
        }
        if (InstagramNavigation.isInsideDM(service, root) || InstagramNavigation.isInsideProfile(service, root)) {
            scheduleHide(150L)
            return
        }

        // 2. Beranda / Home Feed tab
        if (activeTab == InstagramTab.HOME) {
            if (homeBlocked) {
                // REDIRECT ONLY - NO OVERLAY
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    InstagramHomeFeature.handleHomeBlocked(service, root)
                }
                hideImmediate()
                return
            }
            scheduleHide(150L)
            return
        }

        // 3. Search / Explore tab or typing in Search
        if (activeTab == InstagramTab.SEARCH || InstagramNavigation.isSearchOrExploreActive(service, root)) {
            if (feedsBlocked && !InstagramNavigation.isSearchTyping(root)) {
                val bounds = InstagramFeedsFeature.calculateExploreBounds(service, root)
                InstagramFeedsFeature.showExploreFeedOverlay(overlayManager, bounds)
                return
            }
            scheduleHide(150L)
            return
        }

        // 4. Reels navbar tab
        if (activeTab == InstagramTab.REELS) {
            val reelsBlocked = rules.contains("disable_reels") || dmOnly
            if (reelsBlocked) {
                val now = System.currentTimeMillis()
                if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                    lastNavTimestamp = now
                    InstagramNavigation.redirectToAllowedScreen(service, root, rules)
                }
            }
            scheduleHide(150L)
            return
        }

        // 5. Unknown tab or nested sub-pages
        if (activeTab == InstagramTab.UNKNOWN) {
            if (dmOnly) {
                if (!InstagramNavigation.isInsideDM(service, root) &&
                    !InstagramNavigation.isInsideProfile(service, root) &&
                    !InstagramNavigation.isSearchOrExploreActive(service, root)) {
                    val now = System.currentTimeMillis()
                    if (now - lastNavTimestamp > NAV_COOLDOWN_MS) {
                        lastNavTimestamp = now
                        InstagramHomeFeature.handleHomeBlocked(service, root)
                    }
                }
            }
            scheduleHide(200L)
            return
        }

        scheduleHide(200L)
    }
}
