# Settings App Dialog 显式 Window 认证修正 Implementation Plan

> **For agentic workers:** 使用 superpowers:executing-plans；同 Sol 在现任务冻结后串行实施，不与 Settings 文件其他 owner 并行。

**Goal:** 为既有 App `language-write-rejected` 原生 Dialog 接入已存在的实测 Window buffer 平移认证，完整绑定其原图、文字和安全控件证据。

**Architecture:** 只切换该明确 Dialog 的现有 NativeWindowFrameEvidence 入口，并按相同原始字段扩充 runner 的严格验证分支。使用 accepted target 输出所有该 Dialog 附件，不在截图后重采 View。其他 Activity/Machine 门禁不变。

**Tech Stack:** Android Kotlin instrumentation、现有 protocol2 Window/PixelCopy、Python runner。

## 证据与范围

诊断：`build/task-first-evidence/ci-5d08fd6-settings-pr/diagnosis.md`、`diagnosis-index.json`。PR run39884263223、artifact12472329369、merge010998/head5d08；ZIP SHA-256 `390505712dbb5e7fac16e0ee232435090c8ceb0bfa65f3cdf6badf615c6dc727`，4270 CRC 已核。

4 个 App 初始 Activity 第二次比较在4372–4442ms内 accepted，随后明确 Dialog 因 source `[0,0,1408,1188/1198]` 被SDK回写为 `[72,72,1480,1260/1270]` 被默认入口拒绝。原 `accepted=false` 与 `RAW_CAPTURED_ORIGIN_NOT_CERTIFIED` 保留；72是此次SDK输入Rect的实测平移，不是已读取的内部surfaceInsets，不可硬编码或当既有成功。

4 个 Machine 初始窗口域像素非零、第二次到预算线，是独立未解决问题；本计划不处理它、不扩大5秒、不改四零差、不做像素mask或重试绕过。4 个Scale settings机械过不等于视觉通过。整体Settings仍partial FAIL。

遵守 [git-workflow](../git-workflow.md)，沿现授权feature/task-first-ui→develop，由root统一构建/普通推送PR；不直推主干、不微推、不合并发版、不安装或写设备。回退仅revert本测试/runner增量；保留原失败与ZIP。

## 已核现有认证能力

`NativeWindowFrameEvidence.screenshotMeasuredWindowBufferTranslation(window,root,file,observe)` 与默认入口共用同一protocol2、deadline、比较和失败保留。`copyWindow`每次创建新的`Rect(source)`交SDK，避免累加偏移；记录原windowSourceRect及每次sourceRectAfterSdk。

该模式逐次要求非负纯平移、宽高不变、S1/S2平移一致；窗口层实际尺寸必须等于decor加两侧各dx/dy，W1/W2尺寸一致，ROI在真实buffer内。ROI为原source加实测偏移；display仍按decor实际screen origin的独立rect比较。它不会把Window域与display域交叉像素相等当成功，也不会假称知道不可读的surfaceInsets。**不改这套共享实现，不新增反射API、不用RAW诊断重组accepted proof。**

