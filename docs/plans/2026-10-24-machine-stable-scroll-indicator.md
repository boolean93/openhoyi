# Machine 主图右缘变化：稳定滚动指示最小计划

> 只读分析与设计交付，root阅读批准后才交Sol实施。使用brainstorming/writing-plans；执行按executing-plans。此轮仅Machine，不处理Scale或第三模块。

## 结论及证据边界

推荐 Machine 页**原生纵向滚动条持续可见**，关闭其自动淡出，而非隐藏滚动条。不调等待/重试/阈值，不修改截图协议。四配置右缘变化与原生thumb尺寸精确吻合，足够支持这一局部产品视觉稳定方案；没有逐帧scrollbar alpha原值，故不冒称已经直接记录平台内部淡出状态或修复已通过。

来源 `c24d7d0317dafbd5fbf45350d63121a20a8b8463` / `feature/task-first-ui`（develop基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`），PR #4→develop。原运行source `8fee86b4e3f004ed3be5811d22a5bb49c2f87b98`，ZIP `build/task-first-evidence/ci-8fee86b-settings/artifact.zip` SHA256 `4a449a231047c75d05f6c921aa90c3dd6f5fa429ce1a5bbbdc119e86187c0c11`、5999 CRC由root核；四原proof与 `machine-first-failure-summary.json`已保留。

## 原件与源码对应

四份first attempt均geometryUnchanged，scrollX/Y=0；Machine scroll rect `[180,74,2560,1465]`，viewport高1391。1.0 body `[180,74,2560,1732]`高1658，1.3 body末端1880高1806。

| 配置 | 差异范围（screen px） | 像素数 | 原生thumb预测 |
|---|---|---|---|
| light/dark1.0 | x2551..2560、y74..1241 | 10503=9×1167 | round(1391×1391/1658)=1167 |
| light/dark1.3 | x2551..2560、y74..1145 | 9639=9×1071 | round(1391×1391/1806)=1071 |

SDK34 [ScrollBarUtils.getThumbLength](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/com/android/internal/widget/ScrollBarUtils.java)使用实际视口、范围和最小滑块宽计算；本次高度及顶端零offset与该公式精确对应。宽9px即当前density2.25的4dp量级，但原件未记录实际scrollBarSize，不把4dp推断写成已测值。

`SettingsVisualStyle.page`创建整页ScrollView，未设置fade；Machine调用此容器，横向tabs另明确关闭横向scrollbar，与最右纵条无关。fixture等真实Service绑定后直接capture，没有隐藏滚动条或人为滚动主图；capture沿硬件commit→S1→W1→D1→D2→W2→S2。light1.0 first S1_W1=0、后S1_W2/S1_S2=10503，另三配置首S1与后续均有整条差异；D1_D2始终0。符合滚动提示在S1后改变而布局不变，不支持把它当文字或整页偏移。

第二attempt四差异均0但comparison_complete已超5000ms；报告最终elapsed约6732..6868ms含末尾留证，不能把最终elapsed等同首次或第二次纯采样时长。先前同observation复用已生效，不能因耗时降低就称动态thumb已关闭，也不能把第二次晚到的四零签通过。

## 设计选择

批准规格 `2026-10-19-m7-m8-remaining-visual-migration.md` 要求整页可滚、完整反馈/section可达、原字体/16/24/8与触点不缩水，没有要求滚动提示必须自动消失。对机器参数长页，持续保留真实滚动位置提示是用户可见的产品设计，不是只在截图时隐藏动画。

- **选定候选：** 仅Machine原ScrollView `isScrollbarFadingEnabled=false`；原vertical scrollbar enabled/宽度/style/系统drawable/主题颜色/范围/offset保持。原生条保留在最右既有区域，无新固定控件，不占内容预算，不改自然文本。
- 不选隐藏/透明scrollbar：会移除滚动提示，且违反本次不遮罩意图。
- 不选等淡出/预热capture/加帧/sleep/扩大5秒：既不解决产品提示时序，也会改已冻结采集边界。此次不增加平台内部alpha遥测或泛化稳定框架。

SDK34 [View.setScrollbarFadingEnabled](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/view/View.java)在false时将scrollability状态设ON，不关闭滚动条。保持原生样式及系统非滚动内容行为，不强画虚假满长滑块。

## 最小实施（批准后）

仅产品文件 `mobile/src/main/kotlin/io/openhoyi/mobile/MachineSettingsActivity.kt`：在 `visual.page(...pageId="machine")`返回后，从该Activity已安装content取得唯一既有 `product/settings/machine/scroll` ScrollView，设置：

```kotlin
val pageScroll = requireNotNull(findViewById<ScrollView>(R.id.m8_settings_scroll))
pageScroll.isScrollbarFadingEnabled = false
```

原SettingsVisualStyle.page在返回前setContentView已完成，使用现有专用id；Sol实施前核当前唯一id仍对该scroll，不引入查找候选/重试。不得改共享SettingsVisualStyle默认、AppSettings/ScaleSettings、dialog/tabs、业务render的500ms handler或Service；不暂停刷新，不在fixture临时设置/还原此属性。若需验证属性，用原Settings fixture Machine初态检查旁两条只读断言：verticalScrollBarEnabled仍true、scrollbarFadingEnabled=false，不增场景；原所有采样/目标保持。

## 验证与接受终点

1. Sol交局部diff及精确SHA；静态确认只有Machineopt-in，原scroll对象/布局/padding/回调/文本/颜色/scroll offset均不改。无需新增镜像模型测试。
2. root统一Mock双包构建：`:mobile:assembleMock :mobile:assembleMockAndroidTest`，保源码/APK SHA。普通推送工作分支，原Settings CI四Machine主图+既有滚达/恢复/特殊态照常，不删状态；Scale关联9px小条只作旁证，不纳入实现。
3. 原5s四零严格通过才有合格图；未过保原proof与首错，不用第二attempt晚到四零代通过。若变化消失但总时间仍失败，仅记“右缘动态差异已消失、采集仍FAIL”，不得自动叠加性能改造。
4. Astra实看四Machine新主图：G1原双栏/整页滚与指示不盖内容；G2字体身份/字形沿冻结结论；G3原字号行盒/48dp动作不缩水；G4原主题文字/背景及原生指示可辨。核原reach/restored精确滚动与业务不变，非新增矩阵。此计划不预签当前失败图。

## 工作流、发布与回退

root读后才实施；共享工作树保别人改动。普通提交/推送PR #4，经必需CI与评审合develop，不直推main/develop或强推。无设备/发布/签名/升级授权；无BLE/控制指令/库存策略影响，不重发未知操作。回退新增revert移除Machine这一属性及对应只读断言，保留所有失败/成功原件，不清数据。当前仅规划、未改产品/构建/设备/提交。

## 2026-10-24 冻结实现静态验收

Astra限定静态通过；实际5s四零及Machine四主图仍待新CI，不将淡出候选或本次属性断言当实际视觉闭合。

- 产品 `mobile/src/main/kotlin/io/openhoyi/mobile/MachineSettingsActivity.kt` SHA256 `59ff12e5ee8b42deb77db71d0be6e645a663c3cf8166c428b05e20897750ea6a`。
- fixture `mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt` SHA256 `dd8aa16cc662b6aa6ce2b06e856f208a93e95c5d4bc39de9e4410875dc5be623`。

独立核diff仅产品两行、fixture三行。SettingsVisualStyle.page第43行唯一赋予现有ScrollView `m8_settings_scroll`，第68行setContentView后第69行返回body；Machine紧接page返回取得同对象，只设isScrollbarFadingEnabled=false。未reparent/recreate，未改vertical enabled、thumb drawable/style/size、padding、自然文本、监听、scroll offset或500ms render。共享SettingsVisualStyle及其他页无diff。

fixture新增两条只读断言在原capture之后的Machine分支，保原selected section、非空温度、Service/pending等检查；没有调整capture前等待、重试、计时、geometry或5s四零。两文件SHA独立重算吻合，`git diff --check`通过。构建由root统一执行，Astra未改源码/跑设备。

## Root构建结果

统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，1m4s、91 tasks（9 executed / 82 up-to-date），日志 `build/task-first-evidence/ui-2026-10-24-machine-scroll/build.log`。303个Kotlin源构建前后SHA一致，见同目录source-before/source-after.json；diff-check通过。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256：`cdfe0ee3aa8085952298f7a26d35f750af02108f61cea74a26bd685b8d5a5cc5`
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256：`30260e7ea09e2e64ad600d5490459025ba0835a88346559712d6d7a2c5d3d118`

未安装/发布/合并；原5秒四零及四主图是否通过仍待本次CI，构建与静态接受不替代视觉。

## 后续生命周期修订：创建时设置常显（2026-10-24）

原生命周期两轮实际崩溃在 `MachineSettingsActivity.onCreate` 的 `requireNotNull(findViewById(m8_settings_scroll))`。源码因果独立核实：`SettingsVisualStyle.page` 创建scroll后，`TaskPageFrame.install` 将其移入pageContent；pageContent直到TaskPageFrame首次onMeasure才挂到frame。故onCreate同步Activity树查找null是合法未挂载状态，不是scroll未创建。此前静态通过未覆盖此挂载先后，不能用编译通过替代运行事实。

批准最小修订仅两个生产文件：

1. `SettingsVisualStyle.page` 参数末尾增加 `persistentScrollIndicator: Boolean = false`。原ScrollView构造apply内仅在true时设置 `isScrollbarFadingEnabled = false`；false时不调用setter，保原平台默认行为。原id/tag/容器/vertical enabled/颜色/宽度/布局不动。
2. `MachineSettingsActivity` 原page调用以named参数传true；删除其后Activity.findViewById/requireNotNull及fade设置两行。无需查body父链、延迟、重试或吞null；设置作用于原创建对象。

AppSettings/ScaleSettings调用不改，TaskPageFrame公共生命周期不改。保现Machine初态vertical enabled/fading disabled断言与全部lifecycle检查，原5000ms四零不动。继续同工作分支/PR develop、root统一两包构建及原lifecycle运行；不是发版/设备授权。回退使用新提交，不恢复已证实会崩溃的过早查树；原常显视觉需求保持。实施后记录精确SHA，运行通过前不宣称产品崩溃闭合。

Astra增量静态验收：实际diff严格符合上述两文件修订；仅额外删除不再使用的ScrollView import。原对象id/tag/fillViewport保留，默认false不调用setter，仅Machine传true，未动TaskPageFrame与测试。`git diff --check` exit0。冻结SHA256：SettingsVisualStyle `0cd64646732f4684ef5543f524ae20dd9305a3a6714ec340380f1cea06f89f21`；MachineSettingsActivity `6006fbaa196bd6c62c1af90a3fd0c20efeca8e5f865bb7ad2d348ba2cb4cc2cc`。静态无阻断，可交root统一构建；生命周期真实重跑仍未完成。

root构建验证：`:mobile:assembleMock :mobile:assembleMockAndroidTest` exit0，BUILD SUCCESSFUL in 1m 2s（91 tasks，6 executed）；构建后两生产文件SHA与上述冻结值一致。日志 `build/task-first-evidence/required-lifecycle-scroll-fix/build.log`。原失败PR run40070126254实际checkout `3b2d453297a1684f49ace3f40381d74bac21d854`，push run40070122340实际checkout `aa40a2f5e81dd1a55b2804d0533ca4f9f7435e26`；均为同一onCreate崩溃。新提交的原lifecycle运行仍待CI，不用编译/静态结果替代。
