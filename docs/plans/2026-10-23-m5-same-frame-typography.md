# M5 同帧字体与基线取证 Implementation Plan

> **For agentic workers:** 使用 superpowers:executing-plans 按步骤实施；同 Sol 串行，禁止与已有 M5 owner 并行改同文件。

**Goal:** 将 M5 geometry、实际字体、number/unit 基线与同一次 protocol2 PNG 绑定，保留严格相等门禁。

**Architecture:** 复用现有 NativeWindowFrameEvidence 双端 target observation。观察函数返回数据，不在落盘前抛业务基线断言；成功取帧后只序列化返回 target，不重读 View。共享字体 helper 仅拆出无文件写入的快照方法，原 capture API 行为保持。

**Tech Stack:** Android Kotlin instrumentation、JSONObject、现有 protocol2、Python runner。

## 依据和边界

原诊断 `build/task-first-evidence/ci-5d08fd6-m5-pr/diagnosis.md`：run39884263345，原ZIP SHA `c9f706694d885a4498f84353573d90e5c657ac91d57402e5b48526ac00142280`，merge010998/head5d08。no_service_ready 的 accepted target 中 ml Layout 存在；后采时 Layout 缺失且 baseline=-1，被当成合法值算成619，与number685比较失败。已证取证不同帧，不证明稳定产品基线错位或刷新回调唯一根因。

本计划不修改 HistoryQuantity/ExtractionActivity/render频率/业务输入，不跳过 no_service_ready、不隐藏 unit、不接受 -1、不加容差/等待时长，不改 NativeWindowFrameEvidence 的5秒、四零差、协议比较。Lifecycle 的缺 g Layout 是独立失败，未经该运行证据复核不宣称顺带修复。

分支仍授权 feature/task-first-ui→develop，遵守 [git-workflow](../git-workflow.md)。root统一合批构建/提交/普通推送原PR；不微推、不直推主干、不发版/安装/硬件操作。回退仅revert本取证增量，保留失败ZIP。

## 文件责任

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/TaskTypographyEvidence.kt`：提取 `snapshot(activity): JSONObject` 与既有验证预备步骤；现 `capture(activity,directory)`继续验证、取快照、按原文件名落盘，其他调用者输出schema不变。
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`：只改现 `capture` 和必要的局部取值/验证函数，不改特殊态、tools/header/选择器实现。
- `scripts/check_extraction_history_visual_migration.sh`：新增严格绑定与基线数据校验，保留所有现有门禁。

## Task 1：只读快照与同步目标

- [ ] 将 TaskTypographyEvidence.capture 现有本地 items/layout 构造原样抽为返回 JSON 的 snapshot。字体/颜色自检等一次性检查在取帧前执行，不在两次 observe 中创建 probe、写文件、改变页面或清缓存。不要复制另一套丢字段字体采集器。
- [ ] M5 capture 的 observe 在同一次 main 调用构造原 geometry，并附加 `typographyEvidence` 和 `baselinePairs`。保留 geometry 顶层 nodes/charts，避免破坏已有下游读取。
- [ ] 每对 baseline 保存 semanticId、number/unit 实际 identity、文本、screen origin、local baseline、attached/isShown、layoutPresent、viewLayoutRequested；仅 layout 存在且 baseline>=0、无待布局时才计算 screen baseline，否则明确 invalid/原因，不能将 -1 加入坐标。
- [ ] 目标枚举仍按原实际可见 number/unit；记录数量/ID并要求两端一致，不能在某一端缺 Layout 时过滤掉该 pair。geometry 和 typography 在同一主线程回调顺序读取，无异步重采。

## Task 2：先保原件，后严格断言

