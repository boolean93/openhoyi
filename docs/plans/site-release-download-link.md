# 产品页下载入口改为 GitHub Releases

遵循 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。

## 需求与范围

- 用户反馈：页面“下载测试版”写死指向 `v0.2.0-rc.2`，实际最新发布已是 `dev-56`（2026-11-06 13:30），静态版本链接会过时。
- 用户选择方案 C：按钮直接指向 GitHub Releases 页面，由用户在该页选择版本，不再写死某一个 APK。
- 范围：`site/index.html` 下载区与兼容区文案、`scripts/test_product_pages.py`、本规划。不改部署工作流、不引入运行时请求、不改 Android/协议/设备。

## 来源与目标

- 来源：`origin/develop`（`0cef488`）。
- 工作分支：`fix/site-release-download-link`；PR 目标：`develop`。
- 推送与合并：普通推送 → PR → 必需检查（branch-policy/verify/lifecycle/upgrade）通过后 squash 合并；合并后由既有 Product Pages 工作流部署。

## 改动内容

- 下载按钮与页尾链接改为 `https://github.com/boolean93/openhoyi/releases`，删除固定 APK 直链、固定版本号/日期/大小/SHA-256。
- 3 条短提示改为：支持范围随版本变化（以下载版本说明为准）、留在机器旁并保留机器停水方式且不重发指令、截图与下载版本可能不同。
- 兼容区与电子秤表说明改为“不同版本包含的型号不同，以下载版本的发布说明为准”，不再断言“本页测试包只支持 BOOKOO”。
- 折叠区改为“下载前核对”（包名、在 Releases 核对版本码/签名、核对 SHA-256），保留“不是正式发行版、源码提交映射由构建方声明”。

## 验证

- `python3 -m unittest discover -s scripts -p test_product_pages.py -v` 全通过（12 项，含“页面不得出现固定 .apk 直链”“支持范围随版本变化”的断言）。
- 本地 1440/390 渲染检查下载区；`git diff --check`。
- 合并后核对正式 URL 与 Releases 链接可用。

## 协议与设备风险

只改静态网页文案与链接；不连接设备、不发送蓝牙指令、不安装 APK、不改协议门禁。最新 `dev-56` 已由用户授权先装到平板并在设备上核验，与本页改动独立。

## 发布与回退

- 不发布 APK、不打标签、不改签名/版本码；仅合入后由既有 Pages 工作流部署。
- 回退：正常 revert 本 PR 即可恢复上一版下载入口。

## 未完成

- 未做运行时/构建时自动解析最新 release（用户选择方案 C，后续需要时再做）。
