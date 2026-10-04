# 断线队列结果完整性

目标：GATT断线关闭一次传输后，正在执行的命令仍为Unknown，未发送命令仍为Cancelled。命令回调与invalidated通知同时抛Exception时，也必须按原顺序把已经取出的所有命令结算一次，不重发或重试。

根因：deliver捕获命令回调异常后调用invalidated；后者异常逃逸，disconnect的running或waiting遍历立即中断。假驱动测试普通断线通过，其余三项RED，实际遗漏后续结果。

设计：仅disconnect内对每次deliver的Exception收集，所有结果送达后重新抛第一个；后续不同异常作为suppressed保留，同一对象不自我抑制。保持queue关闭时点、结果类型、reason及invalidated调用次数。VM Error、回调同步重建连接和全Hub清理不在本项保证范围。

- [x] 四项假驱动测试实际RED（3失败/1通过，/private/tmp/hoyi-queue-cleanup-red.log）。
- [x] 扩展5项实际RED（4失败/1通过），最小修复后5项GREEN，含不同异常suppressed与同对象防自抑制；原92会话场景通过。
- [x] 双APK/双单测/双Lint/testAPK及协议/会话/传输180任务本地成功，/private/tmp/hoyi-queue-cleanup-green-build.log。
- [x] 独立静态复审无阻塞；未修改协议报文、重试或控制门禁。
- [x] 4c42c26云端native37182337453 success，读取39823协议/32856回放/92会话、完整构建/资源及发布检查标记。
