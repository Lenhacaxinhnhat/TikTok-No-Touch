package com.example.tiktokgesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService: nhận lệnh từ GestureForegroundService và thực hiện
 * thao tác vuốt màn hình thật, giống hệt như ngón tay người dùng vuốt trên
 * app đang mở (TikTok).
 *
 * Người dùng PHẢI tự bật service này trong Settings > Trợ năng (Accessibility)
 * vì lý do bảo mật của Android, không thể bật bằng code.
 */
class GestureAccessibilityService : AccessibilityService() {

    private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // QUAN TRỌNG: nếu service kết nối trong lúc TikTok đã đang mở sẵn
        // (ví dụ người dùng bật/tắt lại service trong Settings, hoặc hệ
        // thống khởi động lại service), sẽ không có sự kiện
        // TYPE_WINDOW_STATE_CHANGED nào bắn ra nữa vì window không đổi.
        // Nếu không đọc trạng thái hiện tại ngay ở đây, foregroundPackage
        // sẽ là null mãi mãi và isTikTokForeground() luôn trả về false,
        // khiến vuốt tay không bao giờ có tác dụng cho tới khi người dùng
        // chuyển sang app khác rồi quay lại TikTok.
        rootInActiveWindow?.packageName?.let {
            foregroundPackage = it.toString()
            Log.d(TAG, "onServiceConnected: khởi tạo foregroundPackage = $foregroundPackage")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Theo dõi app nào đang hiển thị trước mặt người dùng, để chỉ vuốt
        // khi TikTok đang mở (tránh vuốt nhầm sang app khác).
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let {
                foregroundPackage = it.toString()
                Log.d(TAG, "onAccessibilityEvent: foregroundPackage = $foregroundPackage")
            }
        }
    }

    override fun onInterrupt() {}

    private fun isTikTokForeground(): Boolean {
        val pkg = foregroundPackage ?: return false
        return TIKTOK_PACKAGES.any { pkg == it }
    }

    /** Vuốt từ dưới lên -> chuyển sang video tiếp theo (giống thao tác thật trên TikTok) */
    fun swipeNext() {
        if (isTikTokForeground()) {
            performSwipe(up = true)
        } else {
            Log.d(TAG, "swipeNext() bị bỏ qua: TikTok không ở foreground (đang là $foregroundPackage)")
        }
    }

    /** Vuốt từ trên xuống -> quay lại video trước */
    fun swipePrevious() {
        if (isTikTokForeground()) {
            performSwipe(up = false)
        } else {
            Log.d(TAG, "swipePrevious() bị bỏ qua: TikTok không ở foreground (đang là $foregroundPackage)")
        }
    }

    /** Dùng để hiển thị trạng thái debug lên MainActivity, không ảnh hưởng logic. */
    fun debugStatus(): String = "package hiện tại = ${foregroundPackage ?: "(chưa xác định)"}, " +
        "isTikTokForeground = ${isTikTokForeground()}"

    private fun performSwipe(up: Boolean) {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val centerX = width / 2f

        val startY: Float
        val endY: Float
        if (up) {
            startY = height * 0.75f
            endY = height * 0.25f
        } else {
            startY = height * 0.25f
            endY = height * 0.75f
        }

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        // Duration tăng từ 250ms lên 300ms: vuốt quá nhanh đôi khi bị TikTok
        // nhận nhầm thành tap/fling yếu và không chuyển video.
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture hoàn thành")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture bị hủy (có thể do gesture khác đang chạy, hoặc hệ thống chặn)")
            }
        }, null)

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture() trả về false — gesture KHÔNG được gửi đi")
        }
    }

    companion object {
        private const val TAG = "GestureAccessibility"

        // Tham chiếu tĩnh để GestureForegroundService gọi trực tiếp.
        // Chỉ có 1 instance service chạy tại một thời điểm nên an toàn để dùng theo cách này.
        var instance: GestureAccessibilityService? = null

        // Package name của TikTok (bản thường và bản Lite tại một số khu vực)
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )
    }
}
