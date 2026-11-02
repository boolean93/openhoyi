# 批次详情 v2 收尾布局

沿用 develop66ebda9+ed01de0 → feature/task-first-ui → develop draft PR#4；遵守docs/git-workflow.md。依据bean-batch-v2及63a490c业务截图。

1. 底部次级流水入口与“数量变更保留流水”说明分组，主调整操作仍限宽靠右。
2. 进度轨道/填充均圆角8dp高，消除平台默认进度条内缩造成的细线。实际百分比不截断，图形仍限0–100%，不改库存。
3. 保留长名称详情、长原因行点击详情，日期/流水及调整预览确认不变。核对八语言资源、构建Mock/instrumentation；实际用户平板与大字号截图待CI。

仅展示修改，不安装、不设备写入、不合并/发版，不改签名/schema。随下一批普通push，回退本批代码即可。

已实现底部说明及8dp圆角进度条；八语言XML解析、diff及分支规则检查通过。Mock/MockAndroidTest构建成功45s，日志`/tmp/task-batch-detail-v2-build.log`。实图仍待后续CI，不以构建代替视觉通过。
