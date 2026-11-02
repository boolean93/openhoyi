# 高级页面验收对齐用户平板

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。831aa1c高级页面wideFont图虽表格完整，但脚本实际1600×1000/density200，逻辑1280×800大于用户1138×623可用配置，不能替代用户平板验收。将高级pages与business以及其后文档/自定义执行的模拟器配置统一2560×1600/density360；保留landscape/wideFont两档1.0/1.3、8语言×2主题×15页、18业务路径、文档/SAF/执行门禁及原超时。PNG检查从仅非空改为严格2560×1600，业务6图尺寸断言同步，保存adb实际override尺寸/密度为证据。既有其他紧凑/列表测试不删，不做竖屏。仅一次性CI模拟器，不运行本地adb，不安装用户设备。验证bash语法及嵌入Python语法；无需重建未改APK。下一批推送，不合并/发版/签名改变；回退脚本尺寸配置。
