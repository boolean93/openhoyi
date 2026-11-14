# Gitee 同结构更新清单契约

- 来源 develop / 5fa7ec5f；分支 feature/gitee-index-contract，目标 develop，通过 scripts/create-pr 和必需检查。
- 用户最终要求：国内索引保留 GitHub schema1 字段结构，只把 downloadUrl 替换成同版本同文件名 Gitee APK；不新增公开字段。
- 仅固定可信 Gitee 索引响应走专用解析入口，所有原版本/签名/hash/size验证保留；国内 downloadUrl 必须精确已知仓库/tag/file。内部规范化为 GitHub fallback + Gitee首选列表；持久化缓存仍使用现有内部兼容schema以便进程恢复，不改变公开索引。
- 先离线验证坏链接拒绝、清单回退、缓存恢复、下载失败换源。保持生产镜像入口关闭；真实国内索引发布后，再核验匿名清单/APK与Android实际读取下载，记录证据再启用。
- 不操作咖啡机、不覆盖用户正式包、不发布APK/配置凭证，不清理用户数据。仅新增解析契约，原GitHub索引/旧用户兼容不受影响；代码revert可回退。

## 当前证据

- `:mobile:testDebugUnitTest --tests '*Release*Test'` 通过（1m07s）；严格Gitee URL、错版本/错仓库/HTTP/查询拒绝、Github源隔离、规范化缓存往返恢复与原下载重试回归通过。
- Astra 独立只读复核无阻断；生产镜像开关仍关闭，真实Gitee索引与Android联网验证未完成。
- 已准备显式手动云端Mock联网验收：固定匿名索引全部字段对齐（只允许downloadUrl变化）、Gitee真实APK下载验签/hash/manifest、模拟国内源失败后的GitHub同版真下载；仅CI emulator，不安装下载包/不操作BLE。测试APK编译通过，真实发布前不触发。
- 真实dev123上传证据补充：Gitee同仓中间跳转保留文件名，foruda最终basename变为全小写。Android仅在固定foruda最终数字路径允许原名或Locale.ROOT全小写，新增错误大小写/中间跳转/跨版本拒绝测试；Release*回归通过，Astra复审无阻断。
