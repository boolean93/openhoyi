# 设备自动连接：使用会话与扫描调度

## 任务边界与 Git

- 需求与范围：落实已确认的设备自动连接流程；首次手动选择，之后自动恢复常用设备；咖啡机 READY 触发秤扫描；晚开秤仍补连；手动断开只暂停当前使用会话，长期关闭单独设置。
- 来源：origin/develop，df3b9225736fb2f7386a59b1bee8a66d303ed483（2026-11-07 fetch）。
- 工作分支：feature/device-auto-connect；独立工作树 openhoyi-device-auto-connect；PR 目标 develop。
- 唯一规则源：[Git 工作流](../git-workflow.md)；结构依照 [任务模板](task-template.md)。已确认 hooksPath=.githooks，无覆盖用户 hook。
- 分工：Astra 负责本规划与独立复核；openai/gpt-6.1-sol subagent 负责实现、自查、精简测试和证据。实现期间源代码由 Sol 独占写入，Astra 只读。
- 本轮仅本地实现和验证，不推送、不建 PR、不合并、不发布、不安装、不操作物理设备。后续获准提交/推送时仅纳入本任务文件，以普通 push 和 PR 进入 develop，遵守必需 CI，不直推主干。
- 不改协议帧、签名、versionCode、萃取控制、发布流水线；禁止删除未确认操作记录、自动重发未知结果。

## 已确认体验

1. 冷启动/长时间离开后回到可交互前台（含屏幕解锁恢复）检查已记忆设备。短暂页面切换、旋转、短时灭屏不是新会话。
2. 蓝牙恢复、权限恢复触发连接资格重检查，不能清除手动暂停或长期关闭。
3. 咖啡机从非 READY 进入 READY 后，秤未连接且未连接中则立即请求一次自动扫描；受所有资格和安全门禁约束。重复 READY 通知不反复触发。
4. 只连接已记忆地址及协议的秤，不抢陌生设备；咖啡机与秤互不依赖，可先单独用秤。
5. 快速重试到期进入低频等待，而非永久放弃；前台使用中超过 10 分钟才开秤也应自动补连。
6. 手动断开暂停当前使用会话；显式重连立即解除该角色临时暂停。长期关闭自动连接不因新会话或显式连接被偷偷打开。
7. 前台离开/锁屏后停止普通扫描；保留现有必要的萃取恢复与安全通知路径，不为本功能新增后台无限扫描服务。
8. 自动连接不产生额外唤醒、加热、去皮、计时或萃取指令。秤在本杯中晚出现不能改变本杯停止条件。

## 架构

### A. 纯策略层（device-session）

- 将使用会话、每角色自动连接意图、恢复节奏从 Android 回调中抽离为可注入时间的纯 Kotlin 策略。可以演进 ReconnectPolicy/CoffeeReconnectPolicy，避免另造平行状态机却遗留旧计时器继续竞争。
- 状态必须分离：remembered target、persistent autoEnabled、session manualPaused、environmentBlocked、unsupported/credentialBlocked、transport phase、retry due、scan/connect in flight。
- 每角色只允许一个连接尝试；每次尝试使用现有 generation/owner 保护。刷新前台或服务重建不等于解除用户意图。
- 状态/决策可观察：未配置、自动关闭、本次暂停、环境不可用、寻找中、等待开机、连接中、就绪、需处理。UI 和日志读同一份状态，禁止继续把旧 suppressed 当全部状态。

### B. 使用会话与持久化（mobile）

