2026-10-06 当前任务：共享写入登记源码 `e73ffffc680f4dad3bc27f2b2896e7be56c60f7d` 已推送。MachineWriteRegistration五typed Request收拢tracker begin→同步recovery.arm→原失败收尾，返回Registered(token)/Busy/RecordFailed；Service五真实写入仅Registered后继续原baseline/notification/event/send，全部前置门禁/身份/资源不变。持久化失败四类FAILED、BREW consumed回IDLE保持，无发送/自动重试、旧记录不覆盖。20参数测试stub18RED→20GREEN；首次替换错命Mock预热删真机gate导致编译失败，未提交，恢复prepareBrew整个HEAD结构并锚定真实coffeeAddress再替换，逐段前置prefix/基线对照及复审通过，最终219完整构建/回归/Lint成功。错误记录.learnings/ERRORS.md，以后修改重名Mock/真机代码必须唯一分支锚点和完整diff。

新native `37382479655`、升级 `37382479606`、自动UI `37382479607` 最后in_progress，Mock `37382479688` queued。下一轮查这些既有任务并读实际产物，不重复dispatch。本轮没有新增实际Service登记失败的Android fixture，20共享层测试/静态接线不代替该运行证据；下一步可补此入口并继续请求/完成结果协调。85矩阵283807a/native37381614423最终success完整日志读；52c95d4/Mock37380567825完整产物读success，18/108/18+availability/lazy18、12ACK69入口及八语言/五音频/清理通知/两profile80页和三marker通过，不替代新登记源码。用户无设备，不安装/操作；机器计时/其它请求协调/硬件/真实注册Service与历史Alpha迁移未完成。证据docs/evidence/shared-write-registration-2026-10-06.json。

2026-10-06 当前任务：七控制族发送年龄矩阵源码 `283807afc65c58fbe54ecde023b96543b1267bc7` 已推送。只改JVM测试；提取CoffeeSessionFixture保留旧认证/READY模拟流程与原2测试，新MachineDispatchFreshnessTest共5命名方法/85组合：7×4提交前拒绝+7×4实际排队后由complete触发拒绝=56；7×3有效边界实际Write=21；周计划first已写、baseline500ms新鲜、second坏idle拒发且Unknown=4（用tick推进阶段）；4坏idle仍发送精确0200070000停止及fake回调Success。不要把stop回调说成实际停水，不是全设置参数/全曲线。旧DeviceSession两年龄公式临时恢复时前四测试3FAIL，finally原源恢复无生产diff；最终全部device-session check/92命名场景成功，独立复审/diff通过。

新native `37381614423` 最后in_progress；纯JVM测试变更不触发产品Mock（无新生产输入），不重复dispatch。52c95d4/Mock `37380567825` 仍运行中，继续读实际终态产物；其native `37380567798` 与升级 `37380567716` 最终success、完整日志/seed/verify/result实际读。2ae9a37/Mock `37379839704` success完整产物读：18/108/18+availability18/lazyShotRead、12ACK/69入口、清理/通知/八语言/五音频以及两profile各80页和三marker通过，不能替代52年龄源码的产品运行。52自动UI37380567847 success但未视觉验收。证据docs/evidence/machine-dispatch-age-matrix-2026-10-06.json。下一步还需检查机器计时/elapsed timers和共享请求协调；无设备、不安装或控制，目标仍未完成，硬件/注册Service/历史Alpha迁移未验收。

2026-10-06 当前任务：共享采样年龄保护源码 `52c95d48d266510f0ec228cf4e120ac7638d6950` 已推送。TelemetryFreshness先at>=0、now>=at、maxAge>=0再相减；接入PreheatGate取消/ExtractionStartGate咖啡与秤资格、DeviceSession真实发送与出队两guard、Service待机及重量、原Settings/Scale/shot/machine恢复年龄。正常1500包含边界和Settings180000不变，不改字节/优先级/timeout计时器。4项新年龄测试旧3RED，2项真实认证初始化假driver测试旧2RED；实现后6GREEN，完整device-session+219构建/回归/Lint、独立复审及diff通过。负域/MIN→MAX拒绝enterSleep/cancel且无新driver执行，新valid恢复可写；queued sleep出队再校验通过。queued取消仅静态接线，ExtractionPolicy机器计时年龄、ExtractionController机器帧年龄/持续时间等仍未迁移，不说所有时间已统一。

新native `37380567798`、Mock `37380567825`、升级 `37380567716` 最后in_progress、自动UI `37380567847` queued。下一轮查这些既有任务与活动源2ae的Mock `37379839704`，必须新增availabilityChecks=18 lazyShotRead=true marker，不重复dispatch。60f5d5a/Mock37378979088最终success且完整产物读，12ACK/69入口（含READY/CANCELLING）、18/108/18、八语言/五音频/清理通知及两profile80页和三marker通过；共享确认运行已验，不替代活动/年龄源码。2ae/native37379839800成功日志实际读，39823/32856/92、双APK7113/6回环/发布12通过；升级37379839761成功实际seed/verify/result读，自动UI success但未视觉验收。当前无设备，不安装/操作。软件协调/时间保护仍不代表硬件无风险、注册Service真实生命周期或历史Alpha迁移已完成。证据docs/evidence/shared-telemetry-freshness-2026-10-06.json。推送继续使用单命令gh credential helper（全局不改）。

2026-10-06 当前任务：共享恢复活动规则源码 `2ae9a373426c8a837b6aad76feed858dccca3d0f` 已推送。MachineRecoveryActivity统一五typed tracker的busy与availability，Service恢复按钮/cup/schedule busy/三ACK active证据接入；规则保持原状态集合和非Brew shot政策，Brew READY/CANCELLING仍busy。getter只BREW读取shotRecovery且pending时短路ShotGate.active。3新命名测试stub2RED→全GREEN，覆盖36state、144 flags和8 empty/UNKNOWN；219任务完整回归后最终170任务Service编译/单测/双APK/testAPK/Lint成功，独立复审无阻塞。产品原18/108/18保留，新增18 availability断言与隔离Context shot偏好读取0/1次，runner/script marker增加availabilityChecks=18 lazyShotRead=true；实际运行未验收。

新native `37379839800`、Mock `37379839704`、升级 `37379839761`、自动UI `37379839686` 最后in_progress。下一轮查既有任务/实际产物，不重复dispatch。60f5d5a/native37378979071成功且完整日志读（39823/32856/92、双APK7113/6回环、发布12/临时签名），升级37378979031成功产物读；其Mock37378979088仍运行中，12ACK/69入口与共享确认运行不能算通过。自动UI37378979058成功但未作产物视觉验收。docs31b native37379093791成功但不用替代当前源码。Git普通osxkeychain推送两次失败，使用单命令 `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push origin feature/native-ble` 成功；未改全局配置。用户无设备，不安装或控制；整个请求协调及硬件/历史Alpha迁移仍未完成。证据docs/evidence/shared-recovery-activity-2026-10-06.json。

2026-10-06 当前任务：共享机器写入恢复确认源码 `60f5d5a9d96eef68969d3a97a6f1f0f423ba6e4f` 已推送。MachineWriteAcknowledgement接入MobileService五分支，原Evidence/serial/READY/manual-shot优先级/resource/tag/consumed-event-refresh保持；三结果复用原canClear和同步clear，无发送/自动重试。新15项参数化测试stub先5RED，实现后全GREEN；219任务完整回归/构建/Lint通过，独立复审通过。上轮真实Mock37377688521因CANCELLING startShot读取遗漏的curves隔离偏好失败，已修夹具：UUID隔离curves、合法capture-1选择与resolve/validated检查，原精确warning/12ACK69入口不变，偏好不变与finally清理覆盖，修后125任务testAPK/Lint/复审通过。

新native `37378979071`、Mock生命周期 `37378979088`、升级 `37378979031`、自动UI任务 `37378979058` 最后in_progress。下一轮读既有任务产物；共享接线与12/69未运行验收，不重复dispatch。e3aa581/37376998752最终success且完整产物读：18/108/18、旧busy10/58、八语言/五音频/清理通知及两profile各80页和三marker均通过；不能代替本轮12/69。a9b6bb4/native37377688511完整日志读success，升级37377688550的seed/verify/result读success（同源码/schema Mock1→2）；其Mock失败不算通过。证据docs/evidence/shared-write-acknowledgement-2026-10-06.json。用户无设备，不安装/操作；其余业务请求协调、真实硬件和历史Alpha迁移仍未完成。

2026-10-06 当前任务：预热恢复READY/CANCELLING产品测试源码 `a9b6bb42d5b9b3f33e22cf6c1abb23a3800f567a` 已推送，仅androidTest/script。原五kind共通WRITING/WAITING保留；BREW_WAIT真实observe(2,9300)→READY、beginCancel→CANCELLING。READY隐藏advisory但挡五无关控制；CANCELLING精确brew_wait warning挡五入口及startShot，非缺曲线假通过。busy确认10+2=12、入口58+5+6=69；ACK保持attempt0/pending/rawprefs/tracker，刷新非空时间不补缺失。disconnect UNKNOWN后原Storage失败/重试正例保留，fake execution0，不调用实际取消发送。最终101任务testAPK/Lint、shell/diff及独立复审通过，实际运行未验收。

新native `37377688511`、Mock生命周期 `37377688521`、Mock升级 `37377688550` 最后in_progress。必须新marker preheatReady=true/preheatCancelling=true/blockedACK12/entries69，入口仍18/108/18。下一轮查这三项既有任务并读最终产物，不重复dispatch。

e3aa581/native `37376998874` 和升级 `37376998919` 最终success并读日志/seed/verify/result，Mock `37376998752` 仍in_progress，新增18入口尚未运行验收。5a82936/Mock `37376132608` 最终success并读完整产物，原13/78/13、busy10/58、15回读负例、清理通知/八语言/五音频及两profile各80页+三marker通过；该源的三CI均实际证据已验，证据docs/evidence/product-ci-dependencies-2026-10-06.json更新。不能将旧计数替代新增状态/fixture。同源码Mock升级仅qemu/schema1→2，不是Alpha/历史迁移/真实硬件。用户无设备，不安装/控制；整体goal active未完成。

2026-10-06 当前任务：不完整记录的产品入口测试源码 `e3aa581b679dd19c743a70fd01b3084e31c60f57` 已推送，仅androidTest/script。原13fixture保留，新增完整shot-only1、shot false残留地址合法/非法2、machine缺kind残留地址合法/非法2，共18×六入口=108，ACK attempts18。原始字段与expected pending/kind分开；shot要求确切restart、machine残留要求UNKNOWN，空记录严格service_unavailable，ACK保持rawprefs/warning；UUID/detached/noowner/禁组件权限系统及finally不变。不改生产政策，不能说覆盖所有设备操作或READY/GATT。最终101任务testAPK/Lint、shell/diff、独立复审通过，运行未验收。

新native `37376998874`、Mock生命周期 `37376998752`、Mock升级 `37376998919` 最后in_progress。必须读新18/108/18 marker，不能拿旧13/78/13产物代替。下一轮查这三项既有任务，并读对应最终产物。

561b2ed/native `37375463458` 最终success实际日志读，源码残留shot修复的完整CI通过；升级已验，两类证据不替代新增product fixture。5a82936/native `37376132381` 与升级 `37376132464` 均success且实际日志/seed/verify/result读，证据docs/evidence/product-ci-dependencies-2026-10-06.json；其Mock `37376132608` 仍in_progress（旧13marker）。同源码/schema Mock1→2不代表Alpha/历史schema迁移/真实设备。其它docs-only native已终结success，无需重跑。用户无设备，不安装/控制；整体goal active未完成。

2026-10-06 当前任务：产品CI依赖触发范围源码 `5a82936ecf7ce4140ea77dfc24e841dd44b46c90` 已推送，仅两个workflow paths变更。五模块main及Mobile src/mock共149个实际Git跟踪文件、12构建路径及未来新文件匹配；普通JVM测试/文档/Lab排除。原main147旧漏117/133；YAML解析与jobs/permissions/branch结构比较保持，复审补Mock manifest/资源后无阻塞。不改执行器/生产逻辑，未为配置修改重跑Gradle。自动native `37376132381`、Mock生命周期 `37376132608`、Mock升级 `37376132464` 均最后in_progress；本次覆盖新恢复加载/时钟源码的产品运行待这些产物。

d89d6cb/native `37374877456` 与cd39624/native `37374440672` 已success并读日志，完整39823/32856/92、双APK7113/6回环及发布12通过，证据对应json已更新。cd升级 `37374440846` 终态failure，实际annotations为未取得hosted runner、steps空，不算测试结果，不重跑旧任务。

561b2ed/Mock升级 `37375463511` success且实际result/seed/verify读取，同源码/schema Mock1→2数据与恢复保护保持通过，仍非Alpha/真实设备/历史schema迁移。其native `37375463458` 与36ac2bd/docs-native `37375630481` 最后in_progress。下一轮先查上述新三任务与561精确native，读产物再计通过，不重复dispatch；新shared main变更今后自动触发产品级CI。用户无设备，不安装/控制，整体goal仍active未完成。

2026-10-06 当前任务：残留萃取身份修复源码 `561b2ed2e2affcb0b060aba88a72847095f0a400` 已推送。ShotRecoveryState仅false/null为空；false/非null残留地址加载为pending，合法身份保留、非法为空，不改写存储、不发命令。四参数组旧3RED→4GREEN，覆盖加载零写入、错误设备/未决萃取/缺待机、同地址arm幂等、clear失败保持→成功持久化及重载→重复clear不写。全部device-session与219任务完整构建/Lint、独立复审通过。新native `37375463458` 与升级 `37375463511` 最后queued，证据docs/evidence/orphan-shot-recovery-2026-10-06.json，尚未云端验收。

4cec0b3/native `37373939684` 和Mock `37373939613` 已success并读实际日志/产物：十五回读负例、身份5/断连5、busy10/58、存储失败5/重试5、入口13/78及清理/通知/八语言/五音频、两profile各80页与三marker通过，证据docs/evidence/service-recovery-readback-2026-10-06.json。其升级 `37373939603` 终态failure，实际annotations确认未取得hosted runner，steps空，非测试失败，不计通过；有新版既有升级排队任务，不重跑旧任务。

5b48dcb/docs-native `37374526766` 已success并读日志，与cd39624只差文档，核心缺kind残留修复的相同代码通过完整CI；原精确cd/native `37374440672` 和升级 `37374440846` 最后仍queued。d89d6cb/native `37374877456` 与daee29b/docs-native `37375071130` 最后in_progress。下一轮只查这些已存在任务并读终态证据。合成READY与保守加载不代表真实协议/硬件验收；用户无设备，不安装/控制，整体goal active。

2026-10-06 当前任务：萃取恢复确认时钟修复源码 `d89d6cb7b59d490fe1aaf0c3aaef611c395fba08` 已推送。ShotRecoveryGate原缺非负域检查，负数/溢出差值可放行；只增加at>=0，归属/未决萃取优先级和1500ms边界不变。新增3项测试，原6项2RED→修复6GREEN；全部device-session与219任务完整双变体构建/Lint通过，独立复审无阻塞。证据 `docs/evidence/shot-recovery-clock-2026-10-06.json`。native `37374877456` 最后queued；未宣称云端或硬件验收。

本轮查明旧aaa95ee/Mock `37373087119` 与f4c95d6/docs-native `37373207172` 终态failure：job respectively `111974839825`/`111975234093` 为cancelled、steps空、无产物；实际check-run annotations均为“The job was not acquired by Runner of type hosted even after multiple attempts”。属于未取得运行器，不是测试断言结果；不计通过，也不重启旧任务，因为新版4cec0b3/Mock `37373939613` 已在实际模拟器步骤in_progress。4cec/native `37373939684` in_progress，升级 `37373939603` queued；cd39624/native `37374440672`/升级 `37374440846` queued。5b48dcb/docs-native `37374526766` 与3460448/docs-native `37374030409` in_progress。下一轮查询既有任务并读最终产物，不重复dispatch；若新的源码任务终态失败，先读其日志/annotations再决定动作。用户无设备，不安装/控制；整体goal仍active未完成。

2026-10-06 当前任务：不完整恢复记录修复源码 `cd396242553c0faa65d1fb30a4447d3f31d3d832` 已推送。MachineWriteRecoveryState原来丢弃null kind/残留地址；现仅双null为空，其余缺kind输入保守转UNKNOWN，合法身份保留、非法/空身份置null，不写回存储、不发命令、不新增UNKNOWN解锁。4参数组旧实现3RED、修复4GREEN，全部device-session测试与219任务完整本地构建通过（39823协议检查/32856通知回放/92会话），独立复审无阻塞。证据 `docs/evidence/orphan-machine-recovery-2026-10-06.json`。新native `37374440672`、Mock升级 `37374440846` 最后queued，尚无云端运行通过声明。

上一源码4cec0b3的Mock生命周期 `37373939613` 已进入真实模拟器验证步骤，仍in_progress；其native `37373939684`、升级 `37373939603` queued。aaa95ee升级 `37373087127` 已success，实际result/seed/verify产物读取：同源码Mock1→2数据保留通过，仍不是历史schema迁移/Alpha实机。aaa95ee/Mock `37373087119` queued。下一轮只查上述既有任务，完成后读对应产物，不重复dispatch。用户无设备，不安装/控制；整体goal active，真实协议安全与完整原版功能对标未证明。

2026-10-06 当前任务：恢复回读证据测试源码 `4cec0b32704c74c331fb9a708835c2d153fb18ac` 已推送。五类操作各增三项实际ACK负例：idle时间缺失、序号未超过恢复基线、关键回读缺失或不一致，共15项。非空时间字段刷新以隔离待测条件，复位后继续busy及存储失败/有效重试正例。仅androidTest/script变更，无生产协议改动；最终离线testAPK/Lint101任务、shell/diff及独立复审通过，运行尚未验收。

