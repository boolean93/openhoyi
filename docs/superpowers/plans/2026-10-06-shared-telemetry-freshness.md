# 共享遥测新鲜度

目标：统一采样时间资格，拒绝负时间域与减法溢出，不改有效期、报文或失败优先级。

TelemetryFreshness.isFresh(at,now,maxAge=1500)要求at非空且>=0、now>=at、maxAge>=0，再安全相减比较。共享层PreheatGate/ExtractionStartGate、DeviceSession实际写前与出队检查、Service的待机资格使用它；已正确的Settings/Scale/机器与shot恢复年龄也复用，保留180000设置期限和1500采样期限。只处理采样年龄，不改变萃取持续时间、超时计时器和物理动作。

- [x] 先写边界与现有门禁回归，旧公式stub观察失败：负域、MIN→MAX溢出、future/missing、exact1500/1501、zero及MAX邻域。验证取消和萃取咖啡/秤各正确block，并保留优先级。
- [x] 补真实DeviceSession初始化后负采样拒绝enterSleep/取消预热及出队再检查，fake driver无新发送；有效新待机可写正例。
- [x] 实现共享新鲜度，接入原各年龄检查，完整本地构建/回归/Lint、复审。
- [ ] 推送、记录既有CI并读终态产物。无设备，不安装/操作；不将软件证明说成硬件完全无风险。

实际验证：TelemetryFreshnessTest4项旧公式3RED、DeviceSessionFreshnessTest2项原dispatch规则2RED；实现后6项全GREEN、所有device-session与219任务完整构建/回归/Lint通过，独立复审无阻塞。队列睡眠实际假driver验证无执行，队列取消预热复用同检查由静态接线确认，未独立运行；不声称覆盖所有时钟或真实硬件。ExtractionPolicy机器计时年龄、ExtractionController机器帧/持续时间等未在本轮迁移。

60f5d5a/Mock37378979088最终success完整产物读取：恢复12ACK/69入口、18/108/18及所有音频/八语言/两profile80页和三marker通过。活动源2ae9a37升级37379839761成功产物读取，其Mock37379839704尚运行中，新增availability/lazy读取18未验收。
