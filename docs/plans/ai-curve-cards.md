# AI 会话曲线卡片与编辑草稿

- 需求与范围：可信工具结果显示真实分段曲线卡片；推荐卡只导航萃取页。新增／修改／内置复制卡按钮仅“确定”，打开已有编辑页并预填草稿；用户在编辑页主动保存才写入，聊天页不直接保存或弹保存确认框。
- 来源／工作分支：复用已有 `feature/ai-chat`，基线 `599670aab75d94070bf836cb813239e30e3e46e7`，来源 develop，未来目标 develop。保留所有其他未提交改动，不另建重复工程。
- 分工：Sol 实现，Astra 独立设计／终审；共享文件唯一写者，Gradle 由协调指定 owner 串行执行。
- 文件：独立 `AiCurveCard`／`AiCurveCardView`／`AiCurveEditorDraft`，最小接入聊天 Engine、Activity、Presentation、协议返回数据、会话 codec、既有 CurveEditorActivity，以及曲线只读详情／提案接口和 focused tests。
- 推送与合并：用户明确本轮不推送、不创建 PR、不合入。
- 发布／签名／升级：本任务不发布、不安装、不改变包名或签名；候选构建／设备验收由协调者另行按授权安排。session schema5 读取旧1..4，保留 Mock132 schema2兼容及旧 epoch 行为。
- 回退：只回退本功能代码，保留用户曲线、会话、草稿和未知记录；不卸载或清数据。

## 当前交互与安全边界

- `curves` 工具成功结果只登记本轮可信候选，不生成可见卡片。模型比较后必须显式调用只读 `present_curve_cards(ids_json)` 选择实际推荐 ID；每次成功调用替换选择，默认1–3最合适并在正文解释原因，用户明确数量优先（非硬3条）。每轮卡片资源上限仍10条推荐与1条提案，候选登记最多100；超限明确错误，不静默截断。模型 Markdown 不创建可操作卡片；拒绝／失败不提交该轮新卡片。
- 图形使用真实分段 target 的 AI 专用独立段格，按段序等宽排列；没有跨段连线、采样圆点或时间轴。压力／流量分别定标，图外逐段多行列出目标、单位、水量；真实温度、预浸和仅首段时间按来源字段展示，不从水量或流量倒算时长，结束相关字段仅称设定。未知 raw 参数不生成卡片。完整修改前／后值、停用阶段、单位和来源可展开审阅。
- 推荐卡保存稳定 ID、来源、完整规范快照及 SHA256。每次点击重新核对当前来源；删除或内容改变时保留历史快照并禁用打开。仅保存本地选择并导航 MainActivity 的 slot7 萃取页面，不调用启动／预热／快捷启动或写机方法。
- 自定义 `create`：编辑页“创建曲线”；custom `replace`：编辑页“保存修改”，目标仍为同一 draft ID；`copy_from_factory`：可信完整内置来源、转换后的完整基准和应用分配的新 draft ID，编辑页“另存为新曲线”。内置原 ID／内容永不成为写入目标，普通 editor 的 CURVE_ID 原有另存语义不变。
- 卡“确定”只重新核对完整来源并建立新的原生编辑事务；随机进程内 handoff 保存 typed draft，Intent 不携带模型 JSON。只有编辑页主动保存时，才 CAS 检查当前 revision、完整原值、目标、credential revision／epoch、会话 context 及正在使用门禁。一次尝试，失败或未知结果不自动重试。
- 配置重建复用同 handoff 与原编辑器的原始字段、焦点、选区和滚动恢复。进程恢复丢失 handoff 时仍显示已有草稿／原始字段，保留操作类型，禁用保存并明确返回卡片重新确定；绝不降为普通创建。恢复卡本身不是写许可，明确点击后重新核对当前完整来源，建立新的编辑事务。
- 不扩展持久全局 revision 来推断跨重启历史 ABA；新编辑事务能检测其建立后的库 revision／ABA 变化。跨重启重新点卡会核当前完整来源，不继承之前许可。
- 安全截断回答可保留已验证推荐和只读提案卡；不完整提案没有编辑按钮。所有运行时 proposal token 在 turn 结束取消；卡片、会话持久化和 compact 不保存执行许可。`awaiting_editor` 明确只待打开编辑器，不代表保存。
- session schema5 严格限制 assistant 卡片、数目、大小、来源、完整规范快照、指纹和复制来源。旧历史、inactive 模型槽、草稿、OCR 和完整工具协议继续保留；compact 不删除可见卡片，也不把卡片转成批准。
- 模型请求前检查完整 session 容量（包含 inactive 槽），减去将被替换的 active protocol，再预留新 protocol 上界、回答、摘要、卡片及元数据。不足时显示 storage_limit，保留草稿且不发请求；这是本地保存边界，不是256K模型上下文。

