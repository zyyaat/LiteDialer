package com.lightdialer

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.provider.CallLog
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
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
 * v1.2 smoke tests: launches the tabbed main screen on the Samsung A10's API
 * level (28), switches tabs, types on the keypad, inflates every layout, and
 * verifies the provider-failure hardening still holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokeTest {

    @Test
    fun mainActivity_launchesOnApi28() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume().visible()
        controller.pause().stop()
        controller.start().resume()
        controller.destroy()
    }

    @Test
    fun mainActivity_dialIntentPrefillsKeypad() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:01001234567")
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        controller.create().start().resume().visible()
        val dial = controller.get().findViewById<TextView>(R.id.dialText)
        assertTrue("number should appear in dial text: ${dial.text}", dial.text.contains("0100"))
        controller.destroy()
    }

    @Test
    fun mainActivity_switchTabsWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().visible().get()

        activity.findViewById<android.view.View>(R.id.tabRecents).performClick()
        activity.findViewById<android.view.View>(R.id.tabContacts).performClick()
        activity.findViewById<android.view.View>(R.id.tabKeypad).performClick()

        controller.pause().stop().destroy()
    }

    @Test
    fun settingsActivity_launchesAndTogglesTheme() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        activity.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.darkSwitch).performClick()
        // theme change schedules recreate; just make sure nothing crashed
        controller.destroy()
    }

    /** Rows use ?attr/selectableItemBackground, so they need the app theme. */
    @Test
    fun listRows_inflateWithAppTheme() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val themed = ContextThemeWrapper(ctx, R.style.Theme_LightDialer)
        LayoutInflater.from(themed).inflate(R.layout.view_keypad, null)
        LayoutInflater.from(themed).inflate(R.layout.item_call_log, null)
        LayoutInflater.from(themed).inflate(R.layout.item_contact, null)
        LayoutInflater.from(themed).inflate(R.layout.item_section, null)
        LayoutInflater.from(themed).inflate(R.layout.activity_in_call, null)
        LayoutInflater.from(themed).inflate(R.layout.activity_settings, null)
        LayoutInflater.from(themed).inflate(R.layout.activity_main, null)
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