- 建立 application/service 共用的 session/prefs owner，而不是每个 Activity 各自重置。采用 AppVisibility 的 logical owner 去重，补充真实可交互/解锁判断。
- 本版默认连续离开可交互前台 30 分钟视为新使用会话；阈值集中常量，标注待实机体验调优。按离开时长，而非从启动时长或零点计算。屏幕锁定即算离开，即使 Activity started 集合尚未变化。
- 普通冷启动创建会话；但同一次使用中的短时进程/服务重建不得靠重新 new 对象绕过手动暂停。需要持久化会话身份、最近可交互/离开时间及角色暂停状态，冷启动根据可靠的持久上下文恢复或续建。
- 进程存活使用 elapsedRealtime；跨进程/重启需有明确回退规则，时钟回拨、计时基准重置不能生成负间隔或无限暂停。将跨重启/时钟异常的规则和测试写入交付，不泛化为一套复杂时间框架。
- 不把咖啡机 READY、单纯蓝牙 STATE_ON、一次配置重建当新会话。
- 新增每角色长期 autoEnabled（默认 true），与 session pause 分离。旧 coffeeAutoSuppressed/scaleAutoSuppressed 是旧“断开”产生的，不可迁成永久 autoEnabled=false；兼容迁为本次暂停，保证后续新会话可恢复。不得触碰设备未确认写入和萃取恢复记录。
- SettingsDeviceControls 等现有设备设置入口提供咖啡机/电子秤自动连接开关及简短说明；关闭开关停止自动任务，不主动打断现有 READY 连接或萃取。手动连接允许，但不得隐式改回长期开关。
- 如接入“忘记设备”，必须沿用/增加统一的安全门禁，取消该目标的自动扫描和连接、清除常用目标，不清除未知恢复记录；若现有代码没有此操作，不为本任务扩张做大型设备管理 UI，但策略必须支持目标移除/替换立即失效。

### C. 唯一 Android 调度与扫描所有权（bluetooth-android + mobile）

- MobileService 为编排入口，NativeDeviceHub 为 transport owner；MainActivity 只提交可见性/启动需求，不能持有第二个重连循环。
- 蓝牙 STATE_CHANGED、屏幕 OFF/USER_PRESENT 或等价生命周期信号必须按当前 SDK 正确注册/注销 receiver；真实前台状态与 PowerManager/Keyguard 状态合并。权限恢复可通过 onResume/服务刷新检查，无权时不得创建扫描死循环。
- 关键启动路径：若首次打开 App 时蓝牙关闭，服务尚未创建，用户随后开启蓝牙且仍停留 App，必须也能启动合适的服务并恢复调度，不能只在已存在 Hub 注册 receiver。
- ScanCoordinator 保持唯一物理 BLE scan。新增 owner/token 或共享订阅仲裁：手动设备扫描优先；自动扫描不能打断手动扫描、改写手动 UI candidates，也不能因“scan already running”永久丢失请求。
- 手动扫描期间可以安全复用匹配广播供自动策略，但用户正选择/替换目标时不得抢连旧目标。自动扫描结束、取消、后台切换、目标变化均使旧回调失效；严禁 close 误杀其他 owner。
- 自动扫描按记忆地址匹配，不能依赖广播一定带 deviceName；已记忆协议来自成功连接记录，名称只是新设备分类提示。根据现有 scanner 结构增加目标过滤/回调路径；不降低协议初始化校验。
- 初始默认节奏：首次/前台恢复/咖啡机 READY 及时请求 5 秒扫描；快速阶段约 1 分钟内按至少 10 秒启动间隔重试，随后 5 秒扫描/30 秒周期低频等待。常量集中、事件合并，避免 Android 扫描节流。未测不能承诺具体连接耗时。
- 发现目标后只启动一次 GATT 连接；CONNECTING/DISCOVERING/SUBSCRIBING/INITIALIZING/SYNCHRONIZING/READY 不重复扫描或连接。失败按退避重新调度，不重置永久错误/凭证阻塞；新会话也不得绕过凭证错误保护。
- 咖啡机继续使用已验证的凭证连接通路，不要求无必要的全设备扫描；去掉前台正常使用中的永久 10 分钟失效，用低频恢复替代；保留凭证错误上限和原设备恢复门禁。
- 处理 MobileService stopAutomaticScale/automaticScaleOnly 的旧 600000ms shutdown 逻辑，否则新等待策略会被旧服务计时器截断。前台有效自动连接需求可维持 owner；后台无必要工作时正常释放。
- 环境不可用、明确不支持、手动暂停、长期关闭、目标移除时取消自动任务；恢复环境仅重新评估，不修改用户意图。

