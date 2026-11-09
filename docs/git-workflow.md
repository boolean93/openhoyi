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
6. main/develop合并的PR必需检查只有branch-policy、verify两项，必须由精确头提交产生并通过，基线更新后需重新通过，讨论须解决。lifecycle、upgrade及visual AVD不再作为PR必需项：lifecycle/upgrade只在受保护分支push与手动入口运行，作为合并后及Develop自动预发布的前置证据；visual AVD改为手动。日常PR不运行任何AVD，见下节。单人仓库不要求无法自批的人工review计数，但协议改动必须在PR记录独立代码审查与设备证据。不能将模拟写入等同物理停水。
7. PR通过后合并；合并完成才清理工作分支。release/hotfix先完成双主干回合。删除远端分支通过GitHub审核清理，本地hook不开放删除推送。

## 发布规则

只有用户明确批准发版才开始发布操作。当前设备验收/正式签名缺口仍有效，建分支不取消这些阻塞。

- 候选标签 `vX.Y.Z-rc.N`（N从1开始）：目标提交必须属于对应origin/release/X.Y.Z。
- 正式标签 `vX.Y.Z`：目标提交必须属于origin/main；只有候选验收通过后才建正式标签。
- 发布前先冻结APK源码提交，执行现有release签名/版本预检和apk_distribution.py，对实际APK验签、包名、versionCode和哈希。版本必须递增，已安装证书必须匹配；不能用临时CI证书替代正式身份。
- 添加 `docs/releases/<tag>.json` 发布记录，字段：tag、sourceCommit、approved=true、versionCode、signerCertificateSha256、apkSha256、approvalReference、validationEvidence、upgradeEvidence、rollbackPlan。正式版另须hardwareAcceptanceEvidence。记录从标签提交的Git树读取，未提交文件不能充当证据。sourceCommit为实际APK源码提交；标签可指向随后添加记录的提交，但二者diff只允许该条发布记录，避免记录引用自身提交SHA的循环。
- 记录是人工审查输入。脚本检查字段/分支/源码差异，不证明填写的批准或机器效果真实；APK哈希、签名、实机及升级日志须在发布评审实际核对。现有分发工具的sourceCommitVerified=false也不能包装为二进制源码认证。
- 使用annotated tag并先执行 `python3 scripts/git_policy.py tag --name vX.Y.Z`（或启用hook后普通push）。已存在标签禁止移动、重写或删除；出错发布新版本。标签CI是推送后检查，不能撤回已经创建的错误标签，所以仍需本地预检和明确人工批准。
- 用户于 2026-11-06 批准独立的 Develop 自动测试预发布通道，范围和启用条件见下节；正式版/RC 仍逐次人工批准，不因本例外自动获批。创建普通版本标签本身不授权 GitHub Release、APK 分发、安装或硬件操作。
- 回退使用新的修复版本/明确revert PR；旧APK可能因versionCode或数据schema不能覆盖安装。不卸载清历史，不删除设备未确认记录，不通过降级掩盖协议问题。

## Develop 自动测试预发布（独立例外）

- 仅处理本仓库 `develop` 一阶合入历史中、通道建立后的固定源码 SHA。同 SHA 的 push 检查 `branch-policy`、`verify`、`lifecycle`、`upgrade` 必须最新 attempt 全部成功且实际 job 不得 skipped；不能拿 PR、fork、标签或手动 CI 的成功替代。CI 不等于实机验收。
- 经用户本次持续授权，配置完成并显式启用后，自动构建非 debuggable 的签名 Release APK，发布为 `dev-<versionCode>` 的 GitHub **prerelease**，不得设为 stable latest。此授权不包含稳定版、RC、安装、卸载、清数据或任何设备控制。
- 机器状态专用分支 `automation/apk-publishing` 只存版本保留/运行锁/发布哈希，自动化通过 API 以 CAS fast-forward 追加，不作为源码分支，不允许手工直推。所有分配（含失败、取消）永久占号。`dev-*` annotated tag 仅在实际 APK 核验后创建，创建后禁止更新/删除；手工 pre-push 仍拒绝 `dev-*`，不放宽原 `v*` 规则。
- Actions concurrency 只提供串行运行，不能当逐合入可靠队列；控制器按 develop 历史与持久账本补扫。失败不算已发布，四项 CI 未成功的源码不得发布。无新事件的失败由工作流手动重跑补偿；不接受任意源码输入。超过平台 matrix 上限时保留队列并停发，不能静默丢弃。
- 初始下限须覆盖已安装/已分发及人工候选保留版本；每次分配取下限、账本和所有已有主包分发清单的高水位再加一，公开前重读公开高水位。持续使用既有批准的签名；缺少密钥/指纹/版本基线/远端保护时停发，不生成替代密钥。源码声明保留 `sourceCommitVerified=false`，不宣称验证了安装、数据保留或硬件。
- **同包名跨通道的人工 RC/正式发版必须先暂停 Develop 入口，等待全部运行/待运行结束并确认持久锁已释放，再核对账本、所有分发/候选及安装版本。** 新人工候选版本必须高于全部已保留版本；把该人工保留版本纳入 `HOYI_DISTRIBUTION_VERSION_FLOOR` 后才可恢复 Develop。公开前再次排空/核对；被 Develop 超过的旧 RC 不得原样晋升为可覆盖升级的稳定版，须重新分配、构建和相称验收。这是人工发版窗口要求，不声称旧手工发布工具已自动取得共用锁；禁止绕过窗口并发发布。
- 附件必须齐备并回读验哈希，预存预期哈希后才能将 draft 一次性公开；不覆盖已公开附件。未知写入结果先核对服务端记录，已公开且哈希一致则只读跳过；不一致停止，失败号码/裸标签/草稿保留审计，新产物用新号。不得因 token 不足换源码、放宽保护或偷偷引入 PAT。
- 操作与恢复见 [Develop 发布配置指南](develop-apk.md)。只提交 workflow/保护 JSON 不代表已启用；必须核对远端实际保护和签名配置。暂停只阻止新规划，已进入执行的任务必须等待结束或取消并确认结束，不能把修改变量当瞬时撤销。

