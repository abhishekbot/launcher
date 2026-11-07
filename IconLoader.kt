package com.slate.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Loads icons lazily, off the main thread, into a tiny LRU cache. Nothing is loaded if icons are off. */
class IconLoader(ctx: Context, private val prefs: Prefs) {
    private val pm = ctx.applicationContext.packageManager
    private val cache = LruCache<String, Drawable>(40)
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pack: IconPack? = null
    private var loadedPack: String? = null

    fun reloadPackIfChanged() {
        val p = prefs.iconPack
        if (p == loadedPack) return
        loadedPack = p
        cache.evictAll()
        pack = if (p.isEmpty()) null else try { IconPack(pm, p) } catch (_: Exception) { null }
    }

    private fun fetch(e: AppEntry): Drawable {
        pack?.iconFor(e.component)?.let { return it }
        return try { pm.getActivityIcon(e.component) } catch (_: Exception) { pm.defaultActivityIcon }
    }

    private fun fresh(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

    fun bind(v: ImageView, e: AppEntry) {
        v.tag = e.key
        val c = cache.get(e.key)
        if (c != null) {
            v.setImageDrawable(fresh(c))
            return
        }
        v.setImageDrawable(null)
        exec.execute {
            val d = fetch(e)
            cache.put(e.key, d)
            main.post { if (v.tag == e.key) v.setImageDrawable(fresh(d)) }
        }
    }
}
