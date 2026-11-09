# 固件 1.1.4 放开与未知固件/超时提示：独立实现复核

- 复核日期：2026-11-09（Asia/Shanghai）
- 工作树：`openhoyi-task-first/.agents/worktrees/feature-firmware-support`，分支 `feature-firmware-support`，基线 `origin/develop` `63e90bf8`，实现改动未提交
- 复核者/性质：独立 agent 实例，**deepseek-flash 模型**，只读复核（本文件是唯一写入）。**不是 Astra/Sol 模型复核**，不得据此宣称跨模型独立复核。未连接设备、未安装、未提交/推送、未运行完整矩阵/AVD。
- 复核输入：`AGENTS.md`、`docs/git-workflow.md`、`docs/plans/2026-11-09-firmware-114-support.md`、`docs/evidence/2026-11-09-firmware-114-parity.md`、`git diff` 全量（38 个修改文件 + 4 个新增文件）。
- 结论用途：判断该实现能否进入 PR 合并；**不等同于硬件验收**（协议证据与 Mock/单测不等于真机行为）。

## 0. 结论

**可合并（无阻断问题）。** 代码、文案、多语言、文档、测试、回退方向均符合规划与证据边界；控制门禁未放松、未新增绕过路径；未知固件零控制帧与失败分类时序经代码审查与单测验证成立。

合并前/合并时建议落实的 2 个中低项见 §2：**回退方案需精确到"测试+文档同步"**（仅恢复 `setOf(0x113)` 会让新单测与文档失配）、**1.1.4 首次出现设置回读"滤芯"行需实机核对**（`firmwareInteger>=276` 的既有解码阈值）。其余为低优先级建议与历史文档说明。真机启停/设置验收仍是发版/安装前的前置条件，不是本次合并的前置条件（与规划一致）。

## 1. 复核项逐条结论

| # | 复核项 | 结论 | 依据摘要 |
|---|---|---|---|
| 1 | 代码正确性 | **通过** | 档案集合仅 0x113/0x114；控制入口/门禁函数零改动；未知固件零控制帧；`lastFailure` 时序正确；两处 snapshot copy 一致 |
| 2 | UI 文案与触发 | **通过** | UNSUPPORTED 卡片/状态行、超时/认证/连接失败可区分、1.1.4 READY 措辞与文档边界一致；两处报告偏差合理、无回归 |
| 3 | 多语言 | **通过** | 8 份 catalog 键/参数/顺序一致；source.json 哈希匹配；生成资源零漂移；`firmware_unverified` 代码与资源零残留 |
| 4 | 文档同步与措辞 | **通过（含低优先说明）** | 首页/README/site/状态文档已同步；未见"1.1.4 已验证"滥用；历史 plan/记录文档保留旧表述属历史归档 |
| 5 | 测试质量 | **通过（独立重跑）** | 见 §4；非空断言、覆盖 1.1.4 READY+控制、1.1.5 零写入、三类失败分类；含少量覆盖缺口（§2 低项） |
| 6 | 回退干净性 | **行为可回退，非"一行"回退** | 见 §2 M1；无设备数据/schema/持久化变更，回退无数据风险 |

### 1.1 代码正确性细节（核查通过）

