# 双杯各指标运行图证据

edd89da高级页面夹具有记录摘要但未保存采样，空图不能验证全宽双曲线。复用SingleScreenWindowRun已持久化并等待加载的双杯采样，不修改生产数据语义。遵守docs/git-workflow.md；develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4。

TwoCupComparisonPageChecks四指标真实点击/样本不变/绘图区检查后调用取证回调。调用方等待原生绘制提交，保存每指标window.png、字体测量JSON和两杯采样数/指标文本。只在既有中文浅深色专项运行，适用三个横屏profile。原清空/交换/恢复断言和finally不变。

编译test APK、diff和branch检查；不执行本机adb、不新增模拟源、不降低门禁。新截图待CI，不将旧空图当双曲线验收。普通push现有PR，无安装/发版/合并，追加revert回退。
