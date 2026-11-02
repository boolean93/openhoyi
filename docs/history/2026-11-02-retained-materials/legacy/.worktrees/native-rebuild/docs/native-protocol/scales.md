# 电子秤 BLE 协议

## 1. 设备识别

旧应用按广播名包含关系选择协议，匹配大多区分大小写；只有 `difluid` 使用小写匹配。Native 版建议保留原始广播名并统一做不区分大小写匹配，同时记录最终选择的 adapter。

| Adapter | 广播名关键字 | Service | Write | Notify |
|---|---|---|---|---|
| Acaia A | `PROCHBT`, `ACAIA` | `00001820-0000-1000-8000-00805F9B34FB` | `00002A80-0000-1000-8000-00805F9B34FB` | 同 Write |
| Acaia AP | `PEARL`, `LUNAR`, `PYXIS` | `49535343-FE7D-4AE5-8FA9-9FAFD205E455` | `49535343-8841-43F4-A8D4-ECBE34729BB3` | `49535343-1E4D-4BD9-BA61-23C647249616` |
| Felicita | `FELICITA` | `0000FFE0-0000-1000-8000-00805F9B34FB` | `0000FFE1-0000-1000-8000-00805F9B34FB` | 同 Write |
| DiFluid/Microbalance | `Microbalance`, `difluid` | 优先 `000000EE-...` | 优先 `0000AA02-...`，回退 `AA01` | 优先 `0000AA01-...` |
| Mb Ti | `Mb Ti` | 优先 `000000DD-...` | 优先 `AA02`，回退 `AA01` | 优先 `AA01` |
| BOOKOO | `BOOKOO`, `ULTRA` | `00000FFE-0000-1000-8000-00805F9B34FB` | `0000FF12-0000-1000-8000-00805F9B34FB` | `0000FF11-0000-1000-8000-00805F9B34FB` |

DiFluid 类会同时检查 `DD`、`EE` 服务，优先选择兼有 notify 和 write 的组合。写入遇到 Android/uni 错误码 10007 或 10005 时，旧应用轮换 `DD/EE × AA02/AA01` 重试。Native 版应基于特征 properties 选择，而不是依赖固定顺序。

## 2. 通用连接流程

**C1 已确认**

1. 扫描支持的广播名，扫描窗口 5 秒。
2. 建立 GATT 连接。
3. 延迟 2 秒。
4. 请求 MTU 247；失败再请求 185。MTU 失败不阻塞服务发现。
5. 发现服务和特征，订阅 notify；订阅有两次重试。
6. notify 订阅成功后标记已连接。
7. 按 adapter 的时间表发送初始化命令。

第 6、7 步存在旧实现竞态：UI 会先看到“已连接”，此时秤可能还没有开始推送重量。Native 版应增加 `Initializing`，收到首个有效重量帧后再进入 `Ready`。

## 3. Acaia A / AP

### 3.1 接收重量

期望帧头为 `EF DD`。旧 decoder 只处理 `byte2 == 0x0C`，事件类型在 byte4；事件类型 `0x05` 时，从 byte5 开始解析 payload：

| payload 偏移 | 含义 |
|---:|---|
| 0..1 | 重量绝对值，`u16 LE` |
| 4 | 小数位：1→÷10，2→÷100，3→÷1000，4→÷10000 |
| 5 bit1 | 负号 |

旧应用未验证 Acaia 的双校验字节，标为 **C2**。Native 版应保留原始帧并在获得实机样本后补全校验。

### 3.2 初始化与控制

| 时机 | A | AP |
|---:|---|---|
| 500 ms / 2500 ms | `EF DD 0B` + 15 个 `2D` + `68 3B` | `EF DD 0B` + ASCII `012345678901234` + `9A 6D` |
| 1000 ms / 5500 ms | `EF DD 0C 09 00 01 01 02 02 05 03 04 15 06` | `EF DD 0C 09 00 01 01 02 02 01 03 04 11 06` |
| 1200 ms / 5000 ms | `EF DD 00 02 00 02 00` | 同左 |

