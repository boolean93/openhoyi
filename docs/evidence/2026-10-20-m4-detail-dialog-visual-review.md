# M4 原生全文详情增量验收

2026-10-20，Astra。**Batch 长理由、Ledger 已选事件详情在浅/深色 × fontScale 1.0/1.3 的全文可读、逐行滚达和恢复缺口已关闭，限定接受本轮原生详情呈现。** G2 字体身份及精确字号证据仍有边界，不能记为 M4 全门无条件 PASS。

本轮仅审新增详情，不重审此前已接受的 M4 三页 12 主态与 128 张特殊态，不改变 Preparation 已关闭结论；未改产品、测试、设备或 Git 推送。工作树为 `openhoyi-task-first`、分支 `feature/task-first-ui`，保留他人未提交变更。

## 证据与基线

本轮原件为 `build/task-first-evidence/ci-643d828-bean-pr/artifact.zip`；run `39433459326`，head `643d8281ee46a4765133ec6e9796a971d8120dc8`，tested merge `4f990722c0181504b665ab1fe91ac60832619d18`。ZIP SHA256 `70c58db161d42984b9126928985441aa3b3581a1c5cd46804fc9eafbc24b3093`。

根代理已完成 1233 条 CRC、12 主 manifest 的 1192 引用逐 SHA 核验，见同目录 `reference-verification.json`；本审查不重复这些完整性工作。只使用已选择提取的详情 PNG，JSON 在 ZIP 内按需读取，没有全包解压。

审查依据为 [M4 既有接受与缺口](2026-10-19-m4-native-focus-review.md)、[披露补证规划 §2](../plans/2026-10-20-existing-visual-disclosure-completion.md)、[原生角色有限冻结 §10](2026-10-18-native-specimen-review.md)及[严格视觉基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)。已查看概念图 [Batch v2](../research/task-first-ui-concepts/bean-batch-v2.png)与 [Ledger v3](../research/task-first-ui-concepts/bean-ledger-v3.png)：它们约束真实流水字段及正常增减的语义，不提供独立弹窗精确字体、色值或布局。按既有方案保留原生全文详情，不把页面角色的字体资产或色板自动套到系统弹窗。

逐张实看 **32 张 `window.png` 原件**，包括 8 组 initial/restored 和两主题大字号 Batch 的 16 张 line-14…line-21；不是只读 JSON 或联系表。读取全部 8 份 `details-disclosure.json`、32 份 geometry 与对应协议证明，并核对业务报告。原图位于：

`raw/<tested-merge>/tablet-user-landscape-{light,dark}-font-{1.0,1.3}/{batch,ledger}/states/{batch-long-reason-details,ledger-selected-event-details}/`

## 四配置结果

| 配置 | Batch 长理由 | Ledger 已选事件 | 恢复 |
|---|---|---|---|
| light / 1.0 | 18 行初态全部完整，包括调整、+1 g、30 次原说明与末尾 71 g；自然行盒 52 px | 4 行初态完整：调整 -1 g、214 g、豆款/批次、原因“滚动核对 20”；行盒 52 px | 两弹窗均 0→0；Batch 页面 0→0，Ledger 457→457 px |
| dark / 1.0 | 同配置原件独立核验，18 行全覆盖、无省略；52 px | 独立原件 4 行全覆盖；52 px | 两弹窗均 0→0；Batch 页面 0→0，Ledger 457→457 px |
| light / 1.3 | 22 行；初态完整覆盖 0…13，原 scroll 每次移动 66 px，line-14…21 到 528 px 后覆盖全部；末尾 71 g 完整；行盒 66 px | 4 行初态完整，仍为同一真实事件；66 px | Batch 528→0，Ledger 弹窗 0→0；页面分别 0→0、431→431 px |
| dark / 1.3 | 独立原件 22 行全覆盖，同样 8 次滚动到 528 px；末尾 71 g 完整；66 px | 独立原件 4 行完整；66 px | Batch 528→0，Ledger 弹窗 0→0；页面分别 0→0、431→431 px |

