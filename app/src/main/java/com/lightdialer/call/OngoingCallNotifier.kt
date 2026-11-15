package com.lightdialer.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.TelecomManager
import com.lightdialer.R
import com.lightdialer.data.Contacts

/** Posts the required "ongoing call" notification while a call is alive. */
object OngoingCallNotifier {

    private const val CHANNEL_ID = "ongoing_calls"
    private const val NOTIF_ID = 42

    private val nameCache = HashMap<String, String>()

    fun refresh(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val call = ActiveCallManager.first()
        if (call == null) {
            nm.cancel(NOTIF_ID)
            return
        }
        ensureChannel(context)

        val intent = Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(context.getString(R.string.notification_ongoing))
            .setContentText(resolveName(context, call))
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setShowWhen(false)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private fun resolveName(context: Context, call: Call): String {
        val details = call.details ?: return ""
        val display = details.callerDisplayName?.takeIf {
            it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
        }
        if (display != null) return display
        val number = details.handle?.schemeSpecificPart ?: ""
        nameCache[number]?.let { return it }
        if (number.isNotBlank()) {
            Contacts.lookupNameAsync(context, number) { name ->
                if (name != null) {
                    nameCache[number] = name
                    refresh(context)
                }
            }
        }
        return number
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_ongoing),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }
}