## PR 快速门禁与 AVD 分层

- 必需context固定为`branch-policy`、`verify`，不使用workflow级`paths`过滤，避免文档/站点PR永久pending。
- `verify`按PR完整`base...head`diff做严格allowlist分类：仅`docs/**/*.md`（`docs/releases/**`全部除外）、`README.md`、`AGENTS.md`，或仅`site/**`、`scripts/test_product_pages.py`时跑对应快速检查，不setup Android/Gradle；混合site+docs跑两者。`docs/`下脚本、JSON等非Markdown文件、未知路径、代码/CI改动、空diff、分类异常、base/head缺失或浅克隆一律回退完整非AVD验证，不得fail-open。路径按`git -z`返回的真实路径原样判断，不做去空白、反斜杠转斜杠等改写；非规范或含控制字符的路径保守回退完整验证。
- 完整`verify`保留JVM/unit/编译、localization资源、lint及release signing guard；受保护分支push始终完整执行，不能拿PR轻量成功替代发布证据。
- `lifecycle`、`upgrade`只保留`main`/`develop`/`release/**`/`hotfix/**`push与`workflow_dispatch`；合并后运行，不阻塞PR。其它visual workflow改为手动，不删除测试脚本、断言或证据上传。
- Develop自动测试APK仍要求同SHA、同仓库、develop一阶push的四项workflow（`branch-policy`、`verify`、`lifecycle`、`upgrade`）最新attempt成功且实际job不得skipped；PR/fork/标签/手动CI成功不能替代。详见[Develop自动测试APK](develop-apk.md)。

## 强制检查与边界

`python3 -m unittest discover -s scripts -p test_git_policy.py` 验证规则；`python3 scripts/git_policy.py branch`检查当前工作分支。GitHub rulesets阻止main/develop直接合入、强推和删除，并要求`branch-policy`、`verify`两项检查；版本标签禁止更新和删除。`lifecycle`、`upgrade`保留为受保护分支push的发布前置workflow，不列入PR必需context。管理员修改仓库规则、绕过本地hook或故意伪造证据不属于本脚本能保证的范围。治理修改也必须走chore PR，不得为了临时通过关闭保护。

GitHub 服务端规则的可审查配置保存在 [integration.json](../.github/rulesets/integration.json)、[releases.json](../.github/rulesets/releases.json)、[develop-apk-tags.json](../.github/rulesets/develop-apk-tags.json) 与 [develop-apk-ledger.json](../.github/rulesets/develop-apk-ledger.json)；配置更新须按仓库 API 返回核对，不把 JSON 文件存在当远端已启用。首次建立develop是迁移引导操作，在安装保护与hook之前仅推送既有c3710e3基线，此后不保留直推例外。

## 从本地创建 PR

提交本次改动，按现有流程推送工作分支，然后在仓库根目录运行：

```sh
./scripts/create-pr --title 'PR 标题' --body-file /tmp/openhoyi-pr.md
```

正文沿用 `.github/pull_request_template.md` 的四个小节。默认目标为 develop，其他目标用 `--base main` 明确指定；草稿加 `--draft`。单独运行检查用 `./scripts/create-pr --check-only`。

入口按目标分支的完整差异使用与 CI 相同的分类器和检查命令。文档和站点白名单走快速检查，其他改动跑完整 verify（包括 Gradle 单测、构建和 Android lint）。完整检查需要与 verify 工作流匹配的 JDK、Android SDK 及 ANDROID_HOME。失败时修复后重跑，成功后脚本把实际执行的检查写入 PR 正文。网页和直接调用 gh 可以绕开本地入口，合并仍须通过服务端必需检查。
