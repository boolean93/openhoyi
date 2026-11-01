# M8 ScaleSettings 588 主态滚达与恢复视觉验收

## 阶段一结论：dark1.0 的14目标与恢复

**dark1.0 主态14个文本目标、底部协议全文滚达及恢复接受。** 本轮新增打开target12、target13、restored三个PNG路径；0–11的PNG与已实际查看的同源dark1.0主图字节完全相同，复用其视觉观察，但14份独立geometry/proof均重新核对。15条目标/恢复路径对应3种PNG内容，不把同图算成15种新画面。

本结论不签其它配置尚未审目标、特殊态14目标、整个M8或Settings运行通过。初屏仍不能同时看到全部协议正文；整页滚动是批准适配，不缩字、不隐藏状态。

## 来源及配对

source=head `588855c96fdceec79bf87f0fd5950f33fa6aff1c`，push run39911032413/artifact12483598594。原ZIP SHA `1af608e4384ab4a27c3e3faed7afeaab8f3fcb82b1ef052893e34543f29b64e8`，沿已保全5999项CRC记录。本轮没有重新下载或全解包。

索引 `build/task-first-evidence/ci-588855c/settings-reach-selected/index.json`（SHA `7577ae9e87940f6a15e445f375d0d3e5a15e7b04cd9842d056f0d31bb0ec763c`）；README SHA `50e10b03522914fdcf9559fb91c577fe346e4331f8f2fe1c17c81739cbf9dc29`。独立从原ZIP按成员读取dark1.0全部14个PNG/geometry/actual typography/proof并逐SHA核对；geometry等于accepted before.target和after.target、typography.nodes等于geometry.nodes、acceptedActualTarget确在对应nodes、PNG等于proof.imageSha256。恢复本地选件SHA、同帧target及原两scroll精确相等另核。没有把索引机械PASS代替实图。

对照[四主态审查](2026-10-23-m8-scale-settings-four-main-review.md)、[冻结分组](../superpowers/plans/2026-10-18-scale-settings-groups.md)、[字体基线](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)及[批准验收终点](2026-10-20-full-scope-acceptance-gaps.md)。

## G1–G4 与逐目标范围

- **G1：** 688dp总body、左右24dp、卡内16dp保持。target12 scrollY29，target13 scrollY142；横向原卡/文字对齐不变。滚动时顶部页名出屏属同一整页滚动，不冒称固定页头或全页同屏。rail保持，未见目标文字与卡边/分隔线碰撞。
- **G2：** 已见中文和Felicita/ACAIA/DIFLUID字形、粗细、标点正常。实际同帧角色为title54px/500、section45px/500、body36px/400、说明标题36px/500、aux31.5px/400；letterSpacing0、fakeBold=false，与固定Noto Sans SC资产/冻结角色相符。本范围G2接受；公开API不提供逐glyph内部fallback是来源边界，不再据此新增pending。
- **G3：** 0–11全文在主态清楚；target12“协议支持”完整框[686,1411,2054,1465]、一行Layout52px。target13正文完整框[686,1370,2054,1465]、两行Layout95px，ellipsis0；第二行“制。”清楚且未被系统栏截断。文字自然换行为两行，未缩字。其余正文Layout46px、主文字52px，heading65px、title79px，目标框完整。target13与target12属于不同滚动帧，不能说原初屏全部可见。
- **G4：** 实际主字 `#FFEDF3F2`、辅助字 `#FFA9B9B8`；暗底/暗卡/边线层次与原主态一致，协议辅助字两行均可读，青色导航及关闭开关保持。当前已计划状态G4接受，不因未计划pressed/ripple新增门槛；不声称未知原稿逐像素相同。
- **恢复：** 实际restore图页名/副标题、精度关闭、未连接能力及帮助上部回原位置，PNG SHA与该配置main相同；原两ScrollView均x/y=0精确恢复。不是重建/开关变化/特殊态恢复的替代证据。

下表每目标对应 `tablet-user-landscape-dark-font-1.0/target-N/geometry.json`，完整ZIP路径与各自SHA见索引。单独核了每个目标的全文/ellipsis0/实际字体，未因共享PNG省略几何。

