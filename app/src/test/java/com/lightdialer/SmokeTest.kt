package com.lightdialer

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.provider.CallLog
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.lightdialer.data.CallLogStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

/**
 * Launches the real activities and inflates the real list-row layouts on the
 * Samsung A10's API level (28). Any crash the user sees on launch will fail
 * these tests with the actual stack trace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokeTest {

    @Test
    fun mainActivity_launchesOnApi28() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume().visible()
        // simulate backgrounding and returning
        controller.pause().stop()
        controller.start().resume()
        controller.destroy()
    }

    @Test
    fun mainActivity_handlesDialIntent() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:0123456789")
        }
        Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume().visible().destroy()
    }

    @Test
    fun dialpadActivity_launchesAndTypes() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), DialpadActivity::class.java)
            .putExtra(DialpadActivity.EXTRA_NUMBER, "0100")
        val controller = Robolectric.buildActivity(DialpadActivity::class.java, intent)
        val activity = controller.create().start().resume().visible().get()

        // backspace once, then clear all via long press
        activity.findViewById<android.widget.ImageButton>(R.id.backspace).performClick()
        activity.findViewById<android.widget.ImageButton>(R.id.backspace).performLongClick()

        controller.pause().stop().destroy()
    }

    /** Rows use ?attr/selectableItemBackground, so they need the app theme. */
    @Test
    fun listRows_inflateWithAppTheme() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val themed = ContextThemeWrapper(ctx, R.style.Theme_LightDialer)
        LayoutInflater.from(themed).inflate(R.layout.item_call_log, null)
        LayoutInflater.from(themed).inflate(R.layout.item_suggestion, null)
        LayoutInflater.from(themed).inflate(R.layout.activity_in_call, null)
        LayoutInflater.from(themed).inflate(R.layout.activity_dialpad, null)
    }

    /**
     * Simulates an OEM call-log provider that throws (e.g. Samsung rejecting a
     * query shape). The app must degrade to an empty list, never crash.
     */
    @Test
    fun callLogLoad_survivesProviderFailure() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)

        val provider = ThrowingProvider()
        provider.attachInfo(app, null)
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, provider)

        var loaded: List<*>? = null
        CallLogStore.loadAsync(app) { list -> loaded = list }

        // pump the main looper until the background callback lands
        val deadline = System.currentTimeMillis() + 10_000
        while (loaded == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("callback never fired", loaded != null)
        assertTrue("expected empty list on provider failure", loaded!!.isEmpty())
    }

    private class ThrowingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor =
            throw RuntimeException("simulated OEM provider failure")
        override fun insert(uri: Uri, v: ContentValues?) = null
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
        override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
        override fun getType(uri: Uri) = "vnd.android.cursor.dir/calls"
    }
}
