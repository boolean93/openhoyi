# 紧凑横屏锅炉区高度修正

- 基线：develop 4ffef608；分支 fix/compact-boiler-budget；PR 目标 develop。
- 来源：#151 合入后 Mock lifecycle 在 720×360 窗口发现首页快捷曲线被裁切。紧凑锅炉卡片将读数和开关操作并排，保留 48dp 开关和下方目标温度，不改变圆形仪表。
- 验证：本地完整 PR 检查；云端背景短横屏和 Mock lifecycle；不放松窗口裁切检查。
- 协议和设备控制不变；不手动发版或安装。回退使用 revert PR，保留历史数据。
