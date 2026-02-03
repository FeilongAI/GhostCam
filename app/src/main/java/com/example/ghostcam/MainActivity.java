package com.example.ghostcam;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final String PREFS_NAME = "GhostCamPrefs";
    private static final String CONFIG_PREFS = "GhostCamConfig";

    private Switch switch1, switch2, switch3;
    private Spinner appSpinner;
    private Button selectVideoButton, createGhostCamButton, logoutButton;

    private MediaProjectionManager projectionManager;
    private ScreenCaptureService screenCaptureService;
    private boolean serviceBound = false;
    private SharedPreferences configPrefs;

    private List<String> appPackageNames = new ArrayList<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScreenCaptureService.LocalBinder binder = (ScreenCaptureService.LocalBinder) service;
            screenCaptureService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            try {
                configPrefs = getSharedPreferences(CONFIG_PREFS, MODE_WORLD_READABLE);
            } catch (SecurityException e) {
                configPrefs = getSharedPreferences(CONFIG_PREFS, MODE_PRIVATE);
            }
            projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            initViews();
            loadSettings();
            loadInstalledApps();
            setupListeners();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void initViews() {
        switch1 = findViewById(R.id.switch1);
        switch2 = findViewById(R.id.switch2);
        switch3 = findViewById(R.id.switch3);
        appSpinner = findViewById(R.id.app_spinner);
        selectVideoButton = findViewById(R.id.select_video_button);
        createGhostCamButton = findViewById(R.id.upload_video_button);
        logoutButton = findViewById(R.id.logout_button);

        // 更新按钮文字为屏幕录制模式
        selectVideoButton.setText("Start Screen Capture");
        createGhostCamButton.setText("Apply GhostCam");
    }

    private void loadSettings() {
        switch1.setChecked(configPrefs.getBoolean("warn_permission", true));
        switch2.setChecked(configPrefs.getBoolean("disable_ghostcam", false));
        switch3.setChecked(configPrefs.getBoolean("play_sound", false));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = configPrefs.edit();
        editor.putBoolean("warn_permission", switch1.isChecked());
        editor.putBoolean("disable_ghostcam", switch2.isChecked());
        editor.putBoolean("play_sound", switch3.isChecked());
        
        int selectedPosition = appSpinner.getSelectedItemPosition();
        if (selectedPosition >= 0 && selectedPosition < appPackageNames.size()) {
            editor.putString("target_package", appPackageNames.get(selectedPosition));
        }
        editor.apply();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> appNames = new ArrayList<>();

        for (ApplicationInfo app : apps) {
            // 过滤掉系统核心应用，保留用户应用和可能使用摄像头的系统应用
            boolean isUserApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            boolean isUpdatedSystemApp = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            
            // 检查是否有摄像头权限
            boolean hasCameraPermission = false;
            try {
                String[] permissions = pm.getPackageInfo(app.packageName, 
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
                if (permissions != null) {
                    for (String perm : permissions) {
                        if (perm.equals("android.permission.CAMERA")) {
                            hasCameraPermission = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            if (isUserApp || isUpdatedSystemApp || hasCameraPermission) {
                String appName = pm.getApplicationLabel(app).toString();
                appNames.add(appName);
                appPackageNames.add(app.packageName);
            }
        }

        // 按名称排序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < appNames.size(); i++) indices.add(i);
        indices.sort((a, b) -> appNames.get(a).compareToIgnoreCase(appNames.get(b)));
        
        List<String> sortedNames = new ArrayList<>();
        List<String> sortedPackages = new ArrayList<>();
        for (int i : indices) {
            sortedNames.add(appNames.get(i));
            sortedPackages.add(appPackageNames.get(i));
        }
        appPackageNames.clear();
        appPackageNames.addAll(sortedPackages);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sortedNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appSpinner.setAdapter(adapter);

        // 恢复之前选择的应用
        String savedPackage = configPrefs.getString("target_package", "");
        int index = appPackageNames.indexOf(savedPackage);
        if (index >= 0) {
            appSpinner.setSelection(index);
        }
    }

    private void setupListeners() {
        selectVideoButton.setOnClickListener(v -> requestScreenCapture());

        createGhostCamButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "GhostCam settings applied!", Toast.LENGTH_SHORT).show();
        });

        logoutButton.setOnClickListener(v -> {
            stopScreenCapture();
            finish();
        });

        switch1.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        switch2.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        switch3.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
    }

    private void requestScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        } else {
            Toast.makeText(this, "Screen capture requires Android 5.0+", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                startScreenCaptureService(resultCode, data);
                Toast.makeText(this, "Screen capture started!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startScreenCaptureService(int resultCode, Intent data) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);
        serviceIntent.putExtra("width", metrics.widthPixels);
        serviceIntent.putExtra("height", metrics.heightPixels);
        serviceIntent.putExtra("dpi", metrics.densityDpi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopScreenCapture() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        stopService(new Intent(this, ScreenCaptureService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
        }
    }
}
