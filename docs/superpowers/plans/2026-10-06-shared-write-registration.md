# 共享机器写入登记

目标：把五类写入的tracker begin→同步安全记录arm→失败收尾收拢到无Android的MachineWriteRegistration；实际发送只能在Registered token后继续。

sealed Request包含五typed tracker和原begin/失败written参数，结果Registered(token)/Busy/RecordFailed。保留原顺序和策略：begin busy不写存储，arm失败Setting/Cup/Schedule/Sleep按原Failed("safety record unavailable") written；BrewWait按原consumed回IDLE。不传驱动/发送回调，不自动重试、不清除已有记录。Service保留所有前置门禁/身份/返回resource和登记成功后序号基线、事件与发送。

- [x] 参数化五kind测试：成功登记kind/身份/WRITING且后续Busy无写；保存失败/异常不返回token，显式重试成功；已有kind/UNKNOWN与非法身份不覆盖，原失败状态保持。先默认拒绝stub验证失败基线。
- [x] 实现协调器并运行device-session全回归。
- [x] MobileService五begin/arm失败分支接入，原提示/serial/event/send顺序不变，brew失败必须consumed。
- [x] 完整构建/单测/Lint、只读复审。
- [ ] 提交推送并记录/读取当前源码CI。登记成功不等于机器已执行；后续真实回读/不确定结果策略保持。无设备，不安装或控制。

本地：20项参数化测试默认拒绝stub18RED→实现20GREEN；最终219任务完整构建/回归/Lint与复审/diff通过。首次Service批量替换误命中Mock预热分支、删真实preconditions并导致编译失败；未提交，恢复HEAD整个prepareBrew后只锚定真实coffeeAddress后的begin/arm，再复审/完整构建成功。五函数登记前prefix与原基线逐段比较保持；Mock整个流程原样保留。实际Service持久化失败入口未新增Android fixture，本轮共享协调器测试及接线复审不代替该运行证据。

283807a/native37381614423实际完整日志成功读取，85组合JVM矩阵已在精确源回归通过。52c95d4/Mock37380567825完整产物读success，18/108/18+availability18/lazyShotRead、12/69与清理/通知/八语言/五音频、两profile80页和三marker通过，不代替本轮登记接线的产品运行。
