# M4 用豆横屏 IME 紧凑动作行 Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本窄计划执行。Astra设计/验收，Sol生产/测试，root统一构建提交推送；保留同树他人修改，不新派代理。

**Goal:** 原用量编辑状态出现横屏IME时，输入可完整滚达且保存/返回始终完整可用，不牺牲字号、触控或冻结内距。

**Architecture:** 仅本页 `PreparationWorkspace` 为已有两按钮dock增加条件式横向排布。宽度决定双栏不变；用原纵向自然预算判断是否需要紧凑行，再用两按钮自然宽度验证能否同行；键盘收起恢复原排布。无需新页面、通用框架或控件重建。

**Tech Stack:** Kotlin Android Views / WindowInsets，已有Bean visual fixture。

## 1. 基线、证据与边界

- 延续 `feature/task-first-ui` / PR #4 → develop，共同develop基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`，当前检查HEAD `85319cd9078527168a3a75c5cc76d93a48b1ac9e`。遵守 [Git规则](../git-workflow.md)，不发版/安装/设备操作。
- 这是用户整体“可用且对稿UI”目标下的 Astra 具体设计决策，不称用户逐字批准此variant。沿 [M4 §3A](2026-10-19-m4-bean-workflow-visual-migration.md) 的固定主操作/返回和局部滚动，沿 [原Google/Apple设计依据](../research/2026-10-15-task-first-ui-redesign-draft.md) 的关联分组、清楚主次和自适应布局；不再调研或生成新稿追认实现。
- 原失败source `aac5c917c4bada1b30602b263a49db5221f52c74`，run40054358205，`ci-aac5c91/bean-artifact.zip` SHA `ab11c3ba37ac4435f136dd01cd7a14a2c53103b6d3aad70829df4d8c4ad59dd1`。首败 `whole(observation(amount))`，不是保存/扣减业务已失败。
- Astra实际查看原 `states/preparation-amount-IME/window.png`：右侧输入被局部viewport裁切，两个按钮纵向占据剩余右栏；左侧批次仍在原双栏。原几何amount y256..373高117、support-scroll可见y270..352高82；稳定观测无pendingLayout且before/after一致。workspace424、pane上下padding72、固定actions252、dock前margin18 => viewport82，差35px。原宿主font_scale absent恢复absent成功，独立保留。

## 2. 设计取舍与冻结variant

**选：双动作在IME受限状态同行。** 不选逐像素削pane padding：会用断言缺口反推零散数值且改变既定内容边距。不选按高度撤销双栏：现有窄高纵向堆叠更挤，且改变列表上下文。不隐藏返回、不把固定动作放进滚动内容、不缩字。

| 项目 | 本次决定 |
|---|---|
| 触发 | API30+真实 `rootWindowInsets.isVisible(Type.ime())` 为true；原width-first split为true；`editor`存在且是当前编辑态；actions恰为原“保存选豆（不扣存）”与“返回冲煮页”两个按钮；原纵向自然预算不足；两按钮自然宽度+8dp能够放进innerWidth。必须同时满足，不按某台设备px或fontScale阈值硬触发。 |
| 位置/顺序 | 同一个actions容器固定在右栏底部，起始侧原保存主按钮，末端侧原返回次按钮。保原子序、回调、焦点对象；RTL采用start/end，不交换业务语义。 |
| 布局 | 主按钮占剩余宽，返回按完整文案/图标/原padding自然宽；二者间8dp。移除仅两按钮之间原纵向8dp margin，同行中垂直居中，不给单按钮额外top偏移。容器高取两按钮按实际分配宽测出的最大自然高。 |
| 尺寸 | 主按钮保持至少56dp，返回至少48dp；字形、图标、内距不变，不能为了同行强制单行/ellipsis或缩宽裁字。自然宽不足则不启用本variant，记录不满足，不能通过隐藏或裁切制造适配成功。 |
| 字体/色 | 原动作16sp/500/24sp、冻结Noto Sans SC资产与无伪粗字距0；主底 `#087A78`/白字，次动作及描边沿原light/dark token。无新背景、状态色或浮层。输入仍16sp/原52dp下限，单位14sp原baseline；数据和精度不动。 |
| 其余间距 | 两pane四边16dp、栏间24dp、内容到dock8dp全部不动。输入label到控件8dp、局部正文分组和批次72dp触控均不动。 |
| 收益 | 当前纵向actions252px=主126+次108+间隔18；若同行自然高126，则viewport变为424−72−126−18=208px，容纳原117px输入。该算式是原件上的预计收益，不能代替新图验证。 |
| 生命周期 | 键盘隐藏/不再编辑/宽度不够/纵向预算重新充足时恢复原纵向参数；原draft、selection、nextEventId、focus/input connection不重建。不主动show/hideIME，不发键盘动作，不以初始滚动更改门禁。 |

