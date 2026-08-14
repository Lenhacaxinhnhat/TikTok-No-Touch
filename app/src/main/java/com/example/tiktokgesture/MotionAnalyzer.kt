package com.example.tiktokgesture

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Phát hiện cử chỉ vẫy tay lên / xuống bằng cách so sánh độ sáng (luma)
 * giữa các khung hình liên tiếp, tìm trọng tâm vùng chuyển động và
 * theo dõi xu hướng di chuyển theo trục Y theo thời gian.
 *
 * Không cần model AI, chạy nhẹ, đủ để nhận "vẫy tay" trước camera.
 */
class MotionAnalyzer(
    private val onGesture: (Gesture) -> Unit
) : ImageAnalysis.Analyzer {

    enum class Gesture { SWIPE_UP, SWIPE_DOWN }

    // Kích thước lưới downsample để tính toán nhanh
    private val gridW = 24
    private val gridH = 32

    private var prevLuma: FloatArray? = null

    // Lịch sử trọng tâm Y của vùng chuyển động, kèm timestamp
    private data class Sample(val y: Float, val t: Long, val motionAmount: Float)
    private val history = ArrayDeque<Sample>()
    private val windowMs = 600L

    private var lastTriggerTime = 0L
    private val cooldownMs = 900L

    // Ngưỡng để coi là chuyển động thật (không phải nhiễu)
    private val motionPixelThreshold = 18f
    private val minMotionRatio = 0.03f // ít nhất 3% ô lưới có chuyển động rõ

    override fun analyze(image: ImageProxy) {
        try {
            val luma = extractDownsampledLuma(image)
            val prev = prevLuma

            if (prev != null) {
                var sumWeightedY = 0f
                var sumWeight = 0f
                var movingCells = 0

                for (y in 0 until gridH) {
                    for (x in 0 until gridW) {
                        val idx = y * gridW + x
                        val diff = abs(luma[idx] - prev[idx])
                        if (diff > motionPixelThreshold) {
                            movingCells++
                            sumWeightedY += y * diff
                            sumWeight += diff
                        }
                    }
                }

                val totalCells = gridW * gridH
                val motionRatio = movingCells.toFloat() / totalCells

                if (motionRatio > minMotionRatio && sumWeight > 0f) {
                    val centroidY = sumWeightedY / sumWeight // 0 = trên, gridH = dưới
                    val now = System.currentTimeMillis()
                    history.addLast(Sample(centroidY, now, motionRatio))

                    // Xoá mẫu quá cũ khỏi cửa sổ thời gian
                    while (history.isNotEmpty() && now - history.first().t > windowMs) {
                        history.removeFirst()
                    }

                    maybeTriggerGesture(now)
                }
            }

            prevLuma = luma
        } finally {
            image.close()
        }
    }

    private fun maybeTriggerGesture(now: Long) {
        if (now - lastTriggerTime < cooldownMs) return
        if (history.size < 4) return

        val first = history.first()
        val last = history.last()
        val deltaY = last.y - first.y // âm = tay di chuyển lên (Y giảm), dương = tay di chuyển xuống

        // Cần độ dịch chuyển đủ lớn so với chiều cao lưới (ví dụ > 40% chiều cao)
        val threshold = gridH * 0.4f

        if (deltaY <= -threshold) {
            lastTriggerTime = now
            history.clear()
            onGesture(Gesture.SWIPE_UP) // tay vẫy từ dưới lên -> chuyển video tiếp theo
        } else if (deltaY >= threshold) {
            lastTriggerTime = now
            history.clear()
            onGesture(Gesture.SWIPE_DOWN) // tay vẫy từ trên xuống -> quay lại video trước
        }
    }

    /** Lấy kênh Y (luma) từ ảnh YUV_420_888 và downsample về lưới gridW x gridH */
    private fun extractDownsampledLuma(image: ImageProxy): FloatArray {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val out = FloatArray(gridW * gridH)
        val stepX = width / gridW
        val stepY = height / gridH

        for (gy in 0 until gridH) {
            val srcY = (gy * stepY).coerceIn(0, height - 1)
            for (gx in 0 until gridW) {
                val srcX = (gx * stepX).coerceIn(0, width - 1)
                val offset = srcY * rowStride + srcX * pixelStride
                val value = if (offset < buffer.capacity()) {
                    (buffer.get(offset).toInt() and 0xFF).toFloat()
                } else 0f
                out[gy * gridW + gx] = value
            }
        }
        return out
    }
}
