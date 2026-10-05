# 不完整机器恢复记录的保守加载

目标：操作类型缺失但地址仍存在的记录，不能等同完全空记录。原生写入及清除会同步保存两个字段；部分损坏或迁移的残留输入没有可靠完成证据。

方案：只在 MachineWriteRecoveryState 加载边界归一化。仅 null kind/null address 是空记录；null kind/非null地址变为 UNKNOWN，合法地址保留规范形式，非法/空地址不虚构身份。UNKNOWN 保持原有控制及恢复门禁，不发送或重放命令、不写回源记录。

不改 SharedPreferences 字段、协议、已知kind处理或人工确认流程。相比自动删除残留（丢保护）或启动时主动修复存储（无可靠原操作证据），选择内存保守识别。没有新增未知命令的解锁路径。

- [x] 先写合法/非法/空地址残留三负例和完全空记录正例；旧实现4项中3项断言失败，日志 `/private/tmp/hoyi-orphan-recovery-red.log`。
- [x] 最小加载修复，恢复三类输入 pending/UNKNOWN、拒绝 arm、拒绝全部五种回读确认；不修改存储。修复后4项通过，全部device-session测试通过。
- [x] 全模块测试及双变体构建/Lint、独立复审。完整构建219任务成功，日志 `/private/tmp/hoyi-orphan-recovery-full.log`；只读复审无阻塞。
- [ ] 提交推送，记录现有云端任务；不把本地构建或合成输入当硬件验收。
