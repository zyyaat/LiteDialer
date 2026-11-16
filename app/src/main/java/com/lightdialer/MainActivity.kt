package com.lightdialer

import android.Manifest
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lightdialer.call.InCallActivity
import com.lightdialer.data.CallEntry
import com.lightdialer.data.CallLogStore
import com.lightdialer.data.ContactRow
import com.lightdialer.data.Contacts
import com.lightdialer.data.Suggestion
import com.lightdialer.databinding.ActivityMainBinding
import com.lightdialer.util.Avatars
import com.lightdialer.util.DefaultDialer
import com.lightdialer.util.DialManager
import com.lightdialer.util.Formats
import com.lightdialer.util.Perms
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "number"
    }

    private lateinit var b: ActivityMainBinding
    private val callAdapter = CallLogAdapter(
        context = this,
        onClick = { entry -> call(entry.number) },
        onLongClick = { entry -> showKeypadPrefilled(entry.number) }
    )
    private val contactsAdapter = ContactsAdapter(
        onClick = { contact -> call(contact.number) },
        onLongClick = { contact -> showKeypadPrefilled(contact.number) }
    )

    private val digits = StringBuilder()
    private var suggestion: Suggestion? = null
    private var tab = 0 // 0 keypad, 1 recents, 2 contacts
    private var simHandles = emptyList<android.telecom.PhoneAccountHandle>()
    private var simIndex = 0
    private var consumedIntent: Intent? = null
    private val handler = Handler(Looper.getMainLooper())
    private val suggestRunnable = Runnable { querySuggestions() }

    private class Key(val digit: Char, val latin: String, val arabic: String)

    private val keys = listOf(
        Key('1', "", ""),
        Key('2', "ABC", "ب ت ث ة"),
        Key('3', "DEF", "أ إ آ ء"),
        Key('4', "GHI", "س ش ص ض"),
        Key('5', "JKL", "د ذ ز"),
        Key('6', "MNO", "ج ح خ"),
        Key('7', "PQRS", "ق ك"),
        Key('8', "TUV", "ف و"),
        Key('9', "WXYZ", "ط ظ ع غ"),
        Key('*', "", ""),
        Key('0', "", "+"),
        Key('#', "", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.callLog.layoutManager = LinearLayoutManager(this)
        b.callLog.setHasFixedSize(true)
        b.callLog.adapter = callAdapter

        b.contactsList.layoutManager = LinearLayoutManager(this)
        b.contactsList.setHasFixedSize(true)
        b.contactsList.adapter = contactsAdapter

        attachSwipe()

        b.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.tabKeypad.setOnClickListener { selectTab(0) }
        b.tabRecents.setOnClickListener { selectTab(1) }
        b.tabContacts.setOnClickListener { selectTab(2) }

        b.keypad.backspace.setOnClickListener {
            if (digits.isNotEmpty()) {
                digits.deleteCharAt(digits.length - 1)
                updateKeypad()
            }
        }
        b.keypad.backspace.setOnLongClickListener {
            digits.clear()
            updateKeypad()
            true
        }
        b.keypad.smsButton.setOnClickListener {
            val number = digits.toString()
            if (number.isNotBlank()) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                            .putExtra("sms_body", "")
                    )
                } catch (e: Exception) { }
            }
        }
        b.keypad.callButton.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            placeTypedCall()
        }
        b.keypad.suggestionRow.setOnClickListener { s ->
            suggestion?.let {
                digits.clear()
                digits.append(it.number)
                updateKeypad()
            }
        }
        b.keypad.simPill.setOnClickListener {
            if (simHandles.size > 1) {
                simIndex = (simIndex + 1) % simHandles.size
                updateSimPill()
            }
        }
        b.addContact.setOnClickListener {
            val number = digits.toString()
            if (number.isNotBlank()) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_INSERT_OR_EDIT)
                            .setType(android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            .putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
                    )
                } catch (e: Exception) { }
            }
        }

        b.grantPermissions.setOnClickListener {
            val miss = Perms.missing(this)
            if (miss.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, miss.toTypedArray(), 1)
            }
        }
        b.setDefault.setOnClickListener { askDefault() }
        b.defaultBanner.setOnClickListener { askDefault() }

        buildPad()
    }

    override fun onResume() {
        super.onResume()
        maybeShowCrashReport()
        refreshPermissionsUI()
        refreshDefaultState()
        maybePromptDefaultDialer()
        maybeHandleDialIntent()
        simHandles = DialManager.simAccounts(this)
        if (simIndex >= simHandles.size) simIndex = 0
        CallLogStore.loadAsync(this) { list -> callAdapter.submit(list) }
        Contacts.allAsync(this) { list -> contactsAdapter.submit(list) }
        selectTab(tab)
        updateKeypad()
    }

    // ---------------- tabs ----------------

    internal fun selectTab(index: Int) {
        tab = index
        val keypadVisible = index == 0
        b.keypad.root.visibility = if (keypadVisible) View.VISIBLE else View.GONE
        b.callLog.visibility = if (index == 1 && Perms.allGranted(this)) View.VISIBLE else View.GONE
        b.contactsList.visibility = if (index == 2 && Perms.allGranted(this)) View.VISIBLE else View.GONE
        b.addContact.visibility = if (keypadVisible && digits.isNotEmpty()) View.VISIBLE else View.GONE

        b.tabKeypadText.setTextColor(ContextCompat.getColor(this, if (index == 0) R.color.accent else R.color.text_secondary))
        b.tabRecentsText.setTextColor(ContextCompat.getColor(this, if (index == 1) R.color.accent else R.color.text_secondary))
        b.tabContactsText.setTextColor(ContextCompat.getColor(this, if (index == 2) R.color.accent else R.color.text_secondary))
        b.underlineKeypad.visibility = if (index == 0) View.VISIBLE else View.INVISIBLE
        b.underlineRecents.visibility = if (index == 1) View.VISIBLE else View.INVISIBLE
        b.underlineContacts.visibility = if (index == 2) View.VISIBLE else View.INVISIBLE
    }

    // ---------------- keypad ----------------

    /** Keys are fixed-height cells with fixed margins: pressing can never move the layout. */
    private fun buildPad() {
        val pad = b.keypad.pad
        keys.chunked(3).forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { k ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = ContextCompat.getDrawable(context, R.drawable.bg_key)
                    layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                        setMargins(dp(8), dp(4), dp(8), dp(4))
                    }
                    setOnClickListener { v ->
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDigit(k.digit)
                    }
                    if (k.digit == '0') {
                        setOnLongClickListener { v ->
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDigit('+')
                            true
                        }
                    }
                    addView(TextView(context).apply {
                        text = k.digit.toString()
                        textSize = 24f
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                    })
                    if (k.latin.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = k.latin
                            textSize = 10f
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            gravity = Gravity.CENTER
                        })
                    }
                    if (k.arabic.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = k.arabic
                            textSize = 10f
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            gravity = Gravity.CENTER
                        })
                    }
                }
                row.addView(cell)
            }
            pad.addView(row)
        }
    }

    private fun onDigit(c: Char) {
        if (digits.length >= 20) return
        digits.append(c)
        updateKeypad()
    }

    private fun updateKeypad() {
        b.keypad.dialText.text = Formats.number(digits.toString())
        val hasNumber = digits.isNotEmpty()

        // Fixed-height rows toggle between GONE and INVISIBLE so nothing shifts
        b.keypad.backspace.visibility = if (hasNumber) View.VISIBLE else View.GONE
        b.keypad.smsButton.visibility = if (hasNumber) View.VISIBLE else View.GONE
        b.addContact.visibility = if (tab == 0 && hasNumber) View.VISIBLE else View.GONE
        updateSimPill()

        handler.removeCallbacks(suggestRunnable)
        if (digits.length >= 2 && Perms.granted(this, Manifest.permission.READ_CONTACTS)) {
            handler.postDelayed(suggestRunnable, 180)
        } else {
            showSuggestion(null)
        }
    }

    private fun querySuggestions() {
        Contacts.suggestionsAsync(this, digits.toString()) { list -> showSuggestion(list.firstOrNull()) }
    }

    private fun showSuggestion(s: Suggestion?) {
        suggestion = s
        if (s == null) {
            b.keypad.suggestionRow.visibility = View.INVISIBLE
            return
        }
        Avatars.bind(b.keypad.suggestionAvatar, s.name)
        b.keypad.suggestionName.text = s.name
        b.keypad.suggestionNumber.text = Formats.number(s.number)
        b.keypad.suggestionRow.visibility = View.VISIBLE
    }

    private fun updateSimPill() {
        val show = digits.isNotEmpty() && simHandles.size > 1
        b.keypad.simPill.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            b.keypad.simPill.text = String.format(Locale.US, "SIM %d", simIndex + 1)
        }
    }

    private fun showKeypadPrefilled(number: String) {
        digits.clear()
        digits.append(number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
        selectTab(0)
        updateKeypad()
    }

    private fun placeTypedCall() {
        val number = digits.toString()
        if (number.isBlank()) return
        if (!Perms.allGranted(this)) {
            refreshPermissionsUI()
            return
        }
        DialManager.placeCall(this, number, simHandles.getOrNull(simIndex))
        digits.clear()
        updateKeypad()
    }

    private fun call(number: String) {
        if (!Perms.allGranted(this)) {
            refreshPermissionsUI()
            return
        }
        DialManager.placeCall(this, number)
    }

    // ---------------- swipe to call ----------------

    /** Swipe a recents row (either direction) to call that number right away. */
    private fun attachSwipe() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val entry = callAdapter.entryAt(pos)
                if (entry != null && Perms.allGranted(this@MainActivity)) {
                    call(entry.number)
                }
                callAdapter.notifyItemChanged(pos)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                    val v = vh.itemView
                    val reveal = min(abs(dX), v.width.toFloat())
                    val paint = Paint().apply {
                        color = ContextCompat.getColor(this@MainActivity, R.color.green)
                    }
                    val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_call)!!.mutate()
                    val cy = v.top + v.height / 2f
                    val cx: Float
                    val rectLeft: Float
                    val rectRight: Float
                    if (dX > 0) {
                        rectLeft = v.left.toFloat()
                        rectRight = v.left + reveal
                        cx = v.left + reveal / 2f
                    } else {
                        rectLeft = v.right - reveal
                        rectRight = v.right.toFloat()
                        cx = v.right - reveal / 2f
                    }
                    c.drawRect(rectLeft, v.top.toFloat(), rectRight, v.bottom.toFloat(), paint)
                    val size = dp(22)
                    icon.setBounds((cx - size).toInt(), (cy - size).toInt(), (cx + size).toInt(), (cy + size).toInt())
                    icon.setTint(ContextCompat.getColor(this@MainActivity, R.color.white))
                    icon.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(b.callLog)
    }

    // ---------------- permissions / default dialer ----------------

    private fun refreshPermissionsUI() {
        val ready = Perms.allGranted(this)
        b.firstRun.visibility = if (ready) View.GONE else View.VISIBLE
        if (!ready) {
            b.title.visibility = View.GONE
        } else {
            b.title.visibility = View.VISIBLE
        }
        if (ready) {
            refreshDefaultState()
        }
    }

    private fun refreshDefaultState() {
        val isDefault = DefaultDialer.isDefault(this)
        val show = Perms.allGranted(this) && !isDefault
        b.defaultBanner.visibility = if (show) View.VISIBLE else View.GONE
        b.setDefault.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun askDefault() {
        val i = DefaultDialer.requestIntent(this) ?: return
        try { startActivity(i) } catch (e: Exception) { }
    }

    private fun maybePromptDefaultDialer() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (DefaultDialer.isDefault(this)) return
        if (prefs.getBoolean("asked_default", false)) return
        val i = DefaultDialer.requestIntent(this) ?: return
        prefs.edit().putBoolean("asked_default", true).apply()
        try { startActivity(i) } catch (e: Exception) { }
    }

    /** External "tel:" links (browser, contacts...) open our keypad pre-filled. */
    private fun maybeHandleDialIntent() {
        val i = intent ?: return
        if (i === consumedIntent) return
        val action = i.action
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            consumedIntent = i
            val number = i.data?.schemeSpecificPart ?: return
            showKeypadPrefilled(number)
        } else if (i.hasExtra(EXTRA_NUMBER)) {
            consumedIntent = i
            showKeypadPrefilled(i.getStringExtra(EXTRA_NUMBER) ?: "")
        }
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
