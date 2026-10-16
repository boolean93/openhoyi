# 旧版详情图表辨识度

依据legacy-history-detail-v1及27ae12b用户平板有数据截图。压力/水流同路径被后画实线遮住；缺少双物理刻度与中间时间刻度。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

保留旧版pressureBar/waterFlow及独立0..max(12/6,data)投影，不当原生秤重/累计水量。水流改虚线，图例提供同线型示例；增加左右真实刻度、浅网格与中间时间刻度；字号使用scaledDensity，边距按文字测量。短高减少刻度数量，不改数据或时间顺序。所有原始说明保留。

LanguageChartChecks增加真实Canvas重合轨迹双色断言，保留8语言/两主题/RTL上升顺序。旧截图证明重合遮挡；新增原生断言本地不执行设备测试，CI仍需实际运行。Mock/test APK编译及diff/branch检查，无安装/合并/发版/签名/schema修改；普通push，回退追加revert。