- **档案集合**：`CoffeeFirmwareProfiles.kt:17` 私有 `setOf(FW_1_1_3, FW_1_1_4)`，`permitsControl` 仅在 `DeviceSession.kt:258` 的 `acceptSettings` 使用（全仓库唯一调用点）；1.1.3/1.1.4 以外一律 `UNSUPPORTED`。KDoc 已写明"档案成员不等于硬件验收"（`:3-14`）。
- **门禁未动**：`git diff` 中 `send`（`DeviceSession.kt:312`）、`canControlFromIdle/WithFreshSettings/WithBrewSettings`（`:318-335`）、`stopExtraction`（`:384-401`）、启动/设置/睡眠/杯数/预热各入口、`GuardedGattDriver`、`OutboundWritePolicy`、`DeviceState` 枚举、协议编解码/命令编码**全部零改动**。唯一进入 READY 的路径仍是 `acceptSettings`，无新增放行分支。
- **未知固件零控制帧**：认证阶段之后唯一的咖啡写入是认证帧本身（`:179`，基线与证据一致）；所有控制写入前置 `state==READY`（`:312`、各 `canControl*`）。单测 `DeviceChecks.kt:305-330` 在 1.1.5 下调用启动/停止/设置/立即睡眠/预热/杯数重置全部 `Failed`，且断言 `d.calls.size==writesBefore`（零新增驱动调用，不只是"零成功"）。
- **`lastFailure` 时序**：`fail()` 先 `classifyFailure` 写入 `lastFailure` 再 `endConnection(FAILED)`（`DeviceSession.kt:544-548`），故 `setState(FAILED)` 的观察者读到的是本次分类；`endConnection` 对非 FAILED 终态清空（`:523`）；`connect()` 重置设置时一并清空（`:156`）；`UNSUPPORTED` 只从 `SYNCHRONIZING/READY` 的 `acceptSettings` 进入，此前无失败且 `connect()` 已清空，当前不可达"跨状态残留"。FAILED 后队列已 detach，不会再有通知进入 `acceptSettings`。
- **分类口径**：`PROTOCOL_TIMEOUT` 来自 `PROTOCOL_TIMEOUT_REASON`（`:210`、`:274` 两处，与规划一致）；`CONNECT`/`AUTH` 以当时 `state`（CONNECTING/INITIALIZING）判定；其余 OTHER。分类只进 `MobileSnapshot`/UI，未参与任何门禁（全仓库 grep 仅显示层使用）。
- **MobileSnapshot**：新增末位默认字段 `coffeeFailure`（`MobileService.kt:78`）；FAILED 分支显式取 `hub?.coffeeFailure`、其余咖啡状态显式置 `null`（`:509-510`），两处 copy 一致；其余 `snapshot.copy(...)` 不改该字段，不产生跨状态残留。`NativeDeviceHub.kt:112` 只读透传。
- **范围干净**：`git status` 仅含本任务预期文件；无 `.github`、Gradle、`protocol-core`、`app`、`analysis/`、`captures/` 改动；无调试残留（diff 中无 TODO/println）。

## 2. 问题清单（按严重度）

### 阻断

无。

### 中（合并时处理，不阻断代码正确性）

**M1. 回退方案"恢复 `setOf(0x113)` 即可"不完整（测试与文档会失配）。**
规划 `docs/plans/2026-11-09-firmware-114-support.md:236` 写"`git revert` 相关提交（**或**把 `controlVerified` 恢复为 `setOf(FW_1_1_3)`）"。仅执行后者时：`device-session/src/test/kotlin/io/openhoyi/session/DeviceChecks.kt:1163-1178`（1.1.4 期望 READY/允许控制）会失败，`mobile/src/test/.../FirmwarePresentationTest.kt:13`（1.1.4 READY 文案）也会失败，且 README/site/状态文档仍宣称 1.1.4 放开。
建议：PR 的回退段落明确"优先整提交 `git revert`；如只收回控制，必须同时改回这两处单测与 5 处对外文档（README/site/firmware-status/protocol-safety-gates/native-feature-status）"。行为层回退本身干净：无设备数据、schema、versionCode、持久化变化，不需要卸载/清数据。

