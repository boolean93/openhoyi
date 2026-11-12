# 上一杯显式一键萃取

- 需求与范围：上一杯「使用」复用五槽位的一次性直启流程；关闭设置仍只导航。保留历史、草稿指纹、温度及服务门禁，不增加预热或机器操作。
- 来源分支/基线提交：origin/develop df108928。
- 工作分支：fix/last-shot-direct-start；PR目标 develop。
- 改动文件与步骤：提取共享入口与源有效性检查；捕获上一杯历史身份；补单测及 Mock 实窗用例。
- 推送与合并：本轮不推送、不创建PR；后续按 docs/git-workflow.md，通过 scripts/create-pr 和必需检查。
- 验证证据：针对性 JVM 单测、Mock/Debug 编译；实窗仅编译，不安装、不运行设备。
- 协议与设备风险：不改协议及启动流水线；历史/页面/设置变化撤销许可，不队列、不重放。机器拨杆入口不变。
- 发布：不发版、不改版本签名、不安装、不操作机器。
- 回退：代码 revert，保留历史、曲线与设置，不清数据。
- 未完成与阻塞：独立 Astra 复核及已授权后的实窗执行由协调者安排。

## 本地验证（2026-11-12）

- `python3 scripts/git_policy.py branch`、`git diff --check`：通过；现有 `core.hooksPath=.githooks` 未修改。
- JDK 17 / `ANDROID_HOME=/Users/boolean93/Library/Android/sdk` 下执行 `./gradlew :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMockAndroidTest --console=plain`：最终串行运行通过，115 tasks（15 executed）。Mock JVM 共 118 suites / 631 tests，0 failures / errors。
- 新增历史身份变化/历史不可读的许可撤销单测；扩展已有 Mock 实窗检查：上一杯关闭仅导航、开启冷机仅导航不预热、不接受旧历史身份。实窗代码编译通过，未执行。
- 首轮因实现者重叠启动同工作树构建出现 Bluetooth 类型 classpath 错误；后续构建及最终无并发串行运行均通过。后续同工作树必须串行构建，不能把中途错误当源码缺陷修改无关文件。
- 未安装、未操作设备、未 push/PR、未发版。待 Astra 独立复核，设备体验验证另行授权。
