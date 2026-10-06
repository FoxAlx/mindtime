package com.mindtime

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mainDuration: TextView
    private lateinit var mettaDuration: TextView
    private lateinit var phaseLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var sessionButton: Button
    private lateinit var stopButton: Button
    private lateinit var mainSeek: SeekBar
    private lateinit var mettaSeek: SeekBar
    private var syncingSliders = false

    private val refresh = object : Runnable {
        override fun run() {
            renderSession()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 17, 22)
        window.navigationBarColor = Color.rgb(17, 17, 22)
        requestNotificationPermission()
        buildScreen()
        loadDurations()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(20))
            background = solid(Color.rgb(17, 17, 22))
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(text("MINDFUL MOMENT", 12, Color.rgb(184, 164, 255), true).apply {
            letterSpacing = 0.16f
        }, matchWrap())
        root.addView(text("Mindtime", 34, Color.WHITE, true).apply {
            setPadding(0, dp(7), 0, dp(3))
        }, matchWrap())
        root.addView(text("A little space to return to yourself.", 15, MUTED), matchWrap())

        val timerCard = card()
        phaseLabel = text("READY WHEN YOU ARE", 12, MUTED, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        timeLabel = text("65:00", 56, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(13), 0, dp(14))
        }
        timerCard.addView(phaseLabel, matchWrap())
        timerCard.addView(timeLabel, matchWrap())
        timerCard.addView(text("Main meditation  ·  Metta", 14, MUTED).apply {
            gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(timerCard, matchWrap().apply { topMargin = dp(25) })

        root.addView(sectionTitle("YOUR SESSION"), matchWrap().apply {
            topMargin = dp(24)
            bottomMargin = dp(10)
        })
        mainSeek = SeekBar(this).apply { max = 179 }
        mainDuration = text("60 min", 14, Color.rgb(190, 173, 255), true).apply {
            gravity = Gravity.END
        }
        addDurationControl(root, mainDuration, mainSeek, true)
        mettaSeek = SeekBar(this).apply { max = 59 }
        mettaDuration = text("5 min", 14, Color.rgb(190, 173, 255), true).apply {
            gravity = Gravity.END
        }
        addDurationControl(root, mettaDuration, mettaSeek, false)

        sessionButton = Button(this).apply {
            text = "Begin session"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.rgb(24, 21, 35))
            background = rounded(Color.rgb(190, 173, 255), 20)
            setOnClickListener { onSessionButton() }
        }
        root.addView(sessionButton, matchWrap().apply {
            topMargin = dp(15)
            height = dp(58)
        })
        stopButton = Button(this).apply {
            text = "End session"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(220, 211, 255))
            background = rounded(Color.rgb(43, 40, 53), 18)
            visibility = View.GONE
            setOnClickListener { sendServiceAction(MeditationTimerService.ACTION_STOP) }
        }
        root.addView(stopButton, matchWrap().apply {
            topMargin = dp(8)
            height = dp(48)
        })

        root.addView(text(
            "Keep your practice uninterrupted",
            16,
            Color.WHITE,
            true
        ).apply { setPadding(0, dp(21), 0, dp(4)) }, matchWrap())
        root.addView(text(
            "For reliable background timing on Huawei and other phones, " +
                "allow background activity and auto-launch in system settings.",
            13,
            MUTED
        ), matchWrap())
        val batteryButton = Button(this).apply {
            text = "Open battery settings"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(190, 173, 255))
            background = rounded(Color.rgb(32, 31, 40), 16)
            setOnClickListener { openBatterySettings() }
        }
        root.addView(batteryButton, matchWrap().apply {
            topMargin = dp(8)
            height = dp(48)
        })
    }

    private fun addDurationControl(
        parent: LinearLayout,
        label: TextView,
        seek: SeekBar,
        isMain: Boolean
    ) {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(9), dp(15), dp(5))
            background = rounded(Color.rgb(27, 27, 34), 18)
        }
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                text(
                    if (isMain) "Main meditation" else "Metta · loving-kindness",
                    15,
                    Color.WHITE,
                    true
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(label, LinearLayout.LayoutParams(
                dp(64),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        group.addView(heading, matchWrap())
        group.addView(seek, matchWrap())
        parent.addView(group, matchWrap().apply {
            bottomMargin = dp(8)
        })
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (syncingSliders) return
                val minutes = progress + 1
                label.text = "$minutes min"
                saveDurations()
                renderSession()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }

    private fun onSessionButton() {
        val prefs = getSharedPreferences(MeditationTimerService.PREFS, Context.MODE_PRIVATE)
        when (prefs.getString(MeditationTimerService.KEY_STATUS, MeditationTimerService.STATUS_IDLE)) {
            MeditationTimerService.STATUS_RUNNING ->
                sendServiceAction(MeditationTimerService.ACTION_PAUSE)
            MeditationTimerService.STATUS_PAUSED ->
                sendServiceAction(MeditationTimerService.ACTION_RESUME)
            else -> {
                val intent = Intent(this, MeditationTimerService::class.java).apply {
                    action = MeditationTimerService.ACTION_START
                    putExtra(MeditationTimerService.EXTRA_MAIN_MINUTES, mainSeek.progress + 1)
                    putExtra(MeditationTimerService.EXTRA_METTA_MINUTES, mettaSeek.progress + 1)
                }
                startForegroundService(intent)
            }
        }
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, MeditationTimerService::class.java).setAction(action))
    }

    private fun renderSession() {
        if (!::timeLabel.isInitialized) return
        val prefs = getSharedPreferences(MeditationTimerService.PREFS, Context.MODE_PRIVATE)
        val status = prefs.getString(MeditationTimerService.KEY_STATUS, MeditationTimerService.STATUS_IDLE)
        val isMain = prefs.getString(MeditationTimerService.KEY_PHASE, MeditationTimerService.PHASE_MAIN) ==
            MeditationTimerService.PHASE_MAIN
        val total = if (status == MeditationTimerService.STATUS_IDLE ||
            status == MeditationTimerService.STATUS_COMPLETE
        ) {
            (if (::mainSeek.isInitialized) mainSeek.progress + 1 else 60) * 60_000L +
                (if (::mettaSeek.isInitialized) mettaSeek.progress + 1 else 5) * 60_000L
        } else if (status == MeditationTimerService.STATUS_PAUSED) {
            prefs.getLong(MeditationTimerService.KEY_REMAINING_MS, 0L)
        } else {
            (prefs.getLong(MeditationTimerService.KEY_DEADLINE, 0L) -
                android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

        when (status) {
            MeditationTimerService.STATUS_RUNNING -> {
                phaseLabel.text = if (isMain) "MAIN MEDITATION" else "METTA · LOVING-KINDNESS"
                sessionButton.text = "Pause session"
                stopButton.visibility = View.VISIBLE
            }
            MeditationTimerService.STATUS_PAUSED -> {
                phaseLabel.text = "SESSION PAUSED"
                sessionButton.text = "Resume session"
                stopButton.visibility = View.VISIBLE
            }
            MeditationTimerService.STATUS_COMPLETE -> {
                phaseLabel.text = "SESSION COMPLETE · WELL DONE"
                sessionButton.text = "Begin again"
                stopButton.visibility = View.GONE
            }
            else -> {
                phaseLabel.text = "READY WHEN YOU ARE"
                sessionButton.text = "Begin session"
                stopButton.visibility = View.GONE
            }
        }
        timeLabel.text = formatTime(total)
        val controlsEnabled = status == MeditationTimerService.STATUS_IDLE ||
            status == MeditationTimerService.STATUS_COMPLETE
        mainSeek.isEnabled = controlsEnabled
        mettaSeek.isEnabled = controlsEnabled
    }

    private fun loadDurations() {
        val prefs = getSharedPreferences(MeditationTimerService.PREFS, Context.MODE_PRIVATE)
        syncingSliders = true
        mainSeek.progress = (prefs.getInt(KEY_MAIN_MINUTES, 60) - 1).coerceIn(0, 179)
        mettaSeek.progress = (prefs.getInt(KEY_METTA_MINUTES, 5) - 1).coerceIn(0, 59)
        syncingSliders = false
        mainDuration.text = "${mainSeek.progress + 1} min"
        mettaDuration.text = "${mettaSeek.progress + 1} min"
    }

    private fun saveDurations() {
        getSharedPreferences(MeditationTimerService.PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MAIN_MINUTES, mainSeek.progress + 1)
            .putInt(KEY_METTA_MINUTES, mettaSeek.progress + 1)
            .apply()
    }

    private fun openBatterySettings() {
        val powerManager = getSystemService(PowerManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !powerManager.isIgnoringBatteryOptimizations(packageName)
        ) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                Uri.parse("package:$packageName")
            )
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(23), dp(18), dp(21))
        background = rounded(Color.rgb(27, 26, 34), 26)
    }

    private fun sectionTitle(value: String) = text(value, 12, MUTED, true).apply {
        letterSpacing = 0.12f
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds + 999L) / 1000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun solid(color: Int) = GradientDrawable().apply {
        setColor(color)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    companion object {
        private const val KEY_MAIN_MINUTES = "configured_main_minutes"
        private const val KEY_METTA_MINUTES = "configured_metta_minutes"
        private val MUTED = Color.rgb(157, 155, 169)
    }
}
