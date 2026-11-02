# 中文字重共享字体族

失败证据：1c256c8 task-frame38869781029在HomeActivity的TaskTypographyEvidence.capture首次加载300字重时OOM，17,772,319字节字体buffer分配失败，192MiB堆仅余约2.4MiB；未运行到库存页面。主题已用含四字重的task_chinese_family，TaskTypography却再次加载四份独立XML，各自指向同一17MB可变字体。缓存按字重只能避免重复调用，不能消除跨资源重复字体buffer。

API28+改为复用主题同一family资源，再Typeface.create(family,weight,false)，仍缓存300/400/500/700结果；字体文件/变体/主题不变。API26–27没有精确weight重载，保留旧路径，不声称旧系统内存优化。不得删除字体证据断言、换字体、放宽堆或缩减截图。

遵守docs/git-workflow.md；沿用feature/task-first-ui（develop66ebda9+ed01de0依赖）→develop PR#4，普通push。双Mock APK构建，重跑既有字形宽度/实际字重/主题粗体/全页截图检查；原失败为红证据，候选构建不算native绿证据。无BLE、签名/schema变化，不安装、不合并发版；追加revert回退。
