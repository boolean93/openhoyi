# M4 长理由原生详情焦点竞争诊断

证据：ci-49a1b46-bean，source 46e2dbc79ca0732290db92b5c78ad097c82c86cb，light/font1.0/batch/failure-context。已实看原 PNG：库存流水 dialog 实际呈现完整长理由、末尾“该次后库存：71 g”及确定按钮。geometry 只录 Activity，且 sameCompositorFrameProven=false；不能据此声称异常瞬间已获取焦点。

源码 BeanWorkflowVisualMigrationChecks.kt:809 在 row.performClick 后的下一 main 立即 single 查唯一 focused 非 Activity decor；没有原生 focus 异步就绪等待，而 dismissDetails 已有等待。因此证据支持焦点竞争候选，不是完整因果证明。

最小修复只改测试：点击后用既有有界 await，等待唯一非 owner、attached、laidOut、hasWindowFocus decor；不得把全文内容通过放进等待条件，也不吞 single 异常或重试点击。就绪后 main 重核唯一身份，再原样执行 contains(longReason) 全文断言。dismiss 后仍等待 Activity focus 恢复。超时保原件，禁止固定 sleep/放宽全文/生产猜改。实际 CI 复现验证前仍未关闭。

## 2026-10-20 bcc64c1 实际图审：主态通过范围与特殊态缺口

本节为 Astra 实图审查，不是 CI 绿色代验。原件根为 `build/task-first-evidence/ci-bcc64c1-bean/raw/8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249/`。manifest 的 head 为 bcc64c1，实际 tested merge 为 8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249。artifact 12261468139；ZIP SHA256 `683218925a2c39309ed7aaa7443846ddffa45e7b429b4d2146707b8f8986a951`。根代理已核 777 CRC 与 12 manifest 的 736 引用 SHA；本审查另读原始 geometry/Layout/typography/baseline/reachability，并看 12 张主图及全部 128 张特殊态构图（特殊态联系表只用于总览，不代替小字原图）。确认态 light/1.3 另回看原 PNG。

### 逐页结果

| 页 | G1 构图、操作及可达 | G2 字体证据 | G3 排版 | G4 色彩 |
|---|---|---|---|---|
| Preparation 四主态 | 双列、三批次、214 g、18 g 输入、真实无 Service 禁用说明、固定保存/返回均符合批准适配；1.3 预览在初屏以下，但 reachable-6 实际完整展示，不能因初屏缺数字判 FAIL | 现有固定 Noto Sans SC 资产/角色路径保留；原稿字体身份及运行时逐 glyph fallback 仍 unknown | 四主态数值/单位 baseline 全零差；确认后特殊态说明全文滚达未闭合，见下 | 浅深选中填色、after 数字与 g accent、未选图标/正文 ink 均符合；不据配置值认证 ripple 合成 |
| Batch 四主态 | 90 g 主余额、100 g 初始、90%、日期组及两事件首屏完整；调整后 80 g/三事件、失败/冲突/缺批次状态构图未见新增偏差 | 同上，不扩张至 glyph 身份认证 | 主态 5 个逐目标滚达均完整、恢复为真；原生长理由焦点检查本轮执行完成，未留独立长理由 PNG，不能声称逐 glyph 图验 | 余额/操作与浅深面板层级正确；无以普通消耗负数冒充错误状态 |
| Ledger 四主态 | 214/250 汇总、五列、214/232/250 三事件；全库状态不伪造合并余额，缺批次保原空态 | 同上 | 主态 3 个逐目标均完整且恢复；原有两行省略+全文原生详情语义保留，不把省略本身判裁切 | ADD 使用 accent，CONSUME 正文 ink；浅深表头/正文/分隔层级符合 |

12 份 `scroll-reachability.json` 共 60 个主态目标，after.complete 全 true，scrollRestored 全 true。所有 12 份 baseline-alignment 中 deltaPx 全 0。实际 product 标签文字证据均 letterSpacingEm=0、textScaleX=1、includeFontPadding=false。1.0 的 auxiliary/body/balance 对应 31.5/36/90 px；1.3 对应 42.3/45.45/90 px（density 2.25，不能用线性乘 1.3 错判 Android 字体缩放）。自然行盒结合实际 Layout 审查，不把 getter 行高等同 glyph 墨迹。

主 PNG 的真实纯色像素统计与配置交叉吻合：浅色页面 #F9FAF9、白面板、选中 #E2F0ED；深色页面 #11191A、面板 #1B2526、选中 #203D3B。文字配置分别 ink #101820/#EDF3F2，muted #657183/#A9B9B8，accent #087A78/#83D6D0；实际图未见角色互换。纯色统计不是所有抗锯齿像素/半透明 ripple 的颜色认证。

