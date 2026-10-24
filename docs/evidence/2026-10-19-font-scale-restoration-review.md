# font_scale 原缺省恢复：调查结论与隔离探针规格

本轮根保存官方android14-release源码与SHA在 `build/task-first-evidence/font-scale-source/`。ATMS的font URI observer在读不到该键时取1.0；global仍为1.3则updatePersistentConfiguration，后续同mH异步sendPutConfigurationForUserMsg最终Settings.System.putConfigurationForUser明确putFloat FONT_SCALE。因此delete后重现1.0不只是可能的旧写回，delete本身即可触发新写回。SettingsProvider读取未见font特殊物化；am get-config输出不含fontScale，不是比例或事务完成证据；app/broadcast barrier没有证明覆盖ATMS mH。严格absent与1.0不得等同。

## 下一CI独立能力探针，不是已修复

当前工作流-no-snapshot仅禁止自动load/save，不能据此判定手动snapshot不可用。本机无emulator二进制，需guarded disposable CI实测。官方QEMU参考提交4f8bb7a074c4cd727acfdfa1e7bb092a2480696c同步stop/save/start实现并非当前CI二进制认证；源码skip分支可能返回非零布尔且打印cannot，必须核原始诊断，不能仅OK。

1. 仅GITHUB_ACTIONS+实际qemu+唯一serial的本scope可丢弃emulator；首个font写入前保存本run唯一完整AVD snapshot。保存原settings list exact-key存在性/字符串与真正framework Configuration、serial/镜像/build身份。
2. 保存console stdout/stderr/返回码及snapshot list。拒error/fail/cannot/skip、未知响应或非唯一exactname，不将exit0/OK独立作成功。不能删其他snapshot。
3. 所有退出路径先将本scope原始证据拉至host并验证，再finally单次load自有snapshot。save/load能力或结果未知即FAIL，不fallback循环delete/重复load/默认等价放行。
4. adb重连等待只证明连接，不证明持久事务。load完整响应无错误后以真实framework Configuration与settings exact-key精确核原值和原存在性。恢复全VM settings/globalConfig/队列，避免竞争删除单键；不得以get-config作ACK。
5. 仅确认load与证据拉取后删除本run自有snapshot；load失败保留诊断。能力探针先证明本CI二进制支持，随后再由root指定单owner接共同runner helper，避免五份独立cleanup。原应用fixture/记录隔离门禁和失败退出码保持。

尚未运行探针，font_scale恢复严格FAIL保持。该方案不适用于用户设备，不以Mock替代硬件或将未知设备恢复当成功。

## 独立probe初版静态审查：尚有阻断

已读 `probe_ci_font_scale_snapshot.py`。需修：真正配置从官方 `dumpForProcesses` 的 `mGlobalConfiguration: `读取，不是settings/等号格式；transport serial不等同ro.serialno，分别留证，load前后核唯一inventory、qemu、exact AVD、build fingerprint/SDK/hardware与原ro.serialno。只有save成功、exact列表确认、host完整证据拉取校验全部成功才能单次load，不能按save_attempted对未知save/pull失败load；这些失败零load/零delete并保留现场。save/load/delete意图先host落盘，attempt和ACK分离。snapshot列表未知格式failclosed，不猜能力；baseline已有font键时最多能力通过但absent未证明。SHA递归覆盖拉取原件，不仅顶层。负例必须证明未知save/缺list/拉取失败零load、identity变化或恢复不等只一次load且零delete。已交原owner收口，未接任何runner。

## probe修正版静态收口

已读最新版probe/test/workflow：官方processes冒号配置解析、transport与ro.serialno分开、save/list/pull三前提、load前后完整identity、host fsync intent/ACK分开、未知save或pull失败零load零delete、递归SHA、present基线只CAPABILITY_PASS_ABSENCE_NOT_PROVED均收口。9项离线测试覆盖解析与cleanup契约，不证明Android snapshot能力。静态无新阻断，可运行独立guarded CI能力探针；不接原runner、不关闭font_scale严格恢复FAIL。

