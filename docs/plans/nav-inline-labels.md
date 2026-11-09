# 导航图文横排与设备连接快捷入口

基于 develop（55d0a02b），工作分支 feature/nav-inline-labels，PR 目标 develop。遵循 [Git 工作流](../git-workflow.md)。

1. Sol 修改 TaskNavigationView。侧栏、紧凑侧栏和底部栏共用普通目的地的横排图文，保留整项点击、选中态和可访问名称。检查窄宽度的图标与标签不会重叠、挤出边界。
2. TaskShellStatusView 保留两组设备名称和状态的上下布局。各自文字区域作为一个可点击、可聚焦的入口，复用 MainActivity.openSettingsSection("devices")。休眠、连接、断开等按钮保持独立，点击文字只执行导航。
3. 验收普通图文相对位置、导航选中、两组文字分别准确定位到设备连接、从设置其他位置重复进入，以及现有返回逻辑。优先复用 MainNavigationChecks，使用可用模拟器验证代表性布局，记录实际执行与限制。Astra 按原需求复核。
4. 只提交本任务文件。若推送或合入，走工作分支 PR 及仓库规定的必需检查。无协议、数据、签名与升级改动，不发版、不操作真实设备。回退通过 revert PR。

验证结果：API 34 中文 Mock 模拟器上，旧代码在标准侧栏的横排断言失败；新代码通过 mainNavigationChecks=inline，覆盖三种布局、重复定位与返回来源页。平板分别实点咖啡机和电子秤的名称、状态，四次均显示设备连接章节及设备列表。360dp 宽手机底栏的「电子秤」可在图标右侧换行。Astra 已独立复核平板截图与四次点击记录。Mock 和仪器测试包构建、Debug Kotlin 编译通过。未做真机安装或验收。
