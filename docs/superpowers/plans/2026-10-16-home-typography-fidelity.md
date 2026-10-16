# 准备工作台文字与操作对齐

依据brew-preparation-v2和固定字体实测，沿用develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4，遵守docs/git-workflow.md。

693f215用户平板HomeActivity-typography.json：本杯摘要36px/lineHeight52px，实际高130px（约58dp），includeFontPadding=true；当前私有text helper与HoyiUi.label的false不一致，产生额外留白。统一关闭额外font padding、显式400/700字重，相关文字总上下间距8dp。保留中文字体、字号、长文本、原控件和全部状态。主操作复用TaskActionDock对齐右栏，首页已有外边距不重复加；紧急停止仍原独立root安全区，不改确认/命令。

现有首页截图为空态，不能用它与已就绪概念稿做直接像素结论；已选曲线组件截图仅证明组件，不冒充完整页。待新实屏同状态对照。最近配方列表、完整就绪摘要及直接确认入口仍是其他未闭合差异，不因本次样式改动标首页完成。

验证Mock双APK、首页短高/1.3字号原生组件与窗口。保留旧CI不中断，不发版/安装/合并/改签名版本/数据；普通推送现有PR，追加revert回退。

本地双Mock APK成功41秒，diff check通过。首页新布局未获得原生截图，不当视觉验收。
