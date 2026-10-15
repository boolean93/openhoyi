# Task Page Frame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 把真实Activity接入平板左轨/窄屏底栏，提供电子秤与设置工具入口，保留原内容实例、停止区和业务回调。

**Architecture:** 不重写Activity、Insets或BLE所有权。安装器把旧根布局的现有子View按原顺序和LayoutParams移到新的垂直内容列，旧根的Insets监听仍生效；共享导航在内容列旁/下布局。真实控件测量补足纯窗口策略，只有导航项允许局部滚动，内容不新增整页ScrollView。

**Tech Stack:** Android原生View、Kotlin、JUnit4、现有Mock instrumentation、GitHub Actions一次性模拟器。

---

## 范围与治理

基线`feature/task-first-ui`/`d546e22`，已从develop创建并显式记录依赖；继续当前工作树，不建重复分支。PR目标develop，普通推送、必需CI，不直接合并旧PR。不发版、不安装用户平板、不改协议/业务/数据schema；回退为追加修正或revert PR，保留未知记录。其余规则、全21页目标及后续批次见[总路线](2026-10-15-task-first-ui-roadmap.md)。

## 文件与接口

- Create `mobile/src/main/kotlin/io/openhoyi/mobile/TaskNavigationModel.kt`：纯导航模型，枚举`TaskDestination { BREW, CURVES, HISTORY, BEANS, SCALE, SETTINGS }`，`primary`固定前四，`tools`固定后两，`forPage(simpleName: String): TaskDestination?`覆盖Manifest全部24页，未知返回null。Home/Extraction归冲煮；6曲线页归曲线；6记录页归记录；5豆子页归豆子；Scale/Connection/ScaleSettings归电子秤；Machine/AppSettings归设置。
- Create `TaskNavigationView.kt`：同目录。原生导航呈现，`render(mode: TaskNavigationMode, includeTools: Boolean)`只在模式改变时重建导航，不重建业务视图；稳定目标tag为`task-nav:<枚举名>`。标签14sp、图标20dp、目标≥48dp，selected状态和contentDescription可读。宽轨72dp；按实际字高测量，不缩字。`createToolsHost(activity)`返回header工具入口，tag=`task-tools-host`，菜单按钮tag=`task-tools-menu`，菜单电子秤/设置打开原Activity；点击才导航，不连接设备。
- Create `TaskPageFrame.kt`：同目录。`install(activity: Activity, parent: LinearLayout): TaskPageFrame`幂等，tag=`task-page-frame`；`pageContent`是垂直内容列，`navigation`是上述导航View；可读`mode`/`toolsInTopBar`，setter私有。实际Activity类确定选中项，不用旧helper的fallback把复盘误选成豆子。onMeasure按configuration窗口宽选择导航（不因root安全padding误跨600dp阈值），按实测可用高度换dp调用现有策略；模式变更只调整导航/内容容器。四主目的地始终可达，工具在正常轨底、窄屏/短高header；若真实字号令全工具轨仍放不下，移到header。极短大字号下轨内可滚动，不能裁切标签。
- Modify `HoyiUi.kt`：保留`navigation`调用签名，函数体仅委托安装器；header增加上述工具host；`wide`按当前TaskLayoutPolicy的双栏条件，不再仅700dp横竖判断。不要改button默认LayoutParams，本批不重排业务主操作。
- Modify `BeanInventoryActivity.kt`：`beanPage`无论navigation参数是否null都安装共享导航，back语义仍按原参数判断。
- Modify `HistoryDetailActivity.kt`、`LegacyHistoryDetailActivity.kt`：根body建立后安装共享导航，所有数据读取/异步回调不改。
- Create `mobile/src/main/res/drawable/nav_scale.xml`：沿用当前outline图标语言，不生成新品牌图案。
- Create `mobile/src/main/res/values/task_first.xml`：`task_tools`中文“工具”，不把实现细节放进产品。
- Modify `values/colors.xml`、`values-night/colors.xml`：仅accent/primary/soft调整为已认可青色语义；图表与危险色暂不改。`values/styles.xml`的colorAccent引用语义资源，避免主题硬编码旧蓝色。
- Test `mobile/src/test/kotlin/io/openhoyi/mobile/TaskNavigationModelTest.kt`：全部24页唯一归属、未知页无猜测、四主目的地顺序与两个工具分离。
- Test `mobile/src/androidTest/kotlin/io/openhoyi/mobile/TaskFrameChecks.kt`，runner入口`BrewAudioInstrumentation.kt`：真实24个Activity，包括没有记录ID的错误/空态；检查框架唯一、控件在屏内、顺序/选中/工具入口、导航不执行设备写入；保存截图。
- CI `scripts/check_task_frame_ui.sh`、`.github/workflows/task-frame-ui.yml`：仅隔离Mock、一次性模拟器，变更显示/安装前断言`ro.kernel.qemu=1`；用户平板不运行这些命令。

