# 曲线库阶段结构设计实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans。用户已批准设计与执行；Sol实现，Astra规划/独立验收，root统一构建/提交，不重复确认执行方式。

**Goal:** 用可快速比较的小轮廓与可读阶段卡、水量分配替代低信息密度大图。

**Architecture:** 复用上一批只读typed阶段单位模型，新增独立详情结构组件；列表用整库共享量程。显示层不改变配方、持久化schema、执行报文或任何门禁。

**Tech Stack:** Kotlin原生View/Canvas、现有CurveLibraryItem/catalog、多语言资源。

## 工作流与边界

- 依据仓库AGENTS、`docs/git-workflow.md`与`docs/plans/task-template.md`。原来源develop，同一已授权任务继续`feature/task-first-ui`，基线`a945f08`，PR4目标develop；开始检查工作树为空。共享树不得覆盖他人修改。
- 参考已实际查看的批准图：`docs/ui/curve-library/2026-10-29-approved-stage-sparkline.png`（root归档）。图片定构图，不作为数据来源；图中其它配方示例不得硬编码成真实参数。
- 不发版、不合并，不发送真实设备命令。root按前序授权做Mock截图/安装，核同签名、单调versionCode、APK hash和现有数据；不安装辅助测试APK。Astra不构建/提交/改源码。
- 回退为追加修复或明确revert提交，不重写共享历史、不卸载清数据；未知写入记录与原设备门禁保留。

## 设计合同

### 左侧密集列表

- 正常字号下每行最小64dp，小图96×40dp，名称+简短“压力/流量/混合/单位未确定”摘要；标题不被小图挤碎。长名/系统大字允许自然增高，不能靠缩到不可读字体或截重要名字硬保64dp。
- 小图不画坐标、网格、数值、面积；压力蓝实线、流量青线。阶段等宽，同量纲以细阶梯表达目标步骤，跨量纲留可见gap，不连接成物理连续轨迹。共用小图例说明蓝压力/青流量及单位。
- 全库同量纲共享尺度：压力至少0–12bar，流量至少0–6mL/s，真实有效最大值超出则整库统一扩展到覆盖值。搜索、过滤、选中不重新按每项或可见子集归一化。未知单位不画带物理含义的假轮廓。
- 本台正常视口以至少6–7条可比较行为目标，保搜索/分类/排序/滚动位置与选中状态。不是对大字号或短屏强制同数量。

### 右侧配方结构

- 上部紧凑摘要保温度/阶段数，突出真实总水量；目标重量移至结束条件但不删除。
- 2阶段并排卡，3–4阶段用2×2，1阶段不造第二段。每卡编号、控制类型、主目标数值及单位、阶段水量；压力蓝/流量青，用文字补足颜色。主目标约32–40sp，正文14–16sp；不将4段塞成4窄列。
- 水量分配带按各段真实水量比例，标签在带外可读，极小段不能为了标签强行改比例；阶段水量为0保数值0，不伪造宽度。无有效水量或总和0时说明不可绘分配，不除0。颜色复用控制类型、编号识别阶段。
- 表述“阶段水量合计”与“总水量上限”独立：两者确实相等才写加法等式。累计水量不是时间，也不是实时进度；不能以水量/目标流速推算时长。
- 闷蒸设置、首段时长参数、结束条件、目标重量及原建议全文均保留；0秒是参数值，不能宣称阶段耗时0秒。闷蒸参数不擅自放到某两个阶段之间，固件时序未经证实。
- 可滚详情承载3–4阶段及长建议，原完整参数/分享/编辑/使用入口保持可达、≥48dp；固定主要动作不被卡片挤出。继续既有平板布局方向，不影响其他页面。

## 数据与验收样例

沿`2026-10-29-curve-control-preview.md`逐段单位合同：factory flow首段bar后续mL/s；controlProfile首段仅总体flow且首段flag真才mL/s；Custom FLOW_RAW缺模式证据保持未知。`pressureLogic`不提供新的数值目标，压力限制不能当阶段目标。

