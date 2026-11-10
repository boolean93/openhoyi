# Pages 产品介绍改版：按 2026-11-10 内容交接稿重组

- 需求与范围：用户要求依据《OpenHOYI-DeepSeek-内容交接稿-2026-11-10.md》更新 GitHub Pages 产品站，先在本地查看效果，确认后再合入部署。范围限静态站 `site/index.html`、`site/styles.css`、`site/product.js`、`scripts/test_product_pages.py` 与本报规划；不改 App/协议/README/发布流程，不发布 APK、不安装、不操作设备。
- 来源分支/基线提交：`origin/develop` + `f8da4daca3c326b86275a020e3ca6ffb0c6aa01d`（交接稿事实基线；站点现有截图来自 App 合并提交 `58bbd96f`）。
- 工作分支：`docs/product-story`；独立工作树 `/Users/boolean93/workspace/hoyi_app/openhoyi-product-story`，不修改其他工作树或未提交改动。
- PR目标：`develop`。
- 改动文件与步骤：
  1. 按交接稿第三部分重组单页为 10 个内容区块：首屏、四个核心特色（连接/配方/两杯对比/电子秤）、补充功能、界面展示、兼容设备、下载与开始、交流与赞助，并保留截图来源、风险声明与数据边界。
  2. 视觉沿用 App 的中性浅底、墨色文字、绿色主按钮与实时曲线配色；两杯对比做成可切换四项指标的轻量示意，明确标注“功能示意，非 App 截图”“示例数据”。
  3. 兼容设备表保留 8 行型号、能力与验证状态；手机端改为堆叠布局，避免长型号与表格横向溢出。
  4. 更新 `scripts/test_product_pages.py`，覆盖新版文案、边界、截图来源、下载入口、可访问性与部署范围检查。
- 推送与合并：本地预览 → 用户确认 → 普通推送工作分支 → `./scripts/create-pr` 创建 PR → `branch-policy`、`verify` 必需检查通过 → 合并 `develop`（用户确认后才执行）→ Product Pages 在 `develop` push 后自动部署。
- 验证证据：`python3 -m unittest discover -s scripts -p test_product_pages.py -v`；`python3 scripts/pr_verify_scope.py check-docs`；`git diff --check`；本地 HTTP 预览 + Tabbit 浏览器在 1280/768/390/360 宽度检查版式、灯箱、指标切换与横向溢出；桌面与手机截图人工核对。
- 协议与设备风险：不涉及协议编码、单位、队列、门禁、持久未确认记录或停止逻辑；不连接真实设备，不把网页示意当作真实萃取，页面示例数据全部显式标注。
- 发布：不发版；仅站点 PR 合并触发 Product Pages 自动部署。合并前必须经用户确认。
- 回退：通过站点 revert PR 恢复上一版页面并重新部署；不修改设备记录、不重写共享历史。
- 状态：用户已确认本地预览；剩余推送、`./scripts/create-pr`、必需 CI、合并 develop 与 Product Pages 部署核验。

具体规则以 [Git工作流](../git-workflow.md) 为准。

## 实际完成与验证

- 实现：重写 `site/index.html`（首屏 → 四个核心特色 → 补充功能 → 数据边界 → 界面展示 → 兼容设备 → 下载与开始 → 常见问题 → 支持与交流，保留来源说明、完整风险声明、纯静态灯箱）与 `site/styles.css`（App 设计 token：`#087A78` 主按钮、`#101820` 墨色文字、`#F7F9F8` 浅底；实时曲线沿用压力 `#176BD1`、温度 `#654ACC`、累计注水 `#C05A13`、流速/秤重 `#168A55`）。`site/product.js` 增加两杯对比的四指标切换（原生 button + aria-pressed），无网络请求、无假实时状态。
- 内容：两杯对比为桌面/手机两套静态 SVG“功能示意，非 App 截图 · 示例数据”，A 实线/B 虚线、每次一个指标，无第三杯、评分或最优参数；兼容表 8 行 + 三句脚注按交接稿原文；手机端表格改为堆叠三字段。
- 测试：重写 `scripts/test_product_pages.py` 为 19 项，保留 provenance/PNG 尺寸与 SHA、灯箱原图、资源无闲置、品牌图、二维码、Releases 精确入口与部署范围等检查，新增四特色、两侧边界、禁词、8 行兼容表、无网络 JS 与本地渐进增强检查。
- 验证：`python3 -m unittest discover -s scripts -p test_product_pages.py -v` → 19 项通过；`git diff --check` 通过。本地 `python3 -m http.server 8788 --directory site` 预览，Tabbit 实测 320/360/390/414/768/1024/1280/1440/1920 宽度 `scrollWidth == clientWidth`（无横向溢出）；1280 宽度下导航锚点落位于 sticky nav 之下（offset 126px）、四指标按钮 `aria-pressed` 切换与 Space 激活正常、灯箱 Esc 关闭且焦点返回、skip-link 与 `:focus-visible` 正常、外链均 `rel="noopener"`；禁用脚本（拦截 product.js）后首屏、8 行兼容表、压力示意曲线仍可读。关键文案对比度 ≥ 4.68:1；桌面与手机截图存于工作树外 `site-preview/`，未提交设备/本地文件。
- 复核：copy-auditor 独立上下文按交接稿 A–E 清单只读复核，A/B/E/F 硬项与 C 禁词检查通过；其提出的 M1（统一图片说明位于图后却写“以下”）与 S1–S6 建议均在提交前修复：说明移到图片区之前、og:image:alt 补“模拟数据”限定、FAQ 区分截图与功能示意、手机端兼容表单元格补 aria-label、手机 SVG 刻度/单位字号提高、无 JS 时指标按钮隐藏并加 noscript 说明、更新本规划状态。未使用 Astra 模型，独立复核限制如实说明；浏览器渲染与宽度结论以 Lead 的 Tabbit 实测为准。修复后 19 项测试复测通过。
- 待完成：用户本地预览确认、commit、`./scripts/create-pr`、CI、合并 develop 与 Product Pages 部署核验。
