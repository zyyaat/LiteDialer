package com.lightdialer.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Bound by the system while calls are live. Opens our in-call screen and
 * keeps the notification in sync. Zero work while no call is active.
 */
class InCallServiceImpl : InCallService() {

    companion object {
        @Volatile
        var instance: InCallServiceImpl? = null
            private set
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            ActiveCallManager.refreshUi()
            OngoingCallNotifier.refresh(this@InCallServiceImpl)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            ActiveCallManager.refreshUi()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        ActiveCallManager.add(call, callback)
        OngoingCallNotifier.refresh(this)
        val i = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(i)
        } catch (e: Exception) {
            // UI launch must never break the call itself
        }
    }

    override fun onCallRemoved(call: Call) {
        ActiveCallManager.remove(call)
        OngoingCallNotifier.refresh(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        ActiveCallManager.refreshUi()
    }
}