## 验证与独立审查

- 新编辑器流程：`compileMockKotlin`、14类97项 focused tests、`lintMock` 全部通过，0失败／错误／跳过。覆盖 EditorDraft6、Detail3、Card6、Persistence5、CustomCurve18 及相关会话／协议／context／安全／旧schema回归。
- 核心 fake 证据：卡“确定”和取消零写；编辑器显式保存才写且一次性；create 冲突不覆盖；replace 同 ID／完整原值、revision、ABA、正在使用和 origin 变化拒绝；factory-copy 新 custom ID、完整隐藏阶段、内置源不变、伪造工厂 ID／变源拒绝；handoff 重建复用／失效、恢复重新核对，普通另存保留原曲线。
- 内置保护同时有写入结构门禁；本轮未把全部100条资源逐项编辑保存，不声称完成100条逐项操作验收。
- 日志 `/tmp/openhoyi-ai-editor-flow-checks.log`；归档 `../openhoyi-local-materials/ai-curve-editor-flow-checks`。
- Astra 独立源码终审通过：可信卡来源、完整快照、仅导航、确定只打开编辑草稿、三类保存语义、factory 另存、CAS 和恢复无许可。原 Coordinator.confirm 仍为旧 focused tests 保留，AI 生产路径已无调用。
- 本轮未操作设备、真实 Key、用户聊天／曲线、付费服务或咖啡机。Android 真正点选、配置重建、窄窗／三档字号／深浅显示仍未实测；源码审查、JVM 与编译不等于 Android 或硬件验收。

## 被用户后续决定取代的历史

先前实现曾采用聊天页原生确认框直接保存，并通过84项 focused、后续11项卡片回归及 lint；用户随后明确要求“确定→编辑页预填→主动保存”，以上旧路径已删除。旧测试结果仅作历史记录，不能替代当前97项验证；没有构建或安装该旧交互的137候选。


## 段序图语义修正

- 只读核实：此前代码为等宽水平目标段和同单位段间竖连接，并非线性插值或真实采样；但连接和圆点可能带来时间轨迹误读。现仅替换 AI 卡片图，不改曲线库共享 Sparkline 或源数据。
- 新纯投影 AiCurveRecipeDiagram 复用 CurveRecipePlan／CurveStageParameters，独立段格只表示段号及目标水平，不生成逐段时长、采样序列或固定停止原因。完整停用段保留于详情，不进入启用段图。
- 真实100条资源只读统计为2段20条／3段78条／4段2条；代表001：92°C、9／6bar、50／20mL；002：93°C、3／9／6bar、15／45／15mL；086：92°C、首段3bar，后续2／2.8／1.5mL/s，20／35／25／10mL。保留现有合法1段旧schema显示兼容，不新增2段下限。
- 新增 AiCurveRecipeDiagramTest 5项，验证真实代表和旧合法边界／单位／inactive／时间字段；实际运行结果由协调者串行验证后补记。旧137候选保持不变，不安装。


## 最新可视化差异展示与验证阻碍

- 在真实独立 after 分段图下，新增原值→新值差异投影：逐段目标／单位／水量／启停及真实元数据；未变化字段不强调。CREATE 明确新增配方，没有虚构原值；factory-copy 使用可信转换前值。完整前后详情继续可展开。
- 旧 schema 未存停用槽的原值在差异和完整详情均显示“无已存参数”，不把协议 padding 0 当作原始配方参数；不改变原 document 或协议。
- 新分段图5项、差异投影4项 focused 用例已编写，尚未执行。Astra 对新图及差异源码进行了只读复核，最后 legacy 详情修正待回读。Android 实际显示未测。
- 最新16项分段图相关 Gradle 检查两次自动权限审核超时，未执行。阻碍原文：`CreateProcess Rejected("The automatic permission approval review did not finish before its deadline. Do not assume the action is unsafe based on the timeout alone. You may retry once, or ask the user for guidance or explicit approval.")`。本轮不再尝试 Gradle、变相执行或打包；旧97项／137候选不是最新分段图与差异展示的验证证据。


## 已确认参考左卡的局部视觉落地（尚未构建）

