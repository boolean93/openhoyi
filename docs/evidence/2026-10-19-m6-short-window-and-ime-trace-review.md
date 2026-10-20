# M6 短窗与 M3 原生追踪诊断复核

审查基线 49a1b46 / PR 实际 merge 46e2dbc79ca0732290db92b5c78ad097c82c86cb。只读原件与代码；不宣称新修正运行成功，不改变产品 IME 行为。沿 feature/task-first-ui → develop PR#4、docs/git-workflow.md，不设备操作、推送、合并或发版。

## M6：测试坐标错误与真实裁切须分别关闭

原件：`build/task-first-evidence/ci-49a1b46-scale/raw/raw-device/46e2dbc79ca0732290db92b5c78ad097c82c86cb/tablet-user-landscape-light-font-1.3/scale/failure-context/`。已实看 window.png 与 geometry.json。

趋势入口 screen rect `[1832,984,2524,1092]`、globalVisibleRect `[1832,657,2524,765]`，两者均高108，差恰为实际 appOriginY327。当前 visibility 将根坐标与screen坐标比较，是该 target not completely reachable 的测试错误。必须通过同一实际root的global offset与screen origin转换再比较完整位置和尺寸；不能只比较宽高或为该按钮豁免。raw/global、root offset、screen origin和转换后矩形全部留证。TextView全视图可见检查同步使用相同转换。

独立生产缺陷：primary available528，reading自然374 + tare含margin126 + explanation79 =579，差51。实际 reading框高323；freshness框 `[216,877,1778,887]` 高10，真实Layout高61，裁51。tare本体108（另18margin）和capture126都完整。不能因修复selector误比较而关闭真实说明裁切。

生产最小规格（根指定owner后实施，限ScaleActivity及必要本页layout）：当 reading/tare/说明真实自然高溢出，且当前重量label与既有quantity的真实自然宽+8dp gap可以同排容纳时，将当前重量label与number/unit quantity条件同行，保持永久子树。辅助14、值64/单位22、quantity原生baseline、相关8dp均不降。该夹具原label61+18竖gap可回收79px，不能硬减51。freshness保完整61及原间距；tare/capture/状态说明不删。足高保持原label在大数字上方构图；不足宽仍保持纵排并记录无法容纳预算，不猜缩字。右栏、时钟/计时、service/tare/capture控制门禁不改。重测该420dp/1.3捕获短窗及正常1.0/1.3，确认所有重要节点完整；新Window协议不能替代此布局复验。

## M3：UiAutomation Shell transport 封闭修正

已读本机官方 API34 源码：`android/app/UiAutomationConnection.java` 的 executeShellCommandWithStderr 使用 `Runtime.getRuntime().exec(command)`；`android/app/UiAutomation.java` 的 executeShellCommandRwe 返回 `[stdout, stdin, stderr]`。因此字符串 `sh -c '多词脚本' 2>&1` 不能假定得到宿主shell解析；现有空输出/no marker与此机制一致，仍须实际probe验证。

仅修 `ImeRecreationTrace.kt` 与 M3 host runner capability probe；不改生产 IME/focus/show/hide、原恢复断言、等待期限与失败原件。

