# 导入预览分栏与图表标题

沿用feature/task-first-ui（develop66ebda9+ed01de0）→develop PR#4与docs/git-workflow.md。依据curve-import-preview-v1左65%/右35%和已有63a490c冲突态实图（当前仅冲突警示背景已改变），将本页TaskWorkspaceLayout支持区设35%、上限400dp；其他页不变。压力图上增加共享20sp阶段压力目标标题与8dp间距；无压力图不增加标题。原160dp图表预算、阶段表条件隐藏与完整详情入口保留；标题计入原预算。复用八语言已有标题，不改变副本/冲突/保存动作或阶段颜色。Mock/AndroidTest构建及diff；待新版含数据截图、大字/短高检查，不能把4d通用错误状态当正常页证据。不安装/合并/发版/签名变化，本地候选下批推送；回退本页展示变化。