阶段水量：factory `segmentFlowMl`原mL；profile `firstFlowTenths/secondFlowTenths/...`除10；custom `waterTenthsMl`除10。温度、总上限、闷蒸、首段时间和重量分别从真实来源读取；custom不存在的字段显示未提供，不补0。

**Turbo003精确回归：**92°C，阶段1压力2.0bar/15mL，阶段2流量3.0mL/s/135mL；总150mL，分配10%/90%；闷蒸10秒，首段时长参数0秒；目标重量0=不使用，按机器水量结束。结束描述复用既有profile语义；有重量目标的其它配方不能仍显示只按150mL结束。

## 文件分工与步骤

Sol拥有生产与相关测试/资源；Astra仅此计划。root拥有构建、截图脚本和批准图归档。

- [x] **只读模型与测试：**扩展`CurveStageParameters.kt`或新增`CurveRecipeStructure.kt`，提供阶段目标/水量、明确可空的总上限/时间/重量字段及整库共享range；在对应unit test固定Turbo15/135、profile tenths换算、custom缺字段、零水量/总和0、合计不等上限、1/4段及搜索不改变量程。
- [x] **列表轮廓：**在`CurveStageView.kt`或独立`CurveStageSparkline.kt`实现96×40无轴轮廓；`CurveFragment.kt`列表使用共享range，调正常row64；保旧压力renderer对其它调用兼容。测试同真实目标在不同配方中相同高度、跨单位无连接、0与最大不裁切。
- [x] **详情结构：**新增`CurveRecipeStructureView.kt`（可按代码实际等价命名）承载阶段卡/水量分配/条件/建议，替换CurveFragment旧大图区域；不继续扩大Fragment巨型绘图代码。`CurvePreviewMetrics.kt`若被复用，勿影响其它页面。真实绑定包括选中切换，不能复用上一个配方的缺失值。
- [x] **资源与原生断言（实现/编译完成，实跑未验）：**catalog及生成资源同步；调整`CurveLibraryViewportChecks.kt`/`CurveVisualMigrationChecks.kt`为实际阶段卡与分配带合同，保原业务/磁盘/滚动/动作门禁，不只删旧chart断言。断言Turbo两目标/水量、未知无假单位、4段滚达完整与固定使用入口；绘图自报evidence不能代替实际原图。
- [x] **曲线库IME稳定性：**仅调整曲线库自身布局决策，已选中条目后点击搜索、IME缩小可用高度时，不得切成隐藏搜索的单详情而导致失焦；保留当前查询文本、光标、选中项与列表滚动。不能改所有页面的宽高布局阈值或以强制隐藏键盘绕过。验证本台横屏有选中项时连续搜索/修改查询/关闭再打开IME，搜索保持可见焦点，双栏稳定、结果实际更新，详情和使用入口仍可达；键盘关闭恢复正常布局，原窄屏单栏返回合同不变。
- [x] **静审与软件验证：**Sol冻结后Astra审数据/视觉预算。root统一执行catalog校验/生成check、mobile单测、Mock/Debug/AndroidTest构建、lint；编译与原生实际运行明确分开，不构建中改源码。
- [x] **实际图验收（仅下述已见代表范围）：**root本台Mock截Turbo混合及原压力配方浅深代表图，实际查看列表轮廓比较密度、2阶段卡、水量带和结束信息；3–4阶段使用已有真实条目验证滚达，不为凑图写用户fixture。纯flow/unknown缺真实条目则保测试覆盖限制，不称实图通过。
- [ ] **交付：**记录有限通过范围/未验项，root检查仅任务文件、普通push当前分支并更新PR4；必需CI结果真实记录，不预称合并/硬件通过。

## 当前状态与未完成

实现及生产静审完成；v19/v20本台中文代表图已有限通过（范围见下），v20最终构建/单测/lint已由root确认通过，实际原生CI运行仍待验。本轮按root已告知用户的范围，包含曲线库专用IME稳定双栏/搜索焦点修复；原生全状态与硬件不因效果稿批准而自动通过。

