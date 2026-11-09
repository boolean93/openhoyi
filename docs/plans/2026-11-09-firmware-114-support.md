# 固件 1.1.4 放开控制 + 未知固件版本/初始化超时明确提示

- 需求与范围：
  - 用户反馈：朋友机器升级到固件 **1.1.4** 后能连上，但首页显示"不支持"。基线 `DeviceSession.acceptSettings` 只认固件整数 `0x113`（1.1.3），其余一律 `UNSUPPORTED`，因此 1.1.4 只读。
  - 用户已确认方向（2026-11-09）：先核对 1.1.4 与当前协议是否帧层一致；**一致就直接放开控制**。未来遇到不支持的固件必须有明确提示（含固件版本号 + "暂不支持新版固件"），初始化超时也要有明确文案，不能笼统"不支持"或无限卡住。
  - 用户已确认边界：只放开协议一致且证据足够的固件档案；控制入口继续只认 `READY`；不新增绕过路径；不在未做过硬件操作验收的情况下宣称"已验证"。
  - 范围：`device-session` 固件判定改为可扩展“有协议证据的可控制档案”（1.1.3 已有实机验收、1.1.4 协议证据支持放开待实机启停/设置验收，其余仍只读）+ 会话失败分类；`bluetooth-android` 透传失败分类；`mobile` 首页/任务壳文案（未知固件版本提示、初始化超时提示、认证阶段提示）；八语言文案同步；相关文档与站点兼容声明更新。不改协议编解码、不新增固件命令、不改 GATT 端点/认证帧、不新增自动重连放行、不新增任何控制绕过入口。
  - 不在范围：固件探测/升级/刷机；控制帧内容变更；`device_state_*` 枚举值增删（新增枚举值会波及大量穷尽 `when`，本次用旁路失败分类表达，见设计 B）；秤（BOOKOO/Timemore）行为。
- 来源分支/基线提交：`develop` @ `63e90bf8`（`feat(chart): 萃取曲线动态坐标轴与自适应细网格 (#31)`）。
- 工作分支：`feature/firmware-support`（工作树 `.agents/worktrees/feature-firmware-support`，已建）。
- PR 目标：`develop`。

## 证据依赖（必须先读，不得越过证据放开）

- 并行证据任务产出：`docs/evidence/2026-11-09-firmware-114-parity.md`（由 analyst 只读调查 1.1.4 固件镜像与旧版 App 协议编码）。本规划起草时该文件尚未生成，因此“放开范围”由该证据最终定稿，实现开始前必须先读它并按结论执行：
  - 结论为**帧层一致/差异不影响控制**：1.1.4 进入可控制档案，措辞固定为“协议证据支持放开，待实机启停/设置验收”。
  - 结论为**不一致或风险不可接受**：只放开证据覆盖的部分（例如仅遥测/设置回读），控制入口保持 1.1.3 单档案，并在本规划与交付中写清受限范围与理由；不得为了“能用”放开未验证命令。
  - 实现开工时证据文件仍不存在：**不放开 1.1.4 控制**，只实现设计 B/C/D（未知固件与超时提示），1.1.4 继续走未知固件提示路径，回报 Lead 等待证据。
- 实现者不得凭本规划或用户期望擅自越过证据放开；“放开范围”的最终措辞以证据文件结论为准。
- 证据性质声明：该文件是对固件二进制/旧 App 的静态对照，**不构成硬件操作验收**；启停、改设置、回读仍需真实 1.1.4 机器验证（见“风险与边界”）。

## 设计 A：可扩展固件档案（device-session）

现状：`device-session/src/main/kotlin/io/openhoyi/session/DeviceSession.kt:253-256`
```kotlin
private fun acceptSettings(frame:Settings){
    // First validated profile only; other firmware remains read-only.
    setState(if(frame.firmwareInteger==0x113)DeviceState.READY else DeviceState.UNSUPPORTED)
}
```

目标：把"单一白名单常量"改成"命名档案集合"，新增固件只加一行，不改控制门禁；档案带证据注释。