### D. 安全接入

- 复用 DeviceConnectionGate、ShotGate、manualShotActive、未确认设置写入、凭证与目标匹配保护。检查自动 Hub 路径是否绕过仅在 MobileService 的门禁；必要时通过主线程 callback 提供相同资格判断。
- 在扫描结果回调实际发起连接前再次验证资格、owner/generation 和萃取状态，不能只在开始扫描时检查。
- 服务关闭、蓝牙关闭、忘记/换设备、手动断开使待执行自动请求失效；晚到回调不能复活旧连接。
- 不更改 ExtractionController 的断秤安全处理或未知结果处理。现有初始化若有必要协议帧仅沿用，不新增去皮/计时控制。

## 实现步骤 / 预期改动范围

1. 补齐纯策略和会话持久化，添加针对性 JVM 测试。
2. 改造 ScanCoordinator/NativeDeviceHub 的自动扫描调度、目标匹配、重复请求和清理。
3. 接入 MobileApplication/AppVisibility/MainActivity/MobileService 的屏幕、蓝牙和服务生命周期，更新持久设置与自动停止逻辑。
4. 更新 SettingsDeviceControls/TaskShellDeviceStatus 等必要设置与状态文案；中文和英文资源，其他语言英文回退，避免伪翻译。
5. 运行最小相关策略/Android 编译/既有连接安全回归；报告准确命令、结果与限制。Astra 只读独立复核后将修正交回 Sol。

## 精简验收

- 隔夜：手动断秤、离开前台 >30 分钟，保持同进程或恢复进程后均清除临时暂停，晚开秤能自动补连；服务启动/第一次扫描超过原 10 分钟仍等待。
- 短暂：手动断开后短时灭屏、旋转、页面切换、服务重建、咖啡机 READY、蓝牙切换均不清暂停；显式重连可解除。
- 联动：咖啡机进入 READY 只触发一次合并扫描请求；长期关闭/本次暂停/秤连接中/秤 READY/萃取不允许时不触发连接。
- 扫描：自动与手动争用不并发、无互相 close、后台取消/换目标后旧回调无效；记忆秤广告无名称仍能按地址匹配。
- 环境：App 启动时蓝牙关闭，随后开启可启动 owner；权限缺失不反复尝试，恢复后重评估；锁屏后台不常规扫描。
- 意图：长期自动关闭跨会话/进程保留，手动连接不改长期设置；旧 suppressed 不变永久关闭；目标清除无自动连接。
- 安全：未知萃取/设置写入恢复门禁、手动萃取门禁、凭证阻塞、单连接 owner 不回归；不新增自动设备控制写入。
- 本轮不接触物理设备。真实平板亮屏/解锁、不同秤广播特性、扫描节流、实际连接时间与整夜功耗为待授权实机验收，不以 JVM/Mock 通过替代。

## 发布、回退、证据

- 不发版，不改签名或版本，不构建分发承诺；未获得安装与设备操作授权。
- 回退通过后续 revert PR 或修复，保留设备记忆、长期选项和未知记录；不卸载清数据。新增偏好采用独立版本键，兼容迁移不得破坏旧记录。
- Sol 在此处追加实现摘要、最终常量/时间回退语义、实际验证命令与结果、未验证项；不要声称 CI/实机通过，除非确实执行。
- 当前状态：架构、实际 Sol 实现、Astra 独立代码/体验路径复核和精简本地验证完成。复核发现 R1 已由 Sol 修复，最终代码测试与 Debug/Mock 编译通过（见文末）。这是本地开发交付，不代表 UI/实机验收、CI 或可发布结论；未提交、推送、安装或操作设备。

