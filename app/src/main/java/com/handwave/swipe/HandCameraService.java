package com.handwave.swipe;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.hand.detection.Hand;
import com.google.mlkit.vision.hand.detection.HandDetection;
import com.google.mlkit.vision.hand.detection.HandDetector;
import com.google.mlkit.vision.hand.detection.HandDetectorOptions;

import java.util.Collections;

public class HandCameraService extends Service {
    private static final String CHANNEL_ID = "handswipe_camera";
    private static final int NOTIFICATION_ID = 9001;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandDetector detector;

    private float lastY = Float.NaN;
    private long lastFrameMs = 0;
    private int directionVotes = 0;
    private boolean processing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        cameraThread = new HandlerThread("HandSwipeCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        HandDetectorOptions options = new HandDetectorOptions.Builder()
                .setPerformanceMode(HandDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMaxNumHands(1)
                .build();
        detector = HandDetection.getClient(options);

        cameraHandler.post(this::openFrontCamera);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("HandSwipe đang chạy")
                .setContentText("Đưa tay lên/xuống để vuốt màn hình")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "HandSwipe camera", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private void openFrontCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return;

        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) return;

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) return;
                if (processing) {
                    image.close();
                    return;
                }
                processing = true;
                processFrame(image);
            }, cameraHandler);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(@NonNull CameraDevice device) {
                    camera = device;
                    createSession();
                }
                @Override public void onDisconnected(@NonNull CameraDevice device) { device.close(); camera = null; }
                @Override public void onError(@NonNull CameraDevice device, int error) { device.close(); camera = null; }
            }, cameraHandler);
        } catch (Exception ignored) { }
    }

    private void createSession() {
        try {
            Surface surface = imageReader.getSurface();
            camera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(surface);
                        request.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        session.setRepeatingRequest(request.build(), null, cameraHandler);
                    } catch (Exception ignored) { }
                }
                @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) { }
            }, cameraHandler);
        } catch (Exception ignored) { }
    }

    private void processFrame(Image image) {
        final long now = SystemClock.uptimeMillis();
        InputImage input = InputImage.fromMediaImage(image, 0);
        detector.process(input)
                .addOnSuccessListener(cameraHandler::post, hands -> handleHands(hands, now))
                .addOnFailureListener(cameraHandler, e -> resetTracking())
                .addOnCompleteListener(cameraHandler, task -> {
                    image.close();
                    processing = false;
                });
    }

    private void handleHands(java.util.List<Hand> hands, long now) {
        if (hands == null || hands.isEmpty()) {
            resetTracking();
            return;
        }

        Hand hand = hands.get(0);
        if (hand.getLandmarks().isEmpty()) return;

        float y = 0f;
        for (com.google.mlkit.vision.hand.landmark.HandLandmark landmark : hand.getLandmarks()) {
            y += landmark.getPosition().y;
        }
        y /= hand.getLandmarks().size();

        if (!Float.isNaN(lastY) && now - lastFrameMs >= 40) {
            float dy = y - lastY;
            if (Math.abs(dy) >= 14f) {
                directionVotes += dy > 0 ? 1 : -1;
            } else if (Math.abs(dy) < 5f) {
                directionVotes = 0;
            }

            if (directionVotes >= 3) {
                SwipeAccessibilityService.swipeDown();
                directionVotes = 0;
                lastY = y;
                lastFrameMs = now;
                return;
            }
            if (directionVotes <= -3) {
                SwipeAccessibilityService.swipeUp();
                directionVotes = 0;
                lastY = y;
                lastFrameMs = now;
                return;
            }
        }

        lastY = y;
        lastFrameMs = now;
    }

    private void resetTracking() {
        lastY = Float.NaN;
        directionVotes = 0;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        try { if (captureSession != null) captureSession.close(); } catch (Exception ignored) { }
        try { if (camera != null) camera.close(); } catch (Exception ignored) { }
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) { }
        if (detector != null) detector.close();
        if (cameraThread != null) cameraThread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
