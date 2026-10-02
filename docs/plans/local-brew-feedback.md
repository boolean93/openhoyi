# 原生本地萃取提示与音频

目标仍是原版完整本地提示体验，不以试听或播放器类替代结束提示。使用Kotlin/Android，无UniApp/JS运行时。控制与提示独立，0x21灯效不开放。

分层计划：
1. 本地音频目录及两段播放：先1.mp3/2.mp3，再四条相应英语语音之一；切换请求取消旧播放，旧回调不得推进新请求。自然结束/失败清理，显式停止或音频焦点丢失终止串播。Android MediaPlayer与AudioFocus只依赖Application资源，不接触Service/Hub/命令。
2. 依据同杯0x80秒数/累计流量及曲线preinfusionSeconds记录提示数据；显示启发式按旧>2.2/≤1.5等级，不等同秤重或质量。未知结束、缺同杯快照、断线及异常数据不误报成功。只读结果不能改变启动/停止/恢复资格。
3. 本地默认关闭偏好、设置Switch/试听、结束提示接入Home/Extraction与手动杯；已展示记录跨Activity重建不重播，离开页面或新杯时停止本地音频，原有立即停止入口不变。
4. 新UI资源完整同步八语言；Mock结束全链与模拟器音频回调/焦点失败验证。真实机器结束/后台体验留待用户恢复设备。

第1项实施范围为播放器，不宣称完成提示功能或原版体验。复用用户提供旧目录的14个音频文件，记录独立文件SHA256与旧模块位置。构建产物核对资产未压缩，确保AssetManager.openFd可用。纯播放器测试先失败，覆盖顺序、取消、过期回调、同步回调、播放失败及焦点中断；独立审查、双变体编译/回归后提交。

第1项本地验证（2026-10-01）：纯播放器5项、资产SHA1项新增；Alpha/Mock各151项（150通过、1缺真实用户导出跳过），完整协议/会话/共享回归、三应用APK和Mock instrumentation APK编译、双Lint通过。19Python、正式资源生成检查及两APK各6969资源一致；两APK14音频均ZIP_STORED，字节/SHA与原资产证据一致。独立增量审查无实质问题。

新增只在Mock包执行的内置Android instrumentation，先确认MOCK_MODE和包名，再测试prepare立即取消、逐个14资产完成回调、实际两段串播；接入云端Mock生命周期工作流。取消观察仅300ms，焦点竞争/后台中断仍未验证；编译通过不等于Android运行时通过，云端结果另补。没有生产调用方、设置、提示判定或结束弹窗，用户完整提示功能仍未完成。

第2项接入约束（旧源码78345–78430、78108–78153及7740–7815已逐段核对）：
- 原版早期快照仅在isExtractingPhase且机器秒数≤10时更新；该判定排除brewWait，普通槽位压力>1bar或流速>0.8ml/s，6/7槽位阈值更低，也接受3秒内秒数/累计流量增长。不能把所有0x80或预热回报都当早期萃取。
- 原版结束提示由450ms无活动驱动；原生不新增这类设备结束判断，等待现有ENDED_OBSERVED或被动萃取明确结束后只读投影。未知结果/断线/IDLE清理不显示完成提示。
- 独立记录器在同杯begin时清空早期/最终快照，绑定shotId及选定参数preinfusionSeconds。机器秒数与累计流量使用0x80原字段，不能使用ShotPoint.elapsedMs或scaleWeight替代。乱序/回退/跨杯/缺早期快照保守拒绝提示，不能像旧JS一样让NaN落到level0。
- 当前service真实帧路径recordMachinePoint、Mock路径mockTick直接series.machine、手动路径被动Started两帧以及自发/Mock begin/finish都需逐项接入。不得遗漏Mock或用历史是否成功保存作为设备结束事实。记录器异常/提示失败不得阻断finishSeries、恢复记录清除、停止或样本保存。
- 本阶段先实现纯记录器和旧公式独立对照，后接入Service；偏好/弹窗/音频只订阅只读结果，不调用Hub或协议API。

第1项云端运行时结果（0942650）：Verify native app 36865762452与Verify Mock lifecycle 36865762552均success。读取后者mock-lifecycle日志artifact，audio-instrumentation.txt含LOCAL_AUDIO_CHECKS_PASSED clips=14 cancelledPrepare=true sequence=true，确认14资产逐个实际解码/完成、prepare取消无回调及两段实际串播。theme-recreation.json为1→2，生命周期检查通过；后台配置destroy仍false，不冒称通过后台配置重建路径。Android34无音频输出的模拟器不证明人耳听感、真机解码兼容性或真实焦点竞争。此结果只完成播放器阶段，第2–4项仍待开发。

第2项开发与离线验证（2026-10-01）：BrewFeedbackTracker绑定shotId/遥测阶段/预浸参数，保留同杯早期/最终0x80量与机器秒数；旧ID/乱序忽略、跨阶段/计数回退/非法量/缺早期拒绝，finish只接原有结束证明。BrewFeedbackCompletion纯显示gate要求READY/明确无告警/已观察结束，安全原因停机不鼓励；被动杯不读取之前自发杯的stopReason。

Service实际自发、Mock和被动phase6均已只读接入，新杯/未知/非READY/销毁清空，计算失败不阻断历史/恢复/样本；未知外部曲线预浸参数不猜测。三份旧采样确认命令packed slot7全部回报phase8，自发提示与Mock据此映射；phase8使用旧普通阶段阈值，不能误当7的低阈值。其它固件未经验证，异阶段仅抑制提示。没有改启动/停止报文或门禁，尚无UI/偏好/音频订阅。

