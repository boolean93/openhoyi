# M7 短窗剩余12图有限视觉验收

## 结论

已实际打开四配置的 `shortInitial`、`shortReachableAction`、`normalOriginal` 共12张完整原PNG。**四档“查看详情”动作滚达时按钮及文字完整，可有限接受；四档长名称均四行，无省略、叠字或横向溢出。** 与已审恢复证据配对后，未见恢复造成的正文内容/位置变化。此结论不包括按钮打开后的说明Dialog，不签整个M7或24页。

1.3短窗初态的“旧版只读”徽标下缘确被正文viewport裁切；该限制在恢复态也存在。合同允许正文滚动，当前图不能证明徽标永久不可达，也不能签该徽标全文滚达已验收。未据此直接要求产品改动。

## 原件与实际查看范围

仅使用 `build/task-first-evidence/ci-8b52379-legacy-pr/acceptance-index.json` 的既有selected原件：run39893290655，head `8b5237989eaae99181ff137b8873896eb96bda7c`，merge/source `44d34c52a3f3b18537f59cb92607bcc7c6252d09`。ZIP SHA256 `bbbf0013a3872dcc75eef9d586743fbc24c7ca3a6fbf575e9b855f03fccb331d`；未全展开或改动原ZIP。

12张均通过view_image逐张查看完整截图，显示工具从2560×1600缩放到1996×1248。独立重算12组PNG/geometry SHA匹配索引；按需从ZIP读12份frame proof核SHA，并确认各acceptedAttempt前后target均与配对geometry相等。本轮不重复80项轴字审查。

以下本地路径相对 `build/task-first-evidence/ci-8b52379-legacy-pr/selected/`；geometry位于同目录。ZIP路径将selected前缀换为上述merge SHA。

| profile / 索引 | PNG路径（profile之后） | PNG SHA256 | geometry SHA256 |
|---|---|---|---|
| tablet-user-landscape-light-font-1.0 / shortInitial | `detail/special-states/short-window/window.png` | `c1f7719ea024a5892b17b1ffb9c30edb4631849cd738b026c29fce4f50e90b51` | `07a3774a728ccd39a458901ba15edcfeee45cfc6fc836e43419ce691c73b4154` |
| tablet-user-landscape-light-font-1.0 / shortReachableAction | `detail/special-states/short-window/reachable/window.png` | `0579c5a49912503f173b3c7bf99e1a3701b1f5923f03dc96d4237b0a485e7894` | `bc4758d0d5cb8bd707245cb4e3e3098e6a38c2085d3281dbac1501349ee9b7aa` |
| tablet-user-landscape-light-font-1.0 / normalOriginal | `detail/special-states/states/short-window-detail-original/window.png` | `ec2d00ff3c590cee85554e2fb6af21380e4d31ddd02236ade67e2437096a490a` | `4853125552ac106c938d6e7edef194969c6bf89dbc3982656c630a91bad0ce9a` |
| tablet-user-landscape-light-font-1.3 / shortInitial | `detail/special-states/short-window/window.png` | `2e717d3fa1a12c35262e7e97a531fece73e5f83c52090b4bb84e5c30b90a781a` | `327bea6ed01aecd4bc06b027fa2971626bac4efb76fc410d76d7d7ba17b40e8b` |
| tablet-user-landscape-light-font-1.3 / shortReachableAction | `detail/special-states/short-window/reachable/window.png` | `cc6486d3b38cfc1e52f0e99ac652ba13bd4ecdd4092fb90a9128a27395ed4bdf` | `fbab4a97c8be8a03d5cb56bc07a6f29775bb35c574f957311843b875f31c7b68` |
| tablet-user-landscape-light-font-1.3 / normalOriginal | `detail/special-states/states/short-window-detail-original/window.png` | `06fa36dd82e75864401163c1144f3c8af4c149ed6152168763902bb143db6869` | `fc680e29f2bb7c5665236b99f3e8cc13749aa3c19d04f61c316d7b8e3e0722e8` |
| tablet-user-landscape-dark-font-1.0 / shortInitial | `detail/special-states/short-window/window.png` | `03cd518cfc53fbd5255a2a03db26c9d09701c9c63335f4820160e375f2e9f212` | `9648ac7f6a531d598c55ba6aa33fea2a60fec7ec608f07fa52a63d59c264917f` |
| tablet-user-landscape-dark-font-1.0 / shortReachableAction | `detail/special-states/short-window/reachable/window.png` | `fb5a714264b3545e7293114c97f7179395e58da98dc1188d2c2d02efaf13e059` | `379a0c9ac882f1cabb7e92ef0a7a2b403f3783c6389bb6e1730f8048f1ead38d` |
| tablet-user-landscape-dark-font-1.0 / normalOriginal | `detail/special-states/states/short-window-detail-original/window.png` | `c2f2fa97ef1066c2ba28cf0d44a4d6dbe06e5852c8b03fb3adb2d03af376d11e` | `88e834a8b2b99332c1b9d6273fa36fc5ae226a139c7657e858dce6370aca7305` |
| tablet-user-landscape-dark-font-1.3 / shortInitial | `detail/special-states/short-window/window.png` | `bb673838f1f1c5db3fd668707bb254cfc9c59cc45c514901a9c388e5750fb0bc` | `242119a2bc7c367bba3cb8bfa1fb069f90d70b86e7e40f39acaa8d3b0c12ce23` |
| tablet-user-landscape-dark-font-1.3 / shortReachableAction | `detail/special-states/short-window/reachable/window.png` | `ee7ea581d10188465d306b6f1be9359e351c4e31f1a3226909b35a3872fb105a` | `30d79ed87688ce82d9dae8f4383bd8ed7a48be68dcc5e67f70e205173f4ce586` |
| tablet-user-landscape-dark-font-1.3 / normalOriginal | `detail/special-states/states/short-window-detail-original/window.png` | `6a10899293de6126496d99a90412624494928e6913444ebd708fc6a10fdf7ea1` | `9fe80331bbbbfe238e36c426a88eb0ad5383a657163588de634f28dda62adc24` |

