# 全范围视觉验收状态矩阵

2026-10-16，工作树代码ce8c964；各项证据按实际源码版本单独注明。此表不缩减21功能页＋3设置页；所有行目前均**未完成最终视觉验收**。

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。本次只整理实证，不操作设备、不合并/发版；文档检查为页面覆盖数量、PNG/JSON存在及JSON解析。后续普通push，可追加revert回退。

## 统一取证要求

- 已批准稿件保持不变；中文、用户横屏2560×1600/density360/fontScale1.0优先；系统栏另计。大字号、短高、IME为额外检查。
- 同业务状态的实际图、真实字体/布局测量、逐项差异核对才可验收；框架空态通过不能代替有数据页。
- 字体固定源及Google/Apple应用依据见既有研究与详细审计；固定字体身份不证明ImageGen使用相同字体。
- 下表“框架证据”均来自f525db3/run38888397896。24份JSON已逐一解析（density/fontScale核实），但不声称24张PNG均已人工比较。

## 24页状态

| 页面 | 认可稿/范围 | 框架证据状态与缺口 | 字体测量条目 |
|---|---|---|---|
| HomeActivity | [准备v2](../research/task-first-ui-concepts/brew-preparation-v2.png) | 未选曲线；最近配方/本杯就绪待同状态图 | 25 |
| ExtractionActivity | [满配v3](../research/task-first-ui-concepts/extraction-four-series-v3.png)、[无秤v2](../research/task-first-ui-concepts/extraction-no-scale-v2.png) | 784eaaf用户平板有秤/无秤实际启动截图通过；21/0重量样本、四/三指标、参数行/停止完整；绘图区514.148px。1b0deb4无告警行已收起且绘图区增至624.148px，字体/间距/曲线区域仍未最终验收 | 33 |
| CurveActivity | [曲线库v2](../research/task-first-ui-concepts/curve-library-v2.png) | 有列表；选中详情与排序后的状态需另图 | 31 |
| HistoryActivity | [记录v3](../research/task-first-ui-concepts/history-list-v3.png) | 空记录列表 | 17 |
| HistoryDetailActivity | [详情v2](../research/task-first-ui-concepts/history-detail-v2.png) | 63a490c实际结束图已读：32.2秒/22.40g、123采样点、四指标同图；单位缩小、右栏分区及侧栏固定复盘候选待图；图高释放尚未实测，未最终验收 | 8 |
| BrewComparisonActivity | [对比v1](../research/task-first-ui-concepts/brew-comparison-v1.png) | 35db865用户平板336检查通过；实读压力/水量/温度浅色与重量深色专项图，按钮完整、双轨迹和量纲真实切换；051f16e实图确认交换位置/图例/蓝色/12sp轴标；两杯重合不能证明区分效果，新增不同样本整页图待CI；e7929eb原生组件1.0/1.3各9场景通过并已读实虚线/缺数据图，12sp误报关闭；整页未完成视觉验收 | 9 |
| BeanInventoryActivity | [库存v2](../research/task-first-ui-concepts/bean-inventory-v2.png) | 库存空态 | 11 |
| BeanEntryActivity | [入库v2](../research/task-first-ui-concepts/bean-entry-v2.png) | 35db865实图三日期日历入口完整；已按入库v2改双模式并保留独立草稿，候选构建通过；ff651be两模式填写实图与身份/草稿恢复路径已读；b6fc53c业务18路径及收尾不变量已通过；166594d三个横屏job通过，用户平板浅深/1.3字号实图三日期完整，裁切关闭；深色专项单帧保存文字缺失疑点待查，未最终视觉验收 | 20 |
| BeanBatchActivity | [批次v1](../research/task-first-ui-concepts/bean-batch-v1.png) | abe9614业务图有90g/初始100g、三日期和两条流水；摘要/日期分区修正待新图 | 8 |
| BeanLedgerActivity | [流水v3](../research/task-first-ui-concepts/bean-ledger-v3.png) | 051f16e用户平板有事件图已读：入库+250g/余额250g、批次与原因五列对齐，最新记录在前；当前图未覆盖扣减/调整/长原因，时间列无数据源，仍未最终验收 | 14 |
| BeanPreparationActivity | [用豆v1](../research/task-first-ui-concepts/bean-preparation-v1.png) | 8c16949业务18路径通过，三状态实图已读；左右列表/表单成立，63a490c大号余额90g→72g两态实图已读，预览/流水区分清楚；合法粉量仍留旧错误，候选已修待回归；确认态构图与大面积留白仍未完整对齐 | 10 |
| BrewReviewActivity | [复盘v3](../research/task-first-ui-concepts/brew-review-v3.png) | b6fc53c保存后实图：左右参数/记录、三段填写、时长/秤值/结束状态和保存完整；右上分类选择未与认可稿对应，分区线与底部保存范围说明已补，构建通过待新图；右上选择与比例仍未验收 | 9 |
| CurveEditorActivity | [编辑v1](../research/task-first-ui-concepts/curve-editor-v1.png) | 35db865用户平板四阶段实图：1–4分段选择生效，11输入/预览/保存完整；字体、间距和预览比例仍需最终对照 | 31 |
| CurveShareActivity | [分享v1](../research/task-first-ui-concepts/curve-share-v1.png) | 曲线不存在；abe9614业务实图四阶段表格完整，预览/参数标题及底部说明归位；字体/图高仍待对齐 | 9 |
| CurveImportActivity | [导入v2](../research/task-first-ui-concepts/curve-import-v2.png) | 空JSON输入；合法/非法/键盘另验 | 13 |
| CurveImportPreviewActivity | [冲突预览v1](../research/task-first-ui-concepts/curve-import-preview-v1.png) | b6fc53c新导入/冲突有数据图已读：四阶段/单位/完整参数可见；冲突缺警示层级，候选补背景/边界/16dp内边距待图；预览分区标题与左右比例仍不一致 | 9 |
| ScaleActivity | [电子秤v2](../research/task-first-ui-concepts/scale-v2.png) | 55f3f3f普通/大字号实图已证单位缩小与设备两列；仍无有效重量/趋势，用户平板及设备身份待验 | 25 |
| ScaleConnectionActivity | [连接v1](../research/task-first-ui-concepts/scale-connection-v1.png) | 无候选；候选/连接/实验协议限制需另验 | 13 |
| LegacyCurveActivity | [旧曲线v1](../research/task-first-ui-concepts/legacy-curve-v1.png) | 051f16e用户平板40条列表实图确认连续列表，但导入工具栏及未选中提示已改待图；选中重建图已读，参数真实；名称/来源与四格参数已分组待新图，完整说明保留，未最终验收 | 13 |
| LegacyHistoryActivity | [旧记录v1](../research/task-first-ui-concepts/legacy-history-v1.png) | 框架零条；另已读27ae12b的41条记录列表，非空列表成立，构图待对齐 | 13 |
| LegacyHistoryDetailActivity | [旧详情v1](../research/task-first-ui-concepts/legacy-history-detail-v1.png) | 051f16e用户平板浅深实图确认：bar/ml/s双轴、中间秒刻度、实虚线重叠可辨及右栏详情已生效；24/20/14sp已实测；右栏仍为说明长段，未按稿件分组，未最终验收 | 9 |
| AppSettingsActivity | 设置页：保留配置/安全说明、允许整页滚动；无独立认可概念稿，不能假称已与稿一致 | 外观/语言/偏好首屏；允许整页滚动，底部项另验 | 26 |
| MachineSettingsActivity | 设置页：保留配置/安全说明、允许整页滚动；无独立认可概念稿，不能假称已与稿一致 | 模拟回读设置；真实写入/回读不在视觉证据范围 | 37 |
| ScaleSettingsActivity | 设置页：保留配置/安全说明、允许整页滚动；无独立认可概念稿，不能假称已与稿一致 | 未连接能力说明；有能力状态另验 | 14 |