| target | 文本 | 字号px / weight | PNG组 |
|---|---|---|---|
| 0 | 秤工具设置 | 54 / 500 | A |
| 1 | 仅调整本 App 的显示；不发送设备设置命令 | 31.5 / 400 | A |
| 2 | 显示精度 | 45 / 500 | A |
| 3 | 显示到 0.1 g | 36 / 400 | A |
| 4 | 关闭时显示到 0.01 g | 31.5 / 400 | A |
| 5 | 当前协议能力 | 45 / 500 | A |
| 6 | 未连接，无实时设备能力 | 36 / 400 | A |
| 7 | 使用说明 | 45 / 500 | A |
| 8 | App 本地计时 | 36 / 500 | A |
| 9 | 手动开始；不是秤内计时，也不用于自动停水 | 31.5 / 400 | A |
| 10 | 操作限制 | 36 / 500 | A |
| 11 | 萃取进行中或结果未确认，不能去皮、换秤或取作粉量 | 31.5 / 400 | A |
| 12 | 协议支持 | 36 / 500 | B |
| 13 | Felicita 提供实验性只读连接，尚未实机验收。ACAIA / DIFLUID 仍仅有离线解析；不提供连接或控制。 | 31.5 / 400 | C |

### 实看与复用PNG精确清单

路径相对 `build/task-first-evidence/ci-588855c/settings-reach-selected/`：

| 组/恢复 | 路径 | SHA256 |
|---|---|---|
| A，target0–11 | `png/b649e3cd2115a3b938bdda40573d980c399be877cd0a1c38a9019dd3697e955b.png` | `b649e3cd2115a3b938bdda40573d980c399be877cd0a1c38a9019dd3697e955b` |
| B，target12 | `png/0a6b05023a2e001673f2dc7d6ed2ecd043da1b6bbcbb196df30b3f1b6adfa199.png` | `0a6b05023a2e001673f2dc7d6ed2ecd043da1b6bbcbb196df30b3f1b6adfa199` |
| C，target13 | `png/14a4ad02ceb683d4334971d4c98cffcda9129f22cba671bf999dbb669f4d24a2.png` | `14a4ad02ceb683d4334971d4c98cffcda9129f22cba671bf999dbb669f4d24a2` |
| restored，内容=A | `tablet-user-landscape-dark-font-1.0/restored/window.png` | `b649e3cd2115a3b938bdda40573d980c399be877cd0a1c38a9019dd3697e955b` |

原PNG2560×1600，工具展示缩放版；精准坐标/行框来自原geometry，未从缩略图估dp。协议限制正文没有被改写成硬件支持结论。

## 待审

其余三档主目标42项及各自restore尚未在本阶段逐项完成；优先两档1.3 target11/13和restore。特殊态目标沿各自库存，不与主态混签。Machine首capture失败、真实设备验收与本页视觉分账。只新增本文，未改产品或已提交批次文档。

## 阶段二：其余三档42目标与3份恢复完成

**本阶段接受light1.0、light1.3、dark1.3各14个主态文本目标和各自恢复。结合阶段一，四档56个主态文本目标及4份restore全部完成限定G1–G4验收。** “主态全文滚达与恢复”不再列为笼统待审；特殊态、其它Settings页面及硬件范围不包含在本结论内。

本阶段优先实际打开两档1.3 target11/13及restore，随后打开两档target12、light1.3 target0–10共用图、light1.0 target0–11共用图及target12/13。共新增12张不同PNG实看；dark1.3 target0–10与本轮已审588主图相同，复用其视觉；light1.0 restore与本阶段已看的target0–11共用图相同。45条目标/恢复路径对应13种PNG内容，不把重复图冒充独立状态。累计两阶段60条路径对应16种PNG内容（14种target内容＋2种独有restore内容），均已实际看过或明确同SHA复用。

### 独立取证核对

