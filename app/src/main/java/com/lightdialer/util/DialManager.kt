package com.lightdialer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

object DialManager {

    /** The device's call-capable SIM/account handles, used for the SIM pill. */
    fun simAccounts(context: Context): List<PhoneAccountHandle> {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            tm.callCapablePhoneAccounts ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun placeCall(context: Context, rawNumber: String, account: PhoneAccountHandle? = null): Boolean {
        val number = rawNumber.trim()
        if (number.isEmpty()) return false
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val extras = Bundle()
        if (account != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        }
        return try {
            tm.placeCall(Uri.fromParts("tel", number, null), extras)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Opens the main screen with the keypad tab focused and the number pre-filled. */
    fun openKeypad(context: Context, number: String = "") {
        val i = Intent(context, com.lightdialer.MainActivity::class.java)
        i.putExtra(com.lightdialer.MainActivity.EXTRA_NUMBER, number)
        if (context !is android.app.Activity) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}