- [ ] `frames.screenshot` 返回后，以 `result.target` 写 geometry.json；从它提取 typographyEvidence 写原 Activity-typography.json，从它提取 baselinePairs 写 baseline-alignment.json。禁止再调用 geometry(activity)/capture(activity)/getLocationOnScreen/baseline 获取所谓同帧数据。
- [ ] 所有已取原始 pair 先落盘，再逐对要求 valid 且两个屏幕 baseline **精确相等**；通过才标 `PASS_EXACT_BASELINE_ONLY`。稳定但错位必须失败且保留实际双方值。无 Layout 必须明确失败，不能空数组通过。
- [ ] 同步层若因目标变化/超时拒绝，自有失败 attempt target 已带字体与 pair 诊断；不更改共享层异常/重试策略，不把失败 attempt 标成 accepted，也不另开5秒窗口。
- [ ] 字体 snapshot 返回后不得持有可变 View/Spannable 引用；JSON 是当次值。不能用最后一次 callback 的外部变量代替协议返回的 accepted target。

## Task 3：runner 与反例检查

- [ ] 对每个使用 M5 capture 的目录，严格要求 geometry 的 typographyEvidence 等于独立 typography JSON，baselinePairs 等于独立 baseline JSON；accepted attempt before/after target 与落盘 geometry 相等。文件缺失、字段缺失、pair缺失不得 fallback 到旧后采文件。
- [ ] 对每 pair 校验相同 semanticId 对应的原对象/文本、layout 有效、baseline非负、无待布局，以及 `originY + localBaseline == reportedScreenBaseline`，再精确比较两方。旧 status 单独为 PASS 不能代替数值。
- [ ] 用离线拷贝的 JSON 反例验证：unit baseline=-1/layout=false→失败并保因；合法但两方差1px→失败；typography 独立文件与 target 不同→失败；before/after pair对象或值不同→失败；合法相等且完全绑定→通过。只测校验函数，不造业务fixture或硬件成功。
- [ ] `bash -n scripts/check_extraction_history_visual_migration.sh`、内嵌 Python 语法与局部反例、`git diff --check` 后冻结3文件SHA交Astra静态审。root统一双包构建及原矩阵CI，不另跑孤立业务场景。

## 验收与不外推

新 no_service_ready 必须有 accepted PNG/target、有效且精确相等的 pair、字体同帧绑定；若真实稳定帧仍错位，保留为产品候选问题并另审，不能预先关闭。原 History/Comparison 采用同 capture 的路径必须保持 schema/身份/字体语义与其既有基线断言，原tools/Dialog独立Window不改。构建通过不等于此case通过，更不等于M5视觉通过；新增原件交Astra复核。

## Astra 冻结增量静态审核：通过，待统一构建和原运行证据

直接读取并核SHA：TaskTypographyEvidence `09b76a6ca9be4b38ad4aeb3eacb8a741b80018c839d1dcf90e08d412fbe5d4c3`，ExtractionHistoryVisualMigrationChecks `5658b8e5d474ac009482f1844391623c8f3c5e7199408b0640d388aaddac5fa1`，runner `929817fe9f11a47164ebda1c0b02efe513c22d02ef4d08e2d2f3521e4dacadb9`。

`verify`在取帧前执行；原capture API仍按原文件名/字段调用verify+snapshot。snapshot保留原字体、metricRuns、颜色、actualLayout等字段，无文件写入或页面修改。M5唯一Activity screenshot入口双端captureTarget现在同时包含geometry、字体及baselinePairs，返回后仅从result.target写三类文件；随后检查invalid/精确相等，不再main重采。Layout缺失、待布局、negative baseline都记录具体invalidReason与null screen baseline，不能作为合法坐标；原实际可见number/unit的枚举语义保持，未以Layout状态筛掉pair。

runner逐一覆盖全部Activity proof，严格比较before.target==after.target==落盘geometry，字体与pair附件相等，并以可见字体对象的tag集合核pair完整性；对origin+localBaseline、Layout有效、实际文本、非负baseline及精确相等交叉检查，拒绝仅status通过。未改变共享取帧层、超时预算、四零差及特殊态业务输入；已审tools目标/禁用oracle/journal完整状态比较仍在。

本次无新增静态阻断。Sol记录的离线反例不代替Android运行；新增更完整target增加观察成本仍计原5秒内，实际能否按时认证须新CI原件判定，不提前放宽。未运行构建/CI；旧no_service_ready失败与产品基线待证状态保留，不能据静态通过宣称M5视觉完成。
