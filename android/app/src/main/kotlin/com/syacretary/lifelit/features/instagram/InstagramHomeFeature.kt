package com.syacretary.lifelit.features.instagram

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

object InstagramHomeFeature {

    /**
     * Handles "Matikan Beranda" (disable_home & dm_profile_only):
     * Opens the DM inbox by URI, then Profile if that intent cannot be delivered.
     */
    fun handleHomeBlocked(context: Context, root: AccessibilityNodeInfo): Boolean {
        return if (!InstagramNavigation.goToDm(context, root)) {
            InstagramNavigation.goToProfile(context, root)
        } else {
            true
        }
    }
}
