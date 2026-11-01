# 设置滚动夹具目标纠正

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。a0ab801 run39091312445失败于蒸汽输入/标签完整可见断言：夹具只请求按钮矩形，却断言整组。requestRectangleOnScreen不保证按钮外部区域一并进入视口。改为标签/输入/按钮三个原view在同一内容坐标系内的并集，检查三实例存在，完整可见与textFits/输入不变断言全部保留。截图移到断言之前、失败信息输出具体view边界，避免失败无图。此改动校正可达性动作，不宣称产品裁切已修复；后续CI如仍失败按实图修产品。仅测试，无协议/数据/设备/发版/签名变化，MockAndroidTest构建及diff验证，后续批量推送；回退此测试改动。
