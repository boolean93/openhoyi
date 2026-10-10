# 无真机条件下的高级迭代验证

用户明确要求不等待真机，自行完成测试。本计划延续完整需求，不把模拟结果写成实机验收。

分支：既有 `feature/advanced-experience` 从 `develop` 建立，PR #2 合向 `develop`；遵守 `docs/git-workflow.md`。不合并、不发版、不安装用户设备。

## 设计与执行

1. 自制曲线先提供受限、无损的压力参数适配。仅支持文档能完整表达且旧编码器能精确表示的参数：75–105°C、1–4段、0–12bar、整ml阶段水量、0.1g目标重量。无预浸/阶段计时字段，不猜这些字段；原始流量模式不冒充压力模式。非整数水量和不可表示的重量不得静默舍入。
2. 使用已有 `generate_legacy_curve_oracle.py` 执行独立旧JS编码器，其函数哈希必须匹配既有证明。产生固定边界和确定性随机压力配方，涵盖6槽位和有/无秤模式；保存原参数、源码/编码器/输入哈希和独立预期帧。Node不进入APK。
3. 先写参数适配测试取红，再实现适配；逐字节比较独立oracle，拒绝无损性、模式、字段范围错误。此步骤只构造参数，不改变设备执行许可。
4. 后续将受支持参数接入曲线选择与现有启动队列，执行许可由本机语义验证与参数编码产生，不能由导入文件verified字段或任意帧产生。未知固件、未决记录、陈旧遥测与去皮门禁保持。为此追加真实Service/会话故障注入测试与模拟器导入→选择→启动→遥测→结束路径。
5. 第二秤用虚拟GattDriver连接真实DeviceSession，回放公开完整通知，覆盖订阅失败/超时、错端点/单位、乱序/断连/重连、唯一owner、协议配对记忆和只读控制拒绝；模拟器验证页面联动。只读协议不获得额外去皮或重量停水能力。
6. 完成后统一跑协议/会话/应用测试、构建/Lint、升级、生命周期与高级页面CI；独立审查，普通推送现有分支。软件验证与实机未验证分别记录；不再把暂不提供真机作为软件开发停止条件。

## 本轮文件与命令

- 新增 `CustomPressureCurveAdapter.kt`、`CustomPressureCurveAdapterTest.kt`、测试资源 `custom-pressure-curves.json` / `custom-pressure-wire.tsv`。
- 固定种子产生边界和随机配方，执行现有oracle生成器。旧源码只读，fixture不能包含凭证或私人记录。
- `:mobile:testDebugUnitTest --tests '*CustomPressureCurveAdapterTest'`：先红后绿，包含全部6槽位×2模式对比及拒绝测试。
- 后续执行接线与秤回放必须先核对现有测试和最终发送门禁，不降低已通过的安全断言。

## 第一阶段实际证据

- 已使用现有旧JS oracle（函数哈希635ddbe7…匹配；当前源码哈希1451bddc…明确记录，不冒称原始整文件哈希不变）生成72个边界/确定随机压力文档×6槽位×2秤模式，共864帧。fixture包含参数与独立预期帧、完整输入哈希。
- 先用空实现取得真实红：3测试2断言失败（受支持配方无法生成参数）；实现无损适配后3测试通过。补输入哈希与索引/槽位顺序绑定，防止fixture被错配。
- Debug/Mock完整应用单测通过（统一命令32s）；独立静态审查无P1/P2，确认单位/padding/参数限制/无控制授权。未独立重跑oracle的审查不包装成执行证据。
- 本阶段只是受限压力参数适配；CurveLibrary仍拒绝draft执行。下一阶段必须完成选择/最终发送许可与模拟器全流程，不能把本节当完整自制曲线已交付。

## 第二阶段：受限启动许可与当前配方身份

- DeviceSession新增默认拒绝的受限压力参数回调；除了本机host许可，必须满足CustomPressureStartPolicy的无预浸/无阶段计时/无流量逻辑、有效槽位、75–105°C、整ml水量、压力0–12bar、非活动段全零和总水量关系。没有新增任意字节入口，也没有增长legacy帧白名单。
- 最终队列发送前重新校验许可与原context；许可撤销、异常、alarm/sleep、settings改变、遥测陈旧、断连/重连均不发送启动帧。原固件/READY/身份/温度/回读新鲜度门禁保留。真实红为6测试4断言失败；绿6项之后补5故障注入项。
- CurveLibrary新增显式默认关闭开关。开启后仅当前保存且无损可表示的压力文档可resolve；编辑/移除撤销旧profile。未保存、原始流量、非整ml或非0.1g参数不能取得资格。真实红为3测试2断言失败，随后3项绿。864独立oracle帧对比同时核对参数策略。
- 完整device-session JUnit670（0fail/error/skip）、93回放cases；Mobile Debug/Mock各261（0fail/error、1既有skip）；统一回归27s。两轮独立静态复审无P1/P2，建议的五类队列故障注入已落实并通过。
- 应用开关、AndroidDevice/Hub的host许可传递以及UI/模拟器全流程尚未接通；当前不能宣称用户端自制曲线完整可执行。下一阶段继续这些接线，不因暂无真机而停止。

## 第三阶段：产品入口与执行闭环（正在验证）

- MobileApplication显式开启受限压力执行；CurveLibrary许可只匹配当前选中且当前保存配方的精确参数，slot7，分别按有/无秤产生参数。AndroidDevice/NativeDeviceHub传递默认拒绝的回调，MobileService每次许可重新读当前选中ID。导入不选择、不启动机器，原始流量仍只读。
- 编辑、库详情、分享预览同步展示参数兼容和无损边界，8语言52key一致。原只读升级/文档断言使用FLOW_RAW fixture保留，不删只读控制测试。
- 独立审查发现ShotHistory拒绝draft，阻断真实和Mock启动。先新增回归取得红（规范draft ID begin抛出IllegalArgumentException），随后支持规范UUID、仅临时slot7；历史身份不构成设备许可。新测试核对拒绝非法ID和预设槽位、结束和使用证据持久化。
- 本地Debug/Mock各263测试（0fail/error，1既有skip），仪表测试Kotlin编译通过；统一命令27s。只通过编译不代表模拟器路径已执行。
- 新增CustomCurveExecutionChecks：仅disposable CI Mock，通过实际导入/预览确认/库选择/启动确认/停止按钮，断言Service运行和结束、历史、永久使用次数、journal及无Bluetooth hub。既有记录保持，拒绝已有未知/未结束记录，不清理未知状态。接入advanced页面脚本末尾，尚待当前提交模拟器运行确认。

- 独立复审补充真实模拟器门禁：customExecution必须同时满足Mock隔离包、disposable标志、ranchu/goldfish硬件和ro.kernel.qemu=1；不能仅凭旗标写用户Mock数据。