新源码现有CI：native `37373939684`、Mock生命周期 `37373939613`、Mock升级 `37373939603` 均最后确认queued。新marker必须含 `missingTimestampAcknowledgements=5 unchangedReadbackAcknowledgements=5 incompleteEvidenceAcknowledgements=5`，不能用旧产物代替。前一源码aaa95ee/native `37373087183` 已success并读日志（39823协议检查、32856通知回放、92会话、双APK各7113字符串及6转义回环、发布12案例）；其Mock `37373087119` 仍queued、升级 `37373087127` 仍in_progress。下一轮查询既有任务并读取最终产物，不重复dispatch。合成READY不是实际认证/通知或硬件验收；用户无设备，不安装/控制，整体goal仍active。

2026-10-06 当前任务：恢复ACK设备归属测试aaa95ee推送，仅androidTest/script。在原5kind fixture前增加5wrongidentity地址02（hub地址断言）及5snapshot DISCONNECTED确认；availability仍true但实际ACK必须Storage attempt0/pending/rawprefs/warning保持、精确waiting事件、fake execute0，复位READY再原busy/storage流程。testAPK/Lint101任务、shell/diff、独立复审通过，运行未验收。native37373087183确认in_progress；Mock生命周期37373087119、Mock升级37373087127 queued。新Mockmarker须wrongIdentityAcknowledgements5/disconnectedAcknowledgements5/busyStages2/blockedACK10/blockedEntries58/failedWrites5/retries5，不能用旧882marker替代。88200b0/Mock37187311005最终success实际产物读，busy10/58、存储5/5、入口13/78及原清理/通知/八语言/五音频、两profile各80页+3marker全在；对应native/升级已验，证据docs/evidence/service-recovery-busy-2026-10-06.json。去皮47617dd/native37187870905最终success实际日志读，39823/32856/92/7113/发布12通过。旧live任务已终结，不重跑。下一轮只查本轮上述3项现有任务并读最终产物；再对照功能矩阵推进尚缺的软件归属/恢复整合。合成READY不是GATT认证/真实重连，用户无设备，不安装/控制，整体goal保持active未完成。

2026-10-04 当前任务：去皮回读排序47617dd推送，4tests原2RED最终4GREEN，完整180任务本地构建/92会话（含preflight）通过，独立复审无阻塞。StandaloneTare.sample先tick再守WAITING_ZERO/newserial，非零也推进水位，absLong<=50与5秒UNKNOWN不变；written新操作重建baseline，非BLE重传识别。native37187870905确认in_progress；88200b0/Mock生命周期37187311005仍in_progress（活动10ACK/58entry待读）；其native37187311009与升级37187311012均success实际日志/产物读。fd9b569/Mock37186984311已success并读完整产物，五Storage失败/五retry/精确错误/noTransport、入口13/78及原清理/通知/八语言/五音频、两profile各80页+3marker均通过，证据docs/evidence/service-recovery-persistence-2026-10-04.json；不是busy新版证据。预热069681d/native37187536336 success实际日志读，39823/32856/92/7113/发布12通过。下一轮只查tare/native与busy/Mock两项现有任务，勿重复dispatch。下一步对照功能矩阵继续请求归属/事务恢复整合与非设备项，不能把hostserial或合成READY当全协议安全。用户无设备，不安装/控制，整体goal保持active未完成。

2026-10-04 当前任务：预热回读主机序号069681d推送，3tests原2RED最终3GREEN，180任务完整本地构建、独立复审通过。observe在WAITING_TEMP接纳新serial即推进水位，低温也消费；written重建基线，温差/READY锁存/取消token/启动实时检查不变。native37187536336确认in_progress。88200b0/升级37187311012 success且实际seed/verify/result读，同源码Mock1→2；native37187311009及Mock37187311005仍in_progress。fd9b569/Mock37186984311仍in_progress，上一入口Mock已成功。下一轮查现有上述4项任务，不重复dispatch；旧fd9 marker无busyStages，新882 marker须busyStages2/blockedACK10/blockedEntries58，勿混用来源。计划docs/superpowers/plans/2026-10-04-preheat-readback-ordering.md。后续对标回原功能矩阵与实际软件恢复/请求归属，不把hostserial修复或合成READY当全协议安全。用户无设备，不安装/控制，整体goal仍active。

2026-10-04 当前任务：活动事务恢复互斥88200b0推送，扩展真实tracker begin→writtenSuccess两阶段，5kind×2=10次ACK被挡（attempt0/state/prefs保持），4kind六entry×2+BREW_WAIT五entry×2=58入口阻挡。正常预热隐藏advisory，检查确切5preheat-block文案；不调用预热startShot（属于预热消费流程，不能因缺曲线而误当busy保护）。断链UNKNOWN后补serial2/fresh snapshot再原Storage false→retrytrue。最终101tasks testAPK/Lint、独立复审通过，执行尚待native37187311009/Mock37187311005/升级37187311012（均in_progress）。新Mockmarker需fixtures=5 busyStages=2 blockedAcknowledgements=10 blockedEntries=58 failedWrites=5 retries=5 retainedGate=true exactFailureEvent=true noTransport=true syntheticReady=true。旧fd9b569/Mock37186984311仍in_progress；native37186984318及升级37186984336 success并实际日志/产物读。入口8ab251c/Mock37186585805 success实际读取：13/78/ACKattempt13、原通知/清理/八语言/五音频、两profile各80页+3marker全部在；证据docs/evidence/service-recovery-gates-2026-10-04.json。下一轮查上述现有Mock/native/升级，不重新dispatch。合成READY/Storage模拟不能当实际BLE/队列/设备安全验收。无真机操作，整体goal active未完成。

2026-10-04 当前任务：恢复确认存储失败测试fd9b569已推送，仅androidTest/script。五kind合成READY/原机identity/new serial/fresh idle/周计划，注入实际MachineWriteRecoveryState.Storage false→UUID真实commit；失败精确clear_failed文案、attempt1/pending/rawpref/控制warning与设置阻挡不变；retry attempt2清record，第三ACK不写，双queue fake驱动任何execute都throw且计数0/inactive。强制mock=null/running=false，不connect/auth/启动组件；finally双device/scanner/handlers/TraceStore/UUID，shotfixture及生产prefraw保持。最终testAPK/Lint101任务、独立复审通过，运行尚未宣称。新native37186984318 in_progress、Mock生命周期37186984311 in_progress、Mock升级37186984336 queued（均fd9b569）。旧入口8ab251c/Mock37186585805仍in_progress，已确认步骤Verify lifecycle with targeted logs真实活动，不能restart；native37186585789及升级37186585831成功且实际日志/产物读。下一轮查这些现有任务；持久化Mock需SERVICE_RECOVERY_PERSISTENCE_CHECKS_PASSED fixtures=5 failedWrites=5 retries=5 retainedGate=true exactFailureEvent=true noTransport=true syntheticReady=true与入口13/78及原矩阵。计划docs/superpowers/plans/2026-10-04-service-recovery-persistence.md。合成READY及Storage=false不能当真实设备/OS故障/活动事务互斥证明，下一步补busy事务测试。无真机操作，整体goal未完成。

2026-10-04 当前任务：应用层持久恢复入口验证8ab251c已推送，仅androidTest/script，不改生产。ServiceRecoveryGateChecks 13fixtures=6write-kind×shotPending false/true+noPending，六entry共78次精确警告与无owner对照，ACK attempts13、UUID rawpref/原prefs保持；禁组件/权限/systemservices/mock/owner，finally Handler+TraceStore+UUID清理。最终testAPK/Lint101任务编译通过，独立复审修正marker语义，无运行通过声明。native37186585789、Mock生命周期37186585805、Mock升级37186585831确认in_progress；下一轮只查这3项并读产物，Mock必须有SERVICE_RECOVERY_GATE_CHECKS_PASSED fixtures=13 entries=78 acknowledgementAttempts=13 preservedPending=true detached=true noBle=true及原矩阵。杯数d98e1f0/native37186281329成功且日志读取，39823/32856/92/7113/发布12证据已补；恢复时间58fd8fc/native37186079149及升级37186079174均success且日志/产物读取。后续不能把当前无owner gate测试说成READY/busy队列互斥；仍需活动事务阻挡、有效ACK+commit失败保持等产品级场景，无真机操作。

2026-10-04 当前任务：杯数重置边界修复d98e1f0推送，6tests原4RED最终6GREEN，180任务完整本地构建、独立复审无阻塞；timeout/disconnect水位max并丢弃部分count，新begin独立初始化。证据docs/evidence/cup-reset-boundaries-2026-10-04.json，native37186281329确认in_progress。睡眠双段7fa8482/native37185839289 success实际日志读取，39823/32856/92/7113/发布12通过。恢复时间58fd8fc/Mock升级37186079174 success并读取seed/verify/result，局限同源码/schema Mock1→2；native37186079149最后仍in_progress。下一轮只查cup/nativeclock两项既有任务并读日志，不重复dispatch。已读MobileService设置/杯数入口：persistent arm先于write，失败clear失败仍保留记录，timeout token拒绝旧callback，尚未做新增应用级故障注入；后续对照原版功能矩阵推进请求归属/恢复互斥的整体测试，别把单个API边界当全协议安全。用户暂不给设备，无真机安装/操作。

2026-10-04 当前任务：恢复确认时间证据修复58fd8fc推送。五kind×负值/溢出/正常边界15cases原10RED，修复后15GREEN（0失败/错误/跳过）、180任务完整本地构建、独立复审通过。仅MachineWriteRecoveryState类内freshIdle拒绝无效时间，五种canClear其它条件/持久化/协议不变；当前elapsedRealtime正常，本次API异常证据防御。native37186079149及自动Mock升级37186079174已确认in_progress；双段睡眠7fa8482/native37185839289仍in_progress；下一轮只查这三项既有任务并读日志/产物，不重复dispatch。计划docs/superpowers/plans/2026-10-04-recovery-clock-evidence.md。下一安全软件项：CupResetTracker.observe已防倒退，但timeout/disconnected的markAfter直接assign仍待实证非回退及per-operation初始化边界；不能把该观察当已验证缺陷或修复。无真机操作，完整原版对标/真实设置及重量停验收仍未完成。

2026-10-04 当前任务：每周睡眠双路回读修复7fa8482已推送。原8项5RED，引用比较候选新增两方向实际RED；按片段字段/raw值比较后11项GREEN、完整180任务本地构建通过、独立复审无阻塞。云端native37185839289已确认in_progress，下一轮只查已有任务并读取最终日志，不重复dispatch。计划docs/superpowers/plans/2026-10-04-sleep-schedule-ordering.md。上一轮fd6947f/native37185499803已success且实际日志读取，39823/32856/92/7113/发布12均通过，已补证据。所有改动仅主机serial确认契约，不识别重新编号的旧BLE帧、不发送新命令；真实周计划执行/设置/自动重量停等仍待设备。下一步对照native-parity-audit继续软件协调及恢复门禁覆盖，无真机操作。

2026-10-04 当前任务：设置/立即睡眠共享回读序号修复fd6947f已推送。原6项实际RED，候选修复跨操作残留水位新增2项RED；最终8项GREEN，180任务完整本地构建通过，独立复审无阻塞。云端native37185499803最后查询in_progress，下一轮只查已有任务并读取日志；不得当已成功。证据docs/evidence/readback-ordering-2026-10-04.json，计划docs/superpowers/plans/2026-10-04-readback-ordering.md。范围仅主机serial契约，不识别重新编号的旧BLE帧。每周睡眠双路回读尚未改：需独立缓存每路已见序号和值，允许一侧未更新而另一侧更新；旧测试中相同firstSerial=13改变first值需明确契约，不能直接套用单路规则。无真机操作。

2026-10-04 当前任务：服务销毁及正常shutdown验证已完成，512ae86/Mock37183863824与8129a05/Mock37184234219均success并读取实际产物。SERVICE_OWNER含ownerThread=true；SERVICE_SHUTDOWN四fixture allowed1/shot-write-both阻止3、pending保留/localManager/noBle；Hub四组/通知故障与原8语言/5音频、compact/wideFont各80页+3marker全部保留。对应native37183863776/37184234323与同源码Mock升级37183863782/37184234245成功且日志/产物读取。清理证据docs/evidence/cleanup-boundaries-2026-10-04.json、两项计划和原版对标审计已更新；不能将局部代理stop调用当真正注册Service/Binder或硬件验收。测试包不接真实BLE，生产报文/控制门禁/重试未改变。下一步依据native-parity-audit的剩余项推进共享业务协调与尚未覆盖的软件状态，不重复已通过矩阵；真正设置/自动重量停/其它秤与高风险命令仍缺硬件/采样证据，不开放候选控制。用户无设备，不安装/操作机器，总体目标保持未完成。

2026-10-03：新增仅androidTest的MockUpgradeChecks seed/verify及模拟器专属执行器/独立CI。固定Mock/test包、同签名1→2、首次写前检查emulator serial+ro.kernel.qemu，拒绝既有Mock安装，不清数据/卸载/触实机。空偏好种子涵盖12组pref、Keystore虚构密码、完成及进行中历史、v1/v2采样/近期孤立采样、导入曲线/历史；升级后先比UID/raw hash，再读取真实仓库及脱离生命周期的Service恢复门禁，RUNNING必须变UNKNOWN，未确认标记不清除。复审捕捉并修正构造器自建mock的隔离遗漏；各工具/ADB/证书提取均限时。生产APK分发工具新增实际旧新比较，标明installation/dataPreservation仍false；14工具+2模拟器拒绝测试通过，真实同APK不递增拒绝且原manifest保留，最终双APK/双单测/双Lint/testAPK170任务通过及资源7113/6回环。独立复审无阻塞；实际覆盖安装/Keystore延续仍待新云端任务，不用编译当升级通过。仅同源码/schema1→2，不证明历史schema迁移或Alpha/真实设备。计划docs/superpowers/plans/2026-10-03-mock-upgrade.md。

2026-10-03：90aa76f发布配置完整构建37127225211与Mock37127225214均success，日志/产物已读取。native确认39,823协议检查、32,856通知回放、92会话场景、两APK各7113模板/6回环，并有RELEASE_APK_FIXTURE_CHECKS_PASSED和RELEASE_CONFIGURATION_CHECKS_PASSED cases=12。Mock compact/wideFont各80 PAGE_START+三个页面/详情/入口marker，原八语言及五音频marker完整。发布配置本身已完成本地及云端临时密钥验证；不表示已选择持久正式密钥、真实Alpha升级或硬件验收。产物/private/tmp/hoyi-release-mock-37127225214/mock-lifecycle。

2026-10-03：发布配置源提交90aa76f已推送，现有Verify native app37127225211和Verify Mock lifecycle37127225214均经gh确认in_progress。下一步查询这两项既有任务，不重复dispatch。native必须读取原协议/会话/资源检查以及新RELEASE_CONFIGURATION_CHECKS_PASSED cases=12、RELEASE_APK_FIXTURE_CHECKS_PASSED；Mock需下载日志核对原语言入口/页面/详情及语言/音频标记保留。当前只有本地真实发布fixture及开发回归证据，无本次云端新发布检查通过证据。native watch输出/private/tmp/hoyi-release-native-ci-watch.log。

2026-10-03：已落实原生发布构建配置：mobile/distribution.gradle.kts独立版本/签名预检，Android DSL由mobile/build.gradle.kts接入。release显式code/name/previous递增，五外部环境变量、仓库外绝对规范路径、可打开私钥与预期证书SHA256必须通过；APK/AAB的preReleaseBuild均依赖预检。真实12场景通过（/private/tmp/hoyi-release-configuration-final.log），临时独立buildDir release APK实际签名/版本2/0.2.0通过并清理（/private/tmp/hoyi-release-apk-fixture.log）；最终开发双APK/单测/Lint及Mock testAPK170任务通过，实际Alpha/Mock均默认1/0.1.0且原本机证书保持、资源各7113模板/6回环通过。12分发工具测试与19资源测试通过，独立两轮复审无阻塞。本轮未选择/变更持久正式密钥或安装，不修改协议/控制/状态恢复源码；正式密钥身份、已装证书与版本、真实覆盖更新数据保留仍待确认。CI已接入发布配置及实际临时APK测试，本次新云端结果待推送后核对。

2026-10-03：f05d6e5完整回归37125979566 success，已读取日志确认12项分发校验Python、39,823协议检查/32,856通知回放、92会话场景、构建及APK资源各7113模板/6回环通过。该任务不含本次新的release配置，不能当作发布预检云端证据。

2026-10-03：已新增scripts/apk_distribution.py及12项独立Python测试，从实际APK验签、读取限定包名/版本、匹配指定证书并导出公开manifest；默认不覆盖，显式--overwrite才替换。实际本机Alpha/Mock通过，错误证书和默认覆盖均拒绝且原清单不变。声明源码提交明确标为sourceCommitVerified=false。复审无阻塞；没有签包/换密钥/安装/卸载。公开本机debug指纹基线见docs/evidence/local-debug-signing-baseline.json；正式密钥、版本递增、已安装证书比对及升级数据保留仍未实现/验收，详见docs/plans/apk-distribution.md。CI新增12项检查，本次脚本提交的云端完整回归待推送后记录。

2026-10-03：语言入口0b94f39已完成云端实际运行。Verify native app37125094623、Verify Mock lifecycle37125094578及Capture Mock UI37125094627均success；已读取Mock产物compact/wideFont各80 PAGE_START和三项PAGE_LAYOUT/DETAIL_DIALOG/SELECTOR_UI标记，原八语言及五音频标记完整。实际选择/当前项/保存失败/切换还原/同Service检查通过。完整构建日志确认39,823协议检查、32,856通知回放、92会话场景，以及两APK各7113模板与6转义回环。截图任务成功未逐图新视觉核对；不宣称母语、真实通知生命周期、BLE或硬件完整验收。产物/private/tmp/hoyi-language-selector-37125094578/mock-lifecycle。

2026-10-03：语言入口源提交0b94f39已推送，现有云端Verify native app37125094623、Verify Mock lifecycle37125094578、Capture Mock UI37125094627运行中。Mock watch本机session24470，输出/private/tmp/hoyi-language-selector-ci-watch.log。下一步查询这些既有任务，不重复dispatch；成功后下载37125094578产物，核对compact/wideFont各80 PAGE_START以及新增LANGUAGE_SELECTOR_UI_CHECKS_PASSED和原DETAIL_DIALOG/PAGE/八语言/五音频标记。失败则定向修正。当前入口只有本地验证，无本次云端实际运行通过证据。

