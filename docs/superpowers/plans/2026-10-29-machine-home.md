# 机器首页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. 用户已批准设计并指定Astra规划/验收、Sol开发；直接执行，不重复询问方案或分工。

**Goal:** 将首页做成机器大图、双锅炉实时温度/压力与五快捷槽的清晰控制面板。

**Architecture:** 只替换HomeFragment首屏布局，继续使用Main单壳、原MobileService订阅、LiveTelemetry新鲜度与全部写入/启动门禁。协议字段和业务流程不扩展；主图是已有机器资产，非新设备控制。

**Tech Stack:** Kotlin、Android View、现有资源/字体/主题、原生Mock截图、现有JUnit与instrumentation。

## 批准依据与边界

- 用户已批准图片：`docs/ui/machine-home/2026-10-29-approved-home-reference.png`，并追加两锅炉各显示实时压力。Astra已实际view_image；图中未画压力，实施须在右侧每锅炉加入压力，与温度并列。
- 使用`mobile/src/main/res/drawable-nodpi/hoyi_machine.png`，保持机器真实外形/比例，禁止把效果图中的文字/数据烧进资产。
- 来源为既有`feature/task-first-ui`，目标`develop`/PR4；遵守`docs/git-workflow.md`。开工已核工作树干净。Sol不覆盖其他人文件；Astra只写本计划并审查，root统一构建、普通提交推送，不合并发版。
- 用户前序授权本台Mock覆盖安装延续：当前v14、下一次v15由root处理，保持包名/签名、版本递增、数据hash；不得卸载清数据。无辅助测试APK安装，无真实咖啡机命令。可运行现有Mock screenshot CI。本轮不改协议/数据库/服务，不新增唤醒命令。
- 回退由root追加revert/修复提交；不恢复旧APK覆盖新数据、不清未知写入。
- 曲线流量模式适配另列**未做**，不混入本轮。高级页面/侧栏其它设计不扩改。

## 已核字段合同（源码优先）

`protocol-core/src/main/kotlin/io/openhoyi/protocol/Protocol.kt:31–42`以及`HomeFragment.render()`已提供以下字段。明确纠正任何旧记录中“Idle没有蒸汽温度/压力”的错误：**Idle两者都有；Extraction才缺蒸汽两项。** 不新增解码，不按温度推算压力。

| 新鲜帧 | 冲泡温度 | 冲泡压力 | 蒸汽温度 | 蒸汽压力 |
|---|---|---|---|---|
| IdleTelemetry | brewTemperatureHundredthsC / 100 | brewPressureTenthsBar / 10 | steamTemperatureHundredthsC / 100 | steamPressureTenthsBar / 10 |
| ExtractionTelemetry | brewTemperatureHundredthsC / 100 | pressureTenthsBar / 10 | — | — |
| 无帧/过期/断连 | — | — | — | — |

所有分支先调用现有`LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now)`；其READY、时间合法、年龄≤1500ms条件保持。温度沿现有格式/本地化，压力一位小数、单位bar；有效0.0是读数，不当缺失。目标仅取`settings.brewTemperatureC/steamTemperatureC`，明确“目标”，缺失亦—。不从目标填实测、不沿用过期值、不把冲泡压力复制成蒸汽压力。

## 确定布局与验收标准

- 保留104dp侧栏、现有间距与设备状态文字。首个Tab只改可见名称为“首页”；使用独立本地化键`task_home_tab`，不全局替换`ui_brew`或改路由枚举。
- 内容顶栏：HOYI/机器连接状态、标题“我的HOYI”、真实睡眠状态、设备设置/连接入口。不得无依据显示“已就绪”；图中该徽章仅在原业务已有相同事实时显示，否则省略。
- 常规平板：首屏主区约45%机器hero、55%右侧参数，两区间16dp。机器FIT_CENTER、透明底、整机不裁切，使用剩余高度放大；不增加无关统计。
- 右侧上下两张锅炉卡，标题“冲泡锅炉/蒸汽锅炉”。每卡温度、压力两列，标签16sp、主值建议40–48sp、单位18–20sp同基线；卡内16dp，数值列间16dp。目标为独立14–16sp行，开关/动作点击至少48dp。保留真实缺测提示；文字自然换行，不为容纳大图省略重要读数。
- 五槽区独立固定在首屏底部，五张等宽卡8dp间距、槽号/实际名称14sp；已配置槽才能展示其真实阶段预览。允许复用现有CurveStageView等简图，但不能照生成稿虚构平滑曲线/时长。无配方时显示未配置。不增加不存在的“编辑快捷配方”行为。
- 当前配方与“开始萃取”保持清晰主动作；活动杯入口返回原萃取页。五槽沿既有合法PresetSlots与确认流程，不暗改槽协议。未知/不可执行配方仍进入曲线选择或原提示。
- 短高：先缩小hero，必要时不占首屏高度；双锅炉可转并排紧凑卡，但四实测读数/单位、目标、两开关、五槽、开始入口仍优先首屏。低频设备/准备详情继续原真实入口可达。告警/安全提示不可被hero遮挡或仅移进详情。
- 不改ExtractionFragment、大图、固定停止及手动杯自动路由。任何布局改变不得影响同service/同series连续性。

