# 双杯对比页跟随机器拨杆

- 需求与范围：双杯对比在前台时，机器开始手动出水，进入既有实时萃取页；同一轮只自动跳转一次。历史页入口调整由另一任务独立交付，不在本 PR 范围。
- 来源分支/基线提交：`develop` / `3d51c037`；工作分支：`fix/comparison-lever`；PR 目标：`develop`。
- 实施：设备服务沿用被动萃取检测，在进程内发布活动手动标识。前台主页和对比页共用消费去重；对比页使用既有主框架萃取路由，不启动另一套萃取界面。
- 验收：前台新一轮触发一次；同一轮返回或重建不重跳；暂停、结束、断连不消费；观察到待机后的下一轮可触发。主页仍按既有逻辑跳转。
- 验证：`./gradlew --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.ManualShotPresentationGateTest :mobile:compileMockAndroidTestKotlin` 通过（门禁单测 3 项；Mock 导航用例只编译、未运行）。实际拨杆和界面时序未在实机验收。
- 协议与设备风险：只改页面导航的状态传递与消费；不改 BLE 命令、检测阈值、记录提交、萃取停止和重试。不执行设备写入。
- 推送与合并：本地检查通过后普通推送工作分支，经 `scripts/create-pr` 创建 PR，等待 `branch-policy`、`verify` 通过再合入。
- 发布：不发版、不打标签、不安装。不改包名、版本、签名或数据格式。
- 回退：通过标准 revert PR 撤销代码，不清理历史记录或设备未知状态。

参见 [任务规划模板](task-template.md) 与 [Git 工作流](../git-workflow.md)。