2026-10-03：新增设置页原生应用语言入口，八语言自称、独立偏好提交、当前项免重建、失败保持旧语言/选中项及重试提示。成功后刷新通知显示并重建设置页；异步Service绑定后补刷新，不重启设备会话。Mock入口测试覆盖切换/还原、当前项、保存失败和同Service。最终双APK/双单测/双Lint及测试APK构建成功（170 tasks），19项Python、890键目录、889键生成一致性、两APK各7113模板和6转义回环通过，独立复审无阻塞。实际入口运行仍待新云端CI；本轮不安装/操作真机。计划docs/superpowers/plans/2026-10-03-language-selector.md。

2026-10-03：d69de55完整构建37118581368与隔离Mock37118581341均success，产物已读取。compact/wideFont各80 PAGE_START及PAGE_LAYOUT/DETAIL_DIALOG标记完整，原八语言/五音频标记全部保留。详情、三种合成告警和确认取消已在该矩阵通过；不代表全部弹窗视觉、真实BLE或硬件验收。产物/private/tmp/hoyi-dialog-native-37118581341/mock-lifecycle。

2026-10-03：测试修正版d69de55已推送。新Mock任务37118581341经gh确认in_progress，watch exec session19514仍保存到/private/tmp/hoyi-language-dialog-native-ci-watch.log；新完整构建任务37118581368。下一步先查询这两个既有任务，不重启或重复dispatch；完成后下载37118581341产物，核对compact/wideFont各80 PAGE_START、页面marker、新DETAIL_DIALOG marker和原八语言/五音频marker，再按失败修正。当前没有复验成功证据。

2026-10-03：首轮8987993完整构建37117962991通过，Mock37117962959失败。compact中文两主题五页及新增检查先完成，英语浅色Extraction报missing positive action；未证明产品文字缺失。SDK34 TextView.getTextForAccessibility返回mTransformed而非mText，原断言按资源原文比较存在原生按钮AllCaps不匹配。最小测试修正：同Activity原生AlertDialog无回调probe只create不show/attach，使用实际transformationMethod得到精确显示预期；真实窗口通过button1/2 ID核对文字/可见/启用，仅取消。不用忽略大小写放宽断言，不改生产UI。Mock testAPK/单测/Lint通过（/private/tmp/hoyi-language-dialog-transformation.log，107 tasks），独立静态复审无问题，云端验证待补。旧失败产物/private/tmp/hoyi-language-details-37117962959。

2026-10-03：新增LanguageDetailChecks接入原真实页面矩阵：最长格式化工厂详情/采集详情/不可启动fixture，三种合成manualSafetyResource提示原Home render，原Extraction confirmStart后仅UiAutomation匹配Mock包/标题/参数点击取消。全部临时字段、曲线prefs和辅助功能flags独立恢复；不点正向/曲线使用/槽位按钮，双重Mock且hub必须null。最终Mock测试APK/单测/Lint与独立静态审查通过，运行待云端。详见docs/superpowers/plans/2026-10-03-language-detail-dialog.md；不代表100条全曲线、全部弹窗视觉或硬件，语言入口仍关闭。

2026-10-02：d64f620云端完整构建36972449670及Mock运行36972449671均success（Mock 9m3s，没有触发超时）。已读取两份真实页面日志：compact与wideFont各80个PAGE_START和对应最终通过标记，共160个八语言/双主题/五页面初始配置；实际资源断言360dp/1.0、1280dp/1.3均通过。五个设置分组文字与逐步纵向滚动、导航全可见/48dp、根内容和固定启动行不侵入导航、同服务/非语言偏好保留均通过；全页非输入文字检查包含实际view宽度溢出负例，明确省略的列表摘要保留。原八条语言标记和五条音频/提示回归也全部存在。此轮没有生产UI失败，不修改生产UI/协议；未操作真机。产物/private/tmp/hoyi-language-pages-36972449671。仍缺详情、动态告警/未知结果/确认弹窗/完整列表数据与图轴文字、母语质量、注册Service/BLE/硬件验收；不能把初始页面矩阵等同全功能。

2026-10-02：新建LanguagePageChecks及pageChecks分支，计划在真实360dp/1.0与1280dp/1.3窗口分别运行八语言双主题五页（合160），包括设置五组和纵向滚动；仅Mock、不点击机器控制。导航/固定启动可见、文字真实宽度/高度和同服务/非语言偏好断言已编码。独立审查指出设置切组VSYNC竞态已修；审查配额中断，不声称完整复审通过。最终Mock测试APK/单测/Lint成功，脚本固定四mode失败/超时输出核对通过，实际页面运行待云端。语言入口仍关闭，未改生产UI/协议。

2026-10-02：3c924d7云端完整构建36953997155与隔离Mock运行36953997198均success。已读取产物：六阶段START/PASS、八条语言通过标记及五条原音频/提示回归标记完整。800个共享导航fixture（八语言×双主题×320/360/600/700/1000dp×1.0/1.3字体×五选中态）、标题/按钮文字检查通过；真实导航横向分支与字号增长已运行确认，原240紧凑子矩阵/裁切负例/八语言活动杯停止检查保留并通过。此轮未发现需修改的生产UI，不改协议或业务，不操作真机。仍待五页完整内容、详情/错误/滚动/图轴文字与母语质量；语言入口继续关闭，真实Service/BLE/硬件证据独立。产物/private/tmp/hoyi-wide-font-36953997198。

2026-10-02：共享UI布局检查扩展到320/360/600/700/1000dp×1.0/1.3字体×八语言×双主题×五选中态，共800导航fixture；检查真实横向分支与TextView字号增长，原文字完整性/RTL/48dp/负例保留，新增CI必需标记。只改androidTest/脚本/文档；本地Mock测试APK、单测、Lint及只读审查通过，云端运行待验证。不安装或操作真机。

2026-10-02：最新源代码dfc4c55已推送，完整构建36952198505、Mock运行36952198311及截图生成36952198275均success。已读取语言七项和音频五项通过标记。紧凑共享UI240组合+八语言实际停止文字检查通过，日语导航38px/36px裁切修正得到运行证据；独立未注册/无Hub Service原方法的资格返回、相同warning去重、强制两条平台postTime更新及Context失败捕获/清理也通过。真实产品Service没有改动；只在隔离Mock中测量和发通知。截图已下载未逐图新视觉核验。仍缺五页完整长文字/宽屏/字体放大/母语、语言设置入口、注册Service生命周期/notify-cancel异常/权限撤销及真实BLE/硬件验收。功能对标表已更新；不得标完整目标完成。产物/private/tmp/hoyi-ui-service-notifications-36952198311，方案docs/superpowers/plans/2026-10-02-language-layout.md及2026-10-02-service-notification-refresh.md。

2026-10-02：新增仅androidTest的LanguageServiceNotificationChecks：独立未注册/无生命周期/无Hub的Service对象，受限Mock上下文，测试资格返回、8语言真实通知、同警告常规去重/强制postTime更新与Context查询失败，snapshot/真实owner/恢复偏好不变。复审修正强制刷新假阳性；最新Mock测试APK/单测/Lint成功，两轮静态复审无残留问题，ART/平台运行待CI。生产Service未改。CI现会因HoyiUi/通知factory/Service修改自动触发。上一导航fix8377352首次push网络SSL失败，随本次提交一起重试推送；不得视为已运行通过。

2026-10-02：第二轮布局CI36950911811捕捉真实日语导航裁切（320dpプロファイル，Layout38px/可用36px）。已将HoyiUi导航bar最低62dp且wrap、item最低50dp且wrap，保留点击/色彩/底部位置/主体权重及协议逻辑；双构建/双单测/Lint/Mock测试APK通过，静态审查无问题，修复后云端待验证。另有未提交的独立Service通知检查准备中，不修改生产Service。见docs/superpowers/plans/2026-10-02-language-layout.md。

2026-10-02：首组件布局CI36950093899失败于测试环境假设（实际Home宽屏），不是产品裁切结论。修正测试用Home派生的明确320/360/600dp ConfigurationContext和未启动LayoutHost给生产HoyiUi测量，显式MobileTheme/getTheme委托；不改实际Home或App配置，不削弱文字/48dp/父界限/RTL/负例断言。修正版本地Mock测试APK/单测/Lint成功，复审/第二次云端待补。

2026-10-02：新增多语言组件布局检查，未改生产代码。Context现有16组合分别测生产紧凑导航320/360/600dp×五selected态、标题/副标题/危险按钮；ActiveCup八语言对现存固定停止按钮补Layout完整性断言。导航非空/资源匹配、无ellipsis、行范围/高度、父bar边界及48dp目标，含两Android裁切负例；审查修正离屏body默认LTR并复核。最新Mock testAPK/单测/Lint成功，云端结果待补；不计作五页真实内容/宽屏导航/字体放大/母语/硬件验收，语言入口继续关闭。见docs/superpowers/plans/2026-10-02-language-layout.md。

2026-10-02：b20b90f云端完整构建36948773115与Mock36948773151均success，已核对下载产物。语言五阶段全部PASS：Home 16组合、活动杯8语言、三图表48组合科学顺序、通知factory八语言、Android平台通知八语言同key更新及取消清理；原音频/产品提示/焦点/退出五项回归也通过。首页复用修正实际解除本次挂起。语言入口仍未开放：长文字/各页面布局、母语与真实Service通知刷新门禁仍待验收；真实硬件边界不变。最新8458c34另加600秒失败保护，已离线执行模拟成功/非零/超时/非法模式及stdout保留检查；云端完整构建36949018031与Mock36949018081均success，已读取后者产物并再次确认五条语言与五条音频/提示回归标记。详见docs/plans/native-language-parity.md。

2026-10-02：当前处理中：Mock语言验证重复startActivitySync(首页NEW_TASK)存在复用任务后无限等待；Android SDK源码确认等待仅由新onCreate完成。测试改传递既有首页引用，新增五阶段日志，生产代码未改。图表/通知平台检查云端尚未通过；本地编译/复审与新CI结果随后补充。无需真机、未执行设备命令。

# Native BLE / Lab handoff

2026-10-02：追加Mock实际平台通知发布/更新/取消验证：独立tag与91001/91002，不覆盖真实通知ID，不发送PendingIntent；Mock单独授予通知权限，8语言要求同key替换与译后内容更新，安全通知取消保留连接通知，清理各项独立尝试并保留错误。终版编译/Mock单测/Lint及两轮静态审查通过，运行时待云端。图表48组合改批量读像素减少JNI，覆盖不减。66519a0云端完整Verify36947312216成功；其Mock36947312213与此前a161de9 Mock36946687835仍在运行，继续观察同任务，不凭耗时重启。实际Service刷新/去重/权限错误与真实机器安全未验，未改生产代码。

2026-10-02：纯通知显示构造已从Service移到MobileNotificationDisplay，保留Service动态Context、原通道ID/Intent/请求码/flags与安全显示模式。Service整文件按a161de9的明确三组替换逐字重放一致；停止/BLE/恢复/资格/通知去重/失败与时序不改。Android测试先缺API编译失败，迁移后完整回归/三APK/Mock测试APK/双Lint成功，177项/变体（176过、1缺真实导出跳过），19Python与双APK7081资源通过，独立静态复核无实质问题。安全factory新增8语言×两目的地×两displayOnly构造断言，仅编译待云端；实际发布/刷新/权限仍未验收。a161de9云端完整Verify36946687850成功，Mock36946687835尚在模拟器步骤，未重启。计划见docs/plans/notification-display-extraction.md。

2026-10-02：eff6662云端完整Verify36946052287和Mock36946052284成功，已读language日志两标记，16组合Context/Home检查保持通过，8语言活动杯重建保留杯ID/采样/消息与译后真实UI停止按钮同步结束通过。新增加三图表×8语言×深浅的实际Canvas像素顺序检查、8语言通知factory/channels文字与ID/Mock PendingIntent来源检查；不发布通知、不发送意图，结束还原语言和通道。最终Mock测试APK/单测/Lint及静态复审通过，新运行结果待补。通知factory不等于真实刷新，图表顺序不等于文本裁切，语言入口/实际BLE/完整安全仍未验。所有增量仅测试/文档，生产协议无变更。

2026-10-02：c00e038云端Verify36945429783与Mock36945429772成功，已读language-instrumentation.txt标记，八语言×深浅主题16组合实际资源/可见Home导航/RTL方向/重建同Service、消息及非语言偏好保留通过。新增仅测试的活动杯切换：8语言各一Mock杯，watchShot已RUNNING后切换并重建Extraction，保留杯ID/首点与消息，点击译后固定停止按钮同步证明结束，再等观察收尾；最终编译/Mock单测/Lint和两轮静态复审通过，云端待验。未改生产模块，没有语言选择入口；真实通知/长字/图轴/实际BLE与机器安全仍未验。

2026-10-02：继续语言运行时前置验证：新增只在Mock执行的LanguageContextChecks，八语言×深浅主题重建Home，核对可见文字、三类Context语言一致、RTL方向、同Service/同事件与非语言偏好保留；没有生产选择入口，没有额外测试绑定或控制调用。Mock测试APK、Mock单测及Lint本地成功，两轮独立复核无残余实质问题，云端结果待补。实际可见矩形与完整语言/主题偏好map恢复已补，最终构建成功。真实通知/长文字/科学图轴/实际BLE与萃取中切换仍未验。9698395云端完整Verify36944684387及Mock停播/焦点36944684410均success；已读取artifact五LOCAL标记，真实播放活跃后的off/切页/Extraction退出在1秒内静止、瞬时焦点INTERRUPTED且不串播通过。无真实听感/后台/锁屏/永久焦点丢失结论。

2026-10-02：06f5199云端Verify native app36943948835与Verify Mock lifecycle36943948892成功，已读取日志artifact三项LOCAL标记，确认实际设置开关持久化、Home结束展示、配置重建不增加播放请求及新杯关闭，原音频14资产和Service检查保持通过。新增仅Mock测试的焦点竞争、试听off/离开、Extraction新杯展示/退出与跨页不补弹已开发；两轮审查促使先等真实音频活跃再操作，并将取消后静止期限收紧1秒，避免prepare前取消或自然播完冒充停播。最终编译与云端结果另补；该1秒调度容差未实测。未改生产协议或使用真实设备；整体功能与硬件安全仍未验收完成。

2026-10-02：本地萃取提示第3阶段已接入默认关闭偏好卡片/试听和Home、Extraction结束弹窗。关闭订阅停止本地声音、同杯至多一次领取、旋转只恢复文字不重播、新杯清空；异常不阻断原页面渲染。新增10项，双变体177项（176通过、1缺真实导出跳过）；完整协议/会话/共享回归、三APK/Mock测试APK及双Lint通过，19Python与两APK各7081资源一致。Mock设置持久化/结束弹窗/重建不重播/新杯关闭自动检查已编译，云端运行结果待补。BLE及协议模块无修改，机器控制许可不经提示类。独立最终静态审查无实质问题；焦点/后台/真实机器验收未完成。详见docs/plans/local-brew-feedback.md。

2026-10-01：e031ab9云端完整Verify36870318401和Mock运行时36870318230均成功。已读日志artifact，14音频检查仍通过；新增真实Mock Service产品start/stop全链确认自然结束摘要、遥测phase8、下一杯清空、早停抑制。观察连接EOF后查询同一运行任务，没有重跑验证。主题重建1→2，后台配置destroy仍未发生，不能算该路径验收。下一步是默认关闭本地偏好/试听和Home/Extraction一次性结束展示；仍无自动播放/弹窗，没有真实机器验收，整体goal保持未完成。第3阶段接入约束已写docs/plans/local-brew-feedback.md。

2026-10-01：本地萃取提示第2阶段只读记录已接入。BrewFeedbackTracker绑定同杯/阶段/预浸，完成gate抑制未知、告警与安全停机；Service自发/Mock/phase6被动路径接入，其它外部曲线未知预浸不猜测。旧三杯样本证明临时slot7→遥测phase8，提示和Mock对齐，协议/结束检测/恢复/门禁未改。新增16项，双变体167项（166通过、1缺用户导出跳过）；完整离线回归、三APK/Mock testAPK及双Lint通过，19Python/两APK6969资源通过。实际旧源码1893等级+1024phase对照一致且二次生成一致；全Service声明正向重放一致，多轮只读审查无残余实质问题。Mock产品Service全链测试已编译、云端待运行；UI/偏好/提示音订阅仍未接入，不宣称完整功能或真机安全已验收。见docs/plans/local-brew-feedback.md。

2026-10-01：0942650云端完整Verify native app36865762452及Mock生命周期/音频36865762552成功。已读取日志artifact，14音频实际解码/完成、prepare取消和两段串播通过；主题重建计数1→2，生命周期通过。后台配置destroy仍false，焦点竞争/背景播放/真机音频未验证。只有Mock instrumentation调用播放器，Alpha/Mock产品界面均尚无提示播放入口；下一步实现同杯0x80记录/判定，再接偏好/结束展示，不能把本阶段当完整功能。

2026-10-01：本地萃取音频第1阶段。新增与BLE独立的两段播放器/Android MediaPlayer与焦点适配，复用14原版音频并登记SHA。身份与段序号屏蔽旧/重复回调，取消/焦点中断不继续语音；自然失败可进下一段。新增6项，Alpha/Mock各151项（150通过、1真实导出缺失跳过）；完整协议/会话/共享回归、三APK、Mock instrumentation编译、双Lint及19Python/两APK6969资源通过；两APK14资产未压缩且SHA吻合。独立审查无实质问题。已新增Mock-only云端实际解码/取消/串播检查，运行结果待补，不能以编译代替运行时。无生产调用方/偏好/提示判定/结束弹窗，不自动播放，不开放0x21，完整本地提示仍待第2–4阶段。见docs/plans/local-brew-feedback.md。

2026-10-01：加强版云端复跑结束。ad6488f的Verify Mock lifecycle36862089510与完整Verify native app36862089698均success；已读取日志artifact，theme-recreation.json确认Switch后Home配置stop从0增至1，result通过。后台Home配置destroy仍false，不把该路径标作验收。78e3140的原Capture Mock UI36860976197也success；没有逐张视觉审查或咖啡机操作。仅本次文档更新不改变已验证代码/脚本。新云端工作流可供后续Android验证，无需用户平板；重建中断/锁屏/多窗口与真实BLE窗口/完整安全验收仍待补齐，不能标记整体goal完成。见docs/plans/mock-lifecycle-cloud-check.md。

