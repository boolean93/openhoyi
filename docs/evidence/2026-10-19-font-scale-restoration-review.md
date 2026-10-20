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
