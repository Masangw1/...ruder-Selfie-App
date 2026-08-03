package com.example.intruderselfie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val testButton = findViewById<Button>(R.id.test_button)

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            statusText.text = "Requesting camera permission..."
        } else {
            statusText.text = "✅ Camera permission granted! Tap the button to test."
        }

        // Button click: manually start the camera service
        testButton.setOnClickListener {
            Toast.makeText(this, "🔴 Starting camera...", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(this, CameraService::class.java)
            startForegroundService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            val statusText = findViewById<TextView>(R.id.status_text)
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "✅ Camera permission granted! Tap the button to test."
            } else {
                statusText.text = "❌ Camera permission required. Please grant it in Settings."
            }
        }
    }
}
