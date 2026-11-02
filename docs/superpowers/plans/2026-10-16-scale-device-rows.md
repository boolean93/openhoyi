# 电子秤设备信息对齐行

沿用批准的scale-v2，修正f525db3用户平板截图中设备区散落说明。遵守docs/git-workflow.md；来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4。

- 电量、数据状态两行，左标签14sp/右值16sp，35%/65%分配，8dp上下留白、48dp最小行高及细分隔线；值右对齐且允许换行。原设备标题及连接/设置入口保留。
- 只展示现有live电量/能力与freshness，缺失电量继续完整解释，Mock/断连/过期/只读状态不隐藏。概念稿型号无对应可靠字段，本轮不造型号，不编造稳定读数。
- 增补8语言字段标签与纯百分比格式，保留旧scale_battery_format供其他用途。仅更改ScaleActivity展示，不改服务、协议、门禁、计时和去皮。
- 验证资源XML、Mock/test APK编译、diff/分支规则；全语言/大字号/短高实图待CI。可逆样式改动不加镜像实现的测试。不安装/合并/发版，不改签名/schema；普通push现有PR，回退追加revert。
