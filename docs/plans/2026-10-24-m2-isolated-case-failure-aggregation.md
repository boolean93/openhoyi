# M2 独立 case 失败聚合实施计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划执行。Astra 负责规划/验收，root 指定的 Sol 负责 host runner 与离线测试；不要另派 agent 或改产品。

**Goal:** 在原 case 已明确清理成功后继续原矩阵的下一独立 case，保留首 Editor 失败和最终非零退出，让其他配置获得独立验收机会。

**Architecture:** 每次仍只启动一个 `formPage` instrumentation。host 区分「业务断言失败且隔离恢复已证」与「清理/来源/进程/取证不明」；前者可继续，后者立即终止矩阵。最终聚合不修改任何设备原件。

**Tech Stack:** Bash、Python 标准库、现有 ADB instrumentation 与 JSON/SHA256 原件。

## 0. 授权、现状与边界

- root 于 2026-10-24 批准此有条件继续方案；尚未实施、构建或运行。
- 基线：`0396720`，工作树 `feature/task-first-ui`（来自 `develop`），沿 PR #4 → `develop`；不新开并行设备任务。
- 遵守 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。共享仓库不覆盖他人改动。
- 当前 `scripts/check_form_visual_migration.sh` 的嵌入 Python 因第一项失败抛出 `SystemExit`，Bash `set -e` 终止后续循环。三个页面原本已是分别 fresh-process、分别 Scope、分别 instrumentation，并无共享 fixture 的依赖要求。
- 现有 `FormVisualMigrationChecks.run` 在 catch 保留现场、finally 执行 Scope.cleanup 并写 manifest，之后重新抛出原错误。58ae 原包中 Editor 为 `layoutStatus=FAIL`，但下述九项隔离证据均已具备且为真。这证明方向可实施，不证明 Editor 已修复。
- 不改 `FormVisualMigrationChecks`、`AdvancedBusinessUiChecks`、Instrumentation、产品、协议 2、IME/scroll equality、原 5 秒四项零差门禁、布局/业务断言。失败 case 不重试，不跳过断言，不继续该 case 内失败点之后的动作。
- 当前 Editor IME 因果仍未判定；本计划不以聚合代替产品修复，不预先宣称后 11 配置通过。

## 1. 文件所有权

| 文件 | 改动范围 |
|---|---|
| `scripts/check_form_visual_migration.sh` | 保留设置/安装/原矩阵；调用专用 host 执行与聚合；EXIT 收集保持非零 |
| `scripts/form_visual_case_aggregation.py`（新建） | 仅供上述 runner 使用：case 证据核验、终态分类、矩阵聚合；可承接过长嵌入 Python，不建通用框架 |
| `scripts/test_form_visual_case_aggregation.py`（新建） | 用临时目录、合成 manifest/proof、假 subprocess transport 验证真实 host 控制流，零设备调用 |

本规划归 Astra。不得顺手修改其他 runner、workflow、collector、设备 fixture 或全局字体恢复策略。

## 2. 唯一允许继续的隔离合同

### 2.1 本轮新鲜性与正常终结

每个 case 启动前和终结后分别核验，不能用上一个 case 的 PASS 替代：

1. 预先记录完整预期身份：source/head SHA、APK SHA、`GITHUB_RUN_ID`、`GITHUB_JOB`、host run attempt（如有）、page、profile、请求字体与主题。
2. 该 case 的设备目录 `/sdcard/Android/data/io.openhoyi.mobile.mock/files/form-visual-migration/<source>/<profile>/<page>` **必须原先不存在**；host 接收该 case 的终态目录也不得已存在。远端检查必须完成并有明确退出状态；权限/transport/不明输出不是「不存在」。发现既有目录即 ABORT，不删除后重试、不接受其中的旧证明。
3. 原 `am force-stop` 与 exact `pidof` fresh-process guard 保留；每次独立 instrumentation 启动前重新验证。force-stop 只能建立下一次无进程前提，不能补发上次清理成功证书。
4. `am instrument -w` 必须由 host 亲自观察 subprocess 正常完成且 `returncode=0`；完整 stdout+stderr 原样保存。再要求唯一**行首**终态：精确整行 `FORM_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true`，或 `FORM_VISUAL_MIGRATION_FAILED ` 后非空原异常及堆栈。成功与失败不能并存，重复 marker、无 marker、ADB 非零、timeout/崩溃/未知退出均 ABORT；唯一 marker 还必须与下述本次终态 manifest/隔离证据相互一致。returncode=0 本身只证明 transport 正常，不能单独判断业务成功。
   - **终态勘误（2026-10-24）：不要求 `INSTRUMENTATION_CODE`。** 58ae 的 1672 字节原 result.txt 没有该行；原 RUN_PY 直接保存 stdout、stderr合并，没有过滤。当前命令没有 `-r`，官方 Android14r1 `Instrument.java` 的 `TextStatusReporter.onInstrumentationFinishedLocked` 在非 raw 且结果有 `stream` 时只输出 stream；只有另一分支打印 CODE。`BrewAudioInstrumentation` 成功/失败都设置 stream 后 `finish`，与原件吻合。不得加 `-r` 改输出契约或补造 code。
   - 新 helper 将本次实际 subprocess completion/returncode 写入 host case 记录，字段明确是 host 观测，不伪装设备既有字段。历史 archive 未持久化这个 returncode，离线重放必须把它标为测试输入假设，不能声称已从旧包证实 exit0。官方来源：[Android14r1 Instrument.java](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/cmds/am/src/com/android/commands/am/Instrument.java)。
