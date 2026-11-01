# 电子秤主读数字号层级

依据已认可scale-v2及f525db3用户平板实图，主数字与单位不应同等强调。遵守docs/git-workflow.md；来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4。

主读数保持64sp/窄窗48sp及700字重；仅单位缩至主字号45%，保留系统fontScale与同一TextView的完整本地化文本。通过现有scale_weight_format空参数取得各语言单位后匹配后缀，不硬编码g；缺失读数仍为—，不添加假数值。不修改采样、去皮、计时、设备门禁或趋势数据。

验证Mock/test APK编译、diff、分支规则，后续CI检查8语言/大字号可读性及真实截图。此为可逆视觉改动，不新增镜像实现的测试。不发版/合并/安装，不改签名/schema；普通push至现有PR，回退追加revert。