1. 使用固定、无插值进程名 `/system/bin/sh` 调用 `executeShellCommandRwe`，通过stdin写UTF8脚本并flush/close stdin产生EOF。命令正文仅现有内部固定allowlist，绝不拼入用户输入。脚本执行单一目标命令，紧随保存真实 `$?`，输出每次command唯一nonce的严格rc marker；不以echo/printf自身rc当目标rc。
2. stdout与stderr并行持续读取各自有界缓冲，保留原始分流输出、字节数与退出marker。writer和两reader不得在UI线程，全部从同一真实SystemClock截止时间计算remaining，不能每个future另给10秒累加。达到当前131072输出总上限或超时即失败，不能默截后解析成功。
3. 任意失败/超时关闭全部3个PFD，取消并join本次IO worker；处理Binder迟到返回的三个PFD关闭竞态。任意worker未终止继续标diagnostic fail/not cleaned。关闭PFD不证明远端命令已终止，不据此推断trace未开始或可以停止。
4. 正常success必须：stdin完整发送并关闭、stdout/stderr读取结束、仅一个当前nonce合法rc marker、真实rc符合该命令契约、全部worker终止且预算未超。先用确定stdout+stderr+非零退出码的无副作用probe验证transport（例如固定脚本打印两流并以7结束），再做uid/build/help；probe失败不启动trace。
5. `help`是独立契约，不能把所有非0全放过。已看到host rc255/完整帮助，须核此模拟器Android版本对应InputMethodManagerService/ShellCommand实现是否确切定义help返回-1；只在源码确认且输出包含完整所需tracing子命令、transport marker有效时，为help单独接受该已证返回值。uid/build/start/stop保持原严格rc0。若尚无源码证据，help保持DIAGNOSTIC_UNAVAILABLE，不简单改成contains即可通过。
6. trace owner语义不放宽：只有本次start的rc0及原精确“Starting tracing …”确认才设置ownedStart；already-started/空输出/超时/未知一律不取得所有权、不停止别人trace。先原子记录开始意图和nonce，成功再原子记ack；若start可能已执行但ack丢失，标unknown ownership，不自动stop或重发start。host finally仅在完整可验证本scope owned ack且尚未attempt stop时执行一次原清理。
7. stop必须本scope owned且仅一次，保存真实rc/精确ack，再检查实际三个proto的存在/非空/大小/哈希及pull完整性；ack不等于文件已拉取，空文件/无trace不证明无IME事件。原三条追踪记录仍按真实elapsed+wall标记关联，不把未取到proto推成产品IME根因。

本记录给出可实施诊断规格，不预先认定help返回255是成功。根需协调该helper唯一owner，避免与Window helper、M5图审文件发生冲突。验证通过只证明诊断transport可用，不关闭M2/M3真实IME恢复FAIL。

## M6 条件同行候选静态复核

已核 ScaleActivity/ScaleVisualStyle 当前冻结diff：先测原纵排reading+tare+说明真实预算，只在合计溢出且label+8dp+quantity自然宽足够时同行；原永久quantity保持数字/单位基线、64/22角色，freshness独立自然高度+8dp，足高仍纵排。未改右栏/计时/控制和门禁，无硬编码51px。候选可编译/取证，真实51px裁切未据此关闭，需新PNG与完整freshness Layout、screen rect证明。

M3 help受限语义：官方 Android14 IMMS onCommand default→handleDefaultCommands→BasicShellCommandHandler help return -1 可说明 shell255。只允许help在完整帮助、nonce/transport/EOF/线程退出均满足时接受0或255；其他命令0。该依据是官方相应分支语义，不宣称CI framework二进制逐字认证。

## M3 Rwe 冻结候选静态审查（待两处收口）

固定 `/system/bin/sh`、Rwe 三路 descriptor、stdin EOF、双流并发、单次真实总 deadline、合计131072 cap、迟到 binder descriptor 关闭握手与 worker 未终止 fail-closed 均符合本次诊断规格。help 的0/255只在完整帮助、严格transport和空stderr下生效；其他成功命令仍rc0。17项本机host离线结果不是Android API、实际native trace或IME根因证据。

必须收口：

- `collect_ime_recreation_trace.py` 的 `after` fallback-stop 仍使用旧 `call(adb shell cmd...)`，且只检查目录前缀substring。将固定stop命令加入既有allowlist，经同一个 `shell_command` 取得nonce/双流/EOF/预算/真实rc；`shell_ok` 加完整三文件精确ack。保留同scope identity核验、ownedStart和未attempt前提、fallback意图先persist、只尝试一次。失败不重发、不停未知trace。
- Android start/stop 的 `any { trim()==expected }` 应收紧为非空trimmed行唯一expected，避免有效确认行与already/矛盾输出混合时仍认ownership；host fallback同契约。如实际官方输出另有合法行，须列明逐项allowlist，不采用substring。
- 附加解析一致性：两端目前只计当前nonce，foreign nonce alone会失败，但foreign加合法terminal仍可能被忽略。拒绝stdout内其他 `IME_TRACE_RC_` 行；补混合foreign+current终端负例。保留raw stdout/stderr，不能过滤后伪装正常。

