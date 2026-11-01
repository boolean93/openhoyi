# Settings CI 四个一次性 emulator 容量隔离草案

状态：Astra方案审查通过、root已批准实施；代码与离线验证完成，最终独审后独立本地提交，尚未push。

- 需求与范围：将原12格拆为固定 `light-1.3`、`light-1.0`、`dark-1.3`、`dark-1.0` 四个一次性 AVD，每个按 app → machine → scale-settings 原顺序执行3页。原 dark-1.3 ENOSPC 是多profile证据累计，并非host容量不足；不删证据、不清用户数据、不放松比较、不增加单job/page/frame时限。
- 来源/工作分支：沿用已授权 `fix/ci-page-service`，基线 `7b7562ddb4e0621679d1b442d2acc688f4caa096`；PR目标 develop。独立容量提交，排除用户 Scale 草稿与缓存对照。
- 推送与合并：Astra方案审查 → 实现 → 离线负例/编译 → Astra独审 → 独立本地提交；由parent统一决定push和PR。不打断PR7当前运行。
- 验证：原完整运行与捕获/隔离/host fullverify门保留；新增固定scope与artifact receipt聚合负例。新源码真实CI待parent安排，绿色不等于视觉或硬件验收。
- 协议/设备/发布：仅CI host/一次性emulator，不改产品/BLE/秤、未知记录、版本、签名，不发版、不操作实体设备。
- 回退：revert容量提交恢复原workflow；保留失败原件，不用清数据恢复。
- 未完成：真实四AVD CI、完整12格容量实测与视觉验收；本地compile不等于新源码真实CI。

## 工作流接口

保留现有 `on`、`concurrency`、`permissions: contents: read` 和原 `settings-visual` job/check 身份。新增 `settings-build` 一次构建和 `settings-cell` matrix worker（四项固定include，`fail-fast: false`），原 `settings-visual` 改为 `needs: [settings-build, settings-cell]`、`if: always()` 的严格聚合。各job仍最多45分钟；每page instrumentation仍600s，每frame仍5000ms，16G AVD不增加。

四worker并发增加整体runner资源消耗；单job45分钟、单page600秒、单frame5秒均不增加。

`settings-build` 输出唯一APK bundle artifact（名含 run_id/run_attempt），包括 mock APK、test APK、`build-identity.json`。identity记录checkout source SHA、PR head SHA、run_id、run_attempt、两APK路径/sha256。worker checkout相同源码；下载按已知artifact名，逐一校验identity及两文件hash；不重建、不重用旧run/attempt，不接受额外APK。

每worker独立 `android-emulator-runner`，不恢复 snapshot，不共享AVD/data，保留原qemu/唯一serial/API34/KVM/几何/安装/fresh-process验证。调用：

`bash scripts/check_settings_visual_migration.sh tablet-user-landscape <output> <fixed-scope>`

第三参数只接受 `full`（兼容原12格）和四个固定scope；不提供任意pages、theme组合或数量参数。scope中的3页保留原停止/继续语义：业务失败且隔离安全则继续，未知归属或清理失败则停本AVD，其他独立worker继续。每worker EXIT trap照常记录容量、失败raw-device、runtime/IME诊断与font key的原存在性和值恢复；保留第一次失败退出码。不能在worker成功状态上吞掉恢复失败。

## Ledger和fullverify接口

`settings_matrix_isolation.py`：

1. `expected(source, scope='full')` 固定返回12格或某scope的真实3格；`init --scope`仅上述枚举。ledger记录scope、source/head、run/attempt、两APK hash、实际device identity。
2. `load()` 按冻结scope逐项验证exact集合/顺序/身份；不能保留其它9格假skip/pass。`before/after/check_turn/preserve/check_isolation`全保留；一AVD内PID唯一，跨worker按 `(scope, deviceIdentity, manifestPid)`记录，不要求不同AVD的整数PID互异。
3. `finalize()` 按scope真数量要求fullverify涵盖每个原成功capture；原失败状态、unsafeStop、firstFailureExitStatus均不重写。fullverify exit0只可能来自全部3格（或full12格）原完整门通过。
4. 原 `VERIFY_PY` 逻辑仍逐manifest原样运行；固定总数改为ledger的冻结scope数量，并要求scope具体profile/page集合，而非仅比较len。required special-states、exact source/head/APK/ciRun/ciJob、文件SHA、protocol2、所有geometry/完整性、proof和manifest门保留。每worker不下载别人的原件进行重复fullverify。

## 上传与聚合接口

每worker `if: always()` 上传原 evidence artifact，唯一名 `settings-evidence-<scope>-<run_id>-<run_attempt>`，保留所有原文件/失败raw-device/restore/容量；上传设置 `if-no-files-found: error`。保持当前默认compression-level6；证据JSON高度可压，压缩调优不属本任务。capture和EXIT结束后，evidence tree冻结只读，上传完成后从同一未变化的树计算inventory/hash并生成小gate receipt；receipt/inventory/gate原记录复制件均输出到evidence根之外，上传后禁止新增或改写evidence树，不将inventory自身纳入循环。

已实现 `scripts/settings_shard_gate.py`：各命令从CI环境读取source/head/run/attempt/repository并与真实checkout HEAD校验，不允许CLI覆盖身份。

