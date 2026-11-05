# 泰摩蓝牙秤接入

- 需求与范围：扫描、选择、记忆并重连 DOT（TES017）、Basic3 Link（TES016）；按公开协议读取重量、流速、电量，显式去皮。旧款 `timemore scale` / 181D 双重量格式仅实验性只读，不把 Black Mirror 2 全部固件或无蓝牙 Basic/Nano/Mini 当作兼容。README 引用厂商 `open-scale-protocol`。
- 来源分支/基线提交：`origin/develop`，`3f9df29`（已 fetch，工作树开始时干净）。
- 工作分支：`feature/timemore-scales`；PR 目标：`develop`。
- 协作：Sol 主导会话/BLE/应用接入、文档及验证；独立 Sol 工作者只写新协议编解码与其测试；Astra 只读复核方案及最终关键体验/门禁。共享文件由 Lead 集成，不并行覆盖。
- 可观察验收：明确型号能被发现且所选协议可持久化；GATT 不匹配拒绝连接；新代先设克并读取单位，只在克单位确认后发布新鲜称重；负重量/分片/多帧/坏帧正确处理；双重量保留两通道但不猜用途；显式去皮需后续近零样本确认；新协议不触发自动去皮或目标重量联动。旧款只订阅，不写心跳或任何控制命令。
- 改动文件与步骤：`protocol-core` 新编解码/有界通知重组；`device-session` 协议适配器、来源分级、每连接解码状态、无初始化写入的只读路径；`bluetooth-android` 发现及去皮观测；`mobile` 兼容提示及读数；代表性测试与 README。
- 协议来源：TIMEMORE-COFFEE/open-scale-protocol，固定提交 `e31dce5087494ea3d572a89e689e48dc014eee2c` 的 v1.0.3。旧款格式参考 MIT 的 gaggimate/esp-arduino-ble-scales；不复制 GPLv3 或未授权第三方实现。公开协议仓库未声明许可证，不转载全文。
- 协议与设备风险：区分公开文档与实机验证，新增文档来源证据，不冒充 VERIFIED_TRANSPORT；`validatedWeightControl=false`，不能泰摩按重启动/停机；自动去皮单独门禁且泰摩关闭。FFF2 仅无响应写，白名单只允许设置克、读取单位及显式去皮。禁止恢复出厂、清配对、远程关机、未文档模式切换和未知写入重试。CRC 保留零值仅按文档化通知分级接收，非零坏校验拒绝；过载及整数溢出不发布重量。旧款 int16/int32、心跳/订阅来源存在差异，保持实验性只读，不为凑兼容扩张写权限。
- 验证证据（2026-11-05）：最终运行 `./gradlew :protocol-core:check :device-session:check :bluetooth-android:testDebugUnitTest :mobile:testDebugUnitTest :mobile:assembleDebug -PgoogleMirror=aliyun --console=plain`，exit 0。协议自检 39,823 项（含既有 HOYI/BOOKOO 捕获重放 32,856 条，非泰摩实机数据）；会话自检 101 项 + JUnit 690 项通过；BLE 发现 JUnit 3 项通过；mobile 400 项通过、1 项已有条件跳过（`auditsUserExportWhenPathsAreProvided`）。真实 Debug APK 构建完成但未安装、未分发。`git diff --check` 和 `python3 scripts/git_policy.py branch` 通过。新增泰摩会话 7 项代表性测试覆盖双型号 GATT/单位 ACK 区分、负值/无效单位、连接隔离、去皮 ACK/零值顺序、NACK/断线/绝对超时、命令白名单和旧款零写入；另有编解码、发现、目标控制和自动去皮回归。
- 修复与独立复核：首轮发现 Felicita 委托适配器绑定了离线解码器，已改为每连接可选状态解码器，空值时在真实适配器上调用 decode；四项既有只读会话回归恢复。实际 `openai/gpt-6-astra` 完成只读方案与最终代码复核，发现一项去皮 deadline 边界：晚 ACK / GATT 回调先于 tick 时不能恢复成功。已在共用完成入口检查绝对 deadline，新增迟 ACK、早 ACK/迟 GATT 回归并通过；Astra 再读确认关闭，未发现其余未解决阻断。审查包括发现/owner/持久未知、单位授权、写入白名单、业务门禁、UI 与官方来源；未进行运行 UI、截图或实机验收。软件自检与独立静态复核不能替代这些待办。
- 推送与合并：遵守 [Git 工作流](../git-workflow.md)，只提交任务文件，普通推送功能分支，PR 到 develop；合并必须精确源码对应 branch-policy、verify、lifecycle、upgrade 四项 CI 及独立审查。无实机设备证据时如实记录，不能宣称硬件验收完成。
- 发布与签名/升级：不发版、不打标签、不安装、不操作蓝牙设备；不更改包名、签名、版本与存储结构。
- 回退：在功能分支撤回或通过 revert PR 回退；新协议选择在旧代码中不认识时不自动改成 BOOKOO，不卸载、不清历史、不清未确认去皮记录。
- 未完成与阻塞：所有泰摩实机验收待办，包括设备型号/固件/GATT、配对与无名广播、单位读回、正负重及流速、去皮实际动作与回包、断线重连、旧款是否需要心跳、双通道含义及物理控制安全。本次只完成软件适配与离线证据，不宣称上述通过。

## README 梳理（2026-11-06）

- 范围：应用户要求统一列出当前电子秤接入范围，并润色 README 全文；仅文档调整，不改 App 文案、协议或控制门禁。
- 分支：沿用同一适配任务的 `feature/timemore-scales` / PR #14，基于 `ffc5d26` 继续；功能最初来源仍为上述 develop 基线，fetch 后 develop 未变化，PR 尚未合并。开始时工作树干净。
- 步骤：按注册表、发现规则和验收记录核对 BOOKOO、DOT、Basic3 Link、旧款 TIMEMORE 与 Felicita；区分协议接入和实机证据，不把 Acaia / DiFluid 离线候选列为已支持。将适配表放在用户可见正文，协议细节折叠，精简重复措辞。
- 验证：核对事实与本地 Markdown 链接、表格及代码块结构，执行 diff 和分支策略检查；纯文档不重复 APK 构建或设备操作。低风险文案由 Sol 自查，不追加独立实现/硬件验收声明。
- 推送：只提交 README 与本记录，普通推送至既有 PR；不自动合并，必需 CI 规则不变。
- 发布与回退：不发版、不安装，不改签名/版本/数据；必要时追加文档还原提交，保留所有设备未知记录。此前经用户另行授权的平板双包安装不构成泰摩硬件验收。
- 验证结果：按 `ScaleProtocolRegistry`、`ScaleAdvertisementClassifier`、`alpha-acceptance.md` 及构建配置核对范围；`git diff --check`、`python3 scripts/git_policy.py branch` 通过。README 14 个、本记录 1 个本地文件链接均存在；Markdown 表格列数、代码围栏与折叠块配对检查通过。最终 diff 仅包含 README 与本记录，未运行应用或硬件测试。
