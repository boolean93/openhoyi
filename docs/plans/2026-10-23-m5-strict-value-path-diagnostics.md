# M5 strict 值树拒绝：精确路径与类型补证

- 范围：定位comparison summary实际不支持的JVM值；本轮不改JSON类型白名单、不把任意对象toString作为修复。
- 依据：`build/task-first-evidence/ci-4076c2d/m5-first-failure-diagnosis.md`、实际observation/ComparisonSummaryTable/TaskTypographyEvidence与现strict writer。run39906804820，head4076c2d862f94608fa6ec3d405c91d23a6b6867c，ZIP SHA256 `c64ff096375ef23c4af4a0d3a5d8d4317b277c808d66a3cd583f79074e3130fc`。
- 分支：当前授权feature/task-first-ui→develop批次，遵守[git-workflow](../git-workflow.md)、[task-template](task-template.md)，root合批构建推送。Curve/Scale owner文件不动。

## 结论

History/HistoryDetail已实际完成大汇总写盘；首败是comparison missing-samples case PASS之后strict拒绝值树，不是新OOM。`observation().put("text", text.text)`确实保留CharSequence对象；ComparisonSummaryTable前两行使用SpannableString且带真实MetricAffectingSpan，属于强静态候选。另blankNotesPlaceholder直接存taste.text只在no-notes分支，不是此次首例候选。

旧case/proof JSON已将对象字符串化，重解析不能恢复JVM类型。**当前只批准拒绝时path/class探针，不直接将某字段改String或关闭strict。** 下一次原写点运行给出实际路径后，才批准明确的文本字段快照修复；不把源码候选写成已测唯一根因。

## Sol最小实施范围（三文件）

1. `WindowProofStreamingWriter.java`：仅在现strict且streamable=false的拒绝分支，追加只读的首个不支持节点路径与精确class诊断。原支持判定、编码、字段顺序、byte格式、预检时机、临时文件和原子发布不变。可在失败后遍历一次找首个节点，不让成功路径多遍历树；不能序列化整树或转换节点。使用与原streamable完全相同的类型/有限数字规则，JSONObject.keys和JSONArray原顺序，记录JSON Pointer式路径（键的~与/正确转义，数组用index）、class.getName及拒绝类别；不调用未知value.toString/反射私有字段，不输出对象内容。
2. 保留 `JSONException: Unsupported strict JSON value tree` 原前缀，附结构清晰的path/class/reason即可沿现Kotlinfailure与suppressed落盘，不需增加测试capture或独立文件。探针自身失败必须仍抛原strict拒绝异常并附suppressed，不能把探针错误变成主因；无canonical替换/whole-tree fallback。原数据仍原样未写入，不能将此次失败记PASS。
3. `scripts/WindowProofStreamingWriterOracle.java` 与 `scripts/test_window_proof_streaming_writer.py`：扩展现SDK34真实org.json离线oracle，构造非String CharSequence（如StringBuilder及明确标注的最小自定义CharSequence）放在嵌套text路径。证明SDK toString(2)输出引用化文本，现strict仍拒绝、原canonical未变、path/class精确。此Java样本不是Android SpannableString实测，必须标注。覆盖含~/的键、数组index、两个坏值首项顺序、自定义Number/容器子类、非有限数及其原拒绝规则；验证非String未知对象的toString不被诊断调用。保留既有32例/真实17.9MB/192MiB压力和原异常保留测试。

不用重新下载/展开M5包，不需要改ExtractionHistoryVisualMigrationChecks、产品span、TaskTypographyEvidence或runner。writer为共享类，但本次变化仅strict拒绝错误信息，不更改任何成功输出或兼容write入口行为；静态验收必须确认这一边界。

## 下一步修复条件（此轮不实施）

若新错误路径实际定位到已知TextView文字字段，可另批准在该字段采样点明确保存 `text.toString()`，保持旧JSON文字语义与编码字节，并用SDK oracle验证；这是文本语义转换，不是writer泛化接受CharSequence/任意对象。不得给View重新赋text、清除Spanned/span或改字号。TaskTypographyEvidence继续从真实Spanned及实际TextPaint/MetricAffectingSpan取证，原图/行框/baseline链不删。若路径为别类对象，先核字段语义再决定，禁止统一强转字符串。

## 验收、风险与交接

差异审查、离线oracle和diff检查后冻结三SHA交Astra，root唯一完整构建owner；普通合批推送原PR，必需CI通过后合并，不微推/force/no-verify。新CI必须保原失败及新增path/class，并分清首错和finally后续错误。没有5秒/四零/窗口身份、采样、设备写入、未知记录重发或视觉范围变更。不发版；回退仅revert诊断增量，保留原ZIP/日志。当前仅计划，未实施；此补证不承诺M5下一轮通过。

## Astra 静态终审（2026-10-23）

静态通过。独立读取三文件实际diff并核SHA：writer `641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c`；Java oracle `362dce1541859bf1caccc11ad3bc386b25877f5be6ac7349fc33dcc130b89c2e`；Python oracle `9dd816154a323f847d881efc30df97dd9c45d343dc5611709d36189a037e7ef0`。

writer唯一原路径替换为strict且streamable=false时构造详细拒绝异常；原streamable白名单、成功编码/临时文件/发布及兼容write未改。探针遵守相同精确容器/数值类型和有限值规则，按原key/index顺序寻找第一项，JSON Pointer对~/转义正确。仅报告路径、getClass().getName和拒绝类别；未知对象与CharSequence不调用toString，也不转换树或fallback。无匹配节点仍以changed_after_precheck失败，不放行；探针Throwable附到保原前缀的JSONException，拒绝仍在创建临时文件前。现Kotlin异常链未改。

已读strict-value-path-oracle.log及report.json：旧32例加7个路径/类型反例共39通过；原13控制流模型保留；真实17,877,067-byte输入的SDK/strict/ZIP SHA相同，192MiB同进程解析写出与原合成压力通过。新例检查canonical保留、无临时泄漏、未知toString不调用；CharSequence样本明确不是Android SpannableString实证。本次未重复运行oracle；独立diff whitespace检查通过。

无静态阻断，可交root统一构建。尚未取得实际comparison内存拒绝path/class，不授权字段转换、不认定SpannableString唯一根因、不签M5运行通过。只追加本节，未改实现、构建或推送。