### 唯一已定位的 Preparation 特殊态证据缺口（不是生产布局 FAIL）

`confirmed-ledger-matched-receipt` 的“库存已扣减 18 g。开始失败也不会自动回库；更正须在库存页记调整流水。”在四配置均不完整可见：

- 1.0：screen rect `[1406,1039,2488,1145]`，visible bottom 1141，相差 4 px。
- 1.3：screen rect `[1406,1090,2488,1215]`，visible bottom 1141，相差 74 px。

原 PNG 确认其在局部滚动区下缘，固定放弃/返回按钮仍完整；生产源码该说明属于 support-scroll 内容，并非固定区错误裁切。因此目前是 **全文滚达/恢复证据不足**，不得推导必须压缩字体或改布局。`dose-balance-receipt` 的 214→196 本身完整且与同 event 的 authoritative ledger 对应；不把它与下方未完整说明混为一个缺陷。

最小补证限现有 Preparation business 状态和原测试/runner：在 confirmed-ledger-matched-receipt 初态原件之后，按已知原字符串/当前实际 View 找到该说明，滚动既有 support-scroll 至全文 View 与实际 Layout 行盒完整，保留相同采集协议的 PNG/geometry/proof；finally 恢复原 scroll 精确值并留恢复图，重核当前 preparation identity/eventId、库存余额及 journal 未变、固定按钮矩形未变。不触发放弃、确认、写库存或重建；不新增业务状态。post-consume-intent-write-failed 同类说明也应在原状态就地核完整性，不能由确认成功态外推。已有主态 reachable-0…6 早于业务变更，不能代替这两个状态的证明。

### 状态边界

旧 Preparation IME 操作完全出屏的 FAIL，本轮四 IME 图已显示输入及固定操作在键盘上方；诊断型 IME/dialog 原协议仍是 sameCompositorFrameProven=false，不追认为 protocol 2。同理，长理由本轮不再因焦点竞争中断，但全文字符串断言不等于额外原图字体认证。

本轮结论是三页 **12 主态及已列特殊态的限定视觉接受**；Preparation 上述说明滚达仍待补证，G2 不可观察身份保持 unknown，不能标成 M4 全门无条件 PASS，更不能扩展为全 24 页通过。font-scale-restore 的 originalExitStatus=0 只表示主体执行完成；原 key absent 被系统补为 1.0 的清理 FAIL 独立保留。

### 两原状态补证实现静态收口

已复核 BeanWorkflowVisualMigrationChecks.kt（SHA256 `731db33a73eeb6136e5f4039487f6864827af2ff6bba91a065802752f2bbeeb1`）与 bean runner：限定原 confirmed / post-consume 两状态，原图先保存；真实 support-scroll 内按既有完整字符串唯一定位，whole 检查 Layout 全行盒/零省略；finally 精确恢复原 scroll 与 target observation，校验同 intent 对象/eventId、全批次余额、事件及 journal 不变、固定按钮矩形不变。runner 明确要求两状态 visible/restored PNG、geometry、protocol 2 和报告。静态无新增阻断；实际补证尚未运行，不关闭上文视觉缺口。

## 2026-10-20 dc5875c 两说明缺口实际关闭

实际merge `84aaa3efd951e4f6229fc9d235e257e61f0f257c`，原件 `build/task-first-evidence/ci-dc5875c-bean/raw/<merge>/<profile>/preparation/states/`。Astra逐张看 confirmed-ledger-matched-receipt 与 post-consume-intent-write-failed，各浅/深×1.0/1.3的 description-visible、description-restored 共16张原PNG；另读8份support-description-reachability与16份accepted protocol2证明。未重复原12主态全审。

两说明全文在滚后均完整：确认态“不自动回库、更正须库存页调整流水”；未完成态“核对同一事件、不重复扣减、勿另建记录重扣”。自然两行未见裁字/省略，字形/主辅层次及浅深色保留。固定放弃/返回及原有重试动作矩形不移动；滚动仅影响右侧内容，不挤掉左批次。恢复图回到原0位，因此说明再次局部隐藏属于精确恢复，不是新失败。8报告wholeAfter、scrollRestoredExactly、targetObservationRestoredExactly、businessUnchangedAfter/Restored和fixedActionsUnmoved均true，原intent/eventId、余额196000/178000及journal不变。

结论：此前唯一明确的Preparation两状态说明全文滚达/恢复缺口，在这四配置G1/G3实际关闭；所看新增文字G2/G4未见回归。沿用原三页12主态及已列特殊态限定接受。原生dialog未采全文字形、硬件及font_scale清理边界不因此取消，不授全24页通过。
