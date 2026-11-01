# CI第一阶段触发精简

用户授权按改动风险选择代表性真实场景与必要回归，避免重复矩阵。继续现有 `fix/ci-page-service`（develop基线）→PR7→develop，遵守[Git工作流](../git-workflow.md)，普通追加提交，由root统一push；不改变分支保护、不自行合并。

| Workflow | 自动运行 | 手动与原验证内容 |
|---|---|---|
| verify | PR原范围不变；push仅main/develop/release/**/hotfix/**，保全部原tag | 保留 |
| mock-lifecycle / mock-upgrade | 保PR main/develop及上述四类push；原来不触发tag，仍不新增 | 保留 |
| font-scale-snapshot-probe / mock-ui-screenshots | 取消自动触发 | 原完整probe/capture脚本保留 |
| advanced-mock-pages | PR保business | 手动仍pages+business；suite名称、失败与artifact语义不变 |
| branch-policy | 完全不改 | 四required保护及必需检查名称不变，PR无新增path过滤 |

证据：本地保存的 `settings-evidence/ci-duration-audit/jobs.json` 中，普通分支push的verify/lifecycle/upgrade重复占用样本为26m26s（runs 41271354129/41271354119/41271354078）和35m36s（41270087703/41270087718/41270087693），约26–35 runner分钟/轮；含历史失败耗时，仅估计取消重复工作量，不承诺PR排队或墙钟等待同幅缩短。其余可选任务收益不作数值承诺，后续按实际新run确认。

验证仅做六份YAML解析、事件/branch/tag/manual正反例及advanced事件矩阵检查，并与提交前jobs比较；脚本、job名、超时、上传、权限不变，branch-policy字节一致。离线检查通过，原始小证据在workspace `settings-evidence/ci-diagnosis/lean-trigger-policy/validation.json`，不启动模拟器或新增测试框架；新CI实际调度尚待统一push观察。Astra独审通过，无阻断项。未开PR的普通开发分支push将不再运行这三项，这是授权去重的预期；其它复杂visual矩阵仍保留。

范围仅触发去重；不改产品、协议、数据、AVD缓存、复杂visual业务或AGENTS。不发版、不改签名/versionCode、不安装/操作设备。回退使用revert本提交恢复原触发，不改写共享历史。