### Sol 实现证据（2026-11-07）

- 纯策略：新增 `DeviceUseSession`、`AutoConnectTiming` 和 `AutomaticConnectionGate`；移除两角色旧 10 分钟永久到期，快速失败恢复后保留低频等待。UNSUPPORTED 与 session pause 分离；凭证阻塞不因新会话/前台刷新清除。
- 使用会话：Application 级 `DeviceAutoConnectOwner` 先于 Service 注册蓝牙/屏幕/解锁 receiver；`PowerManager.isInteractive` + Keyguard + logical Activity visibility 决定真实前台。持久化 session id、elapsed/wall/BOOT_COUNT 和两个临时暂停；同 boot 只按 elapsed 计算离开时长。BOOT_COUNT 不同或 elapsed 回退创建新会话；墙钟变化不影响同 boot 的间隔。仅可交互前台每 15 秒持久化心跳；短时进程重建最多有该粒度的离开时间误差。旧 suppressed 兼容保留为首次会话暂停，不转换成长期开关。
- 常量：离开 30 分钟新会话；扫描 5 秒；快速阶段 60 秒、至少 10 秒启动间隔；随后 30 秒周期。咖啡机 READY 只在边沿触发秤 `requestSoon`，仍受启动间隔、用户意图、transport 和安全条件限制。常量需授权实机调优。
- Android：唯一 ScanCoordinator 新增 MANUAL/AUTOMATIC 所有权，手动优先并使旧 generation 失效；自动结果不进入手动 candidates。按已记忆地址识别无名称广告，协议仍从已成功记忆的 selection 读取。实际 GATT 前再次检查地址/协议、generation、前台与 host 安全资格。显式换设备、后台、暂停/关闭和 transport owner 关闭取消自动扫描；停止未完成的自动连接，不打断 READY 链路。Hub 提供安全受限的 remembered-target 更新/移除接口，本轮未扩张忘记设备 UI。
- Service：`manualShotActive`、未知萃取/设置恢复、cup/schedule/preheat 与长期开关均参与自动 host gate；保留咖啡机原设备凭证/恢复路径。手动扫描后保留选择资格租约，成功 READY 或离开设备设置释放；新目标失败期间不抢回旧设备。旧 automaticScaleOnly 在有效前台不再 10 分钟退出。
- UI：设备设置各角色独立长期开关和会话说明；Service 的自动状态投影供设置、秤快捷状态和 `device.auto_state` 转换日志共用；新增 session 转换日志不包含密码。中文、英文及其余六语言明确英文回退，修改 catalog 后由现有生成器更新资源。
- 新增/调整精简回归：会话的短恢复/长离开/持续前台/进程重建/重启/elapsed 回退/墙钟回拨；超过 10 分钟等待、请求合并与节流；unsupported/目标移除不等于解除用户暂停；manualShot/未知恢复 gate；新会话不绕过 coffee credential block；锁屏/解锁 visibility；既有 PolicyChecks 的旧 5 秒/永久到期断言改为新节奏及持续等待。
- 实际通过：`python3 localization/validate_catalog.py`（972 source keys，七语言全部有效）；`python3 localization/android_resources.py --check`（每语言 971 个生成资源、无漂移）；`python3 -m unittest discover -s localization/tests`（22 tests，OK）；`git diff --check`（无输出、成功）。
- 首轮子会话曾受环境门槛阻塞：`./gradlew :mobile:compileDebugKotlin :mobile:compileMockKotlin --console=plain` 在 wrapper 启动即 exit 1，`/Users/boolean93/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1-bin.zip.lck` 报 `Operation not permitted`，未绕过缓存路径。随后 lead 在授权环境已代跑：单独 Debug/Mock 编译 exit 0（1m10s）；以下整条精简命令也 exit 0，`BUILD SUCCESSFUL in 1m18s`，包含 session verify `101 passed`。这是修正 R1 **之前**的通过证据，不覆盖本次新改动：

