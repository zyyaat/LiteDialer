package com.lightdialer.util

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.lightdialer.R

object Avatars {

    private val PALETTE = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
        R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
    )

    /** Draws a colored circle with the first letter — zero image loading, zero RAM cost. */
    fun bind(view: TextView, label: String) {
        val key = label.trim()
        val letter = key.firstOrNull { it.isLetterOrDigit() } ?: '#'
        val color = view.resources.getColor(
            PALETTE[Math.abs(label.hashCode()) % PALETTE.size],
            view.context.theme
        )
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        view.text = letter.uppercaseChar().toString()
    }
}
