# Git 分支、推送与发布规则

本文件约束项目全部后续规划、手工操作和智能体任务。AGENTS.md、规划模板、PR模板、本地hook、CI和GitHub保护共同执行；旧计划/历史HANDOFF中的命令不构成例外。

## 分支模型

| 分支 | 来源 | PR目标 | 用途 |
|---|---|---|---|
| main | release/hotfix PR | — | 稳定发布历史；不直接开发 |
| develop | 功能PR与release/hotfix回合 | — | 集成基线，默认分支；不等于已实机验收 |
| feature/slug | develop | develop | 功能 |
| fix/slug | develop | develop | 非线上缺陷 |
| chore/slug、docs/slug | develop | develop | 工具、治理、文档 |
| release/X.Y.Z | develop | main；修正再回合develop | 冻结候选版，只改版本、验证与发布修复 |
| hotfix/X.Y.Z | main | main；再回合develop | 已发布版本的紧急修复 |

slug仅小写字母、数字与单横杠，不嵌套；版本为无前导零的三段整数。release/hotfix不能混入下一版本功能。普通功能从develop新建，不继续把feature/native-ble当长期主干。功能PR可squash；release/hotfix使用merge commit，合入两条主干后再删除源分支。禁止强推和改写共享历史；需要调整已推送内容时追加提交或revert PR。

## 当前迁移

原main为初始提交d0e88b1，当前原生历史在feature/native-ble。以c3710e3建立develop，保留原main和旧分支，不rebase、不删历史、不冒称稳定发版。治理配置通过chore/git-governance的PR进入develop；main需以后完成发布验收后由release PR更新。旧功能分支只作为历史参考，不再新增开发。

## 规划、推送与合并

1. `git status --short`、检查当前分支，保留其他人的文件；`git fetch origin`。
2. 从来源分支建短分支，例如 `git switch -c feature/scale-reconnect origin/develop`。写任务规划，引用本规则及 docs/plans/task-template.md。
3. 新克隆/新工作树启用本地提前检查：`git config --local core.hooksPath .githooks`。先检查现有hook设置；已有用户hook不得默默替换，改由组合调用本hook。本地hook可以被绕过，不能代替服务端保护。
4. 执行与改动相关的验证，只提交本任务文件。`git push -u origin feature/scale-reconnect`，不使用force或no-verify。
5. PR目标默认develop，正文必须有非空的规划与范围、验证证据、协议与设备风险、发布与回退，并链接仓库内实际存在的规划。
6. main/develop合并要求branch-policy、verify、lifecycle、upgrade四项检查，基线更新后需重新通过，讨论须解决。CI不按路径跳过必需检查，避免文档PR永久等待。单人仓库不要求无法自批的人工review计数，但协议改动必须在PR记录独立代码审查与设备证据。不能将模拟写入等同物理停水。
7. PR通过后合并；合并完成才清理工作分支。release/hotfix先完成双主干回合。删除远端分支通过GitHub审核清理，本地hook不开放删除推送。

## 发布规则

只有用户明确批准发版才开始发布操作。当前设备验收/正式签名缺口仍有效，建分支不取消这些阻塞。

- 候选标签 `vX.Y.Z-rc.N`（N从1开始）：目标提交必须属于对应origin/release/X.Y.Z。
- 正式标签 `vX.Y.Z`：目标提交必须属于origin/main；只有候选验收通过后才建正式标签。
- 发布前先冻结APK源码提交，执行现有release签名/版本预检和apk_distribution.py，对实际APK验签、包名、versionCode和哈希。版本必须递增，已安装证书必须匹配；不能用临时CI证书替代正式身份。
- 添加 `docs/releases/<tag>.json` 发布记录，字段：tag、sourceCommit、approved=true、versionCode、signerCertificateSha256、apkSha256、approvalReference、validationEvidence、upgradeEvidence、rollbackPlan。正式版另须hardwareAcceptanceEvidence。记录从标签提交的Git树读取，未提交文件不能充当证据。sourceCommit为实际APK源码提交；标签可指向随后添加记录的提交，但二者diff只允许该条发布记录，避免记录引用自身提交SHA的循环。
- 记录是人工审查输入。脚本检查字段/分支/源码差异，不证明填写的批准或机器效果真实；APK哈希、签名、实机及升级日志须在发布评审实际核对。现有分发工具的sourceCommitVerified=false也不能包装为二进制源码认证。
- 使用annotated tag并先执行 `python3 scripts/git_policy.py tag --name vX.Y.Z`（或启用hook后普通push）。已存在标签禁止移动、重写或删除；出错发布新版本。标签CI是推送后检查，不能撤回已经创建的错误标签，所以仍需本地预检和明确人工批准。
- 目前不提供自动上传/发布APK工作流。创建标签本身不授权GitHub Release、APK分发、安装或硬件操作。
- 回退使用新的修复版本/明确revert PR；旧APK可能因versionCode或数据schema不能覆盖安装。不卸载清历史，不删除设备未确认记录，不通过降级掩盖协议问题。

## 强制检查与边界

`python3 -m unittest discover -s scripts -p test_git_policy.py` 验证规则；`python3 scripts/git_policy.py branch`检查当前工作分支。GitHub rulesets阻止main/develop直接合入、强推和删除，并要求四项检查；版本标签禁止更新和删除。管理员修改仓库规则、绕过本地hook或故意伪造证据不属于本脚本能保证的范围。治理修改也必须走chore PR，不得为了临时通过关闭保护。

GitHub服务端规则的可审查配置保存在 `.github/rulesets/integration.json` 与 `releases.json`；配置更新须按仓库API返回核对，不把JSON文件存在当远端已启用。首次建立develop是迁移引导操作，在安装保护与hook之前仅推送既有c3710e3基线，此后不保留直推例外。