## 文件责任

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt`：仅现captureDialog/dialogGeometry的App语言Dialog路径与其同帧safe-controls。
- `scripts/check_settings_visual_migration.sh`：明确该状态的mapping模式、实测字段与附件同源校验。
- 不改产品、NativeWindowFrameEvidence、Machine初始capture或其运行矩阵/像素/时间条件。

## Task 1：App明确Window接入

- [ ] 现 `language-write-rejected` 调用显式指定实测平移模式；不要把所有EXPLICIT_WINDOW自动宽松接受。其余Machine Dialog仍保持原路径/预期模式，后续若出现相同原点问题另以该态原件判定。
- [ ] 保留原AlertDialog实例、Window.callback归属、Context/owner链、decor/token/focus/attached/layout与唯一窗口检查；observe中复核同一对象，不能借owner Activity的focus或原点。
- [ ] 调用既有 `screenshotMeasuredWindowBufferTranslation(window,window.decorView,...)`。不先调用默认入口失败后另开一个5秒capture；不硬编码72、不读取隐藏surfaceInsets来替代实际SDK回写。
- [ ] 该App Dialog的safeRect、positive/negative角色、实际focused控件identity、原始observation、真实尺寸/density均进入同次 `dialogGeometry` target。原规则仍为唯一negative角色；原focused控件和48dp按钮/52dp编辑框规则保留。same-target记录自然文本/Layout/typography，不采后再用新View状态补字段。
- [ ] 成功返回后 `geometry.json=frame.target`，`dialog-typography.json.nodes=frame.target.nodes`，`safe-controls.json=frame.target`内对应安全控件对象。先写原始记录，再以该记录执行whole/角色/尺寸断言；不得另一次main读取位置/文本/尺寸制造混帧附件。失败仍保协议原件，不能把RAW截图放入成功引用。

## Task 2：runner严格分支，不删旧检查

- [ ] 按具体App状态调用的kind与mapping要求：language-write-rejected必须EXPLICIT_WINDOW + MEASURED_WINDOW_BUFFER_TRANSLATION；其他Activity仍ACTIVITY_STRICT_METRICS_DECOR，其他现明确Dialog保持原预期。禁止只写“两个模式任选”使错误路径混过。
- [ ] 将现无条件 `sourceRectAfterSdk==source` 与 `layerSize==decor` 分支化：默认路径原断言一字不弱化；新模式依据proof accepted attempt的实际字段计算检查如下，不只信mappingMode标签。

```python
source = attempt['windowSourceRectPx']
dx, dy = attempt['measuredSourceTranslationPx']
assert dx >= 0 and dy >= 0
roi = [source[0]+dx, source[1]+dy, source[2]+dx, source[3]+dy]
assert attempt['surface-beforeSourceRectAfterSdk'] == roi
assert attempt['surface-afterSourceRectAfterSdk'] == roi
assert attempt['windowLayerRoiPx'] == roi
# decor 分别来自 before/after 的 windowDecorSizePx，两端必须一致
size = [decor[0]+2*dx, decor[1]+2*dy]
assert attempt['measuredWindowLayerSizePx'] == size
assert attempt['layer-before']['sizePx'] == size == attempt['layer-after']['sizePx']
assert 0 <= roi[0] < roi[2] <= size[0]
assert 0 <= roi[1] < roi[3] <= size[1]
```

- [ ] 保留独立screen-source==decorScreenOrigin、source在decor内、Window/decor/root/token/display身份、ancestor链与真实变换、focused/laidOut/no-layout-request、metrics仅诊断等现全部检查。新模式的before/after geometry.mappingMode也必须与proof一致；不把host metrics当Dialog bounds。
- [ ] 原protocolVersion=2、timeoutMs=5000、comparison_complete≤5000、withinMatchingBudget、四路count=0、colorspaces、windowLayerMatch、displayStable、无容差/无mask、原图SHA全部保留。
- [ ] 要求accepted attempt的 before.target==after.target==geometry.json；typography与safe-controls均等于target对应部分。每个control identity必须唯一对应target nodes；角色/whole/边界/尺寸按同帧数据检查，不能从后采附件证明原图。

## Task 3：验证与交接

- [ ] 离线以合法实测平移结构检验runner局部函数；分别改动S2偏移、W2宽度、ROI越界、target控件值、四路任一路count=1、comparison_complete=5001，均必须FAIL。旧RAW诊断/accepted=false一律不能通过。不得把这些校验结构fixture当业务或视觉证据。
- [ ] `bash -n scripts/check_settings_visual_migration.sh`、内嵌Python语法检查、`git diff --check`。冻结两文件SHA交Astra实际增量复核；root合批双包构建与既有Settings矩阵，不另造Dialog/业务输入。
- [ ] 新App四配置必须产原语言拒绝Dialog accepted原图/映射/字体/安全控件同源证据，再走原dismiss、focus恢复、语言拒绝不落盘及后续原状态；不能因首阻断越过删除后续门禁。

## 验收边界

静态可执行方案不是新Dialog认证成功。必须等新protocol2证明与Astra实际原图核验，才关闭该4格的具体原点缺口；完整App后续步骤、Machine首败、其他Dialog及硬件仍各自保留状态。若实测buffer非对称或平移不稳，原样FAIL，不另猜ROI。计划未改源码、未构建、未触发CI。

## 实施记录（待 Astra 静态审）

- 仅 `language-write-rejected` 的 App Dialog 切换现有 `screenshotMeasuredWindowBufferTranslation`；其他 Dialog/Activity 与共享协议实现未改。
- 该 Dialog 的安全矩形、按钮角色、focused 控件和实测 observation 在双端 target 内取值；成功返回后从 accepted target 落盘，随后严格校验。runner 以具体路径选择实测分支，保留其他路径原断言，并核 S1/S2 source 回写、layer 尺寸、ROI、双端 target、附件与原控件 identity/文本。
- 静态检查：`bash -n`、内嵌 `VERIFY_PY` AST、`git diff --check` 通过。离线局部反例：合法平移通过；S2 偏移、W2 宽度、ROI、控件值、像素 count=1、5001ms、旧 RAW `accepted=false` 均拒绝。未构建、未提交、未触发 CI；实际 Dialog 认证仍待新原件。

## Astra 最终窄静态审核：通过

已读实际两文件diff并核冻结SHA：SettingsVisualMigrationChecks `59a8087e5435d8329bbca72301131883d3217d79e02853eecdbb0b39b443e0e5`，runner `175938ad6ed815f51984d6149a1fbc72dbf5754616117b6b12b20bc04ce00a80`。

只有language-write-rejected且owner为AppSettingsActivity选择现有实测入口；其他Dialog仍默认入口，Machine初始capture未变。safeControls在同次dialogGeometry内记录原角色/控件identity/原始observation/尺寸/owner/focus/唯一窗口，返回后先由frame.target写附件再校验，没有重新读取View补同帧证明；typography继续由同一nodes导出。

runner按app/special-states/language-write-rejected精确路径要求Measured模式，核S1/S2纯平移回写、实际两端layer对称尺寸与ROI、decor/screen映射和before/after target相等；保留默认路径的零偏移/层尺寸限制。safe附件等于target子对象，控件identity唯一对应nodes且原始observation相等，角色/完整性/尺寸规则仍在。原5秒comparison_complete、四零差、颜色域/身份/原图SHA未放宽，共享原点协议未改，RAW/accepted=false不能通过。

本轮无新增静态阻断，可交root统一构建及原矩阵取证。Sol的离线反例不替代真实运行；App四格Dialog新认证、后续步骤、视觉与Machine独立首败仍待各自证据，不签整Settings或旧失败已修复。未构建、未触发CI。
