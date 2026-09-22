# 本地播放设备选择

播放器底部的播放设备入口同时列出本地设备和 AirPlay 设备。本地列表来自 Android 已连接的媒体输出设备，包括扬声器、蓝牙媒体音频、有线耳机、USB 和 HDMI；不会扫描或配对蓝牙设备。请先在系统设置中连接耳机。

点击设备后，通过后台 MediaSession 请求 ExoPlayer 切换首选音频设备，队列、歌曲、进度及播放意图保持不变。勾选依据播放中的实际 AudioTrack 路由，不能仅凭请求成功就认定切换完成。暂停时显示“待播放时确认”，恢复播放后再确认。暂停期间显示的实际设备是最近一次确认结果。

Android/厂商可能不接受某些输出组合。累计播放 8 秒仍未确认目标时，应用暂停并显示错误，可重试或点击面板右上角的 AirPlay 图标打开系统输出选择器。Android 14 及以上尝试打开系统选择器；不可用或较低系统版本进入蓝牙设置。“跟随系统”清除应用首选设备，让系统决定输出。

设备断开时暂停播放；不会主动恢复播放。设备编号仅在当前连接中有效，不跨启动保存。AirPlay 与本地设备使用同一播放队列；本地设备音量使用系统媒体音量，AirPlay 使用远端音量。本地选择不依赖局域网权限。

## 自动验证

```sh
./gradlew :core:player:testDebugUnitTest :app:assembleDebug
ANDROID_SERIAL="${TEST_EMULATOR_SERIAL:?请先设置专用测试模拟器序列号}" ./gradlew :core:player:connectedDebugAndroidTest
ANDROID_SERIAL="${TEST_EMULATOR_SERIAL:?请先设置专用测试模拟器序列号}" ./gradlew :feature:music:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.seasonyuu.fnmusic.feature.music.LocalOutputSheetTest
```

先核实并设置专用测试模拟器的序列号，遵守[测试设备与数据保护规则](testing.md#device-checks)。不要使用日常开发或手动验收的模拟器，也不要将不同模块的测试类放到同一个全局 instrumentation 类过滤器中。

覆盖请求与实际路由分离、暂停期间不超时、切换超时、连续选择、设备移除、设备类型过滤、状态序列化、无局域网权限、本地与 AirPlay 交接及面板状态。平台测试在模拟器播放静音 WAV，检查真实 AudioTrack 路由与音轨重建；该测试在非模拟器上跳过。模拟器测试不能替代蓝牙真机验收。

## 手动验收（待用户完成）

1. 先连接蓝牙耳机，播放歌曲，确认面板列出耳机并正确标示实际设备。
2. 扬声器 → 蓝牙耳机 → 扬声器，核对实际声音、勾选、歌曲和进度；遇系统限制时检查错误及系统选择器。
3. 暂停时选另一设备，确认显示待播放提示；恢复后核对实际输出。
4. 连续快速选择两个设备，确认最终勾选与实际声音一致。
5. 播放时断开耳机，确认暂停，且未自动从扬声器继续播放；重新选择设备后手动恢复。
6. 本地设备 → AirPlay → 指定本地设备，确认队列、进度、暂停/播放意图以及两种音量控制正常。
7. 从系统输出选择器切换设备，确认应用最终显示实际设备；通知栏和锁屏仍控制同一队列。

本次自动验证不安装或操作真机。蓝牙、有线和 USB 的实际兼容性、听音结果需上述手动验收。
