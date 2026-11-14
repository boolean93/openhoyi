# 1.1.3 完整流量曲线兼容补齐

- 需求：补齐用户要求的 1.1.3 官方模式对等支持，不将 #94 的窄范围误当全部完成。
- 基线：develop / 85c6c79b；分支 fix/firmware113-flow-compatibility；PR 目标 develop。
- 范围：临时 0x02 的 Ls、首段流量组合增加精确 1.1.3；依用户最新要求，持久 0x08 也按官方 copy2Hoyi 路径对齐全部既有有效模式，独立策略核验精确支持版本及跟随参数范围。同步中文英文执行/持久提示。
- 证据：沿用 docs/evidence/2026-11-14-firmware113-official-mode-compatibility.md 原官方 1.2.6 UI→commitCurveFieldsToHoyi→startTempChart 的完整字段路径，无最低固件版本判断。首段 bit7、chart/press bit6/7 都完整传递。客户端兼容证据，不是1.1.3固件探针或实机停水证明。
- 验证：复用完整流量组合字节、精确版本、113版本切换出队撤销、报警/过期/停止测试；持久模式先于legacy及宽泛host许可验证版本和跟随参数；Mobile文档序列化/能力测试。完整scripts/create-pr和必需CI。
- 协议风险：不改命令、参数范围、未知结果、重发、设备身份、报警或停止；113/114/115持久跟随、Ls、首段流量对等，未知版本及非法参数仍拒绝；不自动重发、不将传输成功当保存确认。
- 发布回退：不安装、不操作真实机器、不手动发版，不改签名或版本。必要时revert PR，保留用户数据。
- 推送合并：遵循docs/git-workflow.md及用户长期授权，通过scripts/create-pr创建PR，检查通过后合入。
