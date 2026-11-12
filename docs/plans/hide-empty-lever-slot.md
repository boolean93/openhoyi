# 首页隐藏空拨杆卡

- 需求与范围：同设备存在历史 SENT 才显示拨杆「最近发送」卡；首次 pending/failed/unknown、无地址及切换到无记录设备时隐藏。五个本地快捷槽与曲线详情拨杆/槽位入口不变。SENT 仅为 GATT 写完成，非机器当前配方读回。
- 来源分支/基线提交：develop / df108928。
- 工作分支：fix/hide-empty-lever-slot；后续 PR 目标 develop。
- 改动：HomeFragment 保留稳定槽索引，仅可见性变化时重排横排或手机双列；移除空卡及其间距，不按遥测轮询重建。补充精简单测及现有 Mock 窗口检查。
- 推送与合并：本轮仅本地实现，不 push、不创建 PR；后续遵守 [Git工作流](../git-workflow.md) 与 create-pr 入口。
- 验证：相关 JVM 测试、Mock/Debug 编译及 androidTest 编译；设备 Mock 检查待协调者授权执行，不操作平板。
- 协议与设备风险：不改 BLE、发送门禁、UNKNOWN 保护及 journal；不发送真实机器指令、不自动重发、不清数据。已有 SENT 后的 UNKNOWN 仍显示未知并清预览。
- 发布：不发版、不改版本/签名、不安装；无存储格式变化，无需迁移。
- 回退：回退本任务代码即可；旧 SENT/UNKNOWN 记录原样保留，不卸载或清记录。
- 未完成：横竖屏与运行期动态切换的实际 Mock UI 验收待执行，编译不等同设备验收。

## 本地记录

- `git diff --check`、`python3 scripts/git_policy.py branch` 通过。
- 本仓库 Mock 是 buildType `mock`，不是 flavor `mockDebug`；首次任务名探测失败后改用实际任务，无源码影响。
- `:mobile:testMockUnitTest --tests '*HomeDirectPresetSlotsTest' --tests '*MachineLeverRecipeStoreTest' --tests '*MachineLeverRecipePreviewTest'`：10 项通过，0 失败。
- `:mobile:assembleMock :mobile:assembleDebug`：通过。Mock 窗口检查仅增加并编译，不宣称已运行；设备验收由协调者安排。
- `:mobile:compileMockAndroidTestKotlin`：通过；同次 Gradle `BUILD SUCCESSFUL`（112 tasks）。未运行设备测试。
