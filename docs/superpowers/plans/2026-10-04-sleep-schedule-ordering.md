# 每周睡眠计划的双路回读序号

范围：共享状态机的主机序号契约；不识别重新编号的旧BLE报文，不增加发送、重试或协议命令。当前MobileService两路计数递增，软件边界强化不等于发现真机乱序。

规则：每次begin/written建立两路基线，分别记录已接纳的最新序号与片段值。任一路倒退或同序号改变内容均拒绝整个快照。允许一段序号和值不变，另一段正常更新。timeout/disconnected基线不得低于已接纳序号，恢复仍要求两段都更新。UNKNOWN只变RECONCILED，不能声称写入成功或自动重发。

SleepPart没有值equals，tracker内比较day index、enabled bits、days及raw ByteFrame；不能使用对象身份。原测试同firstSerial=13从old值改为new值不符合契约，改成14，第二路12保持不变。

- [x] 原实现8项中5项实际RED：两路旧序号、同序号改值、timeout/disconnect降低基线；其余正常异步和新操作用例通过。/private/tmp/hoyi-schedule-order-red.log。
- [x] 初始候选8项GREEN及完整本地构建通过，但复审与新增测试发现引用比较问题；两方向同值新对象11项中2项实际RED。/private/tmp/hoyi-schedule-order-value-both-red.log。
- [x] 按片段值比较后11项GREEN（0失败/错误/跳过），原92会话检查、完整Alpha/Mock/test APK、蓝牙/双应用单测及双Lint通过；180 tasks，/private/tmp/hoyi-schedule-order-final.log。
- [x] 独立复审收敛，无剩余阻塞；未代替运行测试。
- [x] 源码提交7fa8482推送。
- [ ] 对应native37185839289最后确认in_progress，读取最终日志后补证据。

不能用上述测试证明真实睡眠执行、周计划写入/恢复安全；这些仍待采样及硬件验收。
