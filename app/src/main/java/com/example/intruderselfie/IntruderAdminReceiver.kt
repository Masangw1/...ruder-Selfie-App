package com.example.intruderselfie

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log

class IntruderAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d("IntruderSelfie", "Failed unlock attempt detected!")

        // 👇 ADD THIS TOAST TO CONFIRM THE RECEIVER WORKS
        Toast.makeText(context, "🔴 Wrong password detected! Starting camera...", Toast.LENGTH_SHORT).show()

        // Start the foreground camera service
        val serviceIntent = Intent(context, CameraService::class.java)
        context.startForegroundService(serviceIntent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("IntruderSelfie", "Device Admin Enabled")
        // Optional: Show a toast when admin is activated
        Toast.makeText(context, "✅ IntruderSelfie Admin Activated!", Toast.LENGTH_SHORT).show()
    }
}