**M2. 1.1.4 进入 READY 后新增"滤芯"回读行，属首次出现的用户可见信息，需实机核对。**
`protocol-core/.../Protocol.kt:29` 的既有阈值 `firmwareInteger>=276`：1.1.3=275（`filterInstalled=null`，不显示），1.1.4=276（`filterInstalled = flags&2 != 0`），而 `mobile/.../MachineSettingsPresentation.kt:87` 会把该行渲染为"滤芯已安装/未安装"。证据对该字段只证明"取值 0 或 2、映射与旧 App 阈值一致"（parity §2.4 b[11]），未证明机器语义；UNSUPPORTED 固件不会走到这里（产品快照设置仅在 READY 写入）。
建议：列入 1.1.4 实机验收项，与机器屏幕/滤芯实况对照；若实机不一致，**只在显示层隐藏或标注未确认**，不改 1.1.4 解码与门禁（不要为显示去改协议层）。

### 低（建议，非合并条件）

**L1. `endConnection` 注释声明的"不得残留到 UNSUPPORTED"并不由该路径保证。**
`DeviceSession.kt:521-523` 的注释列出 READY/DISCONNECTED/UNSUPPORTED，但 `UNSUPPORTED` 是从 `acceptSettings` 直接 `setState`（`:256-260`），不经过 `endConnection`。当前不可达残留（见 1.1 论证），属防御性缺口。
建议（二选一）：`acceptSettings` 在非 READY 分支同时 `lastFailure=null`；或把注释改成"该不变量当前依赖 `connect()` 清理 + UNSUPPORTED 只能从已认证状态进入"，避免后人误以为已强制。

**L2. 失败分类与规划文字有微小偏差（行为更好，建议对齐说明）。**
规划 `:110/:226` 称"队列层超时归 OTHER"，实现按当时 `state` 归类：认证写阶段的队列超时→`AUTH`、建链阶段的队列超时→`CONNECT`（两者对用户更准确，且与规划 AUTH 定义"认证写入阶段失败/超时"一致）。
建议：PR 或规划注明该细节差异即可，不必改代码。

**L3. 次级页面仍使用通用文案。**
`SettingsDeviceControls.kt:100`、`MachineSettingsControls.kt:414`、`ExtractionFragment.kt:540,735`、`ExtractionActivity.kt:461` 仍走 `DeviceStatusText.label(...)`：未知固件显示"不支持"，FAILED 显示"连接失败"（不区分超时/认证）。**基线行为相同，无回归**；首页/TaskShell 已覆盖新文案。
建议：作为后续统一项（把这几个咖啡机标签改走 `coffeeLabel`），本轮可在 PR 注明范围；若要本轮解决，改动面比现在大，需另评。

**L4. 命名歧义（内部）。** `CoffeeFirmwareProfiles.controlVerified`（`:17`）含 "verified"，与"1.1.4 未实机验收、不得写已验证"的措辞纪律轻微冲突（私有字段、用户不可见）。建议改名 `controlPermitted` / `controlEnabled`，或保留 KDoc 免责声明即可。

**L5. `DeviceChecks.kt` 末尾缺换行。** `git diff` 显示 `\ No newline at end of file`（原文件有）。无 CI 检查依赖此项，建议补上避免噪声 diff。

**L6. 历史归档文档仍含"仅 1.1.3"。** `docs/plans/product-minimal-white.md:96`、`docs/plans/user-product-site.md:24`、`docs/alpha-acceptance.md:9`（当日验收记录）、`HANDOFF.md:459` 仍是旧表述。按仓库惯例 plans/验收记录是历史文档、不回写，**不要求修改**；live 文档（README/site/firmware-status/native-feature-status/protocol-safety-gates）已同步，无遗漏需改的 live 表述。

**L7. 测试覆盖缺口（可接受）。** 未单独覆盖"FAILED 后 `UNSUPPORTED`/`DISCONNECTED` 不残留旧分类"（当前不可达，见 L1）；1.1.4 只用 `writeSetting` 证明控制放行，未用启动帧覆盖（同一 READY 门禁，启动另有 `permittedStart`/`captureStartContext` 独立单测，风险低）。

## 3. 实现者报告的偏差：确认合理