5. instrumentation 终结后立即拉取**该 case** manifest 与所列原件到新 host case 目录。来源、page/profile、CI 身份与本次参数全部一致；PID 为有效正整数。PID 不要求跨 case 永不复用（系统可复用 PID），新进程证明来自启动前 guard，而非数值不同。
6. 校验 manifest 中所有 `fileSha256`；`fixture-isolation.json` 必须在该 map 中且 hash 匹配。路径不得越出 case 目录；缺文件/无效 JSON/旧 source 或 CI 身份/hash 不一致均 ABORT。manifest 本身另算 host SHA，不能伪称它有自哈希。

新目录前置 + 本轮终态 + 身份及 hash 共同证明新鲜性；不能仅因 source/profile 相同就沿用旧 proof。

### 2.2 必需隔离字段：缺一即 ABORT

`fixture-isolation.json.status` 必须精确为 `PASS_ISOLATION_ONLY`，下列字段必须均为 JSON 布尔 `true`（字符串 `"true"`、缺省值、仅 status 均不接受）：

- `allActivitiesDestroyed`
- `serviceRemovedAndOnDestroyPostconditions`
- `mainQueueBarrierAfterDestroy`
- `scopedWritersTerminated`
- `originalLazyIdentityAndInitializedRestored`
- `legacyUnchanged`
- `typedPreferencesRestored`
- `businessDiskByteUnchanged`

同时要求原 `businessDiskBefore` 与 `businessDiskAfter` 对象存在、相等；保留两份白名单偏好 XML 的实际 SHA，不能要求其物理字节相等来替代已有 typed 恢复，也不能删除 XML 追 hash。隔离 proof 含 `failure`、状态 FAIL、字段未知、构造期失败未产生完整 proof 时一律 ABORT。

`manifest.isolationStatus=SEE_fixture-isolation.json` 不是清理失败本身：现代码业务失败也写这个值，必须读独立 proof。反之 manifest 自称 PASS 不可覆盖 proof 的失败。

### 2.3 首 Editor collector：安全停止与采集质量分开

仅原 `light / 1.0 / curve-editor` 的既有 collector 适用，其余 case 不新增 tracing。**2026-10-24 勘误：初版要求 COMPLETE 才继续，错误地把时间覆盖质量当作隔离安全；本节替代该条件。**

已实读 `ci-58ae69e/frame-artifact.zip`：`collection-complete.json.status=PARTIAL_OR_UNAVAILABLE`，失败为 `Stale or incomplete time coverage: ime_trace_clients.winscope`；owner 的 ownedStart/stopAttempted/stopAcknowledged 均 true，stop 命令 exitCode=0、退出 marker/worker 终结/两路 EOF/预算内均 true，精确停止 ack 已返回，三 raw 文件及 SHA 已保留。collector 源码先验证停止，再检查覆盖，finally 保留原件并正常 exit 1。因此该质量失败不意味着 trace 仍在运行。

**允许继续必须独立满足的安全合同：**

