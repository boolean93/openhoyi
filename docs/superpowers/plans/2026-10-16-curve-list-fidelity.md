# 曲线集合视觉层级

已认可curve-library-v2使用轻量连续列表，只有选中行有明显底色；e6eedb8用户平板原生空选择截图却每行圆角描边卡片、缩略图绿底。沿用develop66ebda9+ed01de0依赖、feature/task-first-ui→develop草稿PR#4，遵守docs/git-workflow.md。

普通行改透明底/1dp分隔，缩略图中性surface底；选中行保留强调底并加accent描边、显式isSelected语义。保留原生selector、最小行高、两行长标题、阶段参数、列表复用与滚动/排序/恢复，不把阶段片段连接成伪造时间轨迹。图中尚未选择，不能用它证明已选右栏对齐。其他差距继续保留，不能将本改动视为整页验收。

仅样式；双Mock APK及后续原生列表恢复/主题/大字体和选中状态截图验证。不改协议、数据、排序，不安装发版合并；普通push/PR，revert回退。

本地双Mock APK构建40秒成功，diff check通过。当前e34f8cd截图与single-screen任务仍运行；本列表候选与复盘上下文尚未推送，最终效果/列表恢复需下一批原生证据。
