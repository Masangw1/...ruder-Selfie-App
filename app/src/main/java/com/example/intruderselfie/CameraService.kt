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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class CameraService : Service(), LifecycleOwner {

    // 🔑 Replace with your Telegram credentials
    private val BOT_TOKEN = "8897914052:AAFuBHgNCbsYSluDwTi3If8Bz03OrOARIaE"
    private val CHAT_ID = "5081465974"

    private val TELEGRAM_URL = "https://api.telegram.org/bot$BOT_TOKEN/sendPhoto"

    // LifecycleRegistry for CameraX binding
    private val lifecycleRegistry = LifecycleRegistry(this)

    override fun onCreate() {
        super.onCreate()
        // Set lifecycle state to RESUMED so CameraX can bind
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

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
                // Unbind anything previously bound
                cameraProvider.unbindAll()

                // ✅ Bind to this service's lifecycle (we are a LifecycleOwner)
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

                val file = File(externalCacheDir, "intruder_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            // Photo captured – send it to Telegram
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

                    FileInputStream(file).use { fileInputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }

                    outputStream.writeBytes(lineEnd)
                    outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                    outputStream.flush()
                }

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
        // Clean up lifecycle
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // --- LifecycleOwner implementation ---
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder? = null
}
