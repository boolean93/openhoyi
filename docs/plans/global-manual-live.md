# 全页面手动萃取实时曲线

- 范围：机器手动出水时，最上层可交互页面自动显示只读实时曲线；已在实时萃取页不另外弹层。关闭只回到当前页面，保留编辑、对话框和结果链。
- 基线：`develop` / `1c3f31ab`；工作分支 `fix/global-manual-live`；PR 目标 `develop`。
- 实施：`ThemedActivity` 统一持有前台协调器，共用手动观测去重和原实时图；只读绑定已运行的服务，安全全屏层不触发宿主跳转。
- 验收：比较、曲线编辑、AI 草稿可被覆盖且可原样回来；已在实时页面不重复通知；同杯一次、下一杯可显示；切后台、断连、不活动的杯不能自动打开。
- 证据：本地 `./gradlew --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.ManualShotPresentationGateTest :mobile:compileMockAndroidTestKotlin` 通过（JVM 2 项，Mock 导航只编译）；云端 Mock 导航代表场景（`task-frame-ui.yml` 输入 `manual_live_only=true`，待执行）；真实机器/拨杆不操作、不作为已验收。
- 协议：不改 BLE、发令、设备状态判定、记录写入、重试或终止路径。只读窗口不提供机器控制。
- 合入：运行 `scripts/create-pr` 本地检查，不直推主分支；等待 `branch-policy` 和 `verify` 通过。
- 发布/回退：不发版、不打标签、不安装；包名、签名、版本和数据格式不变。按标准 revert PR 回退，不清历史或未知记录。

参见 [任务规划模板](task-template.md) 与 [Git 工作流](../git-workflow.md)。
