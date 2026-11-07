package com.slate.launcher

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Minimal ADW/Nova-style icon pack reader (appfilter.xml). */
class IconPack(pm: PackageManager, private val pkg: String) {
    private val res: Resources = pm.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()

    init {
        try { parse() } catch (_: Exception) { }
    }

    private fun parse() {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) {
            res.getXml(id)
        } else {
            XmlPullParserFactory.newInstance().newPullParser().also {
                it.setInput(res.assets.open("appfilter.xml"), null)
            }
        }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val dr = parser.getAttributeValue(null, "drawable")
                if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                    map[comp.substring(14, comp.length - 1)] = dr
                }
            }
            ev = parser.next()
        }
    }

    fun iconFor(c: ComponentName): Drawable? {
        val name = map[c.flattenToString()] ?: map[c.flattenToShortString()] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { res.getDrawable(id, null) } catch (_: Exception) { null }
    }
}