新增文件 `device-session/src/main/kotlin/io/openhoyi/session/CoffeeFirmwareProfiles.kt`：
```kotlin
package io.openhoyi.session

/**
 * Firmware profiles whose control frames have protocol evidence sufficient to enable control.
 * Membership never implies hardware acceptance: README/plans record what was verified on a real machine.
 */
object CoffeeFirmwareProfiles {
    /** 1.1.3: authenticated real-machine sessions, telemetry, extraction and settings writes verified. */
    const val FW_1_1_3 = 0x113
    /**
     * 1.1.4: protocol evidence supports enabling control (docs/evidence/2026-11-09-firmware-114-parity.md);
     * real-machine start/stop/settings acceptance is still pending, tracked in docs/firmware-status.md.
     * Never describe 1.1.4 as verified before that hardware acceptance is recorded.
     */
    const val FW_1_1_4 = 0x114

    private val controlVerified = setOf(FW_1_1_3, FW_1_1_4)

    fun permitsControl(firmwareInteger: Int): Boolean = firmwareInteger in controlVerified
}
```
`DeviceSession.acceptSettings` 改为：
```kotlin
private fun acceptSettings(frame:Settings){
    // Only profiles with protocol evidence may control; every other firmware stays read-only.
    setState(if(CoffeeFirmwareProfiles.permitsControl(frame.firmwareInteger))DeviceState.READY
        else DeviceState.UNSUPPORTED)
}
```
约束（复核点）：
- **不新增 `DeviceState` 取值**：`READY` 仍是唯一控制许可；`send()`、`canControlFromIdle/WithFreshSettings/WithBrewSettings`、`stopExtraction`、`extract` 各门禁一行不改，天然没有绕过路径。
- **不复制版本编码**：继续使用 `protocol-core` 的 `Settings.firmwareInteger`（`Protocol.kt:26`），不新增第二套 `major/minor/patch` 位拼接。
- 档案集合私有且只读；新增档案时必须在 KDoc 注释写证据来源（协议证据文件 + 实机验收状态），不得只写"支持"。
- `observedFirmware` 语义不变（`DeviceSession.kt:61-63`）：仅在认证阶段（`SYNCHRONIZING`）收到的 `0x83` 才填充；1.1.4 进入 `READY` 后沿用 App 既有"已验证协议"卡片文案（该措辞指协议档案匹配，不是硬件操作验收声明；文档与 PR 不得据此写"1.1.4 已验证"）。

## 设计 B：未知固件明确提示（含版本号，"暂不支持新版固件"）

现状：
- 会话：未知固件 → `UNSUPPORTED`，**保持 GATT 连接**（`queue` 仍 active），`lastSettings` 保留首帧设置 → `observedFirmware` 可用；后续 `0x83` 不再写入产品快照（`authenticated` 只含 `SYNCHRONIZING/READY`，`DeviceSession.kt:214-217`）；不发送任何控制帧（门禁只认 `READY`）。
- 自动重连：`NativeDeviceHub` 收到 `UNSUPPORTED` 调 `coffeeReconnect.disable()` → 不再自动重连咖啡机（与基线相同）。
- UI：`FirmwarePresentation.kt:10-16` → `firmware_unverified` "固件 1.1.4 · 未验证，控制已禁用"；首页状态行 `home_firmware_readonly` "固件未验证 · 只读"。

目标行为（明确写死）：
1. 读到 `0x83` 版本后状态保持 `UNSUPPORTED`，**保持连接不断开**（版本提示保持可见；不断开可避免反复重连刷屏），**不发送任何控制帧**（沿用现有门禁，不新增判断），**不自动重连**（沿用 `coffeeReconnect.disable()`，用户手动"连接/重试"仍可重新发起）。
2. 固件卡片文案改为含版本号的明确提示：`固件 1.1.4 · 暂不支持新版固件`。
3. 状态行文案改为 `暂不支持新版固件 · 控制已禁用`（保持简短，避免首页状态行换行；版本号在同卡片上一行）。
4. 不把该固件值送入正常控制快照（保持基线 `authenticated` 规则），不因显示版本产生新控制许可。

实现点：
- `mobile/src/main/kotlin/io/openhoyi/mobile/FirmwarePresentation.kt`：`firmware_unverified` → 新资源 `firmware_unsupported`。
- `mobile/src/main/kotlin/io/openhoyi/mobile/HomeActivity.kt:884`、`HomeFragment.kt:1012`：`UNSUPPORTED -> getString(R.string.home_firmware_readonly)` 文案值调整（`home_firmware_readonly` 新译文）。

