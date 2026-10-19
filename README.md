# OpenHOYI

通过蓝牙连接 HOYI 咖啡机和 BOOKOO 电子秤的开源 Android 应用。

查看萃取中的温度、压力、流量和重量，选择萃取曲线，回看每一杯的记录。项目使用 Kotlin 原生开发，蓝牙协议和设备会话独立成库，方便调试、验证和二次开发。

> **当前处于 Alpha 阶段。** 已有双设备连接、受监护萃取和手动停止的实机记录；目标重量自动停止、设置写入和异常恢复仍待完整验收。试用时请在机器旁看守，并保留机器上的手动停止手段。详见 [实机验收记录](docs/alpha-acceptance.md)。

## 可以做什么

- **连接设备**：扫描并连接咖啡机和电子秤，查看连接状态、实时读数和机器告警。
- **选择曲线**：浏览 100 条工厂曲线与 3 条已采集曲线，按分类或名称查找，配置首页的 5 个快捷槽位。
- **观察萃取**：在实时曲线中查看压力、温度、机器水流、秤流速和重量；启动前确认，萃取中可手动请求停止。
- **管理机器**：查看和调整温度、加热、照明、运行模式、拨杆模式及睡眠计划。设置写入后等待机器回报确认。
- **回看记录**：离线查看萃取历史和采样曲线，导出历史 ZIP；也可显式导入旧版历史数据，单独只读查看。
- **排查问题**：导出传输与操作日志，帮助定位连接、协议和设备状态问题。

以上是已实现的功能范围，具体实机验证进度见 [功能状态](docs/native-feature-status.md) 和 [覆盖矩阵](docs/coverage.md)。

## 设备与兼容性

| 项目 | 当前范围 |
|---|---|
| 手机 / 平板 | Android 8.0（API 26）及以上，支持 BLE |
| 咖啡机 | HOYI；控制功能仅对已采集验证的固件 **1.1.3** 开放 |
| 电子秤 | 已采集协议的 BOOKOO 设备；不保证其他型号兼容 |
| 萃取曲线 | 启动入口限于已校验的工厂曲线及采集曲线；导入的个人曲线暂只读 |
| 其他平台 | 当前仓库提供 Android 应用 |

其他咖啡机固件处于只读或不支持范围，请勿据此推断兼容。BOOKOO 的负值帧已有解码测试，但仍缺少已知负重量的实物对照。

## 选择哪个应用

仓库包含两个独立安装的应用，共用协议和蓝牙库：

| 应用 | 用途 | Android 包名 |
|---|---|---|
| **OpenHOYI Alpha** | 试用连接、曲线、萃取和历史等功能 | `io.openhoyi.mobile` |
| **OpenHOYI Lab** | 开发调试、协议采集和连接诊断 | `io.openhoyi.lab` |

两者可与旧版 HOYI App 并存，但连接同一台设备前，请先断开其他 App 的连接。新应用不会直接读取旧 App 的私有数据；数据迁移需手动导出和导入。

## 从源码构建

准备 **JDK 17** 和 **Android SDK 35**。仓库自带 Gradle Wrapper，无需另装 Gradle。

```sh
git clone --branch develop https://github.com/boolean93/openhoyi.git
cd openhoyi
```

用 Android Studio 打开项目并配置 SDK，或设置 `ANDROID_HOME`。也可在本地 `local.properties` 中填写 `sdk.dir`，不要将该文件提交到仓库。

构建 Alpha：

```sh
./gradlew :mobile:assembleDebug
```

APK 位于 `mobile/build/outputs/apk/debug/mobile-debug.apk`。

需要诊断应用时，构建 Lab：

```sh
./gradlew :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

若网络无法访问 Google Maven，可在构建命令后添加 `-PgoogleMirror=aliyun`，显式使用可选镜像。

### 安装与首次连接

开启 Android 设备的 USB 调试，连接电脑后安装 Alpha：

```sh
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

1. 打开 OpenHOYI Alpha，开启蓝牙并授予扫描、连接所需权限。
2. 确认咖啡机空闲，且其他 App 已断开连接，再扫描并选择设备。
3. 按提示输入咖啡机的六位设备密码；电子秤单独连接。
4. 等待设备就绪和实时读数更新，再选择曲线或操作机器。

未收到的数据会显示为未知；过期读数和断线状态不能作为控制成功的依据。若操作结果未知，先核对机器实际状态，不要反复发送命令。

本地构建使用 debug 签名。更新已有安装包时必须保持签名一致；遇到签名冲突请先核对安装来源，不要通过卸载清空历史来解决。正式签名与升级要求见 [安装包分发说明](docs/plans/apk-distribution.md)。

## 开发与贡献

欢迎通过 [Issues](https://github.com/boolean93/openhoyi/issues) 反馈问题或讨论功能。报告设备问题时，请附上应用版本、Android 版本、设备型号与固件、复现步骤，以及脱敏日志；不要上传设备密码或其他个人信息。

开发从 `develop` 创建短分支，通过 PR 合入 `develop`。分支、验证和发布规则见 [贡献工作流](docs/git-workflow.md)。

### 项目结构

| 模块 | 职责 |
|---|---|
| `mobile` | Alpha 应用：连接、曲线、萃取、历史和设置 |
| `app` | Lab 应用：诊断、采集和日志导出 |
| `protocol-core` | HOYI / BOOKOO 协议编解码，无 Android 依赖 |
| `device-session` | 设备会话、命令队列、重连和控制状态机，无 Android 依赖 |
| `bluetooth-android` | Android BLE 扫描、GATT 与设备会话桥接 |
| `trace-core` | 两个应用共用的日志与 ZIP 导出，无 Android 依赖 |

设计细节见 [架构说明](docs/architecture.md)。

### 运行检查

```sh
./gradlew :protocol-core:check :device-session:check \
  :bluetooth-android:assembleDebug :bluetooth-android:lintDebug \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug
```

离线测试与构建检查不能替代实机验收。涉及启动、停止、去皮或设置写入的改动，需要记录机器回报及实际动作；GATT 写入成功本身不代表机器已执行。

## 更多文档

- [Alpha 实机验收](docs/alpha-acceptance.md)：已验证场景、已知问题与待验收项。
- [功能状态](docs/native-feature-status.md) · [覆盖矩阵](docs/coverage.md)：实现范围、证据与缺口。
- [Lab 使用说明](docs/native-lab.md)：诊断应用、日志采集和手工验证。
- [旧版曲线迁移](docs/legacy-curve-import.md)：导出与只读导入的范围。
- [安装包分发](docs/plans/apk-distribution.md)：签名、版本、升级和分发校验。

## 许可证

项目使用 [GNU GPL v2](LICENSE) 许可证。使用、修改或分发前请阅读许可证全文。
