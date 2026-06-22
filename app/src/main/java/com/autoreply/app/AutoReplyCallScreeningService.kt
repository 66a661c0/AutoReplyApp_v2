package com.autoreply.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.json.JSONArray

@RequiresApi(Build.VERSION_CODES.N)
class AutoReplyCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        Log.d(TAG, "onScreenCall - number: $number, direction: ${callDetails.callDirection}")

        // 수신 전화만 처리
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else true

        if (!isIncoming || number.isNullOrEmpty()) {
            respondToCall(callDetails, buildAllowResponse())
            return
        }

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(MainActivity.KEY_ENABLED, false)
        Log.d(TAG, "Enabled: $enabled")

        if (!enabled) {
            respondToCall(callDetails, buildAllowResponse())
            return
        }

        val targets = loadTargets(prefs)
        val incomingNormalized = normalize(number)
        Log.d(TAG, "Incoming: $incomingNormalized, Targets count: ${targets.size}")

        val matched = targets.find {
            val targetNorm = normalize(it.number)
            targetNorm == incomingNormalized ||
                (incomingNormalized.length >= 8 && incomingNormalized.endsWith(targetNorm.takeLast(8))) ||
                (targetNorm.length >= 8 && targetNorm.endsWith(incomingNormalized.takeLast(8)))
        }

        if (matched != null) {
            Log.d(TAG, "MATCHED: ${matched.name} - rejecting & sending SMS")
            val message = prefs.getString(MainActivity.KEY_MESSAGE, MainActivity.DEFAULT_MESSAGE)
                ?: MainActivity.DEFAULT_MESSAGE
            sendSms(number, message)
            respondToCall(callDetails, buildRejectResponse())
        } else {
            Log.d(TAG, "Not in target list - allowing")
            respondToCall(callDetails, buildAllowResponse())
        }
    }

    private fun buildAllowResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
    }

    private fun buildRejectResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()
    }

    private fun sendSms(number: String, message: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = sms.divideMessage(message)
                if (parts.size > 1) {
                    sms.sendMultipartTextMessage(number, null, parts, null, null)
                } else {
                    sms.sendTextMessage(number, null, message, null, null)
                }
                Log.d(TAG, "SMS sent to $number")
            } else {
                Log.e(TAG, "SEND_SMS permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendSms failed", e)
        }
    }

    private fun loadTargets(prefs: android.content.SharedPreferences): List<TargetContact> {
        val json = prefs.getString(MainActivity.KEY_CONTACTS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<TargetContact>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TargetContact(obj.getString("name"), obj.getString("number")))
        }
        return list
    }

    private fun normalize(number: String): String {
        return number.replace("-", "").replace(" ", "").replace("+82", "0")
    }
}
