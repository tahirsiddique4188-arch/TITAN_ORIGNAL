package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.titan.ide.os.TitanCallAssistantEngine

/**
 * TitanIncomingCallReceiver v3.8 — Intercepts RINGING / OFFHOOK / IDLE phone states
 * so Titan's Equal-Style AI Call Assistant works reliably on Samsung & all Android phones!
 */
class TitanIncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()

        Log.d("TitanCallReceiver", "Phone state=$state number=$number")
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                TitanCallAssistantEngine.onIncomingCallRinging(context.applicationContext, number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                TitanCallAssistantEngine.onCallEnded(context.applicationContext)
            }
        }
    }
}
