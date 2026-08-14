package com.handwave.swipe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class SwipeAccessibilityService extends AccessibilityService {
    private static volatile SwipeAccessibilityService instance;
    private long lastSwipeMs = 0;

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    public static boolean isEnabled() { return instance != null; }
    public static void swipeUp() { if (instance != null) instance.dispatchSwipe(false); }
    public static void swipeDown() { if (instance != null) instance.dispatchSwipe(true); }

    private void dispatchSwipe(boolean down) {
        long now = SystemClock.uptimeMillis();
        if (now - lastSwipeMs < 900) return;
        lastSwipeMs = now;

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);

        float x = size.x * 0.50f;
        float startY = down ? size.y * 0.35f : size.y * 0.70f;
        float endY   = down ? size.y * 0.70f : size.y * 0.35f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 300))
                .build();
        dispatchGesture(gesture, null, null);
    }
}