1. fixture-context、`trace-state.json` 与完成报告 owner 的 source/profile/fixture/package/token 相互一致并属于本次 case。owner token 合法，`ownedStart=true`，不能借用其他 case 的停止结果。
2. 停止确认沿原两条路径之一，不新增命令：
   - owner 的 `stopAttempted=true`、`stopAcknowledged=true`，唯一原 stop 命令的实际 output 含精确停止 ack；命令 exitCode=0，`completedWithExitMarker`、`workerTerminated`、`stdoutEof`、`stderrEof`、`stdinWrittenAndClosed`、`withinTotalBudget` 均为 true；nonce/原 stdout 退出 marker 与记录一致。
   - 或原 collector 已执行的唯一 `fallback-stop.json`：同 owner token、attempted/acknowledged=true，保存的实际 command completed/returnCode/唯一远端退出 marker 与停止 ack 满足原 collector `unique_ack` 契约。不得再调用一次 stop 来补证明。
3. host 亲自观察原 `after` subprocess 正常终结（返回 0 或 1），保存实际 returncode；不是 timeout/被信号终止/仍运行/仅靠文件存在推定退出。instrumentation 也须满足 §2.1 正常终态，Scope 清理满足 §2.2。
4. 完成报告、owner/context、实际命令记录和**本次已取得的所有原 trace 文件**已持久保存；`rawArtifacts` 列出的每个文件大小/SHA 与实存字节匹配。原已取得文件不得丢弃、覆盖或用新文件顶替。文件缺失、传输/写入失败、身份不明不得继续；某 trace 域根本未成功产生只可记质量缺口，不虚造该域原件。
5. `collection-complete.json` 必须是本次正常收尾报告，保留原 `status`、`failure`、`rawArtifacts`；host 另记 `traceSafety=OWNED_STOP_ACK_COLLECTOR_EXITED_ARTIFACTS_RETAINED` 与原 `traceQualityStatus`。不是修改 collector 结果，也不将安全记录称为 trace 内容 PASS。

**质量判定不阻断安全的下一 case：** 原 `PARTIAL_OR_UNAVAILABLE`、时间覆盖不足、旧/不完整内容、解析或某域缺失仍是原 trace 质量 FAIL/不可归因；当上述安全条件全部满足，可记录该 case FAIL 后继续。`differentFromBefore`/`coversRecreateAndComparisonTime` 不再作为隔离安全条件。质量 PARTIAL 使本轮最终非零，即使业务断言成功；原业务 FAIL 另行保留，不能被质量失败替换。

**必须 ABORT：** 未确认 owned stop、身份冲突、命令退出/worker 终结未知、collector 超时/异常终止、收尾报告缺失、已取得原件未安全保留，以及其它无法独立证明上述安全条件的情况。不能根据错误字符串看似“coverage”便放行，也不能因 status PARTIAL 自动推断安全。

完成后登记 host collector 安全终态；`ime-active-profile.json` 不是实际 trace 活跃证明。EXIT 不重新启动或补 stop；可读取原幂等完成报告，但原 quality exit 1 不能把已完成安全判定改成未知，更不能被吞成 0。原质量与业务错误始终并列保留。

## 3. 分类、矩阵与退出优先级

原顺序严格不变：theme `light → dark`；每 theme font `1.0 → 1.3`；每组合 page `curve-editor → bean-entry → review`。共 12 个唯一 key。

| 本次结果 | 条件 | 下一步 |
|---|---|---|
| PASS | 成功终态、全部原逐项校验通过、隔离与 collector 安全合同满足且 trace 质量通过 | 记录后进入下一 fresh case |
| FAIL | 明确业务失败终态（manifest 保留非空原 failure 且 `layoutStatus=FAIL`），或原 trace 质量失败；隔离与 collector 安全合同全部满足 | 原 case 不重试；记录原异常后进入下一 fresh case |
| ABORT | 上述合同任一缺失/失败；成功 marker 与失败 manifest 等证据矛盾；host/设备超时或崩溃 | 立即停止后续，保原件；剩余 key 标 `NOT_RUN` 并写明原因 |

