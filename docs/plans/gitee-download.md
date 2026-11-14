# 国内镜像下载与有界整包重试

- 需求与范围：优先读取固定 Gitee 国内更新清单，失败回退既有 GitHub Pages；国内清单声明的 Gitee APK 优先，失败回退 GitHub；有限整包重试、脱敏诊断。无需断点续传，不加备案/付费服务，不配置凭证或真实上传。
- 来源分支/基线：develop / e86c4827；工作分支 feature/gitee-download；PR 目标 develop。
- 清单兼容：GitHub Pages 的 schema1 不变，避免旧版严格解析器拒绝更新。仅国内清单可选 mirrorDownloadUrls，最多一个固定项目/版本/文件名的 Gitee 地址。新 App 同时兼容旧清单。
- URL 边界：Gitee releases/download → 同仓库 attach_files → foruda.gitee.com/attach_file/数字/同文件名；GitHub 原白名单不变。HTTPS、完整包大小/SHA256/签名/包名/版本校验不放宽。签名查询、完整URL、响应正文不进入日志。
- 步骤：清单解析与失败回退；下载源策略及有界重试；接入现有 owner/日志；假连接与离线验证；独立复核；scripts/create-pr 完整门禁；普通推送与 PR 必需检查通过后合入。
- 验证：模拟 DNS/超时/HTTP/重定向/部分下载/损坏字节/取消/限流/跨源恢复，保留现有校验测试。真实 Gitee 发布与运行可用性由发布端独立验证，不能拿假服务当上线证据。
- 风险：只影响更新下载，不改蓝牙/机器/配方。保留下载 controller 单任务、取消generation和安装租约，不后台弹安装。后台持久化与系统级下载不在本次范围。
- 发布/回退：不手工发版或安装、不触碰真实凭证；保持现有自动 develop 渠道。代码 revert 可回退，既有缓存/用户数据保留。

## 验证与启用状态

- 定向离线测试通过；独立 Astra 复审 Release* 12 suites / 57 tests 全通过，无阻断。覆盖清单与下载回退、损坏字节、严格跳转、取消和服务端限流。
- 镜像入口通过 `ReleaseUpdateHttpCall.mirrorEnabled` 源码参数默认关闭，生产 owner 不传入启用值。未有真实清单与 APK 端到端证据前，只访问既有 GitHub 清单，不增加国内源等待；整包重试和脱敏诊断独立生效。没有新增用户开关。
- 启用前置：发布端默认关闭的 workflow 完成真实权限/API 与匿名附件验证，公开国内清单及 APK 保持 hash/签名/包名/版本一致，并完成 Android 实际下载验证；之后单独 PR 在 owner 启用镜像并记录证据。不能以本次离线测试声称镜像已上线。
