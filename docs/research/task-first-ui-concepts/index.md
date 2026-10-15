# HOYI 页面设计索引

2026-10-15。用户已认可「左侧导航＋冲煮工作台」，并补充满配萃取图为压力、注水量、出液量、温度。

**当前是21页概念稿覆盖，不是21页实现完成。** 图中数据虚构；ImageGen不能证明数值投影、设备能力或门禁正确。应用代码未改。下表记录当前版本和仍需修正的事项，不能把旧稿混作最终规格。

## 页面覆盖

| 页面 | 当前概念稿 | 核对与遗留 |
|---|---|---|
| HomeActivity 准备工作台 | [准备v1](brew-preparation-v1.png) | 已认可方向；最近使用的无语义色块需去除或换真实参数缩略图 |
| ExtractionActivity 实时萃取 | [满配v3](extraction-four-series-v3.png)、[无秤v2](extraction-no-scale-v2.png) | 同图四指标/三指标；出液来源及断线规则见能力规范；四轴竖屏仍待设计 |
| CurveActivity 曲线库 | [曲线库v2](curve-library-v2.png) | 连续滚动、不分页；按使用次数/最近使用排序；参数预览不是时间轨迹 |
| HistoryActivity 本机/长期记录 | [记录v2](history-list-v2.png) | 三数据来源保留；图仍为旧压力＋流速，须改为最新四指标语义 |
| HistoryDetailActivity 记录详情 | [详情v1](history-detail-v1.png) | 四指标、复盘入口；导航顺序需统一为冲煮/曲线/记录/豆子 |
| BrewComparisonActivity 两杯对比 | [对比v1](brew-comparison-v1.png) | 一次聚焦一个指标，两杯同尺度；四指标可切换，不默认叠八条线 |
| BeanInventoryActivity 豆子库存 | [库存v2](bean-inventory-v2.png) | 使用本豆款为主动作，新增为工具栏；库存模块仍独立 |
| BeanEntryActivity 入库 | [入库v1](bean-entry-v1.png) | 三日期等原字段保留；需改为紧凑限宽表单，去掉右侧说明墙和误导性的“不影响库存”文案 |
| BeanBatchActivity 批次详情 | [批次v1](bean-batch-v1.png) | 余量、调整与流水区分；不虚增价格/产地等数据字段 |
| BeanLedgerActivity 库存流水 | [流水v1](bean-ledger-v1.png) | 来源/事件保留；正常耗用负数需改中性色，不用危险红 |
| BeanPreparationActivity 本杯用豆 | [用豆v1](bean-preparation-v1.png) | 选择与实际扣减分开；确认前后余量清晰，恢复不重复扣减 |
| BrewReviewActivity 本杯复盘 | [复盘v3](brew-review-v3.png) | 豆子、粉量、研磨与三块笔记完整；研磨自由文本，无固定单位 |
| CurveEditorActivity 曲线编辑 | [编辑v1](curve-editor-v1.png) | 名称/温度/目标液重/阶段数/四阶段压力与注水量完整；预览为分类阶段，无虚构时间轴 |
| CurveShareActivity 曲线分享 | [分享v1](curve-share-v1.png) | 分享文本/复制/导出/已导出文件分享均在；仅含曲线参数，无私人笔记 |
| CurveImportActivity 曲线导入 | [导入v2](curve-import-v2.png) | 显式粘贴/文件/原文输入，再预览；首稿虚构JSON已弃用；空态预览禁用 |
| CurveImportPreviewActivity 导入预览 | [冲突预览v1](curve-import-preview-v1.png) | 同ID异内容只保留原版或保存副本；正常无冲突保存态另需覆盖 |
| ScaleActivity 电子秤工具 | [电子秤v2](scale-v2.png) | 重量优先；本地App计时来源明确；继续计时/归零按实际能力与状态 |
| ScaleConnectionActivity 秤连接 | [连接v1](scale-connection-v1.png) | 扫描与连接分层；Felicita实验只读限制保留；设备示例说明仅用于概念稿，不进入正式产品 |
| LegacyCurveActivity 旧版曲线 | [旧曲线v1](legacy-curve-v1.png) | 只读列表详情；仅已有元数据，不编造阶段图，不进入机器控制库 |
| LegacyHistoryActivity 旧版记录 | [旧记录v1](legacy-history-v1.png) | 旧版只读来源明确；仅压力bar与机器水流ml/s，不能套原生四指标 |
| LegacyHistoryDetailActivity 旧版详情 | [旧详情v1](legacy-history-detail-v1.png) | 缺失温度/累计水量/原生秤重/结束确认显式说明；不推断物理停水 |

## 哪些稿件不能照搬

- `extraction-running-v1/v2.png`：默认压力＋杯中流速已被四指标要求替代，只可参考布局。
- `extraction-no-scale-v1.png`：运行中连接入口不符合原设备变更门禁，弃用，以v2为准。
- `history-list-v1.png`：数据来源分组不全；v2修复来源，但图表指标仍待更新。
- `bean-inventory-v1.png`：两处填充主动作，弃用，以v2为准。
- ImageGen曲线坐标均为示意，不可直接充当绘图背景。例如部分稿件端点与文字读数/时长不严密对应，真正绘图必须从已保存的采样投影，不能按图片“校准”数据。
- 彩色装饰圆点若不编码真实类别/状态，应统一中性图标或删掉；不得凭图片虚增分类数据。

## 下一轮必须补的覆盖

| 类别 | 具体覆盖 | 当前证据 |
|---|---|---|
| 运行状态 | 未就绪、启动中、停止中、结果未知、秤中断/过期、机器遥测过期 | 已有文字状态矩阵，尚缺新版对应效果稿与运行验证 |
| 自适应 | 用户平板横/竖；大字号；键盘弹起；窄窗下图例与独立量纲刻度 | 目前主要为16:10横屏概念，不构成响应式完成证据 |
| 表单和来源 | 草稿旋转/离开恢复、校验失败、存储失败、导入无冲突/非法/重复、旧数据缺字段 | 现有实现作为语义依据，不等于新版已验收 |
| 实施 | 新布局组件、真实四指标绘图、21页面迁移 | 未开始；旧UI的CI通过不能证明新版 |
| 验证 | 用户任务可达、3秒识别、控件层级、图表单位/缺口/投影、业务门禁、实机横竖 | 未完成；不以PNG数量代替 |

## 规范与生成记录

- [官方原则及整体草案](../2026-10-15-task-first-ui-redesign-draft.md)
- [四指标来源与可用性规范](../2026-10-15-extraction-chart-capabilities.md)
- [本轮生成工具与编辑页提示词](generation-prompts-2026-10-15.md)
- 本轮其余提示词：[分享](prompts/curve-share-v1.txt)、[导入及纠错](prompts/curve-import-v2.txt)、[冲突预览](prompts/curve-import-preview-v1.txt)、[秤连接](prompts/scale-connection-v1.txt)、[旧曲线](prompts/legacy-curve-v1.txt)、[旧记录](prompts/legacy-history-v1.txt)、[旧详情](prompts/legacy-history-detail-v1.txt)。均使用内置ImageGen，不是CLI。