2026-10-01：核对云端真实结果。976b227的Capture Mock UI任务36859468417成功，但Verify与前38818f8都因app_name MissingTranslation失败；本地旧全回归未含Lint。修复两变体品牌translatable=false、source.json仅更新默认XML字节哈希，所有文字值逐项相同，不抑制Lint。最初offline缺lint-gradle，补依赖后两变体0错误11警告。78e3140已推送，完整云端Verify36860976218与新Verify Mock lifecycle36860976201成功；后者不截图，只装Mock、定向OpenHoyiLifecycle日志，五主页面与主题/真实Home前后台符合预期。artifact明确后台尺寸往返没有观察到Home配置destroy，不能算后台重建验收。初始额外重建marker促使进一步加强主题检查为Switch前后计数递增（当前增量结果另查）。本地全回归/三构建及最终双变体145项（144通过、1缺真实导出跳过）/Lint通过，19Python/6AAPT转义/两APK6969值相同。应用日志只在Mock启用，无命令或状态变更。原版鼓励声音进一步核对：等级用0x80机器累计流量而非秤重，自然结束清理不发0x21的255，显式stop才发；同源SHA位置与公式登记docs/evidence/legacy-brew-preferences.json。未开发音频/灯效，无0x21发送入口。见docs/plans/mock-lifecycle-cloud-check.md；功能清单同步语言已编码与未验边界，完整设备安全仍未完成。

2026-10-01：全应用可见性升级。旧三个绑定页报告会遗漏曲线/历史页，慢配置重建超过500ms可产生假后台/前台并重开600s窗口；旧事件序列先真实AssertionError。新增纯AppVisibility与Application ActivityLifecycleCallbacks，逻辑owner通过Bundle移交，曾started配置停止保留、替代started接回，真实stop/destroy释放；后台/未started不能制造可见性。回调存活Activity映射destroy移除；Service唯一owner订阅即时状态、启动Hub读相同模型、销毁取消，移除三页手工报告，普通导航500ms宽限保持。6新模型测试先缺API失败再通过，旧模型/1测试删除；全Service/三页声明变换重放一致。未改Hub/600s策略/协议/队列/恢复。完整协议39823检查/32856回放、会话92、共享69，Alpha/Mock各145项（144通过、1缺真实导出跳过），三APK成功；19Python/生成/6AAPT转义及两APK各6969资源一致，独立静态审查无实质问题。Android34 SDK源码核对重建标志/save/destroy/launch顺序，但没有运行时；重建被中断的保留owner依赖后续真实stop/destroy，Home/锁屏/多窗口仍待验，清单已加alpha-acceptance。未操作真机，语言入口关闭，整体安全未完成；见docs/plans/application-visibility.md。

2026-10-01：预热取消提示资源迁移。BrewWaitCancelGate.blockMessage映射共享规则，旧String包装保持；Mock cancelPreheatMessage保留同内存温度推进/清除目标。Service取消私有接口返回ResourceMessage，公开String接口兼容，超时保存嵌套原因并保留原null字符串；操作与出队分别实时重查，不缓存许可，不变clock/600000ms/恢复/未知处理/setBrewWait(0)。全Service声明变换重放一致，显示解析次数可变化。两新增永久测试先缺API失败再通过，3632494实际旧源码独立八语言对照25920组取消检查+80Mock场景一致，临时实现已删除。完整协议39823检查/32856回放、会话92、共享69，Alpha/Mock各140项（139通过、1缺真实导出跳过），三APK构建成功；19Python、生成一致性、6AAPT转义fixture、两APK各6969资源值匹配；独立静态审查无实质问题。没有真机/Android生命周期验收，语言入口关闭，整体功能和安全未完成；见docs/plans/preheat-cancel-resource-messages.md。

2026-10-01：启动拒绝与告警保留嵌套ResourceMessage，旧String API兼容。共享启动/告警资格、优先级、1500ms边界、C16与未知bit15规则不变；空译文仍阻止。Service仅startShot保存/显示方式改动，整文件两项声明替换核对一致，时钟/验证/门控只执行原次数，纯显示资源解析次数允许变化。新增两永久测试先缺API失败再通过；989f75f独立实际旧源码八语言对照524288告警组+256608启动组一致，临时实现已删除。完整协议39823检查/32856回放、会话92、共享69，Alpha/Mock各138项（137通过、1缺真实导出跳过），三APK构建通过；19Python、生成一致性、6AAPT转义fixture、两APK各6969编译资源匹配。独立静态审查无实质问题。未连接真机，预热取消等嵌套提示和Android/完整安全验收仍待完成，语言入口关闭；见docs/plans/start-rejection-resource-messages.md。

2026-10-01：设置变更内部提示改为不可变资源树。sealed ResourceText当前final ResourceMessage/ResourceSequence，子资源每次按同一resolver先解析，Sequence复制数组成员保持拼接顺序；未知对象/数值子类冻结原规则保持，不持有Context/回调/设置对象。SnapshotMessage接受两类根，initialText保持原日志。MachineSettingsPresentation.changeMessage覆盖12设置类型，旧String API仅解析；其余读回显示函数逐字不变。Service整文件仅两处显示参数替换，实际change/门禁/出队/事件与日志不改。nested测试先真实ComparisonFailure，再序列及17中文预期通过；a9385a8旧实际函数临时对照1098有效设置×8语言=8784组通过后删除旧实现/对照测试。最终协议39,823检查/32,856回放、会话92、共享69，Alpha/Mock各136项（135通过、1缺真实导出跳过），三构建成功；19Python/生成一致性及6AAPT转义fixture、两APK各6969值逐字核验通过，独立静态审查无实质问题。本机emulator/AVD缺失（仅有系统镜像），未跑Android重建/RTL/Context试验或操作真机。其它启动/预热阻止嵌套文字和完整安全验收仍未完成，入口关闭；见docs/plans/nested-setting-messages.md。

2026-10-01：七语言正式XML生成并打包，各871键/总6,097条，排除共享app_name，保留Alpha/Mock名称。android_resources.py完整预校验再生成，确定排序/Android转义+外层引号；--check只读且相同文件不重写，IO失败不保证七文件原子性。原LanguageDraft禁止资源阶段断言实际失败后改为完整871键/无品牌覆盖，Gradle Test登记翻译XML输入。verify_apk_resources.py逐字比较编译默认+七语言值，含所有配置/品牌；读取器曾漏掉AAPT无缩进空行，新增失败测试后修复，默认和译文未改。CLI每次真实编译/链接6复杂转义fixture并回读；Alpha/Mock各6,969值匹配。19Python测试（原10+转换5+APK4）、七语言目录及生成一致性通过；最终协议39,823检查/32,856回放、会话92、共享69，Alpha/Mock各133项（132通过、1缺真实导出跳过）和三个构建任务成功。登记Test输入后最终APK任务UP-TO-DATE，实际APK再次逐字核验通过。CI新增只读生成和构建后APK核验；两轮独立静态审查无实质问题，不声称本次云端结果。没有改生产Kotlin/默认XML/catalog/协议模块，无选择入口/真机操作。Context/重建/RTL/长文字/母语与完整硬件安全验收仍未完成；见docs/plans/android-language-resources.md。

2026-10-01：统一语言资源基础接入。AppLanguagePreference独立app_language/tag，读错默认简中、select同步且成功commit才发布volatile进程值；失败/异常不重读可能已变更的SharedPrefs缓存，重复语言不写。4纯测试覆盖规范tag/非法值/失败与异常/重试/读取异常/提交期间不提前发布，先缺API失败再通过。AppLanguageContext复制配置设Locale/layoutdir及可选原night；provider以原始base和完整desiredConfig缓存，避免Application资源递归，不设全局Locale。Application原始base初始化偏好/provider并override资源；Service只读取Application动态资源，无重启/重放；ThemedActivity统一语言/主题并onResume比较Locale。没有选择UI/select调用方、七语言草稿未打包；通知显示入口仍无自动调用。整份Service/Application按声明插入核对一致，原控制/导出/历史/日志逻辑未改，独立静态审查无实质问题。完整协议39,823检查/32,856回放、会话92、共享69及三构建通过；追加测试后两变体各133项（132通过、1缺真实导出跳过）。见docs/plans/unified-language-context.md。纯测试不执行Android Context/Configuration；缓存失效/重建循环与500ms窗口/通知/RTL仍需运行时证据，未连接真机，完整功能与硬件安全验收未完成。

2026-10-01：新增SafetyNotificationPresentation纯显示投影，保留萃取/手动/机器写入警告优先级与首页/萃取目标。Service提取machineWriteSafetyResource及通道创建；新增尚无调用方的refreshNotificationDisplay，供语言Context更新后只强制刷新同ID通知/通道，warning相同或null也更新。强制失败只Log.w，不重放event/命令或改snapshot；审查发现cancel异常外泄，已外层catch覆盖manager/解析/cancel；channel失败后继续刷新。安全通知displayOnly时OnlyAlertOnce，常规false=旧默认；正常缓存先存后发且同文字失败不重试的旧策略保留。两新JVM测试先缺API无法编译再通过，验证状态/警告组合优先级/目标及null/空译文；不证明Android异常或重响。控制前缀除getter/channel提取外逐字一致，connectionNotification/STOP及恢复确认之后全源码逐字不变；两轮独立静态审查无新增问题。最终协议39,823检查/32,856回放、会话92场景、共享69项、Alpha/Mock各129项（128通过、1缺真实导出跳过），三构建成功。见docs/plans/notification-display-refresh.md。未连接真机、没有语言Context/偏好/入口，显示生命周期/Android通知运行时和完整硬件安全验收仍未完成。

2026-10-01：恢复显示继续保留资源身份。ShotRecoveryClearGate新增resource并保留block显示包装，原共享gate输入/调用次数/优先级不改；Service恢复等待event保存ResourceMessage。manualSafetyMessage改为私有ID及公开只读文字getter，六处赋值/两处清除保留原条件；七处服务内部存在判断改为ID，空译文不解除警告。notification文案仍getter，刷新/目标选择其余不变。本轮新增资源身份矩阵和noDisplay/空译文两项测试，保留原明确中文及时效断言；矩阵两接口共享实现，不独立冒称等价证明。测试先缺resource无法编译，一次全回归因新测试漏传args失败，修正后完整离线协议39,823检查/32,856回放、会话92场景、共享69项及三构建成功，追加空译文测试后两变体重跑各127项（126通过、1缺真实导出跳过）。Service从eb180ae18项声明替换正向核对整文件一致；两轮独立静态审查无新增实质问题。见docs/plans/recovery-message-identity.md。尚无统一语言Context/偏好/通知强制刷新/选择入口；其它gate返回文字仍待迁移，没有真机操作，完整功能与硬件安全验收未完成。

2026-10-01：持续消息改用ResourceMessage/SnapshotMessage，保留资源ID/冻结参数及产生时initialText。Service140处直接资源event、3处条件资源event、扫描/Mock直存提示迁移；Home解析显示，日志仍保留原文字/事件kind/ownerId，无重放日志或命令。独立审查发现3处提前getString遗漏及BigInteger/BigDecimal可变子类漏洞；前者已修正，后者先用ComparisonFailure复现后改为仅精确class保留，子类冻结文本。shotRecoveryClearBlock与其它gate返回String等明确仍待迁移，不声称完整语言刷新。整份Service按140处+8项声明修改从f0023ff正向重放一致，Home/Mock分别仅一项显示变化；命令/门禁/定时器未改。新增7项消息测试含872默认模板格式对照；最终离线协议39,823检查/32,856回放、会话92场景、共享JUnit69项；Alpha/Mock各125项（124通过、1缺真实导出跳过）、三APK构建成功，Python10项及七语言完整目录校验通过。见docs/plans/snapshot-resource-messages.md。尚未接入语言Context/偏好/通知刷新/选择入口，没有真机操作，硬件与完整功能验收仍未完成。

2026-10-01：并行完成七语言完整资源目录草稿localization/catalog/{en,ru,th,ar,ja,ko,es}.json，各872键、共6,104条；source.json保存默认XML SHA及解引号/换行后中文模板，既有七语言57键共399条分组草稿原值全部保留。日语非已有键180处技术术语/睡眠及拨杆告警润色；尾水预警允许剩余水不改成无告警/安全确认。新增只读validate_catalog.py与10项Python测试（缺API、错误源类型和遗漏ALM/LRM/RLM方向字符均先复现再修正），检查源哈希/内容、七语言完整键、重复JSON键、格式索引/类型/补零宽度/次数、空值/换行/方向控制字符；全部通过。新增LanguageCatalogTest先因缺ru.json失败，之后每语言6,104模板按对应Java Locale实际格式化；参数标记及单数字7的补零/本地数字输出已验证，不证明语义/排版。实际复现Gradle未登记外部JSON时：将en/ui_home临时置空后仍UP-TO-DATE；mobile Test登记catalog/drafts JSON与default XML输入后同样空值正确失败，再逐字节恢复，最终Alpha/Mock各118项（117通过、1缺真实导出跳过）、两构建任务成功且APK相关任务UP-TO-DATE。CLI最终7语言complete=true/errors=[]；独立审查校验器及前一协议发送保护未发现实质问题（静态，未声称复跑协议测试）。CI新增Python检查，尚未确认这次云端结果。新增native-language-runtime-audit.md明确持续String事件、Service/Application/通知、500ms页面过渡、RTL、变体app_name与元数据接入边界。本轮没有改生产UI/服务/资源/控制协议，没有实际启用语言切换或新打包资源，未操作真机；正式转换、语义/视觉核验和安全验收仍未完成。

2026-10-01：localization/drafts/basic-controls新增en/ru/th/ar/ja/ko/es各47条、共329条草稿，覆盖导航、连接与去皮不同阶段、基本设置和应用信息；两组合计57个不同键/399条，默认资源872条，完整翻译仍未齐。参数化版本/包名/无障碍模板保留参数位置类型与次数，未知/写入等待不翻成已执行。LanguageDraftTest新增组完整性与参数语义两项检查，首次因缺目录在键集合断言失败（red日志）；测试工具DefaultStringResources新增template读取未格式化模板，避免空参数调用String.format，不涉及生产运行时。补草稿后Alpha/Mock各117项：116通过、1项缺真实导出跳过；两APK构建任务成功且生产APK任务UP-TO-DATE，未新增打包资源。所有生产代码/资源与协议未改；未重复协议全回归、未操作真机、未启用语言切换。母语准确性、RTL/长文字布局与完整目录翻译仍待完成。

2026-10-01：实际发送路径增加OutboundWritePolicy与GuardedGattDriver（device-session纯Kotlin），DeviceSession只把queue驱动改为包装器，所有会话写入在delegate.execute前检查角色/固定写端点/已有命令格式与字段范围。BOOKOO只放行已有四初始化及去皮；咖啡机支持现有认证、启动/停止、设置、等待温度、睡眠两片/立即睡眠与杯数重置；未知/滤芯/鼓励LED/密码修改及畸形帧拒绝且不触及传输。0x11（十进制17）只接受已有温度等待格式；0x17（十进制23）的冲突恢复出厂/忽略告警命令一律拒绝。合法格式不等于安全参数/执行授权；原采集启动许可、固件、认证、代次、新鲜度、状态与持久恢复门禁保持；不修改/补算传入帧，不重试。原始AndroidGattDriver仍通用，包装器保护所有DeviceSession路径，不保护另起的裸驱动。新测试先因缺API失败再通过，最终8项覆盖合法编码和拒绝无delegate调用/接受身份不变/失败一次调用；完整离线协议39,823检查/32,856回放、会话92场景、共享JUnit69项无失败；Alpha/Mock各114通过、1缺真实导出跳过，三APK构建成功。见docs/plans/outbound-write-boundary.md；未操作真机，硬件风险保证仍未完成。

2026-10-01：补齐当前修改旧包的压力偏好/滤芯静态证据（docs/legacy-pressure-filter.md及JSON）。压力开关本身无蓝牙写入，输入上限10/12、实时图10/13，不能误称机器压力保护或所有图表10/12；编辑参数仍可能间接影响后续发送。名字syncTankPressLimitFrom83实际同步滤芯，门槛是原始字节1/2大端数值≥276、长度≥13、字节11 bit1；不推断展示固件版本或其它flags。滤芯写入1802000000/1802000100来自静态代码，旧UI先保存目标并在4500ms内屏蔽相反回读，不构成执行确认。原生编码/解析/发送和资源均未改，滤芯入口继续关闭。只核对源码定位、SHA、候选帧与文档差异，不重复Gradle；没有新真机或固件验证。

2026-10-01：核对旧版brewTips/brewTipsLed消费路径，确认不是完整纯UI：鼓励音频播放/停止可自动调用setBrewEncourageLed，以0x21发送四等级/结束帧。旧hex转换器不补校验，候选5字节2102000000/2102000100/2102000200/210200FF00由静态代码计算，非真实采样；BleWrite失败分支可能重发现后重试。新增docs/legacy-brew-preferences.md与可复查源码SHA/行号/offset证据，明确LED偏好DefVer!=2时旧版强制默认开启、弹窗的时长/旧重量时间启发式不等于质量或停止证明。UnsupportedCommandGroup新增BREW_ENCOURAGEMENT_LED仅为文档边界，没有编码/发送入口，不能冒称低层拦截防火墙；原生保持不发送且不复制自动重试。协议文件与唯一声明枚举项新增一致，未改已有命令方法。完整离线回归通过：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各114通过、1项缺真实导出跳过，三个APK构建成功。未连接真机；提示/音频产品功能未开发，灯效需固件/真实写入/结束及异常恢复证据，完整语言/其它未完成范围继续保留。

