# 共享恢复活动状态 Implementation Plan

**Goal:** 将恢复按钮与实际清除证据的事务活动规则统一至device-session，保持现有控制策略。

**Architecture:** MachineRecoveryActivity纯Kotlin object提供五typed tracker State的isBusy重载，以及available(kind,五state,shotPending,shotActive)。Cup/Setting/Schedule/Sleep各WRITING与其WAITING阶段busy；Brew仅IDLE/FAILED/UNKNOWN/CANCEL_WRITTEN非busy，READY/CANCELLING保持busy。available未知/空kind false；仅Brew额外阻止pending或active shot。Service保持原快照和优先级，不改BLE报文、存储、超时、实际出队检查。

**Tech Stack:** Kotlin/JUnit4、现有Android Service。

- [x] 创建 `device-session/src/test/kotlin/io/openhoyi/session/MachineRecoveryActivityTest.kt`：遍历所有5typed state逐一检验busy；每kind四种shot flags检查available，其它事务设置busy以防意外扩大原规则；UNKNOWN/null全拒绝。创建默认拒绝stub并执行测试观察预期失败。
- [x] 实现 `device-session/src/main/kotlin/io/openhoyi/session/MachineRecoveryActivity.kt`，复用isBusy重载，运行全部device-session测试。
- [x] 接入 `mobile/src/main/kotlin/io/openhoyi/mobile/MobileService.kt`：恢复按钮availability、cup/schedule busy、ACK证据的setting/sleep/brew active统一调用；五ACK证据序号/clock/address、其它控制门禁与shot/manual先后不变。
- [x] 完整构建/回归/Lint、独立只读复审、git diff与shell检查。
- [ ] 提交推送后记录CI，读取当前活动接线及已有60f5d5a回归终态产物。

此处不扩大按钮可点为可清除：身份、READY、回读序号和时间仍由MachineWriteAcknowledgement与原canClear处理。保持非Brew ACK按钮的旧shot政策，不宣称整个Service协调已迁移或硬件已验收。

验证命令：`./gradlew :device-session:test --tests io.openhoyi.session.MachineRecoveryActivityTest --offline --console=plain -PgoogleMirror=aliyun`；完整执行现有protocol/trace/transport/app/mobile checks、双变体assemble、testAPK及Lint。用户无设备，不安装或操作。

实际本地记录：3命名测试的默认busy/拒绝stub产生2预期失败，实现后3项全通过（枚举36state、144 own-kind/shot flags及8 empty/UNKNOWN资格组合）。初次219任务完整构建/回归/Lint成功。最终getter仅BREW读取shotRecovery，pending=true时不调用ShotGate.active，保持原短路。产品18夹具新增availability旧策略及lazy读取0/1次对照，原18/108/18保留，marker runner/script同步增加availabilityChecks=18 lazyShotRead=true。最终Android接线170任务构建/单测/testAPK/Lint成功，独立复审无阻塞。

上一源60f5d5a的升级37378979031成功且seed/verify/result实际读取，仅同源码/schema Mock1→2；其native37378979071、Mock37378979088及自动UI37378979058运行中，不代替本轮活动接线验收。
