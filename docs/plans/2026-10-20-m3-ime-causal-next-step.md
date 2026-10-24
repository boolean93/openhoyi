# M3 IME 因果关联：最小下一步

**结论：现有证据不足以修改恢复策略。Sol 只补一次重建的 caller/token/elapsed 关联诊断，Astra 再决定是否有可修复的应用因果链。** 不再扩大 trace 种类或运行矩阵。

## 范围与 Git 边界

- 当前工作树 `openhoyi-task-first`，`feature/task-first-ui`，基线 `a47c870`；与本地 `origin/develop` 的共同祖先为 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`。这是 PR #4 的既有续修，不新建长期分支；PR 目标 `develop`。
- 本规格只新增此文件。Sol 修改下列限定文件前先检查工作树；其他人在做 M4，不撤销或提交其文件。
- 推送/合并仍遵守 [git-workflow.md](../git-workflow.md)：本地验证、普通推送当前工作分支、更新 PR、四项必需 CI 通过后才能合并。本规格本身不执行 push、CI、安装或设备操作；后续 CI 执行由主任务按授权协调。
- 不发版；不改包名、签名、版本或升级数据；不触及 BLE、秤、机器、队列、未知记录。回退用后续 revert PR 移除诊断；保留原件和原失败。

## 已知事实及剩余缺口

权威材料为 `build/task-first-evidence/ci-bcc64c1-curve/raw` 与同级 `m3-offline/{timeline.md,timeline-summary.json,schema-provenance.json,decoded-*.json}`。不改写原件。

`CurveImportActivity.restoreRawState()` 当前仅在新字段 active、focused、attached、存在 windowToken 时请求一次 show；收到成功 result 后仍等待后续 draw、IME visible、无活动动画/待布局才应用 scroll。失败时产品停在 `WAITING_FOR_NATIVE_WINDOW`，没有执行最终 scroll。原文、selection 1076–1084、focus 保持，scrollY 4885→4281，IME true→false；不能把 scroll 偏差当独立的 clamp 缺陷直接修。

现有原件已包含以下可复用信息，无须再为这些字段增加采集：

| 原件 | 事实 | 不能推出 |
|---|---|---|
| `ime-diagnostics.txt:51–60`、`input-method.txt` ImeTracker history | `28726d08` 为 `HIDE_SOFT_INPUT`；`4007e924` show 终止在 `PHASE_CLIENT_ANIMATION_CANCEL`；`d23e28eb` 为 `HIDE_SOFT_INPUT_BY_INSETS_API` 且 onHidden；`b2657f01` 同 reason 但 server cancel | request tag 不是 window token；client origin 不是业务调用栈 |
| 61 条原生 trace | show/hide 交错，新字段 ID 3，focus 为真 | pre-action snapshot 不证明动作完成；时间相邻不证明请求继承 |
| `input-method.txt` StartInput history | seq 11、12 的 server targetWin 文本相同 | 不能据此把跨进程 BinderProxy 与 app identityHashCode 等同 |

API-34 SDK `InputMethodManager.java:3038–3062` 的 `closeCurrentInput()` 也产生 `ORIGIN_CLIENT_HIDE_SOFT_INPUT/HIDE_SOFT_INPUT`，且没有 `hideSoftInputFromWindow` 的 trace 点；`:2990–2994` 的无 next-served-view 分支可调用它。这仅是候选源码路径，不是 CI 固件同版或实际调用证据。service 的 field-3 wrapper 仍标 `source-inferred`。collector 保持 `PARTIAL_OR_UNAVAILABLE`，不得借离线解码改为 PASS。

真正缺口：产品 show/result/动画与 native trace 没有共同 elapsed 时钟；旧、新 Activity 的字段、root、window token 没有同进程关联；现存测试调用点与 framework 请求没有足够明确的归属边界。

## 给 Sol 的唯一实现任务

限定修改：

1. `mobile/src/main/kotlin/io/openhoyi/mobile/CurveImportActivity.kt`：仅给现有 evidence 事件补字段，不改变任何恢复条件、listener 次序、show 次数或 UI 操作。
2. `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt`：在当前 import recreate 区间记录旧/新对象与现存测试调用点，失败时也输出证据。
3. `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ImeRecreationTrace.kt`：仅承载上述关联 sidecar/marker；复用原 trace 所有权和 finally 路径，不改 collector 门禁。
4. `scripts/check_curve_visual_migration.sh`：只有确需增加 logcat 时间格式时才改；保留当前原始 `ime-diagnostics.txt`。不增加系统属性、全局 debug 开关或清日志。

- [ ] 给现有 `imeSessionEvents` 和 `imeAnimationEvents` 每项添加真实 `SystemClock.elapsedRealtimeNanos()`；同项记录 `pid`、`tid`、Activity/field/decor 身份、field ID、windowToken 的进程内 identity 与 `toString()`（null 明确保存）。show 前后各记一个时间点，result 即使晚于 pending 结束也可记录到诊断，但仍按原条件 return，不影响控制流。已有动画回调只补字段，不新增动画控制。
- [ ] 在同一进程中，用测试持有的对象引用直接比较旧/新 field、decor、windowToken 的 `===`，输出独立的 `sameField/sameDecor/sameWindowToken`；不要靠整数哈希相等断言同一对象。重建前在 UI 线程冻结旧引用及快照，`replacement_returned` 后 UI 线程冻结新引用及快照；若 token 未附着则记录 unknown，不额外 requestFocus、不等待 token 为满足诊断而成功。窗口可能保留，不能预设 token 必须变化。
- [ ] 在该 fixture 既有 setup show、`activity.recreate()` 前后、原 before/after 对比处、后续 `unfocus()` 的实际执行处加 `callerSite` 常量与 elapsed marker；产品 show 使用 `CurveImportActivity.restoreRawState` 标签。标签只证明该应用/测试调用点，不冒称 framework 调用栈。观察旧/新 Activity 的既有生命周期/attach 事件，记录 elapsed 与对象身份；观察器只能记录，必须在 finally 移除。若为了监听需覆盖现有 listener 或改生命周期语义，停止该项并报告缺口。
- [ ] 输出 `ime-causal-links.json`：保留上述原始事件及 token comparison；引用已有 tracker/history 原文文件、request tag、origin/reason、终态。不把 request tag、IME token、window token 混为一列。无法精确映射的关系写 `unknown`，近邻关系只能标 `temporal_candidate`。
- [ ] 若需要让 tracker 日志进入 elapsed 时间轴，仅增加支持时可用的 device monotonic logcat 副本，并记录时间基准；不把 `threadtime`、host 时间或 Mock 时间当 elapsed。若设备日志时钟与 elapsed 的关系未验证，则保留未排序区，不凭壁钟换算制造精确关联。禁止通过改固件、hook 隐藏 API、JVMTI 或开启全局调试获取调用栈。

这里的最小收益是区分“请求仍关联旧字段/未附着 token”“同一保留窗口上的新 editor”“原测试 cleanup 已经发生”三类情况；不承诺有限公开诊断能恢复任意 framework 调用栈。现有 source-inferred 的 `closeCurrentInput` 候选必须继续保留未知状态。

## 验证与验收边界

Sol 先做静态 diff 审查：恢复分支、单次 show guard、原 `after.toString()==before.toString()`、IME/scroll 断言、原采集状态判定均未改；不增加 hide、retry、延时成功门禁、Mock 时钟或截图副作用。

本地仅编译：`./gradlew :mobile:compileMockKotlin :mobile:compileMockAndroidTestKotlin --no-daemon --console=plain`。若改 shell，加 `bash -n scripts/check_curve_visual_migration.sh`。编译成功只证明可构建，不是重建验收；当前任务不跑设备。

后续仅在主任务授权的既有一次 M3 CI 复现中检验：所有新增事件使用真实 device clock，前后对象比较无歧义，原失败及原件完整保留，产品 show count 仍为 1。不因新 sidecar 完整而覆盖 native collector 的 PARTIAL。

Astra 按下列规则作下一决策：

- 能通过同进程身份和调用点直接证明 show 针对错误/已失效对象，或原测试 cleanup 在断言前触发：只修已证明的具体路径，并单独写修复范围。仅有 show 早于 detach 或 hide 时间相邻仍不足。
- 新旧窗口保留、show 确认在新字段且测试 cleanup 未执行：排除这两类改动方向。禁止添加重试/强制 hide；若仍缺 framework request 继承关系，明确报告具体缺失关系并停在未归因，不再重复同样 trace。
- 若日志基准/身份字段不可取得：列出缺失字段与失败原因，保留 unknown；不能宣告诊断或 M3 通过。

交付给主任务：限定文件 diff、本地编译结果、`行为未变/原断言未变`逐项核对，以及待后续 CI 填充的新增字段说明。实现完成交 Astra 审查，再决定后续 CI 和生产修复；不在本任务内自动执行。

## Astra 静态复审（2026-10-20，Sol 三文件冻结稿）

结论：限定诊断改动静态通过，可交主任务协调后续验证；**M3 仍未通过，生产归因仍未知**。基线 HEAD 为 `a47c87090240d5b682501d84759d48f0bcf36736`，审查对象是该 HEAD 上未提交的三文件 diff，非已运行 CI 的代码。

| 文件 | 复审 SHA-256 |
|---|---|
| `mobile/src/main/kotlin/io/openhoyi/mobile/CurveImportActivity.kt` | `17d9d97faf8d78715cc8a97016cd73785c8c1be11c72e0f72864a574af0fde2b` |
| `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt` | `a8bbdb06e56fc9209cf9bbd5d005284f1c8f04aa8ce56e0cdccdf93f25d55bba` |
| `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ImeRecreationTrace.kt` | `9e074bc2b74dc013234426d64ed626632249599187d0bd6cea275521b1665ab3` |

已核对：产品 show guard、result 判定、layout/animation/IME 等待条件和 scroll 应用逻辑未改，show 仍单次；原 raw 字符串/selection/focus/scroll 等值断言未改；未新增 await、hide、retry 或 Mock 时钟。原 trace ownership/stop 与 collector 判定不变，旧原件的 `PARTIAL_OR_UNAVAILABLE` 不升级。

复审拦下并经 Sol 修正三处：`onActivityCreated` 不再提前读取窗口，改在 `onActivityPostCreated` 监听既有 decor；产品 JSON 在 UI 线程深拷贝，sidecar 变更/序列化加锁并按 revision 原子写，attach 的 decor identity 可关联前后快照；新增 sidecar 写失败单独记录 `causalLinksStatus=DIAGNOSTIC_FAIL_WRITE`，不能阻止既有 owned trace stop。所有新增 attach/lifecycle observer 在原 finally 移除；原失败保留，次要清理错误不覆盖它。

验证证据：复审现场 `git diff --check` 通过；核对 Sol 最终日志 `build/task-first-evidence/m3-causal-links-build.log`，`:mobile:assembleMockAndroidTest` 显示 `BUILD SUCCESSFUL in 29s`，74 tasks、4 executed。这是编译/打包证据，不是设备或恢复验收。审查未运行设备、push 或 CI，也未修改 M4/Window review。

限制：本轮无新运行时因果记录；current-run tracker 映射保持 unknown，未添加 wall/monotonic 假映射。后续只按本规格的决策规则判读新增 sidecar；文件再变须重新审查，不把本结论套用于其他 diff。
