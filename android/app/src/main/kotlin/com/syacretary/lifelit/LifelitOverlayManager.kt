package com.syacretary.lifelit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages adaptive feed-masking overlay views over restricted app areas.
 * Covers the addictive content feed area while leaving the top search bar
 * and bottom navigation bar uncovered and fully interactive.
 */
class LifelitOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var currentActiveApp: String? = null

    fun showFeedBlockOverlay(appName: String, title: String, subtitle: String, isDark: Boolean = true) {
        if (isOverlayShowing && currentActiveApp == appName) return

        hideFeedBlockOverlay()
        currentActiveApp = appName

        val density = context.resources.displayMetrics.density
        val topMargin = (72 * density).toInt()     // Leave top app/search bar exposed
        val bottomMargin = (64 * density).toInt()  // Leave bottom navigation bar exposed

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Outer container: transparent background, with inner content occupying only the feed area
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val feedBlocker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val bgColor = if (isDark) Color.parseColor("#0A0A0A") else Color.parseColor("#F5F5F7")
            setBackgroundColor(bgColor)
            setPadding((24 * density).toInt(), (20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt())

            val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
            val subtextColor = if (isDark) Color.parseColor("#888888") else Color.parseColor("#666666")

            // Title TextView
            addView(TextView(context).apply {
                text = title
                setTextColor(textColor)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            // Subtitle TextView
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(subtextColor)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, (14 * density).toInt())
            })

            // Badge note
            addView(TextView(context).apply {
                text = "Lifelit • Search & Navbar Aktif"
                setTextColor(if (isDark) Color.parseColor("#555555") else Color.parseColor("#999999"))
                textSize = 10f
                gravity = Gravity.CENTER
            })
        }

        val feedParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(0, topMargin, 0, bottomMargin)
        }

        rootLayout.addView(feedBlocker, feedParams)
        overlayView = rootLayout

        try {
            windowManager.addView(rootLayout, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
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
}
