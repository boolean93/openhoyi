# M4 原生流水详情：32 张既有原图限定验收（2026-10-24）

审查：Astra。结论：**Batch 长理由详情与 Ledger 已选事件详情，四主配置的既有原生披露范围限定接受；关闭“只有字符串、没有详情 PNG”的过时缺口。** 不新增采集计划、入口或产品状态，不重复主页面验收，不签整个 M4 或全 24 页。

## 1. 原件身份与本次范围

- ZIP：`build/task-first-evidence/ci-2f784ec/bean-artifact.zip`，SHA256 `0cfc5e84ff8ef74cf16cfc79cd973e434b605ab8e3eb2d384349852338f127a0`。
- 实际 source/head：`2f784ec502b8d0651e02e1de8cc86db8714d4ac2`，push run `40056061748`、job `bean-workflow-visual`；不是 PR merge。manifest 的 `sourceDirty=false`，APK 声明 SHA `58d8287fb3f8bab0fc9706619267307772798ea6003e1663ad8a0bf8b6e4537f`（本次未从 APK 重算）。
- 既有四配置：`tablet-user-landscape-{light,dark}-font-{1.0,1.3}`；中文、density360、原整屏 PNG 2560×1600。只审无 IME 的原生只读详情，不借此重签调整输入对话框。
- 仅按需展开原件到 `build/task-first-evidence/ci-2f784ec/details-review-originals/2f784ec502b8d0651e02e1de8cc86db8714d4ac2/`。每个配置下见下表路径；没有改图或重采。
- 派生机械索引：`build/task-first-evidence/ci-2f784ec/details-review-originals/mechanical-audit.json`，含 8 案例、32 captures 的原件路径、SHA、字体、逐行覆盖、原位置/恢复位置，`failures=[]`。本次实看全部 32 张，不以索引通过替代图审。
- 较新 `ci-484b137-bean/artifact.zip` 只有 Preparation 首配置 manifest，没有详情报告；实际在 `preparation424` 因未复现 424px/1154px/1082px 条件提前失败。不能把该包当 Batch/Ledger 新版本证据或推翻旧有效原件。
- 根任务另核 `2f784ec` 至审查时 HEAD `fd5e09e` 的 main 差异仅 LegacyHistoryActivity/LegacyHistoryContent/MachineSettingsActivity；Batch/Ledger 及其共享 main 路径未变。本结论保留明确的 `2f784ec` 来源，不改写为当前 HEAD 新采。

| 页面与事件 | 配置 | 已实看原图（各 step 下 `window.png`） | 全文覆盖 |
|---|---|---|---|
| `batch/states/batch-long-reason-details`；`m4-long-reason-adjust` | light/dark × 1.0 | 每配置 `initial`、`restored`，共 4 张 | 18 个实际 Layout 行初态全见 |
| 同上 | light/dark × 1.3 | 每配置 `initial`、`line-14` 至 `line-21`、`restored`，共 20 张 | 22 行全部经原生滚动披露，初态不要求全文同屏 |
| `ledger/states/ledger-selected-event-details`；`m4-scroll-adjust-19` | light/dark × 1.0/1.3 | 每配置 `initial`、`restored`，共 8 张 | 4 行初态全见 |

Batch 正文是实际事件 `调整 / +1 g / “盘点修正：保留完整的库存核对说明。”重复 30 次 / 该次后库存：71 g`。Ledger 是从非零列表位置打开的真实事件 `调整 · -1 g / 该次后库存：214 g / 哥伦比亚 水洗 · 批次 1 / 原因：滚动核对 20`。**Ledger 这 8 张不是另一份超长理由夹具**，不扩大成任意长度内容全覆盖。

## 2. 采集与业务边界复核

当前 `BeanWorkflowVisualMigrationChecks.detailDisclosure` 已有原生 Window 采集，不需要新并行入口：核原 AlertDialog callback/owner/window 身份、真实 message 原文与事件、实际 Layout 各行，再按未覆盖行原生滚动，最后恢复 dialog 与父页位置。原窗口采集复用 `screenshotMeasuredWindowBufferTranslation`，没有隐藏 IME、暂停服务、修改文字或弱化协议。

本次核对每张 PNG/geometry/proof 的报告 SHA，并直接从 ZIP 核每份 proof 引用的 captureArtifact PNG SHA。32 份 accepted proof 均为 protocol2、原 5000ms；`S1_W1 / S1_W2 / S1_S2 / D1_D2_app` 共 128 个结果均为零，两端 geometry 相同且就绪。各图按原 Window 坐标映射，不使用矩形 mask 或颜色容差。所有报告的 dialog/page 原始与恢复 scroll 精确相同，事件身份/顺序保持。**这不表示 initial 与 restored 整屏像素必然相同**（状态栏时钟可变），也不表示系统字形身份由 hash 得证。

## 3. G1–G4 实图与实数结论

