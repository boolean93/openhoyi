# Pages 产品站重新设计

- 需求与范围：重做 OpenHOYI GitHub Pages 首页的内容、信息架构与视觉，参考 Linear、Raycast、mac-stats、Rectangle 的产品介绍页；保留既有事实边界、截图来源与下载安全说明。曲线导入指南同步换用新视觉。用最新 develop Mock 的本机平板截图替换站点四张展示图，并按实际情况重写截图来源记录。
- 来源分支/基线提交：develop + `e5cd67ac54a547706b8712675fcbacb4fe935ad7`。
- 工作分支：`docs/pages-redesign`；工作树 `.agents/worktrees/pages-redesign`。
- PR 目标：develop。
- 改动文件与步骤：`site/index.html`、`site/styles.css`、`site/product.js`、`site/import-official-curves.html`、`site/import-guide.css`、`site/assets/screens/*.png`、`site/assets/screens/provenance.json`；更新 `scripts/test_product_pages.py` 的文案/结构/来源断言并保留资产与部署边界检查。
- 推送与合并：本地验证 → 普通推送工作分支 → 草稿 PR → 必需检查通过后由用户确认合并；不直接推送 develop/main。
- 验证证据：`python3 -m unittest discover -s scripts -p test_product_pages.py -v`；本地静态服务 + 浏览器桌面/移动截图；链接与资产完整性检查。

## 截图更新（2026-11-20）

- 来源：最新 develop `e5cd67ac`；工作树当时 HEAD `05053987`，App 源码路径相对 `e5cd67ac` 的 diff 为空。
- 构建：`assembleMock`，`-PhoyiVersionCode=164 -PhoyiVersionName=0.2.0-mock.164`；APK SHA-256 `c3fed7072a9f0a1401461ccd8ccab43bad866697a6214cd1cd1635dc246ee254`；签名与已装 Mock 相同。
- 安装：`adb install -r` 覆盖同 versionCode；未卸载、未清数据。
- 采集设备：M2105K81AC（Xiaomi Pad 5），Android 13，2560×1600 横屏，density 360。
- 采用图：`home`（浅色首页）、`extraction`（浅色模拟萃取中）、`editor`（深色曲线编辑）、`history`（浅色历史）。原图 2560×1600；展示图连续裁去系统栏为 2560×1504，边界 `[0,60,2560,1564]`。
- `provenance.json` 升到 schema 5 / `local-tablet-mock-screenshots`，记录设备、APK 哈希与签名、源码基线、每张图哈希与裁剪边界；不再沿用旧的 CI 模拟器来源字段。
- 页面文案改为“本机 Xiaomi Pad 5 安装的模拟演示版”，明确模拟数据、非真机萃取或硬件验收；不把本地采集写成 CI 或可重现构建。
- 文案二次修订（2026-11-21）：去掉对仗口号和“让／把／从…到…”式营销句，首屏使用“连上 OpenHOYI，冲好这一杯”，段落改成更口语的短句；不改变任何事实边界。
- 色调二次调整（2026-11-21）：由深烘咖啡色改为浅色瓷白／鼠尾草绿／深墨；首屏、对照、边界、下载、AI 和页脚改为浅色表面，四张展示图统一为浅色（曲线编辑页也在浅色主题下重拍），页面不再有大块深色表面。
- 协议与设备风险：仅静态网站，无协议、设备、控制或数据路径改动；不改变发布包、签名、版本或固件门禁。
- 发布：不发 APK、不打版本标签；合并 develop 后由既有 Product Pages workflow 部署。
- 回退：回退站点文件与测试文件即可；不涉及数据迁移或设备状态。
- 未完成与阻塞：无。
