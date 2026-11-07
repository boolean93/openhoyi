# 产品页与 README 增加赞助 / 交流群入口

遵循 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。

## 需求与范围

- 用户 2026-11-08 提供微信支付收款码与「OpenHOYI 交流群」二维码，要求放到 README 和 GitHub Pages，作为赞助与交流渠道；语气是“请我喝杯咖啡”，自愿赞助。
- 范围：`site/index.html`、`site/styles.css`、`README.md`、`site/assets/sponsor/wechat-pay.png`、`site/assets/community/wechat-group.png`、`scripts/test_product_pages.py`、本规划。不改 Android、协议、设备、部署工作流。
- 图片按用户提供的原图直用，不裁剪、不改色；README 与 Pages 共用同一份 `site/assets/` 资源。

## 来源与目标

- 来源：`origin/develop@ca3c0bb`。
- 工作分支：`feature/sponsor`（独立 worktree `openhoyi-sponsor`）；PR 目标：`develop`。
- 推送与合并：普通推送 → PR → 必需检查通过后 squash 合并 → Product Pages 部署。

## 改动内容

- Pages：FAQ 之后新增「支持与交流」区块，两列：微信支付码（请我喝杯咖啡）、微信群码（OpenHOYI 交流群）；手机上上下排列。文案注明“金额随意 · 自愿赞助”，群码注明会过期、失效后到 issues 留言。
- README：新增 `## 赞助与交流` 一节，并排展示两张码，附同样的说明。
- 测试：校验两张 PNG 存在、尺寸与页面 `width/height` 一致、页面与 README 均引用，并断言相关文案。

## 验证

- `python3 -m unittest discover -s scripts -p test_product_pages.py -v` → 14/14 通过。
- 本地 Chromium（CDP）1440/390：无横向溢出、无破图；「支持与交流」区块视觉复核。
- `git diff --check` 通过。

## 风险与边界

- 群二维码为 7 天有效（图内标注 11 月 15 日前），静态页/README 会随时间失效；文案已注明“会过期，失效后到 issues 留言”，后续更新需替换图片。
- 收款码属于用户本人主动公开的收款信息，按用户明确要求发布。
- 只改静态网页与图片；不连接设备、不发送蓝牙指令、不安装 APK、不改包名/签名/版本码。

## 发布与回退

- 不发布 APK、不打标签；合入后由既有 Product Pages 工作流部署并核对正式 URL。
- 回退：revert 本 PR 即移除赞助/交流区块与图片，无数据影响。
