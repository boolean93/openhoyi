# 旧版曲线详情参数分组

沿用develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守docs/git-workflow.md。依据legacy-curve-v1和051f16e选中恢复实图，当前整段17sp文字缺少参数层级。

在原局部滚动容器中重建展示：24sp名称、14sp分类/来源/原位置、2×2参数（温度/水量/目标重量/阶段数），14sp标签/24sp值、16dp内边距和8dp格距。只读提示常显；旧完整说明保留在查看详情弹窗。null保持未知，不构造数值或把旧曲线用于控制。原选择索引、指纹、列表滚动/重建、导入流程不动。

更新选中重建回归为比较全部可见文本及原完整说明，而非只比标题，保留列表恢复路径。Mock/test APK构建、资源解析、diff/branch-policy，实际选中截图与大字号待CI。无协议/签名版本schema/安装/机器操作/合并/发版；普通push，追加revert回退。