A 型去皮：`EF DD 04` + 15 个 `00` + `04 00`，共 20 字节。旧应用没有为 AP 返回去皮命令；这可能是遗漏，不能假设 AP 不支持去皮。

## 4. Felicita

- 接收：取通知前 2 字节为 `u16 BE`，除以 100 得克数。
- 去皮：写单字节 `54`。
- 旧应用没有帧头、符号和校验检查，均为 **C3**。必须用正重量、负重量和稳定/变化状态抓包。

## 5. DiFluid / Microbalance / Mb Ti

### 5.1 初始化与控制

按 500 ms 间隔发送：

| 操作 | 命令 |
|---|---|
| 开启通知/数据 | `DF DF 01 00 01 01 C1` |
| 设置单位 | `DF DF 01 04 01 00 C4` |
| 去皮 | `DF DF 03 02 01 01 C5` |

旧应用会忽略前两条命令的原样回显，也忽略任意 7 字节 `DF DF 01 ...` 帧。

### 5.2 重量解析

高可信路径：帧长至少 9，头为 `DF DF 03`，且 byte5 的最高位为 0：

```text
raw = u32 BE(bytes5..8)
weightGrams = raw × 0.1
```

旧应用只接受 `-500..6000 g`。

其余 `DF DF` 帧使用启发式：从多个候选偏移读取有符号 16 位，先小端再大端，除以 100，取第一个落在范围内的值。最后甚至会回退到十六进制字符串第 2～5 字符。这部分为 **C3**，容易把状态或校验字节误当重量。Native 首版只能实现已经被真实样本覆盖的帧型；未知帧应记录并丢弃。

## 6. BOOKOO / ULTRA

### 6.1 重量通知

有效帧至少 20 字节：

- byte0=`0x03`，byte1=`0x0B`。
- `xor(bytes0..18) == byte19`。
- byte6=`1` 表示负重量。
- bytes7..9 为 `u24 BE`，除以 100 得克数。
- byte10=`1` 表示负流速。
- bytes11..12 为 `u16 BE`，除以 100 得 `ml/s`。

这是当前四类秤中字段和校验最完整的一类，可信度 **C1**。

### 6.2 初始化与控制

所有命令 6 字节，最后一字节为前 5 字节 XOR：

| 时机 | 操作 | 命令 |
|---:|---|---|
| 500 ms | 蜂鸣器设置 | `03 0A 02 00 03 08` |
| 1000 ms | 自动关机 | `03 0A 03 00 14 1E` |
| 1500 ms | 去皮/计时初始化 | `03 0A 07 00 00 0E` |
| 2000 ms | 流速平滑 | `03 0A 08 01 00 00` |
| 用户去皮 | 去皮 | `03 0A 01 00 00 08` |

## 7. Native 适配器契约

每种秤实现独立 adapter：

```kotlin
interface ScaleProtocol {
    val id: ScaleProtocolId
    fun matches(advertisedName: String): Boolean
    fun selectGatt(services: List<GattService>): GattSelection?
    fun initializationCommands(): List<TimedCommand>
    fun decodeNotification(bytes: ByteArray): ScaleSample?
    fun tareCommand(): ByteArray?
}

data class ScaleSample(
    val weightGrams: Double,
    val flowMlPerSecond: Double?,
    val raw: ByteArray,
)
```

解析失败返回 `null` 并带诊断事件，不能沿用旧应用“无效帧也发 scale message”的行为。重量有效性、去皮和萃取停止条件放在业务层，不放在 adapter 中。

## 8. 必须补的抓包矩阵

每种可用秤至少记录：连接初始化、静止零点、依次加 10/100/500 g、去皮、去皮后负重量、持续变化 10 秒、断线重连。Acaia 需验证校验和 AP 去皮；Felicita 需确认符号与帧长；DiFluid 必须按具体型号建立确定的帧型，不能保留启发式解析。

