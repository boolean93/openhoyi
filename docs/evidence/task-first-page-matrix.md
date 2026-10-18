# 全范围视觉验收状态矩阵

2026-10-18，事实同步时HEAD为`d87ef95`；各项证据按实际源码版本单独注明，未提交取证增补不计作运行证据。此表不缩减21功能页＋3设置页；所有行目前均**未完成最终视觉验收**。

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。本次只同步事实，不操作设备、不合并/发版；检查24页覆盖、状态值、引用及文档差异。后续由主任务合批普通push，可追加revert回退。

## 统一取证要求

- 已批准稿件保持不变；中文、用户横屏2560×1600/density360/fontScale1.0优先；系统栏另计。大字号、短高、IME为额外检查。
- 同业务状态的实际图、真实字体/布局测量、逐项差异核对才可验收；框架空态通过不能代替有数据页。
- 字体固定源及Google/Apple应用依据见既有研究与详细审计；固定字体身份不证明ImageGen使用相同字体。
- 下表初始框架证据来自f525db3/run38888397896；当时24份JSON已逐一解析（density/fontScale核实），但不声称24张PNG均已人工比较。较新限定结论以各行精确源码和[Astra复核§6](../superpowers/specs/2026-10-18-strict-visual-baseline.md#6-新证据复核只关闭限定布局缺口)为准，旧记录只用于追溯。

## 24页状态

| 页面 | 认可稿/范围 | 框架证据状态与缺口 | 字体测量条目 |
|---|---|---|---|
| HomeActivity | [准备v2](../research/task-first-ui-concepts/brew-preparation-v2.png) | **10-18最新：Astra已读0f0beda用户横屏font1.0/1.3真实整窗：capture-1、未选本杯豆子，91°C/27.00g/2阶段摘要及697×126px主操作完整；不是已确认18g或开始萃取状态。紧凑1.3组件改用0/12端点，关闭该两阶段场景刻度重叠，不外推四阶段/任意高度。** G1仍有摘要上下排、最近使用在右侧大按钮、右栏工具密集等已确认构图差异；阶段颜色/背景/顶栏也未对齐，G1–G4未通过。历史d873340/run39116385295组件发现的刻度失败已被上述限定新证据替代，不再写未修复 | 25 |
| ExtractionActivity | [满配v3](../research/task-first-ui-concepts/extraction-four-series-v3.png)、[无秤v2](../research/task-first-ui-concepts/extraction-no-scale-v2.png) | 784eaaf用户平板有秤/无秤实际启动截图通过；21/0重量样本、四/三指标、参数行/停止完整；绘图区514.148px。1b0deb4无告警行已收起且绘图区增至624.148px，637f83f实图确认图例含实时值、次要读数数值突出；有秤绘图区599.148px、无秤624.148px，停止完整。bcda211两态实图确认48sp数字/22sp单位，绘图区565.148px、停止完整；异常/参考/大字与最终视觉仍未验收 | 33 |
| CurveActivity | [曲线库v2](../research/task-first-ui-concepts/curve-library-v2.png) | **10-18最新：Astra已读a4786a0关联lifecycle/run39119880608的4张真实成功整窗及前后geometry：中文浅色、density200、1280×703dp、font1.0/1.3，longFactory/noPressure均显示无压力“深烘拿铁”。主按钮469×70px全幅可见，父行947×70px；详情468×60px全幅可见、局部y5..65与主按钮y0..70中心相同。关闭这两个fixture/字号的原3px底裁切。追加用户配置single/run39119880590：Astra已读2560×1600/density360/font1.0中文浅色实际整窗、populated及typography；选中“采集曲线1”（91°C/27g/两阶段），主按钮738×126px、详情738×108px均全幅可见，父行1494×126px，两按钮垂直中心均1375px，关闭该用户配置状态的底裁切。** 不外推未取证状态或严格视觉。single结果336/336，列表专项14夹具；Curve返回/重建保持factory-v3-007、position=2、offset=-142，重建保留query=factory-v3/filter=DARK/sort=MOST_USED，属于限定列表恢复通过，不是硬件动作验收。用户证据位于`build/task-first-evidence/ci-a4786a0-single-user/tablet-user-landscape/single-screen-window-checks/`的`zh-Hans-light-CurveActivity.png`、`states/...-populated.png`、`visual-baselines/zh-Hans-light-CurveActivity/{window.png,CurveActivity-typography.json}`及`list-state-result.txt`。 证据在`build/task-first-evidence/ci-a4786a0-lifecycle/mock-lifecycle/language-detail-success/footer-{landscape,wideFont}_zh-Hans_dark=false_page=CurveActivity-{longFactory,noPressure}-window.{png,txt}`。历史3b5f29f/0f0beda的469×67px失败保留追溯，已由上述限定新证据替代；根因及a7fcc86修正见[footer证据](2026-10-18-curve-footer-clipping.md)。103条列表/恢复、四阶段只读fixture等各有原范围，不冒充可执行选择、稿件或硬件验收；G1–G4仍未通过 | 31 |
| HistoryActivity | [记录v3](../research/task-first-ui-concepts/history-list-v3.png) | 空记录列表 | 17 |
| HistoryDetailActivity | [详情v2](../research/task-first-ui-concepts/history-detail-v2.png) | 712913e用户平板深色Mock结束实图确认主数值/小单位、左栏图表延伸页底、右栏分隔线已生效；121采样四指标可读。复盘字段空态，长内容/未知/缺重/大字与完整入口仍未最终验收 | 8 |
| BrewComparisonActivity | [对比v1](../research/task-first-ui-concepts/brew-comparison-v1.png) | 4240daa大字号336夹具通过；不同31/33点四指标整页已读取，实虚线/单位和30/32秒、36/35.2g吻合；记录入口已前移待新图，用户平板标准字号实图及24/16/14sp已核对；39ac261轴留白两字号10组件夹具已运行通过；整页图高/顶栏比例仍待新图验收 | 9 |
| BeanInventoryActivity | [库存v3](../research/task-first-ui-concepts/bean-inventory-v3.png) | **10-18最新：Astra已读3b5f29f用户横屏中文浅色font1.0实图及geometry，单行固定操作区已运行，-10g/+100g两条流水均108/108px完整；关闭该标准状态第二行裁切，不外推1.3/最大字。** G1仍缺原稿整体标题摘要及部分图标/分隔组织，G1–G4未通过。历史30bee76第二行14/108px失败与36f3cbc仅构建状态已被该限定新证据替代；4240daa选中250g批次与固定选豆入口只证明其当时状态，不代替全状态验收 | 11 |
| BeanEntryActivity | [入库v2](../research/task-first-ui-concepts/bean-entry-v2.png) | **10-18最新：Astra已读3b5f29f用户横屏中文浅色font1.0已有豆款模式：仅选择器显示身份，无重复只读名称框，三日期及取消/保存完整；18路径含模式草稿/重建/取消不变量。关闭该标准状态重复身份与第三日期首屏缺口，不外推新豆款1.3、IME或严格视觉。** 5abe9c2新豆款1.3开封日期需局部滚动的记录保留，不能与已有豆款标准态混为一谈。历史35db865日历入口、ff651be双模式/草稿、b6fc53c业务18路径和166594d当时三日期专项仍仅作用于对应源码/状态；同一深色section-2原图保存文字完整的纠正保留。14/16sp及取消说明有实现，不等于G1–G4验收 | 20 |
| BeanBatchActivity | [批次v2](../research/task-first-ui-concepts/bean-batch-v2.png) | 63a490c业务实图确认90/100g、90%同行、三日期分区及两条流水；与当前BeanBatchActivity无差异。v2已纠正虚构时间；后续6a61d4a进度条/底部说明待新图。用户平板/大字/长原因及字体间距仍待最终验收 | 8 |
| BeanLedgerActivity | [流水v3](../research/task-first-ui-concepts/bean-ledger-v3.png) | 051f16e用户平板有事件图已读：入库+250g/余额250g、批次与原因五列对齐，最新记录在前；当前图未覆盖扣减/调整/长原因，时间列无数据源，仍未最终验收 | 14 |
| BeanPreparationActivity | [用豆v1](../research/task-first-ui-concepts/bean-preparation-v1.png) | 8c16949业务18路径通过，三状态实图已读；左右列表/表单成立，63a490c大号余额90g→72g两态实图已读，预览/流水区分清楚；合法粉量仍留旧错误，候选已修待回归；确认态左侧批次列表/右侧摘要已调整待新图；粉量控件与底部操作区仍未完整对齐 | 10 |
| BrewReviewActivity | [复盘v3](../research/task-first-ui-concepts/brew-review-v3.png) | **10-18最新：0649834用户平板有内容实图已读：时长/克重小单位生效，三段笔记与取消/保存可见；长参数局部滚动，整页字体/比例未最终验收。** 4bd173c业务18路径通过，有内容实图已确认三段记录与取消/保存，仍为旧1600×1000配置；秒/克同大粗体差异本轮修正候选，用户平板和最终视觉待验。 b6fc53c保存后实图：左右参数/记录、三段填写、时长/秤值/结束状态和保存完整；右上分类选择未与认可稿对应，分区线与底部保存范围说明已补，构建通过待新图；候选已移除重复分组选择、统一16sp输入并补取消，五组长文检查保留，待运行；整页比例仍未验收 | 9 |
| CurveEditorActivity | [编辑v1](../research/task-first-ui-concepts/curve-editor-v1.png) | 35db865用户平板四阶段实图：1–4分段选择生效，11输入/预览/保存完整；字体、间距和预览比例仍需最终对照 | 31 |
| CurveShareActivity | [分享v1](../research/task-first-ui-concepts/curve-share-v1.png) | **10-18最新：Astra已读3b5f29f用户横屏中文浅色font1.0四阶段整图及geometry：tableShown=true，表1328×320、图1328×398，四行2.4/5.7/8.3/6.9bar及12/23/34/45ml完整，右侧操作完整。关闭该标准状态四阶段缺表，四阶段1.3/最大字仍待验。** root新读0f0beda advanced/run39117264495 wideFont分享geometry为stages=2、tableShown/tableComplete=true，仅补两阶段结论。G1仍有兼容说明和详情入口占据主操作前方的已确认构图差异，严格字色/构图未通过。历史5abe9c2两阶段普通/1.3完整仍限两阶段；4bd173c的24sp/700数字、14sp/400单位有实测，其1600×1000/density200四阶段图不冒充用户profile；abe9614分区调整保留历史效力，不代替G1–G4 | 9 |
| CurveImportActivity | [导入v2](../research/task-first-ui-concepts/curve-import-v2.png) | 空JSON输入；合法/非法/键盘另验 | 13 |
| CurveImportPreviewActivity | [冲突预览v1](../research/task-first-ui-concepts/curve-import-preview-v1.png) | **10-18最新：Astra已读a4786a0关联advanced/run39119880424中文浅色四阶段滚动前后4张真实整窗及geometry，用户窗口1138×618dp。font1.0表1470×356px、图1470×450px，默认首屏图表完整，scrollY=0；font1.3表1470×431px仍显示，图1470×360px（160dp下限），首屏表尾41px在左滚动区外，scrollY=41后全表完整；5行均可达，右操作区滚动前后Rect(1713,263–2533,736)不变。关闭原1.3整表隐藏/不可达缺陷，不声称大字全表默认首屏完整。** 证据在`build/task-first-evidence/ci-a4786a0-advanced-pages/advanced-page-screenshots/{landscape,wideFont}/import-preview-reachability/zh-Hans-light-CurveImportPreviewActivity-{before,after}-scroll.{png,txt}`及同profile顶层geometry。压力3/9/7/5bar、水量15/25/15/5ml各四行可读；1.3滚到底时配方名上缘随左栏滚动移出可见区，不是隐藏数据。0f0beda/run39117264495的tableShown=false失败已被上述限定新证据替代，仍保留追溯；冲突/其他字号、原稿字体/间距/配色及G1–G4未验收 | 9 |
| ScaleActivity | [电子秤v2](../research/task-first-ui-concepts/scale-v2.png) | 55f3f3f普通/大字号实图已证单位缩小与设备两列；仍无有效重量/趋势，用户平板及设备身份待验 | 25 |
| ScaleConnectionActivity | [连接v1](../research/task-first-ui-concepts/scale-connection-v1.png) | 无候选；候选/连接/实验协议限制需另验 | 13 |
| LegacyCurveActivity | [旧曲线v1](../research/task-first-ui-concepts/legacy-curve-v1.png) | 051f16e用户平板40条列表实图确认连续列表，但导入工具栏及未选中提示已改待图；选中重建图已读，参数真实；名称/来源与四格参数已分组待新图，完整说明保留，未最终验收 | 13 |
| LegacyHistoryActivity | [旧记录v1](../research/task-first-ui-concepts/legacy-history-v1.png) | 框架零条；另已读27ae12b的41条记录列表，非空列表成立，构图待对齐 | 13 |
| LegacyHistoryDetailActivity | [旧详情v1](../research/task-first-ui-concepts/legacy-history-detail-v1.png) | 051f16e用户平板浅深实图确认：bar/ml/s双轴、中间秒刻度、实虚线重叠可辨及右栏详情已生效；24/20/14sp已实测；右栏来源/缺失/限制三组已调整、八语言同步待新图，未最终验收 | 9 |
| AppSettingsActivity | [设置v1候选](../research/task-first-ui-concepts/app-settings-v1.png)：保留配置/安全说明、允许整页滚动；候选紧凑分组已实现、构建通过、未运行验收 | 39ac261用户平板外观/语言/偏好首屏已读，3c0019c用户横屏首屏和底部实图已读，版本/包名及导出/机器入口滚动完整、偏好未变；10bf727标准浅色四行信息实图已对齐；动作图标及大字深色仍未对齐 | 26 |
| MachineSettingsActivity | [机器设置v1候选](../research/task-first-ui-concepts/machine-settings-v1.png)：保留配置/安全说明、允许整页滚动；4d308a7实图已确认四个回读标签/值行；831aa1c实图确认温度标签同行/下划线标签，scrollY=45后三控件在系统可见区内；大字及其他标签仍待验收 | 模拟回读设置；真实写入/回读不在视觉证据范围 | 37 |
| ScaleSettingsActivity | [秤设置v1候选](../research/task-first-ui-concepts/scale-settings-v1.png)：允许整页滚动；bcda211未连接首屏实图确认三组及20/16/14sp、16/8dp；4d308a7底部协议说明实图与滚动断言通过；深色/大字/能力状态仍待验收 | 4d308a7未连接态精度/能力/说明分组与底部实图已读，偏好未改变。真实有能力状态另验 | 14 |

## 每页G1–G4当前状态

依据[Astra严格基线§4/§6](../superpowers/specs/2026-10-18-strict-visual-baseline.md)，G1为间距/构图，G2为字体，G3为字号/行高，G4为背景/状态色。精确候选参数尚未冻结，一律保留 `BASELINE_PENDING`；只有Astra已明确的构图差异或仍未有新运行关闭的裁切记为 `FAIL`。没有任何页获得 `PASS`；局部缺口关闭、编译成功和业务通过不改变这一点。每页其余必验状态仍待取证，不能由下表外推已验。

| 页面 | G1 | G2 | G3 | G4 | 当前判定边界 |
|---|---|---|---|---|---|
| HomeActivity | FAIL | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | Astra§6：摘要/最近使用/右栏构图不同；限定刻度缺口已关闭 |
| ExtractionActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 运行态证据不等于精确四项冻结 |
| CurveActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | a4786a0真实整窗关闭density200中文浅色1.0/1.3无压力fixture及density360中文浅色1.0两阶段采集曲线的底裁切；用户配置列表返回/重建锚点保持；同稿严格四项另验，不记PASS |
| HistoryActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空同状态及精确基线待补 |
| HistoryDetailActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 限定四指标实图不覆盖全部状态 |
| BrewComparisonActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 实图/轴专项不等于精确构图冻结 |
| BeanInventoryActivity | FAIL | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | Astra§6：标题摘要/图标/分隔差异仍在；两流水标准态裁切已关闭 |
| BeanEntryActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 已有豆款标准态缺口关闭，不外推新豆款大字/IME |
| BeanBatchActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 同稿精确对照与长内容待补 |
| BeanLedgerActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 调整/耗用/长原因状态待补 |
| BeanPreparationActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 已有业务状态不能替代精确四项 |
| BrewReviewActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 有内容实图不覆盖全部字体/比例/IME |
| CurveEditorActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 四阶段完整不等于精确字体/间距验收 |
| CurveShareActivity | FAIL | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | Astra§6：兼容/详情与主操作顺序有差异；标准四阶段缺表已关闭 |
| CurveImportActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 合法/非法/IME同状态与基线待补 |
| CurveImportPreviewActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | a4786a0中文浅色1.3四阶段全表保留并可局部滚达、右栏固定；1.0首屏图表完整；未通过严格四项 |
| ScaleActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 无读数不能替代有效重量/趋势状态 |
| ScaleConnectionActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选/连接/限制/错误状态待补 |
| LegacyCurveActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空只读图仍需同稿精确对照 |
| LegacyHistoryActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空列表证据不等于精确构图冻结 |
| LegacyHistoryDetailActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 双轴/来源实图不覆盖全部必验状态 |
| AppSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 设置稿仍为候选，完整滚动/状态待精确验收 |
| MachineSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选稿与模拟回读不证明真实写入或四项通过 |
| ScaleSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选稿、未连接/能力状态须分别核对 |

## 已获得的其他证据（不能跨版本外推）

- 709a337 advanced/run38886601670：15业务页8语言×2主题、常规/1.3字号各240夹具通过。四阶段分享图已人工核对；分区调整abe9614在其后，须新图。
- 27ae12b single/run38885839398：普通横屏通过，用户平板332/336、大字号334/336；失败仅旧入库/编辑裁切，后续f525db3/run38888405636三个横屏各336夹具通过，第四阶段裁切专项已关闭。复盘IME本轮未复现不等于根因已修。
- cbe8076补用户平板浅色真实Mock启动流程；不是硬件验收，也未覆盖无秤运行态。
- 55f3f3f电子秤普通/大字号实图已核对单位和设备行，仅无读数状态；fb19415指标分段、edd89da摘要表已有实际高级夹具图，但空图不能覆盖有采样双轨迹。

## 下一步优先级

1. **P0取证**：按[Astra执行顺序§7](../superpowers/specs/2026-10-18-strict-visual-baseline.md#7-交给-sol-的取证接口和执行顺序)及[颜色取证计划](../plans/2026-10-18-visual-color-evidence.md)补齐整窗、manifest、逐span/Canvas和实际颜色。d87ef95的取证代码已提交、构建通过仍不等于已有运行产物；[footer成功整窗补充](2026-10-18-curve-footer-clipping.md)待样张批次合编译/CI，保留严格宽高检查。
2. **P1三页原生样张**：执行[已定最小计划](../plans/2026-10-18-strict-native-samples.md)，准备四阶段已选/本杯已确认、四阶段分享、三豆款非空库存，共12张浅深色与1.0/1.3候选整窗。Astra按原稿锚点逐项审查后才可冻结；候选不能追认当前偏差，也不能当产品页已验收。
3. **P2完整24页**：按准备/运行、曲线与导入分享、记录与复盘、库存与用豆、工具与设置五组，先收齐同版本同状态证据，再集中修改/构建/一次取证；保留最大字号、短高、IME及业务安全要求。设置三页仍在范围，原生样张只覆盖的共享规则不能外推其余页面；不取消在途CI、不以微推送代替逐项验收。
