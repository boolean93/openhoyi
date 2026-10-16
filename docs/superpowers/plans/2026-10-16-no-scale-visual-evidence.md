# 无秤运行态视觉取证

依据extraction-no-scale-v2及已批准三/四指标同图规格；develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

1. MockDeviceRuntime提供仅模拟器内部可调的scaleConnected状态，默认true，不增加产品设置或真实蓝牙入口。断秤采样scaleState=DISCONNECTED、weight/weightAt=null；机器三指标/运行状态照常。重连后恢复原Mock采样。
2. MockDeviceRuntimeTest验证断秤空闲/运行/结束和恢复，必须从原始snapshot检查而非仅隐藏UI线条。
3. 后续在既有真实Mock开始/停止检查中加入无秤运行态，验证chartPoints无重量、三项机器值存在，再采集用户平板截图和字体证据。不能把第1/2步当截图验收完成。
4. 按实际图对照认可无秤稿，核对主数量语义、三指标、来源解释和固定停止。大字号/短高沿用现有门禁。

本次先完成第1/2步，保留取证缺口。运行指定单测及Mock/test APK构建；普通push现有分支。无真实连接/协议命令、安装、合并、发版、签名/schema变化；追加revert回退，不清用户记录。