2026-10-01：localization/drafts/safety新增七语言（en/ru/th/ar/ja/ko/es）各10条、共70条安全/初始提示翻译草稿，覆盖立即停止、停止处理中、断线及三个不同未知/运行提醒、通知和初始提示。尚未进入Android资源，不会随英文等系统Locale自动启用不完整翻译；现有生产源/资源未改。两项LanguageDraftTest先因缺语言文件失败，补齐后通过，机械核对七语言/相同键集合/非空/无意外百分号与隔离；不能据此证明母语准确性、全部文案完整或实际布局。Alpha/Mock定向离线各114通过、1项缺真实导出跳过，两包构建任务成功。见localization/README.md；后续补其它资源与语义/RTL/Service一致性后才转换为正式资源。未重复协议全回归，未连接真机，完整语言和真实安全验收仍未完成。

2026-10-01：回查旧app-service.js设置页langRows，真实顺序为CN/EN/RU/TH/AR/JA/KO/ES，共八语言，不将英语视为完整范围。新增纯AppLanguage（zh-Hans/en/ru/th/ar/ja/ko/es），保存标识与显示词分离，非法/缺失保存值保持简中，legacyIndex仅记录原顺序，不自动导入旧包偏好；只有阿拉伯语标记RTL。两项测试先因缺API失败，再实现后通过，覆盖八项顺序/Locale回转/恢复与非法值。新增docs/plans/native-language-parity.md，明确八语言翻译、Activity/Application/Service统一资源、持续事件/通知刷新、萃取中重建/停止入口及RTL图表检查。当前只有未接入运行时的基础类型与计划，没有切换入口/偏好，未修改既有运行时或控制文件。Alpha/Mock定向离线各112通过、1项缺真实导出跳过，两包构建成功；未重复协议全回归，未连接真机，完整语言与真实安全验收尚未完成。

2026-10-01：MobileSnapshot不再把初始中文当成已有事件，message由固定字符串改为nullable（默认null表示尚无事件）；新增messageForDisplay，仅null读取device_initial_message资源，显式空字符串保留。HomeActivity运行中状态使用该显示方法；服务未启动提示/手动萃取优先级及状态本身保持原样。两项测试先因缺API/资源失败，实现后通过：初始显示不改连接状态和数据、已有事件/空翻译/快照copy不触发回退。Service/Home完整源文件与两项声明变换一致，初始中文资源在两包核对通过。完整离线回归协议39,823项检查/32,856条通知回放、会话92场景；Alpha/Mock各110项通过、1项缺真实导出跳过，三个APK构建成功。事件产生/日志/控制发送未改，未连接真机。原曲线/导入元数据与历史原因保留规范值，完整翻译和语言选择尚未完成；整体真实功能/安全验收未完成。

2026-10-01：新增只读CurveDetailsText，资源化采集/工厂曲线详情模板及报文校验可用/缺失/不用重量共5条资源；CurveActivity仅增加lazy显示器并替换item.details显示，原canStart仍独立决定启用和槽位资格。CurveLibrary/CurveCatalog/FactoryCurveCatalog/FactoryWireProof（含FactoryCurveAdapter）文件未改，规范详情字段仍保留用于兼容/核对，工厂tips和采集endMode作为原始内容显示，尚未翻译全部元数据。三个新测试先因缺API失败，再实现后通过：103条曲线在有/无真实仓库工厂proof时206次详情比较与原字段完全相同，显示前后resolve不变；空翻译不改资格/参数/存储详情，未知元数据保留原详情。补103条总数断言后两变体再次通过。Activity正向两项声明替换核对、五条编译资源核对通过；完整离线回归协议39,823项检查/32,856条通知回放、会话92场景，Alpha/Mock各108通过、1项缺真实导出跳过，三个APK构建成功。未连接真机；初始提示及全部元数据翻译/语言选择仍待完成，软件对照不代表机器安全验收。

2026-10-01：实际Mock运行器为MockDeviceRuntime（之前handoff的MockEngine称呼不准确），13条提示新增11条资源、复用2条；构造函数注入(Int)->String，Service只传延迟执行的getString lambda，初始化时不访问未附加Context。JVM原测试改读同一XML资源，不保留重复中文表。新增emptyTranslationsCannotPermitBlockedMockChanges先因缺注入API编译失败，再实现后通过；验证空翻译仍返回非null阻断萃取中设置/去皮、入睡后杯数重置及预热中设置，未错误修改数据。Runtime/Service完整源文件正向替换核对，13条XML/两包编译资源相同，Alpha/Mock各105测试通过、1项缺真实导出跳过，含10项Mock运行器测试，两包构建成功。合成遥测/状态转换及协议代码不变，本轮运行定向回归而未重复协议全回归；未连接真机。MobileSnapshot初始固定提示、库详情与完整翻译/语言选择仍待完成，真实功能和安全验收未完成。

2026-10-01：MobileService运行/回读/被动萃取/启动失败/采样失败及退出阻断41项声明提示替换，新增35条资源、复用6条；萃取状态仍以current.name展示，异常仍以simpleName展示。事件code、身份和serial/确认条件、手动萃取识别、恢复clear、服务退出阻断及命令调用未改；历史reason中的连接中断/机器手动萃取/机器待机回报/设备服务停止保持原值。整个Service正向显示替换重放一致，41条XML/两包编译资源核对及10项动态格式对照通过。完整离线回归：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。MobileSnapshot的初始默认提示仍为固定中文，MockEngine/库详情等外部来源提示及完整翻译/语言选择尚未完成；不把历史存储字段强行翻译。未连接真机，整体真实功能和安全验收未完成。

2026-10-01：MobileService.prepareBrew/cancelBrewPreparation/startShot/stopShot区域60项声明显示替换，新增53条资源、复用7条；动态温度先toString，曲线名和阻断原因作为参数，nullable超时原因仍按原插值呈现null。预热token/目标温度与最终permitsWrite、原设备匹配、10分钟取消、归零preflight、startBlock/studio门禁、安全记录arm/clear、启动请求字段/日志、历史stopReason及紧急停止路径均未改。限定区域正向替换后整个Service一致；60条XML/两包编译资源逐值核对，26项动态格式对照（含百分号/换行/null原因）通过。完整离线回归通过：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。事件code/默认中文一致；未连接真机。运行/回读事件与Mock/库详情等剩余提示、完整翻译/语言选择仍待完成，整体真实功能与安全验收未完成。

2026-10-01：MobileService.changeMachineSetting/resetCupCount/changeSleepSchedule/enterSleepNow四类机器操作的80项声明文案替换，新增77条资源、复用3条。回读新鲜度/清醒待机、expected比较、单日变化限制、原身份与安全记录arm、token/serial推进、最终发送、clear调用和6/8/12秒超时均保留；GATT写入后仍等机器回读，不把写入成功作为完成。整Service限定区域正向替换后一致，80条XML及两包编译资源逐值核对，12项动态设置名称格式对照通过。完整离线回归通过：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。事件code/默认中文一致；未来翻译时显示message可变，诊断用code。未连接真机；预热/萃取/运行事件等服务剩余提示、库详情及完整翻译/语言选择仍待完成，整体协议和实机安全验收未完成。

2026-10-01：MobileService.scan/connectCoffee/connectScale/disconnect区域25项声明文案替换，新增24条资源、复用1条；扫描数量、失败类型及断开role.name使用字符串参数保留原ASCII/原标识。密码六位数字规则、记忆凭证重试、两类原机器身份匹配、手动/应用萃取禁止切换、杯数/计划回读阻断、预热取消后等待、候选排序/64条限制及连接回调均未改。限定区域正向替换后整个Service一致，25条默认XML与两包编译资源逐值核对通过，14项动态格式比较通过；事件code保持原样，默认中文一致。完整离线回归通过：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。未连接真机；服务机器写入/运行事件等剩余提示、库详情和完整翻译/语言选择仍待完成，真实安全与功能对标验收未完成。

2026-10-01：MobileService去皮启动阻断/独立去皮反馈及机器设置未知提醒新增14条默认资源，限定两个去皮区域及一个getter共3项声明变换。tareStartBlock的WRITING/WAITING_ZERO/UNKNOWN仍返回非null，其余null；手动/应用萃取阻断、READY判断、重复去皮、回调状态与发送均未改。settingWriteUnresolvedMessage改为访问时getter，避免Service尚未附加Context时读资源。事件code/触发条件不变，默认中文与事件message逐值一致；未来翻译时这类事件的显示message会随语言变化，诊断识别应使用事件code。整个Service正向重放与声明替换一致，14条XML/两包编译资源核对通过。完整离线回归通过：协议39,823项检查/32,856条通知回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。会话回归包括去皮未确认阻止下一杯与排队去皮发送前重检查。本轮未连接真机；服务连接/机器写入等剩余提示、库详情、完整翻译及语言选择仍待完成。

2026-10-01：MobileApplication操作记录导出/历史ZIP导出反馈新增6条资源、4项声明显示替换，全部/部分历史分别使用完整模板。unavailable>0的原条件与数量保留；trace导出export.finished的message仍为原诊断文本，Toast在主线程读取显示资源，避免翻译改变诊断日志。Archive写入代码、CSV/TSV列与README、路径、样本有效性、UNKNOWN状态及导出日志code/result未改。整个Application正向重放一致，61项动态格式对照、6条XML/两包编译资源核对通过。Alpha/Mock各104项测试通过、1项缺真实导出跳过，两包构建成功，其中两项Archive测试覆盖UNKNOWN/路径注入及单份采样失败保留其它历史。本轮未改协议/会话，未重复完整协议回归，未连接真机。库详情/服务剩余提示、完整翻译/语言选择及实际文件选择器/导出验收仍待完成。

2026-10-01：LegacyCurveActivity列表/空状态/详情回退/导入与读取反馈共19项声明显示替换，新增18条资源、复用3条。工厂/用户列表以及changed/未变化导入结果使用整句模板，判断仍为原factory/changed布尔条件；分类映射、未知值回退、详情数值及原已有legacy_curve_detail模板保持原逻辑。独立legacy_curves.json、Codec/Store边界/校验/原子落盘、导入日志code/result、线程generation、文件选择及控制曲线库未改。整个Activity正向重放与声明的显示替换一致；88项动态格式比较覆盖空白/百分号/换行名称与分类/异常文本；21条XML和两包编译资源逐值核对通过。Alpha/Mock定向离线测试各104通过、1项缺真实导出跳过，两包构建成功；未改协议/会话，未重复完整协议回归，未连接真机。库详情及服务剩余提示、导出反馈、完整翻译/语言选择仍待完成，真实旧版导出验证仍缺证据。

2026-10-01：LegacyHistoryActivity、LegacyHistoryDetailActivity及MobileApplication.importLegacyHistory结果Toast共23项显示替换，新增20条资源、复用3条。列表与详情的日期、秒数、profileName空白回退、失败状态与两行换行保持原样；旧版采样单位/不证明真实结束的说明保留。独立legacy_history.json、Codec/Store大小边界/冲突/去重/原子落盘、legacyId、异步读取generation及控制库均未修改，导入日志code/result未改。74项默认格式比较（含百分号/换行名称和异常信息）、三个完整源文件正向重放、23条XML及两包编译资源逐值核对通过。Alpha/Mock定向离线测试各104通过、1项缺真实导出跳过，两包构建成功；本轮未改协议/会话，未重复全协议回归，未连接真机。旧版曲线导入浏览页、库详情/服务剩余提示、完整翻译和语言选择仍待完成；真实旧版历史/曲线导出仍缺证据。

2026-10-01：HistoryActivity和HistoryDetailActivity的列表/详情/空状态/未知结果/停止原因显示新增35条默认资源、复用12条，共53项声明替换。状态与停止原因仍按原枚举/name映射，未知原因仍显示原值；manual标识、shotId、日期格式、读采样线程、结束判定、30天/500条保留及ZIP文件名/MIME/导入导出调用未改。动态数值保持原Locale.ROOT小数格式与ASCII计数，完整/部分采样提示分别使用整句资源。两个完整源文件正向重放与声明显示替换一致，47条XML/两包编译资源逐值核对通过；180项动态格式比较覆盖四种默认Locale。最初临时核对脚本误用了已复用资源的旧拟定名称，修正脚本后通过，应用未发生该错误。Alpha/Mock定向离线测试各104通过、1项缺真实导出跳过，两包构建成功；本轮未改协议/会话，未重复完整协议回归，未连接真机。旧版历史与导入页面、库详情/服务剩余提示、完整翻译及语言选择仍待完成。

2026-10-01：CurveStageView、ShotChartView、LegacyShotChartView的阶段编号、空状态和各物理量图例共11处显示文字迁入默认资源。动态整数先按原toString转换，旧版小数保持原默认Locale的%.1f格式；单位、量程、路径、缺测断点、单点绘制与条件不变。119项格式对照通过（旧版小数覆盖简中/美国/德国/阿拉伯Locale）；按声明替换正向重放三个完整源文件一致，Alpha/Mock APK内11条资源逐值核对通过。定向离线验证：Alpha/Mock各104项通过、1项因缺真实导出跳过，两包构建成功。本轮未修改协议/会话代码，因此未重复完整协议回归；未连接真机。历史及导入页面、库详情/服务剩余提示、完整翻译和语言选择仍待完成。

2026-10-01：曲线库CurveActivity入口/筛选/空状态/详情/槽位及资格提示新增30条资源、复用1条。分类改为CurveCategoryFilter稳定枚举，保存枚举name，兼容旧Bundle中文键；空或无法识别的旧状态回到ALL（旧异常值可能导致空列表，本轮明确改为恢复全部）。筛选使用legacyKey匹配未修改的库数据，显示label只负责资源，未知类别保留原文本，“其它”有独立资源。原CurveSearch字符串筛选方法保持原样，新增类型重载仅委托规范键；两项新分类恢复/筛选测试先因API未实现失败，迁移后通过。完整103条曲线、各ID/名称/空白等查询及六类新旧保存值做5,016项顺序/启动资格对照，均一致；本轮临时比较库未加载工厂控制proof，不能据此声明工厂实机验收。51组默认动态文案（含百分号名称）对照、两变体30条编译资源逐值核对通过。整个Activity与声明的显示/分类标识修改正向重放一致，选择/槽位回调、item.id、canStart条件、曲线元数据与启动报文不变。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit61项；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建成功。曲线详情的库文案、阶段图表、旧版导入/历史页面、服务剩余提示及完整翻译/语言选择仍未完成，未操作真机。

2026-10-01：机器写入未确认提醒的分类移入纯会话层MachineRecoveryWarningPolicy，按原规则仅在BREW_WAIT的WRITING/WAITING_TEMP/READY阶段暂不显示恢复提醒；不清除持久kind，不发送/确认/重放写入。Mobile的MachineRecoveryText将可空kind映射为资源，服务在访问时解析。六类写入提醒、萃取重启/睡眠未知提示及acknowledgeManualSafety里的18条等待/保存失败/用户核对事件新增26条默认资源；事件code与清除/证据核对流程未改。两个原初始化常量改成字符串getter，避免Service尚未附加Context时读取Android资源。三项共享分类测试先因API未实现失败，迁移后通过；全部七种可空kind与八种预热阶段做112项空值/中文对照一致，通过后移除临时旧实现。Alpha/Mock的26条编译资源逐值相同，整个MobileService正向重放与声明的显示/分类替换一致，存储key、clear调用、原机器证据和发送门禁不变。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit61项；Alpha/Mock各102通过、1项缺真实导出跳过，三个APK构建成功。服务其它业务/回报文案、曲线/历史及图表页面、完整翻译/语言选择仍未完成，未操作真机。

2026-10-01：StudioStartGate、BrewWaitCancelGate、ShotRecoveryClearGate三种Android提示适配器改为按当前Context注入资源解析，连同服务的设置回报过期提示新增13条默认资源。共享PreheatGate/ShotRecoveryGate的判断、原机身份、未知结果及新鲜待机限制未改。新增空译文回归先因接口未实现失败，迁移后验证阻止仍非null、可操作情形仍null，检查显示不写Storage且pending不被解除。与迁移前实际原生实现做19,236项组合对照，覆盖全部连接/萃取状态、五类机器帧、缺失/未来/1500ms边界回报、持久未确认/地址/手动萃取和全部八种预热阶段、运行模式、温度边界及不同曲线；对照过程中零存储写入。通过后移除临时旧实现。四个生产文件正向重放只含声明的显示/实例调用替换，整个服务其它发送/恢复逻辑不变；两变体13条编译资源逐值相同。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit58项；Alpha/Mock各102通过、1项缺真实导出跳过，三个APK构建成功。服务剩余恢复/业务文案、其它页面及完整翻译/语言选择未完成，未操作真机。

2026-10-01：实时萃取ExtractionActivity全部中文文案资源化，新增99条、复用7条。覆盖开始/预热确认、最大水量与有秤/无秤目标、Mock效果、通知权限提示、待归零/手动/未知/已结束指导、温度准备、停止原因与按钮标签。数字仍使用原ASCII及Locale.ROOT两位格式；前置/末尾换行与连续空格按带引号Android资源保留。94条静态值逐字核对，850组动态确认/概要/状态/温度/重量模板对照与迁移前原生表达式一致（含百分号曲线名、槽位7及1–5、有/无目标和未知值）；Alpha/Mock的99条编译资源逐值一致。103项声明的显示替换正向重放后，整个Activity与当前源码完全一致；发送回调、profile/slot/scaleMode参数、全部按钮使能/可见性、通知检查与预热/启动/停止门禁未改。仅运行与显示迁移相关的两变体测试及构建，Alpha/Mock各101通过、1项缺真实导出跳过，两个APK构建成功。页面引用的服务文案、图表共用组件、其它页面及完整翻译/语言选择仍未完成；无中文字面量不等于整个萃取页已完成多语言。未操作真机。

