package com.example.ghostcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "GhostCamChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String FRAME_FILE = "ghostcam_frame.dat";

    private final IBinder binder = new LocalBinder();
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;

    private int width;
    private int height;
    private int dpi;

    // 静态变量用于 Xposed 模块访问
    private static volatile byte[] latestFrameNV21;
    private static volatile int frameWidth;
    private static volatile int frameHeight;
    private static Context appContext;

    public class LocalBinder extends Binder {
        ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        createNotificationChannel();
        
        handlerThread = new HandlerThread("ScreenCapture");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification());

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        width = intent.getIntExtra("width", 720);
        height = intent.getIntExtra("height", 1280);
        dpi = intent.getIntExtra("dpi", 320);

        if (resultCode != -1 && data != null) {
            MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            if (mediaProjection != null) {
                startCapture();
            }
        }

        return START_STICKY;
    }


    private void startCapture() {
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    processImage(image);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, handler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "GhostCamCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, handler
        );
    }

    private void processImage(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        // 创建 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, 
            Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);

        // 裁剪到正确尺寸
        if (bitmap.getWidth() != width) {
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();
            bitmap = cropped;
        }

        // 转换为 NV21 格式并保存
        byte[] nv21Data = bitmapToNV21(bitmap);
        latestFrameNV21 = nv21Data;
        frameWidth = width;
        frameHeight = height;

        // 保存到文件供 Xposed 模块读取
        saveFrameToFile(nv21Data);

        bitmap.recycle();
    }

    private byte[] bitmapToNV21(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        int ySize = w * h;
        int uvSize = w * h / 2;
        byte[] nv21 = new byte[ySize + uvSize];

        int yIndex = 0;
        int uvIndex = ySize;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int pixel = pixels[j * w + i];
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                // RGB to YUV conversion
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                nv21[yIndex++] = (byte) clamp(y, 0, 255);

                // NV21 格式: VUVU...
                if (j % 2 == 0 && i % 2 == 0 && uvIndex < nv21.length - 1) {
                    nv21[uvIndex++] = (byte) clamp(v, 0, 255);
                    nv21[uvIndex++] = (byte) clamp(u, 0, 255);
                }
            }
        }
        return nv21;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void saveFrameToFile(byte[] data) {
        try {
            File file = new File(getExternalFilesDir(null), FRAME_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            // 写入宽高信息
            fos.write((width >> 8) & 0xff);
            fos.write(width & 0xff);
            fos.write((height >> 8) & 0xff);
            fos.write(height & 0xff);
            // 写入帧数据
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 供 Xposed 模块调用的静态方法
    public static byte[] getLatestFrameNV21() {
        return latestFrameNV21;
    }

    public static int getFrameWidth() {
        return frameWidth;
    }

    public static int getFrameHeight() {
        return frameHeight;
    }

    public static Context getAppContext() {
        return appContext;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "GhostCam Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Screen capture for virtual camera");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setContentTitle("GhostCam Active")
            .setContentText("Screen capture is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
    }

    private void stopCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
