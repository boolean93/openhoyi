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

## 2026-10-09 接线与恢复增量

- A1/A2：通用 ScaleObservation/Capabilities/Adapter 接入唯一 NativeDeviceHub；独立秤、连接、设置页已注册并有入口。显示资格与目标重量控制资格分离，断开/切换/关闭同时清通用读数。BOOKOO 仍为实接协议，其它候选保留离线证据，不宣称设备兼容验收。
- A3：压力/秤重估算杯中流速分图；800ms 连续采样回归修复。ShotPoint 新增 nullable brewing 真实证据，样本 V3 兼容 V1/V2（旧证据不补猜），预热点保留原始记录但实时图断开，预热不显示萃取用时。
- A6/A9：长期日志与准备页接线。准备 ID 先持久化再消费，confirm 写失败可同 ID 重试。claimed 尚未 associated 的剂量不能被下一杯覆盖；同一恢复函数在进程启动、显式日志恢复与准备页重试中填现有复盘空项，成功才确认关联，不再次扣豆。日志终态写失败可由同 identity 的近期持久化终态修复，不降级已知终态、不清用户 notes。
- A10：版本化最小参数文档、本地压力草稿编辑、主动复制/文本文件分享、粘贴/文件导入预览、冲突另存和动态草稿库已实现。草稿不可 resolve/start，不能放宽执行白名单。
- 新增用户文字覆盖现有七语言，共49份 advanced locale 文件；资源 key、XML、占位符、日期与换行已校验。没有新增翻译屏蔽或硬编码用户文案。
- 库存、protocol-core check（39,823 checks）、device-session check 与当时移动端236项测试及 Debug编译统一通过；此后增加关联恢复测试和接线，必须以最新重跑结果为准。独立审查找到的持久化关联、终态重放与比较页刷新问题已实现修正，仍需末轮复审。
- 当时未完成：A4参考对照、A11全局设置与聚焦、界面运行/布局验收、完整质量审查、生命周期/升级检查以及PR四项CI。未推送、未安装、未发版，以上不代表完整需求已交付。

## 2026-10-09 参考曲线、导航与编译资源验证

- A4：只读历史参考默认关闭，仅主动选择同曲线已结束记录；按实际萃取开始对齐，旧记录无阶段证据时标明记录起点，不把历史画成预测。图表身份绑定 ShotSeries 实际采样杯的 curveId，不使用用户后来选择的下一杯配方。
- A5：阶段目标采用固定 0–12 bar 轴和离散阶段线，流量/原始模式不冒充压力，阶段顺序不冒充时间轴。
- A11：四栏导航为冲煮/曲线/历史/豆子；应用设置独立承载主题、语言、反馈、信息与导出。设备设置保持独立控制门禁。首页新增实时萃取入口，无已选配方也能查看实际手动萃取。
- 最新全量软件回归通过：mobile Debug 与 Mock 各 245 项（各 1 skipped），device-session 646 项，app 9 项；protocol 回放 39,823 checks。新增首页入口和字符串修正后，再次运行 mobile 两套单测、Debug/Mock 构建、Mock AndroidTest 编译和 Lint，成功。
- 编译字符串校验扩展到分模块 XML，仍要求精确 key/value/语言集合一致；修复 Android 会吞掉未加引号的前导空格。Alpha/Mock 各核对 9,305 条编译字符串配置，literalRoundTrips=6；localization 21 项测试通过。
- UI 自动化从旧五栏固定坐标改为隔离 Mock 前台检查、明确控件文字和有界滚动；重复匹配报错而非猜测。新增 XML 控件定位测试 4 项通过。脚本语法已通过，但尚未在 CI 模拟器运行，不声明运行或视觉验收通过。
- 仍待：末轮独立代码审查、原生页面运行/布局验收、生命周期和升级检查、推送及 develop PR 四项 CI。新增型号实机与发布仍不在本任务授权内，不以 Mock 替代。

## 2026-10-09 末轮独立审查修正