1. **UNSUPPORTED 咖啡状态行用 `home_firmware_readonly`**（`DeviceStatusText.kt:20`，值"暂不支持新版固件 · 控制已禁用"）：合理。首页/TaskShell 的咖啡机状态行均走 `coffeeLabel/coffeeResource`，固件卡片另给"固件 x.x.x · 暂不支持新版固件"（`FirmwarePresentation.kt:11-20`），满足"含版本号"的用户要求（版本在卡片行，状态行保持短）。
2. **秤 UNSUPPORTED 回退为 `device_state_unsupported`**（`DeviceStatusText.kt:32` 及 HomeActivity/Fragment `label()`）：合理、无回归。`UNSUPPORTED` 在 `device-session` 中唯一写入点是咖啡 `acceptSettings`（`DeviceSession.kt:258-259`），秤不可达；`label(UNSUPPORTED)` 仅剩秤/非咖啡使用。原生 UI androidTest 的期望值也用 `DeviceStatusText.label`（`HomeWorkbenchChecks.kt:95`），与其实现一致，不因该改动失败。
3. **失败分类顺序**：`fail` 先写分类后 `endConnection`，`hub` 在状态回调内同步读取，满足"观察者回调时已可读"（`NativeDeviceHub.kt:50-62` → `MobileService.kt:509`）。

## 4. 实际执行的验证（独立重跑，非实现者结果复用）

环境：`GRADLE_USER_HOME=/Users/boolean93/workspace/hoyi_app/.dsh-gradle`，`--offline`，未跑完整矩阵。

| 验证 | 命令 | 结果 |
|---|---|---|
| 会话校验 | `./gradlew --offline :device-session:test --rerun-tasks` | `Session checks: 103 passed`，BUILD SUCCESSFUL（含 `PASS 1.1.4 firmware profile opens the existing ready gate`、`PASS coffee failure classification is display-only and cleared on reconnect`、`PASS unsupported coffee firmware never opens control gate`） |
| Mobile 单测 | `./gradlew --offline :mobile:testDebugUnitTest --rerun-tasks` | 93 个 XML/489 个测试，failures 0、errors 0、skipped 1（既有），BUILD SUCCESSFUL；`FirmwarePresentationTest` 4、`DeviceStatusTextTest` 2、`LanguageCatalogTest` 1、`LanguageDraftTest` 4、`ResourceMessageTest` 9 全绿 |
| catalog 一致性 | `python3 localization/validate_catalog.py` | `sourceKeys:966`，7 语言各 966、`errors:[]`、`complete:true`，exit 0；脚本另核参数索引/类型/次数 |
| 生成资源漂移 | `python3 localization/android_resources.py --check` | `resourcesPerLanguage:965`、`changed:[]`、`drift:[]`，exit 0（无手改痕迹） |
| 多语言测试 | `python3 -m unittest discover -s localization/tests` | Ran 22 tests，OK |
| source 哈希 | `shasum -a 256 mobile/src/main/res/values/strings.xml` vs `source.json.sha256` | 均为 `79f442da961da8b517c9e8de0cf964e36c7b97be328e4be04cd3d9487707bbf4`；source.json 键序=XML 键序、键值逐条一致（0 mismatch） |
| 站点检查 | `python3 scripts/test_product_pages.py` | Ran 14 tests，OK（`固件 1.1.3` 断言仍满足） |
| 残留引用 | `grep -rn firmware_unverified`（排除 build/.git） | 仅规划文档中的"旧键→新键"说明，代码/资源零引用 |

## 5. 合并前必须补的验证/实机项（发版/安装前，非合并门槛）

