# OpenHOYI

[产品介绍网站](https://boolean93.github.io/openhoyi/) · [功能状态](docs/native-feature-status.md) · [实机验收记录](docs/alpha-acceptance.md)

## 这是什么

OpenHOYI 是面向 **HOYI 咖啡机**的开源 Android 应用，使用 Kotlin 原生开发。

通过蓝牙连接咖啡机和电子秤，在手机或平板上选择萃取曲线、观察实时数据，回看每一杯的记录。

项目目前处于 **Alpha 阶段**，已有双设备连接、受监护萃取与手动停止的实机记录，部分控制功能仍待完整验收。

## 可以做什么

- **连接设备**：连接咖啡机和电子秤，查看实时读数、连接状态与机器告警。
- **选择曲线**：浏览 100 条工厂曲线与 3 条已采集曲线，按分类或名称查找，设置首页的 5 个快捷槽位。
- **观察萃取**：实时查看压力、温度、水流、秤流速与重量；启动前确认，萃取中可手动请求停止。
- **管理机器**：查看和调整温度、加热、照明、运行模式、拨杆模式与睡眠计划，等待机器回报确认设置结果。
- **回看记录**：离线查看萃取历史与采样曲线，导出历史 ZIP；也可导入旧版历史，单独只读查看。
- **排查问题**：导出传输与操作日志，帮助定位连接与设备问题。

具体实现与验证进度见[功能状态](docs/native-feature-status.md)、[覆盖矩阵](docs/coverage.md)和[实机验收记录](docs/alpha-acceptance.md)。

## 支持什么

### 平台与咖啡机

| 项目 | 当前范围 |
|---|---|
| 手机 / 平板 | 支持 BLE 的 Android 8.0（API 26）及以上设备 |
| 咖啡机 | HOYI；固件 **1.1.3** 已实机验收并开放控制；**1.1.4** 协议证据支持放开，待实机启停/设置验收 |
| 萃取曲线 | 可启动已校验的工厂曲线与采集曲线；导入的个人曲线暂只读 |

其他咖啡机固件处于只读范围：首页会显示固件版本与“暂不支持新版固件”，不发送控制帧；认证后读取不到兼容设置帧时会明确提示协议初始化超时，不会无限停留在连接中。目前不提供其他平台的应用。

### 电子秤

| 品牌 / 型号 | 接入状态 | 可用功能 |
|---|---|---|
| **BOOKOO**（当前已采集协议） | 已接入，有实机连接与归零通知记录 | 重量、设备流速、去皮 |
| **Felicita**（指定 ASCII 通知协议） | 已接入，待实机验证 | 克单位重量、手动去皮；旧只读绑定须重新扫描选择 |
| **TIMEMORE DOT（TES017）** | 已按公开协议接入，待实机验证 | 重量、设备流速、电量、手动去皮 |
| **TIMEMORE Basic3 Link（TES016）** | 已按公开协议接入，待实机验证 | 重量、设备流速、电量、手动去皮 |
| **旧款 TIMEMORE**（广播名 `timemore scale`） | 实验性只读，待实机验证 | 指定双通道协议的重量读数 |
| **Acaia A**（`PROCHBT` / `ACAIA`） | 已接入，待实机验证 | 确认克单位后的重量、手动去皮 |
| **Acaia AP**（`LUNAR` / `PEARL` / `PYXIS`） | 已接入，待实机验证 | 确认克单位后的重量；暂不提供去皮 |
| **DiFluid**（`Microbalance` / `difluid` / `Mb Ti`） | 已接入，待实机验证 | 自带克单位的重量、手动去皮；协议最低固件 Microbalance **V300** / Ti **V010** |

适配范围以表中型号与协议为准，不代表品牌全系列兼容。

### 应用与构建

| 应用 / 构建 | 用途 | Android 包名 |
|---|---|---|
| **OpenHOYI（主包）** | 供用户使用，连接真实设备 | `io.openhoyi.mobile` |
| **Mock** | 使用模拟设备，体验功能与调试界面 | `io.openhoyi.mobile.mock` |
| **OpenHOYI Lab** | 协议采集与连接诊断 | `io.openhoyi.lab` |

这些应用可与旧版 HOYI App 并存，但同一台设备不能同时供多个 App 连接。旧版数据需手动导出、导入；Mock 结果不代表真实设备验收通过。

## 如何贡献代码

欢迎修复问题、改善体验、补充文档或参与设备适配。较大的改动请先通过 [Issues](https://github.com/boolean93/openhoyi/issues) 讨论范围与验证方式。

1. Fork 仓库，从最新 `develop` 创建短分支，例如 `feature/scale-support` 或 `fix/connection-state`。
2. 完成改动并补充与风险相称的测试；设备适配请记录型号、固件与脱敏协议证据，不把离线测试当作实机验收。
3. 提交 PR 到本仓库的 `develop`，说明改动目的、验证结果与已知限制，等待必需检查通过；不要直接推送 `main` / `develop`。

完整规则见[贡献工作流](docs/git-workflow.md)。只想反馈问题也很欢迎，请附上应用与 Android 版本、设备型号与固件、复现步骤及脱敏日志，不要上传设备密码或个人信息。

### 本地构建

准备 **JDK 17** 和 **Android SDK 35**，使用仓库自带的 Gradle Wrapper：

```sh
# 将 YOUR_USERNAME 替换为你的 GitHub 用户名
git clone --branch develop https://github.com/YOUR_USERNAME/openhoyi.git
cd openhoyi
git switch -c feature/scale-support
```

使用 Android Studio 配置 SDK，或设置 `ANDROID_HOME`；也可在本地 `local.properties` 中填写 `sdk.dir`，不要将该文件提交到仓库。

| 构建 | 命令 | APK 路径 |
|---|---|---|
| Alpha（Debug） | `./gradlew :mobile:assembleDebug` | `mobile/build/outputs/apk/debug/mobile-debug.apk` |
| Mock | `./gradlew :mobile:assembleMock` | `mobile/build/outputs/apk/mock/mobile-mock.apk` |
| Lab | `./gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |

若无法访问 Google Maven，可在命令后添加 `-PgoogleMirror=aliyun`。

连接已开启 USB 调试的 Android 设备后，可安装构建产物，例如 Alpha：

```sh
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

覆盖安装前，请确认咖啡机空闲、签名与原安装一致，并在构建时用 `-PhoyiVersionCode=<更大的版本码>` 指定递增版本。平板上的 Debug 与 Mock 应从同一源码以相同版本同步更新，不要通过卸载、清数据或降级绕过冲突。签名与升级要求见[安装包分发说明](docs/plans/apk-distribution.md)。

首次连接时，开启蓝牙并授予扫描、连接权限，断开其他 App，按提示输入咖啡机的六位设备密码；电子秤需单独连接，并按设备说明进入可发现状态。等待设备就绪、实时读数更新后再操作。

### 项目结构与验证

| 模块 | 职责 |
|---|---|
| `mobile` | Alpha 应用及 Mock 构建：连接、曲线、萃取、历史与设置 |
| `app` | Lab 应用：诊断、采集与日志导出 |
| `protocol-core` | 协议编解码，无 Android 依赖 |
| `device-session` | 设备会话、命令队列、重连与控制状态机，无 Android 依赖 |
| `bluetooth-android` | Android BLE 扫描、GATT 与设备会话桥接 |
| `trace-core` | 共用日志与 ZIP 导出，无 Android 依赖 |

提交前运行与改动相关的检查，例如：

```sh
./gradlew :protocol-core:check :device-session:check :mobile:testDebugUnitTest
```

涉及启动、停止、去皮或设置写入时，还需要机器回报与实际动作的证据；GATT 写入成功本身不代表设备已经执行。更多开发资料见[架构说明](docs/architecture.md)、[Lab 使用说明](docs/native-lab.md)与[旧版曲线迁移](docs/legacy-curve-import.md)。

## 赞助与交流

如果 OpenHOYI 帮到了你，可以请我喝杯咖啡；遇到问题或想聊用法，也欢迎进群。赞助完全自愿，不影响功能与支持。

<p>
  <img src="site/assets/sponsor/wechat-pay.png" alt="微信支付收款码" width="220">
  <img src="site/assets/community/wechat-group.png" alt="OpenHOYI 微信交流群二维码" width="220">
</p>

群二维码会过期；失效后请到 [issues](https://github.com/boolean93/openhoyi/issues) 留言。

## 许可证

项目采用 [GNU GPL v2](LICENSE) 许可证，使用、修改或分发前请阅读许可条款。

## 免责声明与使用风险

OpenHOYI 是个人开发的开源实验项目，与咖啡机制造商无关联，也未获得其认证。本应用会通过蓝牙向咖啡机发送控制指令；软件缺陷、协议理解偏差或设备兼容性问题，可能导致控制异常、设备无法使用，甚至设备损坏。

Debug / Alpha 版本仅供了解风险的用户自行评估和试用，并非经过完整验证的正式产品，不保证功能正确、持续可用或适用于你的设备。**试用时请在机器旁看守，并保留机器本身的停止手段。** 出现异常或操作结果未知时，请停止使用并核对机器实际状态，不要反复发送控制指令。

在适用法律允许的最大范围内，本项目及其贡献者按“现状”提供软件，不作任何明示或默示保证，也不对使用或无法使用本项目造成的设备不可用、损坏、数据丢失或其他损失承担责任。本声明不排除或限制适用法律规定不得排除或限制的责任。完整许可条件见 [LICENSE](LICENSE)。
