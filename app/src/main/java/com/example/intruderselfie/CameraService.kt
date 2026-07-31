package com.example.intruderselfie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class CameraService : Service(), LifecycleOwner {

    // 🔑 REPLACE WITH YOUR TELEGRAM CREDENTIALS
    private val BOT_TOKEN = "8886927716:AAEFjHPz0YuTBMxPtnhY-r-r7s1JoGw_lZQ"   // ← Replace with your full token
    private val CHAT_ID = "5081465974"                  // ← Replace with your numeric chat ID

    private val TELEGRAM_URL = "https://api.telegram.org/bot$BOT_TOKEN/sendPhoto"

    private val lifecycleRegistry = LifecycleRegistry(this)

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "🔴 Intruder detected! Taking photo...", Toast.LENGTH_SHORT).show()
        startForegroundServiceNotification()
        takeSilentPhoto()
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "intruder_channel"
        val channel = NotificationChannel(channelId, "Security Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Security System Active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
    }

    private fun takeSilentPhoto() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

                val file = File(externalCacheDir, "intruder_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Toast.makeText(this@CameraService, "📸 Photo captured! Sending to Telegram...", Toast.LENGTH_SHORT).show()
                            sendPhotoToTelegram(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Toast.makeText(this@CameraService, "❌ Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
                            android.util.Log.e("IntruderSelfie", "Camera error: ${exception.message}")
                            stopSelf()
                        }
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("IntruderSelfie", "Error: ${e.message}")
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------------------------------------------------------------
    // 📤 SEND PHOTO TO TELEGRAM BOT (FULLY UPDATED)
    // ------------------------------------------------------------------
    private fun sendPhotoToTelegram(file: File) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                android.util.Log.d("IntruderSelfie", "📤 Starting Telegram upload...")
                android.util.Log.d("IntruderSelfie", "📁 File size: ${file.length()} bytes")
                android.util.Log.d("IntruderSelfie", "🔑 Using bot token: ${BOT_TOKEN.take(10)}...")
                android.util.Log.d("IntruderSelfie", "👤 Chat ID: $CHAT_ID")

                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                val url = URL(TELEGRAM_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                // Write the multipart body
                DataOutputStream(connection.outputStream).use { outputStream ->
                    // Chat ID
                    outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd)
                    outputStream.writeBytes(lineEnd)
                    outputStream.writeBytes(CHAT_ID + lineEnd)

                    // Photo file
                    outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                    outputStream.writeBytes(
                        "Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"" + lineEnd
                    )
                    outputStream.writeBytes("Content-Type: image/jpeg" + lineEnd)
                    outputStream.writeBytes(lineEnd)

                    // Write the image bytes
                    FileInputStream(file).use { fileInputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }

                    // End boundary
                    outputStream.writeBytes(lineEnd)
                    outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                    outputStream.flush()
                }

                // Get the response code
                val responseCode = connection.responseCode
                android.util.Log.d("IntruderSelfie", "📡 Response code: $responseCode")

                // Read the response body
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                        reader.readText()
                    }
                }

                android.util.Log.d("IntruderSelfie", "📄 Response body: $responseBody")

                // Show result on the phone
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    android.util.Log.d("IntruderSelfie", "✅ Photo sent to Telegram successfully!")
                    runOnUiThread {
                        Toast.makeText(this, "✅ Photo sent to Telegram!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.util.Log.e("IntruderSelfie", "❌ Telegram upload failed. Code: $responseCode")
                    android.util.Log.e("IntruderSelfie", "❌ Response: $responseBody")
                    runOnUiThread {
                        Toast.makeText(this, "❌ Upload failed. Code: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("IntruderSelfie", "❌ Error sending to Telegram: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.disconnect()
                // Clean up: unbind camera and stop service
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(this).get()
                    cameraProvider.unbindAll()
                } catch (_: Exception) { }
                stopSelf()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    // Helper to run code on the UI thread from a background thread
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
}
