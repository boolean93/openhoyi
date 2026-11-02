# Android Native 协议层边界

## 模块拆分

建议新工程先建立纯 Kotlin 协议模块，再接 Android BLE：

```text
:protocol-core
  coffee/         咖啡机帧 encoder/decoder，纯 ByteArray
  scale/          各电子秤 adapter，纯 ByteArray
  model/          原始协议模型，不含 UI 文案

:bluetooth-android
  CoffeeGattClient
  ScaleGattClient
  GattOperationQueue
  ScanCoordinator
  BluetoothPermissionController

:domain
  CoffeeMachineRepository
  ScaleRepository
  ExtractionSession
```

`:protocol-core` 不依赖 Android，可用 JVM 单元测试覆盖所有测试向量。`:bluetooth-android` 只负责权限、扫描、连接、服务发现、CCCD、MTU、串行读写与回调转发。业务层把协议事件组合成设备状态和萃取会话。

## 两条独立状态机

咖啡机：

```text
Disconnected -> Scanning -> Connecting -> Discovering
-> Subscribing -> AwaitingSettings83 -> Ready
```

电子秤：

```text
Disconnected -> Scanning -> Connecting -> Discovering
-> Subscribing -> Initializing -> AwaitingFirstSample -> Ready
```

每次连接生成 `generationId`。所有延时任务和 GATT 回调必须携带该 id；与当前连接不一致的回调直接丢弃，防止上一次连接的定时器污染新连接。

## Android GATT 规则

- 每个 `BluetoothGatt` 同一时刻只允许一个未完成操作。
- 写 characteristic、写 CCCD、请求 MTU、发现服务都经过串行队列。
- 以 Android callback 作为操作完成边界，不能用固定延时假装写入成功。
- 咖啡机和秤各有自己的队列，不能共享当前 `serviceId`、`writeId` 或 `deviceId`。
- 日志记录设备角色、连接 generation、opcode、长度和脱敏后的 hex。默认不要记录 BLE 密码。
- Android 12+ 使用 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`；旧版本按系统要求处理位置权限。

## 协议解析原则

1. 每个帧型先检查最小/精确长度，再读取偏移。
2. 明确使用 `ByteOrder.BIG_ENDIAN` 或 `LITTLE_ENDIAN`。
3. 原始测量值与 UI 补偿值分开。
4. 能验证 XOR/校验和的协议必须先验校验。
5. 未知 opcode、未知长度和非法范围形成诊断事件，不更新最后有效状态。
6. 不把 notification 边界天然视为协议边界；先通过抓包确认，再决定是否需要 accumulator。

## 建议的公开模型

```kotlin
sealed interface CoffeeFrame {
    data class Settings(/* raw settings */) : CoffeeFrame
    data class IdleTelemetry(/* temperatures, pressures, alarms */) : CoffeeFrame
    data class ExtractionTelemetry(/* time, pressure, flow, flags */) : CoffeeFrame
    data class OtaStatus(/* status */) : CoffeeFrame
    data class Unknown(val opcode: UByte, val bytes: ByteArray) : CoffeeFrame
}

sealed interface DeviceLinkState {
    data object Disconnected : DeviceLinkState
    data class Connecting(val device: DeviceIdentity) : DeviceLinkState
    data class Initializing(val step: String) : DeviceLinkState
    data class Ready(val device: DeviceIdentity) : DeviceLinkState
    data class Failed(val reason: LinkFailure) : DeviceLinkState
}
```

页面只能观察 repository 暴露的 `StateFlow`，不能直接调用 `BluetoothGatt`，也不能直接解释字节数组。

## 首轮实现顺序

1. `protocol-core`：实现 `0x83`、`0x40`、`0x80` 和现有秤 decoder，跑固定向量测试。
2. 只读连接：扫描、连接、订阅、记录原始帧，不发送设备设置命令。
3. 与旧应用并排抓包，逐字段对照。
4. 开放低风险写入：校时、温度、开关、去皮。
5. 实现曲线下发和会话状态机。
6. 最后单独实现 OTA。

这能让协议验证与 UI 重做解耦，后续 Compose 或 View 系统的选择不会影响 BLE 层。

