# 三页原生视觉样张最小计划

2026-10-18。Astra负责设计和验收，Sol负责实现与取证。本计划仅定义下一批，不代表已开始开发或样张通过。

## 范围与Git边界

- 需求：把[严格视觉基线补充草案](../superpowers/specs/2026-10-18-strict-visual-baseline.md)中的字体、字号/行高、间距、背景及状态色制作成可审阅原生样张，再决定如何用于产品。用户要求这四项逐页严格对照，不能只看可用性或无裁切。
- 来源/工作分支：沿用既有 `feature/task-first-ui`，目标 `develop`、PR #4；本轮规划读取基线为`116d19d`，已知后续footer提交`a7fcc86`。Sol实施前重新记录实际HEAD和未提交文件，不覆盖其他人的并行修改。既有develop及依赖来源沿用主任务规划。
- 文件所有权：Astra本次只写本文，不提交推送；下面列出的Mock样张及测试文件由Sol后续明确领取。严格基线spec已移交主任务，不在本任务修改。
- 推送/合并：本地实现及验证完成，由主任务合批普通推送工作分支；遵守[Git工作流](../git-workflow.md)及[任务模板](task-template.md)。本计划不授权合并、绕过必需CI或取消/重启在途CI。
- 协议/设备：纯展示、不启动BLE、不连接服务、不写配方/库存/偏好、不执行开始/停止/归零/确认/分享/导出。现有未知记录和门禁不变。
- 发布/升级：不发版、不安装真实平板、不改包名、签名、versionCode、schema。运行仅限既有CI Mock模拟器取证；不新增发版类型。

## 1. 选定实现：独立Mock展示页，不全局改主题

新增一个只编入`mock`构建类型的原生`StrictVisualSampleActivity`，`exported=false`，不加入产品导航。Activity只接收三个白名单fixture id和候选版本id；读取不可变内存展示模型，使用原生View绘制。所有操作是视觉样本，无业务回调；底部按钮显示设计状态，但不得绑定任何设备、库存或外发动作。

候选tokens独立放在`mobile/src/mock/kotlin/io/openhoyi/mobile/StrictVisualSampleTokens.kt`；颜色若用资源，新增`sample_*`名称，禁止覆盖现有`mobile_*`资源。使用固定中文字体文件及`TaskTypography.face`，字号/字重/行高、Shape和Paint显式读取候选tokens。不得修改`TaskVisualTokens`、全局主题、`mobile/src/main/res/values*/colors.xml`或真实Activity来生成第一批样张。

复用现有纯展示/纯计算能力：`CurveStageParameters.pressureFraction`、`StageAxisTicks`等。若现有View硬编码读取产品颜色或字号，样张使用Mock专用绘制适配器并复用投影函数；不为样张给生产View新增全局可变开关，不通过反射改生产Paint，不把PNG当绘图背景。原生排版使用真实字体测量，不能手写图片文字或将控件栅格化贴回界面。

这是**候选原生设计样张**，不是实际Home/Share/Inventory页面验收。manifest及对照报告必须标`specimenOnly=true`、`productAcceptance=false`。后续候选通过后，真实产品页面仍需独立同状态验证；不能把样张PNG直接归为产品已一致。设计说明放在报告/联系表标题，不挤占被比较的应用内容区域。

## 2. 三个确定fixture

所有名称、id、日期和数值固定，不使用UUID或当前时间。数据全部为展示fixture，不插入用户存储，不修改原稿数据来源规则。

| fixture id | 原稿 | 确定状态/字段 |
|---|---|---|
| `preparation-confirmed-four-stage` | `brew-preparation-v2.png` | 标题“冲煮准备”；曲线“经典浓缩”、93°C、目标36g、4阶段压力2/9/8/6bar；本杯“哥伦比亚 水洗”、已确认18.0g；模拟咖啡机就绪/秤数据实时；单一主动作“开始萃取”及既有再次确认说明。最近使用3行：经典浓缩93°C/36g/4阶段、浅烘长萃91°C/40g/3阶段、柔和降压92°C/36g/3阶段。只展示真实定义过的字段，不编造未来轨迹或真实设备连接 |
| `share-four-stage-not-exported` | `curve-share-v1.png` | 曲线“经典浓缩”、压力模式、93°C、目标36.0g；四阶段压力3/9/7/5bar，注水15/25/15/5ml；兼容说明保持真实语义；分享文本主操作、复制次操作、导出文字操作、已导出文件分享禁用；完整参数入口和隐私范围说明保留；无有效导出URI |
| `inventory-three-beans-selected` | `bean-inventory-v3.png` | 3款总500g：哥伦比亚 水洗90g、耶加雪菲 日晒160g、巴西 拼配250g；第一款选中，批次1，初始100g/剩余90g/90%；购入2026-10-02、烘焙2026-09-28、开封2026-10-03；最近流水调整-10g/原因盘点修正、入库+100g/原因—；不得加虚构流水时间戳或本杯归属 |

