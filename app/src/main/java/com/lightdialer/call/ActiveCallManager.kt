package com.lightdialer.call

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import java.util.IdentityHashMap

/** Holds the live Call objects the system gave us via InCallService, plus UI listeners. */
object ActiveCallManager {

    interface Listener {
        fun onCallsChanged()
    }

    private val calls = IdentityHashMap<Call, Call.Callback>()
    private val listeners = ArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())

    val size: Int
        @Synchronized get() = calls.size

    @Synchronized
    fun add(call: Call, callback: Call.Callback) {
        calls[call] = callback
        // Plain registerCallback delivers on the main looper — fine for our UI needs
        call.registerCallback(callback)
        refreshUi()
    }

    @Synchronized
    fun remove(call: Call) {
        calls.remove(call)?.let { call.unregisterCallback(it) }
        refreshUi()
    }

    @Synchronized
    fun first(): Call? = calls.keys.firstOrNull()

    @Synchronized
    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Re-render all attached UI (called from Call.Callback on the main thread). */
    fun refreshUi() {
        val snapshot: List<Listener>
        synchronized(this) { snapshot = ArrayList(listeners) }
        if (snapshot.isEmpty()) return
        main.post {
            snapshot.forEach { it.onCallsChanged() }
        }
    }
}
