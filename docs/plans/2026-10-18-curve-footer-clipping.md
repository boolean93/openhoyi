# 曲线详情底部操作行裁切修正

- 需求与范围：修复同排操作外框在父行内裁切；保留文字、字号、点击回调、选择数据与能力门禁。按已确认设计，以控件外框垂直居中，行间距共用8dp，不保留主按钮单侧6dp偏移。
- 来源分支/基线：本批续作已建立的 `feature/task-first-ui`，基线 `116d19d`；该功能分支来源及依赖见总路线，不重建或改写历史。
- 工作分支：`feature/task-first-ui`；PR目标：`develop`。
- 改动文件：`CurveActivity.kt`；定向检查 `LanguageDetailChecks.kt`、`CurveLibraryViewportChecks.kt`、内存只读fixture `CurveFooterFixtures.kt`；对应报告 `BrewAudioInstrumentation.kt` 与严格计数门禁 `scripts/check_mock_lifecycle.sh`、`scripts/check_task_frame_ui.sh`；本规划与证据文档。
- 步骤：核对失败截图及几何 → 对照本机 Android 34 `LinearLayout.java` 测量/布局算法 → 扩充双操作完整可见与外框中心对齐断言 → 去掉按钮单侧偏移并将8dp间距放在整行 → 编译两种Mock APK并检查差异。
- 失败基线：CI `39116731390` / `3b5f29f` 的 `longFactory` 实际按钮高70px，仅可见67px；父行高77px、density=200。既有严格检查已失败，不降低其门禁。新增UI检查本地不安装运行，待后续CI实际执行。
- 验证：保留已有1.0/1.3及用户平板横竖窗口；详情语言矩阵保留 `longFactory/captured/unavailable` 并增加明确 `noPressure/fourStage`；组件检查增加两类型在实际集合可用尺寸中的布局与截图。原失败1280×703dp窗口继续由lifecycle landscape/wideFont覆盖。
- 推送与合并：本批仅本地提交；由主任务之后批量普通推送功能分支，PR四项必需CI通过再合并。禁止本批推送/合并。
- 协议与设备风险：只改显示布局及Mock检查，不改协议、单位、队列、门禁、未知记录、重试与停止。不点击本杯选择，不写曲线存储，不操作机器。
- 发布：不发版、不安装、不改签名/版本/升级路径；编译不能替代运行或硬件验收。
- 回退：使用追加revert提交撤销本批布局/测试，不清数据、不卸载、不处理未知记录。
- 未完成：新UI运行及截图须后续CI执行与Astra复核，不能由本地构建声称通过。

规则以 [Git工作流](../git-workflow.md) 为准。
