# Legacy短窗Detail零高workspace：下一步封闭诊断与修复边界

旧5d08 PR失败已证实存在真实祖先viewport不足，不是单凭screenMappingVerified=false推定坐标算法错。先解决零高workspace的分配，再谈内部按钮滚达；不通过多滚几次、延时或取消whole门禁掩盖。

- 基线：head5d08fd658ad7300d0478a2b5e117e88eb3c99f11，实际PR merge0109982664efc7b3951094c35a6f0eaed586fbfb（本地无merge树，源码推理按head，不冒称逐字审核merge）。原件 `build/task-first-evidence/ci-5d08fd6-legacy-pr/artifact.zip` SHA `77e208506d2cab48301fbc6531433b3ebce0e2fcef897bd1a0788e3644587526`。
- 分支/PR：现授权feature/task-first-ui→develop，按[Git工作流](../git-workflow.md)，root合批普通推送，禁止直推主干。
- 文件/owner：后续同一Sol只读复核 `LegacyHistoryDetailActivity.kt`、`TaskWorkspaceLayout.kt` 与 `LegacyVisualMigrationChecks.kt`；本轮只写规划，不授权凭猜测改共享workspace/坐标桥。必要测试诊断仅LegacyVisualMigrationChecks/现Legacy runner；产品候选先交Astra静态预算设计复核再实施。
- 设备/发布/回退：没有设备写入、未知重发、数据迁移、签名/安装/发版；保留原失败与ZIP，代码仅独立revert，不清记录。

## 本次离线追加确证

直接读取原ZIP的 `raw-device/<merge>/tablet-user-landscape-light-font-1.3/detail/special-states/short-window/reachable/geometry.json`：

| 真实节点 | screen rect |
|---|---|
| product/page/root | [0,485,2560,1115] |
| legacy/detail/name | [216,674,2524,1003]，高329px |
| legacy/detail/metadata | [216,1021,986,1082]，可见至1079 |
| task-workspace | **[216,1115,2524,1115]，高0px** |
| legacy/detail/full-notice（reach后） | [252,1115,2488,1223] |

初态button顶1971，support.scrollY从0到856后顶1115，仍从root底开始，原/reachedPNG相同。button `rawGlobalVisibleRectDefined=false`、`globalVisible=false`，故mapping=false是确实无可见交集时的fail-closed状态；其raw rect/Point不是可拿来修映射的有效可见数据。不得删除shown/rootShown条件或强行补root偏移把它变成visible。

源码对应：Detail自然长heading在workspace前；workspace是body内height0/weight1。短窗630px，标题/heading占满剩余空间后workspace落到0高。TaskWorkspace supportingMinimum用min(reachableActionHeight,height)，height=0时也为0；“childheight-supportheight=1000”只报自然内容差值，不证明有真实viewport。TaskPageFrame那个外层ScrollView是导航rail，不是能滚动整个Detail的内容host。当前requestRectangleOnScreen返回值已由测试写report，不能误说完全未采。

因此当前可确认的具体产品问题是**该短窗长heading挤掉整个workspace**。现图不足以计算最小可用正文/图/按钮组合，也不能据此断言修改通用TaskWorkspace即可解决。

## 最窄下一步

1. 不先再跑同一CI收一遍相同失败。由Sol在固定head源码/上述原geometry上列自然预算：根insets、shell/page header、Detail heading各真实行高/margins、body可用高、workspace剩余高、support viewport与child的measured/laidOut高和margin/padding、chart自带viewport、实际最大scroll范围。原geometry若已有则直接引用；仅缺字段加入shortDetailState，随下一合法候选CI原样落盘，不能改成合成dp预算。
2. 设计候选限定Detail本页的短高fallback：当完整自然heading+真实workspace最低内容预算超过可用body时，使用永久的本页内容scroll承载原heading与原workspace，并为原workspace分配真实最低自然高，使完整说明按钮可滚达；正常足高split保持原固定操作/两栏。优先复用本页可复用容器；当前并不存在整页内容scroll，禁止误把导航rail当其替代。新增永久host不得重建chart/button/list或改监听、字号、heading全文、原数据。
3. 此候选的最低高必须从实际chart Paint/标题与48dp按钮自然高、原间距计算；不得硬编码本图329/630/856/1000，也不得通过缩字体/藏标题/减96dp“修复”。只保证support内部scroll，而祖先workspace仍0高，不是有效候选。若候选需要扩大到共享TaskWorkspace/TaskPageFrame，先报告理由及受影响页面，不直接扩散修改。
4. Sol先给上述精确测量路径、永久host/对象关系和正常split不变证明，由Astra复核再实施；当前不直接指定一个未验证的minHeight常量。这个设计检查可离线完成，不需要为诊断盲跑CI。
5. 测试保旧short-window分支：真实resize、原对象/Window/token、非零祖先viewport、每层mapping、真实按钮48dp/whole、原chart实际plot≥96dp、原档案/selected/native不变；若新增本页scroll，逐一保存/恢复所有永久scroll且在原窗口恢复后采restored。short初态、每次真实reach及restored都独立原PNG/protocol2/5秒/四零差。固定split仍初态完整，不借stacked滚达放宽。