再次按原ZIP成员读取余下42目标的PNG/geometry/typography/proof，全部原SHA吻合；逐目标geometry==accepted before.target==after.target，acceptedActualTarget在自身nodes内、全文与索引一致，完整rect==visibleRect、ellipsis0、实际Layout高度不超过content、各行水平边界不超宽。实际字体值/weight与每条索引对应，letterSpacing0/fakeBold=false。不是仅核一次共用图的geometry。

另核三份restore选件SHA、同帧before/after target、actual typography.nodes、PNG proof SHA；reachability.beforeScroll==restoredScroll，两原ScrollView实际x/y均回0。原protocol2/5秒/四零证据沿每个成员保留，没有新增拍摄或改预算。

### 实图逐范围判断

| 范围 | 已见内容与配对实值 | 结论 |
|---|---|---|
| 两档1.3 target0–10 | 页名、副标题、显示精度标题/标签/关闭说明、能力标题/未连接行、使用说明、本地计时标题/正文、操作限制标题完整。字体随1.3按Android实际缩放，开关与标签不碰撞。 | 各11目标分别接受，不把同图中操作限制正文未完整显示当target10标题失败，也不以标题替代正文。 |
| 两档1.3 target11 | scrollY56，操作限制正文框[686,1404,2054,1465]，42.3px/400、Layout61px、一行ellipsis0。实图末尾“取作粉量”可读，未被任务栏覆盖。 | 关闭主态1.3第二帮助正文缺口。 |
| 两档1.3 target12 | scrollY160，协议支持标题框[686,1399,2054,1465]，45.45px/500、Layout66px。标题完整，层级/分隔与上段清楚。 | 独立标题接受，不提前签该帧下方未见正文。 |
| 两档1.3 target13 | scrollY295，协议正文框[686,1348,2054,1465]，42.3px/400、Layout117px、两行ellipsis0；“实验性只读、尚未实机验收、仅离线解析、不提供连接或控制”全文及最后标点清楚。 | 关闭两档第三帮助全文缺口；第一行末“离”与第二行“线解析”自然断行，不是丢字。 |
| light1.0 target0–11 | 全部12项在共用主布局图可读；36px/400正文、31.5px/400辅助、标题强调500，未见横向碰撞或裁字。 | 与dark1.0各自独立证据接受；不混主题。 |
| light1.0 target12/13 | scroll29/142，标题36px/500、Layout52px；协议31.5px/400、两行Layout95px，全文框[686,1370,2054,1465]。第二行“制。”清楚，卡片内边距保持。 | 关闭light1.0底部协议全文缺口；与1.3的断行位置不同属实际自然排版。 |
| 三档restore | 页头/精度关闭/能力及帮助上部回原，两个scroll精确0，布局/字号/状态未漂移。light1.3系统时钟改变，dark1.3顶部系统状态图标改变，不是App恢复失败。 | 原页面恢复接受，不要求系统栏像素与main相同。 |

G1四档688dp总宽/两侧24dp/卡内16dp不随滚动改变，rail与内容对齐、卡片/分隔层级稳定。G2实际Noto Sans SC冻结字体角色与已见字形接受，G3全部目标行框/全文及恢复接受；不会因公开API无法读私有fallback无限保留pending。G4浅色主字 `#FF101820`、辅助 `#FF657183`，深色主字 `#FFEDF3F2`、辅助 `#FFA9B9B8`，与白/暗卡、页面背景和关闭开关/青导航相符，全部已看目标可读。未要求的pressed/focus/ripple不新增为本范围门槛。

### 精确PNG清单与每目标覆盖

以下路径相对 `settings-reach-selected/`；完整ZIP路径和每个独立geometry/proof SHA仍以索引每target为准。表中合并仅限**PNG字节相同**，各target几何均单独核过。

