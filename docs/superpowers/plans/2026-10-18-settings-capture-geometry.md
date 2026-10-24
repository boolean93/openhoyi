# 设置截图与可见性矛盾诊断

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。e8a900a机器底部截图第三行被系统栏截断，但完整可见断言通过；不能认定产品或测试根因。截图前后各记录scrollY、可见窗口矩形、三控件全局边界和尺寸；增加控件位于windowVisibleDisplayFrame内的断言。两次动画回调只同步布局，不保证合成截图，额外等待100ms合成机会并再次同步frame，不以该等待代替边界断言。保留原完整可见/textFits和输入不变检查。失败保留截图和诊断，无产品改动。AndroidTest构建及diff，合批推送后从实证区分时序与遮挡。无安装/协议/数据/签名/发版变化；回退仅诊断。
