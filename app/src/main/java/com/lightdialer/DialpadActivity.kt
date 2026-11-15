package com.lightdialer

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.lightdialer.data.Contacts
import com.lightdialer.data.Suggestion
import com.lightdialer.databinding.ActivityDialpadBinding
import com.lightdialer.util.DialManager
import com.lightdialer.util.Formats
import com.lightdialer.util.Perms

class DialpadActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "number"
    }

    private lateinit var b: ActivityDialpadBinding
    private val digits = StringBuilder()
    private val suggestions = ArrayList<Suggestion>()
    private lateinit var sugAdapter: SuggestionAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val suggestRunnable = Runnable { querySuggestions() }

    private val keys = arrayOf(
        '1' to "", '2' to "ABC", '3' to "DEF",
        '4' to "GHI", '5' to "JKL", '6' to "MNO",
        '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
        '*' to "", '0' to "+", '#' to ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDialpadBinding.inflate(layoutInflater)
        setContentView(b.root)

        sugAdapter = SuggestionAdapter { suggestion ->
            digits.clear()
            digits.append(suggestion.number)
            update()
        }
        b.suggestions.layoutManager = LinearLayoutManager(this)
        b.suggestions.adapter = sugAdapter

        buildPad()

        b.backspace.setOnClickListener {
            if (digits.isNotEmpty()) {
                digits.deleteCharAt(digits.length - 1)
                update()
            }
        }
        b.backspace.setOnLongClickListener {
            digits.clear()
            update()
            true
        }
        b.callButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            DialManager.placeCall(this, digits.toString())
        }

        val prefill = intent?.getStringExtra(EXTRA_NUMBER)
            ?: intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        if (!prefill.isNullOrBlank()) {
            digits.append(prefill)
            update()
        } else {
            update()
        }
    }

    private fun buildPad() {
        keys.toList().chunked(3).forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { (digit, letters) ->
                val tv = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 24f
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_key)
                    text = keyLabel(digit, letters)
                    layoutParams = LinearLayout.LayoutParams(0, dp(74), 1f).apply {
                        setMargins(dp(9), dp(6), dp(9), dp(6))
                    }
                    setOnClickListener { v ->
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDigit(digit)
                    }
                    if (digit == '0') {
                        setOnLongClickListener { v ->
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDigit('+')
                            true
                        }
                    }
                }
                row.addView(tv)
            }
            b.pad.addView(row)
        }
    }

    private fun keyLabel(digit: Char, letters: String): CharSequence {
        if (letters.isEmpty()) return digit.toString()
        val s = SpannableString("$digit\n$letters")
        s.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.38f), 2, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun onDigit(c: Char) {
        if (digits.length >= 20) return
        digits.append(c)
        update()
    }

    private fun update() {
        b.dialText.text = Formats.number(digits.toString())
        handler.removeCallbacks(suggestRunnable)
        if (digits.length >= 2 && Perms.granted(this, android.Manifest.permission.READ_CONTACTS)) {
            handler.postDelayed(suggestRunnable, 180)
        } else {
            showSuggestions(emptyList())
        }
    }

    private fun querySuggestions() {
        Contacts.suggestionsAsync(this, digits.toString()) { list ->
            showSuggestions(list)
        }
    }

    private fun showSuggestions(list: List<Suggestion>) {
        suggestions.clear()
        suggestions.addAll(list)
        sugAdapter.submit(list)
        b.suggestions.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
