# M5 Extraction case.json 单点流式写盘计划

> 执行者按 superpowers:executing-plans 执行；只批准下述一个Kotlin调用替换，验证可用临时离线程序，不修改共享writer。

**Goal:** 消除已证 STARTING case.json 一次性巨大String写盘点，完整保存同一proof。

**Architecture:** 复用已经接受同一proof树的 `WindowProofStreamingWriter.writeStrict(JSONObject, File)`；原finally、cleanup、persist次序及异常聚合完全保留。

**Tech Stack:** Kotlin instrumentation、既有严格Java streaming writer、SDK34 JSON离线oracle。

## 来源与范围

基线 `58ae69ed99c1bbde98311fed24c8bf9c056b9134`，既有develop派生 `feature/task-first-ui`，PR→develop。按 `docs/git-workflow.md` / `task-template.md`，root统一构建提交推送，不微推、不发版。

已实读 `ci-58ae69e/m5-first-failure-diagnosis.md`（SHA `1e1d8950702ad099e06ade0cb1c739374a0d5df69cb1c1499346be8c1fc25343`）及实际源1540–1699。push39915609525/source58ae；PR39915616104/tested merge13b02f96f0240cdbcf9b353bed2b803c64926a94。两run同在light1.3 Extraction STARTING `proof.toString(2)` 申请75497480B而OOM，case.json未成；外层strict summary已保同一个cases[0]。STARTING机械PASS不等于实图接受，后续未到不签。

严格writer当前SHA `641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c`，已核该接口在写临时文件前拒绝非白名单树，再完整UTF8写出并原子替换；本项不增加任何fallback或支持类型。

## 唯一实施文件与步骤

`mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`，仅 Extraction 特殊态 finally 的原1687行：

- [ ] 先冻结原SHA及原源码片段；确认同工作树其它人的M7/IME/Settings修改不触碰。
- [ ] 仅将：

```kotlin
try { File(caseFolder,"case.json").writeText(proof.toString(2));persist() }
```

替换为：

```kotlin
try { WindowProofStreamingWriter.writeStrict(proof,File(caseFolder,"case.json"));persist() }
```

- [ ] 不移动 `scope.cleanup()`，不改变proof状态赋值、每case写次数、成功后persist调用或外层finally persist。case写失败仍不执行同try后面的persist；外层finally仍尝试summary，并保原failure优先/suppressed/相同Throwable不自抑制。completed只在全部case正常结束后设true。
- [ ] 不修其它JSON写点。History `caseDirectory`同形点目前只有静态潜在风险，本计划不顺带修改；不因存在潜在风险扩大到全repo序列化替换。

## 必要验证

- [ ] 在已保存push原ZIP中定向读 Extraction special manifest 的cases[0]（STARTING），不全解包、不修改原件。用SDK34 JSON解析为实际JSONObject，按原SDK `proof.toString(2)` 与当前writeStrict比较UTF8字节/长度/SHA，记录ZIP member、原summarySHA、子对象oracleSHA。不要将父manifest字节SHA冒称子case的SHA。
- [ ] 正常堆计算期望后，同一个实际子对象树在192MiB下由strict writer写出，核字节等价/完整发布；不得只用小synthetic对象代实际STARTING大树。若本机因JSON解析自身内存不足，准确报告，不改heap门槛或删字段凑通过。旧oracle54例/13flow可以复用既有结果，不要求重新研究writer。
- [ ] 静态逆替换应精确恢复原KotlinSHA；writerSHA保持上述不变。核无多余diff，`git diff --check`。针对本处case写失败、原case失败+写失败、cleanup失败+写失败，沿现代码确认原Throwable对象与suppressed/外finally persist，不额外改控制流。
- [ ] Astra窄静态审核后root统一双包构建/下一批PR CI；新原件需有STARTING case.json完整SHA且summary对应case相同，观察是否进入后续case。只有真实执行才关闭该OOM；离线等价不签整个Extraction/M5或其它配置。

## 风险、回退与未完成

无产品/UI/5s四零/业务门槛变化，无设备写入、重发、停水或未知记录处理。回退只撤这一行，保旧FAIL日志与ZIP。新writer保已有canonical文件的原子失败语义，不删除旧原件。宿主font_scale absent→present1.0为独立后错，仍未关闭。旧SpannedString首拒已越过仅限已证路径，不据此签全部文字节点。当前只批准执行范围，未改源码或构建。


## 2026-10-24 Astra 独立窄静态验收

本项静态接受，可以并入 root 统一双包构建。独立核 `git diff` 仅 1687 行调用替换；以当前内容逆替换精确等于 HEAD 原文件，原 SHA256 `8004099e6a6b70736e935ee3a33ecceb13bd3d17ebfc79ec6e66c9e1147169d9`，新 SHA256 `e32ad6c65cccc1435b4fc407e08b6f9acebad31fa29690b6179cac5f1ece003e`。共享 writer SHA256 `641ffbc5536f1a7d1278133548371d353e2dd673b624022be5a989695a2d7a2c` 未变；`git diff --check` 退出 0。

沿现代码核三条失败路径：case 写失败成为 failure；原 case 失败或 cleanup 失败存在时，写失败仅追加 suppressed，并保相同 Throwable 不自抑制。case 写失败不执行同 try 的 persist，但外 finally 仍执行 summary persist；completed 仍只在全部 case 正常结束后置 true。没有移动 cleanup、改状态或触碰 History 同形写点。

独立只读核 Sol 离线程序 `/tmp/ExtractionCaseOracle.java` 及证据 `build/task-first-evidence/ci-58ae69e/extraction-case-streaming-offline.json`：正常堆分支对 SDK `one.toString(2)` 与 strict 输出执行 Arrays.equals；192MiB 分支在同一进程解析原 summary 并写同一 cases[0]，按正常分支长度/SHA核结果。定向读取原 ZIP member 与临时输入逐字节相等，summary 为 25,572,256B / SHA256 `2db4666d60ad4cb88a2fd4dd38b0915424bbd1c1837e04a60e23ddfd231beccb`；独立重算两份输出均为 23,318,106B / SHA256 `997428dbec51241e4f0cff86d645e76de98ec6cb349bf9f7f9f2ecac352720ae`。member 是 `raw-device/58ae69ed99c1bbde98311fed24c8bf9c056b9134/tablet-user-landscape-light-font-1.3/extraction/special-states/manifest.json`，不是子 case 文件。此为核验已保留运行材料，未另占 root 构建。

结论仅此写盘替换与离线等价；Android STARTING 新 case.json、后续 case、font_scale 后错及整 M5 均未关闭。不发版、不安装、不操作设备。
