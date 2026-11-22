# 实时萃取曲线轴标题与留白

- 范围：首页及共享的实时萃取图。名称在上、单位在下，数字列只量刻度；靠近图边，保留完整时轴和图内末端读数。历史、对比、旧版详情保持不变。
- 来源：develop `652de25fb0b76d6c08438828722915b74b526a6e`；产品修复从该基线走 `fix/live-chart-axis-layout` → develop PR，仅带产品变更和可持续测试。独立 `fix/live-chart-axis-stack` 留作已批准的 Mock 取证，不把测试专用 workflow/runner 引入产品 PR。
- 验证：覆盖四/五轴、近平方窗口、短窄窗口、中文正常字和英文大字；核对 Canvas 可见文字、刻度及 plot 边界、时间锚点、原历史回归。使用 [云端 Android Mock](https://github.com/boolean93/openhoyi/actions/runs/44978463114) 获取原生画布画面，不是实机测试；本机不启动 AVD、不安装。
- 协议、数据与发布：仅绘图定位，不修改采样、单位、刻度/量程、BLE、安全门禁或签名和升级。不发版。不兼容时可代码回退，无须卸载或清记录。