## 不混入本任务

Curve/History本轮机械越过只对其格有效；Detail视觉未通过。capture push39884256142的“查看详情ambiguous”另有failure.png但缺同刻hierarchy，不能借早期Curve idle-accessibility诊断它；本计划不改capture脚本，也不把该问题与此零高workspace合并。

## Sol离线预算候选（待Astra审核，未实施）

原ZIP中短窗root为 `[0,485,2560,1115]`（630px；density 2.25、font 1.3，insets 0、IME false）。长名 `[216,674,2524,1003]` 高329px，metadata高61px且底部仅见至1079，readonly高79px至1100；workspace从y1115起高0。support按钮自身高108px，`scrollY=856`后按钮顶仍是root底y1115。图View的底层backing高643px、Paint plot高160.1106dp只是被零高祖先遮住，绝非可见96dp。

对照足高原件：短名workspace高1044px、chart viewport高859px、双栏split；长名workspace高704px、chart viewport高393px、stacked，仍有真实support可滚。故fallback只按本页真实可用body与自然内容比较，不按长名、短名或某个固定窗口高度切换。已知长名足高body约1184px；短窗body无法容纳上述heading加workspace。

候选在Detail本页增加一个**永久、单一子节点**内容ScrollView，承载原heading和原workspace；chart、support、按钮及监听仍为同一实例。足高时子节点按viewport精确高测量，保留workspace原height=0/weight=1及既有split/stacked、固定操作位置；不足时子节点按自然总高测量，给workspace动态最低高并让外层内容滚达，不修改共享`TaskWorkspaceLayout`或`TaskPageFrame`。单用`fillViewport`无效：ScrollView的UNSPEC测量下weighted workspace仍可能为0，须显式分配。

最低高从当前布局实测推导，不写死fixture像素：primary纵向padding 36+36px、标题77px、chart上距36px，加至少96dp=216px可见Canvas viewport，约401px；support至少`max(56dp=126px, 原按钮108px, 48dp=108px)=126px`，故workspace至少约527px。图backing继续保留原643px/160.1106dp Paint plot，96dp viewport只能说明可滚露出连续96dp plot，轴字/legend仍须独立滚达与whole核验。heading自身约426px，加原间距与workspace后总需求约1007px；足高长名body约1184px仍走原布局，短窗触发fallback。这些是原fixture诊断预算，最终阈值须以运行时自然测量值取代。

下一合法候选CI需报告shell/header/body、heading每项自然行高与margin、外层scroll viewport/child/max、workspace模式及真实高、chart Canvas屏内交集与plot、support viewport/child/max、全部scroll身份及x/y。短窗初态、每次reach、restored分别保留原PNG/protocol2/5秒四零差；窗口/token/对象身份不变，恢复原窗口及所有永久scroll后再采restored。固定操作在足高原split不变；短高不能缩字、删内容、降低96/48门禁或放弃短窗支持。当前只读候选，未改产品/测试。

## Astra 候选设计审核：允许本页最小实施

已复读本页 `LegacyHistoryDetailActivity` 的 weighted workspace/primary chart viewport/固定 full-notice，以及 `TaskWorkspaceLayout.onMeasure` 的 stacked secondary 最低动作高与 primaryRequired 分配。候选处理的是已证实的祖先零高，不是修改可见性映射；**允许同 Sol 串行实施本页永久内容 ScrollView 候选**，不修改共享 Workspace/PageFrame。

实施约束：527px、401px、1007px 只作本 fixture 预算解释，不得落为阈值常量。必须按当前可用宽度实际测量 heading、primary 标题/上下 padding/间距、support 按钮自然高度与边距，显式给 workspace 剩余或最低高度；测量状态切换不重建原子树。足高必须恢复原 viewport 精确分配及 weight 行为，不能因 ScrollView 的 UNSPEC 测量把原 split/固定操作改成自然全长布局，也不得依赖前一帧 measuredHeight 造成阈值振荡。

96dp 指屏内真实 Canvas plot 交集，不是 viewport 高度、backing 高度或外层 scroll 可滚范围。候选保留原图 backing 后，短窗测试须在原 chart/外层两层 scroll 上实际滚达，证明至少连续96dp plot可见，并独立覆盖轴字/legend；若实测外层可视区不足，保持失败，不把“理论可滚”写成通过。新增永久外层 scroll 与原 support/chart/prose 各自身份、x/y、窗口恢复前后均要记录并精确恢复。

此为实施许可，不是产品修复完成或视觉验收。源码冻结后再静态审实际测量实现，合批 CI 后审短窗初态/reach/restored 新原图；未触发 CI、未开发。

## Sol 最小实施（待Astra静态复核、root统一构建）

仅在`LegacyHistoryDetailActivity.kt`增加Detail本页永久`legacy/detail/content-scroll`；原heading、workspace、chart、support/prose和按钮仍只创建一次。ScrollView的`measureChildWithMargins`按当前viewport与自然预算将原同一content子树精确测量：足高时content=viewport、原workspace height0/weight1占余量；短高时content=真实heading高度+原边距+workspace最低预算，由外层滚动保证祖先非零。最低预算按当前宽度实测原samples标题、原button自然高度、primary padding/chart margin与96dp实际viewport、support 56/48dp下限计算；没有落入527/401/1007px fixture常量，也未改共享Workspace/PageFrame、字号、说明全文、监听、BLE/业务。