```sh
./gradlew :device-session:test \
  --tests io.openhoyi.session.DeviceUseSessionTest \
  --tests io.openhoyi.session.CoffeeReconnectWireTest \
  --tests io.openhoyi.session.SingleScaleConnectionSlotTest \
  --tests io.openhoyi.session.ShotRecoveryGateTest \
  --tests io.openhoyi.session.MachineWriteDispatchPermitTest \
  :mobile:testDebugUnitTest --tests io.openhoyi.mobile.AppVisibilityTest \
  :mobile:compileDebugKotlin :mobile:compileMockKotlin --console=plain
```

- `device-session:test` 现有依赖会自动执行既有 verify smoke（包含 PolicyChecks）；没有另加无关完整矩阵。上述首轮 Gradle/JVM 命令已由 lead 运行通过；本次 R1 不修改 device-session 或本地化，复用该证据。
- 自查重点：自动扫描不会调用手动 candidates 回调；scan 回调读取地址的权限异常在 catch 内；重复 READY 不反复触发；短时 UI 重建不清 pause；手动连接不写 autoEnabled；自动 scale callback 同时检查 host gate 与 DeviceConnectionGate；咖啡机 OUTCOME_UNKNOWN 保留同地址恢复资格但仍过 service 的恢复记录匹配；不修改协议帧/萃取停止条件/未确认记录。
- 未验证：本次 R1 的新回归/最终编译（见下）、receiver/FGS/扫描 owner 的 Android 运行时、设置界面布局/操作、平板锁屏解锁、晚开真实秤、Android 扫描节流、连接耗时和整夜功耗。首轮 JVM/编译已有 lead 通过证据。无 adb、安装、物理操作、CI、提交、推送、PR、发布、版本或签名改动。

### 独立复核修正和证据（2026-11-07）

- R1：首开蓝牙关闭且无 Service，`bindExisting(flags=0)` 失败；之后进程 owner 因蓝牙恢复或设置开启自动连接而启动 Service，已停留的 MainActivity 不会重新绑定。此项由 Astra 只读独立复核发现，Sol 修正，不将实现者自查冒充独立复核。
- 新增 Application 级 `ServiceAvailability` 多订阅通知，独立于 `DeviceAutoConnectOwner.onChanged`；`onCreate`、单纯 bind 或启动请求都不发布可用。真实/Mock 的 `onStartCommand` 完整初始化成功后发布；shutdown 完成（不含安全门禁拒绝停止）和 onDestroy 发布停止。owner id 校验避免旧实例停止误清新实例，重复成功/停止幂等。
- MainActivity 实际接入纯 Kotlin `VisibleServiceBinding` seam：onStart 订阅并回放当前状态，onStop 先退订再释放；启动成功通知只 `bindService(flags=0)`，不启动服务、不增加扫描/连接循环。显式 UI 启动仍使用 `BIND_AUTO_CREATE`；已有或正在等待 binder 的绑定不重复。失败的 Android bind 立即 unbind 清理 dispatcher；退出后迟到的 connected 回调被 lease guard 忽略。已提前 bind、但当时 running=false 的实例在成功通知后重新 ensureKnownDevices/publishService，停止通知清空并发布 UI owner。
- 核对长期自动开关：设置经进程 owner 启服务，复用相同成功通知，无需另一个 onResume。Activity 旋转/短时重建会退订旧页并对新页回放可用状态；Service 重启再次发布，失败启动不被误当可用。未修改会话暂停、扫描 owner 或任何协议/设备控制。
- 新增 `ServiceAvailabilityTest` 四个精简回归，覆盖初始无服务→保持同页→进程启动成功→只一次有效 flags=0 绑定/获得 owner→退出后不补绑；显式 create 先绑定后运行及停止；Activity 重建回放/旧页清理；旧 Service 停止与多订阅独立清理。测试调用的 seam 就是 MainActivity 使用的逻辑，不是另造测试模型；Android binder 投递仅以 host 回调模拟，仍需授权环境运行与最终独立复核。
- 本次实际执行：`git diff --check` exit 0。以下最小 Gradle 命令在本子会话 wrapper 即 exit 1（同默认 `/Users/boolean93/.gradle/...zip.lck` `Operation not permitted`，未进入 Kotlin 编译/测试）；未申请升级、未改缓存路径绕过。请 lead 在授权环境代跑以下命令，**本次新测试与编译当前待跑，不能视作通过**；无需重复已通过的 device-session/localization：

