package com.example.bypassyou.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import android.util.Log
import com.example.bypassyou.data.repository.SettingsRepository
import com.example.bypassyou.data.repository.VipRepository
import kotlin.math.pow

class AudioBypassManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val vipRepository = VipRepository(appContext)
    private val settingsRepository = SettingsRepository(appContext)

    private var isBypassingNow = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activePreviewMp: MediaPlayer? = null

    private fun calculateAcousticVolume(percent: Int): Float {
        val p = (percent.coerceIn(5, 100) / 100.0)
        val factor = ((10.0.pow(p) - 1.0) / 9.0).toFloat()
        return factor.coerceIn(0.01f, 1.0f)
    }

    @Synchronized
    fun handleIncomingCall(incomingNumber: String?) {
        Log.d(TAG, "handleIncomingCall called for number: '$incomingNumber'")

        if (!settingsRepository.isBypassEnabled) {
            return
        }

        if (incomingNumber.isNullOrEmpty()) {
            return
        }

        val isVip = vipRepository.isVipNumber(incomingNumber)
        if (!isVip) {
            return
        }

        performSoundBypass()
    }

    private fun performSoundBypass() {
        if (isBypassingNow) {
            return
        }

        try {
            isBypassingNow = true
            acquireWakeLock()
            playBackupRingtone()
        } catch (e: Exception) {
            Log.e(TAG, "Error in performSoundBypass", e)
        }
    }

    @Synchronized
    fun restoreOriginalAudioState() {
        if (!isBypassingNow) {
            return
        }

        try {
            stopBackupRingtone()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone", e)
        } finally {
            isBypassingNow = false
            releaseWakeLock()
        }
    }

    private fun playBackupRingtone() {
        try {
            stopBackupRingtone()
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val acousticVolume = calculateAcousticVolume(settingsRepository.targetVolumePercent)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(appContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(acousticVolume, acousticVolume)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play backup ringtone", e)
        }
    }

    private fun stopBackupRingtone() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping backup MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    @Synchronized
    fun stopPreviewSound() {
        try {
            activePreviewMp?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview sound", e)
        } finally {
            activePreviewMp = null
        }
    }

    @Synchronized
    fun playPreviewSound() {
        try {
            stopPreviewSound()
            stopBackupRingtone()

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val acousticVolume = calculateAcousticVolume(settingsRepository.targetVolumePercent)

            val previewMp = MediaPlayer().apply {
                setDataSource(appContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(acousticVolume, acousticVolume)
                prepare()
                start()
            }
            activePreviewMp = previewMp

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                synchronized(this@AudioBypassManager) {
                    if (activePreviewMp == previewMp) {
                        stopPreviewSound()
                    }
                }
            }, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound preview", e)
        }
    }

    @Synchronized
    fun updatePreviewVolume(percent: Int) {
        val acousticVolume = calculateAcousticVolume(percent)
        activePreviewMp?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.setVolume(acousticVolume, acousticVolume)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BypassYou:RingingWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(60_000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed releasing WakeLock", e)
        }
    }

    companion object {
        private const val TAG = "AudioBypassManager"

        @Volatile
        private var instance: AudioBypassManager? = null

        fun getInstance(context: Context): AudioBypassManager {
            return instance ?: synchronized(this) {
                instance ?: AudioBypassManager(context).also { instance = it }
            }
        }
    }
}
