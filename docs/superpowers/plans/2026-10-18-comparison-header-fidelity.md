# 两杯对比顶部与图例对齐

依据认可brew-comparison-v1与35db865四指标实图；沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

BrewComparisonActivity保留两个真实Spinner、缺失ID占位和swap回调。交换按钮移至两张选杯卡之间，宽横屏同排、窄横屏纵排；自适应测量给卡片分配剩余宽度，按钮自然宽度。卡片间16dp间距。原共同豆款/曲线说明保留为14sp普通字重独立行，不伪造相同豆款。指标选择后、图表前放右对齐14sp图例，使用与实际曲线一致的蓝色。TwoCupChartView两杯同蓝色实/虚线，轴标12sp，其余投影、量程、缺失状态与采样不改。

现有TwoCupComparisonPageChecks保留四指标切换/真实身份交换/缺失选择/全部控件完整/96dp绘图区验证，不降低检查；这是既有功能的可逆布局改动，不增加镜像实现的测试。构建Mock与test APK，CI用原四指标实图检查顶部与绘图区。截图通过前不声明视觉一致。不安装/设备写入/合并/发版，不改schema签名；普通push，追加revert回退。
