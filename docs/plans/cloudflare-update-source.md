# Cloudflare 优先检查与下载

- 来源 `origin/develop`，短分支 `feature/cloudflare-update-source`，PR 目标 `develop`，通过 `scripts/create-pr` 创建并等待保护检查合入；不发人工版本。
- 用户指定已上线固定代理索引 `https://openhoyi.ios.dog/boolean93.github.io/openhoyi/updates/dev.json`；APK 使用同域固定仓库/tag/文件路径。只改 App 路由，不部署、更改 DNS 或代理服务。
- 索引先代理再 GitHub Pages。旧索引对照 GitHub；格式/签名/网络失败回退，单源与总请求有时限。下载由验证后的 GitHub 身份在本地构造代理链接，先代理后同版本 GitHub，忽略旧缓存 Gitee 镜像，不混合文件。
- 保留文件大小、SHA256、签名、包名和版本验证；失败文件清理、取消不重试、任意 URL/代理重定向拒绝。GitHub 原有安全 CDN 跳转不变。
- 验证：相称 JVM 网络伪连接用例及完整本地门禁；云端 disposable Mock 实际 Android 连接、索引一致性、完整 APK 验签和注入代理失败后 GitHub 回退。用户平板不覆盖、不清数据、不操作咖啡机。
- 不改协议和设备状态，不引入凭据，不改变签名、包名和版本分配。客户端真实网络验收不能代表大陆全网长期可用。
- 回退：独立 revert PR 恢复 GitHub-only 路由，不回滚版本、不删用户缓存或记录。
