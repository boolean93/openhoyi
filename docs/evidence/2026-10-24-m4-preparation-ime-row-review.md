# M4 amount-IME四配置限定图审

## 结论

四张新PNG已逐张实看，当前实际窗口中的117px输入、保存/返回文字与触点完整，浅深×1.0/1.3所见区域可接受。**四图仍为纵排，未触发紧凑同行；不能用本批关闭旧424px workspace窗口下的同行修复验证。** 本run workspace增至484px，多60px，原纵向预算已足够。宿主font_scale恢复失败仍独立，不签整Bean。

## 原件与协议边界

run40056061748，artifact12545524403，source/head `2f784ec502b8d0651e02e1de8cc86db8714d4ac2`。原ZIP `ci-2f784ec/bean-artifact.zip` 115,491,719B，SHA `0cfc5e84ff8ef74cf16cfc79cd973e434b605ab8e3eb2d384349852338f127a0`；沿root remote digest/1233 entries CRC记录。仅选32文件33,167,944B，路径/member/SHA索引 `ci-2f784ec/amount-ime-selected/index.json`；未整包解压。选出的geometry/typography/stability/business/主manifest和主proof均按manifest文件SHA核对。

**IME geometry协议明确为 `unsynchronized_IME_diagnostic_not_main_protocol2`，sameCompositorFrameProven=false。** 四组before==after，稳定三帧consecutive1/2/3、pendingLayout=false、withinBudget=true。可以结合实图与稳定几何作本限定观察，不能把主态window-frame-sync的5秒四零搬来冒充IME帧同帧证明。IME实际visible=true，图片确见系统键盘。

## G1–G4与实际预算

- 四图维持左右两栏、输入标签/数字/单位分组；左侧选中哥伦比亚水洗/批次1/214g清楚，批次列表下部仍属滚动边缘，不签所有批次可见。
- amount自然高117px=52dp，rect==visibleRect：1.0 `[1875,295,2488,412]`，1.3 `[2035,295,2488,412]`。单位g完整，18文字与选中区域可见，没有旧输入上下被截问题。输入字体36/45.45px、400；保存/返回36/45.45px、500，未缩字号。
- workspace `[216,234,2524,718]` 高484；右pane `[1370,234,2524,718]`，左右padding36px=16dp；两栏间54px=24dp。support-scroll `[1406,270,2488,412]` 高142px，≥117px；actions `[1406,430,2488,682]`。dock前18px=8dp，按钮间18px=8dp。
- 保存按钮 `[1406,430,2488,556]` 高126px=56dp；返回 `[1406,574,2488,682]` 高108px=48dp，四档文字/图标完整、全部在键盘之上，仍上下两行。此处无需强行开启同行，符合“仅纵向不足才紧凑”的设计。
- 浅主字#101820/深#EDF3F2，保存白字青底，返回浅#087A78/深#83D6D0；选中底/输入描边/正文层次清楚，未见新增遮挡或数字单位碰撞。未签系统键盘字体/配色为产品规格。

新484−72(padding)−252(actions)−18(dock前)=142px；旧424−72−252−18=82px。变化来自实际可用窗口，不是这四图证明同行腾出空间。选包geometry没有持久化productLayoutEvidence预算字段，不能伪造某个actionsCompactReason实测值；“纵向已满足、不需同行”由实际几何和执行合同共同支持。

## 原业务边界

四配置 `bean-workflow-business.json` completed=true；`real-choice-and-draft-batch-amount-event-id-recreate-no-write` 与 `actual-amount-IME-local-reveal-and-fixed-actions` 两阶段余额均214000mg，events完全相同（既有ADD和两次CONSUME），intent=null。原对应fixture在第一阶段核替代批次草稿重建、amount=18、nextEventId不变，然后回选主批次；IME前实际requestFocus并setSelection(0,1)，实图也见首字符选择。后阶段到达意味着原whole(amount)、56/48dp完整操作、预算/guard及收键盘恢复纵排断言已越过。

原business文件不保存光标selectionStart/End字段，不扩大为“IME后选区严格恢复已独立证明”；只记录原操作和可见选择。无设备/服务命令及无新增库存写入限于此阶段，不能把后续显式确认业务中的合法扣减混作IME写入，也不由此签整个Bean。

## 四张PNG SHA256

| 配置 | SHA256 |
|---|---|
| dark1.0 | `5d7ab3e20a1b42695e44115ec93b384f346c3adda0a4d225f1534ca98d8b32b0` |
| dark1.3 | `cbce79f3a4d8e17e0669e1fe143a7712c478b48a5425e192a41816c4a08b9953` |
| light1.0 | `c5bfe1eac806608dc14a67e8cf999bbbaaf3557d1aff347d77c711c31b7c3264` |
| light1.3 | `b1c7e017f9ea6fba3f7ea31ba1cc1efb06ec1c2baa76f0068426f1b4ab88cfe2` |

## 最小后续补证建议（仅规划）

只复现旧失败light1.0的实际workspace高424px、右pane宽1154px（inner1082px）这一已知预算，在受控Activity窗口下保持真实IME显示/编辑焦点/原具名控件，不改系统字体、文本、padding或48/56/52dp门槛。先记录实际窗口/insets与workspace，目标预算未达到则明确未复现，不靠截图缩放或伪造geometry凑424。用现有guard验证纵向required459>424、紧凑实际开启；核117px输入、两原按钮同行完整、自然宽条件、草稿/eventID/no-write及收键盘纵排还原。采同一既有IME诊断/稳定证据并保持其非protocol2边界；不扩大语言/主题/设备矩阵、不加sleep/重试或新通用遥测。本轮未改测试或执行此补证。