滚动中上沿出现上一行的局部字形，是原生 viewport 正常裁切；该行已在此前帧完整展示，不能把任意中间帧要求成超高 message 整体可见。反之，也不能用字符串存在代替滚达：本轮已从各帧实际 `screenRectPx` 与 scroll viewport 重新计算 fullyVisible，并取并集，分别得到 18/22/4 行全部覆盖。各帧 start/end 拼接与完整 expectedMessage 相等，ellipsisCount 全为 0。1.3 恢复图重新隐藏末段，是回到原 0 位的结果，不是全文回归。

## G1–G4 增量判断

| 门 | 本轮结论及依据 |
|---|---|
| G1 构图、操作、可达 | 限定通过。中央原生弹窗与暗化背景层次清楚，标题/正文/关闭动作分区稳定；长理由只滚正文，标题和确定按钮不移动。32 帧确定按钮完整位于实际 safe rect，1.0 为 144×138 px，1.3 为 144×168 px，density 2.25 下均超过 48 dp 下限。没有为了单屏压字或移走关闭入口。 |
| G2 字体 | 所看中文、数字、正负号与 g 的字形清楚，未见缺字、重叠或可见异常；原生标题、正文、按钮层级可接受。**详情证据没有独立 Typeface/SHA、weight、fakeBold、letterSpacing 或逐 glyph 来源，保持 unknown；不能引用 owner Activity 的 Noto 字体报告证明弹窗。** |
| G3 字号、自然行盒 | 全文可读与行盒滚达限定通过。正文实际 Layout 行盒为 52/66 px，横向行范围未越出真实 viewport，逐行无省略、未见字形被自然行盒裁切，末尾库存和单位可读。**本次 geometry 未记录详情 TextView 的 textSizePx、requested sp、font padding 等完整 typography 字段，精确字号角色一致性未证实。** 52/66 px 是 Layout 行盒，不是字号或 glyph 墨迹高度；不以线性乘 1.3 推定字号。 |
| G4 色彩 | 接受本轮实际观察到的原生详情适配。浅色白底/深正文，深色灰底/浅正文，确定操作可辨；正常 +1/-1 不以危险红渲染。以下为原 PNG 实际合成像素，不能误记为页面 tokens；未覆盖按下/ripple 等瞬态。 |

对 8 张 initial 原 PNG 的 title/message/positive 实际矩形分别统计纯色与文字内实色像素，浅深各两页两字号结果一致：

| 对象 | 浅色 | 深色 |
|---|---|---|
| 弹窗底 | `#FFFFFF` | `#424242` |
| 标题字内实色 | `#212121` | `#FFFFFF` |
| 正文字内实色 | `#1C2631` | `#F1F5F9` |
| 确定字内实色 | `#2865DE` | `#7DDCCE` |

这些是所见截图内的实色样本，包含抗锯齿的其它像素没有被替换或归一化，也不反推资源 ARGB/alpha。浅色系统蓝按钮与页面 teal 不同、深色灰弹窗与页面 surface 不同；本次明确按原生详情适配接受，不把差异伪装成已冻结页面色板一致。

## 同次绑定与业务边界

32 份证明均为 protocol 2、`EXPLICIT_WINDOW`、`MEASURED_WINDOW_BUFFER_TRANSLATION`。本次逐份核对 accepted attempt 的 geometryBefore.target、外置 geometry、geometryAfter.target 完全相等；Window/decor/token/owner/message identity 与各自 disclosure 一致。`S1_W1`、`S1_W2`、`S1_S2`、`D1_D2_app` 四比较 count 全为 0，accepted/withinMatchingBudget 均真，timeout 保持 5000 ms。仅认证测得的 dialog rect；`acceptedTargetScope` 明确 owner 背景未认证，不能把这些证明扩成整页主态或 IME 同帧认证。

Batch 绑定 `m4-long-reason-adjust`，原事件 +1000 mg / 71000 mg、完整 30 次理由；Ledger 四配置均绑定 `m4-scroll-adjust-19`，-1000 mg / 214000 mg，原因“滚动核对 20”。原始 formatter 期望全文与各帧 message 一致，未拿任意同词节点替代。原 Window callback/owner 验证在 8 报告均为真。

