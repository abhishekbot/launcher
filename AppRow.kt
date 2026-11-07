package com.slate.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** One reusable row: [icon] name [badge]. Used by both favorites and the app list. */
class AppRow(ctx: Context) : LinearLayout(ctx) {
    private val icon = ImageView(ctx)
    private val name = TextView(ctx)
    private val badge = TextView(ctx)
    private var applied: Style? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ctx.dp(9), 0, ctx.dp(9))

        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(icon, LayoutParams(ctx.dp(40), ctx.dp(40)).apply { marginEnd = ctx.dp(14) })

        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        addView(name, LayoutParams(WRAP, WRAP))

        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        badge.gravity = Gravity.CENTER
        badge.setTextColor(Color.BLACK)
        badge.setPadding(ctx.dp(7), ctx.dp(1), ctx.dp(7), ctx.dp(1))
        addView(badge, LayoutParams(WRAP, WRAP).apply { marginStart = ctx.dp(10) })
    }

    fun applyStyle(s: Style) {
        if (applied === s) return
        applied = s
        name.setTextColor(s.fg)
        name.typeface = s.typeface
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp)
        gravity = Gravity.CENTER_VERTICAL or s.gravity
        badge.background = GradientDrawable().apply {
            cornerRadius = context.dp(10).toFloat()
            setColor(s.accent)
        }
    }

    fun bind(e: AppEntry, s: Style, icons: IconLoader, count: Int) {
        name.text = e.label
        if (s.showIcons) {
            icon.visibility = View.VISIBLE
            icons.bind(icon, e)
        } else {
            icon.visibility = View.GONE
        }
        if (s.showBadges && count > 0) {
            badge.visibility = View.VISIBLE
            badge.text = if (count > 99) "99+" else count.toString()
        } else {
            badge.visibility = View.GONE
        }
    }

    fun bindText(text: String) {
        name.text = text
        icon.visibility = View.GONE
        badge.visibility = View.GONE
    }
}
