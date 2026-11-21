# 设置页 B：固定分类导航

- 来源 develop `652de25f`、分支 `fix/settings-category-navigation`，最终目标 develop；给用户看实际 hosted Mock 画面后再按仓库标准开 PR。
- 六分类（设备、机器、萃取、外观、AI、维护）。宽窗固定本地分类栏；窄窗常显两行各三项、无需横划。内容为一个连续文档，上下自然滚动跨分类，目录高亮随动，不吸附、不循环；点击才定位，外部深链仍聚焦精确标题。保留主导航、设备栏、滚动锚点、原控制树、10 个设置深链、回退和输入草稿，不触发写入。
- 复用原有真实控件，包括连接、秤精度、机器/计划、外观、更新和诊断；保留 AI 独立授权与无 Key 安全态。不引入二套设置状态或改变硬件门禁、协议、凭据、命令及签名。
- 用独立的 JVM/Android Mock 验收精确深链、选项切换、宽/窄、明暗、重点控件；不操作本机模拟器或实体设备。
- 不手动发布；可用回退 PR 退回布局，既有设置与历史仍使用同一存储。

The wide pane uses the existing controls grouped by intent: observed boiler, light and schedule switches; two-state supply and mode selectors; unit-labelled temperature editors; palette and background previews; quiet route rows; and collapsed maintenance operations. Existing confirmations, readback, and write gates remain authoritative. QA captures the same wide layout in both themes, the ordinary phone, and the short English/Large viewport; category screenshots require the final anchor landing, not partial heading visibility.