## 文件所有权与实施步骤

### 1. Sol：首页首屏与真实读数

**Modify:** `mobile/src/main/kotlin/io/openhoyi/mobile/HomeFragment.kt`。可在同目录新增小型`MachineHomeView.kt`承担纯布局，只有确有助于隔离首屏时创建，不搬Activity/业务。

- [ ] 先为首屏新增稳定压力节点`product/home/brew-boiler/pressure`、`product/home/steam-boiler/pressure`的验证，保原temperature/target/toggle/quick-launch/direct-preset标签。原Idle/Extraction/过期fixture应分别验证四值、蒸汽双—、四—，再替换布局。
- [x] 复用现有render分支，将真实压力同时呈现于首屏，详情原压力节点保留；缺测分支覆盖新增节点，避免最后一帧残留。
- [x] 按上节构图创建hero/锅炉/五槽/启动区，使用已有资产与字体主题；不制造静态示例读数。核心绑定必须逐一映射上表四列，现有`HomeFragment.render()`为实现依据，不引入新的传感器推断。

- [x] 逐项核原`confirmBoilerChange`、`MachineSettingsEditGate.allowed(...) && !BuildConfig.MOCK_MODE`、`host.showExtraction(..., confirmStart = true)`、`showDevicePanel`、safetyWarning及离页dialog清理未被绕过。效果稿开关不是乐观写入许可。

### 2. Sol：名称和本地化

**Modify:** `mobile/src/main/kotlin/io/openhoyi/mobile/TaskNavigationView.kt`（仅首Tab文字映射）、`mobile/src/main/res/values/strings.xml`、`localization/catalog/source.json`、七语言catalog与生成的values-*/strings.xml。

- [x] 新增首页/锅炉标签等必要文字，沿现有catalog更新并生成；首Tab改“首页”不影响现有“冲煮”业务文案。
- [x] 不重绘机器图、不改104rail和设备区行为、不扩其它页面。必要的键数断言只按实际生成结果更新，不改断言逻辑。

### 3. Sol与root：有限验证

**Root owns:** `scripts/capture_mock_ui.sh`两处首页selector及批准参考图，不覆盖其编辑。

**Existing checks:** `mobile/src/test/kotlin/io/openhoyi/mobile/LiveTelemetryTest.kt`；`mobile/src/androidTest/kotlin/io/openhoyi/mobile/MainNavigationChecks.kt`、`HomeWorkbenchChecks.kt`、`ProductVisualMigrationChecks.kt`。首Tab资源期望另同步`LanguageContextChecks.kt`、`LanguageUiLayoutChecks.kt`、`LanguagePageChecks.kt`的Main专用分支，非Main的ui_brew保留。只更新与本首页变化有关的实际视图入口/几何，不删原服务、告警、确认、首屏与数据保持门禁。

- [x] 读数验证覆盖：新鲜Idle含不同冲泡/蒸汽压力及0.0；新鲜Extraction蒸汽双—；READY但1501ms过期、断连四—；目标仍独立。不借Mock伪造协议支持。
- [x] root统一构建（Sol/Astra不并发启动Gradle）：

```sh
python3 localization/validate_catalog.py
python3 localization/android_resources.py --check
./gradlew :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleDebug :mobile:assembleMockAndroidTest :mobile:lintDebug
```

预期现有单测与新增读数断言通过，两变体/测试APK和lint成功；资源验值按现有脚本执行。工具环境参数沿root已验证配置，不新增依赖。

- [ ] 复用现有Mock screenshot CI核首页与原导航/确认流程；本台仅安装Mock v15，先签名/版本/APK hash及原数据校验，不安装辅助测试APK、不发送真实设备命令。

### 4. Astra：实际图与交付