## 3. 有限实施任务

Sol owner仅：

- `mobile/src/main/kotlin/io/openhoyi/mobile/BeanPreparationActivity.kt` 中私有 `PreparationWorkspace` 及编辑态注册两个原按钮的最小接线。
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt` 中**现有** amount IME/恢复路径必要预算证据。没有新业务场景/新语言矩阵。

不得修改 `BeanPreparation.kt`、库存/消费策略、ScaleOwner、全局style/insets、其它Activity或共用布局默认值。

- [ ] 明确登记编辑态这两个按钮，不依赖当前本地化字符串比较，也不把其它确认/放弃/未知恢复态的任意两按钮误判为此variant。
- [ ] 每次measure独立计算**原纵向基准**：两按钮按原innerWidth分别自然测量，主/次原margin计入，原pane16dp计入；不能用上次横向actions高度作为判定输入，避免横向后变“预算足够”又翻回纵向的测量循环。
- [ ] 用真实按钮文本/图标/compound padding/minimumWidth测自然宽；主按钮剩余宽必须≥其自然宽，返回宽≥自然宽，间隔固定8dp。若候选成立，再按分配宽各自测自然高，检查同行后的support预算≥editor实际自然最小高（原max52dp逻辑保留）。只有候选实际也足够才启用。
- [ ] 在同一容器更新orientation和原子LayoutParams，不remove/re-add输入或按钮。纵向分支显式恢复宽match、weight0、首项margin0/第二项top8dp、start/end0；同行分支首项剩余width/weight，次项自然width、start8dp、top0。保祖先forceLayout链，避免探测量污染真正布局缓存。
- [ ] 读取已有rootWindowInsets；不覆盖 `TaskProductVisualStyle.page` 的消费/inset listener。原ADJUST_RESIZE引发measure时更新状态；若另需监听只在本私有workspace附加、不消费、不更改共享root回调。不注册长期动画/轮询。
- [ ] `productLayoutEvidence`记录原纵向required、IME真实值、candidate widths/自然高、是否启用、未启用原因、最终actions rect/height、support预算。无论失败都保原件，不输出一个伪通过常量。
- [ ] 原 `whole(observation(amount))`、固定动作完整、草稿值/选区/批次/eventId恢复、保存选择不扣减及后续业务断言全保留。仅在原IME检查旁记录variant和测量；键盘收起后的原恢复检查核实纵向参数还原。不加sleep、重试、mask或容差。

## 4. 有限验证与验收

1. Sol静态核 diff、按钮/输入对象身份及原监听未变；`git diff --check` 应0，交精确文件SHA。不是新库存算法，不重复添加镜像模型测试。
2. root统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，保原失败日志、新构建日志与APK SHA。普通推送工作分支后沿现有Bean CI检查，不新开设备执行或改变fixture。
3. 精确新SHA原 amount-IME 图必须证：真实输入完整、字/单位/边框全可见；同行两个原操作完整可触达，主次清楚、不覆盖support；原panel16/栏24/dock8不变；原input及动作自然高不降。截图仍原5秒四零，失败不能删。
4. 原IME收起/草稿/重建/库存无写恢复证据保持；普通主图不应变成永久同行。其它配置用原已授权任务产物验，不从当前1.0预计收益外推大字/其他宽度通过。
5. 如果自然宽条件在实际原失败配置不成立，或同行后仍无法容纳真实editor，停止这一候选并带实际测量给Astra，不顺带削padding或扩大成通用紧凑框架；这不是允许接受失败，而是避免无证据叠加第二种设计。

不涉及咖啡机/秤命令、BLE、库存扣减、确认门禁、队列或未知记录。回退只revert本页布局/证据增量，保留真实草稿/事件/用户数据，不能清库恢复。root按PR必需CI与评审合并；Astra/Sol不独自提交推送。当前是设计交付，未实施/运行/视觉验收。

## 5. 原失败配置同行宽度可行性核对

2026-10-24，规划后按root要求定向复核原geometry，不改源码。**613px只是amount输入框，不能拿来作为actions的innerWidth。** 原 `productLayoutEvidence.secondaryWidthPx=1154`、pane左右共72px，因此actions内宽1082px；两按钮原rect分别 `[1406,370,2488,496]` / `[1406,514,2488,622]`，宽均1082。

保存文案原Layout horizontal line right667−left307=360px，返回577−397=180px；两按钮 `contentWidthPx=974`，相对View宽1082的非文字水平占用各108px，包含已有compound占位。原单行内容所需宽估算 `(360+108)+(180+108)+18=774px`，小于实际dock1082px，余308px。这排除了“原失败配置因把amount613当dock宽导致候选必不成立”的误读；它不是运行时按钮UNSPEC测量结果，背景/控件minimum等仍由计划内真实自然测量验证，不伪称新UI已通过。

## 6. 冻结源码静态验收（2026-10-24）

**增量修正后限定静态验收通过，交 root 统一构建；新CI与原图验收尚未完成。** Astra不运行Gradle/设备。

初版review提出三处：minSdk26而IME API无30守卫；横排未显式垂直居中/关闭baseline对齐；fixture对所有配置无条件要求横排。root首构建另发现LayoutParams apply内width/height被外层val遮蔽，编译失败保留在 `build.log`；此编译点不是初次静态审已捕获的问题，不掩去首败。

修正后实际核对：

- SDK>=30短路后才读Type.ime/isVisible，26–29保持原纵排；不修改全局Insets或主动IME行为。
- 注册的是编辑分支原save/back对象，不靠文案搜索；两个按钮原监听、输入对象、prefs/库存/事件ID全未改。
- 每轮先以真实原纵向参数测基准，自然宽用当前控件UNSPEC/minWidth，按剩余实际宽重测自然高；最后真实dock测量再次检查预算。不以amount613误作innerWidth，不以旧横排预算触发模式反复切换。
- 横排CENTER_VERTICAL+baselineAligned=false；纵排恢复保存的原gravity/baseline。LayoutParams显式`this.width/this.height`，start/end margin按layoutDirection解析，横排仅返回start8dp、无单侧top偏移；纵排首top0/次top8dp、widthMATCH、weight0。探测量后原editor祖先forceLayout链保留，动作子控件和dock同样forceLayout；没有reparent或reset scroll。
- 证据从本轮measure记录候选与最终预算，onLayout再记录最终screen rect/height；fixture按真实split和原纵向是否不足要求对应orientation，不把所有配置必须横排作为新门槛。原whole(amount)、两固定按钮完整且56/48dp、预算足够始终保留；自然宽不足不能借分支豁免接受。键盘收起经原unfocus条件+committedFrame再核纵排参数。
- API时点采用原ADJUST_RESIZE触发布局与现有rootWindowInsets，不新增轮询、sleep、mask或截图特殊处理。静态不等于该时点已在设备验过，留原CI整条业务路径验证。

独立重算工作树SHA256：

| 文件 | 冻结SHA256 |
|---|---|
| `mobile/src/main/kotlin/io/openhoyi/mobile/BeanPreparationActivity.kt` | `c165b37a82603b4e0ead26dc0254c473cb1d856efe656bee01f366bd350607fb` |
| `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt` | `f7a6c1ab25ed6a6bac81caf72303a86bd0cdf7143760322ce8ddd303fd3e3e60` |

`git diff --check` 返回0。root重试构建日志另保留 `build-retry.log`，这里不提前声称构建成功；旧amount82px裁切原件及恢复事实继续保全。

## Root 构建结果

第二次`:mobile:assembleMock :mobile:assembleMockAndroidTest`退出0，1m3s，91 tasks（9 executed）。首失败日志保留 `build/task-first-evidence/ui-2026-10-24-bean-ime/build.log`；新日志 `build-retry.log`。303个Kotlin源码构建前后SHA一致，见同目录source-retry-before/source-after.json。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA `13ab62a396e2aef624302b5dbb4626222642436240937b46c32ef7f6b8e634ce`。
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA `950d177683b99704784cd18d813bbfe3e1a56853ad40d9250989a00e9f5eef5c`。

仅编译与静态复核通过，原amount-IME完整可见和恢复门槛待精确新CI图/测量验证；不因预计208px而签实际通过。未安装、设备操作、合并或发版。
