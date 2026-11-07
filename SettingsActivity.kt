package com.slate.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout
    private var dark = false
    private var fg = 0
    private var sub = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        fg = if (dark) Color.WHITE else 0xFF111111.toInt()
        sub = if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(if (dark) 0xFF121212.toInt() else Color.WHITE)
        }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(32))
        }
        scroll.addView(box)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    // ---------------------------------------------------------------- helpers

    private fun accent() = Opt.accentColors[prefs.accent.coerceIn(0, Opt.accentColors.lastIndex)]
        .let { if (!dark && it == Color.WHITE) 0xFF4FC3F7.toInt() else it }

    private fun dialog() = AlertDialog.Builder(
        this,
        if (dark) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    )

    private fun header(t: String) {
        box.addView(TextView(this).apply {
            text = t
            setTextColor(accent())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isAllCaps = true
            setPadding(dp(20), dp(24), dp(20), dp(6))
        })
    }

    private fun row(title: String, summary: String?, trailing: View?, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(13), dp(20), dp(13))
            isClickable = true
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })
        if (summary != null) {
            col.addView(TextView(this).apply {
                text = summary
                setTextColor(sub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        r.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (trailing != null) r.addView(trailing)
        box.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun toggle(title: String, summary: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            isChecked = value
            isClickable = false
            isFocusable = false
        }
        row(title, summary, sw) {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
    }

    private fun choice(title: String, labels: List<String>, cur: Int, set: (Int) -> Unit) {
        row(title, labels.getOrNull(cur) ?: "", null) {
            dialog().setTitle(title)
                .setSingleChoiceItems(labels.toTypedArray(), cur) { d, which ->
                    set(which)
                    d.dismiss()
                    build()
                }
                .show()
        }
    }

    private fun hasNotifAccess() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun hasA11y() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true

    private fun appLabel(key: String): String {
        if (key.isEmpty()) return "None"
        return try {
            val cn = ComponentName.unflattenFromString(key)!!
            packageManager.getActivityInfo(cn, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            "None"
        }
    }

    private fun pickApp(title: String, current: String, set: (String) -> Unit) {
        val apps = AppRepository(this, prefs).load()
        val names = arrayOf("None") + apps.map { it.label }
        val idx = apps.indexOfFirst { it.key == current } + 1
        dialog().setTitle(title)
            .setSingleChoiceItems(names, idx) { d, which ->
                set(if (which == 0) "" else apps[which - 1].key)
                d.dismiss()
                build()
            }
            .show()
    }

    private fun hiddenAppsDialog() {
        val apps = AppRepository(this, prefs).load()
        val hidden = prefs.hidden.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].key in hidden }
        dialog().setTitle("Hidden apps")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, which, on ->
                if (on) hidden.add(apps[which].key) else hidden.remove(apps[which].key)
            }
            .setPositiveButton("Done") { _, _ ->
                prefs.hidden = hidden
                prefs.favorites = prefs.favorites.filter { it !in hidden }
                build()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun findIconPacks(): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        for (action in listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")) {
            for (ri in packageManager.queryIntentActivities(Intent(action), 0)) {
                seen[ri.activityInfo.packageName] = ri.loadLabel(packageManager).toString()
            }
        }
        return seen.toList()
    }

    private fun iconPackDialog() {
        val packs = findIconPacks()
        val names = arrayOf("Default (system icons)") + packs.map { it.second }
        val idx = packs.indexOfFirst { it.first == prefs.iconPack } + 1
        dialog().setTitle("Icon pack")
            .setSingleChoiceItems(names, idx) { d, which ->
                prefs.iconPack = if (which == 0) "" else packs[which - 1].first
                if (which > 0) prefs.showIcons = true
                d.dismiss()
                build()
            }
            .show()
        if (packs.isEmpty()) {
            Toast.makeText(this, "No ADW/Nova-compatible icon packs installed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- screen

    private fun build() {
        box.removeAllViews()
        box.addView(TextView(this).apply {
            text = "Slate Launcher"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setPadding(dp(20), dp(24), dp(20), dp(4))
        })

        header("Appearance")
        choice("Theme", Opt.themeLabels, prefs.theme) { prefs.theme = it }
        choice("Background tint", Opt.dimLabels, prefs.dim) { prefs.dim = it }
        choice("Accent colour", Opt.accentLabels, prefs.accent) { prefs.accent = it }
        choice("Font", Opt.fontLabels, prefs.font) { prefs.font = it }
        choice("Text size", Opt.sizeLabels, prefs.textSize) { prefs.textSize = it }
        choice("Text alignment", Opt.alignLabels, prefs.align) { prefs.align = it }
        choice("Favorites position", Opt.favPosLabels, prefs.favPos) { prefs.favPos = it }
        toggle("Clock and date", "Tap the clock for alarms, the date for calendar", prefs.showClock) { prefs.showClock = it }
        toggle("Show app icons", "Off by default for the lightest, fastest list", prefs.showIcons) { prefs.showIcons = it }
        row("Icon pack", if (prefs.iconPack.isEmpty()) "Default (system icons)" else prefs.iconPack, null) { iconPackDialog() }

        header("Alphabet rail")
        toggle("Show A-Z rail", null, prefs.showRail) { prefs.showRail = it }
        choice("Rail side", Opt.railSideLabels, if (prefs.railLeft) 1 else 0) { prefs.railLeft = it == 1 }
        toggle("Haptic feedback", "Tick while sliding along the rail", prefs.haptics) { prefs.haptics = it }

        header("Apps")
        row("Hidden apps", "${prefs.hidden.size} hidden", null) { hiddenAppsDialog() }
        toggle("Search finds hidden apps", null, prefs.hiddenInSearch) { prefs.hiddenInSearch = it }
        toggle("Open keyboard with app list", null, prefs.keyboardOnOpen) { prefs.keyboardOnOpen = it }
        toggle("Launch on single match", "Open the app automatically when only one result is left", prefs.autoLaunch) { prefs.autoLaunch = it }

        header("Notifications")
        toggle(
            "Notification badges",
            if (hasNotifAccess()) "Shows unread counts next to apps" else "Needs notification access (opens system settings)",
            prefs.showBadges
        ) {
            prefs.showBadges = it
            if (it && !hasNotifAccess()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        header("Gestures")
        choice("Swipe down", Opt.swipeDownLabels, prefs.swipeDown) { prefs.swipeDown = it }
        row("Swipe left opens", appLabel(prefs.swipeLeftApp), null) {
            pickApp("Swipe left opens", prefs.swipeLeftApp) { prefs.swipeLeftApp = it }
        }
        row("Swipe right opens", appLabel(prefs.swipeRightApp), null) {
            pickApp("Swipe right opens", prefs.swipeRightApp) { prefs.swipeRightApp = it }
        }
        toggle(
            "Double tap to lock",
            if (hasA11y()) "Accessibility service is on" else "Needs the Slate gestures accessibility service (Android 9+)",
            prefs.doubleTapLock
        ) {
            prefs.doubleTapLock = it
            if (it && !hasA11y()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        header("System")
        row("Default home app", "Make Slate your launcher", null) {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) { }
        }
        row("About", "Slate Launcher 1.0. No ads, no tracking, no internet permission.", null) { }
    }
}