## G1–G4及动作预算

对照[短窗自然预算/永久正文滚动合同](../plans/2026-10-23-legacy-short-detail-viewport.md)、[实际内容坐标滚达合同](../plans/2026-10-23-legacy-chart-content-rectangle.md)、[固定字体合同](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)。

| 项 | 实际像素和配对证据 | 限定判断 |
|---|---|---|
| G1 结构/间距 | 短窗标题栏固定，长名称和元信息位于可滚动正文；动作滚达后上方显示图表局部，下方独立描边按钮完整。正常窗口长fixture仍为stacked，历史采样卡和数据来源卡按序。 | 无目标按钮与图表重叠；长fixture不能证明足够短内容的split分配。没有全页间距逐项认证。黑色上下区是短窗口外，不是产品留白。 |
| G2 字体 | 中文长名称、标题和“查看详情”字形清楚，字重层级一致，无缺字/异常粗细。1.3动作文字明显放大，未压缩来凑高度。 | 本轮图及geometry没有新增该动作逐字平台字体身份验证，沿已冻结字体合同/原manifest关联，不用资源常量冒充实际Paint或平台glyph身份。 |
| G3 全文/行框 | 四档名称4行、ellipsisCount=0。四按钮实际屏幕rect与visibleRect均[252,971,2488,1079]，108px/2.25=48dp，完整单行、ellipsisCount=0、mappingVerified=true。1.0文字Layout高52px、1.3高66px，content高72px，均留在按钮内。 | 关闭这四图按钮全文与48dp可见目标预算缺口；不证明真实点击、Dialog正文或所有其它动作。正常窗口图表下缘及数据来源正文未全见，不能以局部图签完整图表/说明。 |
| G4 配色 | 浅色深字与青色动作、深色浅字与浅青动作清楚；琥珀只读徽标区分元信息。图表压力蓝实线/水流青虚线仍可辨。 | 已见主题层级有限接受；未做全页合成色对比度测量。 |

1.0短窗徽标rect/visibleRect均[823,1014,1001,1078]。1.3徽标rect[1022,1021,1244,1100]，visibleRect[1022,1021,1244,1079]，下部少21px（约9.33dp）；真实截图可见徽标被底边截断。长名称本身完整，不将徽标裁切误写成长名称失败。此初态没有要求所有正文同时完整，因此仅保留明确的未验收项；若要关闭徽标全文，最小下一步是查已有heading/徽标滚达原件，若缺件再在原fixture补对应滚达帧及恢复，不缩字体、不隐藏徽标、不先改产品。

## 与已审恢复证据对照

与[前次短窗审查](2026-10-23-m7-short-detail-visual-review.md)的chartRestored/normalRestored配对。四正常窗口原图与恢复图在实际应用区域[0,74,2560,1466]像素完全相同；全屏差异仅系统状态栏时间/图标，不能宣称全屏逐像素相等。

四短窗初态与chartRestored在[0,485,2560,1115]比较：差异仅x2515–2523的9px宽右侧滚动条区域，1.0全局y656–839、1.3 y656–833，其余应用区域像素相同。结合此前所有scroll精确恢复及已看恢复图，这支持名称、元信息和初始正文位置恢复，不将滚动条显示状态包装成全图零差，也不外推其它操作后的恢复。

## 未审边界

未打开完整说明Dialog或其它支持动作；本轮normalOriginal是长名称fixture，不是普通Detail主态，更不是Curve/History主态。1.3短窗徽标全文滚达仍缺对应验收；正常窗口数据来源正文也未由此12图关闭。前次80项Canvas结论独立保留，不重复计数。

仅新增本文；未改旧review/产品/规划，未构建、触发CI、操作设备、提交或推送。当前未发现必须立即改产品的确定合同差异，不把局部有限接受写成整页通过。
