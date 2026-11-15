package com.lightdialer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.lightdialer.call.InCallActivity
import com.lightdialer.data.CallEntry
import com.lightdialer.data.CallLogStore
import com.lightdialer.databinding.ActivityMainBinding
import com.lightdialer.util.DefaultDialer
import com.lightdialer.util.DialManager
import com.lightdialer.util.Perms
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = CallLogAdapter(
        onClick = { entry -> call(entry.number) },
        onLongClick = { entry -> DialManager.openDialpad(this, entry.number) }
    )
    private var consumedIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.callLog.layoutManager = LinearLayoutManager(this)
        b.callLog.setHasFixedSize(true)
        b.callLog.adapter = adapter

        b.openDialpad.setOnClickListener { DialManager.openDialpad(this) }
        b.grantPermissions.setOnClickListener {
            val miss = Perms.missing(this)
            if (miss.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, miss.toTypedArray(), 1)
            }
        }
        b.setDefault.setOnClickListener {
            DefaultDialer.requestIntent(this)?.let { i ->
                try { startActivity(i) } catch (e: Exception) { }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeShowCrashReport()
        refreshState()
        maybeHandleDialIntent()
        CallLogStore.loadAsync(this) { list -> adapter.submit(list) }
    }

    /** If the app crashed last run, offer to share the saved stack trace. */
    private fun maybeShowCrashReport() {
        val file = File(filesDir, "last_crash.txt")
        if (!file.exists()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_body)
            .setPositiveButton(R.string.crash_send) { _, _ ->
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Lite Dialer crash report")
                        putExtra(Intent.EXTRA_TEXT, file.readText())
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.crash_chooser)))
                } catch (e: Exception) { }
            }
            .setNegativeButton(R.string.crash_delete) { _, _ -> file.delete() }
            .show()
    }

    private fun refreshState() {
        val ready = Perms.allGranted(this)
        b.firstRun.visibility = if (ready) View.GONE else View.VISIBLE
        b.title.visibility = if (ready) View.VISIBLE else View.GONE
        b.callLog.visibility = if (ready) View.VISIBLE else View.GONE
        b.openDialpad.visibility = if (ready) View.VISIBLE else View.GONE
        if (ready) {
            b.setDefault.visibility =
                if (DefaultDialer.isDefault(this)) View.GONE else View.VISIBLE
            maybePromptDefaultDialer()
        } else {
            b.setDefault.visibility = View.GONE
        }
    }

    private fun maybePromptDefaultDialer() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (DefaultDialer.isDefault(this)) return
        if (prefs.getBoolean("asked_default", false)) return
        val i = DefaultDialer.requestIntent(this) ?: return
        prefs.edit().putBoolean("asked_default", true).apply()
        try { startActivity(i) } catch (e: Exception) { }
    }

    /** External "tel:" links (browser, contacts...) open our dialpad. */
    private fun maybeHandleDialIntent() {
        val i = intent ?: return
        if (i === consumedIntent) return
        val action = i.action
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            consumedIntent = i
            val number = i.data?.schemeSpecificPart ?: return
            DialManager.openDialpad(this, number)
        }
    }

    private fun call(number: String) {
        if (!Perms.allGranted(this)) {
            refreshState()
            return
        }
        DialManager.placeCall(this, number)
    }
}
