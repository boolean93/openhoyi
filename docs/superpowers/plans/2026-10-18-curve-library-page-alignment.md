# 曲线库整页对齐批次

> 使用 superpowers:executing-plans 在当前工作树执行。

**Goal:** 按curve-library-v2修正左右占比及选中配方的字号层级，保留连续列表与真实阶段语义。

**Architecture:** TaskCollectionLayout增加默认保持0.4的可选browserFraction，仅CurveActivity使用0.34；CurvePreviewMetrics保留真实原值，单位缩为14.4sp，数值24sp；CurveActivity标题24sp、辅助14sp。其余页面不随此改占比。

**Tech Stack:** Kotlin原生布局及SpannableString。

来源develop66ebda9+ed01de0，feature/task-first-ui→develop draft PR#4；遵守docs/git-workflow.md。

- [x] TaskCollectionLayout参数browserFraction默认.4f，仅用于原浏览区宽度计算，仍有320dp/详情280dp最小值与24dp间隔。
- [x] CurveActivity实例传.34f；详情名称改TITLE_SP，分类/可用性改SUPPORTING_SP；不改排序/筛选/选中ID/恢复。
- [x] CurvePreviewMetrics只对温度°C与液重g的单位使用RelativeSizeSpan(.6f)，24sp数值/700及完整contentDescription不变；非数值状态不缩小。
- [ ] 检查同规格真实图与typography、Mock/instrumentation构建、branch policy/diff whitespace；取得新图后再判断此批是否通过。暂不为微小改动单独push，归入页面批次。

仍待本页后续收口：新建/编辑/分享入口与认可稿的位置、阶段图标题/分隔、阶段颜色与编辑器共享语义；对照图是四阶段，当前截图两阶段不能假称一一匹配。不同曲线的数据、只读/无压力/无数据等状态也要验证。不改设备/库存/schema/签名/版本，不安装/合并/发版。整页批次普通push，revert显示代码回退，不清历史。

## 同页第二组调整

- [x] 新建按钮放到页头右侧，沿用原disabled状态及新建行为；管理菜单保持原入口兼容。
- [x] 详情工具栏提供编辑/分享直接入口，转发原稳定按钮的行为与能力门禁；详情可用宽度达到600dp×字号比例时显示，窄窗/大字仍可通过原菜单访问，菜单不靠直显按钮visibility判定能力。
- [x] 三项参数绘制上下1dp分隔线；压力图前增加20sp阶段压力标题，八语言资源完整；标题纳入真实高度预算，短高回退时隐藏标题而不删完整参数/阶段说明。
- [ ] 完成此批构建，再统一提交/推送。图表阶段配色保持现有共享语义，暂不冒称整页最终验收。

整批本地验证：Mock/instrumentation BUILD SUCCESSFUL in 48s、exit0，/tmp/task-curve-library-page-final-build.log；八语言XML解析、branch policy、diff whitespace通过。包含前一轮占比/字号调整，此次统一提交推送。真实新布局、入口跳转与同状态视觉仍待CI，不标整页完成。
