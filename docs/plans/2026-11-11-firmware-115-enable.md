# 显式启用固件1.1.5

- 需求：按用户授权将1.1.5纳入与1.1.4相同范围；C6、新鲜度、身份、停止路径和禁止命令组保持。
- 分支/基线：现有 `fix/home-switch-readback`，`87bf960dc12aa6b8a29bbbf76e622b05bd84cecb`；沿用主任务 develop 来源，目标PR develop。本子任务不建工作树、不commit/push/PR。
- 步骤：精确profile和完整flow白名单；flow提示及固件文档；启动context绑定固件；补充schema3 Ls/首段flow从library能力、capture至真实队列派发和版本撤销回归。
- 验证：协调Gradle后执行protocol-core/device-session完整test及相关mobile单测；结果记录于[证据](../evidence/2026-11-11-firmware-115-parity.md)。
- 协议/设备风险：静态证据不等同实机；不扩大数值范围、不接受所有未来版本、不新增命令、不清记录、不操作设备。
- 发布/签名/升级：不发版、不安装、不刷机；无包名/版本/签名/持久化格式变化。
- 回退：回退本次许可和对应测试/文字；用户数据和未知结果不清除。
