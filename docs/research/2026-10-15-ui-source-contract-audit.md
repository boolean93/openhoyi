# UI改版：原功能与数据契约对照

2026-10-15。用途：在书面规格审阅期间补做静态查缺，不修改App，不提前进入实施。完整规格仍在原文件，未重复发起确认。

## 源码范围与证据强度

对照基线为`feature/single-screen-ui`的`ed01de0f1b6f6449257e3480053340a9bb75b160`，不是当前develop。本轮逐项核对页面操作、数据模型和新规格的承接位置；[源码文件清单](2026-10-15-ui-source-contracts.json)固定21个Activity及11个相关文件的SHA256。每份工作树文件均与该提交的Git对象内容一致。

SHA只定位证据，不证明设计没有漏项，更不证明新版已运行。下表是实施必须复核的契约清单，不是通过测试的宣告。源码路径统一在`mobile/src/main/kotlin/io/openhoyi/mobile/`，另注明模块的除外。

## 本轮发现并纠正的事项

1. **流水日期属于无依据字段。** `bean-core/.../Inventory.kt`的`InventoryEvent`只有eventId、batchId、deltaMg、balanceMg、kind、reason，没有时间戳，也没有永久的萃取ID。旧概念稿的时间列和“本杯用豆”具体归属不能照搬。改为[流水v3](task-first-ui-concepts/bean-ledger-v3.png)，展示原事件顺序及已有字段；没有原因显示“—”。不拿批次购入日期、UUID或当前时间补造发生时间，不为图片新增schema。
2. **记录工具不能合成一个笼统的导出入口。** `HistoryActivity.kt`按来源提供本机ZIP导出/旧版导入，长期复盘JSON导出/迁入最近记录/对比。新规格明确各自承接，不把迁入当作新观察或采样，也不覆盖旧笔记。
3. **库存调整不是输入增减量。** `BeanBatchActivity.adjustment`输入目标实际余量和必填原因，计算差值后预览，再确认；确认时复核当前余额。规格现明确三步与并发冲突路径。
4. **首页的辅助面板和恢复没有被主工作台替代。** 原扫描/候选、固件/告警、拨杆/睡眠、机器设置、断开、去皮、停止服务和按写入类型区分的手动恢复仍须可达；新增明确文字承接，不让普通状态芯片承担不可猜的唯一入口。原快捷1–5槽位及绑定条件也明确保留。
5. **历史参考与本地趣味反馈原本就存在。** `ExtractionActivity`/`ExtractionReference`及`BrewFeedbackDialog`不能因截图没画就删掉。规格新增明确入口、来源/对齐、原领取/播放资格、重播和恢复规则。参考轨迹不等于预设预测，趣味提示不等于风味评分。

## 21页契约清单

