# Pages 产品站重新设计

- 需求与范围：重做 OpenHOYI GitHub Pages 首页的内容、信息架构与视觉，参考 Linear、Raycast、mac-stats、Rectangle 的产品介绍页；保留既有事实边界、截图来源与下载安全说明。曲线导入指南同步换用新视觉。
- 来源分支/基线提交：develop + `e5cd67ac54a547706b8712675fcbacb4fe935ad7`。
- 工作分支：`docs/pages-redesign`；工作树 `.agents/worktrees/pages-redesign`。
- PR 目标：develop。
- 改动文件与步骤：`site/index.html`、`site/styles.css`、`site/product.js`、`site/import-official-curves.html`、`site/import-guide.css`；更新 `scripts/test_product_pages.py` 的文案/结构断言并保留资产、来源、部署边界检查。
- 推送与合并：本地验证 → 普通推送工作分支 → 草稿 PR → 必需检查通过后由用户确认合并；不直接推送 develop/main。
- 验证证据：`python3 -m unittest discover -s scripts -p test_product_pages.py -v`；本地静态服务 + 浏览器桌面/移动截图；链接与资产完整性检查。
- 协议与设备风险：仅静态网站，无协议、设备、控制或数据路径改动；不改变发布包、签名、版本或固件门禁。
- 发布：不发 APK、不打版本标签；合并 develop 后由既有 Product Pages workflow 部署。
- 回退：回退站点文件与测试文件即可；不涉及数据迁移或设备状态。
- 未完成与阻塞：无。
