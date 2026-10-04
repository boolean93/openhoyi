# 独立去皮回读序号

目标：WAITING_ZERO收到较新非零回读后，旧/同主机序号的零值不能确认去皮并解除unresolved。更高序号零值正常确认，新操作written独立基线不受上次影响。

设计：sample仍先tick；守WAITING_ZERO与新serial后推进writtenAfterSample，再按原abs(Long)<=50判定。保留5秒边界到UNKNOWN、显式重试、priorUnknown与token规则。仅hostserial，当前产品计数递增，不宣称真实BLE重传识别；不能把写成功当秤已归零。

- [x] 原4项实际2RED（oldzero/repeatedzero），新操作低基线及截止UNKNOWN原实现通过；/private/tmp/hoyi-tare-order-red.log。
- [x] 4GREEN（0失败/错误/跳过）、原92会话/前置萃取检查及180任务完整本地构建通过；/private/tmp/hoyi-tare-order-green.log。
- [x] 独立复审无阻塞，未代替运行测试。
- [ ] 提交与对应CI最终日志。

BOOKOO真实去皮及断链全流程未全面验收；无真机操作。
