package com.lightdialer.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lightdialer.R
import com.lightdialer.data.Contacts
import com.lightdialer.databinding.ActivityInCallBinding
import com.lightdialer.util.Avatars
import com.lightdialer.util.Formats

/**
 * The Samsung-style call screen: teal gradient, big white name, a translucent
 * control panel (mute / speaker / hold / keypad) and the big red end button.
 * While ringing it swaps to the two big round answer / decline buttons.
 */
class InCallActivity : AppCompatActivity(), ActiveCallManager.Listener {

    companion object {
        private var current: InCallActivity? = null

        fun intent(context: Context) = Intent(context, InCallActivity::class.java)

        fun finishIfShowing() {
            current?.finish()
        }
    }

    private lateinit var b: ActivityInCallBinding
    private var call: Call? = null
    private var nameResolved = false
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(b.root)
        current = this

        b.answer.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        b.decline.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            call?.reject(false, null)
        }
        b.endCall.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            call?.disconnect()
        }
        b.mute.setOnClickListener { toggleMute() }
        b.speaker.setOnClickListener { toggleSpeaker() }
        b.hold.setOnClickListener { toggleHold() }
        b.keypad.setOnClickListener { toggleKeypad() }

        buildDtmf()
    }

    override fun onStart() {
        super.onStart()
        ActiveCallManager.addListener(this)
        onCallsChanged()
    }

    override fun onStop() {
        super.onStop()
        ActiveCallManager.removeListener(this)
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onCallsChanged() {
        val c = ActiveCallManager.first()
        if (c == null) {
            finish()
            return
        }
        if (c !== call) {
            call = c
            nameResolved = false
        }
        render()
    }

    private fun render() {
        val c = call ?: return
        val details = c.details

        val ringing = c.state == Call.STATE_RINGING
        b.ringingRow.visibility = if (ringing) View.VISIBLE else View.GONE
        b.controlsPanel.visibility = if (ringing) View.GONE else View.VISIBLE
        b.endCall.visibility = if (ringing) View.GONE else View.VISIBLE

        when (c.state) {
            Call.STATE_RINGING -> b.callState.setText(R.string.state_ringing)
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW,
            Call.STATE_SELECT_PHONE_ACCOUNT -> b.callState.setText(R.string.state_dialing)
            Call.STATE_ACTIVE -> {
                b.callState.setText(R.string.state_active)
                handler.removeCallbacks(ticker)
                handler.post(ticker)
            }
            Call.STATE_HOLDING -> b.callState.setText(R.string.state_holding)
            Call.STATE_DISCONNECTED -> {
                b.callState.setText(R.string.state_disconnected)
                b.duration.text = ""
                handler.postDelayed({ finish() }, 800)
            }
        }

        val count = ActiveCallManager.size
        b.callCountBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        b.callCountBadge.text = count.toString()

        val number = details?.handle?.schemeSpecificPart ?: ""
        val display = details?.callerDisplayName?.takeIf {
            it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
        }
        val name = display
            ?: if (number.isBlank()) getString(R.string.unknown_number) else Formats.number(number)
        b.callerName.text = name
        Avatars.bind(b.avatarBig, name)

        if (!nameResolved && display == null && number.isNotBlank()) {
            nameResolved = true
            Contacts.lookupNameAsync(this, number) { resolved ->
                val stillSame = call?.details?.handle?.schemeSpecificPart == number
                if (resolved != null && stillSame) {
                    b.callerName.text = resolved
                    Avatars.bind(b.avatarBig, resolved)
                }
            }
        }

        if (c.state == Call.STATE_ACTIVE) {
            updateDuration()
        } else if (c.state != Call.STATE_DISCONNECTED) {
            b.duration.text = ""
        }

        val svc = InCallServiceImpl.instance
        val active = activeColor()
        val idle = idleColor()
        val muted = svc?.callAudioState?.isMuted == true
        b.muteIcon.setColorFilter(if (muted) active else idle)
        val speakerOn = svc?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER
        b.speakerIcon.setColorFilter(if (speakerOn) active else idle)
        val held = c.state == Call.STATE_HOLDING
        b.holdIcon.setColorFilter(if (held) active else idle)
    }

    private fun updateDuration() {
        val c = call ?: return
        val connectedAt = c.details?.connectTimeMillis ?: 0
        if (connectedAt <= 0 || c.state != Call.STATE_ACTIVE) {
            b.duration.text = ""
            return
        }
        val secs = ((System.currentTimeMillis() - connectedAt) / 1000).coerceAtLeast(0)
        b.duration.text = Formats.duration(secs)
    }

    private fun toggleMute() {
        val svc = InCallServiceImpl.instance ?: return
        val now = svc.callAudioState?.isMuted ?: false
        svc.setMuted(!now)
        render()
    }

    private fun toggleSpeaker() {
        val svc = InCallServiceImpl.instance ?: return
        val target = if (svc.callAudioState?.route == CallAudioState.ROUTE_SPEAKER)
            CallAudioState.ROUTE_EARPIECE
        else
            CallAudioState.ROUTE_SPEAKER
        svc.setAudioRoute(target)
        render()
    }

    private fun toggleHold() {
        val c = call ?: return
        try {
            if (c.state == Call.STATE_ACTIVE) c.hold() else if (c.state == Call.STATE_HOLDING) c.unhold()
        } catch (e: Exception) { }
        render()
    }

    private fun toggleKeypad() {
        val show = b.dtmfContainer.visibility != View.VISIBLE
        b.dtmfContainer.visibility = if (show) View.VISIBLE else View.GONE
        b.keypadIcon.setColorFilter(if (show) activeColor() else idleColor())
    }

    private fun buildDtmf() {
        val keys = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')
        keys.toList().chunked(3).forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { k ->
                val tv = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 22f
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_key)
                    text = k.toString()
                    layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        setMargins(dp(6), dp(5), dp(6), dp(5))
                    }
                    setOnTouchListener { v, ev ->
                        val c = call
                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                c?.playDtmfTone(k)
                                v.isPressed = true
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                c?.stopDtmfTone()
                                v.performClick()
                                true
                            }
                            else -> false
                        }
                    }
                }
                row.addView(tv)
            }
            b.dtmfContainer.addView(row)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun activeColor() = ContextCompat.getColor(this, R.color.green)

    private fun idleColor() = ContextCompat.getColor(this, R.color.text_secondary)
}