触发补充：允许独立probe workflow新增pull_request目标develop，随下一正常PR合批触发，不为探针微推；保workflow_dispatch、只读contents、唯一run/attempt/job AVD、独立guard、25min和always artifact。不依赖或修改原视觉runner，不改变验收契约。

## bcc64c1 后空列表真实输出适配窄审

读取 `ci-bcc64c1-snapshot/raw/013.command.json` 及原始stdout/stderr/exit：命令emu avd snapshot list，stdout字节为 `There is no snapshot available.\nOK\n`，stderr空字节，exit0。新增空列表分支匹配该真实语句，要求terminal OK，且空结果不能与未知行、错误或实际snapshot行混合；未修改save/load/隔离/恢复语义。它只修探针前置解析，不证明save/load或font恢复能力。

窄审发现继承guard仍用 `not result.stderr.strip()`，实构造空白stderr ` \n` 也通过，与本轮精确空stderr契约不一致。最小收口只将snapshot_names guard改为 `result.stderr == ''`，补空白stderr负例；其余原流程不扩改。已报root，当前等待此单点收口。

空stderr单点已收口：已复核snapshot_names要求rc0且stderr严格空字符串，新增空白stderr负例；本审查实际运行10项离线单测全部通过。当前两文件diff仅真实空列表语句适配与该严格守卫/负例，符合原探针边界，静态无剩余阻断。probe SHA256 `e99d77296a7ec3fccab9f8340e76bdc639ea3c59979c0c2311a060a1164f5d58`；test SHA256 `ceda4fa6ec9d5de50f5ddc138b9ce1b9390838917b04417787b295af6ef6bc90`。仅解析/离线契约通过，save/load和font_scale恢复能力仍未验证。

## 2026-10-20 a47c870 后真实 `--` ID 格式窄审

Astra 已读 `build/task-first-evidence/ci-a47c870-snapshot/raw/015.command.json`、stdout、stderr 和 exit。实际命令为 `emu avd snapshot list`；列表行是 `--        font_probe_39417689944_1_probe    67M 2026-10-20 11:50:27   00:00:28.221`，末尾 `OK`、stderr 为 0 字节、exit 为 0。

本次 diff 仅将表格 ID 从数字扩为数字或实际观察到的 `--`，TAG 仍精确解析；未知 ID、异常行、数字/`--` 混表、重复名称、非零 rc、任意非空 stderr 及缺失末尾 OK 仍拒绝。save/list/pull 授权链、单次 load、身份与精确恢复检查均未改变。独立运行 `python3 -m unittest discover -s scripts -p test_ci_font_scale_snapshot.py`：11 项全部通过；`git diff --check` 通过。静态无剩余阻断。

本次实际核对 SHA256：probe `4aa03c06f0b37f3330f0955e25ad6076ec565afc2bf7a42e27e120ac6fd4a3b9`；test `9ffe2ed6d3b8aa22c61afb4ddb1ab7cf12d3558b0dd10ca01fb43a8b5d65884f`。这是原始响应解析的适配与离线契约验收，不追认该失败运行的 saveVerified/loadAcknowledged，也不证明 font_scale 恢复能力；原严格恢复 FAIL 保留。

## 2026-10-20 dc5875c 成功能力原件与现有runner最小接入计划

Astra复读 `build/task-first-evidence/ci-dc5875c-snapshot/raw/` 的 result、operations、restored settings/configuration，并独立重算result引用的186份文件SHA，全部一致。run39422164320/artifact12265061384；ZIP SHA `bcf78c70bcfcfc2a6c3993a6d6eaf3e04a955ed598da631741ad1b2780c2b9be`（root已核187 CRC）。baseline为key absent/framework1.0，实际变更到1.3，模拟业务失败证据先pull到host，save/load/delete各一次有ACK；load后原settings无font_scale，framework真实mGlobalConfiguration为1.0。**这证明该次独立disposable AVD的精确恢复能力，不证明现有视觉runner已接入，不追认任何旧清理FAIL。**

