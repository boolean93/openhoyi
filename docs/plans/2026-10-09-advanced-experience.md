# 高级功能迭代开发计划

**目标：** 完成已讨论的高级功能迭代，而不是仅迁移架构或交付图片。

**依据：** [调研报告](../research/2026-10-08-advanced-features-research.md) 与 28 页 ImageGen 示意。图中参数不是控制许可，也不能照搬不准确轴刻度。

**工作流：** 严格遵守 [Git 工作流](../git-workflow.md)。来源 `origin/develop`，基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；工作分支 `feature/advanced-experience`；PR 目标 `develop`。独立工作树 `/Users/boolean93/workspace/hoyi_app/openhoyi-advanced`。原 `openhoyi-native` 中未提交文件不动、不代提交。

**架构：** 保留原生 View/Canvas 和现有 BLE 会话。库存为独立纯 Kotlin 模块；长期复盘与使用事件独立于 30 天历史缓存；曲线编辑/共享只写本地数据，不自动授予执行资格；秤页面复用唯一连接。新增业务通过明确的应用层接口组合，不让库存直接写机器。

**执行：** 使用 `superpowers:test-driven-development` 验证业务行为；`superpowers:subagent-driven-development` 分派隔离任务并执行规格与质量审查。构建由主任务统一协调，避免多个 Gradle 进程同时写相同输出。下面是完整范围清单，未勾选项必须继续实现，不用单个模块测试替代全量完成。

## 交付与验收清单

- [x] 核实远端、最新 develop、治理合并及正确新分支。证据：`git fetch origin`、`git ls-remote --symref origin HEAD`、`git merge-base HEAD origin/develop`、`python3 scripts/git_policy.py branch`。
- [ ] A3/A11：重设计实时萃取与准备/结束页面。`ExtractionActivity.kt`、`ShotChartView.kt` 与新的实时展示模型：大号读数、分开的压力/杯中流速图、共用时间轴、来源和单位、数据新鲜度与缺口、固定停止入口；手机/平板、深浅色与大字体检查。未知结果不能变成完成。
- [ ] A5/A7：曲线库卡片与详情、永久使用事件和排序。新增 `CurveUsageLedger.kt`、`CurveOrdering.kt`；接入 `ShotHistory.kt`、`MobileApplication.kt`、`CurveActivity.kt`。新鲜阀门开启且非预热的萃取通知才计数，按 shot ID 去重；仅写回调成功的 RUNNING 和失败启动不计数；历史缓存清理不删除累计使用事件。默认最近使用，可切常用并持久记住。测试重启、重复状态、缓存淘汰、相同次数稳定排序、搜索/分类组合。
- [ ] A6：长期单杯记录与复盘。新增 `BrewJournal.kt` 与持久化适配；自动记录状态和配方关联，可选豆子、实际粉量、研磨、曲线跟踪感受、品尝笔记、下次调整。`HistoryActivity.kt`、`HistoryDetailActivity.kt` 和复盘编辑/两杯对比页支持查看、编辑、导出；保留未知状态。测试兼容迁移、重启恢复、同杯编辑、30 天之外可回顾。
- [ ] A8：独立豆库存模块 `bean-core`。豆款、批次、购入/烘焙/开封可选日期、初始量、消耗和调整流水；数量用整数 mg，不用浮点余额。同一消耗 ID 幂等，失败写入不更改内存，不允许负库存；读 API 不依赖 Android/咖啡机。库存首页、批次详情、入库编辑、流水调整四页通过原生 UI 接入。测试新批次、消耗、调整、幂等冲突、损坏输入、持久化失败和重启。
- [ ] A9：选豆/粉量和投粉确认。新增准备上下文与确认页面；显式确认后产生一次消耗事件，与每杯 ID 关联；开始失败不会自动回库，调整必须显式记流水；返回/重进/双击不会重复扣减。仅称豆上下文可把稳定秤读数当粉量。测试取消不扣、重复不扣、失联/未知保留、余额不足和存储失败阻止确认。
- [ ] A1/A2：秤抽象层与独立秤页面、连接和联动设置。统一读数/来源/质量/新鲜度与重量、去皮、电量、设备计时等能力；BOOKOO 迁移行为不变；候选协议按解析证据接入，只读与实机未验证明确区分。独立称重和 App 计时不要求机器连接；活动萃取禁止换秤和去皮，不另建 BLE 连接。能力缺失不伪造 0 或成功。每型号初始化/帧/负重/陈旧/去皮/重连分别验证，实机缺口保留。
- [ ] A10：本地曲线最小编辑、分享、粘贴/文件导入和兼容预览。新增版本化 Profile 文档模型、校验/存储/交换；参数阶段示意不是秒级预测。用户主动复制/粘贴和 Android 分享文件；仅参数，不含私人复盘；导入先预览再保存，不启动机器、不放宽原执行白名单。测试往返、版本、不合法值、大小限制、同名不同 ID、执行资格隔离。
- [ ] A4：只读阶段目标/历史参考对照。只能画可追溯设定与历史；未确定阶段时长不假造完整时间目标；机器水流与秤质量流速不计算直接偏差。对比页标清历史不是预测。R1 口感预测按用户说法可不做，不作为交付要求。
- [ ] A11：全局聚焦与设置、导航。`HoyiUi.kt`、`HomeActivity.kt`、`MachineSettingsActivity.kt`、应用设置页；清晰分离冲煮/曲线/记录/豆子，电子秤独立工具入口，设备操作和低频设置不挤入实时页。新增用户文字走独立 strings 资源并保持现有多语言回退，不强塞硬编码中文。
- [ ] 全部业务单测、协议/会话回归、Debug 编译与 Lint、生命周期和升级检查；原生界面可运行检查，Mock 仅验证 UI，绝不替代硬件证据。
- [ ] 独立规格审查与代码质量审查完成；普通推送本分支、PR 目标 develop、branch-policy/verify/lifecycle/upgrade 四项 CI 精确源码通过。无授权不自动发版/安装/操作设备。