root已留原v17 APK及私有数据快照于`build/curve-stage-design-2026-10-29`，作为后续安装保数据核对依据，不据此声称已执行回退。

## 首轮生产静审（实现中，非视觉验收）

共享量程读取整库而非搜索结果，列表同单位阶梯/跨单位gap、阶段水量来源与独立总上限已核。此前factory结束条件、首段时长、分配带外标签及使用次数缺口已补；原执行/编辑/分享权限保留，IME策略只由曲线库显式启用。

- [x] 已修确定单位错误：`CurvePreviewMetrics.waterSummary`为真且总上限null时，不得从非零目标重量分支得到g；custom缺上限必须显示无单位—，新增该条件回归。
- [x] 短高fallback从旧“左轴/右轴”图描述改为中性阶段结构摘要；新卡片界面不能声称存在坐标轴，保目标与水量及原完整参数可达。

2阶段卡自然高度、3–4阶段滚达及底部动作尚待原图/实际布局，IME首次打开/连续编辑/关闭恢复尚待实机。未把中间代码或编译当作验收通过。

生产收尾复审：总水量缺失分支独立返回无单位，短高摘要已改阶段结构并带各段水量；无新增静态阻塞，可在root最终构建后进入Mock实图。IME分支仅SDK>=30（项目min26），本台验证不外推26–29。卡内未知长单位、3–4段滚达、固定动作与列表密度仍待真实视觉，不以静审代替。

## v18 首次实际图审

Astra实际查看`build/curve-stage-design-2026-10-29/pressure-light.png`、`turbo-light.png`与`ime-search.png`：阶段卡和真实水量关系正确（压力配方35+17=52mL与上限108mL分开；Turbo15+135=150、10%/90%），原动作固定可见。正常态仅5完整行、第6行副标题裁切，尚未满足本台6–7行目标；确定收紧Curve页body上下padding16→8、header下margin16→8合24dp，不改共享header/字号/48dp动作。比例文字改一位小数，避免整数截断67+32=99；实际水量比例不动。

IME图搜索有焦点但列表0项可见，仍需修复。批准仅曲线页IME期间隐藏分类/排序/计数，为至少1完整、优先2结果留空间；只改visibility，不清查询/分类/排序/选中、不重建spinner。收键盘恢复原控制与状态；不改共享rail。右侧闷蒸/结束/建议允许滚达，仍需结尾实图验证。本轮尚未最终视觉通过。

## v19 非IME浅色有限验收

实际查看`build/curve-stage-design-2026-10-29/v19/pressure-light.png`、`turbo-light.png`、`turbo-details-light.png`、`ime-search.png`、`ime-restored.png`。正常态6行名称/模式完整，小轮廓可比较；压力分配67.3%/32.7%修正，Turbo目标/水量/10%与90%正确。滚达图中闷蒸10秒、按水量结束、不使用杯重、首段0秒设置（非实际耗时）、建议全文均可读，固定查看参数/使用按钮完整。此非IME中文浅色范围通过，不继续收紧正常布局。

IME仍0结果可见，隐藏次要控件尚未在真实运行生效，**本项未通过**；关闭键盘截图显示Turbo查询/4结果、分类排序计数恢复，选中项和详情滚动位置保留。根因由Sol继续定点修，不把静审策略正确当作实际修复成功。深色及3–4阶段本批仍待验。

## v19 补图与v20 IME静审

Astra实际查看v19目录`turbo-dark.png`、`pressure-dark.png`、`three-stage-light.png`、`three-stage-details-light.png`。Turbo深色两卡/分配带/列表清晰通过；三段浅色初态三卡完整，滚达16/40/14mL、22.9%/57.1%/20.0%、70mL合计和上限、首段5秒设置与条件全文，固定动作不遮挡。**`pressure-dark.png`实际内容是采集曲线2三段混合深色，不是纯压力**，仅记录三段深色初态通过；四段无实际现成项实图，保单测边界，不外推。

