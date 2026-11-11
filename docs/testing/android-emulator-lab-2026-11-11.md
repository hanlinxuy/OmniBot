# Android 权限预配置测试环境（2026-11-11）

用户授权后，在远端 Apple Silicon Mac 上建立一台独立、无个人账号的 Android Emulator。该机器是 macOS，因而使用原生 Hypervisor.framework + ARM64 模拟器；没有额外部署 Linux VM/redroid。此记录是环境验收，不增加 MobileWorld/iOSWorld 的通过数量。

## 固定环境

- Android Emulator 35.5.10，AOSP Android 14 / API 34，arm64-v8a，userdebug。
- 单个 AVD `omnibot-lab`，序列号 `emulator-5580`；4 vCPU、4 GiB RAM、8 GiB data；无窗口，软件图形。
- 默认工作目录 `~/omnibot-android-lab`；SDK 默认 `~/Library/Android/sdk`。脚本支持 `LAB_DIR`、`ANDROID_SDK_ROOT` 覆盖。不要指向个人 AVD。
- 系统镜像来自 Google：`https://dl.google.com/android/repository/sys-img/android/arm64-v8a-34_r04.zip`。SHA-1 与官方索引一致：`d9f2011131919abe952814e041f16f317242c3fa`。
- OmniBot APK SHA-256：`11c0893bdd73895f550607595eff5ab0dcf226fa39c15a0d4e8134727d6ddde5`，包名 `cn.com.omnimind.bot`；本地和远端文件独立比对一致。
- Shizuku 官方 v13.6.0 r1086，动态解析安装路径启动；不硬编码随机 APK 目录。
- 应用正常初始化 Alpine / Chat Agent Assistant / Pi；独立 run-as 启动检查：Node v22.23.4、Python 3.12.16、`omnibot-pi-acp 0.1.0 (Pi 1.4.0)`。

## 权限与验收

使用 `adb install -r -g` 授予已声明且可授予的运行时权限。实际读回日历、联系人、通知、媒体、定位、相机、蓝牙、短信和通话记录等 runtime permissions 为 granted。测试环境无个人数据。

单独配置并读回 AppOps：悬浮窗、修改系统设置、使用情况、全部文件、安装来源、屏幕投影、精确闹钟。部分特殊权限在 PackageManager 的普通 `granted` 字段仍为 false，应按对应 AppOps 和 Android API 判断，不能只看单一字段宣称失败或全部可用。

- 无障碍不仅写入设置，`dumpsys accessibility` 确认 AssistsService 已绑定。
- 通知监听器登记正确；应用加入省电白名单。
- Shizuku server 有实际 PID；OmniBot 的 `API_V23` 权限已授予。这不单独证明每个 OmniBot 特权工具调用成功。
- 独立 AVD 无锁屏，关闭动画。未修改 vivo 安全设置。
- 应用 UI 权限页显示 `3 of 3 core permissions ready`。
- 快照 `omnibot-permission-baseline` 保存后，将亮度从 102 改为 73，再加载快照；独立读回恢复为 102，无障碍和 Shizuku 仍正常。
- ADB server、console 和 emulator ADB 端口经 lsof 核对仅监听回环地址；通过 SSH 使用，不开放公网 ADB。
- Alpine/Pi 完成后重新保存基线，停止并重新启动模拟器进程。日志确认成功加载基线；重新读回 boot_completed=1、Shizuku PID、无障碍绑定，Pi 健康检查再次通过。

## 使用

在目标 Mac 上运行仓库脚本 `scripts/android-emulator-lab.sh`。初始化前准备：

```bash
mkdir -p ~/omnibot-android-lab/downloads
curl -fL https://dl.google.com/android/repository/sys-img/android/arm64-v8a-34_r04.zip \
  -o ~/omnibot-android-lab/downloads/android34.zip
curl -fL https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk \
  -o ~/omnibot-android-lab/downloads/shizuku.apk
# 将待测 APK 放到 ~/omnibot-android-lab/omnibot.apk。
bash scripts/android-emulator-lab.sh init
bash scripts/android-emulator-lab.sh start
# 等 sys.boot_completed=1 后：
bash scripts/android-emulator-lab.sh provision
bash scripts/android-emulator-lab.sh status
bash scripts/android-emulator-lab.sh snapshot
```

远端当前脚本副本为 `~/omnibot-android-lab/lab.sh`。`restore` 恢复已保存基线；`stop` 停止此模拟器；`start` 在基线存在时加载它。保存/恢复/配置前均核对 AVD 名称，避免混淆另一台模拟器。恢复会丢弃基线之后的测试数据，因此先归档证据和运行结果，再恢复。

首次应用初始化由正常安装向导完成。后续换 APK 时，确认没有运行任务，执行 provision、验收后重新保存 snapshot。若应用更新了权限声明，必须重新 provision，不能假定旧快照包含新权限。脚本没有自动登录任何模型服务。

## 边界

- 本轮验证环境、权限状态、应用启动、Alpine/Pi 健康检查及快照回滚；没有把健康检查算作模型任务成功，未在新设备上重新跑三模型 benchmark。
- Pi run-as 健康检查仍报告已有的 `/sdcard`、`/proc/self/fd` proot 绑定警告；健康检查成功不代表共享存储绑定已通过。
- AppOps 配置不代表每个系统确认框都已端到端验证；应用自己的授权逻辑、角色选择和未来新增权限需按场景验证。
- 不代表 vivo OEM 后台、安装器、真实硬件、真实短信、银行/支付等行为。银行/支付、微信及用户排除应用继续不测。
- 不复制个人数据、手机现有登录态或模型密钥；没有改动模型服务或 vivo。
