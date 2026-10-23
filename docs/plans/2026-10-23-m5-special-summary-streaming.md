# M5 特殊态累计汇总无损流式写盘

- 需求与范围：仅修两个特殊态累计 summary 的整树字符串写点，并保护这两条调用链的原始失败；不泛改 JSON。
- 来源/工作分支：当前已授权迁移批次 `feature/task-first-ui`，基线 `8b5237989eaae99181ff137b8873896eb96bda7c`，本次失败被测 merge/source `44d34c52a3f3b18537f59cb92607bcc7c6252d09`；PR 目标 develop。沿本批串行执行，不覆盖其他 owner。遵守 [git-workflow](../git-workflow.md) 与 [task-template](task-template.md)。
- 依据：`build/task-first-evidence/ci-8b52379/m5-first-failure-diagnosis.md`、`m5-summary-write-sites.md`；已独立核当前两个函数、strict writer 与原 ZIP 汇总。

## 结论与证据边界

允许同时替换 `historySpecialStates`、Extraction 特殊态函数的两个局部 `persist()`，共用现有 `WindowProofStreamingWriter.writeStrict(summary, destination)`。两者同样累积完整可变 case proof/frameSync，逐案重写整个 summary；不必等另一分支再发生相同大字符串分配才消除它。**只有 History 本次 OOM 已证，Extraction 是源码同机制风险，不能记成实际失败。**

History 首格第四案 `sample-gap/case.json` 已机械 PASS，累计 manifest 仍为 RUNNING；栈定位 `historySpecialStates$persist` 的 `toString(2)` 请求 75,497,480 bytes。顶层 strict manifest 成功保存 FAIL，不是本次首因。后续 water-reset、Extraction 和其它配置尚未走到；font_scale 清理失败独立保留。

原 ZIP `m5-artifact.zip` SHA256 `1d9e89e5a9e2e39200d332ef63bc6846f436d4314c91f7dae14b97636df23758`。真实输入仅选择读取 ZIP 成员：

`raw-device/44d34c52a3f3b18537f59cb92607bcc7c6252d09/tablet-user-landscape-light-font-1.3/history/special-states/manifest.json`

其大小 **17,877,067 bytes**，SHA256 `c7efdaf9d496ddf3f800a32abe643ce27759505ebd0ca2174fdc4eb889873755`。这是最近成功落盘的累计 summary，不是失败时未写出的更新树，不得冒称恢复了丢失原件。

## Sol 可执行范围

仅以下三个文件：

1. `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`：两个局部 persist 调用现有 strict API；两特殊态函数内必要的异常保留。
2. `scripts/WindowProofStreamingWriterOracle.java`：追加真实大 summary 与异常保留验证入口。
3. `scripts/test_window_proof_streaming_writer.py`：选择提取该唯一输入、校验身份、驱动验证及记录结果。

现 Java writer 编码器/API 不改；其他 JSON 写点、生产、runner、Gradle/heap 配置、采证和业务逻辑不改。单案 `case.json` 保留现 `toString(2)`，本项不声称消除单案或常驻树的全部内存风险。不拆 proof、不改引用/schema，不删字段、不排序 keys、不压缩或改变两空格格式。

## 写出顺序与异常合同

- 成功路径完全保留 put 顺序、cases/entries 原对象引用与所有 persist 调用位置。History 保留循环前首次写、加入 RUNNING 后写、每案 finally 写、最终 finally 写；Extraction 保留每案 finally 与最终 finally 写，不新增循环前写或重试。
- 每案仍先写 `case.json`，再 persist；若前者失败，仍不额外调用该处 persist（原顺序中的后项未执行），只进入原有最终 finally 写点。不得因这次修复增加失败时写盘次数、重跑 case 或 cleanup。
- 两函数外层记录正在传播的主异常。最终 finally 的 persist 成功则正常传播原异常；失败且已有主异常则把写盘异常附为 suppressed，仍抛原对象；没有主异常则写盘异常本身失败退出。不能因 return summary 而吞最终写盘失败。
- History 每案记录原 case 异常，再保留原 proof FAIL/failure 字段与重抛行为；将 finally 中既有 `case.json; persist()` 写序列放在保护边界内。若写序列失败且已有 case 异常，将其 suppressed 到 case 原异常；否则抛写盘异常。外层最终 persist 再失败仍附到同一主异常，不替换根因。
- Extraction 复用已有 case `failure` 与 cleanup suppressed 路径；保护既有 `case.json; persist()` 写序列，将首个写盘错误设为 failure（此前无错），或附到此前 case/cleanup 主异常。仍走原 `failure?.let { throw it }`；外层再保护最终 persist。不要为此改变 cleanup 顺序/次数或原 proof 状态字段。
- 保留 Error（含 OOM）而非只捕获 Exception；同一 Throwable 不自 suppression。不得吞写错、用旧 canonical 当本次成功、造 PASS、增加 completed=true 的到达路径。写出成功时字节不变；写失败时 strict 原子发布保旧文件并让本次调用失败。

## 验证与交接

