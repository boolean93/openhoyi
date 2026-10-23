# M5 已知文本字段 JSON 快照修复计划

> 执行者：Sol；使用 executing-plans 逐项执行，Astra 静态验收；root 独占完整构建与合批推送。不是独自工作，不覆盖他人文件。

**Goal:** 在已确认的 TextView 文本采证点保存 String，消除 SpannedString 进入严格 JSON 树；不改变实际渲染。

**Architecture:** 只在两个已知文字值写入 JSONObject 的边界调用 `.toString()`。strict writer 的白名单、拒绝路径、成功输出格式及异常保因不改，真实 View/span/Layout/typography 仍从原对象采集。

**Tech Stack:** Kotlin Android instrumentation、SDK34 org.json、现有 Java/Python 离线 oracle。

## 范围与证据

遵守 [Git工作流](../git-workflow.md) 与 [模板](task-template.md)。当前工作分支 `feature/task-first-ui`，PR目标develop；本轮基线 `588855c96fdceec79bf87f0fd5950f33fa6aff1c`，沿已授权迁移工作树，不另开/重写共享分支。

已实际读取 `build/task-first-evidence/ci-588855c/m5-pr-failed.log:212`：PR39911037181首错为 `path=/cases/0/frameSync/attempts/0/geometryBefore/target/nodes/0/textLineBoxes/27/text; class=android.text.SpannedString; reason=non_string_char_sequence`。栈到 historySpecialStates.persist line2023，后续finally同错以suppressed保留。该运行时路径明确指向文本字段，不再只是推测；具体第27项的业务文字仍须原artifact定位，不能凭索引猜是哪个span。原artifact下载/CRC由root维护，本计划未将尚未检查的ZIP当已验。

源码 `observation()` line120直接 `.put("text", text.text)`；同文件no-notes comparison分支line2113直接 `.put("blankNotesPlaceholder",taste.text)`，后者虽然尚未首败，但字段明确是已断言为“—”的文字，属于相同最小修复边界。全文搜索该文件直接 `.put(... .text)` 仅这两处；不泛化处理semanticId/tag/任意对象。

## Sol可执行文件与步骤

允许改动三个文件：

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`：仅下列两处值转换。
- `scripts/WindowProofStreamingWriterOracle.java`：已知文本边界的SDK字节等价/严格拒绝反例。
- `scripts/test_window_proof_streaming_writer.py`：两处边界回归检查及原oracle运行；不增加workflow依赖，不把本地artifact测试称CI覆盖。

- [ ] 先扩展离线红例：源检查要求两个字段保存String，当前代码应红。Java oracle沿现有SDK34 org.json样本，将非String CharSequence置于嵌套`text`和`blankNotesPlaceholder`，未经转换strict必须拒绝且path/class保留。转换后必须等于SDK原CharSequence树的`toString(2)` UTF-8字节。覆盖中文、破折号、引号/反斜杠/换行、补充平面字符及空文本；不将StringBuilder模拟样本宣称为真实Android SpannedString运行。
- [ ] 最小源码变更如下，保持调用所在main采证时点和方法体其他字段不变：

```kotlin
JSONObject().put("semanticId", text.tag).put("text", text.text.toString()).put("fullViewVisible", whole)
// no-notes comparison branch:
proof.put("blankNotesPlaceholder",taste.text.toString()).put("blankTasteDisclosureDisabled",true)
```

不得给TextView赋新text、removeSpan、修改ComparisonSummaryTable或TaskTypographyEvidence；不能把整个JSON树递归转String、缓存跨帧文本、改捕获顺序或跳过拒绝。no-notes原 `taste.text.toString()=="—" && !taste.isClickable` 断言原样保留。

- [ ] 运行 `python3 scripts/test_window_proof_streaming_writer.py`：原39例、13控制流模型、真实17.9MB字节等价/192MiB及新增转换样本均通过；若原件缺失应明确阻断，不跳过。补反例证明其他未知对象/原未转换CharSequence仍被拒绝，strict writer源码SHA与实施前一致。
- [ ] `git diff --check`；冻结三个SHA及实际diff交Astra。源码逆替换两处应精确恢复实施前Kotlin SHA，用此验证未改采证/业务逻辑。root再统一完整编译，不由Sol启动Gradle或单独推送。

## 验收与保留

静态审核重点是“JSON文本语义变String”而非“真实Spanned变String”：原渲染span、字号、TextPaint、baseline、Layout行框和同帧target证据仍在；不要求删span来规避异常。SDK字节oracle证实原文字编码语义，不证实Android渲染；真正SpannedString运行证据来自本次失败日志及后续精确源码CI。后续CI必须越过本路径且保留原比较页case/summary、类型探针与任何新首错，不能靠构建/离线绿就宣称整M5通过。case异常、persist异常、suppressed和completed时序全部不变。

协议与设备风险：仅测试JSON快照，不改产品、设备命令、未知重发、样本、48dp、96dp、5秒四零差、窗口身份或视觉合同。发布：不发版。回退：仅revert这次测试/验证增量，保留原失败日志与ZIP，不清记录。

推送与合并由root完成：检查共享工作树→相关验证/完整构建→普通合批推送现工作分支→更新PR证据→必需CI及评审通过后合并develop；不force/no-verify/微推。当前状态：计划完成，尚未实施；无需求澄清阻断，可交Sol按上述三文件范围执行。

## 原件来源澄清（2026-10-23）

后续已读 `ci-588855c/m5-first-failure-diagnosis.md`：本轮PR实际tested merge为`f1d6f21771c6ab2f86ba18f470777ec9e8ca39b8`，head为588855c；首拒是**History light1.3 missing-samples**，root的textLineBoxes[27]为`product/history/list/m5-history-extra-35/metadata/taste`，序列化文字为空串，运行时为SpannedString。不得与4076 Comparison首拒混为同页同case。可读JSON已经丢失运行时类型，不能据空字符串反驳日志类型。诊断所记旧canonical summary存在不证明本次strict发布成功；两处明确文本边界的批准范围不变。

## Astra 静态验收（2026-10-23）

通过本计划限定静态审核。独立读取实际三文件diff并核SHA：Kotlin `8004099e6a6b70736e935ee3a33ecceb13bd3d17ebfc79ec6e66c9e1147169d9`；Java oracle `737223cc50cb788bbbb3429f2f91d209c22fa7595504bd52768dcbf8c8f15c98`；Python `5bf2ab26c68b7007d24a546b16c778cb4be19060b307b9a180ea304c54159b2b`。writer仍`641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c`，未改白名单/编码/拒绝。

Kotlin仅批准的两处`.toString()`；独立逆替换后字节精确等于HEAD原文件，SHA`b8a2b1bb9c177e74c900612cde9b5cf34d7ecfe817d1ee837636cec78750a958`。真实TextView、span、Layout、typography、主线程采样时点和no-notes原断言不变；无任意对象fallback、业务改动、异常吞掉或门槛弱化。

Java新五类文字各验证原text拒绝、原placeholder拒绝及转换后strict与SDK字节相等，共15例。Python明确检查旧39+15=54及两源码边界；旧未知对象toString不调用反例保留。已读取`ci-588855c/m5-text-json-snapshot-oracle.log`：54例、13控制流模型、真实17,877,067B SDK/ZIP SHA一致与192MiB同进程输出通过。本次未重复运行压力测试；这些自定义CharSequence样本明确不冒充Android SpannedString实机执行。精确源码后续CI仍待，不能据静态通过签本轮M5运行/视觉通过。
