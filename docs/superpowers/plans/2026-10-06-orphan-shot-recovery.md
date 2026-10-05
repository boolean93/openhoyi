# 萃取恢复残留地址的加载保护

目标：与机器写入恢复一致，未确认标记为false但地址残留的萃取记录，不能被当成完全空记录。MobileService读取不存在的unresolved_shot默认false，clear则同步写false与null地址；部分损坏/迁移没有可靠完成证据。

方案：仅在ShotRecoveryState加载时，把false/非null地址保守加载为pending=true。合法地址保留规范形式；非法或空地址保留pending、不虚构身份。完全false/null和原true记录语义不变。加载不写回、不发命令、不新增重试。人工清除资格仍经过原设备/待机/时效/未决萃取门禁。

保留arm同地址pending的既有幂等语义，不误把“没有新增写入”描述成拒绝所有arm。未知身份仍只能只读连接，原有preExisting被动萃取自动清除保护保持。类内显式clear失败保持，成功后持久化false/null并允许重载为空。

- [x] 四参数组先测试完全空、合法残留、非法残留、空串残留，旧实现实际3项断言失败，日志 `/private/tmp/hoyi-orphan-shot-red.log`。
- [x] 最小加载修复，四组通过，0失败/错误/跳过；覆盖加载零写入、错误设备/待机门禁、失败清除和成功重载。全部device-session测试通过。
- [x] 完整双变体构建/Lint与独立复审。219任务成功，日志 `/private/tmp/hoyi-orphan-shot-full.log`；只读复审无阻塞。
- [ ] 提交推送及云端任务记录；运行未完成不计通过，实机仍待用户提供。
