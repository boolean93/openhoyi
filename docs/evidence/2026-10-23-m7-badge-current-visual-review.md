# M7 新短窗徽标有限视觉验收

## 结论与来源

已实际打开四配置badge-reachable/restored共8个PNG路径。**两主题1.3“旧版只读”全文滚达与原scroll恢复的旧缺口关闭；不签短窗初屏徽标完整，也不签整M7。** 1.0原本完整，本轮不要求制造滚动。

原件仅push run39911032377 / artifact12483861886，source=head `588855c96fdceec79bf87f0fd5950f33fa6aff1c`，不是同轮PR merge原件。ZIP `build/task-first-evidence/ci-588855c/legacy-artifact.zip`，164176734B，SHA256 `32f87caae5f5ac3ff43b08ac693dd4dbd60a8e8f1f27158f7fb2c7ff4734307e`；root已核2645项CRC，本次未重做全包扫描，只定向提24个PNG/geometry/proof并读原manifest/初态/字体JSON。24件SHA独立核到各page manifest，8帧PNG SHA与proof一致、accepted geometryBefore.target==geometry==geometryAfter.target。

依据[徽标合同](../plans/2026-10-23-m7-short-badge-reachability.md)及[M7冻结设计](../plans/2026-10-19-m7-m8-remaining-visual-migration.md)。旧8b图只作缺口背景，没有重复看旧图或移植旧source接受结论。

## G1–G4与恢复

| 配置 | 实际reach/restore | 限定判断 |
|---|---|---|
| light/dark1.3 | 同一630px短窗，root屏幕y485，reach徽标rect==visibleRect=[1022,1000,1244,1079]；外层content-scroll实际y21，其余四个ScrollView均0。四字及圆角底边完整，位于日期/时长右侧，与长名称不重叠，浅色棕字淡底、深色浅金字暗棕底可读。restore外层y0，rect=[1022,1021,1244,1100]、visible底1079，恢复原21px裁切。 | **滚达全文接受，精确原位恢复接受**；恢复图再次裁切不是恢复失败，也绝不把恢复图签成完整徽标。 |
| light/dark1.0 | reach与restore全部五scroll均0；徽标rect==visibleRect=[823,1014,1001,1078]。四字、背景和圆角底边完整，和旁边metadata有间距，无覆盖。 | 原初态已完整，不为制造“滚动”而移动；对应两帧接受本目标可见性与原位置。 |

G1：保原短窗与长名fixture、真实徽标对象，不靠恢复正常1391px窗口获得全文。reach时1.3只移动内容21px，上方长名依然自然四行，徽标不是触控按钮，不套48dp；本轮未检查屏外chart/support。G3：1.3同帧单行Layout高61px==content61px、行right168px==contentWidth168px，ellipsis0，完整文本为“旧版只读”；画面四字和底边确实可见，非仅boolean PASS。全部八帧同一配置的badge身份与原初态相同；独立比较restore的完整scroll数组（含实例identity、x/y）及headingReadonly对象与原initial相等，四份badgeReachability.completed均true。四组未换窗口尺寸，实际appSize=[2560,630]。

G2/G4证据边界：本次短窗returned geometry保存文字行框/映射，却**没有同帧actualTypography的textSize/weight/letterSpacing/颜色字段**，徽标两帧目录也没有独立typography文件。实际字形清楚，视觉未见缩字/假粗/缺字。另读同source同profile `detail/special-states/states/long-name-source/LegacyHistoryDetailActivity-typography.json`，实际legacy/readonly为1.0=31.5px、1.3=42.3px、weight400、letterSpacing0，浅文字#FF795119、深#FFEACB87；page字体资产SHA为`a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。这些是**另帧实际字体旁证**而非资源常量，也不能冒充本次八帧同一时刻的逐glyph身份证明。故本次关闭全文可达/恢复与已见颜色可读性，完整同帧G2元数据闭环仍保留，不用CI绿或资产SHA抹平。

八帧认证耗时：light1.0 908/904ms，dark1.0 918/907ms，light1.3 1686/1662ms，dark1.3 1666/1658ms；原protocol2/5秒保持，未重跑像素算法。UI可见区域外黑边是声明的真实短窗口环境，不用黑边代替完整页面验收。

## 实际查看路径与SHA

本次定向选件索引：`build/task-first-evidence/ci-588855c/legacy-badge-selected/index.json`。以下为实际打开的PNG；配对geometry/proof在同目录。

| 原件路径 | SHA256 |
|---|---|
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-dark-font-1.0/detail/special-states/short-window/badge-reachable/window.png` | `c148b75c9237a6f100e2d1ed073491e4e22c5293ff46021e66766f848f139fc3` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-dark-font-1.0/detail/special-states/short-window/badge-restored/window.png` | `c148b75c9237a6f100e2d1ed073491e4e22c5293ff46021e66766f848f139fc3` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-dark-font-1.3/detail/special-states/short-window/badge-reachable/window.png` | `07705bb1a9f48d80bb038496ddb143cf74c3f1bf5d7d9d2b03f5ec7537b4b1ad` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-dark-font-1.3/detail/special-states/short-window/badge-restored/window.png` | `a3c341410866f8a62159042470f18c876c19afa355119fc20b428010d16bc842` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-light-font-1.0/detail/special-states/short-window/badge-reachable/window.png` | `b4935672f1928c9e657a41f3aad99f7d9dd7475898987a36414e1e4733a60c3a` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-light-font-1.0/detail/special-states/short-window/badge-restored/window.png` | `b4935672f1928c9e657a41f3aad99f7d9dd7475898987a36414e1e4733a60c3a` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-light-font-1.3/detail/special-states/short-window/badge-reachable/window.png` | `4e37850eb89295adadd4c22c667d4bc20e3dc80ca23b18de77426e704327086a` |
| `build/task-first-evidence/ci-588855c/legacy-badge-selected/tablet-user-landscape-light-font-1.3/detail/special-states/short-window/badge-restored/window.png` | `e4ad08b98db287a221d125801038708bf7176ac963d55f92abe8e29962fc835a` |

## 未签范围

未重审本source其余short chart/80轴字/固定动作/说明/Dialog、正常12主图或全部特殊态；新CI success不是整M7视觉通过。旧主态Toast及History时间轴取证缺口由独立计划处理，本徽标图不替代。没有改代码、构建、设备操作或提交推送。仅新增本审查文档。
