# 任务工作台改版：实施证据

2026-10-15。此文件只记录实际完成的批次，不把依赖或概念图算成新UI完成。

## 分支与依赖基线

- 工作树：`openhoyi-task-first`，分支`feature/task-first-ui`，从`origin/develop`的`66ebda95dc7986eec66f9fa0b7e6cbd565c74506`创建。
- 显式本地依赖合并：`ed01de0f1b6f6449257e3480053340a9bb75b160`，随后合入已确认设计和计划；未合并、关闭或修改任何远端旧PR。
- 实施前基线提交：`6f4d10489e55308aae4f85240adfd91bdcd122d3`。该点的`mobile`、`bean-core`、`device-session`、`protocol-core`、`bluetooth-android`与`ed01de0`逐文件Git差异为空。
- 依赖相对develop的239文件差异不冒称为本轮新UI；本轮真正实现按`ed01de0..HEAD`单列。

## 本地单测基线

正常Gradle命令先受到缓存锁沙盒权限限制；获得构建缓存访问后，又因用户全局Gradle配置指向不可用的`127.0.0.1:7890`代理，无法下载Maven版aapt2。已核实本机SDK35的aapt2可执行，只对本次命令指定其路径并离线运行；未修改全局代理、项目Gradle依赖或签名配置。

```bash
./gradlew :mobile:testMockUnitTest --offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2 --no-daemon --console=plain
```

实际退出0，`BUILD SUCCESSFUL`。JUnit XML汇总63个测试类、266项、0失败、0错误、1跳过。已有Android API弃用及aapt2覆盖的实验选项警告仍存在，不声称无警告构建。忽略目录中的本机详细记录：`build/task-first-evidence/baseline.json`。

这是继承代码的JVM单测基线，不证明新导航、四指标图、21页、IME、设备控制或真实窗口已验证。没有安装APK或连接硬件。

## 当前交付边界

首批窗口策略已实现于`45494cb`，仅新增`TaskLayoutPolicy.kt`及其测试：600dp导航切换、840dp及实测内容约束的双栏许可、短高窗口工具迁移、320dp绘图区的轴布局切换。该提交没有Activity、服务、库存或回调依赖，当时尚未接入真实View。

实现者先运行新增测试，观察缺少策略类型/枚举的预期编译失败，再添加实现；定向测试7项通过，完整Mock单测XML汇总64个测试类、273项、0失败、0错误、1跳过。控制者独立读取XML确认这些计数。实际执行使用与基线相同的离线aapt2参数，不把原先受代理阻挡的普通命令记为通过。

独立规格审查通过；随后独立代码质量审查通过，无Critical/Important问题。一个非阻塞建议是增加精确内容fit差1dp及极大Int约束的防回归场景；当前实现已用Long计算且审查未发现溢出，此建议不代表现有用例覆盖了这些输入。

以上为首批策略交付时状态；后续进度见下。


## 共享导航与页面容器（本地与三窗口专项通过）

`56502ef`接入24个真实Activity的统一导航、电子秤/设置工具入口及青绿主题。原内容View和LayoutParams迁移，不新增整页滚动、不改业务/控制逻辑。按configuration窗口宽选择rail/bottom（避免原root padding影响600dp阈值），实际容器高度决定工具位置。

独立规格审查未发现确定缺陷。独立质量审查发现工具迁移时可能清除输入焦点，已修复为仅导航模式变化才重挂内容；工具区切换不动业务View。复查无剩余具体阻断项。其他语言工具文案按“优先中文”留待优化。

新增`TaskFrameChecks`：24页唯一Frame、主导航顺序/选中/48dp/完整可见、工具入口、实例和LayoutParams保留；实际点击主导航和工具菜单并等目标Activity；模拟IME压缩高度检查焦点。此处是已编译的检查代码，**尚未有真实窗口执行通过证据**。`noBle=true`仅表示隔离Mock，不能代替硬件验收或Mock业务写入计数。

本地完整命令：`:mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest`，沿用上文离线aapt2参数；实际`BUILD SUCCESSFUL in 43s`，JUnit汇总275项、0失败、0错误、1跳过。脚本`bash -n`及`git diff --check`通过。伪ADB测试证明非模拟器在第一个`getprop ro.kernel.qemu`之后立即拒绝，未调用显示修改/安装命令；没有操作真实平板。

新增CI三窗口：平板横屏2560×1600/density360、竖屏1600×2560/density360、手机450×1000/density200，保存实际24页截图和运行结果。源码c3a5675的PR工作流38727735636三窗口均实际成功，每窗24页与导航/焦点检查通过。取回72张真实截图，人工抽看五张（横屏Home/Extraction/Curve及竖屏/手机Extraction），见`task-first-ui-review.md`。四指标图与21页内容改版仍未完成；容器通过不能当总体UI通过。无发版、无用户设备安装。


