package com.example.bypassyou.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            Log.d(TAG, "PhoneCallReceiver state: $stateStr | number: $incomingNumber")

            val audioBypassManager = AudioBypassManager.getInstance(context)

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (!incomingNumber.isNullOrEmpty()) {
                        audioBypassManager.handleIncomingCall(incomingNumber)
                    }
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK,
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    audioBypassManager.restoreOriginalAudioState()
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhoneCallReceiver"
    }
}
