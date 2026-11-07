package com.slate.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var icons: IconLoader
    private lateinit var style: Style

    private lateinit var frame: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var topSpacer: View
    private lateinit var bottomSpacer: View
    private lateinit var favBox: LinearLayout
    private lateinit var appsLayer: LinearLayout
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var rail: AlphabetRail
    private lateinit var bubble: TextView

    private val appAdapter = AppAdapter()
    private val main = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    private var appsOpen = false
    private var ignoreGesture = false
    private var visible: List<AppEntry> = emptyList()
    private var results: List<AppEntry> = emptyList()
    private var webQuery: String? = null

    private val reloadRunnable = Runnable { reloadApps() }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            main.removeCallbacks(reloadRunnable)
            main.postDelayed(reloadRunnable, 400)
        }
    }

    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val a = e1 ?: return false
                val dx = e2.x - a.x
                val dy = e2.y - a.y
                val min = dp(72)
                if (abs(dy) > abs(dx)) {
                    if (dy < -min && vy < -300) openApps()
                    else if (dy > min && vy > 300) onSwipeDown()
                } else if (abs(dx) > min && abs(vx) > 300) {
                    launchByKey(if (dx < 0) prefs.swipeLeftApp else prefs.swipeRightApp)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (prefs.doubleTapLock) lockScreen()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!overFavorite(e)) showLauncherMenu()
            }
        })
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(this, prefs)
        icons = IconLoader(this, prefs)
        buildUi()
        setContentView(frame)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f)
        NotificationState.listener = { refreshBadges() }
        applySettings()
        reloadApps()
    }

    override fun onResume() {
        super.onResume()
        applySettings()
        refreshAll(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (appsOpen) closeApps(true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettings()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (appsOpen) closeApps(true)
        // On the home screen, back does nothing (like every launcher).
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) { }
        NotificationState.listener = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoreGesture = appsOpen || hit(rail, ev)
        }
        if (!ignoreGesture) detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------- UI construction

    @Suppress("DEPRECATION")
    private fun buildUi() {
        frame = FrameLayout(this)
        content = FrameLayout(this)
        frame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.setOnApplyWindowInsetsListener { _, ins ->
            content.setPadding(
                ins.systemWindowInsetLeft, ins.systemWindowInsetTop,
                ins.systemWindowInsetRight, ins.systemWindowInsetBottom
            )
            ins
        }

        // Home page: clock, date, favorites
        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setIncludeFontPadding(false)
            setOnClickListener { openClock() }
        }
        date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setOnClickListener { openCalendar() }
        }
        topSpacer = View(this)
        bottomSpacer = View(this)
        favBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(clock, LinearLayout.LayoutParams(MATCH, WRAP))
        home.addView(date, LinearLayout.LayoutParams(MATCH, WRAP))
        home.addView(topSpacer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        home.addView(favBox, LinearLayout.LayoutParams(MATCH, WRAP))
        home.addView(bottomSpacer, LinearLayout.LayoutParams(MATCH, 0, 0f))
        content.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        // All-apps page: search + list
        appsLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            translationY = dp(40).toFloat()
            isClickable = true
        }
        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = null
            setPadding(dp(4), dp(12), dp(4), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    runSearch(s?.toString() ?: "", true)
                }
            })
            setOnEditorActionListener { _, _, _ ->
                val first = results.firstOrNull()
                val wq = webQuery
                if (first != null) launch(first) else if (wq != null) webSearch(wq)
                true
            }
        }
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            selector = ColorDrawable(0x22888888)
            clipToPadding = false
            adapter = appAdapter
            setOnItemClickListener { _, _, pos, _ ->
                if (pos < results.size) launch(results[pos]) else webQuery?.let { webSearch(it) }
            }
            setOnItemLongClickListener { _, _, pos, _ ->
                if (pos < results.size) { showAppMenu(results[pos]); true } else false
            }
        }
        appsLayer.addView(search, LinearLayout.LayoutParams(MATCH, WRAP))
        appsLayer.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        content.addView(appsLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // Alphabet rail + letter bubble
        rail = AlphabetRail(this)
        rail.onLetter = { c ->
            if (!appsOpen) openApps(false, true)
            bubble.text = c.toString()
            scrollToLetter(c)
        }
        rail.onTouchState = { down -> bubble.visibility = if (down) View.VISIBLE else View.GONE }
        content.addView(
            rail,
            FrameLayout.LayoutParams(dp(36), MATCH, Gravity.END).apply {
                topMargin = dp(72)
                bottomMargin = dp(72)
            }
        )
        bubble = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xCC000000.toInt())
            }
        }
        content.addView(bubble, FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
    }

    // ---------------------------------------------------------------- settings -> UI

    private fun isDark(): Boolean = when (prefs.theme) {
        1 -> true
        2 -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Suppress("DEPRECATION")
    private fun applySettings() {
        val dark = isDark()
        val fg = if (dark) Color.WHITE else 0xFF111111.toInt()
        val accent = Opt.accentColors[prefs.accent.coerceIn(0, Opt.accentColors.lastIndex)]
        val grav = when (prefs.align) {
            1 -> Gravity.CENTER_HORIZONTAL
            2 -> Gravity.END
            else -> Gravity.START
        }
        val tf = Typeface.create(Opt.fontNames[prefs.font.coerceIn(0, Opt.fontNames.lastIndex)], Typeface.NORMAL)
        style = Style(
            fg, accent, tf, Opt.sizeSp[prefs.textSize.coerceIn(0, Opt.sizeSp.lastIndex)],
            grav, prefs.showIcons, prefs.showBadges
        )
        icons.reloadPackIfChanged()

        val alpha = Opt.dimAlpha[prefs.dim.coerceIn(0, Opt.dimAlpha.lastIndex)]
        frame.setBackgroundColor((alpha shl 24) or (if (dark) 0x000000 else 0xFFFFFF))

        val dim = (fg and 0x00FFFFFF) or (0xB3 shl 24)
        clock.setTextColor(fg)
        clock.typeface = tf
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f)
        clock.gravity = grav
        date.setTextColor(dim)
        date.typeface = tf
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        date.gravity = grav
        val clockVis = if (prefs.showClock) View.VISIBLE else View.GONE
        clock.visibility = clockVis
        date.visibility = clockVis

        search.setTextColor(fg)
        search.setHintTextColor((fg and 0x00FFFFFF) or (0x80 shl 24))
        search.typeface = tf
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        search.gravity = Gravity.CENTER_VERTICAL or grav

        // Favorites position (bottom / center / top)
        val (tw, bw) = when (prefs.favPos) {
            1 -> 1f to 1f
            2 -> 0f to 1f
            else -> 1f to 0f
        }
        (topSpacer.layoutParams as LinearLayout.LayoutParams).weight = tw
        (bottomSpacer.layoutParams as LinearLayout.LayoutParams).weight = bw
        home.requestLayout()

        // Rail side and padding
        val side = dp(28)
        val railPad = if (prefs.showRail) dp(44) else side
        val left = if (prefs.railLeft) railPad else side
        val right = if (prefs.railLeft) side else railPad
        home.setPadding(left, dp(32), right, dp(24))
        appsLayer.setPadding(left, dp(16), right, 0)
        (rail.layoutParams as FrameLayout.LayoutParams).gravity =
            if (prefs.railLeft) Gravity.START else Gravity.END
        rail.setColors((fg and 0x00FFFFFF) or (0xCC shl 24), accent)
        rail.haptics = prefs.haptics
        rail.requestLayout()
        updateRailVisibility()

        // System bars
        var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (!dark) {
            f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = f
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        frame.requestApplyInsets()

        appAdapter.notifyDataSetChanged()
        renderFavorites()
    }

    private fun updateRailVisibility() {
        val searching = search.text.isNotEmpty()
        rail.visibility = if (prefs.showRail && !searching) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- data

    private fun reloadApps() {
        bg.execute {
            repo.load()
            runOnUiThread { if (!isDestroyed) refreshAll(false) }
        }
    }

    private fun refreshAll(auto: Boolean) {
        val hidden = prefs.hidden
        visible = repo.all.filter { it.key !in hidden }
        rail.setLetters(visible.map { it.letter }.distinct().sortedWith(compareBy({ it == '#' }, { it })))
        runSearch(search.text.toString(), auto)
        renderFavorites()
    }

    private fun refreshBadges() {
        if (!prefs.showBadges) return
        renderFavorites()
        appAdapter.notifyDataSetChanged()
    }

    private fun score(label: String, q: String): Int = when {
        label.startsWith(q) -> 0
        label.split(' ').drop(1).any { it.startsWith(q) } -> 1
        label.contains(q) -> 2
        else -> -1
    }

    private fun runSearch(q: String, auto: Boolean) {
        val n = norm(q.trim())
        if (n.isEmpty()) {
            results = visible
            webQuery = null
        } else {
            val pool = if (prefs.hiddenInSearch) repo.all else visible
            results = pool.mapNotNull { e ->
                val s = score(e.normLabel, n)
                if (s < 0) null else s to e
            }.sortedWith(compareBy({ it.first }, { it.second.normLabel })).map { it.second }
            webQuery = if (results.isEmpty()) q.trim() else null
            if (auto && prefs.autoLaunch && results.size == 1) {
                launch(results[0])
                return
            }
        }
        updateRailVisibility()
        appAdapter.notifyDataSetChanged()
    }

    private fun scrollToLetter(c: Char) {
        val idx = results.indexOfFirst { it.letter == c }
        if (idx >= 0) list.setSelectionFromTop(idx, 0)
    }

    private fun renderFavorites() {
        favBox.removeAllViews()
        val counts = NotificationState.counts
        for (key in prefs.favorites) {
            val e = repo.byKey[key] ?: continue
            val row = AppRow(this)
            row.applyStyle(style)
            row.bind(e, style, icons, counts[e.pkg] ?: 0)
            row.setOnClickListener { launch(e) }
            row.setOnLongClickListener { showAppMenu(e); true }
            favBox.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    // ---------------------------------------------------------------- open / close app list

    private fun openApps(focusSearch: Boolean = false, fromRail: Boolean = false) {
        if (appsOpen) {
            if (focusSearch) showKeyboard()
            return
        }
        appsOpen = true
        search.setText("")
        list.setSelection(0)
        appsLayer.visibility = View.VISIBLE
        appsLayer.animate().alpha(1f).translationY(0f).setDuration(160).start()
        home.animate().alpha(0f).setDuration(160).start()
        if (focusSearch || (prefs.keyboardOnOpen && !fromRail)) showKeyboard()
    }

    private fun closeApps(animate: Boolean) {
        if (!appsOpen) return
        appsOpen = false
        hideKeyboard()
        search.clearFocus()
        search.setText("")
        if (animate) {
            appsLayer.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(140)
                .withEndAction { if (!appsOpen) appsLayer.visibility = View.GONE }.start()
            home.animate().alpha(1f).setDuration(140).start()
        } else {
            appsLayer.animate().cancel()
            home.animate().cancel()
            appsLayer.visibility = View.GONE
            appsLayer.alpha = 0f
            appsLayer.translationY = dp(40).toFloat()
            home.alpha = 1f
        }
    }

    private fun showKeyboard() {
        search.requestFocus()
        search.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
    }

    // ---------------------------------------------------------------- actions

    private fun launch(e: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(e.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
            closeApps(false)
        } catch (_: Exception) {
            Toast.makeText(this, "Can't open ${e.label}", Toast.LENGTH_SHORT).show()
            reloadApps()
        }
    }

    private fun launchByKey(key: String) {
        val e = repo.byKey[key]
        if (e != null) launch(e)
        else Toast.makeText(this, "Pick a swipe app in Settings > Gestures", Toast.LENGTH_SHORT).show()
    }

    private fun webSearch(q: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    private fun onSwipeDown() {
        when (prefs.swipeDown) {
            0 -> expandNotifications()
            1 -> openApps(true, false)
        }
    }

    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {
            if (!GestureService.notifications()) {
                Toast.makeText(this, "Enable Slate gestures in Accessibility settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun lockScreen() {
        if (!GestureService.lockScreen()) {
            Toast.makeText(this, "Enable Slate gestures in Accessibility settings (Android 9+)", Toast.LENGTH_LONG).show()
        }
    }

    private fun openClock() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    private fun openCalendar() {
        try {
            startActivity(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }

    private fun hit(v: View, ev: MotionEvent): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
            ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }

    private fun overFavorite(e: MotionEvent): Boolean {
        for (i in 0 until favBox.childCount) {
            val v = favBox.getChildAt(i)
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            if (e.rawX >= loc[0] && e.rawX <= loc[0] + v.width &&
                e.rawY >= loc[1] && e.rawY <= loc[1] + v.height
            ) return true
        }
        return false
    }

    // ---------------------------------------------------------------- menus

    private fun dialogTheme() =
        if (isDark()) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    private fun showLauncherMenu() {
        val items = arrayOf("Slate settings", "Change wallpaper")
        AlertDialog.Builder(this, dialogTheme()).setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, SettingsActivity::class.java))
                1 -> try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                } catch (_: Exception) { }
            }
        }.show()
    }

    private fun shortcutsFor(e: AppEntry): List<ShortcutInfo> {
        return try {
            val la = getSystemService(LauncherApps::class.java)
            if (!la.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(e.pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
                .filter { it.isEnabled && (it.activity == null || it.activity == e.component) }
                .take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun showAppMenu(e: AppEntry) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        for (s in shortcutsFor(e)) {
            labels.add(s.shortLabel?.toString() ?: s.id)
            actions.add {
                try {
                    getSystemService(LauncherApps::class.java).startShortcut(s, null, null)
                    closeApps(false)
                } catch (_: Exception) { }
            }
        }

        val favs = prefs.favorites
        if (e.key in favs) {
            labels.add("Remove from favorites")
            actions.add { prefs.favorites = favs - e.key; renderFavorites() }
            labels.add("Move up")
            actions.add { moveFavorite(e.key, -1) }
            labels.add("Move down")
            actions.add { moveFavorite(e.key, 1) }
        } else {
            labels.add("Add to favorites")
            actions.add { prefs.favorites = favs + e.key; renderFavorites() }
        }
        labels.add("Rename")
        actions.add { renameDialog(e) }
        labels.add("Hide app")
        actions.add {
            prefs.hidden = prefs.hidden + e.key
            prefs.favorites = prefs.favorites - e.key
            refreshAll(false)
        }
        labels.add("App info")
        actions.add {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        labels.add("Uninstall")
        actions.add {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg)))
        }

        AlertDialog.Builder(this, dialogTheme())
            .setTitle(e.label)
            .setItems(labels.toTypedArray()) { _, which ->
                try { actions[which]() } catch (_: Exception) { }
            }
            .show()
    }

    private fun moveFavorite(key: String, delta: Int) {
        val l = prefs.favorites.toMutableList()
        val i = l.indexOf(key)
        val j = i + delta
        if (i < 0 || j < 0 || j >= l.size) return
        l.removeAt(i)
        l.add(j, key)
        prefs.favorites = l
        renderFavorites()
    }

    private fun renameDialog(e: AppEntry) {
        val input = EditText(this).apply {
            setText(e.label)
            setSelection(text.length)
            setSingleLine(true)
        }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.setLabel(e.key, input.text.toString())
                reloadApps()
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.setLabel(e.key, null)
                reloadApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- list adapter

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = results.size + if (webQuery != null) 1 else 0
        override fun getItem(position: Int): Any = if (position < results.size) results[position] else (webQuery ?: "")
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? AppRow) ?: AppRow(this@MainActivity).apply {
                layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
            }
            row.applyStyle(style)
            if (position < results.size) {
                val e = results[position]
                row.bind(e, style, icons, NotificationState.counts[e.pkg] ?: 0)
            } else {
                row.bindText("Search the web for \u201C$webQuery\u201D")
            }
            return row
        }
    }
}
