# 萃取图清晰度、曲线直达与根页面标题精简实施计划

> **For agentic workers:** 使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务实施；用户已明确授权 Sol 开发，Astra 负责设计、规格/质量审查和原图验收，不重复请求执行确认。

**Goal:** 让用户一眼辨识同图四指标、实际读数和单位；使用曲线直接进入萃取工作区；回收根页面重复大标题的空间。

**Architecture:** 保持 MainActivity 单壳和现有服务/协议门禁。实时显示只在 ExtractionFragment → ExtractionOverlayView → ProductHistoryChartRenderer 的 live 分支改进；历史默认分支、原始样本与统计不变。根标题按页面处理，不全局删除共享组件标题。

**Tech Stack:** Kotlin、Android 原生 View/Canvas、现有投影及资源体系、JUnit、Android instrumentation/CI 截图。

## 范围与协作

- 来源为既有 develop 派生任务；沿 `feature/task-first-ui`，基线 `6b19c1b`，继续 PR4 → develop。遵守 `docs/git-workflow.md` 与 `docs/plans/task-template.md`，普通提交/推送，不强推，不直接推 main/develop，不发布或合并。
- Astra 只写本计划及审查；Sol 写生产/测试；root 负责冻结、统一构建、CI、提交和推送。保留他人的工作树修改。
- 当前无在线 ADB 平板或本地模拟器，先走现有 CI 的 2560×1600、density 360 截图。明确这是模拟器证据，不称本台/硬件验收。
- 不增加设备命令、不改启动确认、固定停止、未知结果、重连、授权/告警门禁；曲线直达不自动启动、不自动确认。

## 可直接实施的图表规格

1. 萃取页删除重复“实时萃取”大标题，顶部只保紧凑上下文行：**当前曲线名 / 真实状态 / 用时 / 工具**。Mock 提示、风险和未知状态仍明确可见；长名称自然换行，工具至少 48dp。不保留重复的两层压力/水量/温度/秤读数面板。
2. 图表顶部由现有四图例升级为**唯一四指标条**，不新增一层大卡。顺序为压力、注水量、出液量或秤读数、温度；每项名称 14sp、当前值 24sp、单位 14sp、独立量程 12–14sp。当前值用已有数值模型格式化，禁止从显示文案正则拆数字。单位显示 `bar / mL / g / °C`；注水是累计量，不能写成流量 mL/s。
3. 当前 `FourMetricChartProjection` 明确使用原始秤读数，没有推断杯重基准。因此本批默认仍叫“秤读数”；只有已有可信杯重基准/净出液数据明确接入，才允许称“出液量”。无秤保留第四项 `— g` 与缺测说明，禁用选择、不造零线；过期或缺测也不沿用旧值冒充当前值。
4. 保持当前 live 调色板：浅色压力 `#176BD1`、注水 `#C05A13`、秤读数 `#168A55`、温度 `#654ACC`；深色对应 `#80B4FF/#FFAE78/#64DEA3/#A994FF`。图例、线条、当前选轴色一致；沿用各指标可辨线型。选中项有清晰底色/下划线，不仅靠颜色。
5. 左侧只显示当前选中指标的**名称＋单位＋真实刻度**，如“压力 · bar”；默认压力。四项始终保留各自完整量程，点击仅切换轴及强调，不隐藏其它指标。顶部短提示“点指标切换纵轴”可与指标条合并，避免单独占大行。横轴为真实经过时间，末刻度带 `s`。网格轻、文字对比清楚，单位/最高刻度不重叠。
6. 曲线绘制采用**分段单调三次 Hermite（受限斜率）**，只改变 Canvas 路径。必须经过每个原始点，区间值不超相邻端点范围，局部峰谷保留；两点直线、单点圆点。沿投影已有 segments 分别绘制，不跨断连、缺测、停杯或水量重置连接。禁止移动平均、丢峰、重采样、改存储/统计。可把数学计算抽为纯 Kotlin 小 helper，live 与 fallback 共用；embedded/history 默认行为不改变。
7. 实际历史参考保持明确名称和虚线/低强调，不新增参考或虚构未来轨迹。图面不铺多余面积色；保留原样本查看与无障碍完整说明。
8. 正常横屏平板字号 1.0 下真实 `plotBounds` 高至少 **260dp**、宽至少 pageContent 的 **70%**，固定停止完整可见、无重叠。留系统 inset 浮动余量；量的是数据绘图区，不是整个 chart 容器。短高/大字指标条可 2×2 自然增高，重要状态和停止不裁切，不压字号凑通过。

## 各页面标题清单与去留

