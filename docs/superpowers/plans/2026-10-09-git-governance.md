# Git 分支与发布治理实施计划

Goal: 所有后续规划、推送、合并和发版遵循 docs/git-workflow.md。
Architecture: AGENTS.md 约束开发行为，本地 pre-push 提前拒绝误操作，GitHub rulesets 与无路径过滤的 PR 检查保护 main/develop。已有 feature/native-ble 历史保留，以 c3710e3 创建 develop；不把未实机验收的代码发布为稳定版。
Tech Stack: Git、Python 标准库、GitHub Actions、GitHub rulesets。

- [x] 检查现有分支、工作树、CI、签名与发布边界；保留他人的未跟踪资料。
- [x] 编写拒绝错误分支、错误合并路径、缺规划/验证/风险/回退说明及非法标签的测试，确认未实现时失败。
- [x] 实现统一检查脚本、PR模板、仓库AGENTS和本地hook。
- [x] CI支持所有规范分支，main/develop PR无路径过滤地执行基础、生命周期、升级检查。
- [x] 本地8测试及独立审查通过，develop基线已推送。
- [ ] 治理分支推送并创建develop PR，通过四项CI合并。
- [x] 启用 GitHub 分支/标签保护并核对实际API返回；默认分支develop，规则无bypass。main不变，不发版。

本计划采用当前同一会话直接实施；已有设备验收阻塞不阻止本次仓库治理。验收需区分本地hook、服务端保护和CI状态，不能仅凭文档称已强制执行。

执行分支：chore/git-governance，来源c3710e3，PR目标develop，不发版。验证：8项Python测试、5份workflow YAML解析、独立审查；协议/设备报文无变化。回退采用chore PR还原配置并核对服务端rulesets，不清设备记录。
