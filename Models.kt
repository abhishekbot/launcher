package com.slate.launcher

import android.content.ComponentName
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper

class AppEntry(val component: ComponentName, val key: String, val defaultLabel: String) {
    var label: String = defaultLabel
        private set
    var normLabel: String = norm(defaultLabel)
        private set
    var letter: Char = letterOf(normLabel)
        private set
    val pkg: String get() = component.packageName

    fun rename(l: String) {
        label = l
        normLabel = norm(l)
        letter = letterOf(normLabel)
    }

    companion object {
        fun letterOf(n: String): Char {
            val c = n.firstOrNull()?.uppercaseChar() ?: return '#'
            return if (c in 'A'..'Z') c else '#'
        }
    }
}

class Style(
    val fg: Int,
    val accent: Int,
    val typeface: Typeface,
    val sizeSp: Float,
    val gravity: Int,
    val showIcons: Boolean,
    val showBadges: Boolean
)

/** Holds per-package notification counts, filled by NotificationService. */
object NotificationState {
    @Volatile
    var counts: Map<String, Int> = emptyMap()
    var listener: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun update(m: Map<String, Int>) {
        counts = m
        main.post { listener?.invoke() }
    }
}
