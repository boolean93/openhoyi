# 首页夜间配色修复

- 需求与范围：首页背景、锅炉仪表盘和快捷槽位遵循应用暗色设置，保留既有浅色外观；不改布局、导航、设备逻辑和曲线含义。
- 来源：develop `7fbfcbea69da126b2361c48fcf88e1d6acf80b07`。
- 工作分支：`fix/home-dark-theme`；PR 目标 develop；本轮不创建 PR、不发布或安装。
- 实现：首页专用颜色纳入已有 TaskProductVisualStyle 的明暗选择；替换 HomeFragment 与 BoilerTemperatureDial 的硬编码颜色。
- 验证：编译、相关单测；在既有 HomeWorkbenchChecks 中检查实际首页背景、圆盘和文字、槽位颜色。设备测试未执行时明确标注，不以编译充当真机验收。
- 风险与回退：仅显示颜色，无协议写入和持久数据变更；可回退代码，不卸载或清数据。
- 流程：遵循 [Git 工作流](../git-workflow.md)，如后续授权 PR，先完成相关本地检查及必需 CI。

## 本地验证记录

- 最终代码：`./gradlew :mobile:testMockUnitTest :mobile:compileMockAndroidTestKotlin :mobile:assembleMock` 成功（2026-11-12）。Mock 单测 556 项，555 通过、1 项既有 LegacyCurveWireAuditTest 跳过，无失败。
- Astra 独立静态复核已完成；修正夜间主按钮填充与文字对比，以及紧凑首页没有圆盘时的测试条件。
- `git diff --check` 通过。
- 新增首页实际视图颜色断言只编译，尚未在设备执行；本轮未安装 APK、未运行模拟器、未触发机器指令。需要后续授权后检查真实浅/暗切换和截图。

## 真机 Mock 验收（后续授权）

- 平板 M2105K81AC，安装前真实曲线页显示「已结束」、机器待机；未触发真实设备命令。
- 按项目规则同源码 Debug/Mock 双包均以原签名、原 versionCode 80 覆盖；两次 install -r 与测试 APK 安装均成功，首次安装时间不变。Debug 未启动。
- 当前旧 Debug APK 与已留存安装记录的 7132ff69 精确匹配；该提交与本次 develop 基线全部 src/main/debug/mock 一致，不覆盖丢失既有功能。
- 安装前后 Mock 全部 69 个数据文件相同；真实包 71 个稳定业务文件相同，只有运行中 trace 和 devices.xml 活动状态改变。
- 在设置页真实切换深色；浅色和深色各跑一次 HomeWorkbenchChecks，均通过新增首页实际背景、仪表盘与文字、快捷槽位颜色断言及原数据不变检查。MIUI 后台启动受限时由 adb 显式启动同一 Mock 首页辅助测试启动，不涉及真实包。
- 两张真实截图已查看，浅色原样，暗色页面/圆盘/槽位全部深色、文字清晰。证据位于本机 `/tmp/openhoyi-home-dark-evidence/` 的 light-home.png、dark-home.png、light-check.txt、dark-check.txt。
- 恢复 Mock 浅色时遇到页面意外切到豆子，疑似并行操作者，已停止触控交接；Mock 暂留深色。本轮不宣称覆盖其它页面/竖屏/弹窗。
