# M6 原证据汇总去重：708 OOM窄修

## 已证根因

push39427126224日志`build/task-first-evidence/ci-7082e04/scale-push-failed.log`明确在ScaleVisualMigrationChecks.supportReachability:748的JSONObject.toString(2)→JSONStringer.newline→StringBuilder扩容分配75,497,480字节失败，heap limit201,326,592。源码741–748将六次capture的完整target和完整proof同时嵌入results，最终再pretty序列化；capture已分别把完整geometry.json和window-frame-sync.json落盘，proof内部还包含前后完整geometry。因此这是已证的汇总重复保留/序列化OOM，不是当前失败的5s同步/坐标/视觉问题。仅日志尚不证明六份原件都完好，须待保全包确认；不追认本run通过。

同文件scaleSpecials的state、short-height/detail、connectionSpecials的state及preRecreateFrame也把已落盘同原件再次嵌入多项summary，属于同一明确结构风险，宜同一小批去重，避免只修六次后在15态汇总重现。不扩到其他runner或共享采集helper。

## 最小范围

唯一owner：`ScaleVisualMigrationChecks.kt` + `scripts/check_scale_visual_migration.sh`。不改NativeWindowFrameEvidence、app/largeHeap/5000ms/四零差/geometry字段/采集次数/顺序/PNG与原失败上下文；不改snapshot清理接线。

1. 保每次capture原geometry.json、window-frame-sync.json、全PNG、Typography/baseline及所有字段；先确认现有文件写完，再用流式SHA256计算原文件SHA，不重新parse/toString它们。
2. 聚合报告版本明确如`evidenceSummaryVersion=2`。每条只存case/tag及必要小型业务事实，加`geometryRef:{path,sha256}`、`frameSyncRef:{path,sha256}`。path相对该报告所在目录，必须是本page/profile内预先指定capture目录（不用`..`）；允许用统一page-root基准，但报告明确`referenceBase=page_root`。选一种全文件一致。禁止把大JSONObject放进新ref附加字段。局部captured变量用完即可释放，不把所有CapturedFrame存另一集合。
3. support六tag原序精确不变，引用after-scroll-1…6；restoredY与原finally回滚不变。special各原case、short状态acceptanceStatus、小型业务核验不变，仅把geometry/proof树替换为ref；connection preRecreateFrame同理。main单次window/initialFrameSync暂不动，避免扩大schema变更。
4. 完成一条可持久化小型partial汇总（completed=false），最终completed=true；写失败保原失败且不继续当成功。该写盘不插入NativeWindow内部匹配时钟/采集流程。不要以捕获OOM后再尝试构建巨大完整summary作为补救。
5. runner用显式schema版本读取ref，严格相对路径/无越界/无symlink、原文件存在/原SHA相等、case映射目录精确；解析所指原proof后执行原accepted/protocol2/5s/四零差/mapping/身份规则，原geometry消费者读取所指同文件，全字段原件保留，不能仅验证ref存在。短窗原Activity/显式Dialog模式按case原范围判定不变。
6. 六tag及原15/5状态集合/requiredproof计数不减；manifest原fileSha256仍覆盖原独立文件和小summary。不得把旧schema默默当新schema通过；旧原件历史验收仍按其旧版本，不改写旧证据。

## 核验

先离线以已保存708原文件构造仅引用summary，验证重读得到原JSON结构和原SHA，缺文件/篡改SHA/越界路径/错case目录拒绝；不要求重新将全部树序列化为一个字符串来“证明相等”。对每份按文件或逐份结构校验，不同时持有全部树。统一AndroidTest构建、runner语法/精确ref负例，再本批CI。下一实际run需看到六滚达汇总及后续原special完成，仍按真实时间/视觉接受，不用离线内存估算声称OOM永久解决。不加heap、不减原图、不删字段、不改compressed/minified来掩盖重复结构。

## 2026-10-20 静态复审（待最终冻结/构建）

已核两文件实际差异：六次support、15个Scale特殊态、五个Connection特殊态及forty-gesture均保留原capture与独立完整geometry/protocol文件，summary改为page_root相对路径及流式SHA；每次完成后只持久化小型partial摘要。没有改变5s、四零差、mapping、状态集合或截图次数。host拒绝越界/symlink/错case/SHA不符并逐份重走原协议。最后要求`capture_ref_pair`将geometry内容与accepted attempt的`geometryBefore.target`精确相等绑定；owner收口后记录最终hash，不能据本静态复审声称OOM已由真实CI消除。

### 最终静态结论

引用geometry与accepted target精确绑定已补，当前无剩余静态阻断。最终Kotlin SHA256 `130af9c3f66557b8719b061bd2b853db860cd6f8314b4190dfd47f8bd9717f29`；runner `b85cb6440dc16a8f0ee3d47eecb8f98d065c6ffc6479cee427b07732366e3dab`。本轮额外hash漂移仅双引用一次SHA、forty-gesture双引用与释放detail CapturedFrame长生命周期；均已核实际diff。待统一编译/真实CI，旧OOM不追认通过。
