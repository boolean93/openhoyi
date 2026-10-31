# 曲线库分段控制预览实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans，按下列有界任务执行。用户已批准直接修复，由Sol开发、Astra设计验收、root构建提交；不重复询问执行方式。

**Goal:** 列表缩略图和详情按每段真实压力/流量控制目标呈现，修复流量曲线无图或全部误标压力。

**Architecture:** 以只读、带单位的分段预览模型统一列表与详情；保留原阶段横轴及平板滚动结构。渲染不改变任何配方、执行协议、启动权限和写入门禁。

**Tech Stack:** Kotlin Android原生View/Canvas、现有CurveLibraryItem与catalog资源。

## 已核字段合同

证据：`analysis/readable/app-service.js:28390`的`segTargetUnit`（位于工作树外兄弟analysis目录，只读）、`:28738`的`curvePressToTenths`、`:40494`的模式位编码；仓库`FactoryWireProof.kt`的adapter及`CoffeeCommands.kt`。

| 来源 | 第1段 | 第2–4段 |
|---|---|---|
| factory variableFlowLogic=false | target /10 bar | target /10 bar |
| factory variableFlowLogic=true | target /10 bar（adapter首段flow=false） | target /10 mL/s |
| controlProfile variableFlowLogic=true且firstSegmentFlowMode=true | target /10 mL/s | target /10 mL/s |
| controlProfile variableFlowLogic=true且首段flag=false | target /10 bar | target /10 mL/s |
| controlProfile variableFlowLogic=false | target /10 bar（首段flow flag不独立推翻总体模式） | target /10 bar |
| custom PRESSURE | target /10 bar | target /10 bar |
| custom FLOW_RAW | 原始目标，单位未确定 | 原始目标，单位未确定 |

`CustomCurveDocument.fromLibraryItem`把`variableFlowLogic || firstSegmentFlowMode`折叠为FLOW_RAW，schema没有逐段模式；不可凭该值把整个草稿猜作mL/s。本轮不改存储schema，不恢复已丢失信息。来源若无法确定，明确文字，不造物理轴。`segmentFlowMl`/`firstFlowTenths`等是阶段水量，不是mL/s；`pressureLogic`不是新的数值来源，压力限制绝不画成控制目标。

## 设计验收

- 横轴明确“阶段”，各阶段等宽独立短横线/点，不连成时间采样曲线，不计算秒数或过渡轨迹。
- 压力采用现有压力蓝实线、流量采用现有水流青虚线；数字与单位同时给出，不能只靠颜色。纯压力/纯流量单物理轴；混合左bar右mL/s，各按真实量纲映射。压力正常0–12bar；流量上界按真实max取6/10/20/30mL/s中覆盖值，列表与详情一致，显示0和上端。量程覆盖全部有效目标，不截大于12的流量原值、不把未知当0。
- 详情每段目标可读，图例单位/刻度不重叠，原建议/参数/动作/滚动不丢。列表缩略图同一投影与颜色，列表文字给明确“压力/流量/混合/单位未确定”，可读单位不能藏在点击后。
- 未知FLOW_RAW不画带bar或mL/s的假目标。显示原始数字与“单位未确定”，保留查看原参数路径。
- 复用当前平板布局与字号，不改其它页面风格；本台Mock v17起实际查看纯压力、工厂混合、可用纯流量和未知代表状态。没有真实纯流量库项时，用现有只读测试fixture验证，不静默改用户数据。

## Sol实施任务

**范围：** `mobile/src/main/kotlin/io/openhoyi/mobile/CurveStageParameters.kt`、`CurveStageView.kt`、`TaskProductVisualStyle.kt`内`TaskProductStageRenderer`、`CurveFragment.kt`；必要新增独立只读预览模型；对应单测与catalog/生成资源。既有其它CurveStageView调用保持压力语义兼容，不全局误改编辑器或历史图。

- [x] 先以纯模型测试冻结表格：压力60→6.0bar；工厂flow=[60,25,30]→[6bar,2.5mL/s,3mL/s]；首段flow=true全流量；flow=false忽略单独首段flag；0保持有效；FLOW_RAW保持未知；分段水量不影响目标；1..4段只取生效段。
- [x] 共享预览模型承载每段index、raw target、quantity与可显示值；替代CurveFragment仅`pressureTargets`决定图可见的条件。未知保留原因，不整图伪装压力。
- [x] 同时适配productRenderer和fallback，缩略图与详情使用同一目标和独立轴范围。保留旧pressure-only调用兼容入口，确保已有压力测试不被机械删掉。
- [x] 更新标题/说明/无障碍与资源；不全局把历史pressure文案替换成混合，不改变曲线选中/启动/编辑/排序/保存逻辑。
- [ ] 针对实际Canvas检查纯压/纯流/混合的单位、段数和端点位置；未知不出现虚假物理轴。对应真实列表及详情绑定覆盖，不新增无界语言/设备矩阵。

