# 实时萃取圆表、阶段零线与图优先布局实施计划

> **For agentic workers:** 使用 `superpowers:executing-plans` / `superpowers:subagent-driven-development` 执行。用户已明确授权直接实现及Mock/Debug同版本覆盖安装，Sol开发，Astra设计/独立审查/原图验收，root统一构建安装，不重复审批。

**Goal:** 采用已选简洁数字圆表B，让同图四指标成为页面主体；明确阶段零线与缺测，提供可辨尾端名称及柔和但真实的绘制。

**Architecture:** 实时显示层修改，不改执行协议和原始记录。renderer/projection/gauge与Fragment布局分工，历史默认分支不被无条件改变。

**Tech Stack:** Kotlin原生View/Canvas、现有ShotSeries/四指标投影、单测与实际截图。

## 分支和安全边界

既有develop派生任务，继续 `feature/task-first-ui`，基线 `3ad0765`，PR4→develop；遵守 `docs/git-workflow.md` 与 `docs/plans/task-template.md`。不发布、不合并、不强推。用户本轮授权Mock与真实Debug使用相同递增版本分别覆盖各自包；root核各自签名/包名/版本/APK hash及原数据，不交叉覆盖、不卸载清数据。不自动启动真实机器，软件/Mock与机器效果分开记录。

## 明确的显示合同

### 1. 停泵与缺测

源码证据：`Protocol.ExtractionTelemetry` 只有 `valveOpen = statusBits & 64`、`brewWait = statusBits & 32`；`ShotSeries` 存的 `brewing = valveOpen && !brewWait`，false同时可能包含预热等待。**不得把所有历史brewing=false直接当停泵归零**。

root已确认并获Astra审查批准的显示predicate：ShotPoint追加可空原始statusBits（旧记录null）；已见本杯brewing=true后，相邻Extraction通知间隔≤1500ms，明确bit64关阀且bit32非brewWait。仅满足这些条件才在live投影画0基线，标“暂停”，概览/无障碍注明“阶段零线（显示推导），不是实测压力”。无新通知不补零点；恢复后的大间隔仍起新段。追加持久化尾列须向后兼容，原pressure字段不改。连续可信停泵样本可接0线；超过原缺测间隔、断连/UNKNOWN/旧记录无证据依旧断段。累计注水mL、秤读数g、温度°C均保真实量，不随停泵清零；图不声称残余物理压力实测为零。

### 2. 图优先与圆表B

- 正常无异常横屏，真实grid高度目标为右侧可用内容高65–70%，本台约430–460dp；不是chart卡面积。异常提示展开允许占比下降，停止不裁。
- 顶部总预算约96dp：左上48dp为曲线名/时间/上下文/工具，左下48dp为紧凑指标；右端96×96dp简洁圆表B，与两行并排，不再额外加一行或巨右侧栏。
- 复用原RUNNING完整guidance（含守机提示），同一行不再重复“萃取中”。普通IDLE已就绪/ENDED可合并；STARTING/STOP_REQUESTED/手动杯注意/预热在途/UNKNOWN/告警/通知权限及其他阻断仍完整展开。
- 每项指标名称14sp、值24sp、单位14sp同排；压力大值只由圆表显示，压力选轴标签仍可点。其余注水/秤读数/温度值不隐藏。常驻range和教学句移入概览/说明，选中指标轴仍有完整真实量程。
- B表细圆弧＋中央真实压力与bar，无9bar最佳暗示、无臆造安全红区。缺测显示—bar、指针/弧隐藏；量程需覆盖实际高值，不把超量程值夹成12。历史/参考数据不能冒充当前仪表。
- 轴标题放紧凑轴头，不独占大行；刻度列按实际字宽，单位/最高刻度不能碰撞。底部停止56dp固定、图间距8dp，保原回调。

### 3. 线形、尾标、渐变与时间

- 保蓝压力/橙累计注水/绿秤读数/紫温度及原单位；没有可信净杯重基准仍称秤读数。
- 每条有效当前曲线末端跟随短名称；只锚真实观察末点，不造未来点。同位置尾标避碰，可用短引线明确归属；不能挡数据、轴、停止，缺测旧线不被标为当前新值。
- 压力和注水可低透明垂直渐变（建议顶部alpha≤0.12、底部0），逐segment裁剪填充，不跨断连/重置空白。线条仍是主体，禁止把不同量纲面积当可比较绝对面积。
- 保无过冲Hermite及全部端点/峰谷；重复平台保留，不能为圆滑改数。Mock每秒量化台阶不得误诊为渲染错误。
- 仅呈现层采用约150–200ms短过渡，依帧刷新；时间轴30秒边界变化可平滑bounds，不插入未来数据、不更改原time/统计。新杯、断连/缺测/后台立即取消；不让新值与旧杯混合，不每帧重建控件。仪表数字报告真实当前值，动画不伪装为额外测量。

## 两位Sol的文件所有权