```sh
./gradlew :mobile:testDebugUnitTest --tests io.openhoyi.mobile.ServiceAvailabilityTest \
  :mobile:compileDebugKotlin :mobile:compileMockKotlin --console=plain
```

- 剩余风险：Android 运行时 FGS/binder/系统蓝牙回调与真实设备体验仍待授权验证；纯 JVM 与编译不替代实机。无提交、推送、PR、合并、发布、安装、adb 或实际设备操作。

### Astra 最终独立复核与验证（2026-11-07）

- 实现模型为实际 `openai/gpt-6.1-sol`，由 Astra 独立审查；R1 回交 Sol 实现，最后一次交还写权限后才运行最终验证。Astra 仅更新本节证据与状态，不替代 Sol 修改源代码。
- 复核范围：隔夜与短离开会话语义、长期选项与临时暂停分离、Application 环境事件接入、咖啡 READY 边沿、长期秤等待、单扫描 owner/手动优先/迟到回调、连接前安全门禁，以及 R1 的页面生命周期绑定/解绑和启动成功通知。R1 已闭环，本轮静态复核未再发现阻断项；不将代码路径审查说成实际 UI 体验验收。
- 首轮完整精简验证退出 0，BUILD SUCCESSFUL in 1m18s；五个 session 筛选类共 69 个测试、AppVisibilityTest 7 个测试及既有 verify 101 项通过。其后首位 Sol 自查增加了 DeviceUseSessionTest 的第七个用例并调整策略，故最终补跑该类，不仅验证 R1，不重复无关矩阵。
- 最终授权命令（未改 Gradle 缓存路径；首次 workspace-write 拒绝后按原命令审批重试）：

```sh
./gradlew :device-session:test --tests io.openhoyi.session.DeviceUseSessionTest \
  :mobile:testDebugUnitTest --tests io.openhoyi.mobile.ServiceAvailabilityTest \
  --tests io.openhoyi.mobile.AppVisibilityTest \
  :mobile:compileDebugKotlin :mobile:compileMockKotlin --console=plain
```

- 最终结果：exit 0，BUILD SUCCESSFUL in 1m17s。XML 确认 DeviceUseSessionTest 7、ServiceAvailabilityTest 4、AppVisibilityTest 7，均 0 skipped/failures/errors；verify 101 项全部通过，Debug/Mock Kotlin 编译通过。存在既有弃用等编译警告，未扩张本轮范围处理。以上结果覆盖上文 Sol 交付时的“当前待跑”历史状态。
- 本地化证据沿用 Sol 已实际执行的 catalog/生成资源校验与 22 项 unittest；最终源代码交付 `git diff --check` 通过。未运行无关完整测试矩阵。
- 本轮完成边界：本地架构规划、实现、精简验证、独立静态复核。真实 Android 广播/FGS/binder/扫描节流、设置布局和操作、锁屏隔夜/晚开秤/功耗/连接时延仍待授权平板验收；不安装、不控制设备、不提交或推送、不发布。

### 平板双包安装记录（2026-11-07，用户插机后单独授权）