- [x] 对比批准稿与本台中文浅/深首页原图：hero比例/留白、四参数大字、目标区别、五槽真名和启动全部清楚，rail文字完整，未知值没有伪装实时。
- [ ] 核截图对应状态来源；正常Idle必须看到真实两锅炉温度/压力，缺测不能用图片静态值填充。短高沿既有检查保重要控件，常规平板不能为了短高退回稀疏小字首页。
- [ ] 确认原告警/详情/启动确认可达，萃取大图与停止未改。新缺陷只作本首页定点修复。
- [ ] 记录软件/代表图通过范围与硬件待验；root只提交本任务文件、普通推PR4，保留未完成的曲线流量适配，不称全项目完成。

## 当前进度

| 项目 | 状态 |
|---|---|
| 用户设计批准、Astra读图与字段核实 | 已完成 |
| 首页布局、首屏双压力、首页Tab名称 | 已实现；静审及本台v16中文浅/深正常态实图通过 |
| 单测/构建/原生Mock流程 | root确认340 tests、0 failure、1 skip；构建/lint成功，两variant资源11041；完整原生流程不据此外推 |
| 本台Mock v16首页原图 | 已实际图审通过：四值、双目标、五槽、开始、侧栏完整；v15目标裁切已闭合 |
| 短高CI/缺测状态原生视觉 | 未执行新一轮验收，保留待验 |
| 真实温度/压力/锅炉写入与停水 | 不在本次Mock验收范围 |
| 曲线流量模式适配 | 另项未做 |

### 首版静审记录（2026-10-29）

- `MachineHomeReadings` 由同一次 `LiveTelemetry.machine` 新鲜度过滤结果生成四值：Idle双温除100、双压除10；Extraction仅冲泡温压，蒸汽双—；有效0不丢失。新增测试检查1500ms有效、1501ms过期及断连。目标温度仍来自独立settings。
- `HomeFragment` 原锅炉确认与编辑门禁、五槽确认入口、活动萃取跳转及详情/告警路径保留；`MachineHomeView` 只负责机器图与双卡。首页专用导航键仅影响Main的BREW，其他ui_brew语境不改。
- 正常首屏最小高度静算约658dp，本台约667dp可用高，余量约9dp。此前742–774dp是审查加法错误，已撤回“必裁切”判断；不以错误预算要求动态重构。实际长槽名、字号和告警显示仍需按原图/原有检查判断，不预先称视觉通过。
- 固定360dp机器/双卡区域、40sp数值与缩小单位、五槽80dp及底部启动的实际呈现尚待本台Mock v15截图。构建中保持生产冻结，本记录不替代构建结果或硬件验收。

### Mock v15 实图审查（2026-10-29）

实际查看 `build/machine-home-2026-10-29/home-light.png`（Xiaomi 2560×1600）：机器hero左、双锅炉右的视觉层次符合批准方向；四参数大字与单位、五槽真实名称、开始萃取和104dp侧栏设备文字均在首屏清楚可见。当前仅覆盖中文浅色正常Idle Mock状态，0.0冲泡压力正确显示。

**未通过项：** 两卡目标温度行下半部被裁切。只回收卡内读数上间距/上下padding，不缩主要四数值字号，不增总高度挤出底部启动；下一Mock v16必须复验目标全文与底部留白。深色及缺测状态尚不据此批准。

root确认本批340单测0 failure/1 skip、构建和lint通过、两variant资源各11041；安装保留17数据文件。以上构建/安装为root报告，截图为Astra实际查看；不构成硬件协议验收。

### Mock v16 最终有限验收（2026-10-29）

- [x] Astra实际查看 `build/machine-home-2026-10-29/v16/home-light.png` 与 `home-dark.png` 两张Xiaomi原图。双目标“目标93/120 °C”全文完整且有底部留白，v15裁切闭合；主40sp四数值/单位、五槽全文、当前曲线、开始按钮和104dp侧栏设备状态均在首屏完整可读。机器图保持比例，浅深主题层次清楚。本轮中文正常Idle Mock状态视觉通过。
- [x] root确认v16完整构建、340 tests零failure/1 skip、lint通过；两variant资源各11041匹配。安装APK hash及17数据文件验证通过，切换主题后已恢复原浅色首页。未安装辅助测试APK。
- [ ] 新短屏CI、缺测/断连状态的原生截图及完整导航确认流程尚未在本批复验。字段缺测已有静审/单测证据，不等同原生视觉全状态通过。
- [ ] 真实机器温压、锅炉写入和停水仍待硬件验收；曲线流量模式适配仍为另项未做。以上有限验收不宣称全应用或全CI通过。

文档已冻结，交root按PR4流程提交/推送；本节不预先记录提交结果。
