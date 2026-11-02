# Task Navigation Test Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 把旧bottom-bar专用组件断言迁移到新Frame，保留/加强8语言×2主题×5宽度×2字号的文字、目标、方向、工具可达与原停止检查，不用旧结构断言掩盖新UI。

**Architecture:** Detached LayoutHost只测配置下布局，不冒充实际Activity选中；真实Home选中在LanguageContextChecks真实启动/recreate后检查。24实际Activity选中已有TaskFrameChecks三窗口执行，继续保留。真实门禁/业务/服务不变。

**Tech Stack:** 既有原生instrumentation，不增加测试框架。

治理同docs/git-workflow.md/当前feature/task-first-ui→PR#4 develop；只改职责文件、不push/release/device操作；不改控制/门禁或未知记录。root保留其余编辑。

## 红灯证据

930c7ad push CI38730299537的artifact/mock-lifecycle/errors.txt：`zh-Hans dark=false width=320 fontScale=1.0 selected=HomeActivity missing navigation entries`，LanguageUiLayoutChecks.kt:39。真实前后台边计数/theme重建/本地音频全部通过，到了context组件旧结构断言才失败。旧检查认为root首子就是4项bar、>=700横排、wide>=700；新root首子是TaskPageFrame且600是rail/840和内容fit才wide。

## 责任与步骤

Worker只拥有`mobile/src/androidTest/kotlin/io/openhoyi/mobile/LanguageUiLayoutChecks.kt`与`LanguageContextChecks.kt`；root负责runner/scripts成功标记与证据。不要修改任何生产代码迁就旧断言。

- [x] 核对真实失败与TaskPageFrame/TaskNavigationView。保留verifyClippingDetection和textFits完整负向fixture，不降低任何字号/行高/最小48dp/文本完整性检测。
- [x] components对每width320/360/600/700/1000、font1.0/1.3创建配置Context；高度显式640dp，页面host类仍LayoutHost。去掉伪selected page的5次相同循环，不声称dummy实际选中了Home/Curve等。独立断言HoyiUi.wide符合840dp与min320+280+72+24+32、height>=360的批准策略；不能仍按700判断。
- [x] 安装Frame并以EXACT width/640dp height measure/layout（不能UNSPEC height0让工具条件错判）。递归查Frame.navigation下`task-nav:<ENUM>`，4主顺序始终固定，actualActivity unknown所以全部未选中；RAIL>=600方向VERTICAL/BOTTOM方向HORIZONTAL。标签text与独立资源labelIDs相等，TextView.textFits、font1.3实际textSize>1.0、item最小48dp/clickable/focusable、方向RTL/LTR均保留；bar局部滚动允许但标签不能被自己的row裁掉。
- [x] 正常rail核对2工具项顺序/文字/target/textFits；compact工具核对header或fallback中的task-tools-menu，其文字与task_tools翻译相符且48dp，工具不能消失。用于root判断：`val frame=root.findViewWithTag<View>(TaskPageFrame.TAG) as TaskPageFrame`，递归查找不依赖旧child索引。根helper未知class不能伪断言按旧navigation selected参数选中。
- [x] 在LanguageContextChecks每个真实Home语言/主题重建后，查实际task-nav:BREW.isSelected=true，其他三个主项false；在24页专项已有的实际class映射不能删掉。原三个资源上下文/方向/theme/service同一实例/event/采样/停止不动。
- [x] 保留独立header/stop测量与文字完整性/48dp断言。只迁移结构和阈值，不减少真控件断言；真正无法放下的控件报错给root修改UI，不改expected/吞异常。
- [ ] 与root协调Gradle；assembleMockAndroidTest编译，diff --check，只提交两职责文件。独立规格再质量审查，最终新CI上下文检查与业务/生命周期全部重新通过才算验收。root会把成功marker从selections=5改成真实声明`destinations=4 tools=2 actualHomeSelected=true`，不保留已不存在的伪计数。


a5c31d8迁移与b5c208b修复独立header误查GONE菜单已提交；独立规格与质量复查通过。最后AndroidTest编译已通过（20s），真实CI尚待，不能把上面实现勾选当运行完成。完整JVM当前295项0失败1既有skip。
