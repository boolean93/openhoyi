# M5 Comparison：light1.3 ENDED/ENDED 来源全文限定验收

## 结论

实际看主图、source1滚达、source2滚达、双scroll恢复 **4张原PNG**，并对来源与摘要小字作未缩放像素裁片复查。该配置正常ENDED/ENDED的两个来源全文，以及主图已显示摘要的水平分隔层次，**G1–G4限定接受**。旧“285px正文被131px视口截断”的问题在这两个真实滚达目标中关闭；不要求来源在未滚动主屏同时显示，也不将别态的图代入本态。

本报告不签整个Comparison、其余三配置、20目标全部视觉或未显示的口感第三行。既有708 UNKNOWN/ENDED和4076 missing-samples各有独立边界，本次不重复冒认。

## 来源与核验

run `40054362666`，tested merge/source `1874942f56f1e60b8bb3fa13524c35cadaffcbc9`；原件 `build/task-first-evidence/ci-aac5c91-m5/artifact.zip`。不是当前HEAD0396720的新运行证明。

成员前缀：`raw-device/1874942f56f1e60b8bb3fa13524c35cadaffcbc9/tablet-user-landscape-light-font-1.3/comparison/`。只选择四PNG及各自geometry/typography/frame-sync共16件；未全解包。独立重算16件SHA，与该页manifest.fileSha256逐项一致；四份geometry均等于各accepted attempt的geometryBefore/After.target，实际typography与geometry.typographyEvidence相等；四PNG SHA也等于proof.imageSha256。accepted elapsed分别2505/1194/958/1097ms，保原5000ms。以上是取证资格，不代替以下实看结论。

对照 `2026-10-19-m5-extraction-history-visual-migration.md` Comparison真实details自然预算/16与8间距/原动作不减合同、`2026-10-18-comparison-header-fidelity.md` 的选杯/交换/图例结构，以及full-scope既定G1–G4终点。已有 `2026-10-23-m5-light13-current-visual-review.md` 的不同fixture接受不用于推定本态。

## 实看结果

|范围|视觉判断与同帧实值|
|---|---|
|两个来源全文|两杯分别为2023-11-14/15 22:13:20、SOE浅烘、已观察结束，29/32采样点和已保存在本机完整。“对齐：首次观测到萃取的采样；不是已验证的真实启动时刻”末尾“刻”自然折到第五行，原尺寸下笔画完整、无省略或底边截字。不是把单字换行误报为缺字。|
|G1来源布局|source1 `[252,1144,1316,1429]`，source2 `[1424,1144,2488,1429]`，可见矩形均与本体完全相等；两列均1064×285px。左右列留白分明，细竖界仅属于来源卡片，不是摘要的整格竖线。两张滚达图中两个来源同时完整显示；顶部选择器因上区滚动被裁不代表本目标不完整。|
|G2 / G3来源|实际42.3px/400，letterSpacing0，辅助色#FF657183；原生Layout五行、高285px，零省略，第四行实际1050px小于1064内容宽，第五行42px。Noto Sans SC 2.004-H2资产SHA a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da，配实际角色、布局和中文/数字实图按既定终点接受；不要求不可读取的逐glyph私有fallback。|
|摘要层次|主图/恢复图看见表头及“萃取时长”“结束时秤读数”：27.4/31.0秒、36.20/39.50g。三条纤细水平分隔清楚，未再形成包围每格的竖向框线；主值、辅助行名、表头粗细层级可辨，数字单位无互压。该已显示部分的旧整格边框差异关闭。|
|G2 / G3摘要|行名42.3px/500、实际Layout61px；主值45.45px/400、Layout66px。原尺寸裁片核数字、小数点、g及秒字无缺损；主图整体复核列对齐与间距，没有把分析裁片分界当产品裁切。未见的口感第三行不由此签全文。|
|G4|浅底、白色内容面、灰蓝辅助#FF657183与主值#FF101820、青色交换/选中、蓝色压力实/虚线层级清楚。两杯来源并排易区分，分隔不抢正文；只接受本浅色既有状态，不外推深色、pressed/focus或未看指标。|
|恢复|实看恢复图，两杯选择顺序、日期、共同豆款/曲线、主图与摘要回原位置。原comparison-reachability记录20目标、selectedSourceStatuses=[ENDED,ENDED]，initial/restored两scroll均[[0,0],[0,0]]，exactScrollRestored/idsAndMetricUnchanged/journalUnchanged为true。本次独立读取该记录；不将20个机械目标冒称20张全部实看。恢复图可见滚动条与主图不同，不要求系统时钟/滚动条逐像素相等代替原精确scroll合同。|

## 原图清单

路径相对上述ZIP前缀；四图全部实际打开。原2560×1600全图在工具显示时有缩放，因此另裁原像素来源各1120/1145×315及摘要两块1175/1135×290检查小字，不重采或修改原PNG。

|成员|SHA256|
|---|---|
|`window.png`|`b9164a394e46bbb25d6bf03d6f7b0174ba217b69816cd113b2fe3cec5f916bb2`|
|`states/comparison-target-8-reachable/window.png`|`f8da9e7e0600a2b0d92d32836b33e5837ccd0d9ca6d41ad6222ae313215a0b0b`|
|`states/comparison-target-9-reachable/window.png`|`3bbf7106216b151001cd03fb65921ad8a36a78a4461b70e6bbdcd8f40bfc2f79`|
|`states/comparison-scrolls-restored/window.png`|`681e49002ad81471263f7c55033466ddcebe233b47864479e4292d8ccaed4441`|

## 未扩大的范围

其余配置/状态、未看taste行/四动作及全部20目标、硬件数据能力仍按原清单。主图来源本就位于可滚区，不把本次滚达证明说成初态所有说明同屏；本次未发现需要新增产品修复的限定差异。仅更新full-scope Comparison一行，不改M6或其他人的行。
