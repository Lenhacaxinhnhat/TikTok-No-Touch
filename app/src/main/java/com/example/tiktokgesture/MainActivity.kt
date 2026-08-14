package com.example.tiktokgesture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tiktokgesture.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serviceRunning = false

    private val requestCameraPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "Đã cấp quyền camera", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Cần quyền camera để app hoạt động", Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }

    private val requestNotificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Chưa cấp quyền thông báo — service vẫn chạy được nhưng bạn sẽ không thấy thông báo nền",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /** Trả về true nếu đã sẵn sàng (đã có quyền hoặc không cần xin trên phiên bản Android này) */
    private fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                Toast.makeText(this, "Camera đã được cấp quyền rồi", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Tìm 'TikTok Gesture' trong danh sách và bật lên",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnToggleService.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Vui lòng cấp quyền camera trước (bước 1)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            ensureNotificationPermission()

            val intent = Intent(this, GestureForegroundService::class.java)
            if (!serviceRunning) {
                try {
                    ContextCompat.startForegroundService(this, intent)
                    serviceRunning = true
                    Toast.makeText(this, "Đã bật nhận diện cử chỉ. Mở TikTok và thử vẫy tay!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Lỗi khi bật service: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                stopService(intent)
                serviceRunning = false
                Toast.makeText(this, "Đã tắt nhận diện cử chỉ", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }

        updateStatus()
        ensureNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val accessibilityOn = GestureAccessibilityService.instance != null

        binding.tvStatus.text = buildString {
            append("Camera: ${if (cameraGranted) "✓ đã cấp" else "✗ chưa cấp"}\n")
            append("Accessibility: ${if (accessibilityOn) "✓ đã bật" else "✗ chưa bật"}\n")
            append("Nhận diện cử chỉ: ${if (serviceRunning) "✓ đang chạy" else "✗ đang tắt"}")
        }
    }
}
