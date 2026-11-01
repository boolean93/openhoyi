# M8 ScaleSettings 四配置有限视觉复核

## 结论

已实际查看四配置主态及各一张底部协议说明滚达图，共8张原PNG。可有限接受已见区域的文字层级、实际字体表现、大字换行及深浅色可读性；**不能关闭M8整页：G1仍有冻结24dp与实测16dp的边距差异，完整滚达/恢复及特殊态尚未逐图审完。** 不是机械PASS转视觉PASS，不外推新提交、其它页面、语言或硬件。

## 来源与实际查看范围

原run `39884263223`，artifact `12472329369`；被测merge `0109982664efc7b3951094c35a6f0eaed586fbfb`，head `5d08fd658ad7300d0478a2b5e117e88eb3c99f11`。原ZIP `build/task-first-evidence/ci-5d08fd6-settings-pr/artifact.zip`，SHA256 `390505712dbb5e7fac16e0ee232435090c8ceb0bfa65f3cdf6badf615c6dc727`；沿用preservation的4270项CRC记录，不重复全包展开。

精确索引：[acceptance-index](../../build/task-first-evidence/ci-5d08fd6-settings-pr/scale-settings-acceptance-index.json)。每个 `tablet-user-landscape-{light,dark}-font-{1.0,1.3}`：

- 主态：对应 `scale-settings/window.png`，读取review-input中原PNG。
- 底部：同格 `scale-settings/main-reachability/target-13/window.png`，semanticId `product/settings/scale/help/2/body`。按原ZIP选择提取并查看，不是设计合成图。
- 四主态PNG实际SHA与索引清单一致；四底部PNG重新核原ZIP字节与临时审阅文件一致。八组geometry均核为同一次accepted attempt的before.target==geometry==after.target。本次没有重跑完整protocol像素计算。

底部PNG SHA（light1.0 / light1.3 / dark1.0 / dark1.3）：`c88d80abe0981ac7239fd40c98e22a285edb114acddeaa91331b691858c9808c` / `f93735e01095d4bf22e6f172e4b325cd5434c674032ad0ae1fd1fb56a390aa08` / `d1cc7e2c2996a41df7e4b6cf7cb8f58fd0243c74027dd5ba1fed393326731720` / `4ae73ad21de46c23b4e3c91410ac20de921f619b32407578ef0f3b1d21fe2176`。

## 对照依据

实际查看 [scale-settings-v1](../research/task-first-ui-concepts/scale-settings-v1.png)，读取 [分组设计](../superpowers/plans/2026-10-18-scale-settings-groups.md)、[整页滚动](../superpowers/plans/2026-10-18-scale-settings-scroll.md)、[固定中文字体](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)。概念图用于布局层级，不将其像素尺寸或生成字体当平台字体真值。

## G1–G4

| 项 | 实际观察与测量 | 限定结论 |
|---|---|---|
| G1 间距与结构 | 三组顺序清楚；精度标签/解释分行，能力行与说明卡对齐，无横向挤压。density2.25，body宽1548px=688dp。body padding36px=16dp，说明间距18px=8dp；精度操作行126px=56dp。冻结分组文字却要求688dp含左右24dp。 | 分组与滚动策略可接受；**左右边距合同差异保留，G1不签完全一致**。不能因为屏幕观感整齐自动改写24dp要求。 |
| G2 实际字体 | 实图中文与拉丁数字一致、无明显缺字/假粗；实际target报告标题/组标题weight500，正文/辅助400，fakeBold=false、letterSpacingEm=0。字体证据标Noto Sans SC 2.004-H2，清单font SHA为a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da。 | 已见文字与冻结固定字重层级一致；不是凭Typeface对象hash识别字体，更不宣称等同概念图生成字体。 |
| G3 字号、行框与内容 | 1.0标题54px、组标题45px、精度正文36px、辅助31.5px；1.3依次59.399998/53.100002/45.45/42.3px，均为实际target值，不按1.3简单乘算。四主态完整可见文本无重叠、缺笔或异常挤行。底部协议说明1.0一行、1.3自然两行，四张滚达图均能读完。 | 已见区域有限接受。首屏底部裁切是整页滚动，不能把未入视口内容判为完整；target13的真实完整说明弥补该文本的可读性缺口，不代表所有目标已验。无图表，本项图表检查不适用。 |
| G4 颜色 | 浅色深主字、灰辅助字、白卡/浅底与青色选中导航可分辨；深色浅主字、灰绿辅助字与卡底边线层次稳定，开关关闭状态与说明一致。实际文本色light主#FF101820/辅#FF657183，dark主#FFEDF3F2/辅#FFA9B9B8。 | 已见静态主题可读性有限接受；颜色字段是TextView当前Paint而非屏幕合成采样，未声称量化全页对比度或逐像素匹配概念图。 |

底部滚达时页标题/组标题会移出顶部，是整页scroll的真实结果，不是把滚动前截图裁剪拼接。系统状态栏/底部系统栏不当作产品卡片区域。未连接状态明确写无实时能力，说明明确本地计时不用于自动停水、协议只读/未实机验收；本次未把这些文字当成硬件能力证明。

## 未关闭项

1. G1的24dp设计文字与16dp原件差异需明确设计决定或修正后原图，当前不擅改合同、不开发。
2. 每格14主态目标及14说明态目标：这里只看其中主态target13，其他目标与全部恢复PNG没有逐图视觉签字。索引记exactScrollRestored与机械通过，仅作证据库存，不代替审图。
3. precision-recreated、real-scale-no-data及其它特殊态没有在本轮审图；ready-nonmock-hardware-capabilities仍待真机，不能以Mock补签。
4. 同run App/Machine八格失败，整个run不是通过。本结论不关联新8b52379 Legacy成功结果或其尚未收取原图。

本次只新增本文档，未改源码、触发CI、构建、设备操作或提交推送。
