# M6 三配置短窗与趋势详情：实际六帧视觉验收

## 结论与范围

本次实际打开 light1.0、dark1.0、dark1.3 的 short-height 与 short-height-trend-detail 共 **6 张原 PNG**，并读配对 geometry、实际 typography、baseline、Dialog 返回状态及协议原件。在下列范围内接受 G1–G4：短窗重量/单位/完整只读说明/固定去皮与采纳区，以及原生趋势详情的标题、七项轴字、图表、两行说明与取消。关闭这三配置此前未看实图的缺口，不以机械 PASS 代替看图。

不是整个 M6、全部 support 控件、所有特殊态或硬件验收；不追认其他 CI。旧643 light1.3 的结论仍只属于旧证据，本次未重复看它。

## 原件与独立核验

source/head `588855c96fdceec79bf87f0fd5950f33fa6aff1c`，run `39911032456`，artifact `12483735149`。原 ZIP：`build/task-first-evidence/ci-588855c/scale-push-artifact.zip`，SHA256 `f956dec3c4c9180dcebaffe8870d3794f4bc6c178da6d601b1671316838e7945`。

输入 `scale-short-selected/index.json` SHA256 `31f7c2999d5844ced54d69cb9aff7f25c7d9eb4961019bb31144f0b42f59a00b`；README SHA256 `134d14a45621f3a84df656779739b30b797d58797b20226075d9b979d1259f5c`。本次独立重算六组所有 selected 文件 SHA，并逐组确认 geometry 等于 accepted attempt 的 before/after target。六帧 protocol2、原5000ms内及四零差为取证资格，不是视觉结论。三份 dialog-return-state 的 before/after 对象精确相等；只证明原记录的 buffer、timer、scroll 恢复，不把它称为返回后截图验收。

沿用 `2026-10-19-m6-short-window-and-ime-trace-review.md` 的条件同行、固定动作及原右栏滚动合同，并按 `2026-10-20-full-scope-acceptance-gaps.md` 的既定 G1–G4 终点判定。Scale 产品源码643至588未变只作连续性辅助，不代替本次六图。

## 逐配置 G1 / G3

|配置|短窗实际观察|同帧量测|趋势 Dialog 实看|
|---|---|---|---|
|light1.0|当前重量在大数字上方；重量、完整实验性只读说明、去皮状态、底部采纳动作无重叠或裁切；右栏已显示开始/清零|主区 `[216,547,1778,1092]`；freshness `[216,849,1778,898]` 全49px；number/unit baseline均786|浅色圆角原生窗；标题/说明/取消完整；轴字与网格分离，无截字|
|dark1.0|结构同浅1.0；深色背景和辅助文字清楚，禁用动作仍可辨|同上；baseline均786|深色圆角窗，七项轴字及说明清楚；取消与内容分区明确|
|dark1.3|当前重量label与quantity条件同行；不是缩字压缩。完整只读说明独立一行；底部固定采纳动作完整；右栏手动计时说明两行完整|主区 `[216,564,1778,1092]`；freshness `[216,812,1778,873]` 全61px；baseline均749|字体增大后标题、轴字、两行说明、取消仍完整，无碰边或重叠|

三个图表均在 `[612,474,1948,1014]` 完整可见，宽1336、高540px。实际七轴字 `g / -2.0 / 8.0 / 18.0 / -10 s / -5 s / 0 s` 全部实看；1.0 plot local `[96,63.611996,1300,458.388]`，1.3 `[117,79.2504,1300,442.7496]`。轴字保护区与边界留白合理；右上最新点和左下短线段清楚，不把不同 segment 连成一条假连续曲线。原件记录5点，但重叠点不能据图声称五个独立可分辨圆点。

三个短窗均无 inline chart，独立趋势 Dialog 是批准的短窗呈现。底部固定动作 `[216,1110,2524,1236]`；去皮108px高，底部126px高，在density2.25下未削减48dp预算。本次只验显示与既有禁用状态，未实际操作硬件。

