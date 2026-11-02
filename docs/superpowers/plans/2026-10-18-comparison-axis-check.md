# 对比图轴字号取证基线修正

沿用 develop66ebda9+ed01de0 → feature/task-first-ui → develop draft PR#4，遵守 docs/git-workflow.md。

CI 39083184345 在 two-cup-pressure 的 bar 标签上失败。产品 TwoCupChartView 已按 051f16e 将轴文字从 11sp 提至 12sp；TwoCupChartChecks 的独立 TextView 仍使用 11sp，TextBoundsCanvas 精确比较导致误报。仅将该独立预期改为 12sp，不读取产品值来生成预期，不放松系统字号换算、边界、重叠、单位、样本和绘图区检查。四指标图的 11sp 预期不改。

现有 CI 已提供失败复现。本地构建 test APK、diff/branch-policy；后续同一 CI 在 1.0/1.3 字号下运行才能证明修正通过。无产品或协议改动，无安装、机器操作、签名/版本/schema 修改、合并或发版。普通推送现有功能分支，追加 revert 回退。
