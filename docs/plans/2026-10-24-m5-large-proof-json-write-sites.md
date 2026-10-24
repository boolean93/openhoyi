# M5 大型证据 JSONObject 写盘点 Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 顺序执行。Astra规划/验收，Sol实施，root统一构建/推送。只改列明调用，不改writer，不替换全仓JSON。

**Goal:** 一次收口 ExtractionHistoryVisualMigrationChecks 中携带完整几何/全页文字快照/帧proof的大型JSONObject磁盘写入点，避免再次为同一种整树String分配逐点跑CI。

**Architecture:** 在原写盘语句位置复用 `WindowProofStreamingWriter.writeStrict(JSONObject, File)`。同一对象、同一路径、同一调用次数和原控制流；不删字段，不改嵌套结构或JSON根类型。

**Tech Stack:** Kotlin instrumentation、现有Java strict streaming writer、Android SDK34 JSON离线字节oracle。

## 1. 基线与证据

- `feature/task-first-ui` / PR #4 → develop；共同develop基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；本次只读HEAD `2f784ec502b8d0651e02e1de8cc86db8714d4ac2`。遵守 [git-workflow](../git-workflow.md)。不发版、不设备操作。
- 用户整体UI目标下的测试证据可靠性修正，不将采集失败改称产品通过。最新 [只读诊断](../../build/task-first-evidence/ci-aac5c91-m5/first-failure-diagnosis.md)：PR run40054362666、head aac5c91、测试merge/source1874942f56f1e60b8bb3fa13524c35cadaffcbc9，`UNKNOWN_live` 的 `chart-header-reachability.json` 原1209行 `toString(2)` 一次申请37,748,744B而OOM。
- 旧STARTING/STOP_REQUESTED `case.json` 已完整落盘（19,413,580B/22,556,172B）且等于summary子对象；证明上一单点writer运行越过，不证明完整M5。该run另有font_scale absent→present1.0恢复FAIL，必须单独保留，本计划不修宿主恢复。
- 原ZIP `ci-aac5c91-m5/artifact.zip` SHA `e69c5b6c36e6f4db444c637197a359de238164913cdbb6a552b7009528218d69`，1988项CRC通过；不全包解压。
- writer `mobile/src/androidTest/java/io/openhoyi/mobile/WindowProofStreamingWriter.java` 冻结SHA `641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c`。沿既有strict白名单、UTF8、2空格、临时文件/原子发布及异常清理契约，不新建fallback/JSONArray入口。

## 2. 唯一实施文件：八个明确写点

`mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`。下表行号为本次基线定位；按函数/路径核对，不按移动后行号盲替换。

| 原行/函数 | 文件/传入对象 | 纳入理由 |
|---|---|---|
| 987 `diagnostic` | `geometry.json`，原 `geometry(activity).put(...).put(...)` | 全Activity几何/文字/图表诊断；保unsynchronized诊断标签与原main线程位置 |
| 1042 `capture` | `geometry.json`，`target` | 完整captureTarget，包括全geometry、typographyEvidence及baselinePairs |
| 1043–1044 `capture` | `${activity.javaClass.simpleName}-typography.json`，`target.getJSONObject("typographyEvidence")` | 全页文字/布局快照，是上一大target的完整子树，不是小业务摘要 |
| 1092 `requireExtractionPlot` | `extraction-plot-budget.json`，`evidence` | 显式嵌入完整 `frame.proof`，不是只含plot尺寸 |
| 1209 `extractionChartHeaderReachability` | `chart-header-reachability.json`，`it` | reached逐项嵌完整frameSync；本次实际OOM点 |
| 1519 `extractionSpecialSupportReachability` | `extraction-support-reachability.json`，`it` | reached/reachedText及restoredFrameSync嵌完整proof |
| 1888 `extractionJournalUnavailable` 所在dialog写段 | `geometry.json`，`dialogFrame.target` | 完整dialog几何/全原生文字与activityState；保持在原proof检查之前写盘 |
| 2160 `historySpecialStates` 的case finally | `case.json`，`proof` | 已 `proof.put("frameSync",captured.proof)` 的完整saved-case证据；保原persist和异常聚合 |

所有点只用此替换模板：

```kotlin
// before
File(folder, "name.json").writeText(objectExpression.toString(2))
// after（objectExpression 与路径采用上表各点的原表达式）
WindowProofStreamingWriter.writeStrict(objectExpression, File(folder, "name.json"))
```

第1点必须保持原两个put和 `main { ... }`；第5/6点保持原 `.also { ... }` 返回同一个对象。第8点精确为：

```kotlin
try { WindowProofStreamingWriter.writeStrict(proof,File(caseDirectory,"case.json")); persist() }
```

不得顺带合并/提前写其它文件、搬动checks、返回新解析对象或修改对象构造顺序。

## 3. 明确排除与保留

- 已strict的Extraction case原1687、special summaries原1547/2023、顶层manifest原2272原样保留，不重复迁移。
- `baseline-alignment.json` 原1045是JSONArray，dialog `typography.json` 原1889也是JSONArray；**不改根类型，不包一层JSONObject，不扩writer API**。
- 小/局部证据原531 extraction-business、700 fixture-isolation、804 history-initial-chart、887 history-preview-reachability、923 standalone-history-plot、1253 history-business、1280 comparison-business、1313 detail-business、1374 extraction-reachability、1538 id-contract、1632/2204 fixture、1997 comparison-reachability本次不改。已逐段区分：history/standalone两个plot仅局部Canvas/尺寸；comparison/detail等目标记录不嵌完整frameSync。不能因为名字带reachability就全替换。
- 内存比较/克隆的 `toString()`、JSONArray构造、失败插值、`TaskTypographyEvidence`与`NativeWindowFrameEvidence`内部写点不在本范围。现JSONObject树本身、重复proof持有、解析堆占用仍可能占内存；本计划只消除明确整树磁盘序列化String，不承诺所有OOM都消失。
- 原finally、cleanup、scope恢复、cases状态、completed时点、case→persist顺序、primaryThrowable/suppressed优先级不动。strict拒绝仍失败，不默默改兼容writer、toString fallback或删陌生类型。

