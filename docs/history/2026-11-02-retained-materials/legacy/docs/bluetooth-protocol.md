# 蓝牙协议

本文依据当前 `app-service.js` 的编码和解码逻辑整理，不是厂商协议原稿。表中“已确认”表示App明确读写该字段；未被代码使用的字节仍需抓包确认。

## 总体连接模型

咖啡机与电子秤都使用 Android BLE 和 `uni.*` 接口。扫描、连接和通知入口共享，但 `common/ble-gatt-dispatch.js` 只注册一次全局 `onBLECharacteristicValueChange`，再按规范化后的 `deviceId` 分流：

- 等于 `globalData.bleMac`：咖啡机报文，写入 `bleData`，触发 `msg`。
- 等于 `globalData.scaleMac`：电子秤报文，交给 `processScaleNotifyHex()`。

这意味着机器和秤的MAC/设备ID不能相同，断线清理也必须保持两个状态独立。

## HOYI咖啡机

### 发现与GATT

扫描名称包含 `HOYI` 的设备。Android 12及以上请求 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`；Android 6–11请求位置权限。

| 类型 | UUID |
|---|---|
| Primary service | `55535343-FE7D-4AE5-8FA9-9FAFD205E455` |
| Write characteristic | `49535343-8841-43F4-A8D4-ECBE34729BB3` |
| Notify characteristic | `49535343-1E4D-4BD9-BA61-23C647249616` |

代码优先从扫描结果按忽略大小写和连字符的方式匹配UUID，找不到时回退到上述常量。连接超时为22秒；连接成功约2秒后发现服务；通知订阅最多重试2次。设备列表收到 `0x83` 初始化帧后才进入首页。

### 接收报文

报文在App中先转成十六进制字符串，再按字节数组解析。没有统一的长度字段或包头；byte 0就是消息类型。

#### `0x83` 初始化/设置

| 字节 | 已确认含义 |
|---|---|
| 0 | `0x83` |
| 1 | 软件主版本；小于64时按常规初始化解析 |
| 2 | 高4位次版本，低4位修订版本 |
| 3 | bit7压力模式、bit6流量模式、bit5萃取加热、bit4蒸汽加热、bit3灯、bit2运行模式、bit1供水模式、bit0睡眠计划 |
| 4 | 萃取设定温度，℃ |
| 5 | 萃取温差补偿，×0.1℃ |
| 6 | 蒸汽设定温度，℃ |
| 7 | 待机分钟档：0/15/30/60/120 |
| 8 | 待机温度，℃ |
| 9–10 | 萃取杯数，大端16位 |
| 后续扩展 | 压力限制、滤芯等状态；按固件版本和帧长兼容解析 |

`0xA1` 也可触发列表页进入首页，主要与OTA/设备状态路径有关。

#### `0x40` 待机实时状态

| 字节 | 已确认含义 |
|---|---|
| 0 | `0x40` |
| 2–3 | 萃取温度，大端值 ÷100℃ |
| 4–5 | 蒸汽温度，大端值 ÷100℃ |
| 6 | 萃取压力 ÷10 bar |
| 7 | 蒸汽压力 ÷10 bar |
| 12及扩展位 | 手动拉杆/状态遥测；不同固件长度和含义存在差异 |

#### `0x80` 萃取实时状态

| 字节 | 已确认含义 |
|---|---|
| 0 | `0x80` |
| 2 | 阶段或曲线槽位；6/7用于特殊手动状态 |
| 3–4 | 萃取秒数，大端16位 |
| 5 | 萃取压力 ÷10 bar |
| 6–7 | 累计水量，大端值 ÷10 ml |
| 8–9 | 萃取温度，大端值 ÷100℃ |
| 10 | 当前水流 ÷10 ml/s |
| 11 | bit6阀门打开、bit5等待萃取、bit4压力跟随、低4位曲线模式 |
| 12 | 手动拉杆等级或百分比；由阶段决定，`0xff`无效 |

App综合秒数、累计水量、压力、水流和byte 11判断萃取是否活跃。停止发送活跃帧约450 ms后结束一次历史记录。

### 发送命令

短命令通常是5字节，长命令包含参数并常用末字节异或校验。不要假设所有5字节的最后一位都是校验：当前代码中部分为固定0。

| 命令 | 方法 | 用途 |
|---|---|---|
| `0x01` | `checkTime` | 校时并携带6位BLE密码，15字节异或校验 |
| `0x02` | `startChart` / `startTempChart` | 开始、停止或下发20字节曲线 |
| `0x03` | `setPress` | 压力/流量模式开关 |
| `0x04` | `setTemp` | 温度相关16位参数 |
| `0x05` | `setBlewTemp` | 萃取温度 |
| `0x06` | `setSteamTemp` | 蒸汽温度 |
| `0x07` | `setSteamShift` | 蒸汽档位参数 |
| `0x08` | `copy2Hoyi` / `setChart` | 写入HOYI曲线参数 |
| `0x09` | `setSleepTime` / `setSleepArrTime` | 睡眠计划 |
| `0x0A` | `rstCups` | 杯数清零 |
| `0x0B` | `setBleConPwd` | 设置/校验6位BLE密码 |
| `0x0C` | `setTempPowerEn` | 萃取加热开关 |
| `0x0D` | `setSteamPowerEn` | 蒸汽加热开关 |
| `0x0E` | `setLedEn` | Barista灯开关 |
| `0x0F` | `setRunMode` | 运行模式 |
| `0x10` | `setWaterInMode` | 水源模式 |
| `0x11` | `setBrewWait` | 萃取等待参数 |
| `0x12` | `setWaterMode` | 排水/供水模式参数 |
| `0x13` | `setWaterStart` | 启停排水/供水流程 |
| `0x14` | `setStandby` | 待机时间和温度 |
| `0x15` | `setSleepEn` | 睡眠计划总开关 |
| `0x16` | `rstZero` | 拉杆零点校准 |
| `0x17` | `setFactory` / `ignoreAlarm` | 恢复出厂或忽略报警，按payload区分 |
| `0x18` | `setTankFilterEn` | 水箱滤芯状态 |
| `0x20` | `sleepStart` | 立即休眠 |
| `0x21` | `setBrewEncourageLed` | 萃取提示灯 |
| `0xA0` | `sendOtaCmd` | 进入OTA，载荷为ASCII `HOYI` |
| `0x80 0xAA` | `sendOtaFileLen` | OTA文件长度 |
| `0x80 0xAB` | `sendRstApp` | OTA后重启应用区 |

OTA数据块前两字节是块序号，后接最多17字节固件内容，末尾异或校验。接收端使用 `0xA0–0xA5` 系列应答推进状态机。

## 电子秤

### 前台自动重连

首页显示时会从 `sacleMac`、`sacleName` 读取最近一次成功连接的秤。在随后10分钟内，每15秒检查一次连接条件，并复用首页按钮的 `reConnectScale()` 与 `ConnectScaleBle()` 路径。

- 咖啡机连接后才发起秤连接，避免两条BLE初始化流程同时抢占状态。
- 单次连接超过12秒会主动关闭该BLE链路，并让迟到回调失效；关闭完成后下一轮才能重试。
- `scaleSta` 只在服务、特征和通知订阅完成后设为已连接。
- 每次连接带有代次标记；取消后的旧 transport、服务、特征和通知回调会被丢弃。
- 同一时间只发起一次秤连接。
- 连接成功、首页隐藏/卸载或10分钟到期后停止轮询。
- 没有已保存的秤名称和MAC时不创建轮询定时器。

真机排查时使用固定应用 tag 和 Android BLE tag，避免读取无关日志：

```sh
adb logcat -v threadtime HOYI_SCALE_AUTO:I BluetoothAdapter:D BluetoothLeScanner:D BluetoothGatt:D '*:S'
```

`HOYI_SCALE_AUTO` 的 `START` / `TICK` 表示窗口启动与15秒轮询，`CONNECT_REQUEST` / `CONNECTED` / `CONNECT_FAILED` / `TIMEOUT` 表示单次连接结果。`BluetoothAdapter.startLeScan()` 与 `BluetoothLeScanner` 表示扫描；`BluetoothGatt.connect()`、`discoverServices()`、`setCharacteristicNotification()`依次表示发起连接、发现服务和订阅通知。自动重连直接使用已保存的MAC，不会重新扫描。

### 支持的名称与GATT

| 内部类型 | 扫描名称 | Service | Write/Notify |
|---|---|---|---|
| `A` | `PROCHBT`、`ACAIA` | `00001820-...` | `00002a80-...` |
| `AP` | `PEARL`、`LUNAR`、`PYXIS` | `49535343-fe7d-...` | write `...8841...`，notify `...1e4d...` |
| `F` | `FELICITA` | `0000ffe0-...` | `0000ffe1-...` |
| `D` | `Microbalance`、名称含 `difluid`、`Mb Ti` | `000000EE-...` 或 `000000DD-...` | 目标 `0000AA01-...`，按特征能力回退，写入可选 `AA02` |
| `BOOKOO` | `BOOKOO`、`ULTRA` | `00000ffe-...` | write `0000ff12-...`，notify `0000ff11-...` |

UUID省略部分均为 `0000-1000-8000-00805f9b34fb` 或表内完整厂商UUID后缀。代码会根据实际特征的notify/write能力回退，不完全依赖固定UUID。连接后优先设置MTU 247，失败再尝试185。

### 数据解析

- Acaia `A/AP`：`EF DD` 帧；event type 5是重量，payload按精度位和符号位换算。
- Felicita `F`：通知前2字节作为无符号16位重量，÷100 g。
- DiFluid/Microbalance `D`：兼容多种 `DF DF` 格式、大小端16位和扩展32位重量，过滤命令回显并限制合理范围。
- BOOKOO：20字节 `03 0B` 帧；byte 6为重量符号，bytes 7–9为重量÷100 g；byte 10为流速符号，bytes 11–12为流速÷100 ml/s；byte 19为异或校验。

秤数据写入 `scaleWeight` 和 `scaleFlow`，并触发 `scalemsg`。萃取开始时记录重量基线，目标杯重使用相对重量和 `wdelta` 补偿。

### 控制命令

各类型初始化和去皮命令不同：Acaia使用 `EF DD` 协议；Felicita去皮是单字节 `0x54`；D类用 `DF DF` 7字节命令开启通知、设置单位和去皮；BOOKOO用6字节 `03 0A` 命令设置蜂鸣器、自动关机、去皮/计时和流速平滑。

## 修改与验证规则

协议修改优先放入可独立测试的纯函数，保留真实通知样本作为fixture。至少验证：短帧、断包、无效校验、负重量、不同固件长度、设备同时连接、断线重连和命令重复发送。UI Mock只能验证App数据流，不能证明真实GATT时序和固件兼容。
