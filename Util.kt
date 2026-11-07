package com.slate.launcher

import android.content.Context
import android.view.ViewGroup
import java.text.Normalizer

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

private val MARKS = Regex("\\p{M}+")

/** Lower-case and strip accents so "Café" sorts and searches like "cafe". */
fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