1. **1.1.4 真机只读→启停→设置**：连接后首页"已就绪"、卡片 `固件 1.1.4 · 已验证协议`；采集首帧 `0x83` 原始 hex（证据预测 `830114FD5C007D0F3500190069`，前 12 字节 XOR=0x69）；`0x40/0x80` 字段与屏幕对照；开始/停止（含手动停止）、常用设置写入+回读、每周睡眠两包、杯数重置、预热（若入口可用）；每步保留 WireTrace。
2. **Lab 导出 1.1.4 GATT**：服务 `55535343…`、写 `49535343-8841…`、通知 `49535343-1E4D…` 属性与 `0x2902` 订阅（固件镜像不含 GATT 表，parity §3-1 无法证明；若端点/属性变化应 fail-closed，需确认实际仍进入 READY）。
3. **认证/密码链路**：正确与错误密码在 1.1.4 的响应差异（不改机器密码、不记录明文），确认"认证未完成，请重试"分类不误导（parity §3-2 未证明密码校验位置）。
4. **未知固件提示**：1.1.5/未来版本实机（或可复现 Mock）：卡片"固件 x.x.x · 暂不支持新版固件"、状态行"暂不支持新版固件 · 控制已禁用"；WireTrace 无新增 Write；连接保持、不自动重连、可手动重试；换 1.1.3 后旧帧不串身份。
5. **初始化超时提示**：收不到兼容 `0x83` 的场景（可构造/模拟）：10 s 后状态行"协议初始化超时"、卡片"固件版本读取超时 · 可能是暂不支持的新版固件"；观察自动重连（5 s→30 s）反复提示的闪烁是否可接受（规划明确本轮不改重连策略）。
6. **1.1.3 回归**：READY/控制/文案不变；设置回读**不出现**滤芯行（275<276）；详见 M2 的 1.1.4 滤芯行对照。
7. **UI/多语言**：UNSUPPORTED、FAILED+超时、AUTH、1.1.4 READY 四种状态首页截图；至少 zh+en 与 ar（RTL）、compact 小屏（<400 dp）长文案不截断；`values-*` 已生成但布局未实机验证。
8. **PR 完整 verify**：本轮只跑最精简 JVM/localization 子集，未跑 lint/APK 构建/AVD（与"精简测试规则"一致，CI 负责完整 verify）。

## 6. 未验证边界

- **静态证据边界（来自 parity §3，本实现未扩大）**：1.1.4 GATT 属性、密码是否被校验/由谁校验、`0x80 b[11]/b[12]` 位语义与 `0x40 b[1]`、1.1.3→1.1.4 逐差异、OTA 是否同时更新 BLE 模块——均无法从镜像证明，需实机/Lab 采集。
- **无真机**：本复核未连接/未安装/未操作设备；启动、停止、设置生效、回读一致性均未验证；"协议证据支持放开"不等于硬件验收。
- **未运行完整矩阵/AVD**：`androidTest`（含 `HomeWorkbenchChecks` 期望值）仅做静态一致性核对，未运行；未跑 lint/APK/两变体八语言资源校验。
- **自动重连策略未改**：未来不支持固件若反复超时，会按 5 s→30 s 反复出现 FAILED 提示，属已知体验边界（规划已声明后续可另提案暂停重连）。
- **显示层状态不持久化**：`lastFailure`/`coffeeFailure` 只存在于进程内，进程重启后回到初始文案（设计如此，无数据风险）。
- **次级页面文案**：设置页/萃取页对 UNSUPPORTED 仍显示"不支持"、对超时仍显示"连接失败"（基线行为，见 L3）。
- **Mock 模式**：不构造 `PROTOCOL_TIMEOUT`，超时文案只在单测的显示映射层验证，未在 Mock UI 中验证。
- **1.1.4 滤芯回读行**：新增显示，实机语义未核（M2）。

## 7. 复核变更边界

- 本文件是本轮唯一写入；未修改产品代码、测试、规划、实现者文件、`analysis/` 或 `captures/`；未提交、未推送、未安装、未连接设备。
- 结论针对工作树当前未提交状态（基线 `63e90bf8` + 本任务 diff）；若实现者继续修改需重新复核。
- 本复核由 deepseek-flash 的独立 agent 实例执行，**非 Astra/Sol 模型**，按 AGENTS.md 如实标注，不宣称跨模型独立复核。
