package com.example.bypassyou.service

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val handleUri: Uri? = callDetails.handle
        val phoneNumber: String? = handleUri?.schemeSpecificPart

        Log.d(TAG, "CallScreeningService onScreenCall. Phone: $phoneNumber")

        if (!phoneNumber.isNullOrEmpty()) {
            val audioManager = AudioBypassManager.getInstance(applicationContext)
            audioManager.handleIncomingCall(phoneNumber)
        }

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }

    companion object {
        private const val TAG = "CallScreeningServiceImpl"
    }
}