## Task 1: 导航模型先红后绿

- [x] 创建`TaskNavigationModelTest.kt`。明确断言primary为BREW/CURVES/HISTORY/BEANS，tools为SCALE/SETTINGS；对Manifest24个具体类名逐个断言上述归属，`OtherActivity`为null；任何24页不能误入未知或另组。
- [x] 定向运行`:mobile:testMockUnitTest --tests io.openhoyi.mobile.TaskNavigationModelTest`，观察缺少新模型的预期失败。
- [x] 实现纯模型，不引入Android、服务或回调，重跑定向和原布局策略测试。导航到当前顶层页不重复startActivity；Extraction点击当前冲煮保持当前页，曲线/记录子页点击对应主目的地仍返回顶层列表。

## Task 2: 安装真实Frame和导航

- [x] 在上述文件中实现接口；原内容实例/现有LayoutParams必须原样迁移，不克隆字段、不重新设置选中值，不调用任何控件performClick。
- [x] 真实接线代码为：

```kotlin
fun navigation(activity: Activity, parent: LinearLayout, selected: Class<out Activity>) {
    TaskPageFrame.install(activity, parent)
}
```

```kotlin
// beanPage: navigation只控制原back语义，不再决定是否有全局目的地。
HoyiUi.navigation(this, root, navigation ?: javaClass)
```

```kotlin
// 两个原来没有导航的详情页，body加入root后、setContentView之前。
HoyiUi.navigation(this, root, javaClass)
```

- [x] Frame按窗口configuration宽与实际容器可用高重算。header host在工具入header时可见，其余时候GONE；header不存在时提供同样明确的fallback工具行，不让入口消失。不能每次onMeasure重建内容或丢焦点。
- [x] root原Insets监听保留，禁止额外叠加系统栏/IME padding造成双重扣除。导航占用现有root安全区，与内容并列；停止仍在原内容列固定区，不被放进ScrollView。
- [x] 编译Mock与AndroidTest，执行所有Mock JVM测试；新导航模型测试不得通过修改旧断言掩盖错误。

## Task 3: 真窗口检查与CI

- [x] 新runner开关`taskFrameChecks=true`只在MOCK_MODE允许，显式分支运行`TaskFrameChecks`，不改变原runner分支或原成功标记。
- [x] 原生instrumentation打开全部24页，等待实际layout，检查唯一Frame、4个主导航的相对顺序/选中归属、至少48dp、工具可达及宽轨/窄栏模式，输出`TASK_FRAME_CHECKS_PASSED pages=24 noBle=true`及实际PNG。它只验本批框架，不宣称业务21页或四指标图已通过。
- [x] 三个主要CI窗口为用户平板横屏2560×1600/density360、竖屏1600×2560/density360、phone450×1000/density200；各自保留结果/崩溃日志/截图。不运行对真实平板的ADB命令。
- [ ] 保留现有生命周期/升级/业务/窗口工作流，不能关闭必需CI。以新源码运行框架专项，人工查看实际截图；如果旧业务页因新宽度露出问题，记录并修复真实布局，不缩小总范围或伪造通过。

## 验证命令

```bash
./gradlew :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain
bash scripts/check_task_frame_ui.sh tablet-user-landscape
bash scripts/check_task_frame_ui.sh tablet-user-portrait
bash scripts/check_task_frame_ui.sh phone
git diff --check
python3 scripts/git_policy.py branch
```

本机缓存环境使用已验证的`--offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`，不将其写进CI或全局配置。新组件独立规格审查、代码质量审查后再提交；最终证据写`docs/evidence/task-first-implementation.md`。框架通过仍不等于四指标图或21页视觉改版完成。


首轮c3a5675/CI38727735636三窗口框架通过；原verify/lifecycle/俄语Scale回归仍有失败，不勾选整体验收。见实施证据与真实读图记录。
