# 修复秤刷新列表的名称回退

- 来源/目标：现有 `fix/difluid-endpoint-compat`（develop 派生）→ develop；仅本地提交，不推送、不创建 PR、不发版。后续 PR 使用 scripts/create-pr。
- 目标：当前广播缺少名称、系统缓存名称可识别时，秤仍能成为扫描候选；保留原来可识别广播的显示能力。
- 证据：官方 Android 桥 DCBluetoothDevice.java:48/54 分别以 BluetoothDevice.name 和 ScanRecord.deviceName 提供 name/localName，官方 JS 使用 name || localName。原生扫描此前只读后者。
- 实现：当前可识别广播优先，其次可识别缓存名；覆盖所有既有秤族和 HOYI 机器。与官方缓存优先有意不同，避免过期缓存遮蔽当前合法广播。名称仅作候选提示，不放宽 GATT 协议验证。批量扫描结果复用单条结果处理及 generation 保护。
- 刷新/权限：人工扫描已有自动扫描取消及旧回调失效保护；连接页保留权限、旧 Android 定位与蓝牙检查，列表直接消费扫描候选，无额外名称过滤。本轮不修改这些行为。
- 验证：bluetooth-android JVM 单测及 mobile Debug Kotlin 编译；名称回退、冲突优先级、所有既有名称族与空白/无关设备回归。未运行模拟器、安装或真实 BLE。纯名称测试并非 Android 回调到界面的端到端验收。
- 风险/回退：没有用户现场广播或实体秤，不能确认本次差异就是实际故障根因，也不能承诺官方硬件覆盖。必要时回退本轮扫描名称及批量回调改动。无签名、包名、版本或升级变化。

## 最终验证

- `./gradlew :bluetooth-android:testDebugUnitTest :mobile:compileDebugKotlin --console=plain`：BUILD SUCCESSFUL，44 秒；Bluetooth 1 个测试类、5 项测试，0 失败、0 错误。
- `git diff --check` 通过。Astra 已独立复核扫描实现，确认广播优先、缓存回退及批量回调沿用代次保护。
- 未进行实机扫描、安装、连接或界面端到端验证；不能据离线测试声称用户现场已恢复或覆盖官方所有硬件。
