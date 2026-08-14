package com.handwave.swipe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int CAMERA_REQ = 100;
    private static final int NOTIFICATION_REQ = 101;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermissionIfNeeded();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 56, 48, 40);

        TextView title = new TextView(this);
        title.setText("HandSwipe");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);

        TextView info = new TextView(this);
        info.setText("Camera trước nhận diện chuyển động tay.\nTay đi lên = vuốt lên. Tay đi xuống = vuốt xuống.");
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 24);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, 24);

        Button accessibility = new Button(this);
        accessibility.setText("1. Bật quyền Trợ năng");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button start = new Button(this);
        start.setText("2. Bắt đầu");
        start.setOnClickListener(v -> startHandSwipe());

        Button stop = new Button(this);
        stop.setText("Dừng");
        stop.setOnClickListener(v -> stopService(new Intent(this, HandCameraService.class)));

        root.addView(title);
        root.addView(info);
        root.addView(status);
        root.addView(accessibility);
        root.addView(start);
        root.addView(stop);
        setContentView(root);
        updateStatus();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQ);
        }
    }

    private void startHandSwipe() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
            return;
        }
        if (!SwipeAccessibilityService.isEnabled()) {
            status.setText("Chưa bật Trợ năng. Hãy bật HandSwipe rồi quay lại.");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        Intent service = new Intent(this, HandCameraService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText("ĐANG CHẠY — đưa một bàn tay trước camera.");
    }

    private void updateStatus() {
        status.setText(SwipeAccessibilityService.isEnabled()
                ? "Trợ năng: ĐÃ BẬT"
                : "Trợ năng: CHƯA BẬT");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
    }
}
