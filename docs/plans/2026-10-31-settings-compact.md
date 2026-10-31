# 设置与设备入口优化

- 范围：设置内联分组、共享机器编辑控制器、设备连接页、来源返回。
- 来源：origin/develop c3710e3；显式整合依赖 feature/task-first-ui 2683fd6。
- 分支：feature/settings-compact，PR目标develop；不修改源共享工作树。
- 验证：assembleMock、compileMockAndroidTestKotlin、testMockUnitTest通过；363测试：362通过、1跳过、0失败。七语言目录验证通过。新增MainNavigationChecks折叠/草稿/来源与overlay返回断言，尚未实际执行Instrumentation。
- 协议：复用现有MobileService和机器确认/门禁/回读，不扩展协议。未操作真实机器。
- 推送：完成验证后普通推送、PR、四项CI；本轮未推送合并。
- 发布：不发版、不安装共享平板，不变更签名版本或未知记录。
- 回退：revert本任务提交，保留应用数据与未确认设备记录。
- 阻塞：旧ScaleConnection视觉矩阵仍面向被替换布局，需要随新设备页迁移并运行；本机emulator -list-avds为空，system-images目录无可用ABI，未建立运行窗口；共享平板禁止争用。因此系统手势、紧凑屏幕、保存交互、扫描连接与前后截图尚未实际验证。无安装、真实机器操作或发布。
- 失败定位：早期构建资源与测试源码不同步造成R反射失败；最终失败为新增资源后的三处固定数量断言，已同步完整catalog与精确数量并全量复跑通过。基线2683fd6中StopActionPresentationTest 4项、StudioStartGateTest 2项均通过。
