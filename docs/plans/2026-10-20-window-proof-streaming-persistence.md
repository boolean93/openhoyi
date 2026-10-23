# 单份 Window proof OOM：最小无损写盘方案

## 结论与证据

不是 708 的 summary 重复嵌套。本次 `643d828` Scale PR `39433459427` 在 Connection 的 `forty-gesture` 内，单份 `writeProof(true)` 的 `proof.toString(2)` 扩容失败；catch 中再写 FAIL proof 的同一序列化也 OOM，作为 suppressed 留在原错误后。栈落 `NativeWindowFrameEvidence.kt:255/559/578`、`ScaleVisualMigrationChecks.kt:921`。

原件：`build/task-first-evidence/ci-643d828-scale-pr/raw`，artifact `12268859502`，root已核 ZIP SHA `49e1b906e9ee66d19acfd1aa298f0a101c20a021d6267abe9a09dd0594aca153`、484 entries CRC；实际 tested merge `4f990722c0181504b665ab1fe91ac60832619d18`，不可写成 head `643d828` 同一提交。

本次只读核对原文件而不重复完整性工作：

| 实际文件/现象 | 数值与边界 |
|---|---|
| forty-gesture timing sidecar | 恰 2 次 attempt；两次 proofWrites 均完成 build，但 serialization/fileWrite 为 null |
| forty-gesture | 有原 window PNG及6份留存角色PNG；**无 frame-sync.json、无 geometry.json**，不可称正式 accepted |
| 同配置 Connection 主 proof | 5,333,367 bytes；普通长名 proof 5,343,490 bytes，均实际存在 |
| Connection failure-context/geometry.json | 7,336,845 bytes，40 行状态的独立失败诊断，不是原 accepted geometry |
| 实际 OOM | 申请 75,497,480 bytes；free 25,165,824、距 OOM约40MB、growth limit201,326,592。申请大小是 StringBuilder backing-array扩容，**不是已知最终 proof 文件大小** |

`geometry()` 同时留页面 nodes 与 attachedWindows；每个 observation又留子孙文字/可见性，因此40行证据树明显大于普通3行树。单份 proof 保留所有 attempts 的 before/after；2 attempts 最多四份大树再被 pretty-indent。上述重复是当前完整证据合同的一部分，本批不删除、不改引用结构、不只保 accepted attempt。失败 proof根本没写出，不能捏造它的精确最终字节数。

## 最小实现边界

来源为当前任务分支 `feature/task-first-ui` 的冻结候选，沿现分支合批 PR→develop；执行前 owner核实际分支名，以 `docs/git-workflow.md` 为准。不合并、不发版、不安装、不操作硬件；无签名/升级改动。回退仅 revert 本组 writer/caller，不改协议。

唯一实施 owner 建议 Sol_bcc；先只改：

1. `NativeWindowFrameEvidence.kt` 的终态 `writeProof` 序列化/落盘位置。
2. 新 androidTest 纯 Java streaming writer（与已有 JSON equality helper 分开职责）。
3. 官方 SDK34 org.json oracle 验证脚本/Java harness，复用现 SDK源加载方式，不另引 JSON 实现。

不改 Scale业务/40行/手势/recreate/summary/runner，不改变 geometry 构造、匹配算法或采集次数。现独立7.3MB失败geometry已经成功写出，尚无证据要求本批把所有其他 `toString(2)` 一并替换；后续只有实证再收口，不提前重构全仓。

## Writer 精确合同