本次对象是计划保留的**既有原生只读详情 Dialog**，不是重新制作的 ImageGen 主页面。其系统标题/按钮字号、font padding 和 native accent 不等于主页面的 title24/500、action16/500 与产品 teal。以下把差异明列，接受范围仅为原生详情披露适配；不能反向修改主页面冻结角色，或宣称这张 Dialog 与未提供的原稿弹窗逐像素一致。

### G1：间距、固定操作与全文披露——限定接受

四配置均为中心原生弹窗，标题和“确定”在正文滚动区外；未见正文盖住操作、横向溢出或末行被固定按钮遮挡。Batch 1.3 的 `line-14…21` 顶部会裁去已经滚出的旧行，这是原生 viewport 边界，不是当前目标行缺字；每个目标行完整，最后 `71 g` 完整。恢复图回到原初态。Ledger 四行全见，不以列表中允许的省略代替详情全文。

两主题同字号的实际几何相同。例：Batch1.0 标题 `[666,157,1894,287]`，正文 `[636,303,1924,1239]`，scroll 可见区 `[612,287,1948,1255]`，确定 `[1777,1264,1921,1402]`。1.3 scroll `[612,292,1948,1243]`，确定 `[1777,1252,1921,1420]`。正文左右比 scroll 内缩各 24px，正文首行比 scroll 顶内缩 16px；确定与 scroll 底保持 9px 净距。上述是原生实测 px，不偷换成主页面 16/8dp 间距。确定分别 144×138px、144×168px，均超过原 48dp 最低触控要求，且在真实安全区内。

### G2：字形与字重——原生角色限定接受

另对 8 个 initial 的 title/message/positive 做原像素局部终核：依 geometry 的整数 screen rect 裁切，正文取首 400px（Ledger 四行全高），三块仅原尺寸拼接，未 resize/resample；派生图宽1288px，以 `view_image(detail=original)` 实看，工具未再缩放。`details-review-originals/pixel-crops/index.json` 记录每块原图坐标；灰色间隔及正文裁片末端截行只是检查图边界，不是产品界面。全页及全部滚动内容仍以32原图检查为准。

32 原图可见中文、数字与 `g` 未发现缺字、乱码、异常笔画或人为压缩。实际 title/message/positive 均 weight400、fakeBold=false、letterSpacing0、textScaleX1。此处没有把 native title400 伪称为主页面500；也没有从 PNG 识别字体二进制。原件未证明逐 glyph fallback 或系统标题字体文件身份，保留来源边界，不借此重开已冻结主字体研究。

### G3：字号、Layout 与单位——限定接受

| 原生角色 | font1.0 实际 sizePx | font1.3 实际 sizePx | 实测附加条件 |
|---|---:|---:|---|
| title | 45 | 53 | fontPadding=true；基线82/96px |
| message | 36 | 45.45 | fontPadding=false；真实行盒52/66px；基线42/53px |
| positive | 32 | 42 | fontPadding=true；基线81/100px |

三角色 lineSpacingExtra=0、multiplier=1。Batch 全文18/22行、Ledger4行，全部零省略；正文横向 Layout 界限处于内容宽1288px内。原生 `+1 g / -1 g / 71 g / 214 g` 是同一文本行，不冒充主页面独立单位节点或新字号角色。各行屏幕框来自真实 Layout，不等同逐 glyph 墨迹；实际 PNG 再确认可读、无相邻行叠压。1.3 数值按真实 Android 结果记录，不用简单乘法推断。

### G4：背景和文字色——原生适配限定接受

对 8 张 initial 原 PNG，按真实 title/message/positive 可见矩形统计纯色内区与字形实心像素（非抗锯齿边缘）；两字号相同主题结果一致：

| 区域 | light 实屏 RGB | dark 实屏 RGB |
|---|---|---|
| Dialog 表面 | `#FFFFFF` | `#424242` |
| 标题实心文字 | `#212121` | `#FFFFFF` |
| 正文实心文字 | `#1C2631` | `#F1F5F9` |
| 确定实心文字 | `#2865DE` | `#7DDCCE` |

浅色按钮蓝、深色按钮青是现存 native Dialog 的实屏差异，不谎称两者已经迁为主页面 `#087A78`。背景遮罩、滚动指示与文字层次在全部滚动/恢复图保持可读，未见异常状态色跳变。未采 pressed/focus/ripple 的合成效果不签署。

## 4. 关闭项与保留项

- 关闭：Batch 指定长理由及 Ledger 指定已选事件，四配置原生详情“没有 PNG/尚未检查字形与全文”的缺口；完整目标披露、固定确定、原位置恢复与所见原生浅深层次限定接受。
- 不关闭：Preparation424px特定紧凑窗、原生调整输入/IME其他独立失败、font_scale清理、其它页面、任意新长理由/语言/硬件状态。历史失败保留原版本与原状态，不被这32张追认。
- 不做：新采集计划、重复主页面矩阵、另一个窄入口、降低5s四零或放宽全文断言。已有代码和原包已经满足此次限定图审所需证据。