v20 IME修正静审无阻塞：TaskCollectionLayout测量时读取rootWindowInsets，仅状态变化post单次回调，合并读取最新值；CurveFragment仅切次要控件visibility。共享rail/协议/查询状态不改，实际键盘上结果可见仍待v20原图。

## v20 深色与IME有限验收

Astra实际查看v20目录`ime-search-dark.png`、`ime-restored-dark.png`、`pressure-dark.png`、`turbo-dark.png`：IME期间搜索有Turbo光标，两条结果名称/模式可见，排序/分类/计数隐藏生效；关闭后4结果、原控制及选中详情恢复。本台SDK>=30这一真实搜索状态视觉缺口闭合，不外推任意键盘/旧系统。

本次`pressure-dark.png`确为采集曲线1，9.0/6.5bar、35/17mL、67.3%/32.7%与独立108mL上限正确；Turbo深色两阶段及分配带清楚，正常6完整列表行保留。结合v19已看浅色与三阶段滚达，此批有限设计验收通过。root报告v20同签名安装及17数据文件hash保留；最终构建/lint仍收尾，暂不预记通过。

未验边界：四段、纯flow、未知单位原生图；原生CI实际运行与旧系统SDK26–29；真实设备控制。模型/编译证据不能替代这些范围。v20浅色新图待root提供，不把v19图冒称v20。

## 最终有限验收与冻结

Astra补看v20 `ime-search-light.png`、`ime-restored-light.png`、`turbo-light.png`、`pressure-light.png`：浅色搜索IME同样保焦点且显示两条结果，收键盘恢复4结果/原控件和选中详情；正常纯压力、Turbo两阶段及六完整列表行可读，与已验深色一致。结合v19三阶段滚达，当前中文本台代表范围通过。

root最终确认`build-v20.log` SUCCESS 2m17：350tests零failure/零error、1skip，Mock/Debug/AndroidTest编译及lint通过；两variant各11257 strings匹配，6roundtrip通过；同签名v20安装及17数据文件hash保持。原生测试APK编译不等于CI实跑，四阶段/纯flow/unknown原生状态、SDK26–29与硬件验收仍未覆盖。

已完成本轮实现、静审、有限实图及软件验收；未执行的原生矩阵/硬件保留边界。文档冻结，交root普通提交/push PR4，不预记合并或发布。

root补充最终localization Python 22 tests及git policy 8 tests通过。剩余未勾交付步骤是root尚待执行提交/push，不是Sol待实现。


## v21 详情内边距小修与有限验收

用户指出右侧详情贴边。本轮继续 `feature/task-first-ui → develop` / PR4，不发版；只在 `CurveFragment.detailScroll` 的内容 wrapper 增加四边 16dp padding。标题、阶段卡、水量标签与分配带、条件和建议对齐同一内缘；卡内字号/间距、列表、顶部摘要、固定动作及所有业务门禁不变。回退仅撤销该显示层一行，不改用户数据或协议。

- [x] 静审：生产 diff 仅 wrapper 的 `setPadding(dp(16), dp(16), dp(16), dp(16))`，不叠加卡内 padding，不改变滚动或动作回调。
- [x] 实图：Astra实际查看 `build/curve-padding-2026-10-29/turbo-light.png`、`turbo-bottom-light.png`、`turbo-dark.png`。浅深首屏标题、两阶段卡与分配带均有一致左右留白，顶部不再贴圆角；浅色滚到底后建议全文及底部留白可见。固定“查看全部参数”与“使用此曲线”保持完整，列表仍六完整项。本台中文 Turbo 所见范围通过。
- [x] root报告 Mock/Debug 构建及 lint 成功（1m45），v21同签名递增覆盖安装、APK hash一致，17数据文件hash保持。本轮未重新运行350单测，不作为本轮新增通过证据；未外推其它配方/字体/硬件。

本轮文档冻结，可由root普通提交/push至原PR4；未预记CI、合并或发布。