## 设计 C：初始化超时提示（区分连接/认证失败）

现状：`INITIALIZING/SYNCHRONIZING` 超过 `stageDeadline` 时 `fail("protocol initialization timeout")`（`DeviceSession.kt:207`、`:270`），手机端只看到 `DeviceState.FAILED` → `device_state_failed`="连接失败"；故障原因文字只进 `diagnostic` 日志，没有用户可见区分。真实固件若在认证后不返回兼容 `0x83`，用户看到的就是无限"连接失败/不支持"，正是用户要求修掉的体验问题。

设计（最小、可离线测试）：新增旁路"失败分类"，不动 `DeviceState` 枚举。
- 新增文件 `device-session/src/main/kotlin/io/openhoyi/session/CoffeeSessionFailure.kt`：
```kotlin
package io.openhoyi.session
/** Why the current coffee session ended in FAILED; display-only, never a control permit. */
enum class CoffeeSessionFailure { CONNECT, AUTH, PROTOCOL_TIMEOUT, OTHER }
```
- `DeviceSession.kt`：
  - 顶层常量 `private const val PROTOCOL_TIMEOUT_REASON = "protocol initialization timeout"`，两处超时 `fail(PROTOCOL_TIMEOUT_REASON)` 复用。
  - 新属性 `var lastFailure: CoffeeSessionFailure? = null; private set`（语义：最近一次 `FAILED` 的分类；显示用，不参与任何门禁）。
  - `connect()` 重置连接时（`:153` 清空 `lastSettings` 处）一并 `lastFailure=null`。
  - `endConnection`（`:515`）在 `setState(terminal)` **之前**：`if(terminal!=DeviceState.FAILED) lastFailure=null`（保证 `READY/DISCONNECTED/UNSUPPORTED` 不残留旧分类，且观察者回调时已可读）。
  - `fail(reason)` 改为先分类再 `endConnection(FAILED, reason)`：
```kotlin
private fun classifyFailure(reason:String):CoffeeSessionFailure = when {
    reason==PROTOCOL_TIMEOUT_REASON -> CoffeeSessionFailure.PROTOCOL_TIMEOUT
    role==DeviceRole.COFFEE && state==DeviceState.CONNECTING -> CoffeeSessionFailure.CONNECT
    role==DeviceRole.COFFEE && state==DeviceState.INITIALIZING -> CoffeeSessionFailure.AUTH
    else -> CoffeeSessionFailure.OTHER   // 服务发现/订阅/队列超时；秤会话也归 OTHER 且无人读取
}
private fun fail(reason:String){
    connectionRequest++
    lastFailure=classifyFailure(reason)
    endConnection(DeviceState.FAILED,reason)
    diagnostic(reason)
}
```
  - 触发条件精确化（写进 KDoc 注释与测试）：
    - `PROTOCOL_TIMEOUT` = 连接/服务/订阅/认证写入均成功，但在 `SYNCHRONIZING` 的 10 s（`DeviceSession.kt:177`）内**没有收到认证后的有效 `0x83` 设置帧**，或 `INITIALIZING` 10 s 内未完成认证写入（`:174`）。含义：**可能是暂时不支持的新版固件**，也可能是链路干扰/机器忙；文案不下"密码错误"结论。
    - `AUTH` = 已订阅并发出认证帧后，认证写入阶段失败/超时；不代表密码错误（基线 `CoffeeCredentialRetryGate` 已明确"传输失败不能证明密码被拒"，本次保持该立场，不恢复密码失败计数）。
    - `CONNECT` = GATT 建链阶段失败/超时（含蓝牙环境问题）。
    - `OTHER` = 服务发现/订阅/队列超时/断连等；沿用现有 `连接失败` 文案。