### 分批最小范围（此节仅批准实现计划，尚未分派或修改）

先接现有 `scripts/check_scale_visual_migration.sh` 及 `.github/workflows/scale-visual-ui.yml`，加一个专用host事务helper及其离线契约测试（建议 `scripts/ci_font_scale_snapshot_scope.py` / `scripts/test_ci_font_scale_snapshot_scope.py`）。不修改产品、Instrumentation、视觉5s协议或运行配置。不先改正在补Extraction的M5 runner，不一次改所有流水线。parent安排owner/文件锁后实施，首个集成CI证明成功与业务失败两条退出路径后，再将相同薄适配顺序接M4/M5/M6之外原有修改font_scale的runner；每个原业务集合不删减。

复用已验证probe的严格console ACK、snapshot list解析、state/identity与单次intent机制；禁止直接执行probe.run冒充业务事务（其自身会改1.3并制造失败）。需要复用公共函数时保持独立probe语义不变，新增文件所有权由parent一次协调，不复制后放宽解析。

### 事务顺序与不可省略条件

1. **唯一disposable范围。** workflow显式新建每run/attempt/job唯一AVD（`force-avd-creation: true`），配置与host guard绑定；不得仅凭GITHUB_ACTIONS=true或qemu=1授权。保留唯一ADB inventory、transport serial、实际AVD exactname、qemu、SDK34、hardware、build fingerprint及ro.serialno身份检查。非指定CI/非唯一设备/身份缺项直接拒绝，绝不向真实或用户已有AVD发snapshot操作。当前探针firmware成功只作已测能力证据，新job仍需自己验证ACK。
2. **基线与owner。** 在该runner首次font_scale变更之前save；建议在安装与配置准备之前由host事务包住整个runner，以免改变“原值”的定义。snapshot名称含run/attempt/job/唯一scope；先查不存在，持久化host owner manifest（源码HEAD/dirty、APK SHA、实际device identity、original key presence/exact value/framework、scope UUID、AVD名），同scope device marker在save前落盘。明确同一AVD无并发runner且每次只有一个owner。原始设置用`settings list system`精确presence/value，framework用已证`dumpsys activity processes`的Configuration，不用get-config当ACK。
3. **save严格验证后才启动业务。** host fsync save intent→一次save→rc0、字节空stderr、完整合法末尾OK且无错误诊断→exact snapshot list唯一名称。任何不明确都停止，不启动font变更、不重试save/load、不delete未知快照。业务进程继承一个不由任意环境字符串单独认证的scope引用，runner核对host owner文件/source/设备marker后才进入snapshot清理模式。
4. **业务和证据在rollback前结束。** 原Instrumentation隔离/清理断言全部照常执行；原业务exit code先记host，不因恢复成功变0。runner原收集逻辑仍运行，host收集完整当前scope原件、失败上下文、已有native trace/日志、结果及清理报告，必须在load前完成。不能保留原失败pull的`|| true`然后视为可回滚；pull/host写入失败禁止load。可列明因业务早停本来未生成的预期文件为MISSING，保留失败；但对设备上实际已生成文件必须完整拉取并逐项hash、相对路径/源SHA/scope核对，原manifest引用存在者逐项验真。host manifest写完/fsync后才置evidencePreserved=true，后续artifact上传使用host目录不再从已回滚设备补取。
5. **替换而非叠加key清理。** snapshot模式下原runner的put/delete字体恢复段不再执行；保留同名清理报告接口但状态写`PENDING_SNAPSHOT_RESTORE`，不得提前报恢复成功。原非snapshot入口保持既有严格行为且不可自动降级。必要force-stop在留证之后、load之前，对该Mock包单次执行，不清数据。先证明所有业务/采集进程已退出；无法确认则保失败和证据，不并发load。
6. **一次load、直接验现态。** saveVerified+host证据校验成功+业务已结束后，再核同device identity、同owner marker、同snapshot唯一名与host源码身份；fsync load intent后一次load。ACK及重连后重新核完整设备身份、回滚后的owner marker，再从当前设备直接读key presence/exact value/framework，三者逐项等于baseline。不得读取随snapshot回滚的旧结果文件当新恢复证据；不以default1.0等同absent。load未知/失败不重试、不fallback delete/put、不宣称恢复。
7. **删自己的快照与退出。** 只有load ACK、身份及精确恢复全部通过，才一次delete该owner exactname，并查list确认消失。其它快照不动。业务非零→最终仍返回原非零（另外记录清理错误）；业务0而保存/留证/恢复/delete任一步失败→最终非零；全部过才返回0。host final报告分别列businessExitStatus、evidencePreserved、restoreStatus、snapshotCleanupStatus，不能以总PASS掩业务FAIL。SIGTERM可沿同有界单次收尾；强杀无收尾时记未证恢复，依disposable job终结销毁，不自动接管重试。

