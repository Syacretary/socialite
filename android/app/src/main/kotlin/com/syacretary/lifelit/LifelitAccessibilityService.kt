package com.syacretary.lifelit

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.syacretary.lifelit.core.AppFeatureHandler
import com.syacretary.lifelit.features.instagram.InstagramHandler
import com.syacretary.lifelit.features.tiktok.TikTokHandler
import com.syacretary.lifelit.features.twitter.TwitterHandler
import com.syacretary.lifelit.features.youtube.YouTubeHandler
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NativeAppLimit(
    val appId: String,
    val packageName: String,
    val isRestricted: Boolean,
    val dailyLimitMinutes: Int,
    val actionOnLimit: Int
)

/**
 * LifelitAccessibilityService
 *
 * Lightweight, modular accessibility dispatcher.
 * Coordinates feature handlers (Instagram, YouTube, TikTok, Twitter)
 * with zero-flicker debounced evaluation and robust lifecycle management.
 */
class LifelitAccessibilityService : AccessibilityService() {

    private var overlayManager: LifelitOverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private var pendingHideRunnable: Runnable? = null
    private var pendingEvalRunnable: Runnable? = null
    private var limitCheckRunnable: Runnable? = null

    private lateinit var featureHandlers: List<AppFeatureHandler>
    private var prefsLoaded = false

    // ── Real-time usage tracking for time-limit enforcement ──────────────
    // UsageStatsManager only commits foreground time when an app is paused,
    // so a continuously scrolling user could pass the limit without any
    // native intervention. We track the live foreground session ourselves
    // and use the higher of (tracked, UsageStats) as the effective usage.
    private val usagePrefs by lazy {
        getSharedPreferences("lifelit_usage_tracking", Context.MODE_PRIVATE)
    }
    private val trackedUsageMs = mutableMapOf<String, Long>()
    private var trackedUsageDate: String = ""
    private var foregroundPkg: String? = null
    private var foregroundSince: Long = 0L
    private var foregroundBaselineMs: Long = 0L

    // UsageStats queries are relatively expensive; cache them briefly.
    private var lastSystemUsageCheckAt = 0L
    private var lastSystemUsagePkg = ""
    private var lastSystemUsageMs = 0L

    // Throttle repeated reminder banners/popups so they don't fire on every
    // accessibility event while the user is still inside the same app.
    private val lastInterventionAt = mutableMapOf<String, Long>()

    companion object {
        var instance: LifelitAccessibilityService? = null
            private set

        private const val PREFS_NAME = "lifelit_rules"
        private const val KEY_RULES  = "active_feature_rules"
        private const val PREFS_CONFIG = "lifelit_app_config"
        private const val KEY_CONFIG = "app_configurations"

        // Minimum gap between two non-blocking interventions for the same app.
        private const val INTERVENTION_COOLDOWN_MS = 60_000L

        // Periodically re-check the limit while a restricted app is foreground,
        // even if the app emits no accessibility events (e.g. long video).
        private const val LIMIT_CHECK_INTERVAL_MS = 15_000L

        private val activeRules = mutableMapOf<String, MutableSet<String>>()
        private val activeLimits = mutableMapOf<String, NativeAppLimit>()

        /**
         * Transient system packages that must NOT cause overlays to hide.
         * Includes heads-up notification popups, quick settings, volume sliders,
         * ColorOS/Realme Smart Sidebar, Samsung Edge Panel, system dialogs, and keyboards.
         */
        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.oplus.smartsidebar",
            "com.coloros.smartsidebar",
            "com.oplus.sidebar",
            "com.coloros.floatlet",
            "com.oplus.screenshot",
            "com.coloros.screenrecorder",
            "com.oplus.safecenter",
            "com.oplus.sublauncher",
            "com.oplus.gesture",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.sidebarservice",
            "com.miui.freeform",
            "com.miui.notification",
            "com.huawei.android.internal.app",
            "android",
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "com.samsung.android.honeyboard"
        )

