# 杯数重置的边界序号

目标：Settings/Idle两路已接纳序号在timeout/disconnected后不能降低；边界前部分证据必须丢弃，未知结果需重新获得两路回读。新操作不继承上一操作水位或计数。范围主机序号共享API，当前产品计数递增，不识别重新编号的旧BLE回读。

设计：timeout/disconnected以maxOf保持两路水位再markAfter，保留原清空部分回读逻辑。accepted begin初始化本操作水位/count；active/UNKNOWN拒绝begin不重置。written、token、resolve匹配与未知结果不重发规则不改，不新增报文。

- [x] 原6项4实际RED：两channel×timeout/disconnect旧zero误确认；partial丢弃和新操作fixture原实现通过。/private/tmp/hoyi-cup-boundary-red.log。
- [x] 6项GREEN（0失败/错误/跳过），完整180任务本地构建通过；原会话检查/双APK/test APK/单测/双Lint，/private/tmp/hoyi-cup-boundary-green.log。
- [x] 独立复审无阻塞，未代替实际测试。
- [ ] 源码提交与对应CI证据。

真实杯数重置执行和恢复仍未硬件验收，软件边界通过不等于证明设备安全。
