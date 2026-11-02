# HOYI 高级功能页面设计示意

使用内置 ImageGen 生成 **28 张独立设计图**，基于 [高级功能调研报告](../../research/2026-10-08-advanced-features-research.md)。

**[打开按模块浏览的图片总览](gallery.html)** · [页面清单与提示词](manifest.json) · [检查记录](verification.json)

共 6 组：冲煮与实时状态 7 页、曲线与分享 6 页、记录与复盘 4 页、库存与联动 6 页、独立电子秤 3 页、设置 2 页。手机深色实时页是布局变体，不是另一个功能。

视觉采用暖白底、深青主操作、蓝色压力、琥珀色杯中流速与红色停止；参考 Material 3、自适应导航、Apple HIG Charts 和 Ant Design 的信息层级原则，出处见调研报告。

## 使用边界

- 这是页面设计示意，不是运行截图或已实现功能。所有设备、参数、库存和曲线均为样例。
- 每个规划主页面有独立 PNG；弹窗、空状态、权限流程及所有手机/平板变体并未穷举。
- 页面 05 是可选只读参考方案，不承诺完整走势或口感预测。
- 设备支持、执行资格和库存确认需按调研方案实现；新 UI 不改变已有控制限制。
- 生成图中的轴刻度、局部导航选中态、样例日期和部分文案仍需在正式设计时精校，不应直接当作实现规格。

## 页面清单

总览仅使用下面的选定版本；原始图和中间修订保留，不覆盖、不混入默认展示。

### 冲煮与实时状态

准备、实时、结束和断链；实时页有平板浅色与手机深色两种示意。

- 01 · [冲煮准备](01-brew-preparation-v2.png)
- 02 · [设备管理](02-device-manager-v2.png)
- 03 · [实时萃取 · 平板](03-extraction-live-tablet.png)
- 04 · [实时萃取 · 手机深色](04-extraction-live-phone-dark.png)
- 05 · [目标与历史参考](05-target-history-reference-v2.png)
- 06 · [萃取结束摘要](06-shot-summary.png)
- 28 · [萃取断链与结果未知](28-live-connection-unknown.png)

### 曲线与分享

选择与排序、查看、编辑、分享、导入和兼容性预览。

- 07 · [曲线库与排序](07-curve-library.png)
- 08 · [曲线详情](08-curve-detail.png)
- 09 · [曲线编辑](09-curve-editor-v2.png)
- 10 · [曲线分享](10-curve-share-v2.png)
- 11 · [粘贴或文件导入](11-curve-import-v2.png)
- 12 · [导入预览与兼容性](12-curve-import-preview.png)

### 记录与复盘

保留每杯记录，写下感受与下次调整，并比较两杯表现。

- 13 · [萃取记录列表](13-brew-journal.png)
- 14 · [单杯记录详情](14-shot-detail.png)
- 15 · [个人复盘编辑](15-shot-review-editor.png)
- 16 · [两杯历史对比](16-shot-comparison.png)

### 咖啡豆库存与联动

独立库存、批次、入库、流水，以及冲煮时选豆和确认消耗。

- 17 · [咖啡豆库存首页](17-bean-inventory.png)
- 18 · [豆子与批次详情](18-bean-batch-detail.png)
- 19 · [咖啡豆入库与编辑](19-bean-stock-entry-v2.png)
- 20 · [库存流水与调整](20-stock-ledger-adjustment.png)
- 21 · [选豆与粉量](21-bean-dose-selection.png)
- 22 · [投粉确认与库存联动](22-dose-consumption-confirm-v3.png)

### 独立电子秤

独立称重工具、连接管理和按实际能力展示的联动设置。

- 23 · [独立电子秤](23-scale-workspace-v2.png)
- 24 · [连接电子秤](24-scale-connect.png)
- 25 · [电子秤能力与联动设置](25-scale-link-settings.png)

### 应用与咖啡机设置

将低频偏好与设备设置移出实时萃取区。

- 26 · [应用设置](26-app-preferences.png)
- 27 · [咖啡机设置](27-machine-settings.png)