| owner | 文件/职责 | 不得越界 |
|---|---|---|
| graph Sol | `ExtractionOverlayView.kt`、`ExtractionHistoryVisualStyle.kt`、`FourMetricChartProjection.kt`、`LiveChartGeometry.kt`、新增圆表/阶段证据helper及针对测试；必要ShotPoint可选证据需先协调root | 不改Fragment启动/准备/停止回调，不改执行协议，不改已存raw值 |
| layout Sol | `ExtractionFragment.kt`头部与提示布局；必要旧Activity同构入口由root确认 | 不编辑renderer/projection；原状态/安全/确认门禁不动 |
| root | 参考证据、资源冲突协调、统一构建、同版本双包安装、截图 | 不以CI绿色替代设备效果 |
| Astra | 本计划、规格/质量审查、实际PNG验收 | 不写生产、不构建/提交 |

顶部96dp合同必须只有一个owner负责总测量。可由Overlay预留左上48dp并由Fragment原header挂chartHost顶部、右让112dp；不能各自额外占48dp，也不能盖住legend触控/读屏节点。长文/大字按真实内容宽度自然降级，不用固定tile高度误判成窄屏。

## 可执行步骤与验证

- [x] root确认阶段predicate，Astra批准显示语义（不是实测物理0）。
- [ ] graph Sol写暂停/预热等待/未知/断连/恢复原点与旧记录尾列兼容回归，再实现live-only零线；保原压力记录完全一致。
- [ ] graph实现B圆表、尾标避碰与两色克制渐变，验证0/缺测/高压越范围、同值四尾标、断段、浅深可读。
- [ ] graph实现可取消呈现过渡，测试最终路径原点/无过冲、30s跨界、新杯/后台/断连无遗留动画；报告原样本与显示帧区别。
- [ ] layout按96dp共同合同压紧上下文和指标，移除常驻重复范围/教学行；保异常完整、固定停止与工具一步可达。
- [ ] Astra冻结diff后分别作规格与质量审查，不把代码静审当视觉通过。
- [ ] root统一相关单测、Mock/Debug/AndroidTest编译、lint、语言生成与APK资源核对；原生编译和运行结果分开。
- [ ] root同版本覆盖安装两包，分别验证签名/hash/版本及原数据；不重发任何未知设备指令。
- [ ] 实际查看中文浅深、正常/大字运行图，记录真实grid/pageContent比例及字形边界；已知停泵/缺测/尾标碰撞须用可信fixture或授权证据验证，不伪装硬件实测。完整CI仍单列结果。

## 当前证据与回退

只读确认现主图链路与brewing字段歧义，方案已发两Sol；尚无本轮实现/构建/视觉通过。参考旧HOYI与Decent由root提供明确来源，取其固定量纲/清楚读数/柔和呈现，不复制滤波改数，不未经比较宣称优于竞品。

代码回退用普通修复/revert及更高版本同签名APK；保所有数据和未确认记录，不卸载、不盲降级。两包同版本升级授权延续，实际安装/设备操作仍逐项如实记录。

## 研究依据与首轮静审（实现尚未验收）

- Decent官方 [`machine.tcl` L337–339](https://github.com/decentespresso/de1app/blob/main/de1plus/machine.tcl#L337) 将profile/preview设为quadratic，**live默认linear**；Insight使用该配置。root缓存证据在 `build/live-instruments-2026-10-31/decent-machine.tcl`、`decent-insight.tcl`。不能将本轮柔和显示宣传为“照搬Decent默认样条”。
- 旧HOYI只读证据 `analysis/readable/app-service.js:69888` 使用curve与0.18透明渐变；60410的平滑针对瞬时流量，不可直接用于压力或改写采样。本轮采用更克制的渐变，保真实峰谷和缺测。
- 首轮布局静审确认Main Fragment保留UNKNOWN背景、选曲条件、准备/安全/写入在途等完整提示；普通guidance才并入顶部。header与图共用父容器并反馈真实高度，避免额外重复48dp。尚待修正旧测试mode节点合同及旧Activity读数去重核对。
- graph中间稿原始statusBits可空尾列与旧V1–V3读取兼容，暂停predicate区分关阀/预热等待，仪表暂停显示—而非伪称测得0。尚需最终测试与截图。
- 已提出大字/长文指标自然高度不能被固定48dp内滚动隐藏；四尾标同值碰撞和96dp圆表针/刻度/数字须用真实图核实。本阶段不记构建、视觉或硬件通过。

用户已选B参考图：`docs/research/task-first-ui-concepts/live-pressure-gauge-b.png`。圆表实现须保细刻度、细针与真实数字，不能缩成普通环形进度。

存储兼容边界：新版读取V1–V4，旧版不认识新增V4状态尾列；不能将直接降级旧APK称为可用回退。回退代码时必须保留V4读取支持，再以更高版本同签名覆盖，不删除或改写已存样本来迁就旧版。

### 冻结差异复审

静态阻塞已闭合：旧Activity重复读数已去除，Mock完整语义检查迁到真实compactClock；compactTop按header与指标自然高度测量。尾标改正向排布、底部限位后逐项反向避碰，容量不足显式降级；实际代码及两顶两底、四顶、四底、空间不足测试已独立复核，原反例期望为10/30/70/90，不再逐个顶部clamp产生重叠。允许进入统一构建及实际截图验收。此结论仅为静态规格/质量审查，不代表测试已执行、65–70%面积已达标或视觉已通过。