## 四指标纯投影（294项本地JVM通过，Canvas尚未替换）

`7fae22e`新增纯模型，`555c356`修复质量审查发现的极大Float取整向内裁峰问题。完整19项专项含两个真实先失败后修复的边界回归；完整XML294项、0失败、0错误、1既有跳过，控制者独立核对。默认压力/累计注水/有值的秤读数/温度；不使用flow、不补0g，不跨缺口或水计数重置连线，参考不补本杯未来，量程覆盖负值并同杯只扩不缩。无服务/持久化/控制写入；独立规格审查、修复后质量复查通过。

## c3a5675原有回归的失败与处置

三窗口框架通过不等于全部CI通过。PR原有business路径与upgrade通过；verify因新增task_tools缺7语言被MissingTranslation阻断；lifecycle在Extraction点已选BREW后仍留Extraction，旧脚本却继续找Home设备连接；advanced-pages在俄语Scale粉量Capture中主重量被挤到29px、实际字体高53px而失败。

已补工具基本翻译（不关闭lint）；生命周期路径改用原生Back从Extraction返回Home，保留所有Activity/前后台边计数断言；秤页064655b将读数固定，次要说明/计时工具局部滚动，去皮/捕获固定；独立规格/质量审查通过，本地294项JVM及两assemble通过，仍待实屏重跑。尚未以新源码重跑这些失败，不宣称恢复绿色。四指标原生控件检查已先写，旧图没有该图例，准备记录真实失败再替换；预期的新增图表失败与原回归分开报告。

本地尝试lintDebug，但离线缓存缺少lint-gradle:31.10.0，在extractDebugAnnotations前失败，未进入新资源检查；不记为lint通过，待云端原verify检查。七语言新增资源已逐份XML解析成功，不等于lint验收。旧single-screen手机产物显示西班牙语旧ExtractionOverlay轴刻度0.0/3.0纵向重叠（334/336），不是框架专项漏报成功；新图必须按实际高度决定tick密度，原文字边界断言保留。


## 930c7ad第二轮云端结果与图表实施

原verify38730308188已成功，实际lint已通过MissingTranslation；upgrade亦成功。专项38730308099是先测的旧图red：三窗口frame24仍成功，而FourMetricChartChecks明确`machine-only: expected usable four-metric legend, got []`（logcat），不是业务控制失败。之后7cd5c89接入四指标native View/Canvas；e71cc8b依规格复查修正参考只画focus指标，避免八线；文字不透明、非焦点线alpha210。图表独立规格复查通过，质量/新源码编译/UIgreen继续验证中。

生命周期push38730299537已通过原前后台边计数、theme配置重建、clips14/本地反馈/页面离开音频检查，随后LanguageUiLayoutChecks仍假定root首子为旧bottom bar，报missing navigation entries；正在迁移结构/阈值测试，保持8语言/2主题/5宽/2字号、textFits负向fixture、48dp和停止断言。新导航真实24页归属已经专项证明，但不能冒充这轮完整生命周期成功。

b425b4d新增只读每杯图身份，真实缺属性红灯后完整JVM295项/0失败/0错误/1既有跳过，独立规格/质量审查通过。身份只影响展示量程会话；active id/finish/checkpoint/控制未改。


## 新图与测试迁移当前本地状态（待云端）

原高级页面38730308241的pages/business均实际成功：秤页64/48sp自然高度+局部次区滚动修复通过原8语言2主题页面矩阵，业务路径继续通过。仅代表930c7ad源码，不能代替后续新图整项回归。

图表7cd5c89/e71cc8b/9914ea3实现四指标原生图、独立范围、原始秤读数语义、真实分段、仅焦点历史参考、真实参考身份/日期/对齐直接展示；root将traceIdentity/referenceLabel接入Activity。质量审查的短高静默空白已改为明确native提示/局部可滚图例且暂不绘制不完整轴，正常高度恢复；新增100dp/font1.3压缩及恢复断言。正常3fixture仍要求可用plot、物理轴、文字bounds/字号不降；成功标记改为实际4fixture并包含shortHeight=true。

导航组件a5c31d8/b5c208b适应真实Frame与640dp测量，保留全部8语言2主题5宽2字号/48dp/RTL/textFits负fixture及独立header/stop。dummy不再假装实际五种页；实际Home重建后检查BREW选中，24真实页专项保留。质量审查发现GONE tools子树未测量，已只跳过该GONE分支，不改textFits本体。

