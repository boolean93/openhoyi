# 原版功能对标审计（2026-09-30）

基线为旧项目 `docs/features.md`、`docs/bluetooth-protocol.md` 和现有拆包源码；首次审计原生基线为提交 `1437e8d`，后续条目随实现更新，最近变更见HANDOFF。此表检查用户能力及其当前实现入口，不把代码存在、离线通过或部分实杯记录当作全部功能验收。

| 原版能力 | 当前原生入口/证据 | 当前结论与剩余工作 |
|---|---|---|
| 扫描、权限、咖啡机认证与初始化 | HomeActivity、MobileService、NativeDeviceHub、DeviceSession；实机报告 | 已开发，部分固件1.1.3连接已验证；权限/后台/断线完整验收未齐 |
| 温度、压力、重量、睡眠和机器告警仪表 | HomeActivity、LiveTelemetry、MachineAlarms；共享告警规则与新鲜度测试 | 已开发；秤流速物理单位及真实仪表仍需核对 |
| 机器手动拨杆、自动压力/流量模式 | MachineSettingsActivity、MachineSettingChange、PassiveShotDetector | 模式写入与被动历史已开发；机器手动萃取由拨杆操作，不由App启动/停止；真实被动识别样本未齐 |
| 五个快捷槽位、曲线启动与停止 | PresetSlots、CurveLibrary、ExtractionController、CoffeeCommands；旧版报文oracle | 已开发100条工厂曲线与采集曲线；部分手动停止已有实杯证据，目标重量自动停止及各槽位未全面验收 |
| 曲线分类、选择和显示 | CurveActivity、CurveLibrary | 已开发；分类/名称查找与报文证明不等于允许任意导入曲线控制 |
| 曲线复制、编辑、删除、保存 | LegacyCurveActivity、LegacyCurveAdapter仅只读/离线候选 | 尚未开发完整编辑；用户明确后置。真实用户曲线旧/新报文证据不足，继续只读 |
| 实时图表和本机历史 | ExtractionActivity、HistoryActivity、HistoryDetailActivity、ShotHistory | 已开发；保留短杯/未知记录是诊断需要的明确差异，仍有30天/500条限制 |
| 原包历史/曲线迁移 | LegacyHistory、LegacyHistoryActivity、LegacyCurveCodec/LegacyCurveStore与导入页面 | 文件导入已开发，合成格式已测；缺两类真实导出。分包后不能直接读取旧包私有数据 |
| 秤连接、去皮与重连 | BOOKOO、ScaleSessionControl、StandaloneTare、ReconnectPolicy | BOOKOO已开发、部分实测；其它型号只有部分只读候选解析，未实现完整BLE控制 |
| 温控、补偿、加热、照明、拨杆/运行模式、水源与待机 | MachineSettingsActivity、MachineSettingChange、SettingsWriteTracker | 已开发受限命令和回读确认；每类真实设置/失败恢复证据未齐，不自动重试未知写入 |
| 睡眠计划、立即睡眠、工作室预热/取消 | SleepScheduleWriteTracker、SleepNowTracker、BrewPreparation、PreheatGate | 已开发；完整生效和恢复仍需实机。没有独立取消回读，不把传输成功当作取消已生效 |
| 累计杯数清零 | CupResetTracker、MachineWriteRecoveryState；双路新归零与序号回归 | 已开发未实机执行；旧本地设置密码确认由当前杯数输入＋二次确认替代，两路都归零才确认 |
| 连接密码保存 | CoffeeCredentialStore、CoffeeCredentialRetryGate | 已开发成功后加密记忆；不是修改机器蓝牙密码，免输/失败回退仍需实机复验 |
| 修改机器蓝牙密码 | 旧版setBleConPwd/0x0B；原生无执行入口 | 未开发/未开放，缺修改、确认、新旧密码重连及失败恢复的可靠序列 |
| 拉杆校准、恢复出厂、排水、水箱滤芯及OTA | UnsupportedCommandGroup与当前无开放控制入口 | 未开发完整控制；存在旧命令不代表已有安全流程。需型号/固件、前置条件、回读或人工恢复证据；滤芯乐观UI与版本门槛见 [专项审计](legacy-pressure-filter.md) |
| 多语言 | 主要原生页面与服务反馈已资源化，启用语义/按钮样式与文字分开；已核对旧版八语言并新增稳定标识基础，见 `docs/plans/native-language-parity.md` | 七语言目录各890键，正式Android资源各889键已生成并打包；统一语言Context、独立偏好提交、主要持续提示身份和通知刷新接口已开发。设置页语言入口已开发，实际入口切换/还原/不变/失败及同服务已在云端Mock通过，母语/布局/RTL、原始元数据与完整Android生命周期/通知验收未完成，不宣称全应用多语言完成 |
| 原生应用版本和更新 | MachineSettingsActivity读取BuildConfig的版本/版本号、模式和包名；APK元数据/资源核对通过 | 只读应用信息已开发，布局待核对；更新安装流程、原生分发/签名连续性仍未实现，不能安装厂商旧版APK作为原生更新 |
| 深浅色与Mock | ThemedActivity、values-night、MockDeviceRuntime、Mock构建；云端Mock截图 | 已开发并有离线视觉证据；Mock不发送蓝牙，不能替代真实控制验收 |
| 萃取提示与提示灯偏好 | 已追踪brewTips/brewTipsLed：鼓励音频播放/停止可自动发送0x21，见 `docs/legacy-brew-preferences.md` | 本地提示/音频未开发；提示灯缺真实执行/恢复证据，原生无发送入口，不按纯UI开关迁移 |

