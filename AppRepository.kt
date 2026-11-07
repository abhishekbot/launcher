package com.slate.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class AppRepository(private val ctx: Context, private val prefs: Prefs) {
    @Volatile var all: List<AppEntry> = emptyList()
        private set
    @Volatile var byKey: Map<String, AppEntry> = emptyMap()
        private set

    fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == ctx.packageName) continue
            val cn = ComponentName(ai.packageName, ai.name)
            val key = cn.flattenToShortString()
            val def = ri.loadLabel(pm)?.toString() ?: ai.packageName
            val e = AppEntry(cn, key, def)
            prefs.label(key)?.let { e.rename(it) }
            out.add(e)
        }
        out.sortWith(compareBy<AppEntry> { it.letter == '#' }.thenBy { it.normLabel })
        all = out
        byKey = out.associateBy { it.key }
        return out
    }
}