| Activity | 核对的原功能/数据 | 设计承接/实施复核点 |
|---|---|---|
| HomeActivity | 设备面板、告警/安全消息、分类型手动恢复、停止、快捷槽1–5、设备设置/服务、豆与秤入口 | 工作台旁明确“设备连接”和“快捷曲线”；保留原确认和门禁，不把停止服务混同停止萃取 |
| ExtractionActivity | 启动确认、slot1–5/普通7来源、准备/取消、去皮等待、停止、通知/告警、历史参考、反馈所有者 | 运行主图和固定操作；安全消息直接可见；原回调不因显示刷新重复触发 |
| CurveActivity | 搜索、两种排序、分类、旧曲线、新建/导入、分享、编辑/副本、可执行能力、绑定槽位、用于本杯 | 连续列表；当前筛选可见；绑定和用于本杯分开，不能把所有曲线开放绑定 |
| HistoryActivity | 本机/长期/旧版来源、来源专属导出、旧版导入、迁入最近记录、对比、列表恢复 | 工具随来源切换，迁入不变为自动操作；读取失败不显示成正常空列表 |
| HistoryDetailActivity | 本机找不到时从长期观察回退、状态/起止/时长/原因/秤读数、异步样本、完整笔记、复盘/对比 | 图表缺样本必须说明；未知状态不标完成；完整文本入口保留 |
| BrewComparisonActivity | 两杯ID选择/恢复、不能选同一杯、同豆/不同豆/未知豆、同/不同曲线、各杯状态/笔记/详情、样本失败 | 单指标同尺度图不替代原文字对比；缺系列或读失败不画零线；每杯完整入口可达 |
| BeanInventoryActivity | 豆款/批次和余量、入库、全量流水、批次详情、缺数据/失败 | 独立模块，不依赖BLE；用于本杯是任务入口，不暗中耗用 |
| BeanEntryActivity | 新建或已有豆款、名称、初始重量、三日期、event/batch/bean身份、保存状态/校验 | 短表单限宽；重建保留幂等身份；已有豆不能变成新豆重入库 |
| BeanBatchActivity | 余量/初始量/批次号/三日期、流水、目标余量/原因、预览/确认、并发余额复核、调整草稿 | 不把实际余量输入改成delta；前后/差值与原因可理解；冲突后重新预览 |
| BeanLedgerActivity | 按既有事件顺序逆序、批次过滤、类型/变更/余额/豆/原因/批次、局部滚动恢复 | 无事件时钟，不画时间列；无永久本杯归属不编造；负耗用中性色 |
| BeanPreparationActivity | 批次/粉量、稳定秤捕获、先保存选择再确认耗用、attempted/confirmed/关联本杯、原恢复/弃置、草稿ID | 不在onResume或保存复盘时扣豆；单次事件身份稳定；已尝试不能当未尝试取消 |
| BrewReviewActivity | beanId/beanName、dose、grind、expectation、taste、next；原豆即使不在库存也保留；光标/草稿 | 原生多行编辑；不强加研磨单位；旧豆身份不被选择器重建清空；不修改观察结果 |
| CurveEditorActivity | 名称/温度/目标液重、1–4阶段和压力/注水量、草稿ID、预览/保存、只读模式 | 阶段横轴非时间；保存不执行；1–4切换保留未展示输入，不新加duration字段 |
| CurveShareActivity | 复制、文本分享、JSON导出、成功URI后的文件分享、文档/URI恢复 | 用户显式动作才外发；无有效导出不启用文件分享；只分享参数 |
| CurveImportActivity | 显式剪贴板读取、文件、原文编辑、64KiB上限、严格UTF-8/解码、预览、草稿 | 空态无伪格式示例；拒绝超限/非法输入；预览不自动保存或执行 |
| CurveImportPreviewActivity | 非法/不可用、同ID异内容冲突、查看原版/保留/另存、正常导入、finishedSave | 一次保存；不能把冲突改成覆盖；重复导入沿用原存储结果 |
| ScaleActivity | 重量/新鲜度/能力/电池/精度、去皮状态、App计时/重置/恢复、粉量来源捕获、连接/设置 | 连接≠实时；无电池能力不画0%；已暂停有值显示继续；普通秤页不误开放粉量回填 |
| ScaleConnectionActivity | 原服务所有者、权限/蓝牙、扫描、候选、活动门禁、只读协议限制、候选滚动恢复 | 不建第二连接；不把离线解析协议画为可连接；运行中不越权切换 |
| LegacyCurveActivity | 旧文件导入、只读名称/类别/原顺序/工厂来源及已有温度/水量/重量/阶段数、读取失败 | 缺字段为未提供；没有阶段目标不能造可执行图，不能加入控制库 |
| LegacyHistoryActivity | 只读日期/配方名/时长列表、来源返回、列表恢复、读取失败 | 不在该子页新造导入按钮；保留旧源身份，不合并到原生观察 |
| LegacyHistoryDetailActivity | 旧压力bar/机器水流ml/s、历史摘要、完整来源说明、缺数据 | 不把wFlow/wTrend解释成原生秤重/流速，不补温度/累计注水/结束确认 |

## 关键源码定位

- `HomeActivity.kt`的`showDevicePanel`及设备面板、`acknowledgeManual`、`presetButtons`：设备/恢复/槽位入口。
- `HistoryActivity.kt`的`buildScreen`、`onActivityResult`、`migrateRecent`：不同来源交付与迁入；文件导出由用户明确选URI。
- `BeanBatchActivity.kt`的`adjustment`和`bean-core/.../Inventory.kt`的`adjust`/`commit`：目标值、预览、并发复核、幂等事件及落盘先于内存改变。
- `ExtractionActivity.kt`的`chooseReference`/`loadReference`：同curveId、ENDED、最多20条、样本可用；`ExtractionReference.align`保留起点证据。
- `BrewFeedbackDialog.kt`的`updateDisplay`/`claimBrewFeedback`/`leave`/`save`：资格、单次领取、离开停止与旋转不重播。现有字符串明确“趣味提示，不代表风味评分”。
- `StopActionPresentation.kt`、`ShotGate.kt`、`ExtractionController.kt`：原状态和控制入口仍是权威；设计文档不替代它们。

## 尚未证明的事项

本次没有改Kotlin、数据库、BLE、签名或版本，没有运行新版页面或安装设备。静态对照不能证明21页新布局可用，也不能证明门禁、生命周期、库存或声音回归通过。正式规格审阅后仍需独立实施计划、真实控件迁移和对应测试/读图；不以此清单作为完成标记。
