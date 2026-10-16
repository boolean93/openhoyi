# 最近10秒秤趋势

认可scale-v2包含最近10秒图，不能用字体调整替代。分三步实施：纯只读窗口模型及边界测试；接到现有service通知（每个真实notification，不以250ms UI轮询造点），连接生命周期分段；原生Canvas图/短高可达与真实fixture截图。无数据必须空态，过期/断连/来源证据变化断线，负重保留，轴为实际秒与g；不改变去皮、粉量稳定判定或自动停止。

模型保存真实receivedAtMs，忽略重复与乱序，不把过期/未来值当新点；10秒单调窗口、最多2048点限内存，时钟回退清会话缓存。每段保留ScaleEvidence，不混为已验证控制数据。模型只是显示缓存，不写库存/历史，不新增BLE owner。后续需把可见图与同源文本摘要、连接/过期状态一起提供。

遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。先JVM红绿验证重复/断点/过期/未来/负值/乱序/时间回退/内存边界，再原生图轴/短高/字形和模拟通知连接检查。无设备安装或操作，不合并发版、不改签名/schema，普通push与追加revert回退。纯模型完成不等于趋势图交付。

第一步：ScaleTrendWindow只读模型及5项JVM测试完成。红测10秒：缺失ScaleTrendWindow；实现后绿测28秒，5 tests/0 failures/0 errors/0 skipped。有断连/1500ms缺口/证据变更分段、负值、重复/乱序/未来/过期拒绝、10秒过期、时钟回退和容量/快照独立性。尚未接入service或页面，不宣称趋势图可用。
