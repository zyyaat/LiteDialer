package com.lightdialer

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the stack trace of any crash to filesDir/last_crash.txt before the
 * system handles it. MainActivity offers to share the file on next launch —
 * this is how we debug device-specific crashes without adb.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, "last_crash.txt").writeText(
                    "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n" +
                        "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
                        sw.toString()
                )
            } catch (_: Exception) {
                // never let the logger itself break crash handling
            }
            systemHandler?.uncaughtException(thread, throwable)
        }
    }
}