- `bluetooth-android/src/main/kotlin/io/openhoyi/bluetooth/NativeDeviceHub.kt`：新增只读属性 `val coffeeFailure:CoffeeSessionFailure? get()=coffee.session.lastFailure`（与 `coffeeFirmware` 并列，`:110`）。
- `mobile/src/main/kotlin/io/openhoyi/mobile/MobileService.kt`：
  - `MobileSnapshot` 新增末位默认字段 `val coffeeFailure: CoffeeSessionFailure? = null`（`:56-79`；现有构造全部具名，安全）。
  - `onDeviceState`（`:489-560`）咖啡分支：`FAILED` 时 `coffeeFailure=hub?.coffeeFailure`，其余状态置 `null`（两处 `copy` 都要显式写，避免跨状态残留）。
  - 顺序保证：`DeviceSession.fail()` 在 `setState(FAILED)` 前写 `lastFailure`，`hub.onState` 同步读，不会读到上一条分类。
- UI 映射（`mobile/src/main/kotlin/io/openhoyi/mobile/DeviceStatusText.kt`）：
```kotlin
fun coffeeLabel(context: Context, state: DeviceState, failure: CoffeeSessionFailure?): String =
    context.getString(coffeeResource(state, failure))

internal fun coffeeResource(state: DeviceState, failure: CoffeeSessionFailure?): Int = when {
    state==DeviceState.FAILED && failure==CoffeeSessionFailure.PROTOCOL_TIMEOUT -> R.string.device_state_protocol_timeout
    state==DeviceState.FAILED && failure==CoffeeSessionFailure.AUTH -> R.string.device_state_auth_failed
    else -> resource(state)
}
```
  - `HomeActivity.kt:675`、`:884` 与 `HomeFragment.kt:775`、`:1012` 的咖啡机状态行改调 `coffeeLabel(s.coffeeState, s.coffeeFailure)`；秤状态行继续用 `label(s.scaleState)`。
  - `TaskShellDeviceStatus.coffee(...)` 增加 `failure: CoffeeSessionFailure?` 参数，`connection==FAILED` 时用 `DeviceStatusText.coffeeResource(connection, failure)`；`TaskShellStatusView.present`（`:236` 起）传 `snapshot.coffeeFailure`。
- 固件卡片（`FirmwarePresentation.describe`）在 `FAILED` 时优先展示超时解释：
```kotlin
fun describe(firmware:CoffeeFirmware?, state:DeviceState, mock:Boolean,
    failure:CoffeeSessionFailure? = null): String {
    if(state==DeviceState.FAILED && failure==CoffeeSessionFailure.PROTOCOL_TIMEOUT)
        return text(R.string.firmware_protocol_timeout)
    if(firmware==null || state !in setOf(DeviceState.READY,DeviceState.UNSUPPORTED))
        return text(R.string.home_firmware_initial)
    if(mock) return text(R.string.firmware_mock, firmware.toString())
    return if(state==DeviceState.READY) text(R.string.firmware_verified, firmware.toString())
        else text(R.string.firmware_unsupported, firmware.toString())
}
```
  - 调用点 `HomeActivity.kt:710`、`HomeFragment.kt:812` 传 `s.coffeeFailure`。
- 自动重连策略：**不变**。超时后仍按现有 `CoffeeReconnectPolicy` 退避重试（5 s→30 s）；提示在每次 `FAILED` 窗口可见。若验收认为闪烁不可接受，后续单独提案"连续 N 次协议超时后暂停自动重连"，不在本次悄悄改策略。

## 设计 D：文案与多语言（8 语言全同步）

规则依据 `localization/README.md`：中文源在 `mobile/src/main/res/values/strings.xml`；`catalog/source.json` 保存默认 XML 精确 SHA256 + 中文模板（963 键）；七份译文 catalog（`en/es/ja/ko/ru/th/ar`）必须**键集合完全一致**（`validate_catalog.py` 缺键/多键都报错），参数索引/类型/次数/换行一致；`values-*` 由 `android_resources.py` 生成，不得手改。**因此新增/修改键必须同时更新 8 份 catalog + 默认 XML，并刷新 source.json 哈希。**中文文案以用户确认措辞为准，七语言按现有术语风格翻译（如日文ファームウェア/制御、韩文펌웨어/제어），不得只留中文。

文案清单（键 × 中文源；其余七语言同键同参数）：

