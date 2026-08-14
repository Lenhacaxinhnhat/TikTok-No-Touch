package com.example.tiktokgesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Theo dõi app nào đang hiển thị trước mặt người dùng, để chỉ vuốt
        // khi TikTok đang mở (tránh vuốt nhầm sang app khác).
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { foregroundPackage = it.toString() }
        }
    }

    override fun onInterrupt() {}

    private fun isTikTokForeground(): Boolean {
        val pkg = foregroundPackage ?: return false
        return TIKTOK_PACKAGES.any { pkg == it }
    }

    /** Vuốt từ dưới lên -> chuyển sang video tiếp theo (giống thao tác thật trên TikTok) */
    fun swipeNext() {
        if (isTikTokForeground()) performSwipe(up = true)
    }

    /** Vuốt từ trên xuống -> quay lại video trước */
    fun swipePrevious() {
        if (isTikTokForeground()) performSwipe(up = false)
    }

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

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()

        dispatchGesture(gesture, null, null)
    }

    companion object {
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
