# 咖啡机已存密码与自动重连修复

- 需求与范围：普通蓝牙/初始化失败不再使已存密码失效；旧失败计数不阻塞升级后的自动与手动连接。保留现有退避、自动连接开关、手动断开及萃取/未知写入门禁。设置保留显式输入本机密码入口；临时受限不冒充缺少密码。
- 来源分支/基线提交：develop / a5290b0996af635356e99abf5105a33ce77f49e6。
- 工作分支：fix/coffee-credential-reconnect，工作树 /Users/boolean93/workspace/hoyi_app/openhoyi-fix-coffee-reconnect。
- PR目标：develop。
- 设计：沿用加密密码与 READY 后保存行为；普通 FAILED 继续现有退避。忽略旧凭据失败计数，区分需要输入与暂不能连接。显式重输失败不替换原密码，不改设备密码。
- 改动文件与步骤：先在 CoffeeCredentialTest 等最小必要测试复现旧计数和连续失败；再更新 CoffeeCredentialStore/MobileService 与连接入口和必需文案；执行针对性测试与资源校验；Astra 独立只读审查。
- 分工：Sol 实现、测试、自查；Astra 设计确认和独立复核；协调助手负责规划、PR、CI及合入。
- 推送与合并：检查工作树 → 本地验证 → 普通推送工作分支 → 创建PR → branch-policy、verify、lifecycle、upgrade 必需CI通过及审查问题解决 → PR合入develop。用户在本聊天明确授权修复并创建PR合入。
- 验证证据：先失败后通过的针对性JVM测试、所改资源的生成/校验、PR精确源码CI。现场连续掉线及恢复尚未实机验收，不以单元测试替代。
- 协议与设备风险：不修改协议帧、控制写入、版本、签名或未知结果记录；重试只重建连接并沿用原初始化，不自动重发业务写入。忽略旧失败计数会恢复原本被误封锁的连接尝试。
- 发布：不发正式版本、不创建标签、不手动分发APK、不安装或操作设备。仓库已有develop合入CI可能自动产出测试APK，按现有流程运行。
- 回退：通过revert PR回退代码；保留加密密码和设备/历史/未知记录，不卸载、不清数据。旧失败计数失效不需恢复用户数据。
- 未完成与阻塞：真实咖啡机开机/离线后恢复体验待授权实机验收；现场最初断连原因仍未知。

具体规则以 [Git工作流](../git-workflow.md) 为准。

## 实现与审查记录

- 已实现：旧计数不参与重连判断；连接入口区分 STARTED/PASSWORD_REQUIRED/BLOCKED；系统密钥暂不可读保留密文并按原策略重试；设置提供显式输入密码连接；仅同地址 READY 后保存新密码。
- RED：原逻辑在连续 FAILED、遗留计数=2、第三次自动重连三项回归失败；新增凭据对象脱敏回归在修正前失败。
- Astra 独立只读复核：2026-11-09，基线 a5290b09 至本任务源码 diff，无阻塞项。已追踪三个普通连接入口和 NativeDeviceHub→MobileService→DeviceSession 调用链；确认原萃取、设备身份、前台/暂停及最终 Hub 门禁保留，两个凭据对象 toString 脱敏。未运行 BLE、安装或读取真实密码。
- 本地验证：CoffeeCredentialTest、LanguageCatalogTest、LanguageDraftTest、ResourceMessageTest；compileMockAndroidTestKotlin；localization 资源校验。精确最终结果记入 PR。
