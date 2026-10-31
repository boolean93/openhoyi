# 历史主读数单位层级

沿用develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守docs/git-workflow.md。依据history-detail-v2和63a490c实际history-detail-dark.png，时长/重量数字与单位目前同大。

仅独立详情页的两个40sp主读数应用0.55倍单位span，数字保持40sp/700；匹配当前Locale.ROOT格式数字前缀，兼容日/韩无空格单位，未知破折号不修改。字符串及无障碍读法不改，不改采样/结束结果/库存。图表偏矮另查workspace/固定底部布局，不以缩小数字替代。

验证Mock/test APK构建、diff/branch-policy；八语言/浅深/大字号和同状态截图仍待CI。无协议、签名/版本/schema改动、安装/设备操作/合并/发版；普通push，追加revert回退。

运行补证：712913e/run39085501557用户平板深色history-detail-dark.png已读取，主单位缩小/图表延伸页底/右侧分隔均已生效；非有复盘内容状态，不代表整页最终验收。