### 最小验证及验收终点

离线测试覆盖：身份/owner/source不符零load；save未知/缺list零业务变更；pull失败或hash不符零load；业务非零但恢复成功仍原码；业务0恢复失败转非零；absent与present精确值两分支；framework不同拒绝；load未知不重试/不delete；仅成功恢复删自己一次；原业务清理错误不能被snapshot覆盖。不得为测试放宽console错误/空白stderr/混表拒绝规则。

集成首批保留原Scale四配置及既定业务，新增host事务原件即可，不新增产品scenario。实际验证：原基线、save ACK/list、真实业务原件及原退出码、load前host inventory/hash、load后现态与delete ACK齐全。业务本身若失败，只能签“恢复事务成功，业务FAIL”，不得签页面通过。首批若业务正常完成，可利用已有离线失败分支及独立probe的模拟失败能力证据，但**真实runner早停路径仍需下一实际失败或单独明确授权的host级故障注入才能称集成失败路径已验证**，不能制造产品失败冒充。

该方案会回滚disposable AVD整个snapshot，不是只回滚一条setting，因此严格限定独占一次性CI，并必须先保全所有业务证据。无真实设备、无用户数据清除、无取消当前CI、无旧FAIL追认。

### 首批Scale接入静态收口

已复核四文件最终候选：runner `b47ab1d28fdd7f5bd87549f0595b3e898a0d1fc60d6e5fd34e921ed37961313a`，workflow `7375a2753a09c6f4259545fb4f946bafa5a05e9f786f90e4795a6cd5ffbca90e`，helper `186d2045d1459566dba6d530bff7329b7abe26c231e57c22f59c9809a9ffdf1e`，test `3b3b1ed5152865035cb671f30de9907bd94d7601153a6397f155ed2d211701cb`。

原业务码先durable记录、终态报告写失败保原非零；snapshot模式force-stop移至留证后；拉取树manifest source/fileSHA及安全路径检查；业务进程组有界终止与quiescent不明零load；关键identity/list原件保留且每Scope实例UUID前缀不覆盖verify子进程记录。Astra实际重跑专用13测试通过（其中disk full输出是预期负例，不是本机新失败）；probe11及shell/Python/YAML检查由owner报告通过。静态无剩余阻断，可交root合批实际CI。此为首批集成候选接受，不代表runner实际回滚/业务或视觉已通过；原probe成功与旧FAIL边界不变。

### 2026-10-24：Scale 正式集成正常路径实际收口

run `40056758145` / artifact `12546056702` 已 success。实际 tested merge source 为 `084a7b18ec62e2d316b2bf0df514f0d2369f31e9`，不是 PR head `8fee86b`。原 ZIP `build/task-first-evidence/ci-8fee86b-scale/artifact.zip` SHA256 `586913d2f5889a7369a357827971d6268ff8094682b486ca207efdf350152aac`，root 已核 remote digest / 3139 条 CRC。

