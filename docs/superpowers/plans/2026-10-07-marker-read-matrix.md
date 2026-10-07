# 恢复文件实际读取矩阵

**Goal:** 使用真实Android私有文件与生产Service lazy验证损坏/残留记录不能误放行，合法记录保留设备归属。

**Architecture:** 仅androidTest增加RecoveryMarkerReadChecks。每fixture隔离prefs/noBackup目录，清空缓存偏好使文件成为唯一证据；无Hub/BLE/系统/组件，实际调用六控制入口。生产代码及清除资格不改。

- [x] SHOT/MACHINE各十场景：无文件、空文件、坏JSON、4097字节、目录、已清除record、缺字段、坏类型/未知kind、合法record、残留地址；共20场景，18pending×6入口=108，2无marker回到无Hub门槛。
- [x] TARE四场景：无文件、空、目录、非JSON，存在文件即恢复UNKNOWN；不模拟物理秤，不宣称Hub去皮启动运行证明。
- [x] 编译测试APK、Lint、独立审查（无阻塞）；提交并推送云端执行。运行marker读取后再宣称Android通过，本轮只补验证不要求原实现RED。

清除偏好且无marker只表示当前版本未记录请求，不证明旧版本硬件状态。矩阵不读取或删除产品文件，清理失败独立聚合。

2026-10-07：6ff0ac7/Mock37594972905实际产物通过，后续fdf3827/Mock37595770233产物再次命中20/108/2/4精确marker与result。新矩阵Android执行已验证，仍限于隔离文件/入口门禁，不替代设备写入或物理状态。
