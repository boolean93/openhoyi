# OpenHOYI

面向 **HOYI 咖啡机**的开源 Android 应用，使用 Kotlin 原生开发。

通过蓝牙连接咖啡机和电子秤，实时查看温度、压力、流量与重量，选择萃取曲线，回看每一杯的记录。

目前已接入 **BOOKOO、泰摩 TIMEMORE DOT / Basic3 Link**，并提供旧款 TIMEMORE 与 Felicita 的实验性只读连接。不同设备的可用功能与验证进度不同，详见下方[电子秤适配情况](#电子秤适配情况)。

> **项目处于 Alpha 阶段。** 已有双设备连接、受监护萃取和手动停止的实机记录；目标重量自动停止、设置写入与异常恢复尚未完成全面验收。试用时请在机器旁看守，并保留机器本身的停止手段。详见[实机验收记录](docs/alpha-acceptance.md)。

## 免责声明与使用风险

OpenHOYI 是个人开发的开源实验项目，与咖啡机制造商无关联，也未获得其认证。本应用会通过蓝牙向咖啡机发送控制指令；软件缺陷、协议理解偏差或设备兼容性问题，可能导致控制异常、设备无法使用，甚至设备损坏。

Debug / Alpha 版本仅供了解风险的用户自行评估和试用，并非经过完整验证的正式产品，不保证功能正确、持续可用或适用于你的设备。出现异常时，请停止使用并核对机器状态，不要反复发送控制指令。

在适用法律允许的最大范围内，本项目及其贡献者按“现状”提供软件，不作任何明示或默示保证，也不对使用或无法使用本项目造成的设备不可用、损坏、数据丢失或其他损失承担责任。本声明不排除或限制适用法律规定不得排除或限制的责任。完整许可条件见 [LICENSE](LICENSE)。

## 可以做什么

- **连接设备**：扫描并连接咖啡机和电子秤，查看连接状态、实时读数与机器告警。
- **选择曲线**：浏览 100 条工厂曲线与 3 条已采集曲线，按分类或名称查找，设置首页的 5 个快捷槽位。
- **观察萃取**：在实时图表中查看压力、温度、机器水流、秤流速与重量；启动前确认，萃取中可手动请求停止。
- **管理机器**：查看和调整温度、加热、照明、运行模式、拨杆模式与睡眠计划；写入后等待机器回报确认。
- **回看记录**：离线查看萃取历史与采样曲线，导出历史 ZIP；手动导入的旧版历史可单独只读查看。
- **排查问题**：导出传输与操作日志，帮助定位连接、协议和设备状态问题。

以上为已实现的功能，实际可用范围取决于设备及其验证进度。详见[功能状态](docs/native-feature-status.md)与[覆盖矩阵](docs/coverage.md)。

## 设备与兼容性

| 项目 | 当前范围 |
|---|---|
| 手机 / 平板 | 支持 BLE 的 Android 8.0（API 26）及以上设备 |
| 咖啡机 | HOYI；控制功能仅对已采集验证的固件 **1.1.3** 开放 |
| 电子秤 | BOOKOO、TIMEMORE、Felicita 的指定协议，详见下表 |
| 萃取曲线 | 可启动已校验的工厂曲线与采集曲线；导入的个人曲线暂只读 |
| 其他平台 | 目前仅提供 Android 应用 |

其他咖啡机固件处于只读或不支持范围，请勿据此推断兼容。

### 电子秤适配情况

“已接入”表示应用具备相应协议实现，**不等于该品牌全系列、全部固件均已通过实机验证**。

| 品牌 / 型号 | 接入状态 | 可用功能 | 验证进度与限制 |
|---|---|---|---|
| **BOOKOO**（当前已采集协议） | 已接入，有实机记录 | 重量、设备流速、去皮 | 已有双设备持续收数与去皮后归零通知记录；负重量、断线恢复及目标重量自动停止仍待完整验收 |
| **TIMEMORE DOT（TES017）** | 按官方公开协议接入，待实机验证 | 重量、设备流速、电量、手动去皮 | 连接后先确认克单位；暂不开放目标重量联动、自动去皮与称豆取粉量 |
| **TIMEMORE Basic3 Link（TES016）** | 按官方公开协议接入，待实机验证 | 重量、设备流速、电量、手动去皮 | 与 DOT 共用公开协议，仍需逐型号、逐固件验证；功能限制同上 |
| **旧款 TIMEMORE**（广播名 `timemore scale`） | 实验性只读，待实机验证 | 双通道重量读数 | 仅接入指定的九字节通知格式；不发送初始化、心跳或去皮命令，部分固件可能无法持续出数 |
| **Felicita**（FFE0 / FFE1 ASCII 通知协议） | 实验性只读，待实机验证 | 克单位重量读数 | 仅订阅读数，不发送控制命令；未按商业型号逐一确认兼容 |

BOOKOO 现有验收记录未细分商业型号，不作为全系列兼容承诺。旧款 TIMEMORE 的两个通道不预设物理含义，也不能据此认定所有 Black Mirror 2 / DUO 均兼容。上述读数是否出现，还取决于实际广播名、GATT 服务与通知格式是否匹配。

**尚未适配的设备**：Acaia、DiFluid 目前只有离线协议候选，尚不能在应用中连接；未列出的泰摩 Basic4 等型号也不在已适配范围。没有蓝牙能力的 Basic2、Nano、Mini 等设备无法通过软件获得蓝牙连接能力。

### 协议来源与连接说明

泰摩 DOT / Basic3 Link 的实现依据官方公开的 **[open-scale-protocol](https://github.com/TIMEMORE-COFFEE/open-scale-protocol)**，采用 [Black Mirror SCALE BLE Protocol v1.0.3](https://github.com/TIMEMORE-COFFEE/open-scale-protocol/blob/e31dce5087494ea3d572a89e689e48dc014eee2c/Timemore%20Black%20Mirror%20SCALE%20BLE%20Protocol.md)。感谢泰摩公开协议；OpenHOYI 为独立实现，并非厂商认证产品。协议仓库未声明独立开源许可证，此处仅链接引用，不转载全文。

连接前，请按电子秤说明进入可发现或配对状态，并断开其他 App。应用只识别明确的设备名称与协议，不会仅凭通用蓝牙服务推断型号。遇到未识别的新固件，可提交脱敏广播、GATT 信息及型号 / 固件版本，帮助补充适配。

<details>
<summary>开发者说明：泰摩协议细节与验证边界</summary>

- **新协议**：使用 FFF0 服务、FFF1 通知与 FFF2 无响应写入。重量按 0.1 g 转换；CRC 使用初值 `FFFF`、反射多项式 `A001` 和大端尾码。官方重量章节未明确缩放比例，CRC 也缺少完整向量；具体解释参考 [MIT 社区实现](https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/dot.cpp)，仍需与真实读数及数据包对照。
- **单位与有效读数**：连接后先设为克并读取单位，收到克单位确认后才接收重量。单位变为 oz 或未知时，立即停用该重量读数；每次连接重新确认，不跨设备复用解码缓冲。仅通知帧允许部分固件保留的零 CRC；非零 CRC 校验失败、异常长度、溢出和过载均不作为有效重量。
- **去皮与写入**：单位设置成功应答不等于单位读回；去皮写入成功也不等于归零，仍需协议接受与后续有效零重量读数。应答缺失、超时或断线时，操作结果保留为未知，不自动重发。不远程启动秤计时、不更改工作模式、不恢复出厂或清除配对。应用本地计时独立运行。
- **旧款协议**：使用 181D / 2A9D，严格接收九字节双 `int32LE` 格式，第二通道作为主重量，另一通道另行显示。格式参考 [MIT 社区实现](https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/timemore.cpp)；社区实现存在 `int16` / `int32` 分歧，本项目不混用有分歧的格式。
- **待验证项**：所有泰摩适配均尚未完成实机验收，包括型号与固件、正负重量及流速对照、单位、去皮实际动作、断线重连和旧款双通道含义。离线测试与构建不代替这些验证，目标重量启动 / 自动停水、自动去皮及称豆取粉量继续关闭。

</details>

## 选择哪个应用

仓库提供 Alpha 与 Lab 两个应用，共用协议和蓝牙库；Alpha 另有 Mock 构建，可用于离线体验。

| 应用 / 构建 | 用途 | Android 包名 |
|---|---|---|
| **OpenHOYI Alpha（Debug）** | 连接真实设备，试用曲线、萃取与历史等功能 | `io.openhoyi.mobile` |
| **Mock** | 使用模拟设备，体验功能与调试界面 | `io.openhoyi.mobile.mock` |
| **OpenHOYI Lab** | 开发调试、协议采集与连接诊断 | `io.openhoyi.lab` |

这些应用可与旧版 HOYI App 并存，但连接同一台设备前，请先断开其他 App。新应用不会直接读取旧 App 的私有数据；数据迁移需手动导出、导入。Mock 结果不代表真实设备验收通过。

## 从源码构建

准备 **JDK 17** 和 **Android SDK 35**。仓库自带 Gradle Wrapper，无需另装 Gradle。

```sh
git clone --branch develop https://github.com/boolean93/openhoyi.git
cd openhoyi
```

使用 Android Studio 打开项目并配置 SDK，或设置 `ANDROID_HOME`。也可在本地 `local.properties` 中填写 `sdk.dir`，不要将该文件提交到仓库。

按需选择构建目标：

| 构建 | 命令 | APK 路径 |
|---|---|---|
| Alpha（Debug） | `./gradlew :mobile:assembleDebug` | `mobile/build/outputs/apk/debug/mobile-debug.apk` |
| Mock | `./gradlew :mobile:assembleMock` | `mobile/build/outputs/apk/mock/mobile-mock.apk` |
| Lab | `./gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |

若无法访问 Google Maven，可在命令后添加 `-PgoogleMirror=aliyun`，使用可选镜像。

### 安装与首次连接

开启 Android 设备的 USB 调试，连接电脑后安装 Alpha：

```sh
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

覆盖安装前，请确认咖啡机空闲、签名与原安装一致，并在构建时通过 `-PhoyiVersionCode=<更大的版本码>` 指定递增版本。不要通过卸载、清空历史或降级来绕过安装冲突。平板同时使用 Debug 与 Mock 时，两包应从同一源码以相同版本构建并同步更新。

1. 打开 OpenHOYI Alpha，开启蓝牙并授予扫描、连接所需权限。
2. 确认咖啡机空闲，且其他 App 已断开连接，再扫描并选择设备。
3. 按提示输入咖啡机的六位设备密码；电子秤单独连接。
4. 等待设备就绪、实时读数更新，再选择曲线或操作机器。

未收到或已过期的数据不作为有效读数；断线状态不能作为控制成功的依据。若操作结果未知，请先核对机器实际状态，不要反复发送命令。

本地构建默认使用 debug 签名。正式签名与升级要求见[安装包分发说明](docs/plans/apk-distribution.md)。

## 开发与贡献

欢迎通过 [Issues](https://github.com/boolean93/openhoyi/issues) 反馈问题或讨论功能。报告设备问题时，请附上应用版本、Android 版本、设备型号与固件、复现步骤及脱敏日志；不要上传设备密码或其他个人信息。

开发从 `develop` 创建短分支，通过 PR 合入 `develop`。分支、验证与发布规则见[贡献工作流](docs/git-workflow.md)。

### 项目结构

| 模块 | 职责 |
|---|---|
| `mobile` | Alpha 应用及 Mock 构建：连接、曲线、萃取、历史与设置 |
| `app` | Lab 应用：诊断、采集与日志导出 |
| `protocol-core` | HOYI / BOOKOO / TIMEMORE 协议编解码及其他秤离线候选，无 Android 依赖 |
| `device-session` | 设备会话、命令队列、重连与控制状态机，无 Android 依赖 |
| `bluetooth-android` | Android BLE 扫描、GATT 与设备会话桥接 |
| `trace-core` | 两个应用共用的日志与 ZIP 导出，无 Android 依赖 |

设计细节见[架构说明](docs/architecture.md)。

### 运行检查

```sh
./gradlew :protocol-core:check :device-session:check \
  :bluetooth-android:assembleDebug :bluetooth-android:lintDebug \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug
```

离线测试与构建不能替代实机验收。涉及启动、停止、去皮或设置写入的改动，需要记录机器回报及实际动作；GATT 写入成功本身不代表机器已执行。

## 更多文档

- [Alpha 实机验收](docs/alpha-acceptance.md)：已验证场景、已知问题与待验收项。
- [功能状态](docs/native-feature-status.md) · [覆盖矩阵](docs/coverage.md)：实现范围、证据与缺口。
- [Lab 使用说明](docs/native-lab.md)：诊断应用、日志采集与手工验证。
- [旧版曲线迁移](docs/legacy-curve-import.md)：导出与只读导入的范围。
- [安装包分发](docs/plans/apk-distribution.md)：签名、版本、升级与分发校验。

## 许可证

项目使用 [GNU GPL v2](LICENSE) 许可证。使用、修改或分发前请阅读许可证全文。
