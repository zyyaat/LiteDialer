package com.lightdialer.data

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import com.lightdialer.util.Perms
import java.util.concurrent.Executors

data class CallEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

object CallLogStore {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun loadAsync(context: Context, onLoaded: (List<CallEntry>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val list = try { load(app) } catch (e: SecurityException) { emptyList() }
            main.post { onLoaded(list) }
        }
    }

    private fun load(context: Context): List<CallEntry> {
        if (!Perms.granted(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<CallEntry>(120)
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null,
            null,
            CallLog.Calls.DEFAULT_SORT_ORDER + " LIMIT 200"
        ) ?: return out
        cursor.use { c ->
            val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val iDur = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                out.add(
                    CallEntry(
                        number = c.getString(iNum) ?: "",
                        name = c.getString(iName)?.takeIf { it.isNotBlank() },
                        type = c.getInt(iType),
                        date = c.getLong(iDate),
                        duration = c.getLong(iDur)
                    )
                )
            }
        }
        return out
    }
}