最小修改仍仅 `ImeRecreationTrace.kt` 与 `collect_ime_recreation_trace.py`；不改生产IME行为。统一编译运行期间先不修改，待root释放。

M3 收口复核：已实际阅读最新两文件，Android start/stop与host fallback均唯一完整ACK；host fallback已走同allowlist shell_command；两端混合foreign/current marker拒绝。原scope、意图先落盘与只一次stop保留。本轮静态阻断关闭，可编译并运行真实CI探针；25项本机host离线结果不替代Android Rwe、proto或IME行为验收。

## 2026-10-20 M3 原生 trace 离线分析封闭任务

本次实际读取 `ci-bcc64c1-curve/raw/ime-native-traces/light-1.0-import` 三原件并重算SHA，与stopped-device records一致：clients107858字节/42事件，service10167字节/14事件，manager2365字节/5事件。三个envelope共享realToElapsedOffsetNanos=1792495943001068967；实际测试merge8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249，run39414723962。Rwe probe/start/stop收到完整ACK，非旧空输出。

完整性仍有边界：collection-complete实际PARTIAL_OR_UNAVAILABLE，客户端min44.942367724/max46.046876631秒未包住recreate44.916661341/比较46.405935354两端。事件驱动trace没有端点事件不自动证明截断，但也不能直接把collector改PASS；三文件存在、SHA正确只证明原件可分析，不证明每时刻状态都被采到。

已独立读envelope排序：manager hide44.935470505→service hide44.936293876；manager startInput45.244204594→show45.246424124→service show45.247343845；client hide45.276536059；client notifyHidden45.386272340→manager hide45.386429857；client showInsets45.501294770、service hide45.503313206、client hideInsets45.505835865；client notifyHidden45.920581995→manager hide45.920710919。仅证明真实show/hide交错，不证明应用主动hide或具体根因。

交Sol只读任务：从官方Android14 tag解析不可变commit，取inputmethod trace及递归import proto，保存URL/ref/SHA并与SDK34 dumpDebug字段链核对；不得以strings/猜field编号当正式解码，也不把参考源当CI二进制认证。完整解析三文件全部entry与unknown fields；用同设备elapsedRealtimeNanos排序，offset仅wall换算，不混host/mock clock。对齐trace-state marker和产品事件前先核clock/单位。提取schema实际存在的旧新window/root/inputConnection/editor package/fieldID/servedView、焦点、requested/current visibility、control target、animation、show/hide flags/reason；缺失值保unknown，不把proto absent当false。

沿每个where对应SDK/AOSP调用位置核dump发生在动作前还是后。尤其核第二次hide请求主体、旧新token关联、show回调与隐藏通知关系；跨进程先后不是因果证明。输出三decoded JSON、统一timeline JSON/MD、schema来源与完整性限制到build evidence。结论分observed/source-inferred/unknown；未判明请求主体前不改生产，不盲加show重试、不放宽scroll恢复断言。若字段不足，提出下一次最小诊断采集，不伪造本次结论。

## 2026-10-20 M2 editor-stage-hidden-values：独立下一诊断

依据 `build/task-first-evidence/ci-a47c870-frame/m2-offline-diagnosis.md` 及其指定原件，run39417690223/source12de379c8bc98ed1d309d04c8e73edf09b3cae87：11字段、selection、focus、draftId相同；IME true→false、content高680→1391、scroll662/range662→0/range0。原严格相等断言仍FAIL。结果码2及单次show返回true不证明最终IME可见；合法range0不证明存储/clamp缺陷。与M3外观相似不构成同源证据，不改产品恢复策略。

批准复用已静态冻结的 M3 因果工具，但为**独立 M2 单次重建 scope**，不扩大矩阵：