- 参考曲线审查发现旧选择弹窗捕获过去身份：选择时先刷新当前图表身份，身份不符拒绝加载；加载入口再次核验。独立静态复查确认原 P2 路径关闭，不冒称 UI 已执行。
- 库存恢复审查发现首次 journal.observe/claim 失败可能让旧 confirmed dose 关联到下一杯（P1）。调整为 MobileService 在 Mock/真实启动派发前持久化 history 与 dose->shotId；claim 保存失败不派发；journal 回调只恢复已有绑定，不再首次 claim。日志关联失败保留原杯所有权以便重启恢复。
- 增加 ShotHistory.begin 写失败重试回归，先红确认旧实现提前占用 activeId；改为局部 next 保存成功后才替换内存、设置 activeId、通知 observer。未派发启动的存储失败不制造未知硬件结果。
- 另增加首次 journal 观察失败后重启仍绑定原杯、不能改绑下一杯的回归。独立静态末审两处修复均无新增 P1/P2；运行测试以本节之后的实际输出为准。

末轮修正后实跑：mobile Debug/Mock 各 247 tests、0 failures、0 errors、1 skipped；两种 APK 构建、Mock AndroidTest 编译、Lint 通过。编译资源复验两种 APK 各 9,305 配置匹配；Python scripts 32 项、localization 21 项通过。尚未进行模拟器运行验收、推送、PR 或发布。

## PR 与运行验收增量