## 第一项：永久曲线使用和排序

文件：新增 `mobile/src/main/kotlin/io/openhoyi/mobile/CurveUsageLedger.kt`、`CurveOrdering.kt`、对应 JVM 测试和 `mobile/src/main/res/values/advanced_curves.xml`；修改 `ShotHistory.kt`、`MobileApplication.kt`、`CurveActivity.kt`。

1. 写行为测试：同杯重复真实萃取观察计一次，仅 RUNNING 命令回调不计；预热不计；UNKNOWN 中断此前使用保留；30 天缓存清理后仍有统计；NOT_STARTED 不计；相同计数以最近使用和 ID 稳定排序。先运行测试确认新增行为缺失。
2. 纯 Kotlin 账本接口 `record(shotId: String, curveId: String, startedAtMs: Long): Boolean`；`stats(curveId: String): CurveUsageStats`；提交持久化成功后再更新内存。同 ID 参数冲突拒绝。
3. `ShotHistory.observeRunning` 只由 `MobileService` 当前、阀门开启且非预热的通知触发，回调不改变设备写入确认/未知记录。新增第十字段持久化该观察证据，迁移仅采纳此明确标记且非 manual；旧八/九字段没有实际运行证据，不从结束或停止请求猜测计数。查代码确认：控制器 RUNNING 可能仅代表写回调成功，停止一个未发送的开始也可能进入 ENDED_OBSERVED，因此不能用旧状态名补造使用事件。
4. `CurveOrdering.sort(items, mode, ledger)` 在搜索分类之后排序；`RECENT` 最近时间倒序，`MOST_USED` 次数倒序、最近倒序、ID 顺序。UI 使用明确标签，展示真实累计次数，偏好持久化。
5. 验证 `:mobile:testDebugUnitTest`、`:mobile:assembleDebug`、`:mobile:lintDebug`；人工检查排序不启动机器。

## 验证、发布与回退

基线/单测命令：`ANDROID_HOME=/Users/boolean93/Library/Android/sdk JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew --offline -Dhttp.proxyHost= -Dhttps.proxyHost= :mobile:testDebugUnitTest :protocol-core:check :device-session:check`。

协议风险：不自动重试未知设备写入，不清恢复记录；新增候选秤不能自动启用停水；用户数据模型不能改写设备状态。新 UI 的目标、计时、流速都标明真实来源。

发布：本任务不打标签、不分发 APK、不改签名、不安装、不操作实机。开发 CI 不等同签名/升级/硬件验收；版本号、包名与签名身份保持现状。

回退：通过 revert PR 回退代码；新增存储独立键/目录，旧格式保持可读，不清数据，不删未知设备记录。库存纠正通过新流水而非覆盖历史。

未完成与阻塞：以清单和后续验证记录为准。额外型号的完整实机兼容须有对应样机证据；此限制不阻止其它离线功能与 UI 开发。

## 当前验证记录（2026-10-09）

- 基线移动端 JVM、protocol-core check、device-session check 通过：协议回放 39,823 checks，会话 verify 93 cases。Android 编译使用本机 SDK35 官方 aapt2，显式传 `-Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`；这是构建环境参数，不改仓库规则。
- bean-core 先红验证：addBatch stub 抛 NotImplementedError；实现后 Gradle verify 通过，覆盖重启、幂等、并发、余额调整、写入失败、损坏和溢出。独立规格及质量审查通过；Android 页面与适配仍需单独审查。
- Android AtomicFile 的 finishWrite 存在仅记日志不抛的失败路径，已用 FileOutputStream.fd.sync + Files.move(ATOMIC_MOVE, REPLACE_EXISTING) 的 AtomicDocumentStorage 替换；不支持原子 move 时明确失败，不降级截断写。新增 sync/move 失败测试。
- 当前功能全量验证、Lint、界面运行和后续模块尚在推进；本节不是完成声明。
