# 历史列表字体留白

390a7ae标准横屏正常态HistoryActivity截图与同源typography.json实测：density1.25/fontScale1；标题21.25px字形、lineHeight31px、控件62px；状态17.5px字形、lineHeight25px、控件51px，includeFontPadding=true。列表三文本绕过HoyiUi.label统一规则，造成一条记录占用过高。

保留17/14sp字号、全部状态/元信息/长备注，标题显式500、状态/元信息400，includeFontPadding=false；普通行透明，仅选中强调。最小64dp目标和列表分隔、长名两行/完整详情及列表恢复保持，不靠删字段或缩字解决。

遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。双Mock APK、现有长名/列表恢复/多语言单屏与真实截图测量。纯样式，不加镜像实现测试；不安装/合并/发版，不改数据/协议/签名。普通push、追加revert回退。本页右侧已选正常态、图和操作栏仍需完整视觉核对。

本地双Mock APK构建40秒成功，diff检查通过。保留全部文本与元信息；候选实际原生列表高度及恢复仍待新截图，不将旧62/51px测量当修正结果。
