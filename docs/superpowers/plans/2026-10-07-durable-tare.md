# 去皮未确认状态持久化

**Goal:** 三个产品去皮路径共享同步持久化未确认状态，进程重启不能放行下一杯。

**Architecture:** `StandaloneTare.Storage` 只保存 pending 布尔值，默认内存实现兼容纯回放。生产 Alpha/Lab Hub 显式注入 Android SharedPreferences 与独立持久标记适配器；StandaloneTare 开始前先保存 true，确认新零重量或首次已知未发送失败才尝试清除，读/写异常或清除失败保持 UNKNOWN。原 UNKNOWN 的重试失败不清除旧记录。Tracker 序号、通知代际和写后采样序号继续约束旧回调；不增加自动重发。恢复 UNKNOWN 不阻止紧急停止/预热取消，只阻止新杯并允许显式去皮重试。

**Tech Stack:** Kotlin shared state、Android SharedPreferences.commit、真实 Session 队列 + fake GATT。

- [x] 纯状态 RED：初始pending/读取异常、发送前保存失败、WRITING/WAITING_ZERO重建、零回报清除失败、正常确认、首次已知未发送、未知重试失败、晚回调/迟到零，证明旧实现缺失持久化。
- [x] 实现共享可选Storage；NativeDeviceHub增加可选tareStorage，生产MobileService与LabService传入Android适配器。兼容未注入的离线回放，不把它算作产品持久化证明。
- [x] 实际Service独立去皮10场景注入隔离Android适配器，检查发送前durable true、拒绝清除和归零清除、新tracker读取结果。新增持久化Android checks直接使用真实适配器验证重建UNKNOWN和显式retry。
- [ ] 完整回归/构建/Lint、独立审查、提交推送；云端实际marker/result读取后才认定Android运行通过。

记录键新增 `scale_tare_safety/unresolved_tare`。缺键表示当前版本未记录请求；不能据此证明升级前老包最后一次去皮已完成。没有绑定/修改机器配置或新的协议帧。真实归零/断链时序与升级前未知去皮需人工验收，持久化不证明设备事务完成。

审查修正：SharedPreferences commit=false不回滚缓存，不能把新adapter读取缓存false视为完成。`TarePersistenceBarrier` 每次写先建立/同步文件marker，再写record；只有record commit成功且marker删除成功才算清除。read使用marker存在或record pending，任何读取异常保守pending。Android marker位于noBackupFilesDir/scale_tare_pending；Mock fixture使用独立缓存子目录marker，不能触产品路径。8项屏障测试先RED7失败；Android清除失败fixture实际写缓存/磁盘false再返回false，检查marker/新adapter UNKNOWN。此为进程重启保护，不声明断电/存储介质损坏保证。Mock覆盖升级新增第13组偏好及marker保留检查，同源码/schema1→2，不是历史Alpha迁移。
