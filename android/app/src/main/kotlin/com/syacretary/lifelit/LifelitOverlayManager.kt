package com.syacretary.lifelit

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * How the time-limit intervention should be presented over the restricted app.
 */
enum class LimitInterventionMode {
    /** Small, non-blocking heads-up banner at the top of the screen. */
    NOTIFY,

    /** Centered, dismissible intervention dialog over the restricted app. */
    POPUP,

    /** Full-screen blocker that stays until the user leaves the app. */
    BLOCK
}

/**
 * Manages adaptive feed-masking overlay views over restricted app areas.
 *
 * Adaptive Positioning Architecture:
 * - Dynamic Bounds: Accepts exact pixel coordinates (customTopPx, customHeightPx)
 *   measured dynamically from AccessibilityNodeInfo (e.g. bottom of toolbar down to
 *   top of pivot_bar).
 * - Leaves top search bar / header and bottom navigation bar completely uncovered
 *   so 100% of touches reach the underlying app natively.
 *
 * It also renders the native time-limit intervention windows (banner, popup, and
 * full-screen blocker) using TYPE_ACCESSIBILITY_OVERLAY so they appear on top of
 * the specific social media app instead of only inside Lifelit.
 */
class LifelitOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // ── Feed mask overlay ────────────────────────────────────────────────
    private var overlayView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var isOverlayShowing = false
    private var currentActiveApp: String? = null

    // ── Time-limit intervention overlay ──────────────────────────────────
    private var interventionView: View? = null
    private var interventionParams: WindowManager.LayoutParams? = null
    private var interventionShowing = false
    private var interventionPackage: String? = null
    private var interventionMode: LimitInterventionMode? = null
    private var bannerHideRunnable: Runnable? = null

    // Default fallback passthrough heights in dp if node bounds are not yet computed
    private val DEFAULT_TOP_PASSTHROUGH_DP = 96   // Status bar (~40dp) + YouTube toolbar (~56dp)
    private val DEFAULT_BOT_PASSTHROUGH_DP = 56   // Bottom navigation bar (~56dp)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun accessibilityOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
    }

    private fun statusBarHeightPx(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) {
            context.resources.getDimensionPixelSize(resId)
        } else {
            dp(24)
        }
    }

    fun showFeedBlockOverlay(
        appName: String,
        title: String,
        subtitle: String,
        isDark: Boolean = true,
        customTopPx: Int? = null,
        customHeightPx: Int? = null
    ) {
        // Never stack a feed mask above an active intervention dialog. The dialog
        // owns the screen until the user dismisses it or leaves the app.
        if (interventionShowing && interventionMode != LimitInterventionMode.NOTIFY) {
            return
        }

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            accessibilityOverlayType(),
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

            // Lifelit Brand Emblem
            val iconResId = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            if (iconResId != 0) {
                addView(android.widget.ImageView(context).apply {
                    setImageResource(iconResId)
                    val iconSize = (32 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER
                        bottomMargin = (12 * density).toInt()
                    }
                    if (!isDark) {
                        setColorFilter(Color.parseColor("#111111"))
                    }
                })
            }

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

    // ══════════════════════════════════════════════════════════════════════
    // Time-limit intervention windows
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Shows a native intervention window above the restricted app.
     *
     * @param appName   Human-readable label of the restricted app.
     * @param packageName Target app package, used to load its launcher icon.
     * @param title     Main headline.
     * @param message   Supporting copy.
     * @param mode      Banner, dismissible popup, or full-screen blocker.
     * @param onDismiss Invoked when the user dismisses a non-blocking intervention.
     * @param onOpenLifelit Invoked when the user taps "Kembali ke Lifelit" on a blocker.
     * @return true when the window was attached successfully.
     */
    fun showLimitIntervention(
        appName: String,
        packageName: String,
        title: String,
        message: String,
        mode: LimitInterventionMode,
        isDark: Boolean = true,
        onDismiss: (() -> Unit)? = null,
        onOpenLifelit: (() -> Unit)? = null
    ): Boolean {
        // Avoid rebuilding the same window on every accessibility event.
        if (interventionShowing && interventionPackage == packageName && interventionMode == mode) {
            return true
        }

        hideLimitIntervention()

        val isBanner = mode == LimitInterventionMode.NOTIFY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (isBanner) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT,
            accessibilityOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (isBanner) Gravity.TOP or Gravity.START else Gravity.CENTER
            x = 0
            y = if (isBanner) statusBarHeightPx() else 0
        }

        val view: View = if (isBanner) {
            buildNotificationBanner(appName, packageName, title, message, isDark) {
                hideLimitIntervention()
                onDismiss?.invoke()
            }
        } else {
            buildInterventionDialog(
                packageName = packageName,
                title = title,
                message = message,
                isBlock = mode == LimitInterventionMode.BLOCK,
                isDark = isDark,
                onDismiss = onDismiss,
                onOpenLifelit = onOpenLifelit
            )
        }

        interventionView = view
        interventionParams = params
        interventionPackage = packageName
        interventionMode = mode

        return try {
            windowManager.addView(view, params)
            interventionShowing = true

            if (isBanner) {
                val r = Runnable { hideLimitIntervention() }
                bannerHideRunnable = r
                handler.postDelayed(r, 5_000L)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            interventionView = null
            interventionParams = null
            interventionPackage = null
            interventionMode = null
            interventionShowing = false
            false
        }
    }

    fun hideLimitIntervention() {
        bannerHideRunnable?.let {
            handler.removeCallbacks(it)
            bannerHideRunnable = null
        }

        val view = interventionView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        interventionView = null
        interventionParams = null
        interventionPackage = null
        interventionMode = null
        interventionShowing = false
    }

    /** Hides both the feed mask and any active time-limit intervention. */
    fun hideAllOverlays() {
        hideFeedBlockOverlay()
        hideLimitIntervention()
    }

    private fun buildInterventionDialog(
        packageName: String,
        title: String,
        message: String,
        isBlock: Boolean,
        isDark: Boolean,
        onDismiss: (() -> Unit)?,
        onOpenLifelit: (() -> Unit)?
    ): View {
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) Color.parseColor("#888888") else Color.parseColor("#777777")

        val root = FrameLayout(context).apply {
            setBackgroundColor(
                if (isBlock) Color.parseColor("#F20A0A0A") else Color.parseColor("#99000000")
            )
            isClickable = true
        }

        if (!isBlock) {
            // Tapping the dimmed background dismisses a reminder popup.
            root.setOnClickListener {
                hideLimitIntervention()
                onDismiss?.invoke()
            }
        } else {
            // Swallow every touch so the blocked app cannot be interacted with.
            root.setOnClickListener { /* consume */ }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(if (isDark) Color.parseColor("#0D0D0D") else Color.WHITE)
            }
            isClickable = true
            setOnClickListener { /* consume taps so they don't dismiss the dialog */ }
        }

        // App launcher icon (falls back to Lifelit's notification emblem).
        val iconView = ImageView(context)
        try {
            iconView.setImageDrawable(context.packageManager.getApplicationIcon(packageName))
        } catch (e: Exception) {
            val iconResId = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            if (iconResId != 0) iconView.setImageResource(iconResId)
        }
        card.addView(iconView, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })

        card.addView(TextView(context).apply {
            text = title
            setTextColor(textColor)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        card.addView(TextView(context).apply {
            text = message
            setTextColor(subColor)
            textSize = 12f
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.5f)
            setPadding(0, dp(8), 0, dp(20))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (isBlock) {
            val lifelitButton = buildButton(
                text = "Kembali ke Lifelit",
                primary = true,
                isDark = isDark
            ) {
                hideLimitIntervention()
                onOpenLifelit?.invoke()
            }
            card.addView(lifelitButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        } else {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

            // "Tutup" = Close: closes the restricted app entirely (home action).
            // Highlighted as primary so it's the obvious escape hatch.
            val closeButton = buildButton("Tutup", primary = true, isDark = isDark) {
                hideLimitIntervention()
                onDismiss?.invoke()
                // Close the restricted app by going to home.
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(homeIntent)
                } catch (_: Exception) {
                    try {
                        (context as? android.accessibilityservice.AccessibilityService)
                            ?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    } catch (_: Exception) {
                        // Ignore - best effort to close the app
                    }
                }
            }
            val understandButton = buildButton("Paham", primary = false, isDark = isDark) {
                hideLimitIntervention()
                onDismiss?.invoke()
            }

            row.addView(closeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(View(context), LinearLayout.LayoutParams(dp(10), 1))
            row.addView(understandButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            card.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })

        return root
    }

    private fun buildNotificationBanner(
        appName: String,
        packageName: String,
        title: String,
        message: String,
        isDark: Boolean,
        onTap: () -> Unit
    ): View {
        val outer = FrameLayout(context).apply {
            setPadding(dp(12), dp(12), dp(12), 0)
            isClickable = false
        }

        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (isDark) Color.parseColor("#1A1A1A") else Color.WHITE)
            }
            isClickable = true
            setOnClickListener { onTap() }
            elevation = dp(8).toFloat()
        }

        val iconView = ImageView(context)
        try {
            iconView.setImageDrawable(context.packageManager.getApplicationIcon(packageName))
        } catch (e: Exception) {
            val iconResId = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            if (iconResId != 0) iconView.setImageResource(iconResId)
        }
        iconView.contentDescription = appName
        banner.addView(iconView, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            rightMargin = dp(12)
        })

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        textColumn.addView(TextView(context).apply {
            text = title
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        textColumn.addView(TextView(context).apply {
            text = message
            setTextColor(if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#666666"))
            textSize = 10f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })

        banner.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        banner.addView(TextView(context).apply {
            text = "×"
            setTextColor(if (isDark) Color.parseColor("#888888") else Color.parseColor("#666666"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, 0, 0)
            isClickable = true
            setOnClickListener { onTap() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        outer.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        return outer
    }

    private fun buildButton(
        text: String,
        primary: Boolean,
        isDark: Boolean,
        onClick: () -> Unit
    ): TextView {
        val backgroundColor = if (primary) {
            if (isDark) Color.WHITE else Color.BLACK
        } else {
            if (isDark) Color.parseColor("#1A1A1A") else Color.parseColor("#F3F4F6")
        }
        val foregroundColor = if (primary) {
            if (isDark) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, if (primary) Typeface.BOLD else Typeface.NORMAL)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(backgroundColor)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    val isShowing: Boolean get() = isOverlayShowing
    val isLimitInterventionShowing: Boolean get() = interventionShowing
    val isBlockingInterventionShowing: Boolean
        get() = interventionShowing && interventionMode == LimitInterventionMode.BLOCK
}
