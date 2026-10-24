# 曲线库压力语义与参数直达

沿用develop66ebda9+ed01de0来源feature/task-first-ui→develop PR#4及docs/git-workflow.md。依据curve-library-v2与10bf727真实选中窗口：曲线库大图/缩略图使用统一压力蓝，不按阶段混用物理量颜色；保留阶段编号、目标数值、断开的线，不模拟时间轨迹。CurveStageView增加显式pressureColor模式，仅曲线库启用，编辑/分享等既有阶段配色不受影响。

底部将原参数按钮实例移入横排操作区，与使用此曲线并列；原菜单仍调用同实例，原只读/可用性/保存选择回调不变。未选择或过滤掉选中项时两者隐藏。固定底部保持不滚走；TaskActionDock新增显式fullWidthContent=false选项，本页启用不收窄整行，其他页面保持原默认。原viewport夹具加强检查两个底部动作文字和边界。

验证Mock/AndroidTest构建、diff；现有CurveMenu/LanguageDetail/viewport短高和实际窗口截图继续验证，无单测镜像字号。四阶段新实图与大字底栏仍待CI，不能只靠构建称对齐。仅本地提交，下一完整批次推送。无设备/安装/合并/发版/数据变化；回退这两个页面展示改动。