- 对成功 case，现 verifier 的实际窗口/字体/theme/无 IME/协议 2/hash/完整 business/所有 proof 等断言一条不减。机械成功仍 `ASTRA_PENDING`。
- 对合法失败 case，只核身份、证据完整性和隔离；不能强求不存在的 `form-business.json.completed`，也不能把未执行的业务记 PASS。部分 PNG 有证据不等于该 case PASS。
- 专用 host summary 使用独立文件名 `case-aggregation.json`，不得改写设备 manifest、fixture-isolation 或旧 failure。包含全部 12 key、attempted/PASS/FAIL/ABORT/NOT_RUN 数、每项原异常/日志/manifest SHA/proof 路径、继续或停止理由。
- 退出优先级：ABORT/停止安全或收尾保留失败为最高等级非零；否则任一业务或 trace 质量 FAIL 非零；只有 12 项全部通过原验证且收尾成功才 0。可约定 host exit 2=ABORT、1=聚合 FAIL、0=全通过；原 instrumentation exit 与异常全文单独保留，不用 host 数字覆盖。
- 业务成功但仅 trace 质量失败时，不修改原成功的设备 manifest 或 instrumentation 终态；host 明确并列 `businessStatus=PASS`、`traceQualityStatus=PARTIAL_OR_UNAVAILABLE`、`diagnosticQualityFailure=true`、`caseStatus=FAIL` 与聚合 FAIL，不能称产品断言失败。业务本来 FAIL 时保留原异常，collector 错误另列，不能覆盖。仅全通过才输出旧 `FORM_VISUAL_MIGRATION_RETAINED configurations=12...` 成功句。失败聚合输出独立 FAILED/ABORTED 标记，不伪造原成功 marker。
- Bash 只接管这两个已定义的 case 分类状态，不用 `|| true` 吞任意命令失败；EXIT 收集不得把已有非零变 0。额外收尾失败与首业务失败并列保留。
- instrumentation 原 600 秒、collector 原预算、协议原 5 秒、workflow 原 60 分钟均不变。job 被超时截断时，进度文件将剩余项保持 NOT_RUN，工作流仍失败；不承诺一次必然拍齐 11 项，不延长超时换通过。

## 4. Sol 最小实施步骤与离线验证

- [ ] 先增加专用 helper 的分类与推进测试，确认当前 fail-fast 行为不能满足「首 case 业务 FAIL + 完整隔离后下一 case 运行」用例。
- [ ] 抽取 runner 中必要的执行/核验段，保留原命令参数及矩阵顺序；每 case 结束即保存进度与原件，再决定继续/停止。不要把整份 runner 重构为框架。
- [ ] 离线通过以下真实 host 路径测试：
  - 首 Editor FAIL、完整清理/collector 成功，其余 11 PASS：恰好 12 次 instrumentation、无重试、最终 exit 1、原 failure 原样保留。
  - 12 PASS：完整原验证均运行、exit 0；业务 PASS 但原验证失败不得输出成功。
  - 假 `PASS_ISOLATION_ONLY`、任一 required 字段缺失/false/字符串、before/after disk 不等、cleanup FAIL：ABORT，后续调用数为 0。
  - 设备或 host case 目录原先存在、旧 source/head/CI/profile、hash 错、缺 manifest/proof、instrument 唯一 marker 缺失或矛盾：ABORT；不得删除/重跑。实际无CODE且正常返回+唯一marker+完整证明必须可分类，不得误拒；marker存在但transport未知/非零必须ABORT。
  - fresh pidof 未明确执行/有 PID/transport 异常，instrument timeout/crash：ABORT。
  - collector 正常 exit 1、PARTIAL/time coverage不足，但安全合同全部满足：继续下一 case、质量仍 FAIL、最终非零；实际58ae报告应能作为离线回放基例。collector 停止安全 unknown、超时/异常退出、stop 未确认、错 owner、已取得原件缺失/错 SHA：ABORT；合法唯一 fallback ack 可接受，不增加 stop。
  - cleanup PASS 不掩盖原业务 FAIL；质量 PARTIAL 不冒充 COMPLETE；正常业务 PASS + 质量 PARTIAL仍最终非零；伪造 stopAcknowledged=true 但无实际 stop 命令/worker退出证据必须 ABORT。EXIT 收集再失败仍非零并保两错误。
  - 第 N 项 ABORT：前 N 项记录保留，后续 NOT_RUN；矩阵顺序和每 key 最多运行一次。
- [ ] 运行 `python3 -m unittest discover -s scripts -p 'test_form_visual_case_aggregation.py' -v`、`bash -n scripts/check_form_visual_migration.sh`、`python3 -m py_compile scripts/form_visual_case_aggregation.py`、`git diff --check`。测试必须注入假命令执行器，记录实际 ADB/设备调用为 0。
- [ ] 交 Astra 静态复核：原断言/5 秒/600 秒/60 分钟与矩阵逐项对照。root 统一决定提交与普通推送；新精确提交的真实 CI 才证明 case 继续及 fresh cleanup 合同有效。