2026-10-01：MachineAlarms及ShotGate启动预检说明改为可注入的资源显示器，页面/服务通过当前Context解析；新增34条，涵盖C1–C14/C16、未知bit15、当前/过期告警、banner、九类预检阻止原因。已知表保留相同代码顺序，只存资源ID，按实际激活位解析文字，避免把翻译缓存为控制状态。实际阻止仍由CoffeeAlarmPolicy与ExtractionStartGate决定，mayReconnectCoffee/active保留原共享调用；不按文字内容决定许可。新增测试先因解析接口未实现失败，完成后在“translated”和空字符串两种替换下遍历所有65,536种告警组合，故障仍阻止、C16仍为预警。迁移前实际原生实现做197,058项对照：全部组合的活动告警代码/描述、启动阻止说明与ShotGate输出，加上各单个位、多故障、未知/超16位输入和空/未来/1500ms时效边界的banner及摘要；通过后移除临时旧实现。Home/Extraction/Service正向重放只新增显示器实例并改显示调用；报文、实际出队门禁、发送回调及恢复流程不变。两变体34条编译资源逐值相同。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit58项；Alpha/Mock各101通过、1项缺真实导出跳过，三个APK构建成功。其它安全恢复/服务文案、其它页面、完整翻译与语言选择仍待完成；未操作真机，也未证明故障位的物理行为。

2026-10-01：萃取断线/结果未知的提醒分类下沉为device-session纯Kotlin的ShotSafetyPolicy，三类原因与null分支按原Alpha规则保留；Android ShotSafetyAlert仅把原因映射到资源。Alpha首页和服务通知接入同一映射；通知目标页的判断改为检查可空资源ID，不依赖翻译后的内容。该分类不发送命令、不解除恢复标记、也不能证明物理停水。先运行三项新共享规则测试，因API未实现失败；实现后通过。迁移前实际规则的全部54种萃取/设备状态组合做108项空值与文案对照，分支/优先级归一核对完全一致，随后移除临时旧实现。新增10条断线提醒及通知文字资源，Alpha/Mock编译值逐字核对；整个Home/Service只包含声明的资源/显示替换，频道ID、通知重要级别、STOP动作、目标页逻辑以及其它发送/恢复门禁不变。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit58项；Alpha/Mock各100通过、1项缺真实导出跳过，三个APK构建成功。其它安全恢复文案、机器告警、服务外围及其它页面仍待资源化；无完整翻译/语言选择，未操作真机。

2026-10-01：共用HoyiUi导航、返回与当前页无障碍描述、停止按钮和FirmwarePresentation提示资源化，新增13条并复用既有等待固件/停止文字。StopActionPresentation只返回labelResource，Home与Extraction各自按Context显示；visible/enabled谓词未改，传输停止仍走原服务路径。固件显示器使用可注入资源解析器，默认中文内容、未就绪不显示旧机器身份、Mock不声称真实验证的规则保持。迁移前实际实现做396项对照，覆盖全部萃取/设备状态和服务是否运行的停止显示/启用/文字，以及空/不同固件和Mock组合；通过后移除临时旧实现。五个生产文件正向重放与声明显示替换一致，Alpha/Mock的13条编译资源逐值相同。AAPT核对脚本最末string后包含style类型段，已修正分段后完成核对，未修改APK内容。测试XML解析器更名DefaultStringResources，供多个显示器共用。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit55项；Alpha/Mock各100通过、1项缺真实导出跳过，三个APK构建成功。机器告警、安全提醒、服务文案和其它页面仍未完整资源化，无完整翻译/语言选择。上一提交 `6de89f9` 云端Verify成功；未操作真机。

2026-10-01：首页HomeActivity全部中文文案资源化，新增114条、复用7条已有默认资源，涵盖六类未知结果核对、连接密码、拨杆确认、入睡限制、去皮进度、紧凑状态、曲线与五槽位标签。106条静态替换逐值核对，130组动态模板与迁移前原生表达式一致；Alpha/Mock编译资源114条逐值核对含连续/前置空格与换行。按钮辅助方法改为显式primary参数，仅原“选择曲线”的brewButton初始化传true，不再用翻译文字决定样式。按119项声明替换正向重放，整个Activity与当前源码一致，连接/权限、启动、停止、去皮、睡眠/设置的谓词与回调未改。本轮只跑与显示修改相关的两变体测试和构建，Alpha/Mock各100通过、1项缺真实导出跳过，两个APK构建成功。首页共用组件及服务返回的动态说明、其它页面和完整翻译/语言选择仍未完成；Activity无中文字面量不等于整个首页已支持多语言。上一提交 `5af6126` 云端Verify成功，Capture Mock UI读取时仍在运行，未宣称本轮已视觉验收或实机验证。

2026-10-01：MachineSettingsPresentation改为实例化显示器，Activity/Service按当前Context解析资源，JVM测试注入默认XML解析器；设置概览、拨杆状态、十二类修改说明、详细回读、睡眠计划及缺失/未知/原始值提示全部资源化，本轮新增48条（含Activity最后一处“设置”回退标签）。数字继续按原ASCII字符串传入，原两位时间格式不变；保留未知拨杆组合与不完整睡眠回读，未把局部回读提升为完整配置。迁移前实际原生实现与当前显示器做49,179项逐字对照，通过后移除临时旧实现，只保留注入翻译资源不改启用状态/不补造回读的回归。四个生产文件与声明的显示替换正向重放一致，包括整个MobileService，发送条件/回调/报文不变。Alpha/Mock的48条编译资源逐值核对，含连续空格、结尾空格与换行。最终完整离线回归：协议39,823项检查/32,856条回放、会话92场景、共享JUnit55项；Alpha/Mock各100通过、1项缺真实导出跳过，三个APK构建通过。首页/其它页面、服务外围文案及完整翻译/语言选择仍未完成。上一提交 `a2dda3e` 云端Verify和Capture Mock UI成功；未操作真机，未宣称硬件等价保障已完成。

2026-10-01：九种连接状态标签改为由页面Context读取默认资源；首页拨杆菜单直接按手动/自动压力/自动流量三个资源生成，不再从中文确认文案截取。三项命令映射与发送回调不变；新增12条默认资源逐值核对，通过声明的显示修改正向重放确认四个生产文件没有其它变化。现有连接标签测试同步到资源接口。Alpha/Mock各100项测试中99通过、1项缺真实导出跳过，两种APK构建通过且均包含12条资源。首次构建暴露未引入的可选StringRes注解及旧测试调用，移除注解并更新测试后重跑成功。未改协议/门禁，未操作真机；尚未完成全应用翻译或语言选择。上一提交 `519995d` 云端Verify与Capture Mock UI均成功，Capture任务成功不代表本轮界面已人工验收。

2026-10-01：连接/回读复合概览及两种睡眠计划预览新增10条带参数/标签资源，保持原ASCII数字和Locale.CHINA时间格式；294组默认中文概览/预览与迁移前原生输出逐字一致，Alpha/Mock资源表均包含新条目。Activity除声明的显示替换和颜色判断外与迁移前代码一致。SleepDaySummary增加从原启用掩码解码的可空enabled，颜色不再比较中文“开启”；256种掩码及缺失/异常首段测试保留未知，并验证改显示标签不改语义值。既有显示内容、控制门禁、时间输入/回调和协议不变。完整离线回归共享JUnit55、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各99通过、1项缺真实导出跳过，三个APK构建通过。Presentation/服务与其它页面文案及少量Activity字符串仍需处理，多语言未完成。上一提交 `454041f` 云端Verify通过。未操作真机。

2026-10-01：设置页反馈与确认继续资源化，本轮71处文字/参数化表达式替换新增63条默认资源，复用已有资源；保留等待回读、失败、未知、重新核对、缺水/工作室/补偿提示以及杯数不可撤销与输入确认内容。四段提示的前置换行仍由中性分隔符保留；数字确认参数用原ASCII字符串显示，时间参数解析/范围和发送回调未改。按声明的文字替换正向重放迁移前HEAD后，整个Activity与当前文件一致，XML中文逐值及Alpha/Mock APK资源表核对通过；反向替换会误改原先已有同名资源调用，因此不能用无范围的回填作等价证明。完整离线回归共享JUnit55、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各98通过、1项缺真实导出跳过，三个APK构建通过。睡眠预览模板/复合概览、Presentation/服务文案和其它页面仍未完整资源化，多语言未完成。上一提交 `fa1ee44` 云端Verify通过。未操作真机。

2026-09-30：机器设置页onCreate中的布局、按钮、星期标签、输入提示/范围错误共60条文案移入默认strings资源，动态编辑日标签使用带参数资源；中文值不变。逐值XML核对，并把资源调用回填为原文字面量后，整个Activity与迁移前HEAD完全一致，证明本轮没有顺带改控制/输入谓词。Alpha/Mock的APK资源表均含60条资源；完整离线回归共享JUnit55、会话92场景、协议39,823项检查/32,856条通知回放，Alpha/Mock各98通过且1项缺真实导出跳过，三个APK构建通过。动态render反馈、确认弹窗、产品服务提示和其它页面仍需资源化，尚未完成多语言或语言切换。上一提交 `6f77472` 云端Verify通过。未操作真机。

2026-09-30：机器设置页概览加入只读应用信息卡片，读取生成的BuildConfig版本名/版本号、applicationId和Mock标志，并使用当前变体的app_name。Alpha/Mock分别标明原生设备版/模拟演示，包名可复制；无需设备回报，不接入控制或更新安装路径。新增文案资源化；没有修改签名、版本号、协议或Service逻辑。完整离线回归共享JUnit55、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各98通过、1项缺真实导出跳过，三个APK构建通过。aapt2核对两包实际应用名、包名、版本0.1.0/1及五个信息资源存在，生成BuildConfig的模式值分别false/true。布局仍待云端Mock/真机核对；应用更新与签名分发策略仍未实现，未操作真机。

2026-09-30：完成旧版功能域与当前原生入口的逐项对标审计，见 `docs/native-parity-audit.md`。补列多语言、应用版本/更新、机器密码修改及提示偏好的实际未完成项；明确软件缺口与实机/协议证据缺口并存，不能声称功能全部齐全只差验收。旧本地设置密码在杯数重置中已由当前杯数输入＋二次确认替代，属于交互差异。修正杯数文档的单路/双路表述：代码要求Settings与IdleTelemetry两路新零值，且各自序号递增。本轮只更新文档，未改控制代码、未操作真机；代码基线 `1437e8d` 云端Verify已通过。

2026-09-30：`CupResetTracker` 的设置、待机两路回读各自推进已接受的采样序号，重复或倒序序号不再覆盖较新杯数。此前仅比较写入时基线，旧零值可能覆盖新非零值并错误确认；两项测试先复现失败再通过。另补未知结果下旧值不能错误RECONCILED的测试。报文、启动/重置发送、超时和持久记录流程不变；此序号是主机观察次序，不是固件事务ID，不能识别被调用方重新编号的过时设备内容。完整离线回归协议39,823项检查/32,856条通知回放、会话92场景、Alpha/Mock各98通过且1项缺真实导出跳过，三个APK构建通过；补充未知场景后共享check累计55项JUnit通过。上一提交 `93c9cc3` 云端Verify通过。未操作真机，未声称实机已发生该故障。

2026-09-30：`ShotRecoveryGate` 将人工清除萃取提示的条件移入共享Kotlin层，返回枚举原因；Mobile只映射中文。原顺序不变：手动/应用萃取未结束先拒绝，其次原机器身份，最后咖啡机READY及1.5秒内待机回报。未知旧身份仍沿原显式人工确认路径，不允许后续无关被动萃取自动清除。门禁不改存储、萃取状态或控制命令，计数Storage适配确认0次写入。扩充原产品基线先通过，新增3项共享判断测试；完整离线回归共享JUnit52通过、会话92场景、协议39,823项检查/32,856条通知回放，Alpha/Mock各98通过、1项缺真实导出跳过，三个APK构建通过。增强无写入断言后核心check再次通过。上一提交 `5ad28f9` 云端Verify通过。未操作真机。

2026-09-30：`ShotRecoveryState`、`MachineWriteRecoveryState` 与12项原JUnit迁入 `device-session`。4个生产/测试文件与迁移前HEAD比较，除包名外方法体完全一致。同步Storage接口、读写异常/失败时保守阻止、原机器身份绑定、各类回读证据和未知结果保留不变。Mobile仍提供相同SharedPreferences键/枚举名称及同步commit适配，持有状态实例、采样序号、人工确认和实际控制协调，不将连接恢复视为上一杯结束。完整离线回归共享JUnit49通过、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各97通过、1项缺真实导出跳过，三个APK构建通过。产品测试减少12项是迁移而非删除。未操作真机，上一提交 `906e337` 云端Verify尚运行。

2026-09-30：`ExtractionStartGate` 将产品萃取预检移入共享Kotlin层，接收可空目标重量（空表示未选曲线）、调用方验证结果、设备快照/采样时间和萃取状态，返回枚举拒绝原因。保留未选/未验证曲线、未结束上一杯、咖啡机READY、新鲜待机、睡眠、重量模式秤READY/时效和告警的原判断顺序；不替代参数/固件白名单、设置新鲜度、去皮、持久恢复或最终出队许可。Mobile的 `ShotGate` 仅映射产品输入与文案，active复用共享未结束状态分类，重连规则保持原样。扩充原产品测试后先通过基线，新增4项纯JVM状态/时效/故障决策测试。完整离线回归共享JUnit37通过、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各109通过、1项缺真实导出跳过，三个APK构建通过。上一提交 `0e144d7` 云端Verify通过。未操作真机。

2026-09-30：`CoffeeAlarmPolicy` 统一页面启动/告警banner与会话实际发送前的告警判断，返回首个阻止控制的位序。保留原16位0xBFFF规则：只有无告警或单独bit14/C16尾水预警允许新控制，其它已知故障及未知bit15阻止。告警中文映射仍在Mobile；未改编码、取消预热或紧急停止。原页面先完成全部65,536组合基线，共享层与迁移后页面再次全组合核对；真实会话测试逐位拒绝启动、排队期间出现bit15拒绝实际出队。完整离线回归共享JUnit33通过、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各107通过、1项缺真实导出跳过，三个APK构建通过，补充队列场景后会话check再次通过。上一提交 `60541f0` 云端Verify通过。未操作真机。

2026-09-30：工作室启动与取消预热门禁抽到共享 `PreheatGate`，输入协议设置/温度/曲线ID和目标或取消上下文，返回枚举原因。Mobile两类gate只负责曲线映射与中文文案；不移动发送、持久恢复、超时或最终出队检查，不改变报文字节和拒绝优先级。原产品测试加连接/萃取状态与时效/温度边界后先通过基线，再验证迁移；新增3项纯JVM决策测试。完整离线回归共享JUnit31通过、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各106通过、1项缺真实导出跳过，三个APK构建通过。上一提交 `7a159ce` 云端Verify通过。未操作真机。

2026-09-30：被动手动萃取识别只接受递增时间戳；重复或倒序帧不能替换候选、追加重复点或回退最后活动时间。断线清除时间水位，原有超过2.8秒且收到待机回报才结束的规则不变，不新增机器控制。两项测试先复现失败，再通过完整离线回归：共享JUnit28通过、会话92场景、协议39,823项检查/32,856条通知回放；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建通过。上一提交 `0830a46` 云端Verify通过。主机仍负责连接代次和时间来源，未操作真机。

2026-09-30：预热与取消增加贯穿Mobile/Hub/会话/队列的必填实际发送前检查。`BrewPreparation.permitsWrite` 绑定请求号、目标和WRITING/CANCELLING阶段，取消、消费或断线后旧排队预热不能发送；有效零取消仍沿原恢复门禁发送。Mobile复核持久预热标记、原机器地址和手动萃取门禁，Hub复核App萃取未结束状态。先复现旧排队预热出队，再验证三类失效场景与有效取消原字节。完整离线回归26项共享JUnit、92个会话场景、39,823项协议检查/32,856条通知回放；Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建通过。已经发送的预热不能撤回，取消成功仍须原回报/人工核对，不改变紧急停止。未操作真机。

2026-09-30：`BrewPreparation`、`PassiveShotDetector` 及8项原JUnit测试迁入 `device-session`，4个生产/测试文件方法体与迁移前HEAD逐一比较一致，仅包名和导入改变。共享模块保持无Android/产品模块引用，累计25项JUnit通过；协议39,823项检查/32,856条通知回放、会话91场景通过，Alpha/Mock各104通过、1项缺真实导出跳过，三个APK构建通过。产品测试数量减少8项是迁移而非删除。`StudioStartGate`、预热发送/超时/持久化、被动历史记录继续由Mobile负责；状态机迁移不改变原时序或控制许可，不构成统一业务协调器完成。未操作真机，当前边界见 `docs/architecture.md`。

2026-09-30：`SettingsWriteTracker`、`CupResetTracker`、`SleepNowTracker`、`SleepScheduleWriteTracker` 及17项原JUnit测试迁入 `device-session`。8个生产/测试文件方法体与迁移前HEAD逐一比较一致，仅包名和导入改变；Android页面改为引用共享类型。共享模块测试依赖使用现有JUnit4.13.2，不进入生产依赖。完整离线回归：共享JUnit17通过、会话91场景通过、协议39,823项检查/32,856条通知回放；Alpha/Mock各112通过、1项缺真实导出跳过，三个APK构建通过。原130项产品测试减为113项，是17项迁至核心而非删除。服务仍持有四类tracker实例、采样序号/超时/安全持久化，尚未合并成统一业务协调器。当前边界见 `docs/architecture.md`，未操作真机。

2026-09-30：直接冲泡温度编码方法复用 `MachineSettingChange.BrewTemperature` 的 75–105°C 范围，不再接受仅满足单字节范围的0/1/74/106/255。合法设置范围和报文字节不变；预热取消 `brewWait(0)` 与加热开关不受影响。先复现越界值可编码，再验证拒绝及端点/旧版设置报文对照。完整离线回归39,823项协议检查、32,856条通知回放、91个会话场景，三个APK构建通过；未使用真机。前一提交 `fba2ddb` 云端Verify/Mock仍运行，未宣称云端通过。

2026-09-30：记忆咖啡机密码的失败分类抽为 `CoffeeCredentialRetryGate.connectionState`。只有 remembered 请求进入 FAILED 才消耗两次回退预算；未验证固件 UNSUPPORTED、连接中、主动断开及手动密码失败不消耗，READY按原地址清零。UNSUPPORTED不会据此保存新密码或宣称密码已验证，固件控制许可不变。先复现未验证固件耗尽预算，再修正并核对地址隔离/READY清零。完整离线回归91个会话场景、39,816项协议检查、32,856条通知回放；Alpha/Mock各129通过、1项缺真实导出跳过，三个APK构建通过。上一提交 `1684e31` 云端 Verify/Mock均在启动阶段失败，无作业/日志，不属于已验证测试失败；只重试一次Verify。未使用真机。

