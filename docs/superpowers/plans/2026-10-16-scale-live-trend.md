# 最近10秒秤趋势

认可scale-v2包含最近10秒图，不能用字体调整替代。分三步实施：纯只读窗口模型及边界测试；接到现有service通知（每个真实notification，不以250ms UI轮询造点），连接生命周期分段；原生Canvas图/短高可达与真实fixture截图。无数据必须空态，过期/断连/来源证据变化断线，负重保留，轴为实际秒与g；不改变去皮、粉量稳定判定或自动停止。

模型保存真实receivedAtMs，忽略重复与乱序，不把过期/未来值当新点；10秒单调窗口、最多2048点限内存，时钟回退清会话缓存。每段保留ScaleEvidence，不混为已验证控制数据。模型只是显示缓存，不写库存/历史，不新增BLE owner。后续需把可见图与同源文本摘要、连接/过期状态一起提供。

遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。先JVM红绿验证重复/断点/过期/未来/负值/乱序/时间回退/内存边界，再原生图轴/短高/字形和模拟通知连接检查。无设备安装或操作，不合并发版、不改签名/schema，普通push与追加revert回退。纯模型完成不等于趋势图交付。

第一步：ScaleTrendWindow只读模型及5项JVM测试完成。红测10秒：缺失ScaleTrendWindow；实现后绿测28秒，5 tests/0 failures/0 errors/0 skipped。有断连/1500ms缺口/证据变更分段、负值、重复/乱序/未来/过期拒绝、10秒过期、时钟回退和容量/快照独立性。尚未接入service或页面，不宣称趋势图可用。

第二步候选：MobileService原onScaleObservation接只读缓存，每条真实通知一次；非READY分段、切换所选秤/关闭owner清缓存。ScaleActivity只读points，无独立BLE owner；Canvas固定最近10秒时间窗、g轴、分段不连线、无数据文本与最新读数年龄。正常主区显示，空间不够时次区按钮打开可滚动详情，onStop关闭；复制摘要不代表硬件采样验收。

增加ScaleTrendChartChecks于现有8语言×2主题原生图表阶段，验证左右真实线段、中央缺口无连线、空态无旧像素，独立结果标记成为lifecycle脚本必需项。此原生测试尚未实际运行，不冒称通过。首次构建因不存在android.R.string.close失败，使用cancel后完整JVM/双APK48秒通过；304 tests/0 failures/0 errors/1既有skip。加入结果标记/避免重复摘要setText后最终双APK39秒通过，shell语法/diff检查通过。后续还需真实图表截图、service假GATT采样接线与短高入口可达证据。

第三步候选（审查后）：复用服务真实onScaleObservation方法，隔离fake GATT通知检查receivedAtMs/重量/证据进入缓存且时间不重复；仍用synthetic READY，不证明服务断连或切秤清理。新增原生Panel四状态组件截图，以及fallback打开弹窗、状态变为断连后文案实时更新、dismiss释放检查。详情现在同步页面的连接/过期/来源提示，不把保留的10秒历史当实时连接。组件短高fixture不是完整ScaleActivity短高窗口验收；后续必须补真实窗口可达及滚动检查。无新BLE owner、无设备操作、无安装/合并/发版。
