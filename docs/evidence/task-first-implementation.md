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