- 已普通推送 `feature/advanced-experience`，草稿 PR：[#2](https://github.com/boolean93/openhoyi/pull/2)，目标 `develop`。9d483f9 源码的 branch-policy 和 upgrade 已通过；verify 当时仍运行。
- 9d483f9 的 lifecycle 和 capture 失败：平台 `uiautomator dump` 返回零却未生成层级文件，随后 cat 失败。原脚本未保留 dump stdout/stderr，当前不能断言是 idle timeout 或权限问题。新增诊断保留、删除旧 dump 防止误用旧树、零返回但无文件的失败回归；下一轮 CI 收集根因，不把诊断补齐当作运行修复已通过。
- 新增独立 `advanced-pages` CI，覆盖十四个新增页面、八语言、两主题、compact/wideFont 两窗口矩阵（每 profile 224 fixtures、56 PNG）。只在隔离 Mock 中通过业务 API 初始化明确 fixture；只滚动查看，不点击保存、分享或设备控制；内存/盘文件/设备偏好不变与同一 service 保留必须通过。截图只覆盖中文/阿语两主题，须下载后人工视觉检查；表单提交及对话框仍需单独证据。
- 新增窗口验收器与更新后的 detached shot fixture 编译通过 `:mobile:assembleMockAndroidTest`。synthetic service 现在显式有私有 history，适应启动前持久化历史的必要门禁；不将其当真实库存或硬件证据。Python scripts 33 项、脚本语法和 diff 检查通过。
- 尚未合并、发版或操作真实设备。本节不构成全部 A1–A11 完成声明。

## df376a2 运行结果与修正

- 该源码的 verify 和 upgrade 已真实通过。lifecycle 日志明确为 `ERROR: could not get idle state.`，非文件写入权限问题。首页每 250ms 无条件重设按钮文字，即使内容没变也产生文本变化；改为现有 show 增量更新方法，萃取页同类按钮一起修正。实时采样、刷新间隔、设备门禁不变，不以暂停刷新绕过验收。
- advanced-pages 真正运行到中文浅色 BeanPreparation 后失败：选项 `UI coffee · 剩余 250 g · ui-batch` 被 Android 默认 Spinner 单行省略。保留完整信息，新增可换行原生选项 adapter；相关选豆、复盘、比较、草稿阶段 Spinner 统一采用，不豁免 ellipsis 检查。
- 核对四栏主导航发现库存主页面没有底栏，lifecycle 后续无法从库存选择冲煮。库存主页面补齐四栏，详情/表单仍保留返回导航；滚动区占用剩余空间，底栏固定。
- 新增 AdvancedBusinessUiChecks：17 条本地真实控件动作路径，包括非法入库、入库保存、调整取消/确认、选粉不扣/确认幂等/重建/discard、复盘编辑、草稿保存/复制/粘贴导入/重复/冲突另存/非法拒绝。仅 Mock fixture，保留原未知记录及审计流水，不触发外部分享、SAF 或 BLE。此运行须在布局矩阵成功后才能执行，尚未宣称通过。
- 本地 mobile 两套单测、两种 APK、AndroidTest 编译和 Lint 通过；补齐库存底栏后又验证两种 APK、AndroidTest 编译和 Lint。Python scripts 33 项通过。下一轮精确源码 CI 和截图/表单运行仍待核对。

## 38e5e82 空闲超时诊断

- lifecycle 与 capture 仍在首次首页导航失败，日志依旧为 `ERROR: could not get idle state.`。仅避免相同按钮文字重设不足以解决问题；上一节描述的是源码发现与修改，不能作为根因已证实或运行已修复的证据。advanced-pages 作业仍在运行，尚无终态。
- 新增仅 Mock 的 `idleEvents=true` instrumentation：记录本包无障碍事件类型、变化类型、来源文字/坐标及 TextWatcher 回调，比较正常刷新 2 秒与仅暂停 Home 刷新 2 秒。服务不停止，记录同一 owner、采样时间及运行状态；finally 恢复刷新、监听与 flags。诊断标记不是验收 PASS。
- lifecycle/capture 在正式导航前采集诊断并保留输出；正式检查另起正常刷新进程，不沿用暂停窗口，不放宽 idle、可见性或数据门禁。脚本 33 项、bash 语法及 diff 检查通过；运行证据待 CI。

## 新增页面与业务路径运行证据

- `38e5e82` 的 [advanced-pages 作业](https://github.com/boolean93/openhoyi/actions/runs/37893343542) 已成功。下载 artifact 后核对 compact/wideFont 输出，各为八语言、两主题、十四页、224 fixtures，保留状态与无 BLE 检查均通过；两组实际 PNG 各 56 张，总计 112 张。初步视觉检查中文选豆页确认选项换行完整，阿语深色宽窗口比较页显示 RTL 与可滚动内容；不是全部截图人工验收完成声明。
- `business-flows.txt` 的实际结果为 `paths=17 inventoryEvents=3 newDrafts=3 language=zh-Hans theme=light sameService=true preservedMachinePrefs=true noBle=true`。覆盖上文列出的本地表单动作；不证明外部分享/SAF、真实电子秤、BLE 或咖啡机已验收。
- 同一源码 verify、upgrade 与 branch-policy 成功；lifecycle/capture 的空闲超时仍未修复。诊断提交 `ba93758` 已推送，完整新一轮 CI 在运行。

## 原生事件定位与展示整理

- [ba93758 lifecycle 运行](https://github.com/boolean93/openhoyi/actions/runs/37894635098) 的诊断正常完成，正式导航仍失败。下载 `idle-accessibility.txt`：正常 2 秒有 16 个 `TYPE_WINDOW_CONTENT_CHANGED`/subtree 事件，两个圆点坐标各 8 次；TextWatcher 回调为零。暂停仅 Home 刷新后 2 秒事件为零，同一 service 保持 running 且 coffeeAt/weightAt 继续前进，finally 完整恢复且 errors=0。这定位了无条件重建连接状态圆点背景，而非相同按钮文字。
- Home 圆点改为检查现有 GradientDrawable 颜色，颜色不变时不重建也不 setColor。保持 250ms 刷新、服务采样与设备门禁。正式检查仍需在新源码上证实通过，诊断实验不替代验收。
- 人工查看中文浅色与阿语深色窄屏全部十四页，确认表单换行、RTL、独立单位阶段图和固定导航；长页截图处于检查器滚动位置，不把部分屏外内容冒称丢失。其它主题/宽窗口仍需继续视觉核对。
- 库存/批次/选豆使用追加顺序中的本地化批次序号，流水与准备页不再展示内部事件 UUID；内部持久化 ID、幂等消耗、关联与全部原审计记录保持不变。缺失批次仍显示缺失提示，不补造批次。展示增量与随后圆点修正均已分别通过两 APK、AndroidTest 编译与 Lint；正式运行结果待新源码 CI。

## Canvas 大字体覆盖补齐

- 代码核对发现 ExtractionTrendView 的刻度使用 dp 字号，原 TextView 几何矩阵不能验证 Canvas 内文字。改用 Android 原生 sp 转换；左右刻度按文字宽度与 font metrics 计算边距，时间刻度首尾分别向内对齐，轴标题留两行基线间距。图表高度随系统字体增大，固定停止入口不变。未来区域仍仅表示没有实际数据，不增加预测；狭窄尾部不强塞超宽提示。
- 新增 TrendChartTextChecks：在实际 ExtractionActivity 的图表尺寸/语言/主题/系统字号下绘制生产 View，拦截实际 Canvas.drawText 调用，核对与原生 TextView 的 11sp 字号一致、文字范围未越界。两物理量各检查空态、实际采样、延长历史参考三种数据，不操作机器或改变活动图表。接入原 compact/wideFont 八语言两主题矩阵，每 profile 96 个图表 fixture，并要求专门运行标记。
- 初次测试编译因 Kotlin 可变捕获变量 smart cast 失败，改为闭包前固定 Activity 引用后编译通过。随后改用原生 sp 转换与 TextView 对照的最终源码两 APK、Mock AndroidTest 与 Lint 构建成功（43s）；脚本语法与 diff 检查通过。新增运行检查尚未在模拟器验证。

## 诊断启动配置重建

- `30e076b` 的 verify/upgrade/branch-policy 已成功，lifecycle 在诊断启动阶段失败，未执行正式导航。下载 artifact：Home 已启动 Mock service，随后出现 `recreating:HomeActivity`、旧实例 configuration 销毁、新 Home 启动；诊断保存旧 Activity 引用，等待旧实例的 focus/service 直至超时。因此本次没有事件窗口数据，不能据此断言圆点修复有效或无效。
- 诊断改为在既有 10 秒启动期限内通过 ActivityMonitor 跟踪当前 Home，要求当前实例未销毁、有焦点、完成布局且绑定的原服务 running。不增加期限、不忽略失败，不清数据，也不改生命周期正式检查。Mock AndroidTest 编译成功（18s），diff 检查通过；CI 结果待补。

## 原生首页修复证据与后台恢复检查

- [30e076b capture](https://github.com/boolean93/openhoyi/actions/runs/37895242544) 已成功，下载确认 35 张实际 PNG、Mock 开始日志和偏好文件。其正常刷新/暂停刷新两个诊断窗口事件均为零，service identity 不变且两类采样时间继续前进。已视觉查看横屏准备/萃取中以及手机待机页；压力与杯中估算流速明确分图，固定停止入口可见。不将旧源码截图替代 6e79d78 大字体修正的验收。
- [d918261 lifecycle](https://github.com/boolean93/openhoyi/actions/runs/37896095529) 的诊断也成功（正常 2 秒零事件、同一 service 持续采样）；正式页面导航、主题重建、后台可见性边缘检查已通过。失败点是从后台 `am start HomeActivity` 带回已有任务顶部 AppSettingsActivity，而脚本要求 HomeActivity。运行明确返回 HOT/原任务前台，不是应用未恢复。
- 调整为先严格确认保留 AppSettingsActivity 和 2/2 可见性计数，再通过真实“冲煮”控件导航并严格确认 HomeActivity 与计数仍 2/2；后台退出仍要求 2/3。没有清任务、销毁页面、修改可见性逻辑或放松计数。
- 原截图矩阵手机仅待机，补窄屏真实 Mock 开始、萃取中和结束截图/开始日志。所有启动仅隔离 Mock，不操作蓝牙；不以这两张待运行截图预称手机运行可读性通过。scripts 33 项、两脚本 bash 语法、diff 检查通过。

## 文件交换合约测试与当前执行限制

- 新增 CurveDocumentContractChecks、test APK 内 CurveFixtureProvider 和 test manifest：通过真实页面按钮核对七个 ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT 请求与取消/成功回传；生产 Activity 使用真实 ContentResolver 读写四份受控文件。九条路径核对精确 UTF-8 参数导出、成功后文件分享才启用、取消不读写、导入先预览不自动保存、明确确认后只读草稿、重复确认幂等、非法 UTF-8/超 64KiB 拒绝、未知 URI 拒绝。输出明确 `systemPicker=false`，不冒称真正 DocumentsUI 选择器或外部分享发送已验收。
- Provider 仅 test APK 存在，signature read/write 权限加调用包/签名核验，仅已注册 UUID 和四个固定路径可用；清理仅自身明确注册的 fixture 目录。只 Mock 请求该测试权限，main manifest 无 provider/权限。初始化保留原库存、复盘、剂量、偏好与未知记录，新增一份明确导入的测试草稿保留，设备执行资格仍被拒绝。
- 已接 `documentContracts=true` instrumentation 与 advanced-pages 单独阶段/严格标记。Android-35 与已构建 Mock/main/test 类缓存的 Kotlin compiler 直接编译通过，scripts 33 项、XML 解析、bash 语法、diff 检查通过。这不是 Gradle APK 构建、manifest 合并、签名权限实际授予或 Android 运行通过；这些仍待补。
- 本地 `40f2226` 已提交但未推送。推送的自动审批因额度用尽无法完成，操作未执行；不是代码安全否决，不绕过审批。文件测试子任务也在同一额度限制下停止，主任务已核对其代码并完成本地接线。完整 CI、最新截图/Canvas 运行和最终审查仍未完成，PR 保持草稿。

## 执行恢复与诊断实例跟踪修正

- 额度恢复后通过正常审批推送 `40f2226` 与 `9d59188`，未绕过保护、未使用 reset。完整 Debug/Mock APK、Mock AndroidTest 和 lintDebug 构建成功。核对打包 manifest：测试 provider 只在 test APK，权限为 signature；只有 Mock 请求该权限，Debug 不含测试 provider/权限。Mock/test APK 验签通过且同一 debug 证书；不是正式发布签名或运行时权限授予证据。
- 独立代码审查未发现可执行 P1/P2；明确保留 DocumentsUI、外部分享发送与手机萃取阶段人工核对缺口，不以受控结果回传冒称系统选择器验收。
- [9d59188 lifecycle](https://github.com/boolean93/openhoyi/actions/runs/37943745682) 再次在诊断启动超时，日志仍为 Home 配置重建，正式检查未运行。ActivityMonitor.lastActivity 对配置重建的实例跟踪不可靠；诊断改为启动前注册临时 Application 生命周期回调，识别实际创建/启动/恢复的 Home，销毁时仅清除对应实例引用。回调 finally 注销并记录清理结果。保持同一个绝对启动期限、焦点/布局/原服务 running 检查和独立正式验收流程；不暂停生产刷新或豁免失败。
- 此修正 Mock AndroidTest 完整构建成功（16s），分支规则与 diff 检查通过；Android 运行证据仍待新源码 CI。`9d59188` advanced-pages 作业 37943745544 仍在运行，不重启或以观察等待代替终态。

## 文件测试独立进程运行时修正

- 作业 37943745544 已失败。下载输出核对 compact/wideFont 两组布局矩阵与 17 条实际业务路径均通过；文件合约输出为 Process crashed，未执行九条文件路径。logcat 明确 test APK 独立进程初始化 CurveFixtureProvider 时缺少 kotlin.collections.SetsKt：instrumentation 可使用目标 APK Kotlin 库，不代表独立 Provider 进程也有该库。
- 测试专用 Provider 改为 Java，仅使用 Android/JDK 类，不改生产文件交换或依赖、不复制目标 APK 运行库。原 signature 权限、调用包/签名、注册 UUID、固定路径与读写限制保留；清理仅注册 fixture 目录，并不跟随符号链接。测试 APK 构建成功（17s），javap 字节码检查 kotlin 引用为零，diff 检查通过。运行时签名权限与九条文件路径仍待新源码 CI；不将字节码或编译证据冒称 Android 运行成功。
