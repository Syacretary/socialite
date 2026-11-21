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
 * Positioning Architecture:
 * - Top Search / App Bar: ~76dp left completely uncovered (no overlay window exists here).
 * - Bottom Navigation Bar: ~72dp left completely uncovered (no overlay window exists here).
 * - Feed Content Area: The overlay window is sized exactly to fit between the top bar
 *   and bottom bar.
 *
 * This ensures touches to the search bar and nav bar pass directly and natively to
 * the underlying application without any touch-modal or tap-jacking issues.
 */
class LifelitOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var currentActiveApp: String? = null

    // Exact passthrough heights in dp
    private val TOP_PASSTHROUGH_DP = 76   // Search bar + status bar
    private val BOT_PASSTHROUGH_DP = 72   // Bottom navigation bar

    fun showFeedBlockOverlay(
        appName: String,
        title: String,
        subtitle: String,
        isDark: Boolean = true
    ) {
        // If already showing for this exact app, avoid re-adding
        if (isOverlayShowing && currentActiveApp == appName && overlayView != null) return
        hideFeedBlockOverlay()
        currentActiveApp = appName

        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenHeight = dm.heightPixels

        val topMargin = (TOP_PASSTHROUGH_DP * density).toInt()
        val bottomMargin = (BOT_PASSTHROUGH_DP * density).toInt()
        val overlayHeight = (screenHeight - topMargin - bottomMargin).coerceAtLeast((100 * density).toInt())

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        /*
         * Window LayoutParams:
         * x = 0, y = topMargin
         * width = MATCH_PARENT, height = overlayHeight
         *
         * Because the window itself does NOT cover y < topMargin or y > (screenHeight - bottomMargin),
         * the system naturally routes all touches in those regions to the underlying app!
         */
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

            // Badge / Info
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
            isOverlayShowing = false
            currentActiveApp = null
        }
    }

    val isShowing: Boolean get() = isOverlayShowing
}
