# 推荐卡已有 fixture 的云端执行

- 需求与范围：补跑 #131 已存在的 280/368/728dp detached View 卡宽、完整文字与按钮高度断言，不重复扩展测试矩阵。
- 来源分支/基线提交：develop / 3a9bf91d3e531bd5e4bc2a9fad728c085f2f1103；工作分支 chore/ai-card-cloud-acceptance；PR 目标 develop。遵循 [Git 工作流](../git-workflow.md)。
- 改动文件与步骤：既有 Mock lifecycle workflow 的一个仅手动选择的入口和固定用途脚本；复用既有 Mock 包身份/签名校验及 BrewAudioInstrumentation 的 aiCardConstructionOnly 入口。只装全新一次性云模拟器，不使用本机 AVD/物理平板，不读取真实 AI 凭据或请求模型。
- 推送与合并：本地相关检查及 scripts/create-pr 完整门禁通过后创建草稿 PR；云执行验收与精确 head 必需 CI 通过再合入。
- 验证证据：记录源码 SHA、同次构建 APK SHA256/包名/版本/证书、instrumentation 原始输出和通过状态。尚未运行的检查不写为通过。
- 协议与设备风险：不改变应用、协议、数据或门禁，只运行固定合成 fixture。实际渲染的 detached View 测量不等于活动窗口截图/实机视觉验收。
- 发布与回退：不发版、不更改版本/签名、不向用户设备安装。可 revert 此测试入口；不影响应用数据。
