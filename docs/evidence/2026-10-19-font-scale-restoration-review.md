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