## 开发顺序与证据边界

1. 继续共享业务协调的开发：请求归属、采样序号、超时、事务互斥与安全记录清除；保留参数/固件白名单与实际出队检查。类定义迁移不等于运行时已经统一。
2. 只读应用版本/包身份已开发。在无设备阶段继续梳理完整文案资源化与语言范围、逐项定位提示偏好及原生更新分发策略。这些仍是尚未完成的软件工作，不应标作“仅待实机”。原包本地密码的确认差异已明确，不将其与设备密码混淆。
3. 有真实旧版文件后验证导入；编辑按用户已定优先级后置。未获得逐条字节证据之前不把用户曲线接入启动。
4. 用户重新提供设备后补日常控制验收：自动重量停止、去皮时序、设置/睡眠/预热回读及断链恢复。高风险命令、其它秤和OTA单独取证后开发/开放。

这些是当前实际未完成项。整体对标与硬件安全尚未证明；现有离线测试证明的是所覆盖的编码、状态及队列规则。详细证据见 [覆盖矩阵](coverage.md)、[原生功能状态](native-feature-status.md) 与 [Alpha验收](alpha-acceptance.md)。


2026-10-03：新增设置页原生应用语言入口，八语言自称、独立偏好提交、当前项免重建、失败保持旧语言/选中项及重试提示。成功后刷新通知显示并重建设置页；异步Service绑定后补刷新，不重启设备会话。Mock入口测试覆盖切换/还原、当前项、保存失败和同Service。最终双APK/双单测/双Lint及测试APK构建成功（170 tasks），19项Python、890键目录、889键生成一致性、两APK各7113模板和6转义回环通过，独立复审无阻塞。实际入口运行仍待新云端CI；本轮不安装/操作真机。计划docs/superpowers/plans/2026-10-03-language-selector.md。


2026-10-03：语言入口0b94f39已完成云端实际运行。Verify native app37125094623、Verify Mock lifecycle37125094578及Capture Mock UI37125094627均success；已读取Mock产物compact/wideFont各80 PAGE_START和三项PAGE_LAYOUT/DETAIL_DIALOG/SELECTOR_UI标记，原八语言及五音频标记完整。实际选择/当前项/保存失败/切换还原/同Service检查通过。完整构建日志确认39,823协议检查、32,856通知回放、92会话场景，以及两APK各7113模板与6转义回环。截图任务成功未逐图新视觉核对；不宣称母语、真实通知生命周期、BLE或硬件完整验收。产物/private/tmp/hoyi-language-selector-37125094578/mock-lifecycle。

