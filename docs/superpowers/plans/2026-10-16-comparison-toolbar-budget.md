# 对比页操作区高度回归

来源fb19415 single/run38889727697用户平板334/336：中文浅/深色comparison-metric-PRESSURE高108px仅54px可见。保持develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

TaskComparisonLayout当前先占用height/3给详情，再给toolbar剩余空间，新增48dp分段按钮后正常用户平板也被裁切。改为先以toolbar自然高度分配（仍保留chartMinimum和48dp详情入口），然后将详情限制在剩余空间及height/3内。图表下限不降低，短高仍允许操作区局部滚动，不删数据或安全提示。

已有TwoCupComparisonPageChecks对用户平板全部操作全显、48dp、全宽图及样本/交换/恢复断言为回归证据，保持不变。构建Mock/test APK；新CI再验证全显及有数据截图，不能以编译声称通过。无协议/schema/签名/安装/设备操作/合并/发版；普通push，追加revert回退。
