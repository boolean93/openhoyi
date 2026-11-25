# BLE v2：普通服务与候选恢复账本的共同写入边界

- 基线 `develop` 2496d5dc / 已完成离线阶段 9c8c56aa。本地分支 `fix/ble-v2-shared-writer`。后续 PR 目标 `develop`，本轮不推、不发包、不操作设备。
- 普通路径是 `MobileService.machineWriteRecovery → RecoveryPreferenceStorage.machine → MachineWriteRegistration / 证据确认 → MachineWriteRecoveryState.Storage.write`。保留旧格式、缓存提交顺序、登记逻辑、读观察和 GATT 准入。构建服务不长期占用 lease，仅普通机器标记实际写入时取 lease；温度单独标记、萃取标记不变。
- 现行写入及未启用的拆分 writer 用相同的规范父路径/标记文件名锁（仅进程内）。每次旧写入前在锁内读取当前标记，拒绝候选扩展、未知格式、失效链接或过大文件，即使旧对象曾缓存空或候选 lease 已释放。观察只读，不占写锁，读者仍能看到符合旧版的 UNKNOWN 围栏。
- 普通写入成功之后若要长期接入拆分控制，还有边界：必须让服务持有原文件后端的单点 lease、阻断相竞争的实时控制、核对待回报与启停的权限，不允许从旧缓存授予新指令。当前不会创建拆分 writer、不会分发并行锅炉控制，也不承诺任意早期 APK 版本或跨进程保护。无未知记录重放。
- 以 Mock JVM 定向覆盖两方向锁、别名、遗留格式、围栏存续、旧缓存对象、不确定文件、不同路径和失败释放。Android 真机只曾验证上一阶段隔离文件例程，未验证这次写入整合或实际降级，不放行控制。
- 回退代码无需迁移或删除现有数据；任何无法解释的标记一律保留。