- 直接把现有同一 JSONObject树流式写入有界 BufferedWriter/UTF-8 OutputStreamWriter，不先生成整树 String/StringBuilder/byte[]，也不拼每个完整子树字符串。额外内存限制为固定IO缓冲+容器遍历深度+单个已有标量处理，不随总输出长度增长。
- 输出必须与 **Android34 `JSONObject.toString(2)` 后 `writeText` 的 UTF-8 bytes逐字一致**：对象插入顺序、数组顺序、所有字段/NULL、2空格缩进/换行/冒号/空容器、slash及控制字符转义、Unicode/代理对，以及 `JSONObject.numberToString` 的负零/整数化/非有限数异常语义。不能仅比较 parse 后字典，不用近似数值或 sorted keys。
- 常见精确 JSONObject/JSONArray与原生标量可走流式路径；容器子类/custom对象/非标准 Number等若无法证明与原实现完全一致，整对写盘路径退回原 serializer（先预检，不能已写半文件后混接）。不增加静默删字段或新 accepted 门禁。任何原本会报错的值仍报错，不能输出伪 null。该回退只保兼容，不宣称替未知自定义树解决 OOM。
- 原始 proof树、CapturedFrame返回对象和 schema/protocolVersion不变；host所有读取/引用/SHA算法无需改。成功写后SHA自然对应相同字节。
- sibling临时文件完成写入、flush/close且同目录 rename成功后才发布 canonical proof/返回成功。任一写入/close/rename错误不把截断文件当正式 proof；清理仅本次 owned temp。不可覆盖/删除其它scope原件。
- 保原首错：采集先失败时，writer失败作为 suppressed；采集匹配通过但持久化失败时，整体仍 FAIL，不能因为内存里 accepted=true就返回成功。继续保现失败PNG留存与原异常，不改为只写精简错误proof冒充完整proof。
- 仍只在 terminal持久化，不恢复 attempt中间写盘。**原 started/deadline/5000ms、全geometry比较、四零差、identity、颜色、mapping一字不放宽**。写盘原本就在匹配预算外，流式不把采集挪出预算，也不让预算已过的 attempt变 accepted。
- proof timing sidecar可把旧 serialization/write拆分记为 streamed_write总时长并明确字段定义；不得填虚构0或把它当Android同步性能改善证明。若沿用原字段，明确两者不能独立分解，不混报。

## 验证及接受条件

1. 官方 SDK34 oracle小树覆盖全上述token/转义/数值/NULL/插入顺序、空/嵌套容器、custom回退及异常；逐UTF-8 byte比较新旧，含未配对surrogate与跨writer缓冲边界的合法代理对。
2. 用本批实际成功 Connection主/长名 proof与大失败geometry做原件回放；把大geometry放入2-attempt before/after容器只作**标明合成的压力输入**，不得写成丢失的 forty原proof。host足够内存时旧serializer作为oracle；保输入SHA、输出SHA/长度。
3. 注入输出流中途写失败/close失败/rename失败：canonical无半份成功文件，首错/secondary suppressed合同成立；失败不能输出accepted成功路径。
4. 证明固定buffer和不调用整树toString，而不是只拿主机heap曲线推断Android；有限heap压力测试记录新路径成功及相同bytes，旧路径OOM仅作本机对照，不替实机验收。
5. root统一 `assembleMockAndroidTest`；随后原固定CI场景不新增、不重试。正式关闭需真实 forty-gesture/forty-recreated及原settings-return完整proof/geometry/SHA和业务恢复通过；原643 OOM保留FAIL。独立已采 Scale状态可另作图审，不受此失效proof洗白或否定。

## 本轮状态

仅规划、无实现改动。IME因果未知与本问题无关，继续保留；snapshot `PASS_EXACT`恢复事实也不覆盖业务Exit1。待root分配owner、冻结后静态审。

## 643 后候选静态终审

Astra 已核 caller terminal 唯一写入差异、Java 全量预检/流式字符串转义/有序容器/numberToString、整树兼容回退、close 后 atomic publish 与 owned temp 清理，以及 SDK34 oracle 实际来源与失败注入。此范围静态无剩余阻断，可进入统一 AndroidTest 构建。caller 未改变匹配 deadline、全部 attempts、geometry 比较或四零差；旧拆分计时明确 null，新 streamedWrite 包含序列化/flush/close/原子发布，未伪报为零。

冻结 SHA256：caller `8b29e2c6e96c6e1dfeba318156b7390bc8759ec3f15600a2fce7ecefd9c5fbe1`；writer `0dfc0c7ac1a199a995dc64e34d8c646a7a962bb796c4c5c69faac838a9066528`；oracle `dfbbf1abd2ca8a95996719f2496cc9ae37222d53b52b98ebc8b242b123923cbd`；script `04fcba276365726fc7b86f7c0b168eba705f0776d86cfb6a59aa45f4f70c833c`。

读取的 `window-proof-streaming/report.json` 记录18组逐UTF-8字节 oracle 及192MiB host压力检查。5.33MB/5.34MB真实完整proof、7.34MB失败geometry来源明确；35.42MB两attempt压力树是合成件，不是未写出的forty原proof。此处不声称 Android 内存/原子文件系统调用或原场景已经实测成功；原643 OOM FAIL与forty缺证保留。后续按原CI验证，不增加heap、不删字段、不减少图。