        private val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )

        /**
         * Strict package comparison: exact match, or a sub-package of [target]
         * (dot boundary). Never a loose substring match, so e.g. Threads
         * (com.instagram.barcelona) is not mistaken for Instagram.
         */
        fun matchesPackage(pkg: String, target: String): Boolean {
            if (target.isEmpty() || pkg.isEmpty()) return false
            return pkg == target || pkg.startsWith("$target.")
        }

        fun isTransientPackage(pkg: String): Boolean {
            if (pkg.isEmpty()) return false
            if (pkg == "android") return true
            return TRANSIENT_SYSTEM_PACKAGES.any { it != "android" && matchesPackage(pkg, it) }
        }

        fun isServiceRunning(): Boolean = instance != null

        fun syncRules(context: Context, rules: Map<String, List<String>>) {
            activeRules.clear()
            rules.forEach { (appId, featureList) ->
                activeRules[appId] = featureList.toMutableSet()
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val flattened = rules.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }
            prefs.edit().putString(KEY_RULES, flattened).apply()
        }

        fun loadRulesFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RULES, null) ?: return
            activeRules.clear()
            raw.split(";").forEach { item ->
                val parts = item.split(":")
                if (parts.size == 2) {
                    val appId = parts[0]
                    val feats = parts[1].split(",").filter { it.isNotBlank() }
                    activeRules[appId] = feats.toMutableSet()
                }
            }
        }

        fun loadLimitsFromPrefs(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_CONFIG, null)
                activeLimits.clear()
                if (raw.isNullOrEmpty()) return
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val appId = keys.next()
                    val obj = json.getJSONObject(appId)
                    val pkg = obj.optString("packageName", "")
                    val isRestricted = obj.optBoolean("isRestricted", false)
                    val dailyLimit = obj.optInt("dailyLimitMinutes", 30)
                    val action = obj.optInt("actionOnLimit", 2)
                    if (isRestricted && pkg.isNotBlank()) {
                        activeLimits[pkg] = NativeAppLimit(appId, pkg, isRestricted, dailyLimit, action)
                    }

                    // Also load feature rules directly from config to ensure full sync
                    val feats = obj.optJSONObject("features")
                    if (feats != null) {
                        val blocked = mutableSetOf<String>()
                        val fKeys = feats.keys()
                        while (fKeys.hasNext()) {
                            val fKey = fKeys.next()
                            if (feats.optBoolean(fKey, false)) {
                                blocked.add(fKey)
                            }
                        }
                        if (blocked.isNotEmpty()) {
                            activeRules[appId] = blocked
                        } else {
                            activeRules.remove(appId)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ensureInitialized()
        loadTrackedUsage()
        registerScreenReceiver()
    }

    // Stop the live usage clock while the screen is off so locked-phone time is
    // not counted against the user's daily limit.
    private var screenReceiver: android.content.BroadcastReceiver? = null

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    cancelPendingHide()
                    cancelPendingEval()
                    updateForeground(null)
                    overlayManager?.hideAllOverlays()
                }
            }
        }
        try {
            registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenReceiver = receiver
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenReceiver = null
    }

    private fun ensureInitialized() {
        if (overlayManager == null) {
            overlayManager = LifelitOverlayManager(this)
        }
        if (!::featureHandlers.isInitialized) {
            featureHandlers = listOf(
                InstagramHandler(),
                YouTubeHandler(),
                TikTokHandler(),
                TwitterHandler()
            )
        }
        if (!prefsLoaded) {
            loadRulesFromPrefs(this)
            loadLimitsFromPrefs(this)
            prefsLoaded = true
        }
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        cancelPendingHide()
        cancelPendingEval()
        stopLimitCheckLoop()
        finalizeForegroundSession()
        overlayManager?.hideAllOverlays()
        overlayManager = null
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        cancelPendingHide()
        cancelPendingEval()
        stopLimitCheckLoop()
        finalizeForegroundSession()
        overlayManager?.hideAllOverlays()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // 1. Our own windows. Ignore overlay events, but if the real Lifelit
        //    activity comes to the foreground (e.g. the blocker's "Kembali ke
        //    Lifelit" button), make sure no intervention window is left on top.
        if (matchesPackage(pkg, "com.syacretary.lifelit")) {
            val className = event.className?.toString() ?: ""
            if (className.contains("MainActivity") || className.contains("FlutterActivity")) {
                updateForeground(null)
                overlayManager?.hideAllOverlays()
            }
            return
        }

        ensureInitialized()

        val handler = findHandlerForPackage(pkg)
        val limitConfig = findLimitForPackage(pkg)

        // 2. If this is neither a managed feature app nor an app with an active
        //    time limit, handle transient system overlays and window transitions.
        if (handler == null && limitConfig == null) {
            val isTransient = isTransientPackage(pkg)
            if (isTransient) {
                // Transient system overlay (smart edge, notification, keyboard) -> Do not dismiss current overlay
                return
            }

            // Genuinely exiting to another user app (e.g. Launcher, Settings, WhatsApp) -> Hide overlay
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                updateForeground(null)
                hideAllOverlaysImmediate()
            }
            return
        }

        // The user is actively inside a managed/limited app. Keep the live usage
        // clock running so limits are enforced without waiting for UsageStats commit.
        updateForeground(pkg)

        // 3. Native Screen Time Limit Enforcement (runs completely standalone in background)
        if (limitConfig != null && limitConfig.isRestricted && limitConfig.dailyLimitMinutes > 0 &&
            isTimeLimitExceeded(pkg, limitConfig.dailyLimitMinutes)
        ) {
            val blocked = enforceTimeLimit(pkg, limitConfig)
            if (blocked) return
        }

        // If an intervention popup is currently displayed on screen, do NOT allow
        // feature handlers to run redirects or tamper with the user's focus!
        if (overlayManager?.isLimitInterventionShowing == true) {
            return
        }

        // Apps without a feature handler (Reddit, Facebook, etc.) only need the
        // time-limit enforcement above; there is no feature evaluation to run.
        if (handler == null) return

        // 4. Fast redirect check for Shorts / Reels / Stories / Home
        val root = rootInActiveWindow
        if (root != null) {
            val rules = getRulesForPackage(pkg)
            if (rules.isNotEmpty()) {
                val handled = handler.onFastRedirect(this, root, event, rules)
                if (handled) {
                    hideOverlayImmediate()
                    return
                }
            }
        }

        // 5. Debounced layout and state evaluation
        scheduleEvaluation(100L)
    }

    /**
     * Applies the configured action when [pkg] has exceeded its daily limit:
     * - notifyOnly (0): Native system notification only, NO overlay banner.
     * - popupReminder (1): Modal reminder popup dialog on top of the target app.
     * - blockAndClose (2): Immediately closes app and navigates to home screen, NO full-screen overlay.
     *
     * @return true when the screen is occupied or app is closed, skipping feature evaluation.
     */
    private fun enforceTimeLimit(pkg: String, limit: NativeAppLimit): Boolean {
        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            limit.appId.replaceFirstChar { it.uppercase() }
        }

        val limitLabel = formatLimit(limit.dailyLimitMinutes)

        return when (limit.actionOnLimit) {
            0 -> { // notifyOnly: send native system notification, NO overlay
                if (canSendNotification(pkg)) {
                    sendNativeLimitNotification(appLabel, limitLabel)
                }
                false
            }
            1 -> { // popupReminder: modal reminder popup over external app
                val om = overlayManager ?: return false
                if (om.isLimitInterventionShowing) {
                    return true
                }
                if (canShowIntervention(pkg)) {
                    om.showLimitIntervention(
                        appName = appLabel,
                        packageName = pkg,
                        title = "Waktumu untuk $appLabel sudah habis",
                        message = "Kamu telah menggunakan $limitLabel hari ini.\nIstirahatlah sejenak dan kembali besok.",
                        mode = LimitInterventionMode.POPUP
                    )
                    return true
                }
                false
            }
            else -> { // blockAndClose: immediately close app and return to home, NO overlay
                cancelPendingEval()
                cancelPendingHide()
                overlayManager?.hideAllOverlays()

                // Immediately close the restricted app
                performGlobalAction(GLOBAL_ACTION_HOME)

                // Brief system toast
                showBlockToast(appLabel, limitLabel)
                true
            }
        }
    }

    private val lastNotificationAt = mutableMapOf<String, Long>()
    private val NOTIFY_COOLDOWN_MS = 300_000L // 5 minutes

    private fun canSendNotification(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNotificationAt[pkg] ?: 0L
        if (now - last < NOTIFY_COOLDOWN_MS) return false
        lastNotificationAt[pkg] = now
        return true
    }

    private fun sendNativeLimitNotification(appName: String, limitLabel: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "lifelit_limit_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Batas Waktu Penggunaan",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi batas waktu harian Lifelit"
                }
                nm.createNotificationChannel(channel)
            }

            val iconResId = resources.getIdentifier("ic_notification", "drawable", packageName).let {
                if (it != 0) it else android.R.drawable.ic_dialog_info
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val notification = builder
                .setSmallIcon(iconResId)
                .setContentTitle("$appName — Batas Waktu")
                .setContentText("Kamu telah menggunakan $appName selama $limitLabel hari ini.")
                .setAutoCancel(true)
                .build()

            nm.notify(appName.hashCode(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var lastToastTime = 0L
    private fun showBlockToast(appName: String, limitLabel: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime < 3500L) return
        lastToastTime = now
        handler.post {
            try {
                Toast.makeText(
                    applicationContext,
                    "Batas harian $appName ($limitLabel) telah tercapai.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
            }
        }
    }

    private fun canShowIntervention(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastInterventionAt[pkg] ?: 0L
        if (now - last < INTERVENTION_COOLDOWN_MS) return false
        lastInterventionAt[pkg] = now
        return true
    }

    private fun formatLimit(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "$hours jam $mins menit"
            hours > 0 -> "$hours jam"
            else -> "$mins menit"
        }
    }

    private fun openLifelitApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Real-time usage tracking
    // ══════════════════════════════════════════════════════════════════════

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureUsageDay() {
        val today = todayKey()
        if (trackedUsageDate != today) {
            trackedUsageDate = today
            trackedUsageMs.clear()
            lastInterventionAt.clear()
            usagePrefs.edit().clear().putString("date", today).apply()

            // Invalidate the cached UsageStats value so yesterday's total can't
            // trigger a block right after midnight.
            lastSystemUsageCheckAt = 0L
            lastSystemUsagePkg = ""
            lastSystemUsageMs = 0L

            // If a session was already running across midnight, restart its
            // baseline so yesterday's time is not counted for today.
            if (foregroundPkg != null) {
                foregroundSince = System.currentTimeMillis()
                foregroundBaselineMs = 0L
            }
        }
    }

    private fun loadTrackedUsage() {
        trackedUsageDate = usagePrefs.getString("date", "") ?: ""
        val today = todayKey()
        if (trackedUsageDate != today) {
            trackedUsageDate = today
            trackedUsageMs.clear()
            usagePrefs.edit().clear().putString("date", today).apply()
            return
        }

        trackedUsageMs.clear()
        val raw = usagePrefs.getString("usage", "") ?: ""
        raw.split(";").forEach { entry ->
            val idx = entry.lastIndexOf(':')
            if (idx > 0) {
                val packageName = entry.substring(0, idx)
                val value = entry.substring(idx + 1).toLongOrNull() ?: 0L
                if (packageName.isNotBlank()) trackedUsageMs[packageName] = value
            }
        }
    }

    private fun persistTrackedUsage() {
        val raw = trackedUsageMs.entries.joinToString(";") { "${it.key}:${it.value}" }
        usagePrefs.edit()
            .putString("date", trackedUsageDate)
            .putString("usage", raw)
            .apply()
    }

    /**
     * Keeps a live foreground clock per package. When the foreground package
     * changes, the elapsed session is committed to today's tracked total.
     */
    private fun updateForeground(pkg: String?) {
        ensureUsageDay()
        val now = System.currentTimeMillis()
        val previous = foregroundPkg

        if (previous == pkg) {
            if (pkg != null && foregroundSince <= 0L) {
                foregroundSince = now
                foregroundBaselineMs = maxOf(trackedUsageMs[pkg] ?: 0L, systemUsageFor(pkg))
                startLimitCheckLoop()
            }
            return
        }

        if (previous != null) {
            val elapsed = now - foregroundSince
            if (elapsed > 0) {
                // Keep the higher of the previously tracked value and the
                // system baseline so usage from before the service started is
                // never lost.
                trackedUsageMs[previous] = maxOf(
                    trackedUsageMs[previous] ?: 0L,
                    foregroundBaselineMs
                ) + elapsed
                persistTrackedUsage()
            }

            // An intervention belongs to the previous app. Hide it before the
            // new app (e.g. Instagram -> TikTok) gets its own enforcement.
            overlayManager?.hideAllOverlays()
        }

        foregroundPkg = pkg
        if (pkg != null) {
            foregroundSince = now
            foregroundBaselineMs = maxOf(trackedUsageMs[pkg] ?: 0L, systemUsageFor(pkg))
            startLimitCheckLoop()
        } else {
            foregroundSince = 0L
            foregroundBaselineMs = 0L
            stopLimitCheckLoop()
        }
    }

    private fun finalizeForegroundSession() {
        updateForeground(null)
    }

    private fun startLimitCheckLoop() {
        if (limitCheckRunnable != null) return

        val runnable = object : Runnable {
            override fun run() {
                val pkg = foregroundPkg
                if (pkg != null) {
                    val limit = findLimitForPackage(pkg)
                    if (limit != null && limit.isRestricted && limit.dailyLimitMinutes > 0 &&
                        isTimeLimitExceeded(pkg, limit.dailyLimitMinutes)
                    ) {
                        enforceTimeLimit(pkg, limit)
                    } else if (overlayManager?.isLimitInterventionShowing == true) {
                        // Limit was raised/removed (or a new day began): release the intervention.
                        overlayManager?.hideLimitIntervention()
                    }
                }
                handler.postDelayed(this, LIMIT_CHECK_INTERVAL_MS)
            }
        }

        limitCheckRunnable = runnable
        handler.postDelayed(runnable, LIMIT_CHECK_INTERVAL_MS)
    }

    private fun stopLimitCheckLoop() {
        limitCheckRunnable?.let {
            handler.removeCallbacks(it)
            limitCheckRunnable = null
        }
    }

    private fun trackedUsageFor(pkg: String): Long {
        ensureUsageDay()
        return if (foregroundPkg == pkg && foregroundSince > 0L) {
            foregroundBaselineMs + (System.currentTimeMillis() - foregroundSince)
        } else {
            trackedUsageMs[pkg] ?: 0L
        }
    }

    private fun systemUsageFor(pkg: String): Long {
        val now = System.currentTimeMillis()
        if (pkg == lastSystemUsagePkg && now - lastSystemUsageCheckAt < 15_000L) {
            return lastSystemUsageMs
        }

        val result = try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = usm.queryAndAggregateUsageStats(
                cal.timeInMillis,
                now
            ) ?: return 0L
            stats[pkg]?.totalTimeInForeground ?: 0L
        } catch (e: Exception) {
            0L
        }

        lastSystemUsagePkg = pkg
        lastSystemUsageCheckAt = now
        lastSystemUsageMs = result
        return result
    }

    private fun isTimeLimitExceeded(pkg: String, limitMinutes: Int): Boolean {
        val thresholdMs = limitMinutes * 60_000L
        val tracked = trackedUsageFor(pkg)
        val system = systemUsageFor(pkg)
        return maxOf(tracked, system) >= thresholdMs
    }

    private fun findHandlerForPackage(pkg: String): AppFeatureHandler? {
        if (!::featureHandlers.isInitialized) return null
        return featureHandlers.firstOrNull { h ->
            h.supportedPackages.any { matchesPackage(pkg, it) }
        }
    }

    /**
     * Resolves the configured limit even when the event package differs from
     * the stored package (e.g. TikTok global vs SEA package aliases).
     */
    private fun findLimitForPackage(pkg: String): NativeAppLimit? {
        activeLimits[pkg]?.let { return it }
        return activeLimits.values.firstOrNull { limit ->
            matchesPackage(pkg, limit.packageName) ||
                (limit.appId == "tiktok" && TIKTOK_PACKAGES.any { matchesPackage(pkg, it) })
        }
    }

    private fun getRulesForPackage(pkg: String): Set<String> {
        val appKey = when {
            matchesPackage(pkg, "com.instagram.android") -> "instagram"
            matchesPackage(pkg, "com.google.android.youtube") -> "youtube"
            TIKTOK_PACKAGES.any { matchesPackage(pkg, it) } -> "tiktok"
            matchesPackage(pkg, "com.twitter.android") -> "x_twitter"
            else -> return emptySet()
        }
        return activeRules[appKey] ?: emptySet()
    }

    private fun scheduleEvaluation(delayMs: Long) {
        cancelPendingEval()
        val r = Runnable {
            evaluateCurrentState()
        }
        pendingEvalRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelPendingEval() {
        pendingEvalRunnable?.let {
            handler.removeCallbacks(it)
            pendingEvalRunnable = null
        }
    }

    private fun cancelPendingHide() {
        pendingHideRunnable?.let {
            handler.removeCallbacks(it)
            pendingHideRunnable = null
        }
    }

    private fun scheduleHideOverlay(delayMs: Long = 350L) {
        if (pendingHideRunnable != null) return
        val r = Runnable {
            overlayManager?.hideFeedBlockOverlay()
            pendingHideRunnable = null
        }
        pendingHideRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun hideOverlayImmediate() {
        cancelPendingHide()
        cancelPendingEval()
        overlayManager?.hideFeedBlockOverlay()
    }

    /** Hides the feed mask and any time-limit intervention window. */
    private fun hideAllOverlaysImmediate() {
        cancelPendingHide()
        cancelPendingEval()
        overlayManager?.hideAllOverlays()
    }

    private fun evaluateCurrentState() {
        // A blocking limit window owns the screen; don't let feature handlers
        // replace it with a feed mask or hide it.
        if (overlayManager?.isBlockingInterventionShowing == true) return

        val root = getActiveManagedRoot() ?: return
        val activePkg = root.packageName?.toString() ?: return
        val handler = findHandlerForPackage(activePkg) ?: run {
            hideOverlayImmediate()
            return
        }

        val rules = getRulesForPackage(activePkg)
        if (rules.isEmpty()) {
            hideOverlayImmediate()
            return
        }

        val om = overlayManager ?: return
        handler.evaluateState(
            service = this,
            root = root,
            rules = rules,
            overlayManager = om,
            scheduleHide = { delayMs -> scheduleHideOverlay(delayMs) },
            hideImmediate = { hideOverlayImmediate() }
        )
    }

    /**
     * Resolves the active root node for managed apps while remaining immune
     * to transient system windows (smart sidebar, floating tools, notification popups, our own overlay).
     */
    private fun getActiveManagedRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""

        // If the focused window belongs to a managed app, use it directly
        if (findHandlerForPackage(pkg) != null) {
            return root
        }

        // If the focused window is a transient system element or our own overlay
        val isTransientOrOurOverlay = matchesPackage(pkg, "com.syacretary.lifelit") || isTransientPackage(pkg)
        if (isTransientOrOurOverlay) {
            try {
                val windowList = windows
                for (w in windowList) {
                    val wRoot = w.root ?: continue
                    val wPkg = wRoot.packageName?.toString() ?: ""
                    if (findHandlerForPackage(wPkg) != null) {
                        return wRoot
                    }
                }
            } catch (e: Exception) {
                return null
            }
            // Transient window is active and managed app is underneath: return null so overlay remains intact!
            return null
        }

        return root
    }
}