1. 最小业务源文件 `TaskCurveEditorForm.kt`：只给现有恢复 evidence 的 show_request、show_result、动画、scroll-apply 事件追加真实 elapsedRealtimeNanos/pid/tid、Activity/field/decor进程内身份、field id/token identity与原toString（null明确）。现有 guard、单次show、ResultReceiver控制流、监听顺序不变；不增加requestFocus/hide/show重试/等待，不为诊断读取改变UI。不得重构恢复逻辑或移植M3产品策略。
2. 最小测试文件 `AdvancedBusinessUiChecks.kt`：只在现有 editor-stage-hidden-values fixture 包围其一次重建，复用 ImeRecreationTrace 的已审观察接口。原setup show/recreate前后/原before-after断言/实际cleanup或unfocus执行点，使用M2独有callerSite。UI线程分别冻结旧、新实际field/decor/token引用，输出===比较；null记unknown，不等待token来满足诊断。产品事件在UI线程深拷贝；原有限scroll fixture、三帧采样及严格字符串等值断言不得改变。
3. trace放该fixture独立子目录，检查没有其他owned session再启动；只由本scope stop一次，finally移除观察器，先保存原失败后保留诊断异常，不覆盖原断言。复用helper已有write失败隔离/ownership/PARTIAL语义，不另开系统全局debug、不hook隐藏API。原runner若已整目录保存无需改；仅确有缺口时追加既有失败artifact拉取路径，不改通过条件。
4. M2 sidecar明确引用本次原始native三件/时间基准，不能引用M3 request-tag当本次关系。window token/app identity/IME token/request tag分列；同进程===可证明对象关系，跨进程只保原值，近邻仅temporal_candidate。旧新窗口可能保留，不能要求token必变。
5. 若现有helper接口可直接复用，不修改 ImeRecreationTrace；若需扩展先报精确接口差异，避免破坏已冻结M3消费者。仅编译验证，后续由根安排原M2 CI；不单独设备运行。

收口判定：若直接证明show关联旧/失效field或测试cleanup发生于断言前，只针对被证明路径另定修复。若show对象确为新field且cleanup未执行，则排除这两方向，继续明确framework请求归属unknown；不得因M3类似而加重试或延长等待。新增sidecar不升级collector PARTIAL，也不使M2恢复FAIL变PASS。

### M2 独立因果诊断五文件静态终审

Astra 已核原 finite scroll、三帧采样、before/after 严格相等、show 单次 guard、ResultReceiver/scroll 应用条件未改；未新增恢复重试、hide、requestFocus 或成功等待。M2仅 light1.0/curve-editor/editor-stage-hidden-values 原场景创建独立trace，产品现有事件补真时钟与身份；旧新field/decor/token用引用===，主线程深拷产品事件，finally移除观察器再finish，原业务失败优先。

ImeRecreationTrace 的 M3 默认 fixture/capture caller/prior引用及mark/observe默认保持；M2 prior=null并注明无同scope原native trace。collector仅追加精确M2路径allowlist，原M3路径及ownership/nonce/stop/fallback/PARTIAL保持。runner在原单scenario前后及失败finally获取native三件，没有另起scenario或sidecar-only降级。

终审两处窄收口已核：成功等值断言后的原capture调用前后补marker，用于标识测试副作用调用边界（**不是**内部hide精确执行时刻）；collector异常诊断文件写入另行保护，不能因次要落盘错误覆盖原业务失败。循环后的capture仍只发生于原断言之后，原失败时不会为取图先unfocus。

最终审查SHA256：

| 文件 | SHA256 |
|---|---|
| TaskCurveEditorForm.kt | c1591707fb8eb7d02709a48264273aa57e2ea282f11f5d47b164c19ace7c49df |
| AdvancedBusinessUiChecks.kt | e4e6498c48a3d476e7abfefd5fece9461fa69daadc5f4217d79d20793e1aacc4 |
| ImeRecreationTrace.kt | 09d753285e4a4d825cd13211ca157321dd21b665e34531e500855ca1b83b2a47 |
| check_form_visual_migration.sh | b7f022a46ea03f153df3f6456f79a80175c0ceec9f3754199732893195a255e9 |
| collect_ime_recreation_trace.py | fac2cc0ec54595f34ccb58feed2369f2768e658d78a09faa2fcbf2c4aa0fd829 |

静态无剩余阻断。已读前一冻结稿 assembleMockAndroidTest 31s成功日志；两窄收口后最终编译由根/owner协调，不能以旧日志冒称新hash已编译。本审查 bash-n/py_compile/diff-check通过。新CI尚未取得因果记录，M2原恢复FAIL与M3原PARTIAL均不升级。
