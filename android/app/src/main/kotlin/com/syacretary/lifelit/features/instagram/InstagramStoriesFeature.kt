package com.syacretary.lifelit.features.instagram

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.syacretary.lifelit.core.AccessibilityUtils

object InstagramStoriesFeature {

    private val STORY_VIEWER_IDS = listOf(
        "com.instagram.android:id/viewer_media_view_root",
        "com.instagram.android:id/story_viewer_fragment",
        "com.instagram.android:id/reel_viewer_image_view",
        "com.instagram.android:id/reel_viewer_root"
    )

    fun isStoryActive(context: Context, root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        for (id in STORY_VIEWER_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (AccessibilityUtils.isNodeVisibleOnScreen(context, n)) return true
                }
            }
        }
        val cls = event?.className?.toString() ?: ""
        return cls.contains("StoryViewerActivity", ignoreCase = true) ||
               cls.contains("ReelViewerActivity", ignoreCase = true)
    }

    fun blockStory(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }
}
