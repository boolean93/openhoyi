# 修复首页模式控件的紧凑屏验收类型

- 需求与范围：修复 720×360 首页 Mock 几何验收仍将模式控件当作 HoyiSwitch 的过期类型转换。控件已替换成 CurveEditorSegments；原可见区域、48dp、标签、萃取中禁用、不重叠和状态稳定性检查保持不变。
- 来源分支/基线提交：develop / e52812a9080bc4a11ef0e394a45d5f75ee8e2ba1。
- 工作分支：fix/lifecycle-segment-check；PR 目标 develop。
- 改动文件与步骤：仅改 MainNavigationChecks 的模式控件类型转换；先编译 androidTest，再执行 scripts/create-pr 的现有检查，服务端保护通过后合入。
- 推送与合并：普通推送独立分支，通过 scripts/create-pr 创建 PR，不绕过保护。
- 验证证据：失败工作流 43589460554 的 main-navigation.txt 为 CurveEditorSegments cannot be cast to HoyiSwitch，MainNavigationChecks.kt:92。真实设备断言由 develop push 的既有 GitHub 模拟器生命周期工作流验证，不在用户电脑启动模拟器，不操作用户平板。
- 协议与设备风险：无生产代码、协议、机器控制、数据或签名修改；保留所有原验收断言。
- 发布：不手工发版或移动标签；合入后的既有发布控制器须等待四项 push 检查，通过后补发未发布版本。
- 回退：仅回退测试修复，不影响用户数据。
- 未完成与阻塞：本地编译不等同设备运行；等待 GitHub Mock lifecycle 真实运行证据。