1. 用现有未修改 Android SDK34 org.json oracle 读取真实 17,877,067-byte 输入。普通堆下比较原 `toString(2)` 与 strict 输出逐字节及 SHA；同时核 strict 输出与 ZIP 原件是否逐字节一致，若不一致先保留差异并停止，不能改 oracle 排序/归一化来通过。记录输入成员、长度、SHA，保留全部旧 oracle/strict 拒绝/fault 测试。
2. 复用既有离线 `-Xmx192m` 上限，新增真实 summary strict 路径：该进程不先生成完整 expected 字符串、不读回完整输出比较；参照普通堆已获的长度/SHA，以流式摘要核输出。记录解析与写出是否均在同一有限堆进程内。保留原合成压力测试；有限堆不足时如实失败，不调高上限。它只证明此真实值树离线路径，不证明 Android 全程或未来更大树安全。
3. 窄异常反例覆盖两个函数对应控制流：无原错+写错；case 错+每案 persist 错+最终 persist 错；Extraction cleanup 错+写错；case.json 写错时该处 persist 不执行但原最终 persist 仍执行；成功路径写序与次数不变。断言主异常对象身份、suppressed 顺序、completed 到达条件、非成功退出，不只比错误字符串。可用现离线 oracle 的最小控制流 harness，必须说明与实际 Kotlin 的对应；静态终审仍须核实际 catch/finally，harness 不能代替 Android 执行。
4. `python3 scripts/test_window_proof_streaming_writer.py`、语法/`git diff --check`，冻结三文件 SHA 与实际 diff 交 Astra。不覆盖原 FAIL 原件。root 唯一完整构建 owner，相关变更合批正常推送已有 PR；不微推、不 force/no-verify、不直推 main/develop，必需 CI 与审查通过后才可合并。

## 风险、未完与回退

不改 5 秒/四零差、窗口身份、采样、状态/停止/恢复、设备编码/单位/队列/重试；不写硬件、不重发未知，不以 Mock 证明硬件成功。不发版/安装/分发。回退仅 revert 本窄增量，保留所有原件与未知记录，不卸载清数据。

当前仅规划，未修改源码、未构建或触发 CI。新运行仍须核 History 后续特殊态、其余页面/Extraction、font_scale 恢复与真实视觉。此修复不关闭这些待验项，也不承诺所有 OOM 已消除；若新首因转移，保留原始异常链后另行定位。

## Astra 静态终审（2026-10-23）

**本窄增量静态通过。** 已独立读取三个文件实际 diff、现 Kotlin catch/finally 与离线报告；不以13例控制流模型替代实际代码，不等于 Android 运行或视觉验收通过。

- 两个局部 persist 仅改为现 writeStrict；writer SHA256仍为 `179632d621e6d78f30469c90de7878d1de04ff8c73d4390f3f4f973ecef21f8f`，未改编码器、原子发布或 fallback 合同。单案 case.json、其它JSON、put/数组顺序及完整proof不变。
- History 原 case Throwable 先赋给caseFailure再沿原路径重抛；case.json/persist序列错误附到同一原对象。无case错误时首写错直接抛出。外层catch保存正在传播对象，最终persist错误仅suppressed；无原错则最终写错直接抛。首次persist仍在外层try之前，失败直接退出，不新增最终写；RUNNING persist仍在案内try之前但受外层finally保护，与原写时序相同。
- Extraction 仍先记case错误，cleanup不增加次数；cleanup错误依原顺序成为主错或suppressed。每案写序列错误随后合并到同一failure，再由原failure抛出点进入外层；最终写错继续suppressed。所有新增边界捕获Throwable，且比较对象身份避免self suppression。
- case.json失败会跳过紧随其后的该次persist，最终finally仍执行；没有增加重试、写点或采证次数。completed仍只在全部case正常完成后设置；若仅最终写失败，内存completed已true是原写时序，但函数抛错、不能正常返回summary，不把旧canonical当本次成功。case/cleanup/每案写失败均不能走到completed。
- 已读 `build/task-first-evidence/window-proof-streaming/report.json`：真实17,877,067-byte summary与原ZIP、SDK34 toString(2)、strict输出字节/SHA一致；192MiB独立进程实际解析并写同一输入，按流式输出摘要匹配普通堆结果，没有在该进程额外构造expected整树字符串。旧32例、原35MB合成压力及13例异常身份/顺序/写次数模型通过。报告明确模型不是Android执行；本轮未重复运行离线测试。独立diff whitespace检查通过。
- 冻结SHA256：Kotlin `b8a2b1bb9c177e74c900612cde9b5cf34d7ecfe817d1ee837636cec78750a958`；Java oracle `d61d97a5560fcf80ab71a4b5d20589085d88b8cf1502b836e6a0f431e7e346eb`；Python oracle `71097665d4b807bf6a33e2cfcc97d2c2a4226dcb5379960a991b0ba3540fcd62`，均独立重算一致。

审查范围内无阻断，可交root统一构建/后续真实取证。History已证OOM与Extraction静态风险仍分别记载；单案字符串、常驻树、失败信息构造等其它内存路径不在此次修复证明内。后续case、全矩阵、font_scale清理与视觉缺口未关闭。仅追加本节，未改源码、构建、提交或推送。
