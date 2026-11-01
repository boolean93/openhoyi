# 导入确认操作回归右栏

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。87334eb run39113352006显式表格门禁失败：正常导入table356px/chart664px，容纳表格后剩308px，小于原360px(160dp)图阈值；分享两阶段表192px已显示，chart381px。导入保存dock放整页底部额外占主区高度，而curve-import-preview-v1将确认动作放右侧。把原dock放进右侧decisions支持滚动区域，宽度跟随右栏，不改原save/copy/keep回调或主图阈值/表格行数；错误仍在原全页错误区域。左右内容由原workspace高度预算管理；窄/短视口右栏转下方局部滚动且入口仍可达，需现有布局夹具验证。Mock/AndroidTest构建、diff、正常平板表格门禁仍保留。合批推送，不安装/设备/协议/签名/合并/发版；回退dock父节点。