| 键 | 动作 | 中文源（values/strings.xml） | 说明 |
|---|---|---|---|
| `firmware_unsupported` | 新增（替换 `firmware_unverified`） | `固件 %1$s · 暂不支持新版固件` | 未知固件（含版本号） |
| `firmware_protocol_timeout` | 新增（无参数） | `固件版本读取超时 · 可能是暂不支持的新版固件` | 设备卡片的超时解释 |
| `device_state_protocol_timeout` | 新增（无参数） | `协议初始化超时` | 状态行短标签；含义见设计 C 触发条件 |
| `device_state_auth_failed` | 新增（无参数） | `认证未完成，请重试` | 明确不写"密码错误"（无法证明） |
| `firmware_unverified` | 删除 | — | 被 `firmware_unsupported` 取代，仓库内仅 `FirmwarePresentation` 与单测引用 |
| `home_firmware_readonly` | 改值 | `暂不支持新版固件 · 控制已禁用` | 状态行 UNSUPPORTED（保持短） |

- 不改：`firmware_verified`"固件 %1$s · 已验证协议"、`firmware_mock`、`home_firmware_initial`、`device_state_failed`"连接失败"。
- 同步步骤（实现顺序）：改默认 XML → 更新 `catalog/source.json`（模板 + `sha256`）→ 同步 7 份译文 catalog（键增删与参数一致）→ `validate_catalog.py` 通过 → `python3 localization/android_resources.py` 重新生成 `values-*` → `android_resources.py --check` 无漂移。
- 键数变化：963 → 966（+4 新键，-1 删除键）；实现者须以实际生成结果为准在 PR 记录键数与校验输出。
- 必须同步的对外表述（不得只改 App）：`README.md:31,34`（表格“控制功能仅对已采集验证的固件 1.1.3 开放 / 其他咖啡机固件只读或不支持”）、`site/index.html:143` 兼容声明、`docs/firmware-status.md`、`docs/protocol-safety-gates.md:9`、`docs/native-feature-status.md`。统一措辞：1.1.3 已实机验收；**1.1.4 协议证据支持放开，待实机启停/设置验收**；其他固件只读并明确提示。

## 改动文件与步骤（实现顺序）

1. `device-session/src/main/kotlin/io/openhoyi/session/CoffeeFirmwareProfiles.kt`（新增，设计 A）。
2. `device-session/src/main/kotlin/io/openhoyi/session/CoffeeSessionFailure.kt`（新增，设计 C）。
3. `device-session/src/main/kotlin/io/openhoyi/session/DeviceSession.kt`：`acceptSettings` 走档案；`lastFailure` 属性 + `PROTOCOL_TIMEOUT_REASON` + `classifyFailure` + `fail`/`connect`/`endConnection` 清理（设计 A/C）。**不碰任何控制门禁函数**。
4. `bluetooth-android/src/main/kotlin/io/openhoyi/bluetooth/NativeDeviceHub.kt`：`coffeeFailure` 只读透传。
5. `mobile/src/main/kotlin/io/openhoyi/mobile/MobileService.kt`：`MobileSnapshot.coffeeFailure` + `onDeviceState` 赋值。
6. `mobile/src/main/kotlin/io/openhoyi/mobile/DeviceStatusText.kt`：`coffeeLabel/coffeeResource`。
7. `mobile/src/main/kotlin/io/openhoyi/mobile/FirmwarePresentation.kt`：签名 + 超时/未知固件分支。
8. `mobile/src/main/kotlin/io/openhoyi/mobile/HomeActivity.kt`、`HomeFragment.kt`、`TaskShellStatusView.kt`、`TaskShellDeviceStatus.kt`：传参 + 文案键。
9. 文案：`mobile/src/main/res/values/strings.xml`、`localization/catalog/{source,en,es,ja,ko,ru,th,ar}.json`、生成 `values-*/strings.xml`（设计 D）。
10. 测试（精简，代表性）：
    - `device-session/src/test/kotlin/io/openhoyi/session/DeviceChecks.kt`：现有 `unsupported coffee firmware never opens control gate`（`:305`）改用 **1.1.5**（`830115...`）证明未知固件仍零写入；新增 1.1.4 用例（`830114...` → `READY`、`observedFirmware==1.1.4`，并允许控制路径）。现有 `NotificationOwnerTest.kt:83` 的 1.1.3 断言保持。
    - `device-session` 新增 `CoffeeSessionFailure` 分类用例（可放 `DeviceChecks.kt` 或新 `CoffeeFailureClassificationTest.kt`）：SYNCHRONIZING 无 `0x83` 超时 → `PROTOCOL_TIMEOUT`；Connect 失败 → `CONNECT`；认证写入失败 → `AUTH`；1.1.4 正常 → `lastFailure==null` 且 `READY`；断开/换设备清空。
    - `mobile/src/test/kotlin/io/openhoyi/mobile/FirmwarePresentationTest.kt`：更新 1.1.4=`固件 1.1.4 · 已验证协议`、未知= `固件 1.1.5 · 暂不支持新版固件`、FAILED+PROTOCOL_TIMEOUT=`固件版本读取超时 · 可能是暂不支持的新版固件`、FAILED 其它保持初始文案。
    - `mobile/src/test/kotlin/io/openhoyi/mobile/DeviceStatusTextTest.kt`：新增 `coffeeResource(FAILED, PROTOCOL_TIMEOUT)`/`(FAILED, AUTH)`/`(FAILED, null)` 断言（`resource(FAILED)` 保持 `连接失败`）。
    - `mobile/src/test/kotlin/io/openhoyi/mobile/LanguageCatalogTest.kt:14` 断言总键数 `963` → `966`；`mobile/src/test/kotlin/io/openhoyi/mobile/LanguageDraftTest.kt:41` 断言生成资源键数 `962` → `965`（`app_name` 排除）。两个键数必须与实际 catalog 一致，不得为通过测试删键。
