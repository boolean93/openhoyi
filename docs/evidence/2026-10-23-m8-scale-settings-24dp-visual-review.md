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
