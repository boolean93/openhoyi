# 将创建 PR 的本地检查规则写入仓库入口

从 develop 的 0e52c16d 建 docs/pr-check-entry，目标为 develop。将项目入口已确认的规则同步到仓库根 AGENTS.md。
核对脚本及工作流文档，运行差异检查，通过脚本创建 PR，等 branch-policy 和 verify 通过后合入。
仅改文档，无协议、设备、签名或升级影响，不发版。可回退本次文档提交。