Astra 定向读取原 `snapshot-scope/result.json`、`operations.json`、`business-exit.json`，与 Sol 审计中逐文件 SHA 一致：businessExitStatus=0、processGroupQuiescent=true，save/load/delete 各一次 intent+ACK，saveVerified/loadAcknowledged=true，errors=[]，restoreStatus=PASS_EXACT，snapshotCleanupStatus=DELETED_OWN_ONLY。

完整机械复核由 Sol 执行，Astra 阅读其原审计 `snapshot-scope-mechanical-audit.json`（SHA256 `8482bf0f12640be5411e63a9895e0f4d619a9ac2bf7cd531bc8ac518a7cfeab5`），未重复全量 hash：before/restored 均 key absent、value null、实际 frameworkFontScale=1.0，5/5 identity 与 owner 一致，7 次 snapshot list 只为空或本 owner 快照；1542 pulled、1566 hostBusiness 文件逐 SHA，22 manifest/1534 引用及 inventory 均核对，业务证据在 load 前保全。

**只接受这次独占 disposable Scale 正常完成路径的真实恢复事务。** 不签真实 runner 早停/失败集成路径，不代替视觉验收，也不追认旧 Bean 等恢复 FAIL。原早停边界仍须下一自然失败原件或另行明确授权的 host 级故障注入；本轮不接 Bean runner、不自动注入。

### 2026-10-24：自然业务失败后的真实恢复路径

接受 run `40056753959` 的**自然失败后恢复事务**，关闭此前“尚无真实 runner 失败路径原件”的限定缺口；不关闭该运行截图失败，不扩到未接入的 Bean runner。

实际 source `8fee86b4e3f004ed3be5811d22a5bb49c2f87b98`，artifact `12546935104`；ZIP `build/task-first-evidence/ci-8fee86b-scale-failed/artifact.zip`，68480949B，SHA256 `53364678d9af19d5c28cbdfb802ea84920976a14d6c69b5320cee52168af12fb`，root 已核远端 digest / 804 CRC。不是人工故障注入。

Astra 直接从 ZIP 只读核对 scope 原件与部分业务证据，未整包解压：

- 原 `business-exit.json` 为1且 processGroupQuiescent=true；result 同样保留1，errors=[]、evidencePreserved=true、PASS_EXACT、DELETED_OWN_ONLY。日志 `ci-8fee86b-scale/push-run-failed.log` 第219行保留 LOCAL_AUDIO_CHECKS_FAILED / Equivalent Window/display domains did not synchronize within 5000ms，第242行最终 sh exit1。恢复成功没有把原失败变绿。
- before/restored settings 均无 font_scale key（absent/null），原 dumpsys 的 mGlobalConfiguration 均实际1.0，与 owner baseline一致；不是拿默认值替代key presence。
- save/load/delete 各一次intent+ACK，三命令exit0、stdout OK、stderr空；5份identity parsed均与owner一致，原查询exit0/无stderr；7份snapshot list只出现本owner快照或空。
- evidence-preserved.inventorySha256与原inventory字节相等；381 pulled与392 hostBusiness逐SHA相等，deviceGenerated与pulled映射完全一致。2份带fileSha256的页面manifest/source及379个引用逐SHA相等。其余4份special/negative manifest是case摘要，不具有sourceSha/fileSha256，不冒称同一schema；它们本身已纳入inventory哈希。

审计记录 `ci-8fee86b-scale-failed/snapshot-failure-audit.json`，SHA256 `85185fc747bc826e0f10bf4f01e77a11e4949460834a6c7c02cd84011c55c502`。本次证明已有Scale runner自然业务失败后的留证、原退出码保留及精确还原；不证明截图通过，不改变5s/四零，不追认旧恢复FAIL，不授权新runner或设备执行。