## G2 / G4：实际字体与颜色

实际固定 Noto Sans SC 2.004-H2 资产 SHA256 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da` 与实际 TextView/Paint 角色及中文/数字图形共同成立；不拿资源常量代实值，不要求不可观测的逐glyph内部fallback，也不声称与设计生成图二进制字体相同。

- quantity 数字实际144px、700；单位1.0为49.5px、1.3为56.25px、400。baseline精确相等并与实图吻合。
- freshness 与图表轴字1.0实际31.5px、1.3为42.3px、400；实际布局完整、无省略。已核当前字距0及无fakeBold的对应证据。
- 原生标题system角色实际45/53px、400；取消36/45.45px、500；说明31.5/42.3px、400。标题原生fontPadding/行框沿原生合同，不把标题误认作产品字体资产证明。实看中文无缺字和异常粗细，字距0、无fakeBold。
- 浅色产品主文字 `#FF101820`、辅助 `#FF657183`；深色 `#FFEDF3F2`、辅助 `#FFA9B9B8`。浅原生标题 `#DE000000`、深标题 `#FFFFFFFF`；取消分别 `#FF087A78` / `#FF83D6D0`。实际深浅底色、网格、青绿色曲线/按钮和禁用区关系与冻结视觉层级一致。配对PNG用于判断屏幕合成色，不把TextView ARGB直接等同最终像素。

以上已看对象的字体/颜色范围接受，不再因未要求的pressed/focus或私有fallback添加待办。

## 保留边界

1. dark1.3 初态右栏开始计时仅露顶部，清零及更下方工具不可见。support真实自然1784px、viewport528px，是批准的独立滚动区域；本六帧不证明每个工具滚达后的全文/触控/恢复，也不把屏外内容标已审。1.0下方工具同理。
2. Dialog证明只覆盖实际Dialog窗口，背景被dim的Activity不获同帧接受。返回状态JSON相等不是一张返回后实图。
3. 不覆盖普通全高主态、连接/手势/其他特殊态及未列配置。整run结果与这些六帧限定接受分开。

## 实看原 PNG 清单

以下路径均相对仓库根；每行是一张实际打开的原图，不是合成缩略图推断。

|配置 / 帧|原 PNG 路径|SHA256|
|---|---|---|
|tablet-user-landscape-light-font-1.0 / short-height|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-light-font-1.0/special-states/short-height/window.png`|`5c467bce2c8065af823b93eca334ca15c2054f75cba85e9912c9bfc6fa65197a`|
|tablet-user-landscape-light-font-1.0 / short-height-trend-detail|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-light-font-1.0/special-states/short-height-trend-detail/window.png`|`46230359746ff3211902dafc40bd630548858076bc2669c344c9852c2167aaef`|
|tablet-user-landscape-dark-font-1.0 / short-height|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-dark-font-1.0/special-states/short-height/window.png`|`20056c8a4d5b7cbcf4e59d29c8e4fd998a9777d149535e59282fce951c29b8c7`|
|tablet-user-landscape-dark-font-1.0 / short-height-trend-detail|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-dark-font-1.0/special-states/short-height-trend-detail/window.png`|`d6b88c644570951fd994f16aa1668e05c6ac97f9fe45777ed7f6ec2eb5c5b15b`|
|tablet-user-landscape-dark-font-1.3 / short-height|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-dark-font-1.3/special-states/short-height/window.png`|`3a639e2a416559bd69c416796c1791060adff5176e4b8a9cac645d8908372e3d`|
|tablet-user-landscape-dark-font-1.3 / short-height-trend-detail|`build/task-first-evidence/ci-588855c/scale-short-selected/tablet-user-landscape-dark-font-1.3/special-states/short-height-trend-detail/window.png`|`0dfdf23e36301abac881893f5ff8a43ae2c252a0e6ee8bd60a068ff7b1104e03`|
