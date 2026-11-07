# 介绍页接入新的 App 图标

遵循 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。

## 需求与范围

- 用户 2026-11-07 提供了新的 App 图标设计（黑色正式版、橙色 Mock 版，见 `docs/ui/app-icons/`），要求把新版图标体现到产品介绍页上。
- 范围：`site/` 的品牌资产（favicon / apple-touch-icon / 图标展示）、首页与新增的“两种构建”区块、`scripts/prepare_site_brand.py`、`scripts/test_product_pages.py`、`docs/ui/app-icons/README.md` 的网站说明。不改 Android、协议、设备、部署工作流。
- 原则：图标一律由已提交的 1254×1254 设计稿等比缩放生成，不重绘、不改色；脚本固定设计稿 sha256，避免用错源图。

## 来源与目标

- 来源：`origin/develop@e4c16a0`。
- 工作分支：`feature/site-brand-icon`；PR 目标：`develop`。
- 推送与合并：普通推送 → PR → 必需检查通过后 squash 合并 → Product Pages 部署。

## 改动内容

1. `scripts/prepare_site_brand.py`：从 `docs/ui/app-icons/icon-release.png`、`icon-mock.png` 生成
   `site/assets/brand/` 下的 256px 图标、32px favicon（圆角透明边）、180px apple-touch-icon，
   并写 `manifest.json`（源图/输出 sha256 与尺寸）；`--check` 校验提交物。
2. 首屏：标题上方放正式版 App 图标（112px，CSS 圆角 + 柔和阴影）。
3. 新增“两种构建，一眼分清”区块：并排展示黑色正式版与橙色 Mock 图标，说明 Mock 用模拟设备、用于试界面和截图，两个 App 可同时安装；注明本页截图来自 Mock 演示包。
4. `<head>`：favicon 换成新图标 PNG（32px + apple-touch-icon），删除旧的 `site/assets/mark.svg`。
5. 测试：新增品牌资产校验（manifest 哈希/尺寸、页面引用、无未用文件），合计 13 项。

## 验证

- `python3 -m unittest discover -s scripts -p test_product_pages.py -v` → 13/13 通过。
- `python3 scripts/prepare_site_brand.py --check` → 提交物与设计稿一致。
- 本地 Chromium 渲染 1440/834/390：无横向溢出、无破图；首屏图标、两种构建区块视觉复核。
- `git diff --check` 通过。

## 协议与设备风险

只改静态网页与品牌图片；不连接设备、不发送蓝牙指令、不安装 APK、不改包名/签名/版本码。图标设计稿来源与哈希由脚本固定，不使用重绘或生成图。

## 发布与回退

- 不发布 APK、不打标签；合入后由既有 Product Pages 工作流部署。
- 回退：revert 本 PR 即恢复旧的 `mark.svg` favicon 与不含图标的首屏；无数据影响。

## 未完成

- 未在 Windows/Safari 上核对 favicon 的实际显示；未做 OG 分享图（现有分享图仍用首页截图）。
