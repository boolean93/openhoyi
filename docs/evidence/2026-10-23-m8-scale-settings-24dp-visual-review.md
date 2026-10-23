# M8 ScaleSettings 24dp 新原件有限视觉验收

## 结论

实际打开三主态和六张 1.3 说明滚达 PNG，共 9 个证据路径。**light1.0、light1.3、dark1.3 的左右24dp差异已关闭；已看的两档1.3说明全文可读，未发现缩窄带来的裁字、重叠或异常换行。** dark1.0 原件缺失，四配置总体验收未完成。不能签 M8 整页、Settings 整 run 或全部24页通过。

## 来源与核验

run `39906804817` / job `120260530808`；source=head `4076c2d862f94608fa6ec3d405c91d23a6b6867c`。原 ZIP 777121915B，SHA256 `9429b39e2181f7e99307f434137be40b9cc73f98855144b5ba556a8a595ebfd9`，沿用原件索引的全 CRC 核验，不重新全解包。

索引：`build/task-first-evidence/ci-4076c2d/settings-scale-acceptance-index.json`。本次独立重算该索引全部已提选件 SHA；9组已看 PNG 的配对 geometry 均等于对应 accepted attempt 的 before.target 和 after.target，PNG SHA 等于 proof.imageSha256。不声称重新执行像素协议。所有图为原2560×1600，查看工具呈现缩放版；精确尺寸用原始 geometry，不用显示缩放比推测。

对照 [24dp产品修正计划](../plans/2026-10-23-scale-settings-body-padding.md)、[冻结分组](../superpowers/plans/2026-10-18-scale-settings-groups.md)、[固定中文字体](../superpowers/plans/2026-10-16-fixed-chinese-typography.md) 和旧有限审查。没有拿旧5d08图代替本批缺失档。

## G1–G4 实图结论

