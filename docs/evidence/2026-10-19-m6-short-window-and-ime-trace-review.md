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
