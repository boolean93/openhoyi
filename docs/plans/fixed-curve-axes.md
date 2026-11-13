# 实时曲线固定坐标轴

- 需求与范围：设置页增加默认关闭的固定坐标轴开关；显示偏好持久化，只影响实时曲线，原动态轴算法和历史/对比保持不变。
- 来源分支/基线提交：develop / 31e1ea9。
- 工作分支：feature/fixed-curve-axes；PR 目标 develop。
- 改动：纯显示范围策略、投影独立固定分支、设置页统一 HoyiSwitch、图旁越界提示、中英资源、针对性回归。
- 固定刻度：时间 0–60 s；压力 0–12 bar；水量 0–100 ml；流量 0–4 g/s；温度 80–100°C；出液量 0–60 g。它们只是显示范围，不是能力或控制阈值。当前与参考超界分别提示，不自动扩轴、不夹平数据；数据仍完整保留。
- 推送与合并：本地验证后通过 scripts/create-pr 创建草稿 PR，精确头提交 branch-policy 和 verify 通过后正常合入，不绕过保护。
- 验证：固定范围、默认动态等价、超界含参考与隐藏指标、读数真实、历史不变；字号/深浅主题沿用统一样式。实际执行结果随后记录。
- 协议与设备风险：仅展示及设置，不改变蓝牙协议、采样存储、萃取/预热/秤控制；不操作真实机器。
- 发布：不主动发版、安装或触碰签名；仅 PR 流程。
- 回退：revert PR，新增偏好可忽略；不清数据。
- 独立评审：Astra 只读设计评审建议固定范围与常驻裁剪提示；完成后再审实现。

## 首轮验证

- 已通过：`:mobile:testDebugUnitTest` 精选 FixedChartAxes、FourMetricChartProjection、LiveAxisAnimation、LiveEndpointLabelGeometry 单测；含 Debug 代码编译。
- 已通过：`git diff --check`、`localization/android_resources.py --check`。
- Astra 实现静态复核通过；修正历史越界提示语义及紧凑布局提示占高。
- 云端 `adb devices -l` 无设备；新增 Mock 绘制探针，尚未运行。窄横窗大字号实际屏幕效果待验证，不以编译替代。