- 用户插入平板并明确要求安装两个 App，属独立安装授权，不等同发版或机器操作授权。设备 `b2c7a2e9`（Xiaomi Pad 5 `elish` / M2105K81AC），`adb devices` 状态 device。
- 安装前基线：`io.openhoyi.mobile` 与 `io.openhoyi.mobile.mock` 均为 versionCode 57 / `0.2.0-dev.57`，签名 SHA-256 `c0f42639…6a0f54`（Android Debug），firstInstallTime 分别为 2026-10-27 与 2026-09-25。真实应用当时无萃取（最后事件为 12:14 秤重连 FAILED，最近通知为“设备连接运行中”），满足不打断萃取条件。
- 构建：`./gradlew :mobile:assembleDebug :mobile:assembleMock -PhoyiVersionCode=58 -PhoyiVersionName=0.2.0-dev.58`，exit 0，BUILD SUCCESSFUL in 1m16s（首次调用被 `~/.gradle` 写权限拦截，按原命令审批重试，未改缓存路径）。
- 产物核验：`aapt2 dump badging` 确认包名 `io.openhoyi.mobile` / `io.openhoyi.mobile.mock`，versionCode 58，versionName `0.2.0-dev.58`；`apksigner verify --print-certs` 两个 APK 证书 SHA-256 与平板已装包（拉取 base.apk 复核）完全一致。
- 安装：两次 `adb install -r` 均 Success（保数据覆盖，未卸载、未清数据）。安装后两包 versionCode 58、firstInstallTime 未变、`lastUpdateTime` 为 2026-11-07 12:29，签名未变。
- 数据保留抽查：`shared_prefs` 仍在；`devices.xml` 保留 `coffee=94:C9:60:CD:7C:A4`、`scale=CA:56:5B:4F:CE:FA`、`scaleProtocol=bookoo` 及旧 `*AutoSuppressed=false`。新键 `coffeeAutoEnabled`/`scaleAutoEnabled` 将在首次启动按默认开启生成。
- 边界：未启动 App，未触发连接、加热、萃取或任何机器写入。双包运行时体验（隔夜解锁、晚开秤、蓝牙恢复、自动扫描节流）仍待用户实际使用验证。

### UI 回撤（2026-11-07，用户否决）

- 用户明确反馈：只要求优化蓝牙连接能力，不应改动界面；原方案在设置→设备页新增的「自动连接常用设备」开关与说明、设备状态行的自动状态文案、以及导航栏秤状态文案替换均属越界。用户选择全部撤掉、UI 一点不变。
- 已回撤：`SettingsDeviceControls` 删除 Switch 与说明文字、状态行恢复为 `身份 · 状态`；`TaskShellStatusView` 与基线完全一致；删除 8 语言 `auto_connect_*` 共 11 个键（catalog 与生成资源重新对齐，source.json 哈希刷新，`validate_catalog.py` 961 键全语言有效、`android_resources.py --check` 无漂移）；`DeviceAutoConnectOwner.intentStatus`/`setEnabled` 与 `MobileService.automaticStatus`/`logAutomaticStatus` 一并删除，避免残留未使用 UI 资源。
- 保留的非视觉改动：`beginDeviceSelection`/`endDeviceSelection` 扫描占用标记（无 UI），以及自动连接、会话、扫描、绑定逻辑本身。
- 长期开关不再暴露给用户，`coffeeAutoEnabled`/`scaleAutoEnabled` 保持默认 true 仅作内部语义；用户可见行为回到「手动断开=本次暂停，手动重连=恢复」。若后续需要控制入口，按用户意见放在导航栏，另立小任务。
- 验证：`:mobile:compileDebugKotlin` 与 `:mobile:compileMockKotlin` BUILD SUCCESSFUL；重新构建 versionCode 59 双包，`aapt2 dump resources` 确认不再包含 `auto_connect_*` 字符串，两包 `adb install -r` 均 Success。
