package com.example.intruderselfie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT == intent.action) {
            Toast.makeText(context, "🔴 Phone unlocked! Taking photo...", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(context, CameraService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
