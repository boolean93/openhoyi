# 同步隔离 Mock 截图的 runner 精确契约

- 需求与范围：当前测试 Manifest 已声明五个 runner，截图生产器仍只接受旧两项而拒绝产物。用单一显式 allowlist 同步五项，保留精确目标包、未知/缺失/重复项拒绝。
- 来源分支/基线：develop，7a22e6cbee30322c066ff76a19cde08e7e96cbf4。
- 工作分支：fix/mock-runner-contract；PR 目标 develop。
- 改动：scripts/mock_runner_contract.py、两个消费者及对应回归测试。不修改 App、Manifest、workflow、字号断言或状态隔离门禁。
- 推送与合并：本地相关测试与 scripts/create-pr 完整检查，普通推送，草稿 PR；独立复核及精确头提交 branch-policy/verify 通过后合入。
- 验证：source Manifest 与显式五项一致；两个截图读取器和 APK 检查器拒绝未知、重复、缺失、错误 target。既有签名、源码、同 run 产物与隔离检查保持不变。
- 协议与设备风险：不影响协议、队列、门禁、记录或机器控制。不操作平板，不启动本地模拟器；合入后仅授权云端 Mock 截图验证。
- 发布：不手工发 APK、不改签名版本；既有 Develop 自动流程按原授权运行。
- 回退：revert 本 PR；不卸载或清除任何设备数据。

规则见 [Git工作流](../git-workflow.md)。
