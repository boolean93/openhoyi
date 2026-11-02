# 机器设置标签视觉对齐

沿用feature/task-first-ui（develop66ebda9+ed01de0）→develop PR#4，遵守docs/git-workflow.md。依据既有task-first规格、machine-settings-v1图及10bf727实际图，移除五个标签的卡片式背景，改为底线与选中强调线。保持16sp、48dp最小点击区、原HorizontalScrollView、原切换/恢复逻辑，增加明确selected状态与键盘可聚焦。非选中400/选中500字重，不靠颜色单独表达选择。不改机器命令、确认或回读。验证Mock及AndroidTest构建和diff，视觉结果待下一批CI；不以编译代替截图验收。不安装/合并/发版，不改变签名，后续批量推送；回退仅标签样式。
