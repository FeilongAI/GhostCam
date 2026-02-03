package com.example.ghostcam.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CameraHook implements IXposedHookLoadPackage {

    private static final String TAG = "GhostCam";
    private static final String PREFS_NAME = "GhostCamConfig";
    private static final String FRAME_FILE = "ghostcam_frame.dat";

    private String targetPackage = "";
    private boolean isDisabled = false;
    private byte[] frameData;
    private int frameWidth;
    private int frameHeight;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 加载配置
        loadConfig();

        if (isDisabled) {
            XposedBridge.log(TAG + ": GhostCam is disabled");
            return;
        }

        // 只 hook 目标应用
        if (!targetPackage.isEmpty() && !lpparam.packageName.equals(targetPackage)) {
            return;
        }

        XposedBridge.log(TAG + ": Hooking package: " + lpparam.packageName);

        // Hook 旧版 Camera API
        hookLegacyCamera(lpparam);

        // Hook Camera2 API
        hookCamera2(lpparam);
    }

    private void loadConfig() {
        try {
            // 读取配置文件
            File prefsFile = new File("/data/data/com.example.ghostcam/shared_prefs/" + PREFS_NAME + ".xml");
            if (prefsFile.exists()) {
                // 简单解析 XML 获取配置
                // 实际使用中建议用 XSharedPreferences
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error loading config: " + e.getMessage());
        }
    }

    private void loadFrameData() {
        try {
            File frameFile = new File("/sdcard/Android/data/com.example.ghostcam/files/" + FRAME_FILE);
            if (!frameFile.exists()) {
                return;
            }

            FileInputStream fis = new FileInputStream(frameFile);
            
            // 读取宽高
            frameWidth = (fis.read() << 8) | fis.read();
            frameHeight = (fis.read() << 8) | fis.read();
            
            // 读取帧数据
            int dataSize = frameWidth * frameHeight * 3 / 2;
            frameData = new byte[dataSize];
            fis.read(frameData);
            fis.close();
            
        } catch (IOException e) {
            XposedBridge.log(TAG + ": Error loading frame: " + e.getMessage());
        }
    }


    private void hookLegacyCamera(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Camera.setPreviewCallback
        XposedHelpers.findAndHookMethod(
            Camera.class,
            "setPreviewCallback",
            Camera.PreviewCallback.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Camera.PreviewCallback originalCallback = (Camera.PreviewCallback) param.args[0];
                    if (originalCallback == null) return;

                    param.args[0] = new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            loadFrameData();
                            if (frameData != null && data != null) {
                                // 替换预览数据
                                byte[] scaledData = scaleNV21(frameData, frameWidth, frameHeight, 
                                    getPreviewWidth(camera), getPreviewHeight(camera));
                                System.arraycopy(scaledData, 0, data, 0, 
                                    Math.min(scaledData.length, data.length));
                            }
                            originalCallback.onPreviewFrame(data, camera);
                        }
                    };
                }
            }
        );

        // Hook Camera.setPreviewCallbackWithBuffer
        XposedHelpers.findAndHookMethod(
            Camera.class,
            "setPreviewCallbackWithBuffer",
            Camera.PreviewCallback.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Camera.PreviewCallback originalCallback = (Camera.PreviewCallback) param.args[0];
                    if (originalCallback == null) return;

                    param.args[0] = new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            loadFrameData();
                            if (frameData != null && data != null) {
                                byte[] scaledData = scaleNV21(frameData, frameWidth, frameHeight,
                                    getPreviewWidth(camera), getPreviewHeight(camera));
                                System.arraycopy(scaledData, 0, data, 0,
                                    Math.min(scaledData.length, data.length));
                            }
                            originalCallback.onPreviewFrame(data, camera);
                        }
                    };
                }
            }
        );

        // Hook Camera.setOneShotPreviewCallback
        XposedHelpers.findAndHookMethod(
            Camera.class,
            "setOneShotPreviewCallback",
            Camera.PreviewCallback.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Camera.PreviewCallback originalCallback = (Camera.PreviewCallback) param.args[0];
                    if (originalCallback == null) return;

                    param.args[0] = new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            loadFrameData();
                            if (frameData != null && data != null) {
                                byte[] scaledData = scaleNV21(frameData, frameWidth, frameHeight,
                                    getPreviewWidth(camera), getPreviewHeight(camera));
                                System.arraycopy(scaledData, 0, data, 0,
                                    Math.min(scaledData.length, data.length));
                            }
                            originalCallback.onPreviewFrame(data, camera);
                        }
                    };
                }
            }
        );
    }

    private int getPreviewWidth(Camera camera) {
        try {
            Camera.Parameters params = camera.getParameters();
            return params.getPreviewSize().width;
        } catch (Exception e) {
            return 640;
        }
    }

    private int getPreviewHeight(Camera camera) {
        try {
            Camera.Parameters params = camera.getParameters();
            return params.getPreviewSize().height;
        } catch (Exception e) {
            return 480;
        }
    }

    private void hookCamera2(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook ImageReader.acquireLatestImage
            Class<?> imageReaderClass = XposedHelpers.findClass(
                "android.media.ImageReader", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                imageReaderClass,
                "acquireLatestImage",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object image = param.getResult();
                        if (image == null) return;

                        loadFrameData();
                        if (frameData != null) {
                            replaceImageData(image);
                        }
                    }
                }
            );

            // Hook ImageReader.acquireNextImage
            XposedHelpers.findAndHookMethod(
                imageReaderClass,
                "acquireNextImage",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object image = param.getResult();
                        if (image == null) return;

                        loadFrameData();
                        if (frameData != null) {
                            replaceImageData(image);
                        }
                    }
                }
            );

        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error hooking Camera2: " + e.getMessage());
        }
    }

    private void replaceImageData(Object image) {
        try {
            Method getPlanesMethod = image.getClass().getMethod("getPlanes");
            Object[] planes = (Object[]) getPlanesMethod.invoke(image);
            
            if (planes != null && planes.length > 0) {
                Method getBufferMethod = planes[0].getClass().getMethod("getBuffer");
                java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) getBufferMethod.invoke(planes[0]);
                
                if (buffer != null && frameData != null) {
                    buffer.clear();
                    buffer.put(frameData, 0, Math.min(frameData.length, buffer.capacity()));
                    buffer.flip();
                }
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error replacing image data: " + e.getMessage());
        }
    }

    // 缩放 NV21 数据到目标尺寸
    private byte[] scaleNV21(byte[] src, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        if (srcWidth == dstWidth && srcHeight == dstHeight) {
            return src;
        }

        int dstYSize = dstWidth * dstHeight;
        int dstUVSize = dstWidth * dstHeight / 2;
        byte[] dst = new byte[dstYSize + dstUVSize];

        // 简单的最近邻缩放
        float xRatio = (float) srcWidth / dstWidth;
        float yRatio = (float) srcHeight / dstHeight;

        // 缩放 Y 平面
        for (int y = 0; y < dstHeight; y++) {
            int srcY = (int) (y * yRatio);
            for (int x = 0; x < dstWidth; x++) {
                int srcX = (int) (x * xRatio);
                dst[y * dstWidth + x] = src[srcY * srcWidth + srcX];
            }
        }

        // 缩放 UV 平面
        int srcUVOffset = srcWidth * srcHeight;
        int dstUVOffset = dstYSize;
        
        for (int y = 0; y < dstHeight / 2; y++) {
            int srcY = (int) (y * yRatio);
            for (int x = 0; x < dstWidth / 2; x++) {
                int srcX = (int) (x * xRatio);
                int srcIdx = srcUVOffset + srcY * srcWidth + srcX * 2;
                int dstIdx = dstUVOffset + y * dstWidth + x * 2;
                
                if (srcIdx + 1 < src.length && dstIdx + 1 < dst.length) {
                    dst[dstIdx] = src[srcIdx];         // V
                    dst[dstIdx + 1] = src[srcIdx + 1]; // U
                }
            }
        }

        return dst;
    }
}
