package com.syacretary.lifelit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages adaptive feed-masking overlay views over restricted app areas.
 *
 * Adaptive Positioning Architecture:
 * - Dynamic Bounds: Accepts exact pixel coordinates (customTopPx, customHeightPx)
 *   measured dynamically from AccessibilityNodeInfo (e.g. bottom of toolbar down to
 *   top of pivot_bar).
 * - Leaves top search bar / header and bottom navigation bar completely uncovered
 *   so 100% of touches reach the underlying app natively.
 */
class LifelitOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var isOverlayShowing = false
    private var currentActiveApp: String? = null

    // Default fallback passthrough heights in dp if node bounds are not yet computed
    private val DEFAULT_TOP_PASSTHROUGH_DP = 96   // Status bar (~40dp) + YouTube toolbar (~56dp)
    private val DEFAULT_BOT_PASSTHROUGH_DP = 56   // Bottom navigation bar (~56dp)

    fun showFeedBlockOverlay(
        appName: String,
        title: String,
        subtitle: String,
        isDark: Boolean = true,
        customTopPx: Int? = null,
        customHeightPx: Int? = null
    ) {
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenHeight = dm.heightPixels

        val topMargin = customTopPx ?: (DEFAULT_TOP_PASSTHROUGH_DP * density).toInt()
        val overlayHeight = customHeightPx ?: (screenHeight - topMargin - (DEFAULT_BOT_PASSTHROUGH_DP * density).toInt()).coerceAtLeast((100 * density).toInt())

        // If overlay is already showing for this app, update its layout bounds dynamically if needed
        if (isOverlayShowing && currentActiveApp == appName && overlayView != null && currentParams != null) {
            val params = currentParams!!
            if (params.y != topMargin || params.height != overlayHeight) {
                params.y = topMargin
                params.height = overlayHeight
                try {
                    windowManager.updateViewLayout(overlayView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        hideFeedBlockOverlay()
        currentActiveApp = appName

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = topMargin
        }
        currentParams = params

        val bgColor = if (isDark) Color.parseColor("#0A0A0A") else Color.parseColor("#F5F5F7")
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val subColor = if (isDark) Color.parseColor("#888888") else Color.parseColor("#666666")
        val badgeColor = if (isDark) Color.parseColor("#555555") else Color.parseColor("#999999")

        val feedBlocker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(
                (24 * density).toInt(), (20 * density).toInt(),
                (24 * density).toInt(), (20 * density).toInt()
            )
            isClickable = true // Absorb touches in the feed area

            // Title
            addView(TextView(context).apply {
                text = title
                setTextColor(textColor)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            // Subtitle
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(subColor)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, (14 * density).toInt())
            })

            // Badge
            addView(TextView(context).apply {
                text = "Lifelit • Search & Navbar Aktif"
                setTextColor(badgeColor)
                textSize = 10f
                gravity = Gravity.CENTER
            })
        }

        overlayView = feedBlocker

        try {
            windowManager.addView(feedBlocker, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
            currentParams = null
            isOverlayShowing = false
        }
    }

    fun hideFeedBlockOverlay() {
        if (!isOverlayShowing || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            currentParams = null
            isOverlayShowing = false
            currentActiveApp = null
        }
    }

    val isShowing: Boolean get() = isOverlayShowing
}
