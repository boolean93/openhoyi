# 曲线库与旧版曲线：dc5875c 限定实图验收

## 范围与结论

Astra于2026-10-20查看既有原PNG，不重新采集。实际tested merge `84aaa3efd951e4f6229fc9d235e257e61f0f257c`，head `dc5875c`。root已核curve两manifest的51引用及legacy86引用SHA；本次不重复完整性校验、不拿机械PASS代视觉。

| 已采范围 | G1布局/间距 | G2字体 | G3字号/行盒 | G4颜色 | 限定结论 |
|---|---|---|---|---|---|
| CurveActivity，用户2560×1600，中文浅1.0，主态/非首选择/恢复3图 | 通过已显示布局 | 冻结原生基线通过 | 通过已显示文字和真实压力图 | 通过浅色实际图 | 该配置这3图可接受；不是全部Library状态/四配置通过 |
| LegacyCurveActivity，同设备中文浅1.3，主态/列表恢复 | 通过 | 冻结原生基线通过 | 主态完整；恢复图部分列表行属合法viewport裁切 | 通过 | 该配置普通主态/恢复可接受 |
| Legacy missing-parameters / not-imported / bad-file-read-failure | 已显示布局通过 | 同上 | 已显示全文通过 | 同上 | 缺失/空态/失败不混淆，可接受该3图 |
| Legacy long-name-category / short-window | 已显示部分符合自然换行/局部滚动回退 | 已显示部分无回归 | 未采下方完整滚达终态 | 已显示部分无回归 | **EVIDENCE_PENDING**，不将可滚初态裁切直接判产品FAIL，也不签全文可达 |
| Legacy explicit-import-complete | 卡片层级保持 | 可见部分无回归 | Toast覆盖部分底部区域 | 可见部分无回归 | 不用于确认底部无遮挡；业务替换报告不等于全图验收 |

不外推深色、Library1.3、Legacy1.0、Import、LegacyHistory；不改变这些页已有失败结论。无新产品修正要求，保留下述确切滚达证据缺口。

## 对照与原件

设计对照：`docs/research/task-first-ui-concepts/index.md`登记的`curve-library-v2.png`、`legacy-curve-v1.png`及M3计划§3A、M7/M8计划§3A；冻结原生字体/字号/间距优先于从ImageGen栅格猜字体文件。

- Library根：`build/task-first-evidence/ci-dc5875c-curve/raw/raw-device/84aaa3efd951e4f6229fc9d235e257e61f0f257c/tablet-user-landscape-light-font-1.0/library/`。实看`window.png`、`states/non-first-selection/window.png`、`states/list-restored/window.png`。
- Legacy根：`build/task-first-evidence/ci-dc5875c-legacy/raw/raw-device/84aaa3efd951e4f6229fc9d235e257e61f0f257c/tablet-user-landscape-light-font-1.3/curve/`。实看`window.png`、`states/list-restored/window.png`、`special-states/short-window/window.png`及`special-states/states/`下long-name-category、missing-parameters、not-imported、bad-file-read-failure、curve-explicit-import-complete五张window。
- 同读各geometry、typography、baseline、continuous-list-business及Library canvas、Legacy special manifest；截图均原始2560×1600，不将display伴图计为额外状态。

## Library 逐门依据

G1：原稿左浏览/右详情主次保留，原连续列表而非分页。实际browser `[216,254,1000,1429]`、detail `[1054,254,2524,1429]`，54px=24dp栏距；rail180px=80dp，内容距rail36px=16dp；search高117px=52dp。搜索、排序、筛选、数量、详情三metrics、压力图与固定底部动作各自清楚。右端筛选chip的部分露出是横向滚动提示，不是全文行盒已通过的依据。图为阶段目标而非时间轴，无虚构时间数据。非首选择为真实factory-v3-008，92°C/22.00g/两阶段，未沿用主态93°C/36.00g/四阶段。

G2/G3：实际主/详情标题54px=24sp、列表名36px=16sp，标签/单位31.5px=14sp，值45px=20sp；Paint weight标题500、辅助400，fakeBold=false、scaleX=1。metrics标签结束514到数字起点532为18px=8dp，数字/单位保原baseline；主图axisTitle/刻度14sp、真实Noto Sans SC Paint SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。Canvas targets `[20,90,80,60]` 对应实图2/9/8/6bar，plot `[90,89,1434,400]`，title至最高刻度墨迹18.719px满足18px间距；plot至阶段墨迹24.925px，标签保护4.5px=2dp，轴/阶段字未被边框穿过或裁断。原稿无法证明字体二进制身份，不据此无限unknown；以冻结原生来源、实际字形/角色Paint及行盒共同签本样本，不声称反向识别了ImageGen字体。

G4：原图浅底/白内容区、深正文/灰辅助、teal操作/选择背景与批准方向一致。TextView实际正文`#FF101820`、辅助`#FF657183`；Canvas蓝`#FF176BD1`、网格`#FFDDE3E7`，没有把压力阶段改为温度或成功色。未以调色表单独证明像素，已结合三原PNG。

恢复：筛选后25条；position2/offset−65、query factory-v3、DARK、MOST_USED及selected factory-v3-008在详情返回和重建后完全保持。恢复图所见选中行及参数一致。仅该实际序列，不能替未采长名、只读、无压力、短高状态签收。

## LegacyCurve 逐门依据与剩余边界

G1：41条实际记录不受原稿示例六条限制。左约34%/右约66%、24dp栏距；header真实back/只读标识，计数/导入自然换至下一行，未压缩触控或把导入滚走。右title/metadata、四参数2×2、只读解释与详情入口结构符合；未伪造压力图。卡内36px=16dp、标签到值18px=8dp，数值/单位gap18px=8dp。列表滚动到first3/offset−68后selected4；返回/重建同值，恢复图选中“旧版配方04”，未误取原首项。

G2/G3：1.3使用真实非线性字体换算，不能用scaledDensity线性除值误判缩字：24sp实际59.399998px、16sp45.45px、14sp42.3px。主标题/列表500、辅助400、数值700均fakeBold=false；实际中文笔形与冻结原生基线相符。主态93/60/36.0及单位基线分别830/830、830/830、1103/1103。四值、只读全文、详情入口无重叠。缺参数实图四处“未知”，不是0或伪4；与原本地化文案保持及计划总则§33一致，§3A的“—”不解释为必须改掉现有未知语义。

G4：正文`#FF101820`、辅助`#FF657183`、只读提示`#FF795119`，浅色警示底和teal操作在实图可辨；缺值没有误用成功色，空态/读取失败各显示实际解释。失败图“Invalid UTF-8 curves file”与原有文件未修改说明完整，不伪装导入成功。

长名图左选中名称/分类完整自然换行；右名称五行、分类三行，下方第二排metrics、只读说明和详情入口在viewport下方。short图为真实630px高窗口、列表回退，固定back/import仍显示，右详情未显示。现包没有这些目标逐项滚后完整PNG及恢复图，故只签可见初态，不签全状态G1/G3；后续只补原长名/短窗的既有详情进入/滚达与恢复，保对象/selected/原bundle和固定入口，不新增业务。explicit-import图有真实Toast覆盖底部，保原图不裁不遮，不从其geometry推定无遮挡；普通主态虽有完整同入口，不能替该瞬态作全图证明。
