# Mock覆盖升级与本地状态保留

**目标：** 验证同签名、同包名、较高版本的系统覆盖安装，保留加密认证信息、偏好/历史/采样和未确认写入保护，不碰实机。

**架构：** APK分发工具可比较实际旧/新APK身份和版本，区别声明预检与实际对比。新增androidTest专属MockUpgradeChecks seed/verify阶段，禁止任何组件/蓝牙调用；两阶段之间仅在qemu模拟器执行install -r。种子只允许空的Mock偏好/相关目录，不清空已有数据；保存hash清单和UID，升级后先比原始数据，再读取真实仓库/API与脱离生命周期的Service恢复门禁。正在进行的历史杯须变为UNKNOWN，不能误认为已停止。

**边界：** 版本1→2使用当前同一源码两次构建，仅证明该系统更新和现有schema保留；不证明历史版本schema迁移、真实BLE/机器结果、跨签名升级或真实平板兼容性。fixture密码/地址全为虚构，不记录密码。用户短期不提供设备，脚本必须拒绝物理ADB目标。

- [x] APK实际旧/新比较与拒绝回归（不同包/证书、不递增）；原manifest兼容，新增明确dataPreservationVerified=false/installationVerified=false字段。
- [x] 接入instrumentation参数先复现缺MockUpgradeChecks编译失败；随后仅测试源码实现seed/verify，不改生产存储/协议流程。
- [x] 模拟器专属Python执行器与独立CI：构建同源码Mock1/2，先install1+seed，install-r2+verify，固定package/tag，无截图，不卸载/清数据。失败保留定向日志，所有测试命令有界超时。
- [ ] 双APK/单测/Lint/测试APK、实际旧新比较、本地工具测试、静态复审、云端两阶段运行。真实设备和完整目标保持未验收。


2026-10-03：新增仅androidTest的MockUpgradeChecks seed/verify及模拟器专属执行器/独立CI。固定Mock/test包、同签名1→2、首次写前检查emulator serial+ro.kernel.qemu，拒绝既有Mock安装，不清数据/卸载/触实机。空偏好种子涵盖12组pref、Keystore虚构密码、完成及进行中历史、v1/v2采样/近期孤立采样、导入曲线/历史；升级后先比UID/raw hash，再读取真实仓库及脱离生命周期的Service恢复门禁，RUNNING必须变UNKNOWN，未确认标记不清除。复审捕捉并修正构造器自建mock的隔离遗漏；各工具/ADB/证书提取均限时。生产APK分发工具新增实际旧新比较，标明installation/dataPreservation仍false；14工具+2模拟器拒绝测试通过，真实同APK不递增拒绝且原manifest保留，最终双APK/双单测/双Lint/testAPK170任务通过及资源7113/6回环。独立复审无阻塞；实际覆盖安装/Keystore延续仍待新云端任务，不用编译当升级通过。仅同源码/schema1→2，不证明历史schema迁移或Alpha/真实设备。计划docs/superpowers/plans/2026-10-03-mock-upgrade.md。
第四项中的云端两阶段仍未运行，保留未勾选。