| 文件/页面 | 本批动作 | 必须保留 |
|---|---|---|
| `ExtractionFragment.kt` | 大页名及重复读数移除，按上方方案整合 | 当前曲线名、状态、计时、工具、风险、停止 |
| `HomeFragment.kt` | 移除重复“机器”独占标题 | 机器身份/连接、四参数、锅炉控制、五槽、开始 |
| `CurveFragment.kt` | 已无重复大标题，不二次改版 | 搜索、管理、新建、曲线名、详情与直接使用 |
| `HistoryFragment.kt` | 根“历史”大页名移除；原 header 动作迁入紧凑工具行 | 本地/复盘/旧记录来源、数量、筛选、条目名、全部操作 |
| `BeanInventoryFragment.kt` | 删除重复“豆子”标题字，不删除整个 header | 库存/数量摘要、新建/管理、豆名、库存规则 |
| `ScaleFragment.kt` | 删除根“电子秤”大页名和重复介绍 | 实际读数、设备/新鲜度、危险提示、去皮/计时等原动作 |
| `AppSettingsFragment.kt` | 删除根“设置”大页名/重复副标题 | 机器、秤、外观语言、信息等有意义的分组与说明 |
| 二级编辑/连接/详情 Activity | 不机械套用根页规则，本批不全局移除 | 返回、当前对象/编辑语境、错误和操作确认标题 |

原则：只删用户从 rail 已知的页面名称及重复介绍；对象名称、数据摘要、警告不是“冗余标题”。不得通过隐藏整层 header 一并丢操作，不把已可一步到达动作藏进菜单。正文和分组保现字号与至少 48dp 命中区。

## 实施分工与检查点

### A. 曲线直接进入萃取（导航 Sol）
- [ ] `CurveFragment.kt` 的“使用曲线”保留原选择/保存/校验后，调用 Main 的普通萃取入口；不绕首页，不设置 startConfirmationRequested，不启动泵。
- [ ] `CurveLibraryViewportChecks.kt` 等现有回归验证真实 Tab、所选配方一致、服务未收到启动命令；拒绝/未知状态仍按原门禁处理。

### B. 实时图与合并读数（图表 Sol）
- [ ] 修改 `ExtractionFragment.kt`、`ExtractionOverlayView.kt`、`ExtractionHistoryVisualStyle.kt`，必要新增单一绘制 helper/纯单测；仅必要调整 `TaskExtractionWorkspace.kt`。不与标题 worker 共享这些文件。
- [ ] 先加绘制算法测试：非等间隔、尖峰/谷、平段、两点、单点、各 segment 不连线；断言样本不变、插值无过冲。再实现 live 绘制。
- [ ] 指标条用真实模型，审缺秤/过期/0值、单位和当前选轴；保历史默认分支及检查原门禁。
- [ ] 原生测试对真实 Canvas/节点保留四线、单位/端点、无假秤线、停止可见、真实图高；按新布局定位，不能删除断言或降阈值。

### C. 根页标题清理（标题 Sol，可与 B 并行）
- [ ] 仅上表 Home/History/BeanInventory/Scale/AppSettings 五根文件。若共享 page 工厂需要可选紧凑参数，由 owner 协调，默认保持二级页不变。
- [ ] 保 header 内现有动作、列表锚点/选择、滚动和详情；复用现有页面几何/导航回归，更新真实新容器定位，不放松可见性。

### D. 有限整体验证（root + Astra）
- [ ] 冻结后统一 unit、Mock/Debug/AndroidTest 编译、lint、语言 catalog/生成及实际 APK 资源验证；原生编译与执行分开记录。
- [ ] 现有 extraction-history 脚本仅把 extraction 调到首项以先取证，**全 16 项/原门禁不删**。基线 `6b19c1b` run `40813879757` 的 history 大字图高 `<96dp` 失败仍记录，不称整个 CI 通过，不为此降低阈值。
- [ ] Astra 实际看 CI 中文浅/深萃取图，核四项读数/单位、选轴、峰值形态、停止与 260dp 几何；缺秤及断段用原有证据/测试覆盖。根页逐个核标题移除后保留控制与实际增益，不拿截图存在代替美观验收。
- [ ] 真实普通“使用曲线”仅切至萃取准备页，原开始确认可用；活跃杯不被重启，Tab 往返同服务同杯。

## 参考与证据边界

