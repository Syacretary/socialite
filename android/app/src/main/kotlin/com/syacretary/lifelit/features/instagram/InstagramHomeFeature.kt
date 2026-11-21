package com.syacretary.lifelit.features.instagram

import android.view.accessibility.AccessibilityNodeInfo

object InstagramHomeFeature {

    /**
     * Handles "Matikan Beranda" (disable_home & dm_profile_only):
     * Strictly redirect to DM without displaying any overlay.
     * Guaranteed to navigate directly to direct_tab (fallback Profile).
     */
    fun handleHomeBlocked(root: AccessibilityNodeInfo): Boolean {
        return if (!InstagramNavigation.clickDM(root)) {
            InstagramNavigation.clickProfile(root)
        } else {
            true
        }
    }
}
