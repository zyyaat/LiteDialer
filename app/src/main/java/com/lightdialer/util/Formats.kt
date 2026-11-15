package com.lightdialer.util

import android.telephony.PhoneNumberUtils
import java.util.Locale

object Formats {

    private const val DIALABLE = "0123456789+"

    fun number(raw: String): String {
        if (raw.any { it !in DIALABLE }) return raw
        return try {
            PhoneNumberUtils.formatNumber(raw, Locale.getDefault().country) ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }
}
