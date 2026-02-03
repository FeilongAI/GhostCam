# GhostCam 编译指南

## 环境要求

1. **JDK 11 或更高版本**
   - 下载: https://adoptium.net/
   - 设置 `JAVA_HOME` 环境变量

2. **Android SDK**
   - 安装 Android Studio 或单独安装 SDK
   - 需要 SDK 34 (Android 14)
   - 设置 `ANDROID_HOME` 环境变量

3. **Gradle 8.0+** (项目已包含 wrapper)

## 编译步骤

### 方法一：使用命令行

```bash
# Windows
gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

编译完成后，APK 位于：
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

### 方法二：使用 Android Studio

1. 打开 Android Studio
2. 选择 `File` -> `Open` -> 选择项目根目录
3. 等待 Gradle 同步完成
4. 点击 `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`

## 签名 APK

### 创建签名密钥

```bash
keytool -genkey -v -keystore ghostcam.keystore -alias ghostcam -keyalg RSA -keysize 2048 -validity 10000
```

### 签名 APK

```bash
# 使用 apksigner (推荐)
apksigner sign --ks ghostcam.keystore --out app-release-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk

# 或使用 jarsigner
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore ghostcam.keystore app/build/outputs/apk/release/app-release-unsigned.apk ghostcam
```

## 安装和使用

### 前提条件
- 设备已 Root
- 已安装 LSPosed/EdXposed/Xposed Framework

### 安装步骤

1. 安装签名后的 APK
2. 在 LSPosed/Xposed 管理器中启用 GhostCam 模块
3. 选择要 hook 的目标应用
4. 重启设备或目标应用

### 使用方法

1. 打开 GhostCam 应用
2. 点击 "Start Screen Capture" 开始屏幕录制
3. 授权屏幕录制权限
4. 选择要使用虚拟摄像头的目标应用
5. 点击 "Apply GhostCam"
6. 打开目标应用的摄像头功能，即可看到屏幕内容

## 常见问题

### Q: 编译报错 "SDK location not found"
A: 创建 `local.properties` 文件，添加：
```
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

### Q: 屏幕录制没有效果
A: 确保：
1. GhostCam 服务正在运行（通知栏有图标）
2. Xposed 模块已启用并重启过
3. 目标应用在模块作用域内

### Q: 画面卡顿
A: 可以在 ScreenCaptureService.java 中降低分辨率：
```java
width = metrics.widthPixels / 2;
height = metrics.heightPixels / 2;
```

## 项目结构

```
app/src/main/java/com/example/ghostcam/
├── LoginActivity.java      # 登录界面
├── MainActivity.java       # 主界面，控制屏幕录制
├── ScreenCaptureService.java # 屏幕录制服务
└── xposed/
    └── CameraHook.java     # Xposed hook 实现
```
