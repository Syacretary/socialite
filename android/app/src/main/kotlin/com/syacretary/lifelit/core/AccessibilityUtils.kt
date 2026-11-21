package com.syacretary.lifelit.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class FeedBounds(val topPx: Int, val heightPx: Int)

object AccessibilityUtils {

    fun isNodeVisibleOnScreen(context: Context, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val dm = context.resources.displayMetrics
        return rect.width() > 100 && rect.height() > 100 && rect.left >= 0 && rect.left < dm.widthPixels && rect.right > 0
    }

    fun clickNodeOrDescendant(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val parent = node.parent
        if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return false
    }

    fun findClickableNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val text   = node.text?.toString() ?: ""
        val desc   = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        for (kw in keywords) {
            if ((text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true) ||
                 viewId.contains(kw, ignoreCase = true)) && node.isClickable) return node
        }
        for (i in 0 until node.childCount) {
            val found = findClickableNode(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    fun isNodeSelected(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)) {
                if (node.isSelected) return true
            }
        }
        for (i in 0 until node.childCount) {
            if (isNodeSelected(node.getChild(i), keywords)) return true
        }
        return false
    }

    fun findNodeByViewId(node: AccessibilityNodeInfo?, idFragments: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        for (frag in idFragments) {
            if (viewId.contains(frag, ignoreCase = true)) return true
        }
        for (i in 0 until node.childCount) {
            if (findNodeByViewId(node.getChild(i), idFragments)) return true
        }
        return false
    }

    fun isSearchActive(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        val text   = node.text?.toString() ?: ""
        for (kw in keywords) {
            if ((viewId.contains(kw, ignoreCase = true) || text.contains(kw, ignoreCase = true)) &&
                (node.isFocused || node.isEditable)) return true
        }
        for (i in 0 until node.childCount) {
            if (isSearchActive(node.getChild(i), keywords)) return true
        }
        return false
    }

    fun navigateToHome(context: Context, service: AccessibilityService, packageName: String) {
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                context.startActivity(launchIntent)
            } else {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
