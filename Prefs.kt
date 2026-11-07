package com.slate.launcher

import android.content.Context
import kotlin.reflect.KProperty

object Opt {
    val themeLabels = listOf("Follow system", "Dark", "Light")
    val dimLabels = listOf("None", "25%", "50%", "75%")
    val dimAlpha = intArrayOf(0, 0x40, 0x80, 0xC0)
    val accentLabels = listOf("Sky", "Mint", "Amber", "Coral", "Violet", "White")
    val accentColors = intArrayOf(
        0xFF4FC3F7.toInt(), 0xFF69F0AE.toInt(), 0xFFFFCA28.toInt(),
        0xFFFF7043.toInt(), 0xFFB388FF.toInt(), 0xFFFFFFFF.toInt()
    )
    val fontLabels = listOf("Default", "Light", "Medium", "Thin", "Condensed", "Serif", "Monospace")
    val fontNames = listOf(
        "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-thin",
        "sans-serif-condensed", "serif", "monospace"
    )
    val sizeLabels = listOf("Small", "Medium", "Large", "Extra large")
    val sizeSp = floatArrayOf(18f, 22f, 26f, 30f)
    val alignLabels = listOf("Left", "Center", "Right")
    val favPosLabels = listOf("Bottom", "Center", "Top")
    val railSideLabels = listOf("Right", "Left")
    val swipeDownLabels = listOf("Notification panel", "Search", "Nothing")
}

class Prefs(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("slate", Context.MODE_PRIVATE)
    private val names = app.getSharedPreferences("slate_names", Context.MODE_PRIVATE)

    private inner class IntP(val k: String, val d: Int) {
        operator fun getValue(r: Any?, p: KProperty<*>): Int = sp.getInt(k, d)
        operator fun setValue(r: Any?, p: KProperty<*>, v: Int) { sp.edit().putInt(k, v).apply() }
    }

    private inner class BoolP(val k: String, val d: Boolean) {
        operator fun getValue(r: Any?, p: KProperty<*>): Boolean = sp.getBoolean(k, d)
        operator fun setValue(r: Any?, p: KProperty<*>, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    }

    private inner class StrP(val k: String, val d: String) {
        operator fun getValue(r: Any?, p: KProperty<*>): String = sp.getString(k, d) ?: d
        operator fun setValue(r: Any?, p: KProperty<*>, v: String) { sp.edit().putString(k, v).apply() }
    }

    // Appearance
    var theme by IntP("theme", 0)
    var dim by IntP("dim", 2)
    var accent by IntP("accent", 0)
    var font by IntP("font", 1)
    var textSize by IntP("textSize", 1)
    var align by IntP("align", 0)
    var favPos by IntP("favPos", 0)
    var showClock by BoolP("showClock", true)
    var showIcons by BoolP("showIcons", false)
    var iconPack by StrP("iconPack", "")

    // Layout
    var showRail by BoolP("showRail", true)
    var railLeft by BoolP("railLeft", false)

    // Behaviour
    var keyboardOnOpen by BoolP("kbOpen", false)
    var autoLaunch by BoolP("autoLaunch", false)
    var hiddenInSearch by BoolP("hiddenInSearch", false)
    var haptics by BoolP("haptics", true)
    var showBadges by BoolP("badges", false)

    // Gestures
    var swipeDown by IntP("swipeDown", 0)
    var swipeLeftApp by StrP("swipeLeft", "")
    var swipeRightApp by StrP("swipeRight", "")
    var doubleTapLock by BoolP("doubleTapLock", false)

    var favorites: List<String>
        get() = (sp.getString("favs", "") ?: "").split("\n").filter { it.isNotEmpty() }
        set(v) { sp.edit().putString("favs", v.joinToString("\n")).apply() }

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("hidden", HashSet(v)).apply() }

    fun label(key: String): String? = names.getString(key, null)

    fun setLabel(key: String, v: String?) {
        if (v.isNullOrBlank()) names.edit().remove(key).apply()
        else names.edit().putString(key, v.trim()).apply()
    }
}
