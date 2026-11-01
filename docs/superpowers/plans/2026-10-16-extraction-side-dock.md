# 萃取页右侧固定操作与主图高度

依据extraction-four-series-v3/no-scale-v2，e15e00e和aaf1c44实图显示整页底部动作行使左图提前结束。develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

新增专用TaskExtractionWorkspace，不改其他页面共享TaskWorkspaceLayout。宽横屏左侧读数/图表占完整高度，右侧工具局部滚动、操作固定底部；窄横屏保留主区、工具滚动区及底部固定操作，按实际操作自然高度预算，不缩字/缩点击区。固定操作从root移动到workspace，原按钮实例/回调不变。原准备/通知/告警三行移至workspace之前，全宽不进入工具滚动；未知/过期告警逻辑不变。

沿用TaskLayoutPolicy和原30%/280–320dp支持栏、24dp间距。新增运行取证断言：停止按钮全显；宽窗图表容器底部延伸到停止所在纵向区域。现有单屏/语言/大字号检查不放宽。构建Mock/test APK，CI实际图及几何再验；无设备操作/安装/合并/发版，签名/schema/协议不变。普通push，追加revert回退。
