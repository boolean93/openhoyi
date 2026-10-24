# Preparation 两态说明滚达与恢复：限定验收

## 结论

接受 light/dark × 1.0/1.3 中 `confirmed-ledger-matched-receipt`、`post-consume-intent-write-failed` 两态说明的全文滚达及原位置恢复，共 16 张原 PNG。只关闭该既有缺口，不重签全部 Bean 主态、IME 紧凑同行或末尾 font_scale 恢复。

原件：run `40056061748` / artifact `12545524403`，source `2f784ec502b8d0651e02e1de8cc86db8714d4ac2`。ZIP `build/task-first-evidence/ci-2f784ec/bean-artifact.zip`，SHA256 `0cfc5e84ff8ef74cf16cfc79cd973e434b605ab8e3eb2d384349852338f127a0`。root 已核远端 digest 与 1233 条 CRC；本次只提 72 文件，逐 member 路径/本地 SHA 见 `description-selected/index.json`，未整包解压。

## 实看及逐目标核验

Astra 实看全部 16 张 visible/restored PNG（原 2560×1600，查看工具缩显 1996×1248），不以机械 PASS 代图审。两个状态 × 四配置均见全文自然换成两行，没有 Toast、固定按钮或错误提示覆盖说明。确认态 214→196 g 与后消费失败态 196→178 g 的收据、提示层次一致；后者重试/放弃/返回及底部错误保留，不因滚达改变操作语义。

| 范围 | G1 布局 | G2 字体身份/字形 | G3 字号/行盒 | G4 颜色 | 业务/恢复（独立） |
|---|---|---|---|---|---|
| 两态 light/dark 1.0 | 确认说明 y=1035..1141；失败说明 y=827..933，x=1406..2488；均完整位于支持滚区，固定动作不动 | 沿用原固定中文字体；实看中文字形一致、无缺字/替代符；不新增字体身份验收 | 正文36px；两行高106px，零省略 | light正文 #FF101820；dark #FFEDF3F2；强调色收据、主辅动作与红色错误未混淆 | visible完整；restored回原位置；业务不变 |
| 两态 light/dark 1.3 | 确认说明 y=1016..1141；失败说明 y=793..918，同宽1082px；放大文本无压缩/遮挡 | 同上，保持既有字体身份结论 | 正文45.45px；两行高125px，零省略 | 与1.0对应主题一致 | visible完整；restored回原位置；业务不变 |

72 文件逐一与 ZIP 原 member 字节及所属 manifest.fileSha256 相等。16 份独立 frame proof 均 accepted、5000ms，接受 attempt 的 S1_W1/S1_W2/S1_S2/D1_D2_app count 均 0；geometryBefore.target == 独立 geometry == geometryAfter.target，imageSha256 对应实际 PNG。

8 份 reachability 均 wholeAfter、fixedActionsUnmovedAfter/Restored、businessUnchangedAfter/Restored、scrollRestoredExactly、targetObservationRestoredExactly=true；独立复核 initial==restored。两态库存分别196000/178000mg，滚动不引发新消费，原 eventId/intent/流水语义保留。

**恢复不是再次全文可见。** 1.3 确认态恢复后说明下部裁于原支持滚区；失败态原滚动位置可使说明在视口之外。这与原 initial 完全相等，是原批准滚动内容的恢复，不当作说明首屏可见。只签本次全文滚达与精确恢复。

## 仍未关闭

[四 IME 图限定报告](2026-10-24-m4-preparation-ime-row-review.md)所述新 workspace484px、纵排，不证明旧424px/inner1082紧凑同行。该 run 最终 font_scale presence/value 恢复失败仍独立有效；不以本次局部图审覆盖。