| 配置 | 目标范围 | 本地PNG路径 | SHA256 |
|---|---|---|---|
| tablet-user-landscape-light-font-1.0 | 0–11 | `png/a077cd437b75e0cdad00107cb2300a79fbb164105191d1706a4f1641105370e6.png` | `a077cd437b75e0cdad00107cb2300a79fbb164105191d1706a4f1641105370e6` |
| tablet-user-landscape-light-font-1.0 | 12 | `png/63a905afff56f95d2cbed751e9f91c41cf6a31dcb70894c912943abdfa5eb854.png` | `63a905afff56f95d2cbed751e9f91c41cf6a31dcb70894c912943abdfa5eb854` |
| tablet-user-landscape-light-font-1.0 | 13 | `png/a04d219986a83a476754c01231e354c9a7bc324b1eaa9553cb822b190e12afde.png` | `a04d219986a83a476754c01231e354c9a7bc324b1eaa9553cb822b190e12afde` |
| tablet-user-landscape-light-font-1.0 | restored | `tablet-user-landscape-light-font-1.0/restored/window.png` | `a077cd437b75e0cdad00107cb2300a79fbb164105191d1706a4f1641105370e6` |
| tablet-user-landscape-light-font-1.3 | 0–10 | `png/ebf4c81cbaa79306f56ef73b0ff9b003335539ba1b2f3743f2a39a8936e58003.png` | `ebf4c81cbaa79306f56ef73b0ff9b003335539ba1b2f3743f2a39a8936e58003` |
| tablet-user-landscape-light-font-1.3 | 11 | `png/519c717bbb45906068bf7f788e612eefcd5c549cc92345ebb86c1c398b4c9881.png` | `519c717bbb45906068bf7f788e612eefcd5c549cc92345ebb86c1c398b4c9881` |
| tablet-user-landscape-light-font-1.3 | 12 | `png/25aaaf285055ae475938dfe9d119327caa559163d1db7e98fd776aba341bc063.png` | `25aaaf285055ae475938dfe9d119327caa559163d1db7e98fd776aba341bc063` |
| tablet-user-landscape-light-font-1.3 | 13 | `png/e594a99b2f89d444646cd3ec572374fe87868d74d29c1292e790eccd3599dc31.png` | `e594a99b2f89d444646cd3ec572374fe87868d74d29c1292e790eccd3599dc31` |
| tablet-user-landscape-light-font-1.3 | restored | `tablet-user-landscape-light-font-1.3/restored/window.png` | `f489dddd5050d5435567d497c22709514c75e5d3fc139a4b4e6d535ccc3954be` |
| tablet-user-landscape-dark-font-1.3 | 0–10 | `png/11737eafd4a1f503b806d58a41a8ff17e590729749b21ce2aebdc03e8552e366.png` | `11737eafd4a1f503b806d58a41a8ff17e590729749b21ce2aebdc03e8552e366` |
| tablet-user-landscape-dark-font-1.3 | 11 | `png/1d5407b3c65ff102456a61774fe0a2661d5d582fbdbf013f8d9845ac83581e28.png` | `1d5407b3c65ff102456a61774fe0a2661d5d582fbdbf013f8d9845ac83581e28` |
| tablet-user-landscape-dark-font-1.3 | 12 | `png/3e8dcbbfed9db9d204e263f635c89d46f537c977858213e2478cfd3fcc557d0d.png` | `3e8dcbbfed9db9d204e263f635c89d46f537c977858213e2478cfd3fcc557d0d` |
| tablet-user-landscape-dark-font-1.3 | 13 | `png/f1bde9fd1210d5cbacd5dda4b26ea1ceaee796f129d38ea342be3b8520e3b19f.png` | `f1bde9fd1210d5cbacd5dda4b26ea1ceaee796f129d38ea342be3b8520e3b19f` |
| tablet-user-landscape-dark-font-1.3 | restored | `tablet-user-landscape-dark-font-1.3/restored/window.png` | `3de6159658232e383ba38ef90db39d952076e078fd06db3bcef1432ed616acb2` |

## 更新后的未完范围

上述主态56目标＋4恢复已关闭。仍不包含本来源的全部特殊态/开启后重建/所有说明特殊fixture目标、电子秤主页及其它Settings页面；旧4076已限定接受的特殊图仍沿其来源保留，不自动转签588。Machine诊断和font_scale清理问题不抹去本页已完成视觉结论，也不能因本页通过宣布整run成功。

本阶段仅追加本文；未改源码、设计合同或采证门槛，未重下/全解原ZIP，未构建/提交/推送。
