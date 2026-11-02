# 曲线集合视觉层级

已认可curve-library-v2使用轻量连续列表，只有选中行有明显底色；e6eedb8用户平板原生空选择截图却每行圆角描边卡片、缩略图绿底。沿用develop66ebda9+ed01de0依赖、feature/task-first-ui→develop草稿PR#4，遵守docs/git-workflow.md。

普通行改透明底/1dp分隔，缩略图中性surface底；选中行保留强调底并加accent描边、显式isSelected语义。保留原生selector、最小行高、两行长标题、阶段参数、列表复用与滚动/排序/恢复，不把阶段片段连接成伪造时间轨迹。图中尚未选择，不能用它证明已选右栏对齐。其他差距继续保留，不能将本改动视为整页验收。

仅样式；双Mock APK及后续原生列表恢复/主题/大字体和选中状态截图验证。不改协议、数据、排序，不安装发版合并；普通push/PR，revert回退。

本地双Mock APK构建40秒成功，diff check通过。当前e34f8cd截图与single-screen任务仍运行；本列表候选与复盘上下文尚未推送，最终效果/列表恢复需下一批原生证据。

## 详情主操作

实读e6eedb8 Capture的curve-detail-dark.png：已选采集曲线2缺阶段参数，因此保留文字缺失说明、不编造图；底部使用此曲线横跨整个详情栏，与认可稿右下有限宽主操作不一致。复用TaskActionDock，详情栏宽至少400dp且整窗至少840dp时采用280–320dp右对齐，窄详情全宽；原其他页面624dp阈值不变。短高预览预算改测整个dock，包含padding及文字换行高度。选择/保存偏好/返回逻辑及未验证曲线禁用规则均不变。需要双APK与后续原生选中恢复/大字体/短高图表证据。

曲线详情dock候选双Mock APK40秒构建成功，diff check通过；尚无候选原生图。

## 搜索与分类字体留白

7d9c1a6用户平板原生测量：全部分类15sp/400，lineHeight49px，但includeFontPadding=true、上下18px，实际134px高（约59.6dp）；管理按钮已108px=48dp。搜索和分类仍继承额外CJK font padding，与已统一的文字控件不一致。明确400字体并关闭额外font padding，保留搜索16sp、分类15sp、上下8dp和48dp最小高度及自然增高；不固定高度、不缩字。分类同时同步原生isSelected语义，不只靠颜色。排序、搜索、防丢恢复不变。需双APK和新原生测量/列表恢复验证，未据代码认定筛选区完成。

搜索/分类候选双Mock APK40秒构建成功，diff check通过；尚未推送或取得新原生尺寸，不将59.6dp已降低作为完成结论。
