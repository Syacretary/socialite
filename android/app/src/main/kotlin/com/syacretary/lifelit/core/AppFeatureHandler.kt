package com.syacretary.lifelit.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.LifelitOverlayManager

interface AppFeatureHandler {
    val supportedPackages: Set<String>

    /**
     * Executes fast, zero-delay redirects (e.g. Back on Shorts/Reels/Stories)
     * Returns true if event was handled and consumed.
     */
    fun onFastRedirect(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        rules: Set<String>,
        overlayManager: LifelitOverlayManager? = null
    ): Boolean

    /**
     * Debounced layout and state evaluation (e.g. feed overlay, tab verification).
     */
    fun evaluateState(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        rules: Set<String>,
        overlayManager: LifelitOverlayManager,
        scheduleHide: (delayMs: Long) -> Unit,
        hideImmediate: () -> Unit
    )
}
