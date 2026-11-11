# 初始认证早 Settings 回报竞态

- 需求与范围：trace84 显示主 Settings 在 auth GATT success 之前 1ms 到达，原代码丢弃后停在 SYNCHRONIZING，用户需手动重连。只修 `DeviceSession` 初始认证接收顺序及 Session/NativeDeviceHub 原时间只读透传，不改命令、固件白名单或认证门禁。
- 来源/基线：现有 `fix/home-switch-readback`，`43d0818b4cf2869f5d8efd3fcdfadac065778ca5`；沿用主任务来自 develop 的分支，不新建工作树。PR 目标 develop；本子任务不提交、不推送、不创建 PR。
- 方案：auth 出队 `beforeDispatch` 后才允许缓存合法主 Settings 及接收时间。auth 成功先通知 SYNCHRONIZING，核对 owner/state/deadline 后经 live Settings 相同处理路径发布；失败、取消、断连、超时或换代清除缓存。SYNCHRONIZING 观察者重入取得新报文或新 owner 后，旧缓存不能覆盖。
- 时间边界：保持初始化原 stage deadline 和 auth 实际派发 5s deadline；success 不得延长已过期认证窗口。缓存发布使用原接收时间，并检查同步窗口和 Settings freshness，不伪造新报文。idle、sleep 不缓存。
- 验证：先在未修代码运行独立 `CoffeeInitialAuthenticationRaceTest`，11 例中 5 例失败，直接复现早报文丢失和延迟 timeout 回调问题；修改后全量 JUnit 768 例通过。详细结果见下节。
- 协议/设备风险：不新增任何硬件写入或自动重试；不放宽固件控制权限，不清未知记录，不改变既有锅炉 refresh。设备实际初连效果仍须由主任务在授权范围验收。
- 发布/签名/升级：不发布、不构建/安装 APK、不操作 ADB 或咖啡机；无包名、版本、签名、数据格式变化。
- 回退：仅回退本补丁和对应测试；保留持久化未知记录及用户数据，不卸载、不清记录。

## 验证记录

测试日志位于本机临时目录，非仓库交付依赖：

- 修复前 `./gradlew :device-session:test --tests '*CoffeeInitialAuthenticationRaceTest' --console=plain`：11 例，5 失败，日志 `/tmp/auth-race-red.log`。
- 修复后 `./gradlew :device-session:test -x :device-session:verify --console=plain`：82 suites、768 tests、0 failures、0 errors；日志 `/tmp/auth-race-junit.log`。
- 首次完整任务发现两个旧 DeviceChecks 断言假定“auth 派发后早 Settings 即使 success 也继续 SYNCHRONIZING”，与修复目标冲突。经协调者批准，第一例改为 success 后采纳、success 前仍不发布 idle；第二例改在 auth 派发前接收 Settings，继续验证同步超时和禁止写入。
- 最终 `./gradlew :device-session:test :bluetooth-android:compileDebugKotlin --console=plain`：BUILD SUCCESSFUL，Session checks 103 passed；JUnit 82 suites、768 tests、0 failures、0 errors；Hub Android 编译通过。日志 `/tmp/auth-race-final.log`。未使用 `-x`。
- `git diff --check`：通过。

时间透传边界：Session `settingsObservedAtMs` 和 Hub `coffeeSettingsObservedAtMs` 保留 nullable 原接收时间；测试确认 success 前为空、success 后保持10ms而非11ms、失败/换代为空。Service consumer 的使用由并行 mobile 修改负责，本补丁不声称仅凭这两个 getter 已完成全链路时间传递。

独立回归覆盖：auth 派发前忽略；早主 Settings 成功仅发布一次且保持接收时刻；同步 driver notification/completion；失败和 disconnect 后缓存不落新 owner；无 tick 的 auth deadline 和原初始化 deadline；SYNCHRONIZING 观察者新 Settings、新 generation 及过期窗口；正常 success 后主 Settings。

## 集成与授权更新

用户已要求完成后安装测试包、创建 PR 并合并。此修复与直接休眠／萃取交互一同集成；Service 已采用 Hub 的原接收时间，原生时间缺失保持 null，Mock 不变。2026-11-11 的真实 BLE 测试包已同包同签名同 versionCode 80 覆盖平板，未操作机器。提交、PR 及合并遵守仓库本地入口和必需 CI；不创建发布标签或手动发布 APK。安装成功和合成时序测试不等于该竞态已在实体设备上再次重现并验证。
