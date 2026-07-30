package com.example.intruderselfie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class CameraService : Service() {

    // ------------------------------------------------------------------
    // 🔑 REPLACE THESE WITH YOUR TELEGRAM CREDENTIALS
    // ------------------------------------------------------------------
    private val BOT_TOKEN = "8897914052:AAFuBHgNCbsYSluDwTi3If8Bz03OrOARIaE"      // e.g., "123456:ABC-DEF1234ghIkl"
    private val CHAT_ID = "5081465974"          // e.g., "123456789"

    // ------------------------------------------------------------------
    // 📡 TELEGRAM API URL
    // ------------------------------------------------------------------
    private val TELEGRAM_URL = "https://api.telegram.org/bot$BOT_TOKEN/sendPhoto"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                val file = File(externalCacheDir, "intruder_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            // Photo captured! Send it to Telegram.
                            sendPhotoToTelegram(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            android.util.Log.e("IntruderSelfie", "Camera error: ${exception.message}")
                            stopSelf()
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("IntruderSelfie", "Error: ${e.message}")
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------------------------------------------------------------
    // 📤 SEND PHOTO TO TELEGRAM BOT
    // ------------------------------------------------------------------
    private fun sendPhotoToTelegram(file: File) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                val url = URL(TELEGRAM_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.doInput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                // Write the multipart body
                DataOutputStream(connection.outputStream).use { outputStream ->

                    // ----- 1. Chat ID field
                    outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd)
                    outputStream.writeBytes(lineEnd)
                    outputStream.writeBytes(CHAT_ID + lineEnd)

                    // ----- 2. Photo file field
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

                    // ----- 3. End boundary
                    outputStream.writeBytes(lineEnd)
                    outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                    outputStream.flush()
                }

                // Check response from Telegram
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    android.util.Log.d("IntruderSelfie", "✅ Photo sent to Telegram successfully!")
                } else {
                    android.util.Log.e("IntruderSelfie", "❌ Telegram upload failed. Code: $responseCode")
                }

            } catch (e: Exception) {
                android.util.Log.e("IntruderSelfie", "❌ Error sending to Telegram: ${e.message}")
            } finally {
                connection?.disconnect()
                stopSelf()
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
