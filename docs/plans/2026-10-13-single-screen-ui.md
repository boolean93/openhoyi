# 单屏UI优化设计（用户已修订）

## 目标与验收定义
除AppSettingsActivity、MachineSettingsActivity、ScaleSettingsActivity三个设置页外，所有主要功能页取消整页纵向滚动。固定标题、必要状态、主功能区和主要操作同时可见；状态、开关和次要操作不占整张大卡片。禁止用裁切、隐藏故障、缩小到不可读或取消功能伪造单屏。
无限列表以局部独立滚动呈现；完整内容可经详情弹窗查看。输入法开启时使用窗口resize及聚焦输入步骤，不压缩图表或丢弃输入内容。安全告警与停止按钮不藏进更多菜单。

## 方案选择
A 推荐：固定骨架、弹性主区、紧凑状态带，平板双栏，手机按任务分区；列表局部滑动与辅助详情弹窗。
B 仅减小字体/间距：改动少，但长页仍超高且可读性差，不能满足目标。
C 单纯折叠卡片：保留长页结构，频繁展开、仍可能超高，不作为主方案。

## 页面分组
1. HomeActivity：移除大机器示意图；机器/秤连接状态并排状态带；温度/压力紧凑读数；当前曲线与准备操作突出；快捷预设横向小入口；秤/投粉入口保留。设备控制与断开移入明确的设备弹窗，不丢功能。
2. ExtractionActivity：顶部曲线/准备状态与紧凑关键读数，压力与杯中流速叠加同一图表，分别标注物理轴，并占主要剩余高度。选曲线/豆子/历史参考为紧凑工具栏，阶段目标及来源说明以详情呈现；准备/启动/停止固定可达，所有门禁原样保留。
3. CurveActivity：搜索/过滤/排序合并工具栏，左侧独立滚动列表＋右侧固定大预览，编辑/复制/分享/使用清晰分级。不能丢排序或只读能力提示。
4. HistoryActivity、BeanInventoryActivity、BeanLedgerActivity：局部滚动列表，摘要与工具栏紧凑；库存新增、使用、调整和流水入口保留。
5. HistoryDetailActivity、BrewComparisonActivity：图表优先，紧凑摘要、固定复盘/导出等入口；长字段查看弹窗，原始完整内容不截断存储。
6. ScaleActivity：重量为主，计时其次，连接与协议能力紧凑，归零门禁及本地计时来源明确。ScaleConnectionActivity局部滑动设备列表，连接选择不变。
7. CurveEditorActivity、BeanEntryActivity、BeanBatchActivity、BeanPreparationActivity、BrewReviewActivity：平板网格表单，手机分组/步骤切换但页面本身不滚动，固定保存操作；所有原字段与未保存内容保留，库存扣减确认/idempotency不变。
8. CurveShareActivity、CurveImportActivity、CurveImportPreviewActivity：配方预览主体，传输入口紧凑；原始文本独立编辑/查看面板，导入预览与明确保存分开，不自动选择或执行。
9. LegacyCurveActivity、LegacyHistoryActivity、LegacyHistoryDetailActivity：仍可达则同样处理，不能以遗留命名排除；保持兼容数据和读写边界。

## 分支与交付
origin/develop为66ebda95dc7986eec66f9fa0b7e6cbd565c74506。高级功能7fe992f尚未合入develop：从develop建feature/single-screen-ui，再显式合并高级功能分支作为依赖，绝不直接修改main/develop或擅自合并PR2。新PR目标develop，注明依赖。不公开发版；用户已经授权平板侧载查看，可在同签名与递增版本核对后更新测试包，不卸载清数据。

## 验证
实机小米M2105K81AC Android13，横/竖屏；模拟器紧凑手机与平板、大字体、8语言、浅深色。逐页检查主区域/操作在窗口内、无整页scroll range、图表非零、双轴单位正确且无重叠。列表验证独立滚动和状态恢复，表单验证完整保存。现有协议、库存、使用次数、日志、启动确认、未知写入与停止回归保持，不为布局放松业务断言。平板安装和页面显示不代表机器验收。

## 实施顺序
公共固定骨架及尺寸预算 → 首页/萃取 → 曲线/历史/库存 → 表单/分享/秤 → 遗留页 → 全页面布局与业务回归 → 平板同签名升级/截图 → PR与CI。

## 实施任务与责任

- [ ] 公共布局：固定根内容、状态紧凑，图表尺寸按实际剩余高度；不影响设置页。
- [ ] 实时萃取：研究旧HOYI叠加轴；压力/杯中流速共用时间轴，独立物理量比例，断点与参考虚线保留；固定开始/停止，原门禁保留。
- [ ] 首页/秤/遗留页：去大图，紧凑状态与开关，保留设备入口与控制。
- [ ] 曲线/历史：固定主框架，列表局部滚动，右侧预览/图表优先；完整功能保留。
- [ ] 库存/表单/分享：单屏网格或分组步骤，输入保留、完整字段、固定主要操作，不藏库存确认。
- [ ] 验证：先做相关布局/投影失败用例；实现后运行mobile Debug JVM、编译Debug/Mock/androidTest及原业务验证；平板逐页测量＋截图，横竖屏和大字体；现有Mock CI全门禁。
- [ ] 独立规格审查与质量审查，修正后普通推送feature/single-screen-ui、PR至develop，不合并/不发版；同签名递增versionCode侧载平板经用户授权，不能卸载或清数据。

执行使用subagent-driven-development；多个工作者各自只改分配页面，根agent负责Gradle、协议边界与最终整合。验收21非设置页面，不以简单grep或旧组件测量替代真实窗口验收。
