package com.lightdialer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lightdialer.databinding.ActivitySettingsBinding
import com.lightdialer.util.DefaultDialer

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.backButton.setOnClickListener { finish() }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        b.darkSwitch.isChecked = prefs.getBoolean("dark_mode", false)
        b.darkSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_mode", checked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        b.setDefaultRow.setOnClickListener {
            val i = DefaultDialer.requestIntent(this)
            if (i != null) {
                try { startActivity(i) } catch (e: Exception) { }
            }
        }

        b.versionText.text = BuildConfig.VERSION_NAME
    }

    override fun onResume() {
        super.onResume()
        b.defaultState.setText(
            if (DefaultDialer.isDefault(this)) R.string.is_default else R.string.set_default
        )
        b.defaultState.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (DefaultDialer.isDefault(this)) R.color.accent else R.color.text_secondary
            )
        )
    }
}