## 已获得的其他证据（不能跨版本外推）

- 709a337 advanced/run38886601670：15业务页8语言×2主题、常规/1.3字号各240夹具通过。四阶段分享图已人工核对；分区调整abe9614在其后，须新图。
- 27ae12b single/run38885839398：普通横屏通过，用户平板332/336、大字号334/336；失败仅旧入库/编辑裁切，后续f525db3/run38888405636三个横屏各336夹具通过，第四阶段裁切专项已关闭。复盘IME本轮未复现不等于根因已修。
- cbe8076补用户平板浅色真实Mock启动流程；不是硬件验收，也未覆盖无秤运行态。
- 55f3f3f电子秤普通/大字号实图已核对单位和设备行，仅无读数状态；fb19415指标分段、edd89da摘要表已有实际高级夹具图，但空图不能覆盖有采样双轨迹。

## 下一步优先级

1. 四阶段/IME已获f525db3专项通过；继续等待有数据对比页四指标图，逐项核对线型、单位和字体。后续新增布局仍需对应源码回归。
2. 获取用户平板浅色运行萃取及无秤状态，逐项核对主值、四/三指标、停止、本杯上下文。
3. 用已有业务截图填补库存/复盘/导入有数据状态；旧版三页须找实际有内容夹具，空态不能覆盖。
4. 设置页完整滚动/状态检查及统一字体、间距逐项测量仍保留，不因缺少概念稿而从范围删除。
