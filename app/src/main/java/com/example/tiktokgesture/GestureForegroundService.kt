package com.example.tiktokgesture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground Service giữ camera trước hoạt động liên tục ở chế độ nền,
 * kể cả khi người dùng đang dùng app khác (TikTok). Dùng LifecycleService
 * vì CameraX cần một LifecycleOwner để bind use case.
 */
class GestureForegroundService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startForeground(NOTIFICATION_ID, buildNotification())
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()

            val analyzer = MotionAnalyzer { gesture ->
                val service = GestureAccessibilityService.instance
                if (service == null) {
                    // Người dùng chưa bật Accessibility Service trong Settings
                    return@MotionAnalyzer
                }
                when (gesture) {
                    MotionAnalyzer.Gesture.SWIPE_UP -> service.swipeNext()
                    MotionAnalyzer.Gesture.SWIPE_DOWN -> service.swipePrevious()
                }
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, selector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, cameraExecutor)
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "gesture_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nhận diện cử chỉ tay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Đang nhận diện cử chỉ tay")
            .setContentText("Vẫy tay trước camera để chuyển video TikTok")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val NOTIFICATION_ID = 42
    }
}
