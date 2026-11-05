# OpenHOYI 产品介绍与 GitHub Pages

- 需求与范围：用户要求为 OpenHOYI GitHub 制作产品介绍 Pages 并部署。制作中文响应式静态站，介绍产品、核心流程、兼容性、Alpha 风险、源码试用和贡献入口；启用 GitHub Actions Pages。只部署静态站，不发布应用。
- 来源分支/基线提交：develop / 3f9df291ad23127faf16038d73492efa3e975e2d。
- 工作分支：feature/product-pages；独立工作树 openhoyi-product-pages，不改动其他工作树。
- PR目标：develop。
- 改动文件与步骤：`site/` 静态站、`scripts/test_product_pages.py` 最小静态检查、`.github/workflows/product-pages.yml` 验证和部署、README 网站入口、本规划。
- 分工：Sol 实现/验证/部署；实际模型 GPT-6 Astra 负责只读设计建议与独立体验复核。用户最终确认成品，不以智能体复核代替用户确认。
- 体验验收：首屏可识别产品/Android/Alpha；清楚分开代码能力与实机验收；主 CTA 到试用说明，GitHub/文档/反馈链接可用；桌面和 390px 手机无横向溢出，键盘可访问；示意图清晰标明非截图；线上 HTTPS 可访问且资源无 404。
- 推送与合并：检查工作树 → 本地最小验证及 Astra 复核 → 普通推送工作分支 → PR 到 develop → branch-policy、verify、lifecycle、upgrade 以及新增页面检查通过 → PR 合并。不绕过保护，不直推 develop/main。
- 部署：仓库 Pages build_type 设置为 workflow；合入 develop 后由官方 configure/upload/deploy-pages Actions 发布 site；仅 develop 的 push 或 develop 的手动运行具有部署权限。PR 仅验证，不部署。仓库主页指向部署 URL。
- 验证证据：`python3 -m unittest discover -s scripts -p test_product_pages.py -v` 四项通过；`git diff --check`、`python3 scripts/git_policy.py branch` 通过。Chromium 在 1440/768/390/360 宽度均无横向溢出，禁 JS 下构建锚点和键盘 skip link 通过；本地截图/记录在 `build/product-pages-evidence/`（忽略目录，不加入网站）。页面 9 个唯一外链目标均 HTTP 200。实际 GPT-6 Astra 已查看桌面/手机截图并独立只读复核 HTML/CSS/工作流，结论：无阻塞问题，可进入 PR/上线流程；不代表用户最终确认或硬件验收。PR CI 和最终部署结果记录于 PR 与 Actions，不在部署前预填成功。
- 协议与设备风险：不修改 Android、协议、设备会话、控制门禁、未知记录和重试；网页不连接蓝牙、不发送设备命令。兼容信息以已合入 develop 的 README/验收文档为准，不把其他分支未合并适配宣称已支持。
- 发布：用户明确授权介绍站部署。无 APK 发版、无版本标签、无签名或升级变更、无安装或硬件操作，不宣称完成硬件验收。
- 回退：通过新 revert PR 撤回网页改动，CI 通过合入后重新部署；紧急时可在 GitHub Pages 设置取消发布网站。不删除设备数据，不改写共享历史。
- 未完成与阻塞：Pages 已启用 `build_type=workflow`、HTTPS 强制开启，`github-pages` environment 仅允许 develop 部署（API 已核对）。尚待 PR 四项必需检查、合并、首次部署与线上验证；目标地址 `https://boolean93.github.io/openhoyi/`，未验证前不声明上线成功。

具体规则以 [Git工作流](../git-workflow.md) 为准。