8 报告原 scroll 与 restored scroll 精确相等；原 page scroll 在取证与恢复时保持原值。`restored/window.png` 是**关闭前弹窗恢复图**，不是关闭后页面截图。关闭后的 Ledger 精确非零位置与顺序由同次 `bean-workflow-business.json` 的 `ledger-detail-return-keeps-exact-nonzero-scroll-and-order` 及完成标志支撑；Batch 对应 `long-reason-original-detail-disclosure-no-data-truncation` 完成，事件清单与 71000 mg 保持。业务报告的只读库存/其它模型与 preparation 不变、无机器或 Service 指令边界保留，不将其冒充真实设备验收。

本轮可关闭既有“原生详情只有字符串断言、未采全文字形/逐行滚达与恢复原图”的具体缺口。精确 typography 未采字段及不可观察字体身份仍按对象记 unknown；不据 CI SUCCESS 宣称全 M4 G1–G4 无条件通过，不外推其余配置、其它弹窗、24 页或实机。根记录的 font-scale `absent→absent` / businessExit 0 独立保留，不用本视觉结论替代环境清理结果。

## 元数据剩余项的最小范围

**系统 AlertDialog 原生角色已获适配认可，字体文件 SHA/逐 glyph 来源不是本轮关闭详情缺口的前置条件，也不要求从栅格反证。** 上述 unknown 是知识边界，不能登记成必须无限补采的视觉缺陷；不要求重设计标题或替换系统字体。

若根代理要把精确字号/字重也从“未采”收口，唯一必要增量是在既有 `BeanWorkflowVisualMigrationChecks.detailDisclosure` 的 `state()` 中，对已持有的 `title`、`message`、`positive` 三对象附带公共 API 可读字段：`textSizePx`、`typeface.weight`、`paint.isFakeBoldText`、`letterSpacing`、`textScaleX`、`includeFontPadding`、`lineSpacingExtra`、`lineSpacingMultiplier`、`baseline`；并明确角色为 native title/message/positive。已存在的实际 Layout 行盒、lineCount、ellipsisCount、viewport 与 identity 直接复用，不另建 collector。requested sp 若无真实来源则不倒算、不猜填；Typeface 文件 SHA/逐 glyph 来源继续不作为采样要求。

这些字段应随原同次 target 进入 accepted proof 与外置 geometry，在下一合批原四配置、两个既有 dialog 中获取；不新增点击、fixture、业务状态或独立 CI。正文和按钮的可读性已实图接受，所欠仅精确公共 API 元数据，**当前没有定位到字号/字重不匹配或需要生产修复的问题**。本审查不改代码。

### 最小元数据实施静态复核

已按实际文件完整 SHA256 复核：

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt`：`558af8e6cffd10171729cce2a89ecc04296068fdf8c73937abc600e85d18a62e`
- `scripts/check_bean_workflow_visual_migration.sh`：`69d36fc16cf772754b8ffec251e1543444f553cf9c3f98329ae8bd593efa1eed`

**本次增量无静态阻断，可进入下一合批取证。** `detailDisclosure.state()` 在原 title/message/positive 对象的 observation 内添加 `nativeTypography`，读取上节列出的九项公共 API 字段，附 `nativeRole` 与 `objectIdentity`；没有另找替代对象、设置字号/字体、推定 requested sp 或字体 SHA。weight 不可读时为显式 null，不冒充确定身份。

字段随既有 `state()` 回调进入 frame target；host 先验证 accepted attempt 前后 geometry 完全相等，再核外置 geometry 等于该 accepted target，因此新增字段也受同次绑定约束。host 逐角色校验字段有效域，三个 `objectIdentity` 仅要求整数及跨步骤相等，不新增正数或非零限制；message identity 另与原 `messageIdentity` 相等，title/full message 与报告原文绑定。跨步骤检查保证对象身份连续，**没有把它写成所有 typography 数值跨步骤恒等的额外认证**；各帧保留实测字段供运行后核对。

原逐行覆盖、全文边界、safe rect、48 dp 操作、精确恢复、库存不变以及 protocol 2 四零差/5000 ms 门禁未被本增量移除或放宽；没有新增业务状态或点击。此次仅静态读 diff 与相关调用，未构建、未运行设备/CI、未重审 32 图。新增元数据仍待真实合批产物，不能凭本节将此前未采字段标为已测或将静态通过等同新视觉验收。