| 项 | 本次观察及实际配对字段 | 边界 |
|---|---|---|
| G1 | 三档body x596..2144，1548px/2.25=688dp总宽；组/卡 x650..2090，两侧54px=24dp，内容640dp。卡内文字 x686..2054，即内部16dp、文字宽608dp。标题、组标题、卡片、分隔线对齐，三组层级清楚，无横向溢出。 | 本三档24dp差异关闭；不是将688扩宽后伪装通过。 |
| G2 | 中文、拉丁字母和数字清晰，未见缺字、假粗或风格突变。实际 precision weight400、fakeBold=false、letterSpacing0，字体资产 SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da` 与固定基线关联。 | 不把 Typeface 对象hash或已知资产当每个回退glyph字体证明；公开API仍标 UNKNOWN。 |
| G3 | 主态标题/副标题/精度行/能力行完整可读且不碰撞。Switch 实测126px=56dp，正文1.0为36px、1.3为45.45px。两档1.3的目标9本地计时和目标11操作限制均1行，目标13协议支持2行；均42.3px/weight400，lineHeightGetter56px，Layout高度分别61/61/117px，ellipsis0。目标11底到1465、目标13底到1465恰在app下缘，图中末字/第二行可读，配对visibleRect等完整rect。 | 上移时非目标标题裁切属整页滚动，不签所有内容同时可见。light1.0主态底部协议仍在屏外；其当前geometry显示自然两行，本次没有看该档协议滚达，不沿用旧一行结论。无图表。 |
| G4 | 浅色深主字、灰辅助与白卡/浅底层次清楚；深色浅主字、灰绿辅助与暗卡边线可辨；青色导航选中、关闭开关和未连接能力文案一致。target13底部辅助文字在两主题均可读，无覆盖系统栏。 | 这是已看区域视觉可读性判断，不声称全页量化对比度或与生成稿逐像素等同。 |

两档 target9 与各自主态 PNG SHA 相同，仍核了独立路径的 proof/geometry；因此9个查看路径对应7种PNG内容，未把重复像素当额外状态覆盖。主态底部裁切通过原滚动显示，不是缩字或隐藏状态。协议文案完整保留“实验性只读/未实机验收/仅离线解析/不提供连接或控制”，不构成硬件能力验收。

## 实际查看清单

下表路径相对 `build/task-first-evidence/ci-4076c2d/`；每项配对同目录 geometry.json 和 window-frame-sync.json 均已核。

| 配置 | 原 PNG 路径 | SHA256 |
|---|---|---|
| light-font-1.3 | `settings-selected/tablet-user-landscape-light-font-1.3/scale-settings/window.png` | `17abcb518761b5b9e42ff96c8fbb63f7b8efb8388b8c61def8ea3f8bfea24884` |
| light-font-1.3 | `settings-selected/tablet-user-landscape-light-font-1.3/scale-settings/main-reachability/target-9/window.png` | `17abcb518761b5b9e42ff96c8fbb63f7b8efb8388b8c61def8ea3f8bfea24884` |
| light-font-1.3 | `settings-selected/tablet-user-landscape-light-font-1.3/scale-settings/main-reachability/target-11/window.png` | `5edf8fd2b3b40b5fed487cf5645abc478badf204764e4ba14b54e11f23816463` |
| light-font-1.3 | `settings-selected/tablet-user-landscape-light-font-1.3/scale-settings/main-reachability/target-13/window.png` | `d4e6bd11773cfadb16f4887100a5d66f4e0e38698e4b952a28204bcbc022a25f` |
| light-font-1.0 | `settings-selected/tablet-user-landscape-light-font-1.0/scale-settings/window.png` | `3942df307f7d725d67802294a2fac26c4ea696bd8daacbab28610122ab2c13ab` |
| dark-font-1.3 | `settings-selected/tablet-user-landscape-dark-font-1.3/scale-settings/window.png` | `10c22fb7f6880ddccf3ddd1bd1a86de8874591c2e4dcc45a1e4828e96e497419` |
| dark-font-1.3 | `settings-selected/tablet-user-landscape-dark-font-1.3/scale-settings/main-reachability/target-9/window.png` | `0d6d7f6632da686cae5b1f2131ad660b324361039b3075dd6eeee6b707a54c8e` |
| dark-font-1.3 | `settings-selected/tablet-user-landscape-dark-font-1.3/scale-settings/main-reachability/target-11/window.png` | `3c1aa2e32ea32cef1573ddcd425ab5fe76d9153ebc8f1da6b2f3e9c60a62f855` |
| dark-font-1.3 | `settings-selected/tablet-user-landscape-dark-font-1.3/scale-settings/main-reachability/target-13/window.png` | `8ef405bccad98d22fb108f7e427b3ef882a2ff545daa0b1d6c79863e9a90d3fd` |

## 明确保留

- dark1.0 因本 run ENOSPC 缺原件，须同改动的新真实图，不用其它主题推定。
- 本次未看完整14+14、所有 restored 图和特殊态；索引 exactScrollRestored/机械 PASS 仅作库存，不能转视觉 PASS。
- 首败 MachineSettings 温度 tab 的48dp问题不属于本页24dp修正，也未被此审查关闭。整个 Settings run 仍失败，ENOSPC 是后续错误，不篡改首因。
- 本文只审以上原件，不改源码/设计，不构建、不触发CI、不提交推送。后续窄视觉只需补未审范围，不必重复签已看三档。


## 阶段二：新增10张去重原PNG实看

本阶段实际逐张打开 `settings-next-visual-index.json` 的10张去重PNG，不以图相同替代各目标geometry。索引SHA256 `6f5fe6ed3d02f46900b17fb0688917fdd4de052a5b2b2e64c6153686cd11efa2`。仍为4076c2d / Settings push39906804817原件，仅light1.3与dark1.3；不混入新588运行。本阶段独立复算所有列出的选件SHA；10组PNG==proof.imageSha256、accepted before.target==geometry==after.target、actual-text-typography.nodes==geometry.nodes均相等。逐帧耗时1194/1061/915/932/961/1104/909/972/1055/1087ms；这些认证字段不替代实看结论。

### 逐范围判断

| 范围 | 实际观察及G1–G4 | 限定结论 |
|---|---|---|
| 两主题main target12 | scrollY160时“协议支持”标题完整，实际框[686,1399,2054,1465]，45.45px/weight500、Layout66px、一行无省略；与上方操作限制分隔清楚。标题深浅色均可读，仍沿24dp外边距。此帧协议正文在屏外，不拿标题完整代正文完整。 | 新增接受两个target12标题；正文沿前阶段独立target13结论，不称同初屏。 |
| light main restored | 页名、副标题、精度关闭开关及能力行回到顶部；下方说明回原可见范围。geometry实际settings scrollY0。 | 新增浅色恢复帧视觉检查；不要求系统时间/滚动条像素与初图相同，不据此签深色restore。 |
| 两主题precision-recreated | 页名/三组层级与原布局一致，精度开关确为开启，青色圆钮在右、开关关闭说明仍保留。显示字号没有因重建而缩小；卡片边距/文字对齐未见改变。 | 接受重建后**可见**开启状态与布局，不把截图当实际硬件精度改变，屏外说明不在这两帧签全文。 |
| 两主题all-instructions target13 | scrollY295，三段说明正文均可读：本地计时不用于自动停水、未知/进行中禁止去皮换秤取粉量、协议实验只读及未实机验收限制。正文42.3px/400；协议两行117px，完整框[686,1348,2054,1465]，底部未被系统栏盖住。标题45.45px/500，分隔/卡内16dp保持。顶部“显示精度”标题随整页滚动裁切，不是本目标全文失败。 | 接受这两个特殊态目标帧中的三段说明；与开关关闭的主态target13不是同业务态，分别留证。 |
| dark all-instructions target11 | scrollY56，“操作限制”正文完整落在[686,1404,2054,1465]，42.3px/400一行61px；协议标题/正文此时在屏外。暗底、浅主字、灰辅助、青色开启状态层级清楚。 | 接受该特殊态target11，不拿其替代target13或全部目标。 |
| 两主题real-scale-no-data | 实际画面是**电子秤主页而非ScaleSettings**。中央重量为破折号+g、电子秤未就绪、去皮灰态与“尚未请求”；右侧00:00.0和App本地计时/不是秤内计时说明清楚。顶部仍写“演示模式，不是真实设备读数”，不能依据fixture名字宣称真硬件验收。 | 只接受缺数据不伪造重量及已见状态表达；不计入设置页24dp证明，不签整电子秤页。 |

### real-scale-no-data的额外边界

两张电子秤主页右侧“当前协议未提供电量”贴近支持栏滚动条。light配对TextView原文为9字，rect==visibleRect=[2085,1107,2523,1173]、actual Layout宽438px、行left33/right438、45.45px/400、无ellipsis；所以不能仅凭缩放预览说它已被父容器裁切。原图右边同时存在滚动条，hierarchy完整不等于绘制遮挡排除：本轮不签该末字逐glyph无遮挡，也不据此直接判产品裁切根因或改布局。若扩展电子秤主页全文验收，应核该同帧实际末字/滚动条覆盖关系；当前只记录边界，不生成新修复任务。

G2已核同帧actualTypography节点，不用独立资产常量填未知glyph身份。已见中文、单位与数字无明显缺字或字体风格突变；公开API的UNKNOWN glyph边界保留。G4仅是这两主题已见区域的可读性与开关状态对比，不宣称所有颜色状态/量化对比度通过。

### 新增实际查看清单

以下路径相对 `build/task-first-evidence/ci-4076c2d/`，每项已查看原PNG及配对geometry/proof/actual text。

| 配置/范围 | PNG路径 | SHA256 |
|---|---|---|
| light-font-1.3 / main-reachability/target-12 | `settings-next-selected/tablet-user-landscape-light-font-1.3/scale-settings/main-reachability/target-12/window.png` | `1d6950bcfaf03a83348a10e1abb1dda15cb8668d394fa01d27867859b27cf90b` |
| light-font-1.3 / main-reachability/restored | `settings-next-selected/tablet-user-landscape-light-font-1.3/scale-settings/main-reachability/restored/window.png` | `84d51ca6fade1f1040f8be78af918874ca62b61d9ed7f812a19310c443967f21` |
| light-font-1.3 / special-states/precision-recreated | `settings-next-selected/tablet-user-landscape-light-font-1.3/scale-settings/special-states/precision-recreated/window.png` | `3bc3d10995738f3e589219ee90847cd69161ef02f7bfdf89cc3f9c98af1c81d7` |
| light-font-1.3 / special-states/real-scale-no-data | `settings-next-selected/tablet-user-landscape-light-font-1.3/scale-settings/special-states/real-scale-no-data/window.png` | `e7e62b6fe5fa244f42ab94f18a086b66e8cee0e67fb12e60c287284b7a6b5b32` |
| light-font-1.3 / special-states/all-instructions/target-13 | `settings-next-selected/tablet-user-landscape-light-font-1.3/scale-settings/special-states/all-instructions/target-13/window.png` | `de30cb1a1a996569dc1d0ef74fd9e54e8979b3e3d57a6ac70232e81816bba11a` |
| dark-font-1.3 / main-reachability/target-12 | `settings-next-selected/tablet-user-landscape-dark-font-1.3/scale-settings/main-reachability/target-12/window.png` | `ba13d41fdce96d7be5d3bad1761e665a6ed87247b045935142602bfb530f9602` |
| dark-font-1.3 / special-states/precision-recreated | `settings-next-selected/tablet-user-landscape-dark-font-1.3/scale-settings/special-states/precision-recreated/window.png` | `08023a6f848f08eebcadcbf610df84edcc6ef978ebb13ff90afbf47341b8f266` |
| dark-font-1.3 / special-states/real-scale-no-data | `settings-next-selected/tablet-user-landscape-dark-font-1.3/scale-settings/special-states/real-scale-no-data/window.png` | `28fb6535611b97ed460a17996b3ca99b9cb93c7ae8fbed644f8dfbc67a29fd19` |
| dark-font-1.3 / special-states/all-instructions/target-13 | `settings-next-selected/tablet-user-landscape-dark-font-1.3/scale-settings/special-states/all-instructions/target-13/window.png` | `36989340c933bdef8b1836c94db0ba3b5eec28b0dfda05c9e4d8944fc8707134` |
| dark-font-1.3 / special-states/all-instructions/target-11 | `settings-next-selected/tablet-user-landscape-dark-font-1.3/scale-settings/special-states/all-instructions/target-11/window.png` | `94bdd92443ce27ac4a04e00bdef6fb2680f94761e7946e959a93ac0944d5ccbe` |

### 仍未关闭

main 14+14库存不等于全部逐目标视觉验收：结合前阶段，目前每主题只明确审到main target9/11/12/13；其余20个目标即使PNG与主态相同，仍保留各自target/geometry未逐项审的状态。light restored本次实看，dark restored未独立实看。特殊态仅覆盖上表，不签所有交互、Dialog及恢复。dark1.0原件缺失仍保留；light1.0全文滚达仍未在本阶段补审。Machine首败、设备ENOSPC与整run FAIL不因这些可见范围接受而关闭。没有修改源码、构建、设备操作、提交或推送。
