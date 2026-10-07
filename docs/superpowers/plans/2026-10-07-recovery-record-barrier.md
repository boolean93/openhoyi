# 萃取与机器写入恢复记录的失败提交屏障

**Goal:** clear commit失败已改缓存后，重建Service仍保留原待处理操作、设备地址与类型。

**Architecture:** 通用RecoveryPersistenceBarrier<Record>将完整pending记录同步写入独立文件再提交偏好，清除只有偏好提交与文件删除都成功。clear旧偏好记录时先补完整marker，已有marker不覆盖。读取优先marker，损坏/读取异常由现有State恢复UNKNOWN。生产Service替换两个内联Storage；旧键/schema和操作owner/gate不变。所有脱离Service Android fixture需独立noBackup目录，不允许修改产品marker。

**Tech Stack:** Kotlin泛型RecordStorage/Marker，Android私有JSON marker与fd.sync，SharedPreferences.commit。

- [x] 参数测试对SHOT及五种机器操作覆盖失败缓存/抛异常、文件写/删失败、旧偏好补marker、成功清除和marker损坏；stub确认RED。
- [x] 实现共享屏障与Android两种适配器，替换MobileService Storage；不修改状态清除资格或协议。
- [x] 隔离所有Service fixture文件目录。新增实际Service重建场景，真实偏好已改false/null而报告失败，断言完整记录/各写入入口仍阻止；成功显式清除后偏好/marker均清除。升级种子与验证新增两个marker。
- [ ] 完整构建/测试/Lint与独立复审；提交推送后实际云端读取，不能以编译代替运行。

只对进程恢复负责，不声明存储介质损坏、断电或未知硬件状态保证。旧marker不存在不是旧包操作已完成的证据。仍需真实设备验收。

标记损坏读取由现有State变UNKNOWN；已通过既有资格且当前State仍有可靠记录的clear只检查marker存在后提交/删除，不必解析坏marker。未新增机器UNKNOWN解锁。新fixture实际调用六入口72次并比较精确恢复warning；清除后无Hub返回service_unavailable。其范围是入口门禁与存储重建，不是实际设备出队。