## 构建与交付

- [x] Sol冻结后Astra静审数据合同/布局；root统一运行catalog校验、生成检查、mobile单测、Mock/Debug/AndroidTest构建与lint。禁止并行构建和构建中改源码。
- [ ] root实际Mock截图/安装验证（v17起），Astra用原图验列表和详情单位、混合段区别、可读性及原动作完整；实机协议执行不属于Mock验收。
- [ ] 记录软件/图片范围和仍未验状态，root仅本任务文件普通提交推送`feature/task-first-ui`→`develop` PR4，不合并、不发版。

当前状态：实现及静审完成；v17构建/344单测/lint通过；中文浅深Turbo混合与纯压力代表图实际验收通过。原生CI运行、纯flow/未知原生图及硬件尚未验。

确切首个实图回归：现已选中工厂`factory-v3-003` HOYI Turbo Shot，阶段1应2.0bar、阶段2应3.0mL/s。修前证据`build/curve-library-status-2026-10-29/turbo-before.png`由root保留；修后直接比较现有真实条目，不写fixture、不启动设备。

## 首版静审记录

root独占`scripts/capture_mock_ui.sh`：保留原curves-light/dark，并在其后真实点击HOYI Turbo Shot新增curves-mixed-light/dark截图；不点击使用/启动，不移除原导航和启动断言。该4行已核。

首版typed单位映射、流量上界6/10/20/30、未知原值文本及旧压力renderer兼容路径已核；执行门禁未改。构建中不修改源码，交Sol在构建结束收尾：

- [x] 短高fallback给flow/mixed用中性“阶段目标”模板，不能沿旧“压力阶段参数”误称。
- [x] CurveFragment纯压目标也从typed模型取，避免总体flow=false/首段flag=true时被旧helper隐藏；旧helper对其它页面兼容不改。
- [x] 列表模式摘要补bar/mL/s单位，不能只有详情能知道量纲；compact图继续按同一投影。

Canvas轴字/端点与实际详情宽高仍待原图，不以静态推算标视觉通过。

最终收尾静审：三项已闭合，两原生测试保留几何、全文和只读门禁；mixed检查typed目标及renderer绘制路径的stageTarget单位顺序，FLOW_RAW检查原数/未知原因且不显示假图。draw evidence为renderer自报，并非独立像素或Canvas拦截证明；实际字形/轴碰撞仍以原图验收。当前库与capture条目没有全流量来源，不写用户fixture凑图；纯flow只具模型测试范围，原生纯flow视觉未验。可进入v17实图，构建结果尚待root确认。

## v17 浅色混合实图验收

Astra实际对照`build/curve-library-status-2026-10-29/turbo-before.png`和`build/curve-control-preview-2026-10-29/turbo-light.png`：原详情空白/缩略图—已修复。HOYI Turbo Shot第1段蓝实线2.0bar投影左0–12轴、第2段青虚线3.0mL/s投影右0–6轴，目标文字、轴单位/端点及等宽阶段标签完整无重叠；“横轴为阶段，不是秒”明确。列表缩略图同语义，单位摘要换行仍完整，底部详情/使用按钮完整。此中文浅色混合代表图通过；压力回归、深色待图，纯flow与未知原生图尚未验。

root报告v17构建/单测/lint全部成功：344tests零failure、1skip，两variant资源各11097；安装APK hash及17数据文件保留验证通过。原生CI当前仅编译，不标运行通过。

## v17 最终有限交付（2026-10-29）

- [x] 实际view_image复核同目录 `turbo-dark.png`、`pressure-light.png`、`pressure-dark.png`，连同前述`turbo-light.png`共4图。混合浅深均清晰区分蓝实线2.0bar与青虚线3.0mL/s、双轴量程及阶段横轴；压力回归仍为9.0/6.5bar与0/6/12bar刻度。列表缩略图、单位摘要、原参数/分享/详情/使用动作在所见状态完整，无本轮新增可见裁切或轴字重叠。
- [x] root确认完整构建2m10成功，344tests零failure/1skip、lint通过、资源11097/variant；v17安装APK hash匹配，17数据文件保留。此为本地软件验证与设备Mock展示，不是实机协议执行。
- [ ] 原生CI仅编译尚未运行；纯flow没有既有真实库项，仅模型测试，不造用户fixture，纯flow与未知模式原生图未验；短高/全语言与硬件不外推。
- [ ] 另记既有搜索IME问题：选中条目后再次点击search，IME缩高触发单详情布局隐藏搜索而失焦（root留`edit.xml`现象证据）。本轮未改搜索行为，单独待修，不作为本次分段单位绘制通过的附带认证。

本轮图表范围有限验收通过，文档冻结交root按PR4普通提交推送；提交/CI结果不预记。
