# 首页上一杯曲线横条

- 需求与范围：首页快捷曲线区改成「上一杯使用的曲线」横条与下方五个快捷槽位两排；横条替换原「当前选择名称＋快捷启动」行，不叠加重复按钮。当前所选曲线仍可从既有其他入口访问，绝不把它当上一杯。
- 来源分支/基线提交：已 `git fetch origin`，`origin/develop` 为 `30bfc66bc8a416794de941eaa371bf5c5f15c12c`。
- 工作分支：`feature/home-last-curve`，工作树 `/Users/boolean93/workspace/hoyi_app/openhoyi-home-last-curve`。
- PR目标：`develop`。
- 改动文件与步骤：
  1. 扩充 `CurveUsageLedger` 的只读最新用例查询，并先写测试验证按实际观察的开始时间选最后一杯；不使用按可启动曲线筛选后的「最近」结果。
  2. 建首页上一杯展示模型，比较永久用例与持久 `BrewJournal` 的手动杯记录，并检查本地杯历史是否比账本更新；无记录、手动、数据不可确认、已删/不可重用、可重用分别呈现真实状态。工厂/已采集稳定曲线沿用 `CurveStageParameters.preview` 和曲线库共享刻度。新使用的可编辑草稿从启动时冻结的配方生成参数指纹，在实际运行被观察后追加到账本；仅当前文档参数与历史指纹相同才可复用，旧三列记录保守处理。
  3. 首页以独立横条替换原当前选择行，保留 `product/home/quick-launch` 稳定 tag；点击「再次使用」时按历史 ID 明确更新现选曲线，再走既有 slot 7 萃取确认。无记录与不可重用只可进入曲线库。保留五槽、运行中隐藏和无障碍。
  4. 在隔离模拟器验证有历史且现选不同、空态、紧凑 `720×360dp` 首屏五槽及点击；手动杯优先级由定向单测验证。只跑相称的定向单测和 Mock 构建。
- 推送与合并：本轮只提交本工作树任务文件，不推送、不合并。协调方独立复核后走普通分支推送、目标 `develop` PR、`branch-policy` 与 `verify` 必需检查。
- 验证证据：定向单测 `HomeLastCurveTest`、`CurveUsageLedgerTest`、`ShotRecipeSnapshotTest`、`ShotHistoryTest`、`HomePresetPreviewTest` 与 `assembleMock` 已通过。隔离模拟器从空数据启动，再以 `capture-2` 完成一次实际 Mock 萃取；选择改为 `capture-1` 后横条仍显示 `capture-2`，再次使用的确认与持久选择也回到 `capture-2`。`720×360dp` 下五槽均在首屏，槽 4 两行名称完整显示并点出正确确认。截图在 `build/home-last-curve/`；仅属模拟证据。
- 协议与设备风险：仅本地首页展示与既有曲线选择，不改编码/单位/队列/门禁/未知记录/重试/停止；`MobileService.selectedCurve` 和原确认仍是启动门禁。模拟器不能证明硬件效果。
- 发布：不发版；不改签名、版本或升级路径，不安装实体设备。
- 回退：通过 PR revert 首页行为时须保留支持账本四列指纹记录的读取代码，或先无损迁移；旧三列解析器无法读取新增的四列行。保留使用账本、杯记录与用户数据，不卸载或清记录。
- 未完成与阻塞：待协调方独立体验复核；无实体设备验收。

规则见 [Git 工作流](../git-workflow.md)。