2026-09-30：首页新增固件版本及协议支持状态。`CoffeeFirmware` 身份从认证阶段之后的当前连接设置读取，仅通过 Hub/Mobile 只读展示；未认证、断开、失败、换设备及旧代次帧不能沿用版本。未验证版本保持控制禁用，Mock标注模拟版本。控制许可与报文字节未改变；“已验证协议”不是全部功能实机验收。完整离线回归91个会话场景、39,816项协议检查、32,856条通知回放，Alpha/Mock各127项通过、1项缺真实导出跳过，三个APK构建通过；另补验未验证固件的六类控制无新增传输。上一提交 `eb28055` 云端 Verify通过，Mock采集仍运行。未操作真机，见 `docs/firmware-status.md`。

2026-09-30：新增共享 `ScaleReadingPolicy`，重量启动准备、自动停止策略、实时显示和 Mobile 收数使用相同的既有业务范围（−500到6000g）与1.5秒时效。负时间戳、未来/过期时间及超范围重量不具备业务资格，异常通知不刷新 Mobile 的正常重量时间戳，也不刷新停止策略的健康期限；BOOKOO 解码及报文字节未变。回放测试将日志相对时间统一加10000ms映射到非负单调时钟，保留原始帧、间隔及三杯停止时刻断言。该范围是已有软件规则，不是秤物理量程或硬件安全保证。完整离线回归91个会话场景、39,816项协议检查、32,856条通知回放；Alpha/Mock各124项通过、1项缺真实导出而跳过，三个APK构建通过。上一提交 `96a3033` 云端 Verify 通过；未操作真机。

2026-09-30：去皮 API 必须显式提供实际发送前检查，沿 `ScaleControl` → `ScaleSessionControl` → `DeviceSession` → GATT 队列传递，无默认放行参数。重量预去皮绑定本杯序号、STARTING/准备状态、五秒截止和原咖啡机启动上下文；流量模式延迟去皮绑定原杯且仍 RUNNING/咖啡机 READY；独立去皮复核萃取互斥。取消/过期/条件变化后的排队去皮返回未发送失败，已发出的请求仍按成功/未知结果跟踪。真实会话配合仿真驱动覆盖六类发送前变化，无额外去皮帧到达驱动。完整离线回归88个会话场景、39,816项协议检查、32,856条采集通知回放；Alpha/Mock各123项通过、1项真实导出测试跳过；Lab/Alpha/Mock构建通过。上一提交 `679b640` 云端 Verify 与 `966c436` Mock采集通过。未使用真机。

2026-09-30：萃取预去皮和流量模式延迟去皮也统一走 `ScaleSessionControl` 的同一状态机。取消预去皮只取消咖啡机启动意图，不抹掉仍在 GATT 队列/等待归零的去皮结果；下一杯重量或流量启动均等待上一笔确认，未知须显式重新去皮。实际 BOOKOO 会话测试覆盖取消后尚未收到回调、晚到成功及未知、重复去皮拒绝和显式恢复。完整离线回归：87 个会话场景、39,816 项协议检查、32,856 条采集通知回放；Alpha/Mock各123通过、1项真实导出条件测试跳过，三个APK构建通过。上一提交 `966c436` 云端 Verify（含 Lint）通过，Mock采集尚未结束。未操作真机。

2026-09-30：独立 BOOKOO 去皮状态移入 `device-session`，由 `NativeDeviceHub` 单一管理。共享萃取控制器必须显式检查 `ScaleControl.startAllowed`；去皮等待/未知时拒绝重量和流量启动，未结束的 App 萃取拒绝 Hub 独立去皮。失败或取消的重试不能清除此前未知结果；极端负数不会溢出为近零。归零确认在回报处理时也检查 5 秒截止，不能依赖定时器先执行。页面复用同一状态并给出阻止原因；预热和紧急停止未加入去皮等待门禁。保护仅限同一 Hub 生命周期，不是机器端事务证明或跨进程去皮恢复。 本轮完整离线回归通过：39,816 项协议检查、32,856 条采集通知回放、86 个会话场景；Alpha/Mock各124项单测，123通过、1项缺少真实导出而跳过；Lab/Alpha/Mock构建通过。未安装或执行真机写入。

## 当前状态（2026-09-30）

当前工作分支是 `feature/native-ble`，已推送至 `boolean93/openhoyi`。后文记录了各开发阶段的历史状态；其中“尚未安装”“未推送”等句子仅描述当时，不代表当前状态。以本节和所链接的专题文档为准。

- 停止适配器绑定有效启动请求提交时的机器/槽位，仅准备或过期准备请求不改变归属；底层每次停止在撤销回调及发送前复核地址/连接代次。停止不再默认槽位 7，编码器与会话仅接受显式 1–5 或 7。85 个会话场景、39,816 条协议检查和完整离线构建通过，恢复原机器停止仍可用，未用真机验证。
- 未知启动无阀门开启证据时，显式停止成功后须新鲜连续待机超过 2.8 秒才结束；间隔超过 1.5 秒、萃取帧和断线均重置窗口。每次停止独立编号，旧回调不影响重试。82 个会话场景与完整离线构建通过，新路径仍待机器停水对照。
- 共享 Hub 新增应用萃取连接切换门禁：结果未知只可恢复原咖啡机，未结束期间暂停秤连接变更和手动断开。自动重连暂停不扩展前台预算，已发起初始化不撤回；Lab 捕获拒绝。77 个会话检查及完整离线构建通过，Android 真机切换仍未验收。
- 启动上下文已前移到去皮前捕获：五秒内绑定连接代次/地址、曲线参数及关键冲泡设置，归零后与发送前复核；同机重连也不可复用。截止时刻归零不启动，无去皮门禁拒绝正确退回 IDLE。76 个会话场景通过，Alpha/Mock 完整离线构建通过，尚未用机器验证这些异常时序。
- 正目标预热发送前复核工作室模式；工作室启动发送前复核补偿温度 ±1°C。共享 `BrewTemperaturePolicy`，不改变原报文字节。
- 整周计划写入接口必须携带原计划。首包发送前比对新鲜完整回报，尾包允许自身首段目标回报但拒绝其它日期变化；部分写入不自动补发。69 个会话场景及 Alpha/Mock 离线构建通过，机器端原子性与实际计划生效仍未验收。
- 睡眠计划完整回报要求首尾顺序、10 秒配对窗口及 180 秒时效，新首段使旧尾段失效；开启命令发送前复核入队时的整周内容。67 个会话场景与完整离线构建通过。规则不是机器端事务保证。
- 杯数重置接口必须传入用户确认的原杯数，提交与发送前均要求设置、待机杯数匹配；不再提供无确认值的 Hub/会话入口。离线回归与 Alpha/Mock 构建通过，真实清零仍待验收。
- 设置回报采用 180 秒时效门禁；待机时间/温度在实际发送前复核携带的另一项值。该策略只能防止已观察到的旧快照回写，不能保证机器回报间隔或消除所有机器端并发改动，见 `docs/protocol-safety-gates.md`。
- 用户短期不提供测试设备；继续离线开发，不执行 ADB 或真机写入。机器功能与协议安全尚不能宣布完成。
- GATT 队列在发送前检查期间保持串行；机器设置、睡眠、预热、杯数重置均在真正发送前复查新鲜唤醒待机，紧急停止会撤销尚未发送的旧控制写入。停止写入后若 5 秒无待机确认，状态转未知，只有用户主动重试才再发停止。详见 `docs/coverage.md`。
- 普通设置、整周睡眠计划、立即睡眠、杯数重置和工作室预热加入同步持久化的未确认写入标记，绑定咖啡机地址；重启后只允许连接原机器。预热正常完成后可显式启动匹配曲线，机器确认萃取结束后清除标记；取消命令成功只表示 GATT 写入，仍须人工检查并等待新的清醒待机回报后才能解除限制。两包计划的确认必须发生在完整写入成功之后。当前控制安全边界见 `docs/protocol-safety-gates.md`。
- 预热取消现须在请求及 GATT 实际发送时都有新的清醒待机帧；已在萃取或上杯结果未知时拒绝，防止取消命令和萃取交错。机器告警不阻止取消。旧版机器实际响应仍待设备恢复后核验。
- 上一杯的持久安全记录未清除时，所有新机器设置、杯数重置、睡眠与预热均被服务层及 UI 阻止；之前只有新萃取受阻。当前记录不含启动槽位，进程重启后不推测槽位发送停止，需用户检查机器并用拨杆停液，再根据原设备新鲜待机回报人工清除提示。
- 人工清除上一杯提醒还要求应用萃取状态不在启动、运行、停止中或结果未知，且被动识别的手动萃取已结束；原机器 1.5 秒内的新鲜待机帧只是必要条件，不能单独解除持久标记。