- 已实际查看本地 `../openhoyi-local-materials/ai-card-design-reference/confirmed-stage-design.png`，按用户确认的左卡结构修正；不是要求整页左图右参数，也不声称像素一致。
- `AiCurveRecipeDiagramView` 改为一个共享大图框：真实纵轴刻度、段序标题和分界，每段独立水平目标，底部目标／单位／水量与同一段列对齐。压力与流量混合用独立左右轴，纯流量只用流量轴，不连接跨段、不画采样圆点、不用时间轴。
- Canvas 字体走 AppTextUnits，轴宽／图高按 fontMetrics 测量；底部 TextView 同一辅助字号。段参数按可用宽度排成1／2／4行，保留完整数字和单位；确需更宽时仅图横滚且有显式提示，按钮与其余信息仍在正常卡片流内。
- 卡片层级采用现有 palette／字体：状态圆角 badge、大标题、温度／段数／水量摘要、共享大图、独立预浸／首段／重量设置、可见差异、次级完整参数、主色导航或确定按钮；编辑权限、Engine、协议和源配方不变。
- 轴刻度新增2项 focused 用例（Diagram由5变7），源码静态核查及 Astra 独立源码复核；本轮未运行 Gradle、未编译／lint／新增用例，未打包或设备操作。前述整合树已通过的旧Diagram5／Changes4不能替代本视觉增量的构建与 Android 截图验收。


## Mock138 长串卡片根因与密度修正

- 已实际查看 `../openhoyi-local-materials/ai-card-feedback/mock138-long.jpg`。根因是旧 Engine 的 `curves.also(onCurveResult)` 把检索的每行都自动发布为卡片，读10条就展示10张，与实际推荐无关；不是只需把前10截成前3。
- 活动 AI 与已测 Mock138 sourceProject 的 Engine／Policy／Card／CardView／DiagramView／Activity／SessionStore 源码已逐文件比对一致，继续只在活动 `feature/ai-chat` 唯一写入。独立历史工作树不修改；活动 AiRequestContractTest 前向保留已测 C 的 `custom_curve_document` 契约修正，再适配新增选择工具。
- 新选择工具仅接受严格 JSON 字符串数组中的本轮已查可信 ID，按指定顺序整批验证最新 source fingerprint。重复／未知／变更／超限整批拒绝且不改变旧选择；成功 replace、空数组清空，同序重复幂等；后续页候选可以选择，选后源变化仍保留旧快照且点击重新核对不可用。工具不存库、不操作机器，不从正文提取链接或动作。
- 默认1–3是系统推荐策略，不是数量硬限制；明确5／10允许，超过本轮10卡安全预算明确提示，可正文说明或由用户主动后续轮继续。候选100与持久卡10分离，Session／codec／发前容量预留不扩大，不删除已可见历史。中性“曲线”badge兼容旧纯检索持久卡，不伪称那些旧记录是新推荐。
- 密度按字体决定刻度安全间距：图高不低于96dp且满足5刻度间距；卡padding12dp，减少组距。预浸／首段／目标重量合为可换行元信息；完整参数与主按钮按实际文字宽度同行，不足时堆叠，触区仍至少48dp；关键单位、前后差异、完整停用参数、字号均保留。
- 新增 AiCurveCardSelectionTest 6项；更新实际 Koog 读后选择／只读不展示／Markdown无动作路径、7工具 API 契约、完整 schema token 预算与 HTTP200安全拒绝 selector不执行断言。仅假数据，未调用付费服务。实际构建与测试由协调者锁释放后统一验证，当前不预称通过。


## Mock139 卡宽与参数下留白的测量修正

- 已实际查看 `../openhoyi-local-materials/ai-card-feedback-wide/1000032060.jpg`。修改前活动 Presentation／CardView／DiagramView 均与139 sourceProject及source-manifest SHA256一致；Astra亦核对home历史基线一致，排除误用旧包判断。
- 宽度根因：卡直接style.add以全transcript宽展示，assistant bubble却受78%／520dp上限；动作主按钮weight1再撑满。现仅为卡增加左对齐限宽host，同bubble上限；主次按钮自然宽weight0，按真实文字测量不足才纵排，多行及至少48dp触区保留。
- 高度根因路径：原HorizontalScrollView先按requiredWidth测child，参数可能多行；fillViewport再扩宽child时参数变少，但parent wrap-content高度可能保留初测值。新图专用scroll在FrameLayout推导高度前就确定最终max(viewport,required)宽并EXACT测child，关闭fillViewport二次扩宽，同时保留高度约束、padding和margins。没有继续盲降96dp图高。
- 新增AiCurveCardLayoutPolicy与3项纯像素决策测试，输入是宿主／字体测量后的pixels，不修改系统scaledDensity、字号或源配方。纯policy和源码审查不等于Android View实际measure证据。
- Astra独立源码终审通过；本增量尚待协调者编译、focused与实际frame／截图核验，不能声称参数下空白已在设备消失。未新增说明文案，未改推荐、导航、编辑权限、Engine、Activity或其他工作树；没有设备、安装、AVD、付费或PR操作。