独立规格/质量复查均通过。root统一`:mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest`使用SDK35离线aapt2参数，实际BUILD SUCCESSFUL in42s；独立XML295项0失败0错误1既有skip。最后GONE/选中重测的测试修正另编译AndroidTest实际BUILD SUCCESSFUL in20s；无新图真实UIgreen，不宣称图表/21页/短高整体验收完成。后续继续F3冲煮工作台与剩余集合/表单页面，不做发版。

## 56d0684实际云端结果与工作台后续（2026-10-16）

精确56d0684：Branch policy38733280233、Verify38733280288（含lint）、advanced pages/business38733280281、upgrade38733280100、task-frame38733280170均成功；lifecycle38733280147与single-screen38733280114失败，未当总体通过。

已取回三窗口24页及每窗4个新图fixture。实际抽看横屏four-metrics、手机gaps/reference/short-height与Extraction真实页；组件断言绿但原页面仍拥挤，未视觉验收。原生灰按钮、说明过多后以fb259e2收敛；独立规格和质量静态通过，新截图待跑。

lifecycle实际LanguagePageChecks compact Home仍假定root最后是旧4项bottom bar（229行），本轮迁移至真实Frame：四主项顺序/实际页选中/48dp/完整可见/工具/内容与导航不重叠/原start保留，RTL用全局rect。不删语言矩阵、textFits或业务检查。single-screen真实phone里ru/es Extraction plot0x0、ru对比chart108px不足96dp，是真布局问题，不降低正常plot检查；分别写独立Extraction/双杯计划实施。

Home a8f9bf8→c7a4b83：阶段预览/真实只读用豆与使用记录摘要/原5槽面板；实际文字测量先留96dp图，不足则完整非时间预测阶段文本局部滚动。独立规格发现最初图被压小P2后已修；质量发现fallback辅区0高P2，追加保48dp辅区viewport，静态复查关闭。未当实屏通过；新增真实Home组件normal/180dp/recovery检查，脚本1.0/1.3两轮且断言实际fontScale，完整阶段编号顺序，库存/用豆/使用/日志不变，selected即使启动失败也finally恢复。代码已编译，尚未native执行。

Extraction1daf622：时间/真实数量两主读数，压力/机器原始温度次级，单图占主要剩余空间；工具局部滚动，flow仅详情，不从raw scale推算出液。原确认方法与start/stop/prepare/cancel门禁保留。参考来源标签只包含实际日期与对齐，陈旧“实线本杯/虚线参考”说明另改成真实灰参考语义。规格审查发现临界高度chrome显隐反馈振荡P2，修复中，未宣称此页完成。

root实际完整命令（沿用SDK35离线aapt2覆盖）：`:mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest`，日志`/tmp/task-workbench-build.log`，BUILD SUCCESSFUL in51s/96tasks；独立解析66个JUnit XML，295tests/0fail/0error/1既有skip。此结果覆盖1daf622及当时root测试，但不覆盖后续修复和双杯实现；原生窗口与云端lint仍须重跑。本地lint没有新增通过证据。无用户设备安装、无发版、PR#4仍draft。


## 2026-10-16 横屏复盘短输入高度修正

2773b29 single-screen38844288915已终止为失败。用户平板横屏也在ChineseFormWindowChecks短输入检查失败：117px字段只显示108px，与普通横屏65/60px均对应52dp字段被48dp参数视口裁切；此前8个长文本状态和IME检查通过。生产布局改按原生短输入测量高度与面板padding保留参数区，不削弱测试。`/tmp/task-review-native-height-build.log`完整JVM/两MockAPK构建成功44s；JUnit295/0fail/0error/1skip。精确修正后的原生横屏结果仍待CI。库存列表详情与横屏范围变更一起进入新轮验收；不代表全部21页完成。


## 2026-10-16 用豆准备工作台

BeanPreparation改为主区完整本杯信息/原生内联粉量，辅区预计库存/实际扣减流水与称豆工具，固定保存/确认/返回；长豆名不再截断或必须另开详情。预览只有有效正粉量且库存足够时显示，不产生写入。尝试扣减后的状态不再次预测，实际收据只取eventId/batch/kind/delta匹配的流水，并标明本次扣减，不能冒充后来当前余量。所有select/confirm/恢复/放弃与原幂等事件保持。AdvancedBusiness原17路径新增90→72预览无写入、保存仍预览且无收据、确认重建后真实收据断言，原重复确认只扣一次/放弃不回库继续。最终本地42s构建295/0/0/1，两MockAPK通过；新增native断言和实际横屏截图尚未运行。无硬件安装、无合并发版。
