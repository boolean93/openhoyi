# 同连接萃取设置证据修复

沿 `feature/task-first-ui` → `develop` / PR4；依据 `docs/git-workflow.md`。用户已明确授权修复并覆盖安装更高版本 Debug，root统一测试、签名核验、数据备份与v30安装；本项不是正式发布，不创建标签，不合并主干。

问题：设置只靠机器主动通知，原生端没有设置读取轮询。180秒策略仅参考一次132秒回报间隔，持续收到新鲜待机帧也会因设置回报超过180秒阻断萃取/预热。

修复：新增 `BrewSettingsEvidence`，仅萃取启动、正目标预热及温补读取可使用同一认证连接已经收到的设置。必须READY、设置非空、接收时间非负非未来；连接/断线清空由现有owner承担。UI准备按钮同步此资格，不伪造或延长settingsAt。Service与DeviceSession提交及出队复核一致。

保留：1500ms实时待机、唤醒/告警、未知结果、启动context代次/地址/设置指纹/5秒有效期和dispatch guard。普通/成对设置写入、杯数重置及睡眠计划仍为原180秒策略；停止、取消预热不变。不新增协议或读取命令。

验证范围：新增共享policy非法时间/连接证据测试；持续fresh Idle超过180秒仍能启动和预热；同场普通/成对设置及杯数仍拒绝；断线换机未收到新Settings禁止；出队时stale Idle和主机未知门禁仍拒绝；原预热队列内容测试迁移到新资格。软件测试不等于硬件验收。

root负责执行 `:device-session:test :mobile:testMockUnitTest :mobile:assembleDebug :mobile:lintDebug` 并记录实际结果。回退用revert PR及更高versionCode同签名修复，不卸载清数据、不降级、不清未知记录或重放命令。
