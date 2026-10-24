# CurveActivity 浅色1.0限定图审

Astra，2026-10-24。source `58ae69ed99c1bbde98311fed24c8bf9c056b9134`，push run `39915609521`。仅 `tablet-user-landscape-light-font-1.0/library`，不签全 M3 或四配置。

原件 `build/task-first-evidence/ci-58ae69e/curve-artifact.zip`，定向提取该页到 `library-light1-review/`。实际看了批准 `curve-library-v2.png` 与六张原 PNG：`window.png`、`states/non-first-selection/window.png`、`states/list-restored/window.png`、`library-special-scope/preset-eligibility-and-slots/{chooser,slot-5,restored}/window.png`。主图 SHA256 `cf4ec0f76a09401202cd61f20d401e71f2c15d89bfacc872f57362fa4df79bb0` 与 manifest 对应值一致。原尺寸2560×1600，density360、font1.0、zh-Hans；只用对应真实测量解释画面，不以 manifest 的机械 PASS 代替看图。

## 已接受范围

主态、非首项选择与列表恢复三图的 G1–G4 **LIMITED_PASS**：

- 左侧搜索/管理、排序、横向分类、数量、连续列表完整，右侧名称/来源/资格、三列数值、真实压力图和固定双操作保留批准构图。browser `[216,254,1000,1429]`、detail `[1054,254,2524,1429]`，栏间54px=24dp；左右页内距36px=16dp。原列表约34%保留。搜索117px=52dp自然高；底部详情/使用按钮完整，无旧同行外框越界。
- 原图中字形、粗细、数字单位层次清楚，无乱码或重叠。实际字体SHA为冻结 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`；title54px=24sp、section/metric45px=20sp、action/row36px=16sp、aux31.5px=14sp。93/℃与36.00/g两组baseline均585px、delta0。未反推未知概念图字体身份。
- 主图四阶段2/9/8/6bar与后续两阶段2/9bar、0/6/12轴字完整可读，未伪造时间轴。Canvas title到最高刻度18.719px、plot到阶段字24.925px，均超过既定18px间隔；保护区2dp保持。可见正文/辅助灰、青绿选中/主操作和蓝压力线与冻结浅色层次一致。
- 非首项图是query `factory-v3`、DARK、MOST_USED 的25项连续列表；选中factory-v3-008。上边缘半行是已有scroll位置，不是固定详情裁切。恢复图仍同选中项/列表位置。业务证据before/afterDetailsReturn/afterRecreate均position2、offset−65；只改selected，无机器命令、usage与其它模型未变。此只证明既定Mock流程，不是机器验收。

主图捕获第一attempt曾S1_S2=252而拒绝；同一5s内第二attempt accepted，S1_W1/S1_W2/S1_S2/D1_D2_app均0。没有忽略失败attempt或降低四零门槛。

## 槽位图的明确边界

chooser原图标题及槽位1–5完整，原生Dialog文字可辨、白卡/暗背景层次可接受，仅此弹窗画面。**slot-5与restored不能签固定底栏完整**：slot-5画面仍有“已将Espresso深烘放入槽位3”Toast，restored的只读FlowRaw画面仍有槽位4Toast，均覆盖底部详情区域。这是实看原图所见的旧提示残留，不由slot文件名推定提示应属于该槽，也不据此断言槽位写错；不在本批擅改产品/采集器。

其余slot1–4、路由target/restored、特殊长标题/只读/无压力/短窗与逐行滚达原图虽然已在ZIP内，**本次尚未看完，保持未验收**。已提取该页66张window.png不等于已审66张。其余三主配置、Import/Preview、原IME失败均未被本报告关闭。下一步继续现有原件增量图审，不新增场景或门禁。


## 同日增量：五条来源路由十张原图

继续逐张实看 `library-special-scope/library-source-routes/routes/{edit-copy-factory,edit-draft,import-page,legacy-page,share-page}/{target,restored}/window.png`，没有重复首轮六张。五张 restored 的库页 G1–G4 LIMITED_PASS：均无 Toast 遮挡，固定详情/使用操作、阶段标签和正文说明完整；browser/detail/search 实测边界均与本报告主态相同，24dp栏距与52dp搜索保持。工厂两阶段、草稿四阶段的真实数值和来源/可用性区别清楚；标题、列表、辅助行、单位的字形及浅色层次未见变化。长 UUID query 在原单行输入里局部显示，不将它误作丢失全文或改变搜索模型。

五路业务 `before == returned` 均精确相等（query/category/sort/selected/count/position/offset）；edit-copy回factory-v3-001，edit-draft/share回原draft，import/legacy回105项的当前scope列表。该105来自这一路由scope，不拿它与最初104直接宣称增加了用户记录。状态快照为返回捕获后的main读取，`sameCompositorFrameProven=false`保留；不包装成帧内业务因果证明。十张frame-sync各有accepted attempt，原5s内四项差异均0。

目标页仅作**本次入口到达画面的有限视觉核验**，不替代M1/M2/M7独立全页/特殊态/IME恢复验收：

| 路由目标 | 实图结果 |
|---|---|
| edit-copy-factory | Espresso深烘，92℃/36g，2阶段9bar/50ml与6bar/20ml；副本边界说明完整，输入/图/固定取消保存无覆盖，未称已保存或覆盖原配方 |
| edit-draft | 经典浓缩，93℃/36g，4阶段完整输入与预览同屏；四色标识和阶段对应清楚，末行、轴字、页脚完整；本图无IME，不关闭M2重建失败 |
| import-page | 空输入placeholder、等宽两个来源按钮、JSON标题与辅助说明、底部取消/禁用预览完整；没有填入假示例，不外推输入后错误/SAF/IME |
| legacy-page | 只读标识、左列表/右四参数卡、限制说明和查看详情完整；未见文字重叠/异常色，不据此签M7正式四配置与短窗 |
| share-page | 四阶段压力图与五行参数表完整，右侧分享/复制/导出及禁用的已导出文件分享层次明确，底部隐私说明两行完整；沿M1既定接受边界，不重开全模块 |

当前累计看了 **16/66** 张 `window.png`。剩余 **50张未看**：槽位1–4共4张；特殊态 `captured`、`four-stage-pressure`、`long-factory`、`no-pressure`、`readonly-flow` 各6张，以及 `short-pressure` 16张，共46张。首轮slot-5/restored已看但因Toast覆盖不接受的边界仍有效，不能计入50张未看，也不能签过。

新增十张SHA256（相对于上列routes目录）：

- `edit-copy-factory/restored/window.png`：`9f565017f514ebeb16f5624bf2282a9d882381ebae1679224c8e0fbeb3672de9`
- `edit-copy-factory/target/window.png`：`2b82b4457c0756725ec539085e0219c7ed44f2e5e5216a39c021c5513d6c31c3`
- `edit-draft/restored/window.png`：`167e86b880836545ee78332b283f4270703f510fad0755a195eafec7811635b9`
- `edit-draft/target/window.png`：`818325d3d911c2d72609328117ca9a69ef9310e9665b7fa3bb416d34e39f3c59`
- `import-page/restored/window.png`：`31c94463d1dd1f52fa307bea984a88c7663c0b904698fff93e234cf98f1b192d`
- `import-page/target/window.png`：`e68c38e3ecadf1c918870211bf63f961c887f41c7bdadfeebb66d541bf2d99d1`
- `legacy-page/restored/window.png`：`31c94463d1dd1f52fa307bea984a88c7663c0b904698fff93e234cf98f1b192d`
- `legacy-page/target/window.png`：`9d86db399f24292fdf858f452b0cffea97ce06fb94ea86e3d178b96698c60867`
- `share-page/restored/window.png`：`167e86b880836545ee78332b283f4270703f510fad0755a195eafec7811635b9`
- `share-page/target/window.png`：`3056af38dfcc9c5081285f5e73fa99c90e075197840800dc1b4e777dc4a358cb`


## 同日收口：66个已产目标全部看完

本节覆盖并更新前两节的“尚未看完”清单。66个PNG共有45种SHA；对已看相同SHA复用像素审查，新增31种像素逐张查看，并仍逐目标核本身的frame-sync。**66/66目标已看，未看清单归零；不是66/66全部通过。** 完整路径/SHA索引保存在原证据目录 `library-light1-review/reviewed-image-index.json`。每个目标各有原5s内accepted attempt且S1_W1/S1_W2/S1_S2/D1_D2_app均0，相同PNG没有替代各自协议证据。

### 六特殊态的限定接受

`captured`、`four-stage-pressure`、`long-factory`、`no-pressure`、`readonly-flow` 各6个目标，`short-pressure` 16个目标，合46个目标；含startup/prepared/主态/details initial+restored/页面restored及短窗十个既有滚达目标。其已产范围 G1–G4 LIMITED_PASS：

- captured真实91℃、27g、2阶段9/6.5bar及采集来源清晰；四阶段草稿2/9/8/6bar保持四段对应。标题、数值、来源说明、禁用或可用操作没有覆盖，压力轴和阶段字完整。
- long-factory实际是“深烘拿铁”的长说明，不把文件名解释成已覆盖超长标题。其19行详情全部可读；no-pressure的HOYI Turbo Shot详情18行完整，目标液重明确“不使用”，无假0或假压力图；readonly-flow明确只读且使用/编辑禁用，四行原始参数不冒称bar。各默认页的无压力说明一行完整、底栏不被挤走。原生Dialog保留自身字体和蓝色确定按钮，文字/留白/灰遮罩可辨，不硬套产品按钮色。
- 六份details-report的coveredLines分别为8/9/19/18/9/9行全集，原scroll与恢复均[0,0]；实图也已逐种看完，并非仅据行数签章。六case restorationMismatches均空。
- 短窗使用批准的紧凑详情与文字fallback，未把未显示图签成图表通过。13行内容全部在初态/十个滚后目标内完整出现：名称、来源、93℃、36g、4阶段、资格、图意说明及2/9/8/6bar全文；逐目标画面允许相邻非目标行在滚动边缘部分露出，当前目标完整。返回列表、编辑/分享/更多与固定详情/使用持续可见，末行也未盖住底栏。恢复回全高原窗口[-1,-1]，原Activity身份和原back路径保持；黑边是已有短窗口外区域，不是删内容或缩字。

中途工具缩放预览曾让long-factory/startup阶段1/2看似挤压；原2560图与轴字原尺寸裁片复核证明四标签等距完整，**不登记产品缺陷**。原件四零协议也通过。原图未修改，裁片仅用于检查。

### 保留的反馈缺口及设计判断

槽位1–5与槽位测试restored共6张均有Toast覆盖固定“曲线详情”区域，不能签固定底栏完整；其中slot3显示槽位2、slot4/5显示槽位3、restored显示槽位4，旧操作提示已跨进后续状态。chooser正常弹窗已接受。Sol的只读源码/原件核证表明各槽actualStoredId仍正确，故这里是**反馈时效与遮挡问题**，不是槽位数据误写。

现有证据已足够决定应让当前操作反馈替换旧反馈，并保证不遮挡固定操作；不必等待Toast系统队列机制全解才作UI决策。后续窄设计可选在本页固定、非遮挡的位置展示最新槽位确认，或采用确能替换旧提示且不遮挡操作的反馈方式；必须保持真实写入结果、无额外设备行为。此报告不实施、不批准仅为截图取消反馈，也不把等待旧提示消失当产品问题已经修复。

**最终范围：主态/选择恢复、五路到达与返回、六特殊态及其详情/短窗原件已审并按上述边界接受；槽位chooser接受，六张Toast遮挡反馈图未接受。** 其余三主配置未审，M2/Import原IME恢复FAIL及M1/M7各自未覆盖范围不变。未开始AppSettings下一批。
