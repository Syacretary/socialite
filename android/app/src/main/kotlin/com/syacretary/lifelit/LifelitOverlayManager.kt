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
 *
 * Key design principles:
 * 1. The overlay is ONLY shown when the target app is in the foreground AND the blocked
 *    screen/feature is currently active. It is hidden instantly when the user leaves.
 * 2. Touch input passes through to the BOTTOM NAV BAR and TOP SEARCH BAR areas via
 *    FLAG_NOT_TOUCH_MODAL — only the feed content area in the middle is blocked.
 * 3. The overlay window uses TYPE_ACCESSIBILITY_OVERLAY which requires no SYSTEM_ALERT_WINDOW
 *    permission but is tightly scoped to the accessibility service lifecycle.
 */
class LifelitOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var currentActiveApp: String? = null

    // Approximate heights (dp) for the areas we leave interactive
    private val TOP_PASSTHROUGH_DP  = 76   // YouTube/IG top bar + status bar
    private val BOT_PASSTHROUGH_DP  = 72   // bottom nav bar

    fun showFeedBlockOverlay(
        appName: String,
        title: String,
        subtitle: String,
        isDark: Boolean = true
    ) {
        // If same app overlay already up → no-op; different app → refresh
        if (isOverlayShowing && currentActiveApp == appName) return
        hideFeedBlockOverlay()
        currentActiveApp = appName

        val density = context.resources.displayMetrics.density
        val topMargin    = (TOP_PASSTHROUGH_DP * density).toInt()
        val bottomMargin = (BOT_PASSTHROUGH_DP * density).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        /*
         * FLAG_NOT_TOUCH_MODAL  → Touches outside the overlay's exact view bounds pass
         *                         through to the app below. Combined with the top/bottom
         *                         transparent margins in the root FrameLayout this means
         *                         the search bar and nav bar remain fully clickable.
         * FLAG_NOT_FOCUSABLE    → Keyboard / IME focus stays in the target app.
         * FLAG_LAYOUT_IN_SCREEN → Overlay fills the full screen rect (incl. status bar).
         *
         * NOTE: We deliberately do NOT use FLAG_NOT_TOUCHABLE on the whole window because
         * we want the blocked feed area in the middle to absorb touches (preventing the
         * user from accidentally tapping feed cards through the overlay).
         */
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        /*
         * Root is a transparent MATCH_PARENT FrameLayout.
         * The top and bottom margins leave those bands transparent AND non-touchable
         * (because FLAG_NOT_TOUCH_MODAL is set, touches outside the opaque child
         *  fall through to the underlying app).
         *
         * The inner feedBlocker fills the middle band and IS solid (absorbs touches).
         */
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // Make the root itself pass-through; only the child feedBlocker captures touches
            isClickable = false
            isFocusable = false
        }

        val bgColor   = if (isDark) Color.parseColor("#0A0A0A") else Color.parseColor("#F5F5F7")
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val subColor  = if (isDark) Color.parseColor("#888888") else Color.parseColor("#666666")
        val badgeColor = if (isDark) Color.parseColor("#555555") else Color.parseColor("#999999")

        val feedBlocker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(
                (24 * density).toInt(), (20 * density).toInt(),
                (24 * density).toInt(), (20 * density).toInt()
            )
            isClickable = true   // absorb taps in the middle band

            addView(TextView(context).apply {
                text = title
                setTextColor(textColor)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = subtitle
                setTextColor(subColor)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, (14 * density).toInt())
            })

            addView(TextView(context).apply {
                text = "Lifelit · Search & Navbar tetap aktif"
                setTextColor(badgeColor)
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

    val isShowing: Boolean get() = isOverlayShowing
}
