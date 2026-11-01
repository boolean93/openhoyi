# M8 ScaleSettings 四档主态有限视觉验收

## 结论

本轮实际打开四张主态原 PNG，优先补看此前缺失的 dark1.0。**四档主态的688dp总宽、左右24dp布局差异已闭环；可见标题、精度、能力及说明区域未发现新增重叠、缺字或异常换行。** dark1.0不再列为主态缺图，但其滚达/恢复仍未在本轮审看。

不签全部帮助全文、完整14+14目标、全部特殊态或整个M8/Settings run通过。第三条帮助正文四档均不完整可见；两档1.3的第二条正文也不完整。主态自然滚动不是全文同屏证明。

## 来源与独立核验

- run `39911032413`，artifact `12483598594`；source=head `588855c96fdceec79bf87f0fd5950f33fa6aff1c`，sourceDirty=false。
- 原 ZIP `build/task-first-evidence/ci-588855c/settings-artifact.zip`，470061210B，SHA256 `1af608e4384ab4a27c3e3faed7afeaab8f3fcb82b1ef052893e34543f29b64e8`。沿用root保全的5999项CRC通过记录，没有重新全展开。
- 本轮索引 `build/task-first-evidence/ci-588855c/settings-main-selected/index.json`，独立SHA `b7a16d1ba278d38bbf45b51827758916713a12ab67aebabc98755cff6567a263`。独立重算全部20选件SHA吻合；四组geometry均等于accepted attempt的before.target及after.target，actual-text-typography.nodes等于geometry.nodes，PNG SHA等于proof.imageSha256。
- 四帧均protocol2、acceptedAttempt=2、原5000ms预算，耗时按light1.0/light1.3/dark1.0/dark1.3为3836/3783/3914/3917ms；四项差值均0。仅作为同帧配对，不以机械PASS代替以下实图判断。

对照[冻结分组](../superpowers/plans/2026-10-18-scale-settings-groups.md)、[固定中文字体](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)、[24dp局部修正合同](../plans/2026-10-23-scale-settings-body-padding.md)。旧4076审图结论保留在[旧有限审查](2026-10-23-m8-scale-settings-24dp-visual-review.md)，本次不将其滚达图替代588主态，也不修改旧审查范围。

## G1–G4 实际判断

| 项目 | 四图观察与配对实值 | 限定结论 |
|---|---|---|
| G1 间距/布局 | 四档body x596..2144，共1548px/2.25=688dp；组标题和卡片x650..2090，两边54px=24dp，内容640dp。卡内x686..2054为另加16dp，不能误报body40dp。组标题、卡外沿和分隔线齐整，页标题相对返回箭头的层级稳定。 | 包括新增dark1.0在内，关闭该四主态24dp差异；不外推窄窗。 |
| G2 实际字体 | 已见中文、App、0.1g/0.01g字形清晰，未见缺字或假粗风格突变。同帧actualTypography记录precision/帮助正文weight400、帮助标题500、fakeBold=false、letterSpacingEm=0。字体源关联SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。 | 接受已见区域字体层级与实际getter配对。资产SHA与Typeface对象不是每字fallback证明，glyphFontIdentity仍UNKNOWN。 |
| G3 字号/行框 | 精度标签1.0为36px、Layout52px；1.3为45.45px、Layout66px。帮助正文1.0为31.5px，1行Layout46px/2行95px；1.3为42.3px，1行61px/2行117px；三段自然行数均1/1/2、ellipsis0。页名/副标题/精度主副行/未连接能力行完整且无碰撞。开关与文字留有充分空间，控件框126px=56dp。 | 可见文本接受；屏外Layout存在或ellipsis0不等于全文可见。没有图表，本次不含触发动作或恢复操作。 |
| G4 颜色/背景 | 浅色白卡与浅底、深主字/灰辅助分层；深色暗卡与更暗页面、浅主字/灰绿辅助、细边线清晰。关闭开关圆钮在左，青色返回及电子秤导航选中态一致。dark1.0正文及操作限制可读，底部没有Toast覆盖。 | 只签四主态已见区域可读性与状态表达；不宣称所有开关/禁用/硬件状态或全页量化对比度。系统任务栏/状态栏不属于该Activity颜色认证范围。 |

原图均2560×1600；工具显示为1996×1248缩放预览。上表精确像素来自原同帧geometry/getter，不以预览比例量尺寸。App screen rect为[0,74,2560,1465]，底部系统任务栏不作为页面内容。

## 逐图可见边界

- **light1.0、dark1.0：** 本地计时、操作限制的标题和正文均完整；操作限制正文框[686,1353,2054,1402]在App内。协议支持标题在底缘仅露出上部，第三正文框[686,1512,2054,1607]在App外，不能签全文。
- **light1.3、dark1.3：** 本地计时正文完整，框[686,1277,2054,1338]。操作限制标题可见，但正文[686,1460,2054,1521]仅与App下缘交叠5px，实图不能阅读；第三正文[686,1643,2054,1760]在屏外。不得把metadata的1/1/2行数写成三段都已看清。
- 未连接能力文案是fixture当前显示，不能作为真实设备能力/写命令验收。没有根据未滚达主态裁切要求缩字号、隐藏状态或改产品。

## 本次四张实际查看清单

路径相对 `build/task-first-evidence/ci-588855c/settings-main-selected/`。每项同目录的geometry、actual-text-typography、ScaleSettingsActivity-typography、window-frame-sync四JSON均已配对。

| 配置 | PNG路径 | SHA256 |
|---|---|---|
| tablet-user-landscape-light-font-1.0 | `tablet-user-landscape-light-font-1.0/window.png` | `d772b69717aad3b17e99802b5192fb3b4b23cf523275daf1d46195d37eade73b` |
| tablet-user-landscape-light-font-1.3 | `tablet-user-landscape-light-font-1.3/window.png` | `664d9dda55e04915d4dd57da8f51bdda1a3b7aebc4d20a513d0c879498d40d95` |
| tablet-user-landscape-dark-font-1.0 | `tablet-user-landscape-dark-font-1.0/window.png` | `b649e3cd2115a3b938bdda40573d980c399be877cd0a1c38a9019dd3697e955b` |
| tablet-user-landscape-dark-font-1.3 | `tablet-user-landscape-dark-font-1.3/window.png` | `11737eafd4a1f503b806d58a41a8ff17e590729749b21ce2aebdc03e8552e366` |

## 尚未完成

本轮只有4张主态，没有打开索引中的main-restored或target滚达PNG；所有尚未审的14+14目标与特殊态、精确恢复视觉仍保留。若补全文，应从同588原ZIP定向取实际目标与恢复帧，不借旧4076或其它业务态替代。

Settings本run的Machine capture超时独立保留，不能因为本页四图可用而签整run通过。本文未改产品、既有冻结文档、构建、提交或触发CI；仅新增本审查文档。