短窗测试/runner新增运行时自然预算、各祖先/child与plot屏内交集、永久outer/support/chart/prose全部scroll身份及x/y；plot至少连续96dp真实可见且宽度非零，Canvas每项legend/轴字按原Paint glyphBounds逐项真实滚达、逐项原PNG和protocol2，之后原scroll精确恢复再走原按钮reach。原初态/每个reach/恢复均维持5秒与四零差；足高非fallback和短窗fallback分别显式核。尚无新CI原图，不能称视觉通过。静态检查和源码哈希见实施交接。

## Astra 实施静态审：产品候选保留，取证两项待修

直接核对实际diff并验证SHA：产品 `37b3d09198f6c25964014f90d0770a96cac8ed7667c399c680e33bbddc98e1d0`，测试 `4603b54a4da7fa062c8023b75c41f561e107a92101eeea1d877652c90596afc6`，runner `7c18a2250a992d8c305261b54d4b7f5a9afda95f0c2244e3c72709e6086823e6`。

产品实现按当前MeasureSpec重测自然heading/title/button，永久content只建一次，足高child=viewport、workspace原weight不变，短高显式扩大child；没有527/401/1007硬编码或共享布局改动。原布局恢复与全部ScrollView精确恢复链已有。此轮未发现需要立即改产品的确定缺陷；仍须实际新图验证足高位置/模式及短窗可见96dp，不能将预算公式当运行通过。

**静态暂不通过，最小修复限测试/runner：**

1. **轴字/legend的whole证明不是同帧。** `shortDetailChartReachability`先取`chart.visualEvidence.operations`，capture后又读`chart.getLocationOnScreen`、`visibility(chart)`，与accepted state的visibleRect拼接；报告wholeGlyph不是accepted target内的测量。请在`shortDetailState`同次observe记录chart screen origin、实际Canvas完整text operation索引/role/text/glyphBounds，以及每项屏内包含结果与mapping状态。reach只读frame.target，不在返回后访问View或先前Canvas box判whole。plot的visible96也显式携带并核同帧mapping有效，不能只读visibleRect。
2. **runner只核角色存在，不能证明全部原轴字已到达。** 目前每类至少一个role即可，删掉其余tick/legend仍可通过。以初态accepted Canvas实际完整text operation列表建立精确预期key集合（包含index/role/text/box），逐一唯一对应reach且无遗漏/额外；每步从accepted target重算同帧glyph bounds+origin在visibleRect内，不仅信wholeGlyph布尔。每步核activity/window/decor/token、workspace/support/chartViewport/button/list原身份，不能只比chart与outer两项。恢复继续精确比所有scroll及原业务/native不变量，不减原5秒/四零差。

这不是要求重写通用框架，也不增加fixture。先补两项后冻结；本轮未构建、未签视觉或修复完成。

## Sol 同帧取证补正（待Astra复核）

产品源码保持上述`37b3d...`冻结。仅改Detail短窗测试及Legacy runner：`shortDetailState`在每次帧target内同时保存Canvas全部text operation的原索引/role/text/glyphBounds、chart屏幕原点、有效mapping、clip矩形和逐glyph包含结果；plot交集同target带原plotBounds、density与mapping。滚达链只从已接受的初态operation表定目标、从每步已接受target判whole，不在截图返回后再混入View或先前Canvas测量。runner从初态完整operation表枚举唯一目标，逐项对原索引/role/text/box与accepted target复算包含，且逐帧核Activity/Window/decor/token与全部原对象身份；plot亦从同帧原点/box/clip/density复算真实96dp。原PNG、protocol2、5秒四零差、scroll和业务恢复均未减。静态检查见交接；仍无新设备原图。

## Astra 两项增量复核：静态通过

实读新冻结测试 `62758a217a2531d71f348258517e1b0fa071ba2ca3665f945377d6d0ac18ab48`、runner `b11a3bd11fe5ec938f031ec412ad353e4defc127a32037e7e8ba89c2a13a5a92` 并核hash；产品仍前述37b3版，未要求追加产品变更。

两项已关闭：shortDetailState在同次observe记录实际Canvas完整text索引/role/text/box、chart origin/clip/mapping及wholeGlyph，reach仅以初态accepted operation安排滚动、以新returned target判断可见性；不再capture后拼接View坐标。runner要求初态精确完整operation序列与所有reach一一对应，每步核全部Window/对象身份、完整operation值不变，并从同帧box+origin+visibleRect独立重算plot可见96dp与glyph包含。chart-restored及最终窗口恢复仍核所有scroll精确值，原协议/业务不变量未取消。

允许交root统一构建及原矩阵运行。当前只解除两项静态阻断；真实短窗图/轴字滚达、足高原布局与5秒预算仍待新CI原件，不签产品或视觉完成。