新增16项JVM测试，Alpha/Mock各167项（166通过、1真实导出缺失跳过）。其中原始旧JS函数在隔离Node VM执行生成1893等级与1024 phase案例，锁定源码SHA；generator二次生成逐字节相同。完整协议/会话/共享回归、三应用APK/Mock instrumentation构建、双Lint、19Python/生成检查及两APK6969资源通过。Service整文件可由494bb88按显式声明修改正向重放一致；多轮独立审查无残余实质问题。采样/源码证据见docs/evidence/local-brew-feedback-tracker.json。

Mock instrumentation新增真实产品Service start/stop调用，检查phase8、自然结束摘要、下一杯清空和早停无摘要；已编译，云端运行结果待补。它不接Alpha包，两个Mock guard在任何测试操作前执行；此阶段不证明真实机器或完整结束提示UI。

复现旧源码oracle：`python3 scripts/generate_brew_feedback_oracle.py <app-service.js> /tmp/legacy-brew-feedback.json`；与mobile/src/test/resources/legacy-brew-feedback.json逐字节比较。Node只用于离线开发取证，不进入Android APK。

第3项具体接入方案：
- 设置增加独立“应用偏好”卡片，萃取提示默认关闭，写本地SharedPreferences成功后才更新开关；不受咖啡机连接状态影响，也不加入机器controlButtons。试听显式点击才播放，并标明英语提示音；不提供灯效写入开关。
- Service继续只提供只读摘要，另用纯展示投递模型在结束时记录该杯开关状态。关闭状态的杯不因事后开启而补弹；新杯/断线/未知清空待展示项。展示claim原子消费一次，不能因Home/Extraction同时刷新或Activity重建重复播放。
- 只由Home/Extraction在可见且未开始下一杯时领取结束提示。弹窗关闭、离开页面或本地开关关闭立即停止该页面音频；页面重建若恢复弹窗只恢复同杯文字，不重新领取/播放，不保存或重放任何BLE动作。
- 设置试听和结束弹窗各自有播放拥有者，销毁取消不能停止后建页面的新请求；全部播放走Application资源和已验证Android驱动。异常只影响音频/展示，固定“立即停止”、恢复警告和控制请求不经过投递或偏好类。
- 新卡片/提示资源八语言完整同步并验证APK实际模板；Mock instrumentation验证真实设置保存、Service结束→领取→一次展示、关闭/旋转/新杯不重播，再补音频焦点竞争与后台中断。设备、语言入口和真实控制验收不能用这些Mock结果替代。

第2项云端证据（e031ab9）：Verify native app36870318401与Verify Mock lifecycle36870318230均success。途中gh观察遇EOF，重新查询同一任务确认仍运行，没有重启验证。已读取Mock日志artifact，保留音频14资产/取消/串播成功标记，并新增LOCAL_FEEDBACK_SERVICE_CHECKS_PASSED observedEnd=true telemetryPhase=8 clearedOnNextCup=true earlyStopSuppressed=true；真实Mock Service产品API自然结束摘要、下一杯清空、早停无摘要得到Android34运行时证据。生命周期通过、主题配置stop1→2；后台配置destroy仍false。UI/偏好/自动提示音还未接入，安全停止门禁仅有JVM与静态证据，真实咖啡机与用户操作未验；下一阶段仍按第3–4项开发与验证。

第3项本地开发与验证（2026-10-02）：默认关闭的应用偏好卡片、试听及Home/Extraction结束弹窗已接入。纯偏好模型只在磁盘写入成功后更新进程状态，观察者异常不阻断其它页面；关闭立即清空投递并停止所有已订阅播放器。结束投递绑定该杯开关状态，消费后不重发，事后开启不补弹。Activity配置重建保存同杯文字/语音选择并停止原播放，恢复弹窗不自动重播；新杯与不符合展示资格时关闭弹窗。提示渲染/窗口/点击播放异常隔离，不能中断原控制页面渲染。展示是至多一次领取，若展示失败不保证该杯必定可见。

新增10项纯模型测试。最终完整协议39,823检查/32,856通知回放、会话92场景及共享模块回归、三APK/Mock instrumentation编译、双Lint通过。Alpha/Mock各177项（176通过，1真实用户导出缺失跳过），19Python/资源生成一致性/两APK各7,081字符串与6转义round-trip通过；八语言各新增14个提示键，不改变既有文字。生产协议/蓝牙/会话模块无改动，Service仅增加本地展示投递与偏好订阅，不改变启动/停止/恢复/门禁。

Mock instrumentation已增加真实设置Switch持久化、Service自然结束→Home弹窗、重建只恢复文字不增加自动播放请求、新杯关闭旧弹窗检查；编译成功不代表Android运行时通过，云端结果待补。焦点竞争、后台中断、真实听感/页面视觉、Extraction及被动手动杯展示运行时与真实机器控制仍待验。上轮自动审批因账号额度未执行最后构建；本轮正常审批恢复，最终构建已完成，没有绕过审批。

独立最终静态复审未发现实质问题，确认Service增量不改变协议/恢复/停止；明确剩余运行时缺口包括播放中关闭偏好、Extraction生命周期与窗口异常注入，当前云端检查不能覆盖这些项目。
