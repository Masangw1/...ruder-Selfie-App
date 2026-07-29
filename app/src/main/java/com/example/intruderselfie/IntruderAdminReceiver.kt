package com.example.intruderselfie

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class IntruderAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d("IntruderSelfie", "Failed unlock attempt detected!")

        val serviceIntent = Intent(context, CameraService::class.java)
        context.startForegroundService(serviceIntent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("IntruderSelfie", "Device Admin Enabled")
    }
}
