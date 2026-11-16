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
    val duration: Long,
    val accountId: String? = null
)

object CallLogStore {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun loadAsync(context: Context, onLoaded: (List<CallEntry>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            // ANY uncaught exception here kills the whole process the moment
            // the app opens — degrade to an empty list instead.
            val list = try { load(app) } catch (e: Throwable) { emptyList() }
            main.post {
                try { onLoaded(list) } catch (e: Throwable) { }
            }
        }
    }

    private fun load(context: Context): List<CallEntry> {
        if (!Perms.granted(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<CallEntry>(120)
        // Plain sort order — some OEM providers reject appended "LIMIT n".
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            ),
            null,
            null,
            CallLog.Calls.DEFAULT_SORT_ORDER
        ) ?: return out
        cursor.use { c ->
            val iNum = c.getColumnIndex(CallLog.Calls.NUMBER)
            val iName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val iType = c.getColumnIndex(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndex(CallLog.Calls.DATE)
            val iDur = c.getColumnIndex(CallLog.Calls.DURATION)
            val iAcc = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
            // cap manually instead of via SQL LIMIT (OEM-safe)
            while (c.moveToNext() && out.size < 200) {
                out.add(
                    CallEntry(
                        number = if (iNum >= 0) c.getString(iNum) ?: "" else "",
                        name = if (iName >= 0) c.getString(iName)?.takeIf { it.isNotBlank() } else null,
                        type = if (iType >= 0) c.getInt(iType) else CallLog.Calls.MISSED_TYPE,
                        date = if (iDate >= 0) c.getLong(iDate) else 0L,
                        duration = if (iDur >= 0) c.getLong(iDur) else 0L,
                        accountId = if (iAcc >= 0) c.getString(iAcc) else null
                    )
                )
            }
        }
        return out
    }
}
