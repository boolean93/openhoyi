# Pages 直接下载 Android 测试版

- 范围：现有 GitHub Pages 下载区，读取同源 `updates/dev.json` 中的已核验 Develop 附件和 Release URL；信息不可用时保留原有 Release 列表。主页、下载说明及 Android App 其它功能不重新设计。
- 来源：`develop`（`c0ba9e57ff6b465a7ee231be2cae14eba7c4ac8f`）。本地独立 `fix/pages-apk-download`；先通过正常入口创建指向 develop 的草稿 PR。最终合入时由既有 Product Pages 工作流部署；现有 Develop 测试包自动流程是独立的，本变更不手工发包，也不修改其配置。
- 验证：现有静态页面测试；桌面/小屏真实浏览器核对有效索引直链、失败或禁用脚本时的 Release 回退，以及公开 APK 响应。只修改 Pages，不修改协议、App、构建/签名、发布、安装、升级及数据。
- 回退：回退本站修改即可恢复只跳 GitHub Releases；不触及 APK 和更新索引。
