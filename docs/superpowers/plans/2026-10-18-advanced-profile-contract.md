# 高级页面平板配置契约修正

feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。39114111354 pages在首个BeanInventoryActivity开始时失败：真实1138dp/font1.0与AdvancedPageChecks仍硬编码1280dp不符，尚未执行页面检查；device-size/density确认2560×1600/density360。修复测试遗漏而非产品布局：非compact期望宽度改1138dp，额外要求densityDpi=360；保留compact360dp和所有字号/语言/主题/布局/持久化检查。不放宽为任意宽度，不缩减480夹具。

验证MockAndroidTest构建、diff及完整脚本配置搜索；与复盘单位调整合批普通push。新的实际页面结果仍须运行，不能称已通过。不安装、不操作设备、不合并/发版/签名变化；回退追加revert。
