# M7 新原件短窗详情有限视觉验收

## 结论

已实际查看新PR四配置共20张原PNG：plot滚达、短窗chart恢复、正常窗口恢复，以及legend首项和末个时间刻度各一张。**本轮可有限接受四档连续96dp plot滚达、已见legend/时间轴端点及恢复图的可读表现；不签M7整页、全80项轴字或整个Legacy视觉通过。** 正常窗口恢复图也不是所有内容同时完整可见的证明。

## 来源与清单

仅使用 `build/task-first-evidence/ci-8b52379-legacy-pr/`：run39893290655，head `8b5237989eaae99181ff137b8873896eb96bda7c`，实际merge `44d34c52a3f3b18537f59cb92607bcc7c6252d09`。ZIP SHA256 `bbbf0013a3872dcc75eef9d586743fbc24c7ca3a6fbf575e9b855f03fccb331d`，完整性沿用README的2597项CRC记录，未重做全包展开。

逐图身份、路径与SHA见 [acceptance-index](../../build/task-first-evidence/ci-8b52379-legacy-pr/acceptance-index.json)。实际查看严格为每个light/dark×1.0/1.3的以下项目（20张，不是264选件全审）：

| 索引位置 | 原件路径末段 | 数量 |
|---|---|---:|
| captures.plot | detail/special-states/short-window/chart-plot-reachable/window.png | 4 |
| captures.chartRestored | detail/special-states/short-window/chart-restored/window.png | 4 |
| captures.normalRestored | detail/special-states/states/short-window-detail-restored/window.png | 4 |
| canvasText首项，index0/role legend | chart-text-0-reachable/window.png | 4 |
| canvasText末项，1.0 index20、1.3 index18，role time-tick | chart-text-20-reachable或chart-text-18-reachable/window.png | 4 |

上述20图和配对geometry的本地SHA已独立重算匹配索引。四plot实际同帧shortDetail值独立读取：density2.25、viewport216px、chartVisibleRect `[252,863,2488,1079]`、visiblePlotHeightDp=96。四短窗恢复的scroll列表与短窗初态原JSON精确相等；初态PNG本轮未另看，不能说已逐图比较初态/恢复像素。完整proof两端target/5秒四零差及其它80项wholeGlyph沿用索引核验记录，本轮没有再次全包计算协议。

## 对照与G1–G4

依据 [短窗设计合同](../plans/2026-10-23-legacy-short-detail-viewport.md)、[内容坐标修正](../plans/2026-10-23-legacy-chart-content-rectangle.md)、[旧版双轴辨识设计](../superpowers/plans/2026-10-16-legacy-chart-axes.md)、[固定字体合同](../superpowers/plans/2026-10-16-fixed-chinese-typography.md)。96dp是连续屏内plot交集，合同并未要求96dp viewport同时容纳全部legend、纵轴和时间轴。

| 项 | 实际看到的结果 | 限定判断 |
|---|---|---|
| G1 结构/间距 | 短窗仍是固定页面标题+下方可滚动正文；历史采样卡边界与标题清楚，内外滚动条可辨。恢复短窗回到长名称，恢复正常窗口后名称、元信息、采样及数据来源按序排列，无突然丢失卡片。 | 已见布局没有遮叠。黑色大区是缩短真实窗口后的窗口外区域，不把它当产品留白。没有逐项量化全页所有设计间距，完整说明/固定动作滚达未签。 |
| G2 字体 | 中文标题/长名称清晰、字重层级一致；legend中文、bar/ml/s及数字没有缺字或异常粗细。配套Canvas证据为weight400、fakeBold=false、TaskTypography.face(context,400)，manifest字体SHA均为a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da。 | 已见文字有限接受。字体证据自己声明candidate_asset_association_not_platform_glyph_identity；不把对象/资源关联冒称逐字形平台认证。 |
| G3 图表/字号/行框 | 四plot真实图可见连续曲线，压力蓝实线与机器水流青虚线分开；大字号未缩字掩盖预算。legend滚达图中两图例、bar/ml/s及顶部刻度可读；末项滚达图中0.0/7.5/15.0/22.5/30.0 s整行清楚、最右30.0 s不出界。配套Canvas legend实际31.5px（1.0）/42.3px（1.3）。 | 四档96dp滚达缺口在此新证据中有限关闭。plot帧上下边缘确有非目标轴字裁切，不能拿plot帧签全部轴字；已看的首/末项各自完整。其余72个逐项滚达PNG未审，不签80项全过。 |
| G4 颜色/线型 | 浅色图中蓝压力和青色水流、深色图中浅蓝/浅青可分辨；虚线示例与轨迹一致，网格弱于数据线；只读琥珀标记与辅助文字形成层级。配套Paint色light压力#FF176BD1/水流#FF007E89，dark压力#FF8CB8FF/水流#FF66CAD3。 | 已见静态主题有限接受，未做全页屏幕合成色对比度认证。不是原生秤重/累计水量图，不能据此改变业务指标解释。 |

短窗1.3恢复图的只读徽标下缘在正文viewport边界处被裁切，plot/legend滚达图中的元信息也可能只余一部分；它们不是本次请求目标。允许滚动的合同下，这些帧不能证明徽标/元信息全文可见，也不能仅凭此判永久不可达。完整heading/说明/动作滚达仍需对应实际原图。本轮不新增产品修改结论。

## 未审项目与后续边界

- 其余72个Canvas独立目标原PNG未逐张审；索引记录机械wholeGlyph不替代人工视觉。
- shortInitial、shortReachableAction、normalOriginal的PNG未单独查看；未审全文说明Dialog、全部支持区及动作状态。短窗scroll数值恢复已核，不代表所有恢复前后像素视觉比较完成。
- Curve/History/Detail三种主态本轮未按完整主态清单审；normalRestored是长名称fixture的恢复图，不冒充普通Detail主态。
- 新run成功与本轮20图有限接受不等于Legacy全范围或24页完成；未用旧3a78失败图/5d08原图替代新证据，不涉及真机验收。

仅新增本文档，未改代码/其它规划、构建、触发CI、设备操作、提交或推送。
