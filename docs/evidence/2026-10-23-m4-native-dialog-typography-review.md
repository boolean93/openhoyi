# M4 原生详情 Dialog 字体增量验收

日期：2026-10-23。审阅：Astra。结论：**本次 8 组原生详情 Dialog 的 G2/G3 实际字体元数据缺口关闭；不代表 M4 全部通过，也不代表本次 run 通过。**

## 证据与边界

- Bean PR run `39884263325`；head `5d08fd658ad7300d0478a2b5e117e88eb3c99f11`，source merge `0109982664efc7b3951094c35a6f0eaed586fbfb`。
- 原件：`build/task-first-evidence/ci-5d08fd6-bean-pr/artifact.zip`；SHA-256 `0b29abf2f7106e5b943cde1c65c859a1b66dc6339c5bab17b607127af439848a`。沿用 root 已完成的 CRC 1233 项、12 manifest / 1192 引用核验，不重复全包解压。
- 对照 [旧详情 Dialog 有限验收](2026-10-20-m4-detail-dialog-visual-review.md)。旧 32 图的全文披露/恢复结论不升级为全量 M4 验收。本次只补原生实际字重、字号、字距、行距、baseline 与同次目标/PNG 绑定。
- 范围：`tablet-user-landscape-{light,dark}-font-{1.0,1.3}` × `batch/states/batch-long-reason-details`、`ledger/states/ledger-selected-event-details`，共 8 组 `details-disclosure.json`。
- 本次实际查看 8 张各组 `initial/window.png`；核对全部 32 步 geometry/proof 与披露索引的 PNG、geometry、proof SHA。未声称重新逐图审过本次全部 32 张，也未重审页面主态。

## 实际读取结果

下表均来自对应真实 TextView 的 `nativeTypography`，不是资源常量；四种 light/dark × batch/ledger 组合在同一 font 配置下相同。baseline 是 View 局部像素值，不是屏幕坐标或字形墨迹边界。

| font 配置 | native role | textSizePx | typeface.weight | baseline px | Layout 高度 px |
|---|---|---:|---:|---:|---:|
| 1.0 | title | 45 | 400 | 82 | 130 |
| 1.0 | message | 36 | 400 | 42 | 每行 52 |
| 1.0 | positive | 32 | 400 | 81 | 92 |
| 1.3 | title | 53 | 400 | 96 | 152 |
| 1.3 | message | 45.45 | 400 | 53 | 每行 66 |
| 1.3 | positive | 42 | 400 | 100 | 122 |

全部 96 个 role 观察（32 步 × 3）：`paint.isFakeBoldText=false`、`letterSpacing=0`、`textScaleX=1`、`lineSpacingExtra=0`、`lineSpacingMultiplier=1`。`includeFontPadding` 为 title/positive `true`，message `false`。每组从 initial 到所有 reach/restored 的完整 `nativeTypography`（包含 objectIdentity）一致；各组 initial/restored 整份 geometry 相等。

32 份 proof 均为 accepted、5000ms、EXPLICIT_WINDOW。逐份验证 accepted attempt 的 `geometryBefore.target == geometry.json == geometryAfter.target`，不是采图后另次 main-thread 重读字体。披露引用的三类文件 SHA 均匹配。这关闭了“值存在但不是该图该目标”的关联缺口。

## G2/G3 视觉判断

8 张同次初态图中，标题、正文、确定按钮的字形清楚；没有假粗体、异常拉伸、字距突变、字行重叠或按钮文字裁切。1.3 的正文与标题实际更大，行距同步增加；不以 `1.0 值 × 1.3` 代替 Android 实际值，也不反推请求 sp。

Batch 在 1.0 为 18 行、1.3 为 22 行；Ledger 均 4 行。1.3 Batch 的初态不能同时展示全部 22 行，是原生正文滚动披露，不据此认定裁切缺陷；本次全部 reach 元数据字体保持一致，旧有限逐行视觉验收继续沿用。原生 title/message/positive 是允许的 system role，不要求套页面字体 token，不要求系统字体文件 SHA。

确定按钮 View 实际尺寸 1.0 为 144×138px，1.3 为 144×168px。title/positive Layout 均无省略；正文行框仍为真实 Layout 而非估算字高。新 Ledger page scroll 为 464px（1.0）/438px（1.3），组内恢复精确相等；它与旧记录 457/431 不同，本次不把旧页面绝对坐标套到新 run，也不据字体窄审替代页面布局复验。

## 关闭项与保留项

**关闭：**上述 8 组原生详情 Dialog 的实际 typeface.weight / fake-bold / letterSpacing / textSizePx / textScaleX / font padding / line spacing / baseline 缺证；同次真实对象、双端 observation 与 PNG 的绑定缺证。G2/G3 在此限定范围可接受。

**保留：**系统字体文件与逐字 fallback 身份未采集，但允许 system role 的合同不以此为阻断；不外推其他原生 Dialog、其他字号/窗口、全部 M4 页面或硬件。未重做全主态 G1/G4，也没有将旧图替换为本次未逐图看的 reach 图。

run 的业务退出 0、12 配置机械完成与最终清理失败分别保留：`font_scale absent → present 1.0` 仍为 FAIL。此窄视觉验收不撤销该失败，不签整 run 或 M4 完成。