库存动作文案/入口若原稿与真实操作语义不完全相同，报告必须同时列出原稿意图和原产品动作，不得把“选豆与投粉确认”伪装成点击即扣豆。分享兼容说明不能借排版被删，位置按原稿信息优先级安排；如必须偏离，提交具体差异而非默默接受。

准备fixture是明确的模拟已确认状态，因此能与原稿比较；不得用现有capture-1两阶段/未选豆/“查看萃取准备”的截图冒充该状态。此fixture不验证真正的启动门禁。

## 3. 候选tokens与原稿锚点

候选版本为`strict-visual-draft-2026-10-18`，逐项抄录spec第3节的主动设计补充值，并保存机器可读快照及SHA。字体身份、字重、行高和色板均保持`proposed`；禁止从现有截图自动提取值后写成approved。首轮只实现这三个样张使用到的角色，不扩展24页产品UI。

每个原稿保存原文件SHA、像素尺寸和锚点标注JSON。以下是必须落地的锚点映射，不是允许实施者自行忽略的参考：

| 页 | 原稿锚点 | 原生语义标识/对照内容 |
|---|---|---|
| 共用 | 导航右边界、标题基线、页面内容起点、选中项矩形、底部工具区 | `sample/nav/rail`、`sample/page/title`、`sample/page/content`、`sample/nav/selected`、`sample/nav/tools`；标题与导航层级、颜色和间距独立比较 |
| 准备 | 左配方名称与右“本杯准备”标题；摘要同行；图表绘图区；最近使用标题/三行；右栏分隔；主操作 | `sample/preparation/recipe`、`summary`、`stage/plot`、`recent`、`cup`、`primary`；压力全部蓝色，最近使用在主区，不能复刻当前实现的右侧大按钮 |
| 分享 | 左右卡片外框；温度/液重摘要；图标题/绘图区/说明；四行参数表；分享方式标题/主动作/复制/导出组 | `sample/share/recipePane`、`summary`、`stage/plot`、`table`、`actionsPane`、`primary`、`secondary`、`exportGroup`；兼容/详情不能抢占主操作前大段空间 |
| 库存 | 头部统计；列表/详情分隔；选中行；主重量/单位；比例条；三日期；两流水；单行操作区 | `sample/inventory/header`、`list`、`selected`、`detail`、`weight`、`progress`、`dates`、`ledger`、`actions`；整页平面分区，不给每字段另造卡片 |

原稿锚点在原始PNG上标注，输出精确坐标并由Astra复核；不能把本计划的语义锚点冒充已经完成的逐像素标注。对照时保留原图，不非等比拉伸。目标是用户窗口的实际应用可用区；原稿无系统栏，画布高度不能直接按全屏比例塞入应用区。

首轮按标准横屏测量原生布局，自上而下满足字体/行高、原稿顺序与48dp触控。高度不足时记录每区自然高度、可用高度和差额，保持允许的局部列表滚动并标出相对原稿的差异；不缩字、不删表格行、不隐去说明、不自动放宽门禁。Astra根据实图决定候选排布修订，再冻结。原生候选若无法满足四项，结果就是未通过，不先传播到产品。

## 4. 最小文件与顺序

建议Sol负责以下文件；实施前检查是否存在并适应并行修改：

1. `mobile/src/mock/kotlin/io/openhoyi/mobile/StrictVisualSampleTokens.kt`、`StrictVisualSampleFixtures.kt`、`StrictVisualSampleActivity.kt`及必需的Mock专用绘制适配器；仅在`mobile/src/mock/AndroidManifest.xml`声明独立页面。不要修改main manifest或buildTypes。
2. `mobile/src/androidTest/kotlin/io/openhoyi/mobile/StrictVisualSampleChecks.kt`；给既有`BrewAudioInstrumentation`增加显式`strictVisualSamples`白名单入口，默认测试行为不变。当前颜色取证属于共享并行文件，修改前与主任务协调，不覆盖他人代码。
3. `scripts/check_strict_native_samples.sh`，由主任务在现有CI框架中明确调用；不改旧门禁数量、全量通过定义或硬件脚本。取证manifest、锚点JSON和对照报告随artifact保存，源代码目录不写生成PNG。

先完成一个准备样张的浅色1.0实际布局及参数报告，确认取证接口正确，再补其余两页和配置。不要先做24页或全局主题重构。首轮视觉评审前不把任何候选值应用到真实产品页。