11. 文档/站点：`README.md:31,34` 支持范围表（1.1.3 已采集验证；1.1.4 协议证据支持放开，待实机启停/设置验收）、`docs/firmware-status.md`（状态表与“已验证协议”边界）、`docs/protocol-safety-gates.md:9`、`docs/native-feature-status.md`、`site/index.html:143`、本规划。

## 推送与合并

检查工作树（`git status --short`，保留他人改动）→ `git fetch origin` → 本地最小验证（见下）→ 普通推送 `feature/firmware-support` → Lead 创建 PR（目标 `develop`，正文含本规划链接、验证证据、协议与设备风险、发布与回退）→ `branch-policy`、`verify` 两项必需检查通过 → PR 合并。不 force、不 `--no-verify`、不直推 `develop`、不自行合并、不打标签、不安装。

## 验证证据

- 会话/档案：
  - `GRADLE_USER_HOME=/Users/boolean93/workspace/hoyi_app/.dsh-gradle ./gradlew --offline :device-session:check` → `Session checks: N passed`（含新增 1.1.4/未知固件/失败分类用例）。
  - `GRADLE_USER_HOME=... ./gradlew --offline :mobile:testDebugUnitTest` → `FirmwarePresentationTest`、`LanguageCatalogTest` 通过。
- 文案目录：
  - `python3 -m unittest discover -s localization/tests`
  - `python3 localization/validate_catalog.py`（退出 0，全语言键数一致）
  - `python3 localization/android_resources.py --check`（无漂移）
- CI（代码 PR 走完整 `verify`）：`:bean-core:test :protocol-core:check :device-session:check :trace-core:test :bluetooth-android:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest :app:assembleDebug :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug`；`verify_apk_resources.py` 核对两变体八语言资源。
- **未做/不可替代**：真实 1.1.4 机器验收（连接、实时数据、启停、改设置、回读、未知固件提示、超时提示）。Mock 与单测只证明逻辑分支，不证明机器执行结果。

## 可观察验收标准（用户视角，真实机器）

1. **1.1.4 可控制（协议证据支持放开，待实机启停/设置验收）**：真实 1.1.4 机器连接后，首页状态为"已就绪"、固件卡片显示 `固件 1.1.4 · 已验证协议`（App 既有文案，非硬件验收声明）；能看实时温度/压力/流量；能开始与停止萃取；能改设置（如加热开关/待机）并在机器屏幕与回读一致；未知/超时不出现。
2. **未知固件明确**：接一台非 1.1.3/1.1.4 固件（或 Mock/离线构造）时，固件卡片显示 `固件 x.x.x · 暂不支持新版固件`，状态行显示 `暂不支持新版固件 · 控制已禁用`；不发出任何控制帧（wire trace 无新增 Write）；不自动重连，连接保持。
3. **初始化超时明确**：连上但 10 s 内收不到兼容 `0x83` 时，状态行显示 `协议初始化超时`，固件卡片显示 `固件版本读取超时 · 可能是暂不支持的新版固件`；不无限停留在"连接中/初始化中"。
4. **认证/连接失败仍可区分**：GATT 建链失败显示 `连接失败`；认证帧写入阶段失败显示 `认证未完成，请重试`（不声称密码错误）。
5. **不回归**：1.1.3 机器原行为不变（已验证协议、控制可用）；秤连接与状态文案不变。