## 4. 实施与有限验证

- [ ] Sol冻结原文件SHA和八片段，只替换八个写点。不得改本页产品源码/算法/业务、不触其它owner文件。
- [ ] 窄静态检查每个根均JSONObject；输入是既有proof/geometry JSON树，只含writer现白名单叶值。若发现不支持实际叶类型，先报告精确字段/生产者，不改变writer契约或用 `.toString()` 泛化解决。
- [ ] 优先用既有SDK34 oracle与原件验证：定向取该ZIP已成功产出的最大 `chart-header-reachability.json` 或 `extraction-support-reachability.json` 一份含完整proof的真实大树，以正常堆比较SDK `toString(2)` UTF8字节与strict字节/长度/SHA；同一树192MiB下解析并strict写出比较SHA。未知case的OOM原件可能未生成，不能伪造“读取UNKNOWN_live成功”。源member/字节/SHA和实际选中的case必须记录。
- [ ] 同时用已产capture geometry及其typography子树（只读定向选一份）核SDK字节等价，确认这类捕获根也被strict接受。其余八点沿相同实际对象生产链静态核验；未运行的saved特殊case/dialog不伪称实际越过。无需重新跑writer既有54例/13flow或新造巨大压测矩阵。
- [ ] 对第8点确认：case写成功才在同try内persist；写失败时原caseFailure优先、同Throwable不自抑制、外finally persist保留；不得为了“总有manifest”改变异常次序。
- [ ] 将八处反向替换回原写法后，内存中源码SHA必须等于原冻结SHA；writerSHA不变，`git diff --check`=0。计划外差异直接报root，不自行撤他人修改。
- [ ] Astra静态验收后root统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，普通推送工作分支，原M5 CI验证精确源码。不得提高heap/timeout、加sleep、mask、重试、删字段、改5秒四零或恢复门槛。

## 5. 验收、风险与回退

本次完成标准分层：八点准确替换+原件离线等价+根构建通过可合批；真实CI需观察UNKNOWN_live原写点越过、完整文件与summary字段一致，并继续保留后续最先失败。不能由writer离线通过签产品G1–G4、完整M5或font_scale恢复。

无协议/设备/库存/门禁/停止/未知写入影响，不操作咖啡机或秤。回退只revert八个调用，不删原失败ZIP/输出证据、不清数据；root按PR及必需检查合并，不独立微推或发版。当前状态：仅规划交付，未实施。


## 6. Astra八点静态验收（2026-10-24）

仅ExtractionHistoryVisualMigrationChecks八处调用变化，当前SHA256 `586a88e7c4c848fb6ffed1d599fd535014f2f290b039656b85afe234728d0255`；HEAD原文件SHA `e32ad6c65cccc1435b4fc407e08b6f9acebad31fa29690b6179cac5f1ece003e`。逐块核diff：八个JSONObject/路径与计划逐一对应，diagnostic两个put/main、capture先geometry后台typography、两个also返回对象、dialog写后检查、case写后persist及原catch/suppressed顺序均保留。两JSONArray及小对象未迁移，已strict四点未变。writer实测SHA `641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c` 未变；无产品/门槛/恢复/协议改动。静态无阻断。

离线记录 `ci-aac5c91-m5/large-write-sites/offline.json` 已读核，真实STOP_REQUESTED header 11,996,391字节，SHA `1bdadeaf72b963546e09d65b7ff55544dd6f310cf8712303b4094e5a74030417`；正常SDK34/strict/原member等价，192MiB同树解析写出同SHA。geometry660,008字节及typography子树389,826字节同样等价；oracle源码确实比较UTF8字节，未绕过strict。未产UNKNOWN_live、saved-case、dialog原件不被此离线结果冒充运行通过。根统一Mock双包构建结论另记，原font_scale恢复FAIL不关闭。

## Root 构建验证

2026-10-24：`:mobile:assembleMock :mobile:assembleMockAndroidTest`退出0，35秒，91 tasks（4 executed）。日志 `build/task-first-evidence/ui-2026-10-24-m5-streaming/build.log`。303个Kotlin源文件构建前后SHA一致，见同目录source-before/source-after.json。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA `199b23a28e2884d55a288945ac0a4cf493077662d3d585163cc777818c97235f`。
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA `950d177683b99704784cd18d813bbfe3e1a56853ad40d9250989a00e9f5eef5c`。

仅证明编译和静态/离线契约通过；UNKNOWN_live与其后各原写点仍待新精确CI验证，不签全M5或视觉通过。未设备操作、安装、合并或发版。

Astra独立复跑：补全原SDK classpath后，`-Xmx192m`同一header解析+strict写出11,996,391字节/SHA一致，exit0；首次仅用oracle/classes因缺org.json失败，未影响原件或源码。根另报Mock双包构建成功35秒、303份Kotlin前后SHA一致，日志 `ui-2026-10-24-m5-streaming/build.log` 与 `apks.json`；本批静态/离线/构建层可合批，实际CI边界如上。
