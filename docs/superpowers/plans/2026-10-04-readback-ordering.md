# 设置与立即睡眠回读序号

目标：同一次操作中，较新不匹配回读不能被较旧/相同主机序号的匹配回读覆盖；timeout/disconnected给出的较小序号不能降低水位。真正更新且匹配的回读仍按原规则确认，未知结果不重发。

证据：ReadbackOrderingTest覆盖SETTING/SLEEP_NOW×普通/timeout/disconnect，先baseline10、较新不匹配12，再旧匹配11、重复12、正确新13；原实现6项实际RED。当前产品递增主机计数，这项保护针对共享API排序契约，不声称发现真实BLE乱序或能识别重新编号的旧设备报文。

设计：observe接受新序号后先推进afterSample再比较值；timeout/disconnected用maxOf保留当前水位。written仍建立本次操作基线；被接受的begin清零上一轮水位，被拒绝的begin不改变当前水位。token/结果类型/参数和回读匹配条件不改。此项只处理单路Setting/SleepNow，SleepSchedule双片段独立水位另行分析。

- [x] 原实现6项实际RED，/private/tmp/hoyi-readback-order-red.log。
- [x] 初始修复发现跨操作残留水位，新增2项实际RED，/private/tmp/hoyi-readback-order-reuse-red.log；begin按操作重置后8项GREEN。
- [x] 完整本地协议/会话/蓝牙单测、Alpha/Mock APK及Mock测试APK、双变体单测和Lint通过，180 tasks；/private/tmp/hoyi-readback-order-final-build.log。
- [x] 独立复审无阻塞，未代替执行测试。
- [ ] 新源码fd6947f的native37185499803最后查询in_progress；读取完成日志后补证据。
