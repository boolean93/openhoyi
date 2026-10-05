# 机器写入恢复确认的共享协调

目标：将五类恢复确认中的READY资格、既有回读门禁与同步持久化清除结果收拢到device-session，MobileService保留实际快照/序号/事务状态适配与UI事件。

MachineWriteAcknowledgement为无Android依赖的协调对象。sealed Request包装既有五种Evidence及恢复序号基线；acknowledge(recovery, ready, request)先检查ready并调用原canClear，资格不满足返回WAITING，不写存储；清除失败返回CLEAR_FAILED，成功返回ACKNOWLEDGED。UNKNOWN/无记录/类型不符不会清除。不增加自动重试、发送命令或修改参数/时效策略。

Service沿用各分支构建证据和shot/manual优先级；共用显示helper只映射原等待/失败/成功resource与eventPrefix。BREW_WAIT成功时仍先consumed，再event/通知刷新；其它失败不刷新。Service、存储和协议边界不搬进共享层，不声称运行时其它请求协调已统一。

- [x] 五类型参数化测试：非READY/错记录种类/错身份/活动事务/未更新序号拒绝且不写；有效资格的Storage失败保持、显式重试成功、重复请求不再写。先验证拒绝默认的未完成协调器无法通过成功/失败结果测试。
- [x] 实现协调器并跑全部device-session测试。
- [x] 五个Service分支接入共享协调与显示helper，保持原resource/tag/顺序与所有证据字段。
- [x] 完整构建、单测、Lint、独立复审。
- [ ] 推送后读当前源码产品18/108与preheat12/69等实际矩阵证据。

用户暂不提供设备，不安装/操作真机。此项是软件协调迁移，不能证明真实命令生效或全部功能对标完成。

本地证据：failclosed stub 的15项新测试中5项预期失败；实现后全部device-session与219任务完整构建/单测/Lint通过。上一版真实Mock run37377688521在CANCELLING测试的startShot读取curves偏好时抛错；这属于夹具遗漏，未算通过。本轮将curves纳入UUID隔离，种入已验证capture-1，检查resolve/validated及busy后偏好不变，原精确拦截断言和12/69计数不变。修正后testAPK/Lint125任务通过。原始偏好对照与finally删除隔离文件同步覆盖curves。

旧源码e3aa581 run37376998752实际产物已读，18/108/18入口矩阵通过，busy仍10/58；不能替代本轮共享接线和12/69验收。
