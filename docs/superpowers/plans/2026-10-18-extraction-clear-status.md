# 实时萃取正常状态压缩

依据extraction-four-series-v3/no-scale-v2及784eaaf运行实图。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

当前宽屏在运行中始终单独显示“当前无告警”，与认可稿偏离且挤占绘图区。把已有compact运行模式的正常状态收起规则扩展到所有运行布局：仅active且bits精确0、时间不在未来且<=1500ms时隐藏；非零位、缺失位、无时间、过期/未来时间、非active全部保持显示。不改告警文案、控制门禁、停止按钮或未知状态提示。

MachineAlarms增加纯显示判定并覆盖全部16bit非零告警及时间边界；ExtractionActivity调用。运行取证断言继续要求停止完整、图表有真实数据，并额外验证新鲜无告警行收起。执行MachineAlarmsTest、Mock/test APK构建，再由CI生成实际图/几何；无设备安装/写入/发版/合并，不改schema/签名。普通push，追加revert回退。
