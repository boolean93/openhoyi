# Lunar 连接排查：官方 Acaia Android SDK 对照

> 2026-11-12 更新：收到用户诊断包，已证实这次故障是 17 字节组合重量帧被拒绝，并非 GATT 连接失败。下面的“现场未知”是 11 月 11 日调研时状态；新证据及本轮验证见 [诊断回放](../plans/2026-11-12-lunar-diagnostic-replay.md)。

## 范围与结论

- OpenHOYI 基线：`develop@e7c7ac8aab0cd1accc661c91a6f664dfa0e8a312`，本轮核对时最新公开测试版为 `dev-82`。
- 官方 SDK 固定提交：[8dc977e8123eb5e13b5370b3f357e6af082c79e0](https://github.com/acaia/acaia_sdk_android/tree/8dc977e8123eb5e13b5370b3f357e6af082c79e0)。
- 用户现场 APK 版本、Lunar 年款、固件及失败日志未确认。没有实秤验收，不将离线复现等同用户现场根因。
- 官方仓库公开的是示例 App 及二进制 AAR，不是底层协议源码。公开 API 可以核对功能契约，不能用来证明原始通知的包长、偏移、握手时序或重试规则。
- 本轮没有下载、解码、反编译或运行官方 AAR，没有引入 SDK 依赖。

## 可直接核验的差异

| 项目 | 官方公开示例 | 当前 OpenHOYI |
|---|---|---|
| 扫描 | `ScaleCommunicationService.startScan()`，示例约 3 秒后展示发现的设备 | 自有 `ScanCoordinator` 扫描；广播名识别协议候选，默认手动扫描 5 秒 |
| 连接 | 按所选设备 MAC 发布 CONNECT 事件，由 SDK 服务处理 | 自有 GATT 串行队列；发现并校验唯一的精确写入/通知端点，再订阅、初始化 |
| 连接状态 | UI 订阅 `ScaleConnectStateEvent`，重量另走 `WeightEvent` | `DeviceSession` 在初始化后等待有效克重量，5 秒内没有读数则同步失败 |
| 重量与单位 | 重量广播有 `value` 与 `EXTRA_UNIT`，一起构造 `WeightEvent`；支持 g/oz 显示及单位设置 API | Acaia 原始 status 确认 `unit=2` 后才将重量作为克读数；其他单位、坏包撤销信任 |
| 单位数值 | `PearlSStatusEvent.unit` 使用 `2=g / 5=oz`；另一条 `EVENT_UNIT` 示例使用 `0=g` | 原始 status 的 `2` 不能依据另一 API 的归一化 `0` 修改 |
| 去皮与计时 | README 和示例提供 tare、timer start/pause/stop | Lunar/AP 当前未开放去皮、自动去皮与目标液重控制；不能宣称功能与 SDK 全面对齐 |
| 握手、订阅、心跳、恢复 | 底层在 AAR 内，公开示例没有定义端点、报文、时序或重试算法 | AP 固定端点；2500/5000/5500ms 初始化，4500ms 心跳；失败交给既有恢复策略 |
| Android 适配 | 示例 compile/target SDK 29，旧式蓝牙和位置权限流程 | compile SDK 35；Android 12+ 使用 BLUETOOTH_SCAN / CONNECT，旧版使用位置权限 |

官方示例把连接状态与重量事件分开消费，但 **SDK 内部在什么条件下发布连接成功事件不可见**，不能推断它绝不等待读数。示例的 tare/timer API 也不提供原始命令字节，不能直接据此打开当前协议控制能力。

## 17 字节缺陷的证据等级

已在未修复 OpenHOYI 基线的真实分类器、注册表、会话队列和解码器链路复现：合法校验的 17 字节重量加计时组合包，被 `length == 8` 的限制拒绝；克状态被撤销，后续普通重量无法就绪，重新收到克 status 才能恢复。

该格式依据不是官方 SDK：

1. [AcaiaArduinoBLE 固定提交 80e03977](https://github.com/tatemazer/AcaiaArduinoBLE/commit/80e03977a23fa8ecab340d0e69859f189e34768d) 记录 Lunar 2021 FW 1.0.016 附近重量包从 13 增为 17 字节，并记录相关实秤回归。
2. [acaia-lunar-ble 固定提交 eea7e7c0](https://github.com/beat843796/acaia-lunar-ble/blob/eea7e7c0b68bc4fc8070817f0c09809246e6d74b/acaia_lunar_ble/__init__.py) 声明实测 Lunar 2021 AL008；公开解码器按原六字节重量后附 timer tag `7` 和三个计时字节处理。
3. 本地原始 HOYI 1.2.6 APK（SHA-256 `26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238`）静态复核显示其 type-5 重量解码不要求整个事件恰好 13 字节。这是补充静态证据，不是官方 Acaia wire 文档，也没有运行 APK。

最小修复只增加 `length=12 && frame[11]=7` 的已知组合格式；保留完整帧长、checksum、六字节重量位置和克单位确认。不接受任意尾部，不推测新端点，不改变握手/心跳或控制门禁。验证命令、失败到通过的结果和回退范围见 [任务记录](../plans/2026-11-11-lunar-connection-check.md)。

## 官方来源与接入边界

- [README](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/README.md)：支持的型号、功能和约 5Hz 重量流。
- [AcaiaSDKSampleApp.java](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/app/src/main/java/co/acaia/android/acaiasdksampleapp/AcaiaSDKSampleApp.java)：初始化服务、重量与单位事件。
- [MainActivity.java](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/app/src/main/java/co/acaia/android/acaiasdksampleapp/MainActivity.java)：扫描、连接、单位、状态与去皮示例。
- [TimerActivity.java](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/app/src/main/java/co/acaia/android/acaiasdksampleapp/TimerActivity.java)：仅公开解码后的计时事件及控制 API，没有原始通知布局。
- [app/build.gradle](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/app/build.gradle)：AAR 依赖和示例 Android 版本。
- [LICENSE](https://github.com/acaia/acaia_sdk_android/blob/8dc977e8123eb5e13b5370b3f357e6af082c79e0/LICENSE)：允许使用未修改的 SDK 二进制和复用示例源码，但禁止二进制逆向/解码；另有署名、再许可/转售及付费 App 条件。

本项目根许可证为 GPLv2。接入闭源 SDK 的许可兼容、生命周期所有权、与现有咖啡机及其他秤连接的共存和真实硬件验证，需要单独评估；不能把导入 AAR 当成此问题的无风险修复。

## 最低成本现场验证

Acaia [官方连接排查说明](https://help.acaia.co/hc/en-us/articles/360034045371-Connectivity-Tips-and-Tricks) 指出，同一台秤一次只能维持一个 App 连接，后台 App 也可能占用。这是可排除的条件，不是本次已确定原因。

优先收集应用设置中的版本号截图；关闭其他可能占用秤的 App 后，在 OpenHOYI 尝试连接一次。若仍失败，立即通过应用设置的“导出诊断日志”提供 ZIP。当前基线会记录 GATT 请求、结果及通知帧，可据此区分连接阶段失败与读数同步失败。导出元数据没有 App 版本，不能省略版本截图；也不保证未知旧版具备相同诊断能力。

这一步不要求萃取、清数据、重新安装或升级秤固件。只有日志不足时再补 Lunar 年款、固件及对应 GATT 信息，避免让用户反复试错。
