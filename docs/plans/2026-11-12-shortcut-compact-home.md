# 一键萃取：紧凑横屏快捷卡裁切补修

- 需求与范围：修复 #66 在 720×360 紧凑横屏的首页快捷卡底部裁切。保留标题旁开关 48dp 点击区域、字号、换行、所有萃取安全逻辑及原几何断言。
- 来源分支/基线提交：develop / `5ac1a61166a6d0d9538d697f6a7c8fef8e714783`。
- 工作分支：`fix/shortcut-compact-home`，独立工作树，不改已有功能工作树。
- PR 目标：develop。用户既有一键萃取 PR/合入授权覆盖回归补修；创建补修 PR 后先交协调任务/Astra 独立复核，不自行合入。
- 改动文件与步骤：仅将 shortHome 快捷 tile 最小高度从 96dp 调到 80dp，保留 wrap_content/无限行，不设固定高度；在原 compact 几何检查加入开关可见/48dp 下限，复用现有 Mock 实窗与 lifecycle 手动 CI 补横竖/浅暗截图。
- 尺寸依据：失败 run `43196324219` 中内容 590px、viewport 568px（density=2），卡片 192px 仅 172px 可见；新开关头行保留 48dp，卡最小高度回收 16dp=32px 后预计内容 558px，留 10px 余量。实际名称/预览内容仍可按需撑高，长字不强裁。
- 推送与合并：本地相称验证 → 普通推送短分支 → `scripts/create-pr` 完整门禁 → 必需 branch-policy/verify → 单次必要现有手动 lifecycle/截图 CI → 协调任务独立复核后决定合入。无强推、无绕门禁。
- 验证证据：复用已有失败 artifact 定位；运行相关 JVM、Mock/AndroidTest 编译及 PR 完整本地门禁。只用现有云 CI 的一次性 Mock AVD，不新建/启动本地 AVD，不连接或操作真实设备。
- 协议与设备风险：纯高度预算与测试证据，不改报文、门禁、去皮、重试、停止或未知记录；模拟验证不等于物理出水/停水证明。
- 发布：不手工发版、不更改默认版本/签名、不安装设备。合并后自动 Develop 通道按既有四项同 SHA 门禁运行。
- 回退：使用后续修复或 revert PR；保留用户数据与未知操作记录，不卸载清数据。
- 未完成与阻塞：待实现、验证和独立复核；#66 的自动预发布仍因原 lifecycle 失败被挡住，不手动放行。

规则依据：[Git 工作流](../git-workflow.md)。

## 本地针对验证

- `git diff --check`、`bash -n scripts/check_mock_lifecycle.sh`：通过。
- JDK17 / SDK35，临时 Gradle JVM `-XX:-UseOnStackReplacement`、单 worker、不改项目配置：
  `:mobile:testMockUnitTest --tests '*TaskLayoutPolicy*' --tests '*ShortcutStart*' :mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，27 项 JVM 测试通过，Mock 与 AndroidTest 编译打包通过（2m04s）。
- 实际窗口几何及新截图仍待此分支的一次现有手动 lifecycle CI；编译不替代真实窗口断言。
