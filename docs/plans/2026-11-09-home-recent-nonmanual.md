# 首页最近曲线排除手动萃取

- 需求与范围：用户已确认：首页只显示最近一次非手动萃取曲线；后续手动萃取不覆盖；仅手动历史时显示空态并可选择曲线。手动历史照常保留。曲线已删除、草稿已改或记录不可确认仍沿用原保护，不跳回更老曲线。
- 来源分支/基线提交：`origin/develop` / `a5290b09`。
- 工作分支：`fix/home-recent-nonmanual`；工作树 `/Users/boolean93/workspace/hoyi_app/openhoyi-home-nonmanual`。
- PR目标：`develop`。
- 改动文件与步骤：
  1. 更新 `HomeLastCurveTest` 的手动覆盖预期，运行定向单测确认旧实现失败。
  2. `HomeLastCurve.kt` 删除手动覆盖状态和时间参数；`HomeFragment.kt` 去掉手动日志依赖，只从非手动使用账本和已观察历史解析。曲线使用账本已拒绝 manual，不改存储。
  3. 运行 `HomeLastCurveTest`、`CurveUsageLedgerTest`、`ShotHistoryTest`，编译 Debug；检查 diff，只提交本任务文件。
- 推送与合并：本轮本地提交；后续普通推送工作分支，以 PR 合入 develop，遵守 branch-policy、verify 必需检查，不直接推送主干。
- 验证证据：旧实现定向测试 3 项中 2 项按预期失败（手动覆盖、数据不可确认被覆盖）。修改后 `./gradlew :mobile:testDebugUnitTest --tests io.openhoyi.mobile.HomeLastCurveTest --tests io.openhoyi.mobile.CurveUsageLedgerTest --tests io.openhoyi.mobile.ShotHistoryTest --console=plain` 成功，包含 Debug 主代码与测试编译。流程测试覆盖仅手动时空态、曲线后连续手动保留曲线、重启保留曲线及手动历史不丢失、后续曲线使用更新时间。构建有既有弃用 API 等警告。`git diff --check` 和 `python3 scripts/git_policy.py branch` 通过；无 UI / 实机验收。
- 协议与设备风险：仅首页展示数据筛选，不改设备协议、开始确认、队列、未知记录及记录写入。
- 发布：不发版、不改签名或版本、不安装、不操作机器。
- 回退：revert 本任务提交即可，无存储格式变更，不清数据。
- 未完成与阻塞：未实机验收；本轮按小改动由当前 Sol 实现和自查，无独立 Astra 复核。

规则见 [Git 工作流](../git-workflow.md)。