- `receipt --scope ... --evidence ... --gate ... --bundle ... --artifact-id ... --artifact-digest ... --artifact-url ... --capture-outcome ... --upload-outcome ...`：从磁盘实际读取ledger/fullverify/restore/原退出状态；使用外置freeze inventory（相对路径、byte size、sha256）及摘要，记录原evidence action输出的artifact-id/digest/url和全部身份字段；不凭exit0构造PASS。非法路径/符号链接、空artifact输出、缺原门、缺恢复结果均FAIL。将ledger/fullverify/restore原文件与receipt一同上传小gate artifact。
- `aggregate --gates ... --output ... --build-result ... --workers-result ...`：读取固定四份gate及各自原验证记录，重算其inventory摘要和gate内原记录hash；要求固定四scope、each3pages、全局exact12、同source/head/run/attempt/APK/testAPK/build identity。必须 `needs.settings-build.result == success` 且 `needs.settings-cell.result == success`，任何失败/取消/缺receipt/上传失败都红。不能让绿色receipt覆盖红worker。先保留已有原FAIL receipt，再判gate完整性，避免汇总诊断只剩泛化缺文件错误。
- 聚合不重下数GB原件：worker已经完成逐文件远端/host SHA及完整门；聚合保留每个原artifact-id/digest/url、inventory摘要、每worker的原fullverify/restore记录和12格ledger。URL必须是当前repo/run的Actions artifact URL，ID须正整数、digest须合法SHA256，四ID唯一；这是当前workflow可信step输出的关联记录，不声称重新校验未下载的原artifact ZIP。
- 若上传失败而无artifact id/digest，不伪造关联，不把receipt成功上传当证据上传成功；worker失败、原输出仍按action可用情况保留，聚合明确缺失。
- `if: always()` 仅是调度条件，不保证保存：runner丢失、timeout、上传失败或结果缺失必须报告evidence preservation为UNKNOWN/FAILED且聚合红，不能宣称原件已安全转存。全程不删除或回收原件。
- 为防止fullverify通过后、冻结上传前丢失某格原件，每格生成外置 `cell-preservation.json` witness：固定source/profile/page子树，`evidenceFileCount`，manifest本体SHA，manifest列出的完整文件集合及SHA，scope/run/head/APK/testAPK/PID/status，以及每格inventory摘要。receipt将实际原manifest逐项对照冻结inventory；aggregate再把witness、ledger与inventory交叉核验，不传巨大initialFrameSync副本。删整格、manifest、任一列出文件或篡改计数均不能PASS。

小gate artifact名同样含scope/run/attempt；aggregate仅下载这些小gate（通过四固定名，不能宽泛接收旧attempt或任意pattern），不依赖新增GitHub API权限。

## 离线负例清单

- scope非法/缺省错误、scope恰三页顺序、全模式12格不变、任意减少page被拒、某scope夹带其它profile。
- 第一page隔离安全失败后后两页仍运行且保first exit；未知PID/owner/hash/pull/cleanup停本worker，另外scope不被fail-fast取消。
- fullverify缺页/重复/多余/profile不符、部分capture与成功fullverify冲突、假PASS、恢复失败/缺失、firstFailure覆盖均拒绝。
- build bundle source/head/run/attempt/APK/testAPK错误、旧artifact身份、额外APK或文件名漂移均拒绝。
- gate缺一个scope、重复scope/receipt、缺page/重复page/extra格、四scope混source/head/run/attempt/APK、跨AVD同整数PID合法而同AVD复用PID不合法。
- evidence上传失败/空id/空digest/错误URL、receipt早于上传伪身份、原证据关联重复、完整inventory被改/记录hash不符、恶意相对路径/符号链接均拒绝。
- receipt都绿但needs worker failed/cancelled/skipped仍红；gate原fullverify/restore失败仍红；缺artifact时保留可用失败记录，不将缺失转成skip/pass。
- workflow静态契约：on/concurrency/permissions/原check名、fail-fast:false、always上传与聚合、三层预算不增加、四固定scope、独有artifact名和不清数据/删除证据。
- 直接执行原FONT_RESTORE_PY的模拟：1.3 shard最初font key absent，Android反复回插`1.0`时仍只尝试5次原delete操作、记录原首错7且restore FAIL；原absent或精确原present值可恢复。不能额外put1.0或放宽presence/value。此风险尚待真实独立1.3 AVD证据。

## 本地验证与边界

当前50项自包含host tests通过（原31项 + 新scope/receipt/aggregate/restore/workflow19项）；完整日志 `settings-evidence/ci-diagnosis/settings-sharded-capacity-host-tests.log`。原12格full默认模式及原matrix12 tests保持通过。旧ZIP依赖的archive套件未作为本轮通过证据。

`:mobile:compileMockAndroidTestKotlin` BUILD SUCCESSFUL（30s），日志 `settings-evidence/ci-diagnosis/settings-sharded-capacity-compile.log`。`bash -n`、`git diff --check`、Python compile及Ruby YAML解析通过；本机未提供actionlint/PyYaml，未冒称其检查已运行。

仅修改 `.github/workflows/settings-visual-ui.yml`、`scripts/check_settings_visual_migration.sh`、`scripts/settings_matrix_isolation.py`、新`settings_shard_gate.py`及测试、instrumentation manifest两字段和本计划。原archive测试仅补`ledger={'scope':'full'}`命名空间以保留原契约拒绝测试的意义。用户Scale Kotlin及草稿的原哈希不变。沿用现分支，history独立提交`6ba0378`不混改；未push、不操作设备。

Astra `/root/settings_failure_review` 最终只读独审通过，独立重跑50 tests、bash语法、diff检查与Ruby YAML job结构，并直接读取30s成功编译日志。P1逐格原件保存闭包、P2失败receipt诊断保真均已修复并复核。批准仅本块8文件独立本地提交。新源码真实CI仍待；单16G AVD实际余量、1.3原absent键恢复、上传耗时均未实测，不宣称容量或视觉验收完成。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。
