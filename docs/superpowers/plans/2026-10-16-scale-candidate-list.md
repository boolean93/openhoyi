# 电子秤连接候选列表

规格§7/F7，已认可 scale-connection-v1。继续 develop66ebda9 + ed01de0 依赖的 feature/task-first-ui → develop，draft PR#4，遵守 docs/git-workflow.md。

9d457ef wideFont 中文截图中扫描/连接管理独占两行，候选仍是全宽居中按钮。改为并列工具操作和局部连续候选列表：设备名在前，地址/RSSI次行，实验性只读提示归属对应候选。原生Button保留键盘/无障碍操作，使用平面行和触摸反馈，不缩短名称，不隐藏协议限制。扫描为唯一主操作。重复轮询只更新变化的文本。

责任：ScaleConnectionActivity及8语言候选元数据资源。原 service owner、权限/活动门禁、候选筛选/顺序、connectScale callback、savedScrollY 不变。原40候选禁用/滚动恢复测试仍保留。

本地JVM/两APK编译后普通推送草稿PR；CI原生多语言/字体/窗口/40候选恢复、截图人工检查及独立审查待完成。不增加镜像实现的单元测试。不合并、不发版、不改签名/版本、不安装设备；无协议/数据schema改动。回退采用追加revert，保留数据和未知记录。