2026-10-03：已新增scripts/apk_distribution.py及12项独立Python测试，从实际APK验签、读取限定包名/版本、匹配指定证书并导出公开manifest；默认不覆盖，显式--overwrite才替换。实际本机Alpha/Mock通过，错误证书和默认覆盖均拒绝且原清单不变。声明源码提交明确标为sourceCommitVerified=false。复审无阻塞；没有签包/换密钥/安装/卸载。公开本机debug指纹基线见docs/evidence/local-debug-signing-baseline.json；正式密钥、版本递增、已安装证书比对及升级数据保留仍未实现/验收，详见docs/plans/apk-distribution.md。CI新增12项检查，本次脚本提交的云端完整回归待推送后记录。


2026-10-03：已落实原生发布构建配置：mobile/distribution.gradle.kts独立版本/签名预检，Android DSL由mobile/build.gradle.kts接入。release显式code/name/previous递增，五外部环境变量、仓库外绝对规范路径、可打开私钥与预期证书SHA256必须通过；APK/AAB的preReleaseBuild均依赖预检。真实12场景通过（/private/tmp/hoyi-release-configuration-final.log），临时独立buildDir release APK实际签名/版本2/0.2.0通过并清理（/private/tmp/hoyi-release-apk-fixture.log）；最终开发双APK/单测/Lint及Mock testAPK170任务通过，实际Alpha/Mock均默认1/0.1.0且原本机证书保持、资源各7113模板/6回环通过。12分发工具测试与19资源测试通过，独立两轮复审无阻塞。本轮未选择/变更持久正式密钥或安装，不修改协议/控制/状态恢复源码；正式密钥身份、已装证书与版本、真实覆盖更新数据保留仍待确认。CI已接入发布配置及实际临时APK测试，本次新云端结果待推送后核对。


2026-10-03：90aa76f发布配置完整构建37127225211与Mock37127225214均success，日志/产物已读取。native确认39,823协议检查、32,856通知回放、92会话场景、两APK各7113模板/6回环，并有RELEASE_APK_FIXTURE_CHECKS_PASSED和RELEASE_CONFIGURATION_CHECKS_PASSED cases=12。Mock compact/wideFont各80 PAGE_START+三个页面/详情/入口marker，原八语言及五音频marker完整。发布配置本身已完成本地及云端临时密钥验证；不表示已选择持久正式密钥、真实Alpha升级或硬件验收。产物/private/tmp/hoyi-release-mock-37127225214/mock-lifecycle。

2026-10-03：新增仅androidTest的MockUpgradeChecks seed/verify及模拟器专属执行器/独立CI。固定Mock/test包、同签名1→2、首次写前检查emulator serial+ro.kernel.qemu，拒绝既有Mock安装，不清数据/卸载/触实机。空偏好种子涵盖12组pref、Keystore虚构密码、完成及进行中历史、v1/v2采样/近期孤立采样、导入曲线/历史；升级后先比UID/raw hash，再读取真实仓库及脱离生命周期的Service恢复门禁，RUNNING必须变UNKNOWN，未确认标记不清除。复审捕捉并修正构造器自建mock的隔离遗漏；各工具/ADB/证书提取均限时。生产APK分发工具新增实际旧新比较，标明installation/dataPreservation仍false；14工具+2模拟器拒绝测试通过，真实同APK不递增拒绝且原manifest保留，最终双APK/双单测/双Lint/testAPK170任务通过及资源7113/6回环。独立复审无阻塞；实际覆盖安装/Keystore延续仍待新云端任务，不用编译当升级通过。仅同源码/schema1→2，不证明历史schema迁移或Alpha/真实设备。计划docs/superpowers/plans/2026-10-03-mock-upgrade.md。