- 原生 Alpha 已完成 HOYI 与 BOOKOO 双设备 READY、受监护实杯启动及手动停止、独立去皮的部分实机验证，证据与未验项目见 `docs/alpha-acceptance.md`、`docs/native-feature-status.md`。这不等于硬件等效或完整发布验收。
- 深浅色首页、曲线库、实时萃取、历史、设置及旧版只读页面已按 `docs/ui/native-redesign.md` 改造；设计参考图在 `docs/ui/`。萃取页对缺失的必需设备提供连接入口，首页在主操作前显示新鲜机器告警。
- 原生 UI 的 1600×1000 横屏深浅色五个主页面、曲线详情、选曲线、模拟萃取二次确认、运行中固定停止、自动结束及历史详情，已在无蓝牙权限的云端 AOSP Mock 包完成截图检查；600×1000 竖屏深浅色五个主页面及深色曲线/历史详情也已截图核对。另以 450×900（360dp 宽）检查浅色五个主页面；窄屏设备状态改为单行实时标签，空通知不再占萃取概览空间。首页主操作、竖屏曲线详情固定操作和各主页面底栏完整可见；[最新完整运行](https://github.com/boolean93/openhoyi/actions/runs/36373527863)。历史详情与旧版历史详情已去掉重复底栏，首页曲线操作和主题开关改为横屏紧凑布局。
- 用户短期不提供测试设备。继续离线开发、Mock 与自动化验证；不对用户真机运行 ADB、实机萃取或需要设备的检查。曲线编辑按用户要求后置；其他需要真机证据的项目不得因本地测试通过而标为已验收。
- 原生版可写协议的当前范围、已知硬件证据和恢复真机后的放行条件集中在 `docs/protocol-safety-gates.md`；不能把旧版编码字节相同解释成零硬件风险。
- 会话层现要求认证写入回调完成后收到新的 `0x83` 设置帧才使咖啡机 READY；先到的 `0x83` 不再被缓存后用于开放写入。若之后没有新帧，连接超时失败。47 个会话场景及离线构建通过；READY 前通知不进入业务层。设置结果未知时阻止后续机器控制，直到新 `0x83` 回读；已接受写入后的 GATT 非零回调按设备结果未知处理。杯数重置需写入后 `0x83` 与 `0x40` 都回报 0 才确认；结果未知后要重新取得两种一致回报才能再次重置。立即睡眠结果未知时，须有新的 `0x40` 清醒/睡眠状态才能继续机器控制。真实启动报文前还会同步保存未确认萃取标记，服务重启后恢复警示并阻止新萃取，直到机器结束回报或用户在新鲜待机状态下确认。以上新门禁尚待真机复验。
- 本地离线验证命令为 `./gradlew --offline --no-daemon :protocol-core:check :device-session:check :trace-core:test :bluetooth-android:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest :app:assembleDebug :mobile:assembleDebug :mobile:assembleMock`。GitHub Actions `Verify native app` 在提交与 PR 上跑对应任务；此前只调用纯 Kotlin 模块的 `test`，漏跑了其自定义 `verify` 报文和会话检查，现已改为 `check`，并让 `test` 本身依赖 `verify`，避免开发者本地误用。工作流不连接设备、不发布 APK。

分支 `feature/native-ble`，worktree `../openhoyi-native`，基于独立openhoyi仓库。旧hoyi-project未改动。前一阶段核心提交363cea5；本轮新增原生Lab诊断APK。

## 最新实机进展（2026-09-23）

详见 `docs/validation-2026-09-23.md`。独立APK已安装，咖啡机认证、设置和实时遥测已确认，固件1.1.3；BOOKOO四条初始化写入、Ready及并行遥测已确认。08:54系统GATT Map发现旧版持有咖啡机连接。用户确认无操作并授权后，暂停旧版；再次扫描发现两台设备，原生Lab重新双连。08:56～09:03独占对照中，咖啡机/秤持续约6分钟，累计385/3792条通知；切后台及锁屏各约1分钟仍收数，无自然断线。记忆秤随咖啡机Ready自动连接已实测；当时手动重复点击秤造成一次冗余重连；现已在共享Hub与会话层拦截，待实机复验。旧版目前保持停止，Lab保持连接。

前一晚两次BLE `status=8`断线仍未定因，独占对照只表明短时稳定。08:44仪器测试曾重启Lab并中断连接；后续必须用`scripts/run_lab_smoke.sh`检查服务不存在，不能在活跃连接上直接运行测试。实机仪器测试已验证Activity重建、前后台服务、服务停止后进程内ZIP导出。负重量、异常断线后的重试、SAF选择器、长期BLE和萃取仍未验收。认证未确认时只接收遥测也不Ready的回归已加入，会话场景增至33。

## 当前交付

六模块：protocol-core → device-session → bluetooth-android，另有trace-core供app（Lab）/mobile（Alpha）共用。纯Kotlin模块无Android依赖；两款App均为原生View、Activity、本地Binder和connectedDevice前台Service，没有UniApp/JS/WebView。独立包 `io.openhoyi.lab` 和 `io.openhoyi.mobile`，均不覆盖旧App。

`mobile` 第一段原生日常版已有首页连接、实时状态、传输日志导出、三条采集曲线与100条工厂曲线（分类及名称查找）、五个快捷槽位、常用机器设置写入与回读状态、萃取实时图、历史列表及曲线详情；见 `docs/mobile-alpha.md`。工厂曲线在有秤/无秤两种模式共200条临时启动帧及1000条快捷槽位帧与旧版编码函数逐字节对照，并在发送前再次核对曲线、帧和秤模式；报文不符即拒绝。萃取开始需显式确认，服务层要求咖啡机新鲜待机数据，重量模式要求新鲜秤数据；运行中拦截主动断链和停服务。用户要求暂不安装，因此尚无Alpha实机启动或控制证据。不能把代码验收当成硬件等效或完整替代App。

另有 `io.openhoyi.mobile.mock` 独立 UI 测试包（`HOYI Mock`）。服务用纯合成数据供首页、曲线、萃取图和历史页面检查；扫描仅列虚拟设备，所有控制写入被拒绝。合并清单无蓝牙权限，也不创建 `NativeDeviceHub`。构建命令 `:mobile:assembleMock`，产物 `mobile/build/outputs/apk/mock/mobile-mock.apk`。尚未安装，待用户安排；Mock 验收不代替 Alpha 真机验收。

Alpha 首页若有本包保存的 BOOKOO 地址，且蓝牙权限和开关可用，冷启动会自动启动前台服务；秤重试窗口从进入前台起10分钟，不再依赖咖啡机 Ready。失败退避、手动断开取消本轮；仅因自动重连启动且一直无连接时，后台或窗口结束后自动停服务。尚未安装 Alpha 实机验收。

萃取曲线从首个0x80采样点开始异步暂存，之后约每5秒更新一次文件；状态转为未知或断线时补一次快照，明确结束再写最终曲线。历史未知状态可展示最近已持久化的部分曲线，不把它当完整萃取。进程突然退出仍可能丢最后几秒或尚未写完的队列。

历史页新增“导出历史与曲线 ZIP”，由 SAF 创建用户选定的文件，后台线程写 `history.csv` 与每杯 `shots/NNNN.tsv`。状态 UNKNOWN 原样保留，部分曲线在 README 中说明；一杯采样损坏或读取失败标 unavailable，其他记录继续导出。文件选择器/提供方写入尚未实机验收。

采样文件读取现严格检查大小、表头、行数、数值和时间顺序；仅不存在的文件视为无采样。损坏文件在历史详情显示读取失败，ZIP 中标 unavailable，避免静默丢行或截断后误称完整曲线。

萃取图新增 BOOKOO 报告的咖啡流速，与新鲜重量取同一条秤通知，过期时两者都留空；本地采样升为七列 v2，仍可读旧六列 v1，ZIP 输出包含新字段。界面标为“秤流速”，按设备整数值除100显示；物理单位尚未实机校准。旧样本不会凭空补流速。

实时及历史曲线现绘制已有采样中的萃取温度，红色线使用独立动态刻度；其数据仍来自机器 `0x80` 通知，不新增写入或采样字段。尚未实机检查五条线在小屏上的可读性。

同地址 BOOKOO 连接请求在连接进行中或 Ready 时现在直接复用现有会话，避免扫描列表重复点击导致 GATT 断开重连。服务层保持当前重量快照并提示已连接；切换不同地址、失败后重试和手动断开后重连仍走原路径。尚未实机验收。

原生版所有六个 Activity 已接入本包独立的深浅色偏好和 Android 昼夜主题资源，首页 Switch 切换时仅重建 Activity，前台设备 Service 保持运行。图表、页面背景、卡片、文字与系统栏使用对应色板；未安装实机检查对比度或页面重建时的连接状态。

首页与萃取页现共用 `LiveTelemetry` 的1.5秒新鲜度判定；断链、未来时间戳或过期时温度、压力、秤重及秤流速显示“—”，不保留看似实时的旧数字。状态标签仍说明连接状态；固件版本属于上次已确认的设置，不当作实时遥测。

萃取开始、运行或停止阶段若咖啡机断链，或结果转为未知，`ShotSafetyAlert` 使首页和前台服务通知持续提示人工检查；尝试另发高优先级通知，明确观察结束后清除。通知权限被拒时不能保证独立高优先级通知，且任何通知都不能代替手动停止机器。Alpha后台提醒尚未实机验收。

启动门禁现读取同一条新鲜0x40待机帧的告警位：C1–C14与未知bit15阻止启动，旧版注明可用最后一杯的C16仅显示提示。旧采集没有非零告警帧，位序有旧版代码依据但行为尚未实机验证；会话层负责Ready和报文白名单，告警门禁在MobileService使用的ShotGate中。

首页新增萃取紧急停止按钮，调用与萃取页相同的 `MobileService.stopShot()`；停止请求中禁用重复点击，失联且结果未知时只能人工检查机器，按钮不假装能发送。萃取页在Android 13+首次进入时申请通知权限，未授权会在页面和启动确认显示后台提醒限制；尚未实机验收。

工厂曲线的逐字节许可集合必须从 `MobileApplication.curves` 注入 `NativeDeviceHub`，最终到 `DeviceSession.startExtraction`。若仅在页面和服务层放行，会话层仍拒绝工厂帧。默认无注入时会话仍只接受三条采集帧；不要移除此写入门禁。

机器告警显示：`0x40` 待机帧的字节12–13为16位告警掩码。`MachineAlarms` 依旧版 `listInfo` 将 bit0–14 对应 C1–C14、C16，bit15保持未知。`MobileService` 保留最近一次待机告警及接收时间；首页和萃取页区分当前、过期和未收到。萃取期间只有 `0x80` 帧时，不能把先前 `0x40` 告警称为当前状态。当前无告警忽略命令或由告警自动更改控制的逻辑。已有14,016条咖啡机通知没有非零告警掩码，合成帧测试不代表实机验证。

每周睡眠计划时间编辑使用旧版实际的两包整周路径，不使用未调用过的单日函数；详见 `docs/sleep-schedule.md`。协议模型要求七天与合法时间，服务层只允许在已验证固件、新鲜唤醒待机、两段计划完整回读后改单日；Session两包相隔500ms，第二包失败或断线可能部分应用，结果未知，需重新收到两段完整回报才能再编辑。两包新鲜回报匹配整周才显示确认。尚未安装Alpha或实机写计划；验收时先核对机器当前七天，再改一天，并检查其它六天不变。

待机温度编辑已接入同一条旧版 `setStandby` 命令，详见 `docs/standby-temperature.md`。旧版滑块允许0–100°C；原生版保留机器回报的自动待机时间档位，并在发送前重新核对。五档×101温度的505条旧版帧纳入机器设置对照，设置总样本数从92增至587。新 `0x83` 回报必须同时匹配温度与时间。未安装或实机修改。

冲泡温差补偿接入旧版 `setBlewTemp`（0x05），仅开放旧界面0–5°C六档；详见 `docs/brew-compensation.md`。编码以十分之一度发送，只有新 `0x83` 字节5回报匹配才确认，工作室预热继续使用机器回读值。机器设置对照总样本数从587增至593。未安装或实机修改。

累计杯数重置已接入旧版固定 `0A01A5A500` 命令及原生页面，详见 `docs/cup-count-reset.md`。旧版的设置密码只存于 App 本地，原生版未迁移，改为输入当前杯数并再次确认。服务层要求新鲜且一致的设置/待机杯数；BLE 写入后仅以新的机器报文归零确认，超时/断线结果未知，不自动重发。没有真实重置样本，也未在 Alpha 上实机执行。

本轮离线验证：旧版编码器重新生成200帧，与随包资源字节一致；Alpha单测19项0失败、debug构建成功、Lint 0错误/2条警告。未安装、未发起任何机器控制。

后续增加了首页 BOOKOO 独立去皮：`StandaloneTare` 将 GATT 写入与新鲜归零读数分成两个阶段，写入后5秒未见归零即显示结果未知；萃取期间禁止手动去皮。会话检查34项、Alpha单测22项、debug构建及Lint通过（0错误、2条警告）。该功能尚未实机验收，实际测试需检查写入日志、归零时延和重复点击门禁。

首页五个快捷槽位使用旧版 `startChart(slot,1)` 路径；`scripts/generate_factory_wire_oracle.py` 从旧版编码器另生成 `factory_slot_wire_v1.tsv`（100条×5槽×2秤模式，1000帧）。`FactoryWireProof` 在运行时核对后将帧许可传到底层会话；`CoffeeSessionControl` 保留启动槽位用于停止。快捷位默认旧版前五条曲线，曲线库可本地重指派；点击快捷位仍需显式确认。历史记录新增槽位字段，旧8字段记录继续可读。尚无实机槽位启停证据。

常用机器设置新增萃取/蒸汽设定温度、两路加热、照明、自动待机时间、供水来源、运行模式及每周睡眠计划总开关；首页新增手动/自动压力/自动流量三种拨杆模式。独立脚本 `scripts/generate_machine_setting_oracle.py` 仅执行旧版对应编码函数，生成92条允许参数报文样本。自动待机写入档位0–4，机器回读分钟0/15/30/60/120；更改时间时原样保留最近0x83回报的待机温度，若温度在确认期间改变则拒绝写入。`MachineSettingChange` 限制参数，`SettingsWriteTracker` 只在写入后收到新的匹配0x83设置帧时认定已应用；无回读显示未知。拨杆模式使用flags 0x80/0x40；运行模式另用0x0F命令和flags bit2确认。每日启用位按旧版0x83 0x40帧的bit7–bit1显示；开启总开关要求两段计划和所有时间有效，关闭不依赖计划完整。供水来源用旧版0x10命令和0x83 flags bit1确认，不能与排水流程的0x12命令混同；UI在切换到外接水管前要求确认实际接管。服务层与Hub均禁止萃取期间改设置。实机设置写入和回读时序尚未验收。

首页新增一次性立即睡眠命令，沿用旧版 `20 01 A5 A5 21` 帧。服务层要求已验证咖啡机的新鲜、明确未睡眠待机遥测，并阻止与萃取或设置事务并发；只有写入后更新的0x40遥测回报睡眠状态1才确认入睡，否则显示失败或未知。旧版没有App唤醒命令，用户需操作机器拨杆唤醒。原生萃取门禁拒绝睡眠状态1及其它未知睡眠值。尚未实机验证。

工作室模式还接入曲线温度准备：`setBrewWait(目标温度)` 使用0x11，目标只允许75–105°C；`setBrewWait(0)` 取消。独立脚本从旧版编码器生成32条报文，对应 `brew_wait_wire.tsv`。`BrewPreparation` 将写入结果和之后的新鲜待机温度分开，按旧版规则以机器温度减0x83补偿值后和目标比较，误差≤1°C才就绪；就绪仍需用户确认萃取。取消只有传输回调、没有机器回读字段；10分钟未启动会尝试取消。预热时阻止并发设置、入睡、断链、切换咖啡机和停服务；这些动作会先尝试发送取消命令。实机预热/取消/启动时序尚未验收。

设置报文资源复生成后字节一致；协议检查35,146项（含32,856条通知回放）、会话检查34项、Alpha单测28项通过，debug构建成功，Lint 0错误/2条既有警告。未安装、未发送实机设置命令。

本轮离线复核：临时200帧与快捷位1000帧重新生成后均与随包资源完全一致；会话检查34项、Alpha单测25项通过，debug构建成功，Lint 0错误/2条警告。未安装、未发出实机命令。

Lab仅提供授权/扫描/手动连接/断开/实时数据/日志导出/停止服务。没有萃取、设置、校准、OTA按钮。咖啡机连接必须输入6位密码（数字字节0..9），认证并同步时间；BOOKOO按500ms间隔执行4条初始化写入，收到之后的新样本才Ready。连接并非完全只读，以上初始化是明确例外。

- `app/.../LabActivity.kt`：界面、权限、扫描设备选择、内存密码、未知/过期/断线显示。UI按250ms刷新，不持有Gatt。
- `LabService.kt`：前台通知、双设备Hub、成功秤地址持久化、会话快照；页面离开不关闭连接；旋转不重置重连窗口。
- `LabApplication.kt` / `trace-core/TraceStore.kt`：进程唯一日志队列，避免Service快速重启多writer；导出独立于服务，SAF期间停止服务也可完成。Alpha由`MobileApplication`持有自己的实例。
- `device-session/.../WireTrace.kt`：认证01/改密0B整帧脱敏（含XOR），只在coffeeWrite识别；最大512bytes。Driver真实边界调用，观测异常隔离。
- `protocol-core/.../Protocol.kt`：已观察帧长度、BOOKOO ASCII符号与定点单位。
- `device-session/.../GattQueue.kt`：连接代次、串行、超时关闭、取消排队启动。
- `ExtractionController.kt`：去皮确认/样本时效/一次停止/结果未知；本轮修复断线重连后允许显式手动停止、未发出的启动被取消后可凭新idle结算；近期活动帧优先，不能误解锁。

手动拨杆萃取的被动记录已接入 Alpha：连续两条推进中的 valveOpen 0x80 帧才开始；记录图表并以 manual/槽位6 单独入历史。必须收到距最后活动帧超过2.8秒的 0x40 待机回报才结算，断线保留 UNKNOWN，首页及通知保留人工检查提示，用户确认后只清除提示不修改历史。手动萃取期间产品层阻止曲线启动、设置、断链、去皮和停服务；停止须用机器拨杆，App 不发控制帧。当前仅有合成帧单测，尚无手动拨杆真实样本；安装后需专门验证识别阈值及历史。

旧版 `brew_history_v1` 的显式 JSON 导入已接入 Alpha 历史页，独立只读页面和压力/机器水流图不会污染原生历史或控制层；详见 `docs/legacy-history-import.md`。旧版修改包已加入导出菜单；当前缺少真实导出文件，离线单测只证明格式与分享路径；不能声称用户历史已迁移。

## 控制边界

设备会话仅对HOYI固件1.1.3开放控制；Alpha启动限定三条已采集曲线包或100条经两种秤模式报文校验的工厂曲线；Lab不暴露这些控制入口。其他固件仅看已知帧/Unsupported；其他秤未实现。不要为了UI演示移除门禁。

策略阈值不是厂家认证规格：样本1.5s过期；启动后1.5s请求去皮；去皮后新样本绝对值≤0.5g确认；启动4s未确认则尝试停止；至少7s才允许目标重量停止；通常idle需距最后阀开帧>2.8s才结算。取消确定未发出的start且stop传输成功后，可由新idle结算，前提无活动帧证据。均须实机标定。

回放重量停止17.786s，旧记录17.887s，相差101ms，未证明物理等效。断线或进程被杀无法保证停液，不恢复/重放启动命令。

## 验证与审查

完整命令在README；不能只运行Gradle默认test而漏掉纯Kotlin verify。

- 协议34,991次断言，其中32,856条匿名通知（不是同等数量的独立用例）。
- 会话/策略/回放/trace共32个命名场景通过。
- App JUnit 9项通过（日志顺序/转义/轮转/重开session/IO失败/队列满/关闭/时效与单位）。
- app APK、AndroidTest APK构建通过。App lint 0错误5警告（中文诊断文案未资源化、版本提示）。
- 独立审查确认核心停止边界；独立App范围及两轮生命周期复核已完成。第二位代码质量审查启动后因服务额度中断，不能计为通过；主任务补做最终检查。
- 上述为首轮构建时状态；本轮已通过Wi-Fi ADB安装并运行，实际验证与限制以本文顶部及实机报告为准。

## 下一步

按当前代码核对的功能边界、旧历史迁移前提与高风险设置见 `docs/native-feature-status.md`。用户要求暂不安装/操作平板，实机 Alpha 验收待其另行通知；在此之前不以 Lab 验证替代 Alpha 控制验收。

1. 继续离线拆分服务中的请求协调、采样序号、超时和事务互斥；已有状态/门禁共享不代表运行时已统一。保留产品曲线证明、历史、Android生命周期和存储适配各自边界。
2. 用户恢复提供设备后按 `docs/alpha-acceptance.md` 验证Alpha；已有部分启动/手动停止证据保留，目标重量自动停止、设置回读、预热取消和断线恢复仍须补齐。先只读观察，再逐项受监护操作；常规验证使用定向logcat。
3. 取得旧版 `hy_chartLib` 与 `brew_history_v1` 的真实导出核对迁移。曲线编辑按用户要求后置；未验证用户曲线继续只读。
4. 其它秤、维护/排水/校准/OTA及缺回读的控制仍需真实协议序列与恢复证据，不因离线测试通过开放。具体覆盖与缺口以 `docs/native-feature-status.md`、`docs/coverage.md` 为准。

## 构建与资料

Java17、SDK35、min26、Gradle8.11.1、Kotlin2.0.21、AGP8.10.0。本机local.properties不提交。Google不可达时 `-PgoogleMirror=aliyun`；全局旧代理通过命令行 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 绕开，不改全局配置。首次补依赖后可offline。

APK：`app/build/outputs/apk/debug/app-debug.apk`。独立包可以直接adb install -r，不用卸载旧App。debug签名为本机调试密钥；未来签名迁移需单独规划，不能承诺任意机器产物可覆盖。

fixture：protocol-core/src/test/resources/notifications.tsv、provenance.json；device-session/src/test/resources/shots.tsv。原始私人日志仍在父目录captures，不提交。scripts/extract_fixtures.py / extract_shots.py可重新提取。

日志最多8×4MiB；队列512项；丢失/错误/淘汰显式统计。sessionId标识日志实例、ownerId标识服务实例、role/generation/token标识传输。突发进程终止可能丢队尾，统计仅当前实例，不代表所有历史的完整性证明。未推送GitHub。

旧版 `hy_chartLib` 迁移已接入修改版显式 JSON 导出和 Alpha 独立只读导入/浏览，见 `docs/legacy-curve-import.md`。源数组与分类配置完整保存，不参与 `CurveLibrary`/启动许可；真实用户文件、SAF与分享流程未实机验证，曲线编辑继续后置。

新增 `scripts/generate_legacy_curve_oracle.py`：对真实旧版曲线导出生成每个非空索引在槽位7/1–5和有秤/无秤模式的旧版报文证据，绑定导出文件与编码函数哈希。4项 Python 检查中，100条已验证工厂曲线的1200帧与既有 oracle 全部一致。尚缺用户真实导出；该证明不进入 Alpha 控制许可。

`LegacyCurveAdapter` 已支持把完整旧版曲线行严格转成原生候选 `StartParameters`，并核对工厂形状的1200帧及 `seg1FlowMode` 特例。它尚未接入 `CurveLibrary`、`MobileService` 或 `DeviceSession` 的授权路径；真实用户曲线的旧/新帧比对与参数安全审查仍是开放控制前的硬门槛。

`LegacyCurveWireAudit` 现可对同一份旧版曲线导出和旧编码器生成的 TSV 逐项离线比较：绑定原始导出哈希、固定编码器哈希、每个非空索引的六个槽位与两种秤模式；任何缺行、错序、帧差异或原生参数拒绝都会失败。用法见 `docs/legacy-curve-import.md`。Alpha/Mock各87项单测中86项通过、1项真实导出条件测试因尚无用户文件而跳过；双变体构建与Lint通过。该比较不接入BLE控制许可，用户仍要求暂不安装。

Mock 版新增纯内存设置回读：常用机器设置、单日睡眠计划、累计杯数重置、去皮、立即睡眠会即时改变模拟快照与页面状态，不发送 BLE；萃取中和模拟入睡后阻止设置与去皮。开始模拟萃取时重量归零，结束后保留最终模拟重量。此前 Alpha/Mock各92项单测中91通过、1项真实导出条件测试跳过；双变体构建通过，Lint 0错误、2警告。此功能只为 UI 操作链路，不能作为真实机器行为证据。

Mock 工作室预热现用纯内存温度斜坡驱动同一个 `BrewPreparation` 状态机，约5秒到目标，可取消；到温后仍需再次确认模拟萃取。预热中禁止修改模拟设置，所有操作继续不创建 BLE Hub。Alpha/Mock各95项单测中94通过、1项真实导出条件测试跳过；双变体构建通过，Lint 0错误、2警告。此路径只验证 UI 和业务状态变化，不代表真实咖啡机预热时序。

增加了128条确定性合成自定义曲线的旧版编码测试资源，六槽位、有秤/无秤共1536帧经过原生 `LegacyCurveWireAudit` 核验，包含首段流量模式及重量取整边界。Alpha/Mock各96项单测中95通过、1项真实导出条件测试跳过；双变体构建和Lint通过。合成曲线不是机器安全参数；真实用户导出仍缺，导入曲线控制继续关闭。

记忆秤重连现在向 `OpenHoyiMobile` 输出低频、无地址的 `scale.auto_reconnect.window_open=600s`、`attempt=N`、`window_closed` 事件；设备 Ready/失败仍由既有状态事件记录。其它诊断详情继续以通用文案显示，避免把连接地址带入 logcat。实机核验顺序和精确 logcat 过滤命令见 `docs/alpha-acceptance.md`；用户尚未授权安装。

Alpha 首页与实时萃取页的“立即停止”入口已移至固定底部栏，只在本 App 发起的萃取仍需处理时显示。停止请求处理中或断线结果未知时保留可见但禁用，并给出原因；机器拨杆手动萃取仍使用机器拨杆停止。小屏、系统导航栏和实机点击仍待安装后核验。

BOOKOO 前台自动重连修复一个状态机缺口：连接尝试若以 `DISCONNECTED`（而非 `FAILED`）结束，原策略会保持 attempting，10 分钟内不再重试。Hub 现在在连接调用返回后的轮询中观察终态，避免误把 `connect()` 内同步的 DISCONNECTED 重置当失败；成功回连后重置失败退避并留5秒防抖，不支持设备停止自动重试。仍需 Alpha 实机验证断链和地址变化行为。

本轮既有首页复用修正：Mock AndroidTest APK、Mock单元测试和Lint离线构建成功（/private/tmp/hoyi-language-existing-home.log，BUILD SUCCESSFUL，107 tasks）；脚本bash -n及diff --check通过。独立只读审查确认首页引用/恢复顺序和原断言保留，无生产改动。运行验证仍待新云端CI。

当前b20b90f首页引用修正已推送，Verify native app36948773115与Mock36948773151启动。CI脚本另加每条隔离Mock instrument 600秒失败上限，保存阶段stdout并触发已有定向诊断；bash/Python语法检查和独立复审通过。未宣称Android运行检查通过。