## 5. CI取证与验证

运行前强制确认`BuildConfig.MOCK_MODE`与`io.openhoyi.mobile.mock`，仅在CI模拟器启动样张Activity，不启动服务/命令/系统分享界面。取证前后核对现有偏好、库存事件、配方及准备上下文未变；失败也不得清除未知记录或重置非本任务数据。mock展示页不持有持久化仓库。

首轮共12张整窗图：3页×浅/深色×fontScale1.0/1.3。全部固定用户profile `2560×1600/density360/zh-Hans`；设置系统栏状态后记录实际Insets和可用窗口，布局完成且稳定后使用`uiAutomation.takeScreenshot()`，不对图片缩放。对同一profile，窗口几何不一致则停止对应比较并记录原因，不能用截图拉伸补齐。

每张同时保存：源码/候选版本/字体/原稿SHA、APK SHA、CI run/job、fixture完整输入、窗口/字号/系统镜像/Insets、原始PNG、G1–G4参数JSON、锚点实际值与差异报告。沿用`TaskTypographyEvidence`和新增颜色取证；其独立Drawable ARGB不当屏幕合成色，`unknown`不自动通过。样张自有Canvas直接输出Paint颜色/字号/实际文本bounds及坐标，避免沿用当前Canvas未采集缺口。

输出目录：

```text
build/task-first-evidence/strict-native-samples/<source-sha>/<profile>/<fixture>/
  manifest.json
  window.png
  visual.json
  anchors.json
  comparison.md
```

本地验证为Mock/AndroidTest编译、脚本语法、diff检查及fixture断言；不得称为视觉通过。CI检查原生文字完整、触控尺寸、四阶段/两流水/摘要内容、无业务回调、状态与存储不变。Astra逐图核对：G1间距/构图、G2字体/字重/单位、G3字号/行高、G4背景/边框/选中/禁用等颜色。像素抗锯齿仅在参数和几何相同、差异确在覆盖率边缘时单列，不使用任意容差或模糊图自动通过。

系统最大字号、短高、IME属于24页后续正式验收，首轮12样张不能覆盖或取消这些要求。此处三个页不是输入表单，不为凑IME图片添加虚假输入框。

## 6. 交付、冻结与回退

交付一份联系表：原稿、同状态候选浅色1.0、逐项差异；再附深色/1.3适配图。候选字体身份/色板说明置于报告，明确哪些来自批准规格、哪些为主动设计补充、哪些仍未通过。无需向用户逐项询问参数；必要审阅应面对这个具体结果。

Astra只在三样张的G1–G4均有明确结论后决定冻结共享参数。若只通过两页，第三页保持失败，不用平均评分。冻结结果不等于24页产品实现完成；Sol随后另接真实页面迁移任务和业务回归。

回退：移除新增Mock样张入口、Mock文件、测试分支及脚本即可；不影响main产品颜色、布局或存储，不卸载、不清历史、不降级。仍未完成项为候选原生样张、锚点标注、12张CI证据及Astra逐项验收；本计划不宣称这些已存在。

## 2026-10-18 本批实现与验证事实

- Sol 已补齐三种白名单内存 fixture、Mock 专用 Activity/tokens/图表与图标适配器、Mock manifest、instrumentation 入口及 `check_strict_native_samples.sh`，接入既有 task-frame workflow。没有修改 main 产品主题、资源或业务。提交前基线 HEAD 为 `2947e018a45b6e4d41c3b3f585af8b9ff146d9c6`，复用既有工作树。
- Astra 已完成独立原稿锚点 JSON（三页108项，`pendingPages=[]`），并对本批静态代码及最后两处 recent 门禁复核，允许进入 CI；此结论不是原生样张 G1–G4 视觉通过。
- 最终 Mock 与 AndroidTest 编译命令：`ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`。结果 exit 0，`BUILD SUCCESSFUL in 23s`，`91 actionable tasks: 4 executed, 87 up-to-date`；日志 `/tmp/2026-10-18-strict-native-gates-build.log`。
- `git diff --check`、脚本 `bash -n`、三段内嵌 Python 的 AST 语法检查通过。本地没有安装、启动模拟器/设备或执行 instrumentation；fixture 运行断言、实际基线/字号/滚动预算和12张整窗图仍待 CI。
- 取证保留原有 unknown 字段；样张自有 Canvas 记录真实 Paint、坐标和文字 bounds，但不覆盖产品 Canvas。独立 Drawable ARGB 不是最终屏幕合成色，G1–G4 仍未通过。准备 recent 若实测不足完整行或不可逐行滚达，会先保存现场，并保留12配置后使 CI 失败，不缩字、不降低门禁。
