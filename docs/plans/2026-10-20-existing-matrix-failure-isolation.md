# 原固定矩阵的失败隔离继续取证

Astra，2026-10-20。结论：**值得做，先M8，再M5，M2/M3保额外native trace门禁。只改host调度，不加场景、不放宽任何验收、不重跑失败项。** 当前dc原件已有失败scope独立的PASS_ISOLATION_ONLY（Settings/App、M5/Comparison、Curve/Import），因此不是必须改Instrumentation才能获得安全依据。不能只靠该status字符串继续，必须核整份隔离证据。

## 范围与owner

首批仅 `scripts/check_settings_visual_migration.sh` 及必要一个纯host契约helper/离线测试，唯一owner由root排。M8 App前置失败长期阻断Machine/ScaleSettings，且无native collector，收益最高。M5仍由当前Sol_bcc独占，待其Extraction补齐冻结后顺序接同模式；不得另一owner并行改M5。M2/M3各原owner后续接，原页序/浅深/字号集合、配置/循环次数/超时不变。不依靠snapshot恢复来授权继续，snapshot仅run最终字体清理，两任务分开。

## 每个既有page/profile一次执行

1. 开始先声明原完整固定集合与顺序，每格唯一key=sourceSHA/page/theme/font/profile，创建host矩阵账本；本run待产目录须无同key旧manifest，已有就拒绝重跑而非删除。保原fresh-process pidof严格检查，原配置应用/校验与Instrumentation参数不变。一个格子只启动一次。
2. 将目前`set -e`导致整循环早退的单格命令置于受控捕获返回码分支，记录该格原shell/adb返回码、原Instrumentation stdout/stderr及业务成功marker是否存在；原成功判定不变。只有本格结束后才能选择继续，不能并行、自动重试或用下一格覆盖文件。
3. 无论本格成功或失败，先pull其当前scope全部已生成证据到唯一host目录，保原失败上下文与manifest原FAIL；校验source/profile/page/pid与本次启动身份、各manifest已列fileSHA。失败格未到达的原门禁/截图标NOT_REACHED，不强补PASS。pull/解析/hash未知即停止余矩阵。
4. **继续许可独立于视觉通过。** 必须本格finally已产完整隔离证明，核该runner既有全部isolation字段（不只挑两个true）：业务盘字节未变、typed prefs与Lazy身份/初始化恢复、所有Activity销毁、reader/writer/回调终止、queue barrier、Service所有权/解绑/关闭/handler排空、clock撤销、原observer保留；M8另核feedback停止、provider session撤销、真实Service scope及trace writer结束。字段缺失/false/unknown或manifest isolationStatus不为PASS_ISOLATION_ONLY，立即停止。不得以am force-stop后pid不存在补作此前cleanup成功。
5. 隔离证明通过后，沿既有force-stop/fresh-process过程使下一格从独立新进程开始，并核唯一设备/原source/APK未变；下格继续使用原指定theme/font，不先重置到另一值。失败格在账本记FAILED_ISOLATED_CONTINUED，绝不改成机械PASS。未执行余格若安全门失败统一记NOT_RUN_UNSAFE_PREDECESSOR，不能从预期集合删掉。
6. 每个成功格仍执行原完整主图/特殊态/协议/geometry/业务/requiredproof集合检查；原门禁失败记该格FAIL。现全批末尾检查拆成“原逐格验证”与“固定集合/汇总”两层，既不因首个FAIL中断其他已安全格的验证，也不跳过成功格任何字段。整体只有全部原格成功且原清理成功才0；否则最终非零，保首个原非零退出码及每格原码（业务marker缺失而adb0时记录原adb0+现有语义失败码，不能假称原adb非零）。最终字体清理失败只可把原成功变失败，不覆盖已非零业务码。

## M2/M3额外停止条件

native trace collector在本格finally执行完，原ownedStart/stopAttempt/ACK/三文件留证状态如实保存。若collector仅因schema/解码能力标PARTIAL但已证明自己启动的trace已停止、无采集进程遗留且原scope清理完整，可在该格仍FAIL的前提下继续；trace ownership不明、stop未确认、进程未退出则停止。不能用hostsnapshot回滚、盲stop、重启设备或再开scenario清未知状态。M2/M3当前只有相应指定light/1.0 scope接trace的限制不变，不把所有格改为额外native采集。

## 验证与价值边界

最小离线调度契约：首格视觉FAIL+完整隔离→下格恰一次、最终原码；隔离缺字段/false/错source/旧pid/证据hash错/pull失败→不启动下格；后格成功不洗掉前FAIL；末尾字体清理失败不覆盖原非零；完整预期集合缺项必FAIL；native stop未知不继续。真实CI首批不故意造产品失败，使用自然失败证明继续路径。不能宣称当前旧失败隔离报告本身已证明新host实现正确。

本方案可直接获得被前页阻断的MachineSettings、ScaleSettings及M5后续配置原图，推进24页G1–G4；不是提高通过率。若失败scope没有完整清理证明，该run仍必须停，实际缺口再定位，不能为了产更多图绕隔离。用户最终仍看到整体FAIL与逐格结果，Astra只对真正已采原图作限定设计验收。

## 2026-10-20 M8首批静态终审

Astra核完三文件完整差异：固定light/dark × 1.3/1.0 × app/machine/scale-settings共12格，原序不变，每格最多启动一次；before拒绝既有host/device目录及身份变化，after先pull完整原件并核设备/host库存SHA与manifest owner/source/APK/profile/pid，再逐项核原隔离证明；未知立即停止并保NOT_RUN_UNSAFE_PREDECESSOR。成功格原VERIFY主体检查保留，失败格原manifest与原码不改，成功与失败分别归档，整体不因后格成功转绿。无Kotlin/业务/场景/5000ms协议变化，无snapshot作为继续许可。

终审修正：先前业务首码已入ledger、后续host命令提前EXIT时，collect在abort后读取合法首码，不让后续异常或字体清理替换它；ledger缺失/损坏/非法则保当前非零。Astra实际运行11项host离线测试通过，其中shell选择逻辑确认7优先于后续9、损坏ledger回退9；不是实际整条Android runner通过。bash -n通过。新实现尚待CI自然失败隔离及余页取证验证，旧App失败不追认成功。

可纳入本批提交，静态无剩余阻断。最终SHA256：

- `scripts/check_settings_visual_migration.sh`：`37b9fbfceba0f964a71c1937971a8f8c6829d3624006a2ed0d7fff759a053cca`
- `scripts/settings_matrix_isolation.py`：`18ba3199245372c4c8c45769ce0e2852e9bdc7ed3b32face50c2f30ca07fd754`
- `scripts/test_settings_matrix_isolation.py`：`e9a7b437dd48c7baa608f0059e473e82a0973c12b441515f6988485742800004`
