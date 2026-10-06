package com.mindtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.media.session.MediaSession
import android.media.session.PlaybackState

class MeditationTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSession
    private var foreground = false

    private val tick = object : Runnable {
        override fun run() {
            advanceIfNeeded()
            val prefs = preferences()
            if (prefs.getString(KEY_STATUS, STATUS_IDLE) == STATUS_RUNNING) {
                showNotification()
                val remaining = (prefs.getLong(KEY_DEADLINE, 0L) -
                    SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                handler.postDelayed(this, minOf(remaining.coerceAtLeast(1L), NOTIFICATION_INTERVAL_MS))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        mediaSession = MediaSession(this, "MindtimeSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumeSession()
                override fun onPause() = pauseSession()
                override fun onStop() = stopSession(false)
            })
            setSessionActivity(activityPendingIntent())
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(
                intent.getIntExtra(EXTRA_MAIN_MINUTES, 60),
                intent.getIntExtra(EXTRA_METTA_MINUTES, 5)
            )
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_TOGGLE -> toggleSession()
            ACTION_STOP -> stopSession(false)
            null -> restoreSession()
        }
        if (preferences().getString(KEY_STATUS, STATUS_IDLE) == STATUS_RUNNING ||
            preferences().getString(KEY_STATUS, STATUS_IDLE) == STATUS_PAUSED
        ) {
            if (!foreground) {
                startForeground(NOTIFICATION_ID, buildNotification())
                foreground = true
            }
            scheduleTick()
        } else if (!foreground) {
            stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        mediaSession.release()
        super.onDestroy()
    }

    private fun startSession(mainMinutes: Int, mettaMinutes: Int) {
        val mainMs = mainMinutes.coerceIn(1, 180) * MINUTE_MS
        val mettaMs = mettaMinutes.coerceIn(1, 60) * MINUTE_MS
        val now = SystemClock.elapsedRealtime()
        preferences().edit()
            .putString(KEY_STATUS, STATUS_RUNNING)
            .putString(KEY_PHASE, PHASE_MAIN)
            .putLong(KEY_MAIN_DURATION_MS, mainMs)
            .putLong(KEY_METTA_DURATION_MS, mettaMs)
            .putLong(KEY_DEADLINE, now + mainMs)
            .putLong(KEY_REMAINING_MS, mainMs)
            .apply()
        handler.removeCallbacks(tick)
    }

    private fun pauseSession() {
        val prefs = preferences()
        if (prefs.getString(KEY_STATUS, STATUS_IDLE) != STATUS_RUNNING) return
        advanceIfNeeded()
        if (prefs.getString(KEY_STATUS, STATUS_IDLE) != STATUS_RUNNING) return
        val remaining = (prefs.getLong(KEY_DEADLINE, 0L) - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)
        prefs.edit()
            .putString(KEY_STATUS, STATUS_PAUSED)
            .putLong(KEY_REMAINING_MS, remaining)
            .apply()
        handler.removeCallbacks(tick)
        showNotification()
    }

    private fun resumeSession() {
        val prefs = preferences()
        if (prefs.getString(KEY_STATUS, STATUS_IDLE) != STATUS_PAUSED) return
        val remaining = prefs.getLong(KEY_REMAINING_MS, 0L)
        val now = SystemClock.elapsedRealtime()
        prefs.edit()
            .putString(KEY_STATUS, STATUS_RUNNING)
            .putLong(KEY_DEADLINE, now + remaining)
            .apply()
        showNotification()
        scheduleTick()
    }

    private fun toggleSession() {
        if (preferences().getString(KEY_STATUS, STATUS_IDLE) == STATUS_RUNNING) {
            pauseSession()
        } else {
            resumeSession()
        }
    }

    private fun stopSession(completed: Boolean) {
        handler.removeCallbacks(tick)
        preferences().edit()
            .putString(KEY_STATUS, if (completed) STATUS_COMPLETE else STATUS_IDLE)
            .apply()
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun restoreSession() {
        val prefs = preferences()
        val status = prefs.getString(KEY_STATUS, STATUS_IDLE)
        if (status == STATUS_RUNNING) {
            val now = SystemClock.elapsedRealtime()
            if (prefs.getLong(KEY_DEADLINE, 0L) <= now) advanceIfNeeded()
        } else if (status != STATUS_PAUSED) {
            stopSelf()
        }
    }

    private fun advanceIfNeeded() {
        val prefs = preferences()
        if (prefs.getString(KEY_STATUS, STATUS_IDLE) != STATUS_RUNNING) return
        val now = SystemClock.elapsedRealtime()
        val deadline = prefs.getLong(KEY_DEADLINE, 0L)
        if (deadline > now) return

        if (prefs.getString(KEY_PHASE, PHASE_MAIN) == PHASE_MAIN) {
            val mettaDeadline = deadline + prefs.getLong(KEY_METTA_DURATION_MS, 5 * MINUTE_MS)
            prefs.edit()
                .putString(KEY_PHASE, PHASE_METTA)
                .putLong(KEY_DEADLINE, mettaDeadline)
                .putLong(KEY_REMAINING_MS, (mettaDeadline - now).coerceAtLeast(0L))
                .apply()
            if (mettaDeadline <= now) {
                stopSession(true)
            } else {
                showNotification()
            }
        } else {
            stopSession(true)
        }
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (preferences().getString(KEY_STATUS, STATUS_IDLE) == STATUS_RUNNING) {
            val remaining = (preferences().getLong(KEY_DEADLINE, 0L) -
                SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            handler.postDelayed(tick, minOf(remaining, NOTIFICATION_INTERVAL_MS))
        }
    }

    private fun showNotification() {
        if (!foreground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foreground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val prefs = preferences()
        val running = prefs.getString(KEY_STATUS, STATUS_IDLE) == STATUS_RUNNING
        val phase = if (prefs.getString(KEY_PHASE, PHASE_MAIN) == PHASE_MAIN) {
            "Main meditation"
        } else {
            "Metta · loving-kindness"
        }
        val remaining = if (running) {
            (prefs.getLong(KEY_DEADLINE, 0L) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            prefs.getLong(KEY_REMAINING_MS, 0L)
        }
        val title = "$phase · ${formatTime(remaining)}"
        mediaSession.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Mindtime")
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP)
                .setState(
                    if (running) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    0L,
                    0f
                )
                .build()
        )
        val toggleAction = if (running) ACTION_PAUSE else ACTION_RESUME
        val toggleIcon = if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (running) "Pause" else "Resume"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(if (running) "Metta will begin automatically" else "Session paused")
            .setContentIntent(activityPendingIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(toggleIcon, toggleLabel, servicePendingIntent(toggleAction, 1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", servicePendingIntent(ACTION_STOP, 2))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun activityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun servicePendingIntent(action: String, requestCode: Int) = PendingIntent.getForegroundService(
        this,
        requestCode,
        Intent(this, MeditationTimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meditation session", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Timer and lock-screen controls for an active meditation session" }
            )
        }
    }

    private fun preferences() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds + 999L) / 1000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }

    companion object {
        const val PREFS = "mindtime_session"
        const val KEY_STATUS = "session_status"
        const val KEY_PHASE = "session_phase"
        const val KEY_DEADLINE = "deadline_elapsed"
        const val KEY_REMAINING_MS = "remaining_ms"
        const val KEY_MAIN_DURATION_MS = "main_duration_ms"
        const val KEY_METTA_DURATION_MS = "metta_duration_ms"
        const val STATUS_IDLE = "idle"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETE = "complete"
        const val PHASE_MAIN = "main"
        const val PHASE_METTA = "metta"
        const val ACTION_START = "com.mindtime.action.START"
        const val ACTION_PAUSE = "com.mindtime.action.PAUSE"
        const val ACTION_RESUME = "com.mindtime.action.RESUME"
        const val ACTION_TOGGLE = "com.mindtime.action.TOGGLE"
        const val ACTION_STOP = "com.mindtime.action.STOP"
        const val EXTRA_MAIN_MINUTES = "main_minutes"
        const val EXTRA_METTA_MINUTES = "metta_minutes"
        private const val CHANNEL_ID = "meditation_session"
        private const val NOTIFICATION_ID = 42
        private const val MINUTE_MS = 60_000L
        private const val NOTIFICATION_INTERVAL_MS = 30_000L
    }
}
