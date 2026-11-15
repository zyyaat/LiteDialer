package com.lightdialer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.lightdialer.DialpadActivity

object DialManager {

    fun placeCall(context: Context, rawNumber: String): Boolean {
        val number = rawNumber.trim()
        if (number.isEmpty()) return false
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun openDialpad(context: Context, number: String = "") {
        val i = Intent(context, DialpadActivity::class.java)
        i.putExtra(DialpadActivity.EXTRA_NUMBER, number)
        context.startActivity(i)
    }
}
