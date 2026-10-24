# M5 UNKNOWN 标准字号首屏轴预算窄修

来源 `feature/task-first-ui`/643d828，后续合批 PR → develop；不发布、不签名升级、不设备操作。只改 `HistoryDetailContent.kt` 内嵌预览布局及既有 M5 测试/runner 对应证据；不改独立详情/真实状态/Chart Paint。回退为本窄补 revert。

## 原件与原因

`ci-643d828-m5-pr/diagnosis.md` 对应 UNKNOWN `m5-history-extra-7`，light1.0 首屏 chart 底1309、viewport底1267，时间轴字盒1269.428–1298.428被裁；screen mapping正确。metrics已经inline，不能再用同行指标当新修复。前置 header242 + metrics135 + margin36 + chart margin36 + chart minimum597 =1046，viewport1004，缺42px。原FAIL保留，96dp可见plot通过不等于完整轴通过。

## 唯一候选：条件合并 title 与 warning

同次原geometry：可用header宽1331，more固定108 + 原16dp间距36，剩1187；title `SOE浅烘` 自然字宽214，warning558，合计加原16dp间距36为808，可容纳。meta+warning则975+至少18+558>1331，明确不采用。

仅当真实首屏前置内容+完整chart最小高度超viewport、UNKNOWN warning存在、真实title/warning自然宽加16dp及more原占位足宽时，将永久 title 与永久 warning 在 more 之前条件同行。沿用原字号/字重/颜色/全文，title与warning间16dp，垂直居中，more仍48dp、原16dp隔离。标准足高或长名不足宽仍原纵排；不硬编码42/67px、不按fixture ID选布局、不缩字/压行高/省略warning、不初始自动scroll。

容器须保留同一TextView/监听/对象，布局切换仅改变该容器内部测量排布。原独立warning行8dp顶间距仅在纵排存在；同行使用明确16dp水平间距，不叠加虚假空行。实际测量预计可省49+18=67px，是否满足须按最终真实尺寸复核，不能用预估当PASS。

预算证据分开记录首屏前置header+metrics+chart及全文滚动自然高，避免将chart后说明/notes的滚动需求误当首屏轴需求。记录原/解析header高、title/warning各自自然宽、more占位、同行条件/结果、首屏预算是否满足；不以账本代替实际轴glyph/whole门禁。

## 验证边界

保留现有1.0初始完整chart+时间轴门禁，1.3原允许局部滚达/恢复不变；新布局应用条件不能破坏字体1.3文字自然高。检查原主态/其他5special、1.0 UNKNOWN、1.3 UNKNOWN原图与同采样glyph/全文warning；正常END无warning不变。测试只补该候选布局证据与warning完整，不增加场景、不减原固定footer/notes/说明要求。静态审、统一Mock+AndroidTest构建后用原CI确认。若合行仍不足则保FAIL，不再靠减少间距凑阈值。

## 首轮实施静态审：一处阻断

已读三文件实diff并核冻结SHA：生产 `ed4769f4241f0175a9a3d3db310c82e2ef16e7eaf62de8601940db642921b62b`，测试 `f4c826d7040c6c0c0a5d18d4df96a67f846329994689c0691496d0b8b2464f37`，runner `9c26d88fee4038ccff6de2b31cf81d6c022160b321edd2fbab8bb46bf198383b`。

唯一确认阻断：`EmbeddedHistoryPreview.onMeasure` 首次 `measureChrome()` 用于 verticalChrome/verticalMinimum/metricsInline，但此时新header的inlineWarning尚保留上一轮值；复位false位于后面。这使所谓原纵排预算与metricsInline选择依赖上一次测量结果，不是每轮相同输入从原布局重算。不能仅因本fixture可能仍选inline就接受该测量路径。

最小修正：在本轮首次 `measureChrome()` 前获取原永久 `EmbeddedHistoryHeader`，将 inlineWarning=false，与metricCells.inline=false一起复位，再测原自然高；删除后面重复声明/不必要复位。其余宽度条件、首屏前置账本、真实chart最小值与末轮复测不改。不需增加状态、改变字体/间距或复写测试规则。

其余本轮差异未见静态阻断：永久warning仍同一TextView，仅布局位置变化；more原48dp与16dp间距保留；title与warning足自然宽才同行；原chart/轴Paint未改，1.0初态whole+全部axis glyph门禁仍在，1.3原滚达未取消；测试与runner新增warning全文/条件预算验算，没有以账本替代真实图。首轮不签通过，待上述窄改hash复核后再统一构建。实际新原图仍待CI，不追认643旧FAIL。

## 窄修终核

Astra 已核唯一阻断修正：previewHeader获取及inlineWarning=false已移到首次measureChrome之前，每轮与metrics一起从原纵排重算，后段不重复声明。其余两文件SHA保持。此三文件静态无剩余阻断，可进入统一构建与原CI；不代表UNKNOWN标准字号原图已经修复通过。

最终生产SHA256 `7c73b4769c5031653385640e81f8a958753de117256dfe321e728a523173884d`；测试 `f4c826d7040c6c0c0a5d18d4df96a67f846329994689c0691496d0b8b2464f37`；runner `9c26d88fee4038ccff6de2b31cf81d6c022160b321edd2fbab8bb46bf198383b`。

## 编译遮蔽修复静态增量复核

Astra 读取当前生产文件并仅在内存逆替换第528、532行 `this.width =` 为 `width =`，各匹配一次；逆替换后SHA256恰为旧已审 `7c73b4769c5031653385640e81f8a958753de117256dfe321e728a523173884d`。因此相对该已审版本，字节差异仅为两处 `apply` 内显式接收者限定。

两处赋值现明确指向 `LinearLayout.LayoutParams.width`，消除外层 `val width` 同名遮蔽导致的 `val cannot be reassigned`；赋值表达式、weight、条件同行与原纵排复位路径均未改变。该增量未改变上述设计合同，静态无新增阻断。

新生产SHA256 `c6005f088b111f4d5742a589050b11138161fdd3d99cecd1dcb9c7831d5f151d`。本次仅做静态增量复核，未构建、未提交、未改源码；构建结果由并行构建任务提供，UNKNOWN首屏完整轴及warning视觉验收仍须原CI实证，不签视觉通过。