- 当前实际 live 链路：`ExtractionFragment.kt:206` → `ExtractionOverlayView` → `ProductHistoryChartRenderer(liveMode=true)`；`ShotChartView` 主要为历史包装，不能只改旧 `ExtractionTrendView` 冒充完成。
- 本地 HOYI 解包只读：`/Users/boolean93/workspace/hoyi_app/analysis/readable/app-service.js`，以及 `analysis/raw/assets/apps/__UNI__7D80DAB/www`。其现有秤杯基准/实时量处理可核语义，但不复制其滤波算法或把改动过的解包树称原厂截图。
- 已实际查看本项目旧参考图 `build/task-first-evidence/ci-eb77b2f-m5/raw/6ee51623e7f32f6df425e0d84313d1bc5e7f6bed/tablet-user-landscape-light-font-1.0/extraction/window.png`：重复大标题、大读数和图例确占空间；这是旧原生版，不是本轮结果或 HOYI 官方图。
- [Decent 官方概览](https://decentespresso.com/overview) 与 [官方首次萃取指南](https://decentespresso.com/first_decent_espresso) 作为实时量辨识、固定图例和实际曲线解释的参考。采用其“颜色/变量一致、量纲明确”的原则，不照搬其流量含义或颜色；HOYI 本图是累计注水量。未做实际对比前不宣称优于/达到竞品全部体验。

## 当前状态、发布与回退

- [x] 授权范围与可执行设计确认；读取实际 live 路径、根页标题和当前颜色。
- [ ] 生产实现与独立规格/质量审查完成。
- [ ] 新版本构建与本轮 CI 实际截图验收完成。
- [ ] 硬件验收：本批不执行、不以 Mock 替代。

本轮不发版、不操作真实设备。若后续用户已有授权范围内做 Mock 覆盖升级，由 root 验同包/同签名/递增 versionCode、APK hash 和既有数据保留；当前 ADB 不在线，不把安装列为已完成。回退使用普通修复/revert 提交；不卸载清数据、不改设备未知记录。现有基线历史视觉失败与新萃取有限通过分别报告。

### 首批静态审查（2026-10-31）

已实际审查五根标题 diff：Home 仅删除重复机器页名；History 数量移入原 header、来源与工具保留；Bean 页名替为真实数量/库存摘要、原操作保留；Scale 保 Mock 提示和 guard；AppSettings 清页名列并归零空 header 预算，保工具 host。共享样式及二级页默认不变。此批无静态阻塞，可进入统一构建和原图验收；尚未确认实际空间回收、长文或新 CI 通过。

### 图表中间稿审查（2026-10-31，未冻结）

已读 LiveChartGeometry、ExtractionFragment、ExtractionOverlayView、ProductHistoryChartRenderer 及相关测试。分段 Hermite、原点保存、独立 raw weight 名称、30s 阶梯时间轴方向符合规格；尚不批准实现完成。已交 root/Sol 收口：新杯 traceIdentity 重建旧 points 会把旧杯长时轴带入新杯，需回归；图例 swatch 需与水虚线/温度点线一致；旧 quantityLabel/空 Button.text 原生断言改验真实可见指标条；停更读数需明确最后采样/缺测而非冒充当前；运行时“请守在机器旁”安全提示不可随重复状态隐藏。新图面积检查不能替代已有 pageContent 宽度分母门禁。待冻结后复审和实际 CI 原图验收。

### 冻结稿规格/质量复审（2026-10-31）

已核新杯时间轴二次重置及真实 View 回归、图例线型同步、真实指标节点读数测试、pageBody 宽度门禁；readiness 的原危险颜色/UNKNOWN 背景/chooseCurve 可见条件完整恢复。五根标题和曲线普通直达（slot 7、confirmStart=false）未改业务门禁；16 项矩阵保留，特殊缺秤状态仍要求第四缺测指标存在。Hermite 原端点/分段与历史默认分支保持。

剩一项状态一致性收尾已交 root：`unavailableLiveMetrics` 只更新可见 tile，plot 的读屏 `task_chart_latest` 仍使用旧样本；需 live 统一缺测规则、setter 同步更新描述，并保持原记录。统一构建进行中，尚无本轮运行/视觉通过结论。

### 首轮 CI 原图失败与有界修复（2026-10-31）

首版 `e05d0b3` 的 CI 中文浅色 fontScale=1.3 原图已实际查看，路径 `build/extraction-clarity-2026-10-30/ci/extraction-history-visual-tablet-user-landscape/raw-device/e05d0b302b7b96b037a30304066d0090969d2d40/tablet-user-landscape-light-font-1.3/extraction/window.png`。四项名称、数值、单位、量程完整且数字/单位基线一致，停止按钮 56dp 完整；但**视觉不通过**：geometry 中 plot View 高 400px，真实数据区 y149.5008..281.1244，仅 131.6236px / 2.25 = **58.50dp**。顶部 fallback 每组 257px=114.22dp，轴布局另消耗约119.28dp，趋势被压为横条。此证据不验证普通字号、深色、其它根页面或曲线直达。

批准修复限两处：TaskShellStatusView 的 BOTTOM 设备组改只读名字/状态两行＋独立至少48dp动作横排，两组横排，真实宽不足才上下排列；104dp rail 不改。requiredRailHeight 按独立竖排预算测量，避免当前模式影响判断。live 轴顶部由 line×2+12dp 改 line×1.5+8dp，历史/comparison 不变，真实字形不得相撞。预计先回收到约126–135dp，不提前声称180dp；大字原96dp门槛、普通260dp门槛不降。状态/动作不隐藏、字号不缩、安全提示保留。

新 delta 静审未见设备命令或回调改变。需收口的测量确定性提醒：BOTTOM 自然宽度判定不得受上一次按钮 maxWidth 截断值影响；以独立自然宽确定换行，避免长翻译阈值附近布局依赖历史。正式构建进行中，平板重新在线后的新图待提供，不标视觉完成。

CI 字号环境决定：仅在新建临时 Android14 模拟器 workflow 的检查启动前初始化 font_scale=1.0，之后测试采样其真实存在值并严格恢复；检查脚本的键存在性/逐值校验与失败码不变。不用于真实设备，不把此前系统自动重建1.0导致原缺键无法恢复的失败改称通过。
