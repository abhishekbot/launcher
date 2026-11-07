package com.slate.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** A-Z fast-scroll rail. Only letters that actually have apps are drawn. */
class AlphabetRail(ctx: Context) : View(ctx) {
    private var letters: List<Char> = emptyList()
    var onLetter: ((Char) -> Unit)? = null
    var onTouchState: ((Boolean) -> Unit)? = null
    var haptics = true
    private var active = -1
    private var normal = Color.WHITE
    private var accent = Color.CYAN
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun setLetters(l: List<Char>) {
        letters = l
        invalidate()
    }

    fun setColors(n: Int, a: Int) {
        normal = n
        accent = a
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (letters.isEmpty()) return
        val step = height.toFloat() / letters.size
        paint.textSize = minOf(step * 0.62f, 13f * resources.displayMetrics.density)
        for (i in letters.indices) {
            paint.color = if (i == active) accent else normal
            paint.isFakeBoldText = i == active
            val y = step * i + step / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(letters[i].toString(), width / 2f, y, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouchState?.invoke(true)
                select(e.y)
            }
            MotionEvent.ACTION_MOVE -> select(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = -1
                onTouchState?.invoke(false)
                invalidate()
            }
        }
        return true
    }

    private fun select(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        if (i != active) {
            active = i
            if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetter?.invoke(letters[i])
            invalidate()
        }
    }
}
