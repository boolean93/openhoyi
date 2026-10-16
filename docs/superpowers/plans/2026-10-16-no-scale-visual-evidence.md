# 无秤运行态视觉取证

依据extraction-no-scale-v2及已批准三/四指标同图规格；develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

1. MockDeviceRuntime提供仅模拟器内部可调的scaleConnected状态，默认true，不增加产品设置或真实蓝牙入口。断秤采样scaleState=DISCONNECTED、weight/weightAt=null；机器三指标/运行状态照常。重连后恢复原Mock采样。
2. MockDeviceRuntimeTest验证断秤空闲/运行/结束和恢复，必须从原始snapshot检查而非仅隐藏UI线条。
3. 后续在既有真实Mock开始/停止检查中加入无秤运行态，验证chartPoints无重量、三项机器值存在，再采集用户平板截图和字体证据。不能把第1/2步当截图验收完成。
4. 按实际图对照认可无秤稿，核对主数量语义、三指标、来源解释和固定停止。大字号/短高沿用现有门禁。

本次先完成第1/2步，保留取证缺口。运行指定单测及Mock/test APK构建；普通push现有分支。无真实连接/协议命令、安装、合并、发版、签名/schema变化；追加revert回退，不清用户记录。

## 实际截图接入

- LanguageActiveCupChecks新增visualEvidence模式，只测中文有秤/无秤两杯；原八语言检查默认分支不变。切换Mock状态后等待服务snapshot更新，调用既有startShot/recreate/固定停止检查，不直接灌画面数据。
- 采样到5秒且至少两点后核对机器三指标、重量有无和主数值标签，保存window.png、TaskTypographyEvidence和source.txt；finally恢复模拟连接状态、语言及曲线偏好。
- BrewAudioInstrumentation增加activeCupVisual入口，仅Mock包且明确disposableCiEmulator才运行。capture_mock_ui.sh末尾重新启动Home（负责Mock服务启动）后调用，120秒进程上限、结果标记必需，下载active-cup-visual。既有2560×1600/density360浅色环境沿用，所有旧截图/校验保留。
- 本地只bash语法/构建；运行和实图由CI完成，未完成前不宣称第3/4步通过。

## 主图高度测量补充

针对e15e00e实际运行图的主绘图区偏矮，截图前读取实际chart/legend/explanation/plotView/plotBounds/stop的屏幕像素边界，保存geometry.json及density/fontScale。使用已绘制的plotBounds，不以layoutParams估算；要求当前绘图区存在。仅instrumentation增加反射取证，不修改产品布局或放宽最小图高。后续按相同内容区与认可稿比较图高占比，并区分图例、说明、轴边距和外部操作区占高。