## 协议与设备风险

- 不改帧编码/解码、不新增命令、不改 180 s 设置时效、不改认证帧、不改 GATT 端点；1.1.4 只是让既有 `READY` 门禁对等放开。
- 1.1.4 目前只有**静态协议证据**，没有真实机器操作验收；若实际固件对某命令（如启动帧匹配 `permittedStart`、设置写入回读）行为不同，风险是"命令被忽略/回读不一致"，产品侧已有回读/未知结果提示兜底，但仍需实机确认。不得在 PR/文档写"1.1.4 已验证"。
- 放开后 1.1.4 进入 `READY` 会启用现有全部控制入口（启停、设置、睡眠计划、预热、杯数重置），这些是同一套门禁，风险等级与 1.1.3 现有验收相同但未在 1.1.4 上操作过。建议实机验收先做只读（遥测/回读），再启停，最后设置写入，并保持可随时手动断开。
- 失败分类只用于显示，不参与门禁；`classifyFailure` 依赖会话状态，队列层超时（如连接操作超时）会归 `OTHER` 显示"连接失败"，属可接受保守归类。
- 未知固件保持连接会继续接收 `0x83` 但被 `authenticated` 规则丢弃，不写入产品快照、不进入控制；`coffeeFrame` 只在 `READY` 回调，不产生遥测/历史。
- 中文/七语言文案变更只影响显示，不参与按钮资格或协议判断。

## 发布

不发版：不打标签、不构建/发布 APK、不安装、不操作机器。合并到 `develop` 后如需给朋友机器使用，由用户明确授权后再走发布/安装流程（本次规划不含）。

## 回退

优先整提交 `git revert`（行为回退干净：无设备数据/schema/versionCode/持久化变化，不需要卸载或清数据）。

若只需收回 1.1.4 控制而保留提示功能，必须同时改回：`CoffeeFirmwareProfiles.controlPermitted` 恢复为 `setOf(FW_1_1_3)`，并同步更新 `DeviceChecks.kt` 的 1.1.4 READY 用例、`FirmwarePresentationTest` 的 1.1.4 文案断言，以及 README、site、firmware-status、protocol-safety-gates、native-feature-status 五处对外表述；否则测试与文档会与实际行为失配。文案回退需同时回退 8 份 catalog 与生成资源，保持 `validate_catalog.py` 与 `android_resources.py --check` 一致。不卸载、不清历史/未知记录、不用降级掩盖协议问题。

## 未完成与阻塞

- **真实 1.1.4 机器操作验收未完成**：候选验收人=用户在朋友的真实 1.1.4 机器上操作（或用户自备 1.1.4 机器），Lead 记录结果；designer 只读复核实现与文案（见下条模型路由说明）。验收项：连接/实时数据/启停/改设置/回读/未知固件提示/超时提示。在完成前，所有交付措辞必须是"协议证据支持放开，待实机操作验证"。
- 证据文件 `docs/evidence/2026-11-09-firmware-114-parity.md` 的"无法证明项"若涉及控制帧，需要补 Lab 导出帧或实机采集后才能升级档案措辞（本规划定稿时按实际结论填写受限范围）。
- 按键数与文档同步（`README.md:31,34`、`docs/firmware-status.md`、`docs/protocol-safety-gates.md`、`docs/native-feature-status.md`、`site/index.html`）由 implementer 一并完成，designer 只读复核。
- 独立复核（task-4）在实现后执行，不以实现者自查替代。**本会话没有 Astra/Sol 模型路由**：设计与复核均由同一模型（deepseek）不同 agent 承担，task-4 由 designer 以只读方式独立复核；交付与 PR 中不得宣称“跨模型独立复核”或“Astra 模型复核”。
