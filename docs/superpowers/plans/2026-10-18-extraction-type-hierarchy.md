# 萃取读数层级修正

依据已认可task-first-ui-design第4/5节、满配v3与637f83f实图差异。沿用develop66ebda9+ed01de0来源feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

1. TaskVisualTokens增加LIVE_FOCUS_VALUE_SP=48、LIVE_UNIT_SP=22，仅ExtractionActivity主值用48；单位span按22/48计算，其他页面40sp不变。
2. ExtractionOverlayView的explanation/referenceCaption由11sp改SUPPORTING_SP=14；水平及底部padding按RELATED_GAP_DP=8。坐标轴字号不在此次改动中，仍需独立核对；不改变采样/量程/缺口/参考来源。
3. 构建Mock及AndroidTest，diff与分支检查；已有Capture及frame/single-screen夹具继续验证有/无秤、参考、大字、短高、停止可见性，追加分段字号证据。构建不代替运行验收。不增加镜像实现的字号单测。
4. 与秤设置和分段取证整批推送，不取消已有CI。无设备命令/安装/合并/发版，签名版本schema不变。回退此字号与padding提交，不改变设备控制。
