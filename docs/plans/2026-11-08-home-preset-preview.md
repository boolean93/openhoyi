# 首页快捷配方缩略图

- 需求与范围：首页保留五个快捷槽位。在槽位编号与配方名之间加入曲线库列表同款真实曲线缩略图；空位或不可绘制曲线不显示图。保留整按钮点击、确认、可用性和无障碍；矮屏可滚动使用。不改其他页面。
- 来源分支/基线提交：`origin/develop`，`1d604fad3d502858412a3a3827af6c3312d9b49c`，已执行 `git fetch origin`。
- 工作分支：`feature/home-preset-preview`，独立工作树 `/Users/boolean93/workspace/hoyi_app/openhoyi-home-preset-preview`。
- PR目标：`develop`。
- 改动文件与步骤：
  1. 在 `mobile/src/test/kotlin/io/openhoyi/mobile/` 写预览模型测试，先验证缺失与不可绘制曲线无图，以及使用曲线库生成规则与共享刻度。
  2. 在 `mobile/src/main/kotlin/io/openhoyi/mobile/` 实现小型预览模型；首页按钮加入编号、`CurveLibrarySparklineView`、名称的纵向内容。现有按钮保留 tag、点击、启用状态与内容描述。
  3. 绑定时按曲线库 `CurveStageParameters.preview` 与 `SharedScale.from(library.items)` 更新缩略图；空位隐藏图。矮屏保持可滚动和至少 48dp 触控目标。
- 推送与合并：本轮只在独立工作树实现、验证、提交；协调方复核后决定普通推送工作分支与 PR。禁止直推 `main`/`develop`、强推或自行合并。
- 验证证据：`./gradlew :mobile:assembleMock :mobile:testMockUnitTest --tests io.openhoyi.mobile.HomePresetPreviewTest --tests io.openhoyi.mobile.PresetSlotsTest --offline --no-daemon --console=plain` 通过。隔离 Android 34 Mock 模拟器上，`build/home-preset-preview/home-tablet-scrolled.png` 显示五槽位真实曲线；`slot1-after.png` 证明点曲线位置进入原确认。最终矮屏截图 `home-720x360-final.png` 显示五槽位、曲线和完整两行名称均在首屏；`slot4-720-final-tap.png` 证明点击名称位置仍进入原确认。`uiautomator dump` 读到五个按钮完整的 `content-desc`。空位与不可绘制曲线由 `HomePresetPreviewTest` 覆盖。
- 协议与设备风险：只改首页展示，不改编码、单位、队列、门禁、未知记录、重试或停止逻辑；现有点击仍进入原萃取确认流程。模拟器只能验证界面，不能证明硬件执行。
- 发布：不发版；不改签名、版本或升级路径，不安装实体设备。
- 回退：通过 PR revert 本功能提交；保留用户配方与设备记录，不卸载、不清数据。
- 未完成与阻塞：尚待协调方独立复核；未做实体设备验收。

规则见 [Git 工作流](../git-workflow.md)。