## 5. 发布、风险与回退

- 仅 host 调度与证据归类，不改 BLE/设备命令、单位、持久业务模型、未知记录、产品 show/hide 或实际 scope 清理。Mock 不等于硬件验收。
- 不发版、不签名、不安装真实设备、不合并本轮未通过 PR。提交仅本任务文件，普通推送现工作分支；不得直接推 main/develop、强推或取消在途 CI。
- 回退为 revert 此 host 聚合补丁，恢复原 fail-fast；保留已生成原件和 FAIL，不删设备数据/回滚业务盘来制造隔离通过。
- 未完成：Editor IME 产品恢复仍 FAIL；M2 其余配置须实际运行与 G1–G4 图审。即使新矩阵取得更多独立 PASS，M2 总体仍由原 FAIL 阻断。

## 6. 2026-10-24 冻结实现的限定验收

Astra 已按当前 M2 `FormVisualMigrationChecks`/`BrewAudioInstrumentation` 生产者、HEAD 原 shell `VERIFY_PY` 和现有 collector 源码完成静态对照。本轮无剩余静态阻断，可进入精确提交的真实 CI；不是产品恢复或视觉验收通过。

冻结文件 SHA256：

| 文件 | SHA256 |
|---|---|
| `scripts/check_form_visual_migration.sh` | `54ef0f8241a037af1043013c8c3e77647af91aac7b424994281163489c01e6da` |
| `scripts/form_visual_case_aggregation.py` | `685708cf8bd0c846ead628361b4775d9644e4314899a245377416805f127a267` |
| `scripts/test_form_visual_case_aggregation.py` | `63fffa156fe9fa8a5a29a827409fb5adf8d88c809817230f6930a1c085448812` |

已核：

- 当前 M2 manifest 成功 `PASS`、失败 `SEE_fixture-isolation.json` 与独立 proof `PASS_ISOLATION_ONLY` 分别处理；不套其他批次格式。原成功 verifier 的字体 SHA、PNG 尺寸、theme/locale/density、精确 float32、无 IME、初始及全部 frame proof、业务完成/无命令、geometry/hash 均保留；`layoutStatus` 仍为必需字段。
- 每 case 原顺序、独立 instrument、fresh pidof 和远端目录检查保留；目录原始检查结果现已落盘。旧 host case/result/native/capability/active 证据不删除、不复用。
- collector 质量 PARTIAL 与明确安全停止分离；业务原结果、质量错误与聚合结果分别记录。stop/collector 退出不明则停止后续，剩余 NOT_RUN；不改原四零或 5 秒门槛。
- 已修 EXIT 旧 owner 风险：shell 生成本次随机 sessionNonce，helper 只向本次新建 active marker 写入 nonce、source/head/APK/CI/profile/固定路径；EXIT 完整等值验证，不匹配只保留并报收尾错误，零次 collector.after。已存在本次 completion 也不重复 after；helper 的 after 已尝试路径移除 active marker，避免 EXIT 再发一次。
- `collect` 被 `if` 调用时不依赖隐式 `set -e` 处理错误；显式累积 cleanupErrors。空错误数组采用兼容 `bash -u` 的展开，仍写 `exit-collection.json`。原非零不翻绿，收尾失败升级为 exit 2 并保 originalExit。

Astra 独立运行 `python3 -m unittest discover -s scripts -p 'test_form_visual_case_aggregation.py' -v`：13 tests，0 failure/error。包含真实 `CaseExecutor.execute` 使用假 subprocess transport 遍历 12 case（collector 校验另以专门测试覆盖，不冒称端到端真实 trace）；以及真实 Bash runner 配假 PATH 的五种 EXIT 情形：主 1/收尾成功→1、主 1/收尾失败→2、主 0/收尾失败→2、旧 marker→2 且零 after、本次 completion/主 1→1 且零 after。测试没有实际 ADB、设备或 Gradle 调用。

真实 CI **尚未运行此实现**；不能据离线测试声称后 11 配置已采到、M2 已通过或 Editor IME 已修复。后续仍需检查本次来源、实际隔离证明、原失败保留、完整矩阵实际执行数及 G1–G4 原图。
