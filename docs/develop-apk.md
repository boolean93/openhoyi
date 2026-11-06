# Develop 自动测试 APK

PR 合入 develop 后，同一源码提交的四项必需 CI 全部成功，才生成 **Release 构建的测试包**，上传到 GitHub Releases 的独立预发布通道。它不是稳定版，不会自动安装，也不代表设备协议已实机验收。

## 一次性配置

先合入本功能的 chore PR。负责人确认签名/版本连续性后，按下表安全配置仓库 Actions Secrets/Variables；**不要在聊天、命令参数、日志或仓库粘贴密钥和密码**。不要在 CI 临时生成 keystore。

| 类型 | 名称 | 要求 |
|---|---|---|
| Secret | `HOYI_RELEASE_KEYSTORE_BASE64` | 外部持久 keystore 的单行 Base64，不是临时测试 fixture |
| Secret | `HOYI_RELEASE_STORE_PASSWORD` | keystore 密码 |
| Secret | `HOYI_RELEASE_KEY_ALIAS` | 既有私钥 alias |
| Secret | `HOYI_RELEASE_KEY_PASSWORD` | 私钥密码 |
| Variable | `HOYI_RELEASE_CERT_SHA256` | 既有主包签名证书 SHA256，64 位十六进制 |
| Variable | `HOYI_DISTRIBUTION_VERSION_FLOOR` | 负责人确认的所有已安装、已分发、人工候选保留版本的最高 versionCode，十进制整数，不是“从 0 开始” |
| Variable | `DEVELOP_APK_ENABLED` | 最后才设为 `true`；其他值均暂停 |

截至本次只读调查，仓库级 Secrets/Variables 均为空，environment 仅 github-pages。尚未核验组织级继承配置；不得因此假定它一定不存在或已经可用。本功能未上传密钥或启用真实分发。

现有公开 `v0.2.0-rc.2` 主包 versionCode **52**，签名指纹为：

```text
c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54
```

这只是已公开包的只读基线，不是“实际安装最高版本也是 52”的证明，也不授权选择本地 Debug keystore 作为后续发行密钥。配置的持久私钥必须经负责人确认，并与此前主包身份相同；脚本还会下载前一公开 APK 实际验签。若现有发行身份不能安全延续，先制定签名迁移方案，不能换临时 key、卸载清数据或换包名掩盖问题。

### 服务端保护

现有 `v*` 保护保持不变。另配置并以 GitHub API 核验下面两条 active、无 bypass/exclude 的 ruleset：

- [Develop 标签规则](../.github/rulesets/develop-apk-tags.json)：`refs/tags/dev-*` 不允许更新/删除。
- [分配账本规则](../.github/rulesets/develop-apk-ledger.json)：`refs/heads/automation/apk-publishing` 不允许删除或非 fast-forward；允许自动化正常追加。

JSON 只是待部署配置。控制器每次规划/公开都检查**远端实际配置**，没有保护就拒绝。不要更改现有四项必需检查、免除分支保护或为了上传增加 PAT。原本的版本标签 gate 不接受手工 `dev-*`，由本通道专用校验创建。

本实现使用 Actions `GITHUB_TOKEN`：控制器/发布器只有 `contents:write`、`actions:read`，签名构建只有 `contents:read`。签名 Secrets 只提供给 build 的签名 step；publisher 无 key。workflow 不监听 PR，不下载 PR 工件，不使用共享 Gradle cache。当前使用仓库级 Secret，若改用 environment，须另核实其 develop 分支/工作流限制和权限，不要以隐式新建 environment 当保护已配置。

## 合入后的流程

1. develop push 和四个 CI 完成事件只负责唤醒，不直接信任事件的成功。控制器用 [通道配置](../.github/develop-apk.json) 的启用基线扫描 develop 一阶历史，排除功能分支中间提交及通道建立前提交。
2. 对每个未公开源码 SHA，检查本仓库四个固定 workflow 的最新 push/develop run 与 attempt，要求实际必需 job success；API 完整分页，缺失/失败/进行中/歧义均不能发布。
3. 在受保护的专用状态分支取得持久运行锁，以单父提交和 `force=false` CAS 分配版本。版本高于初始下限、账本所有保留号和现有主包分发清单（含 RC/正式版/草稿）。取消、失败、未创建 tag 的号码也不回收。`HOYI_DISTRIBUTION_VERSION_FLOOR` 是初始/人工窗口下限，不需要随每次自动发布手工追涨。
4. 每个构建 checkout **分配记录中的确切 SHA**；另从当前工作流的不可变 `github.workflow_sha` 取得控制器。真实 keystore 仅存在 runner 临时目录，位于两个源码仓库与上传目录之外，权限 0600，签名 step 退出时清理，runner 销毁时不保留。没有 key 时明确失败，无备用证书。
5. 在实际 APK 上执行现有 Gradle 预检和 APK 工具，验证主包 `io.openhoyi.mobile`、单一既有签名、`debuggable=false`、精确分配版本/名称及 SHA256；用实际前一公开 APK 比较签名/版本。`0.2.0-dev.<code>` 是测试版名，包名不变。
6. 发布 job 只下载**自身当前 workflow run 和 attempt**的构建工件，工件名带 attempt，防止整条重跑时旧 plan 冲突或混入旧产物；计划也必须绑定当前 run/attempt。重新实际验 APK、CI、版本，按版本顺序创建 annotated `dev-<code>`，全部附件传到 draft 并下载回读验哈希。持久记录准备好的附件哈希后，一次性将 draft 转为 prerelease，`make_latest=false`。
7. 用户在 Releases 获取 APK、[分发清单说明](plans/apk-distribution.md)及校验和。清单保留 `sourceCommitVerified=false`、真实源码声明、CI URL/run/attempt，以及安装/数据/硬件未验证状态；不构造伪源码认证或假硬件证据。

Actions 全局 concurrency 仅序列化整条分发运行，默认 pending 可能被后来的事件替换。可靠待处理清单来自**合入历史 + 不可变版本分配 + 真实发布结果**，不是 pending 队列。最新唤醒会补扫遗漏的中间合入。无新事件时，失败恢复用 Actions 新建手动运行（develop，无源码参数），或 **Re-run all jobs** 重新规划并分配更高号；不要只重跑失败签名 job，旧 attempt 的计划会被拒绝，不复用失败号。不把失败算完成。一次超过 Actions 的 256 项 matrix 上限时会明确停下、保留全部记录，需人工分批恢复，不静默只发最后一项。

## 失败、重跑与暂停

- CI 未通过：不访问签名 key、不建发布 tag、不公开；后续事件再检查，不借旧成功或同名 PR job 通融。
- 缺 key、签名/版本不匹配、构建失败：不公开。已分配版本留在账本；修复配置后新分配更高号。不会重用旧号或覆盖旧 tag/附件。
- 上传失败：保留裸 tag/草稿供调查，不自动删除。后续新构建用新号；不能把不同字节覆盖到已存在同名附件。
- “公开成功但响应丢失”：下一次先比较服务端公开状态与**公开前持久保存的精确哈希**，一致则补记已完成，不重建/重发；缺失或不一致则停下调查。状态仅 reserved、没有 prepared 哈希的陌生公开版本不擅自认领。
- 账本锁属于另一仍在运行的 workflow：不抢占。只有 GitHub 确认前一个 run 已 completed（或同 run 的后续 attempt）才能回收取消/崩溃留下的锁，不按短 TTL 强抢。
- 设置 `DEVELOP_APK_ENABLED=false` 暂停新规划。**在途任务读取的是启动时变量快照**，暂停不瞬间终止；须等待/取消并确认全部 running/pending run 结束及账本 lease 释放。不要直接删除状态分支、重置高水位或改标签。
- API 错误/权限不足/超时一律停发，未知写入不盲重试。当前 GitHub 对相对默认分支增改 workflow 的目标提交可能要求 `Workflows:write`，`GITHUB_TOKEN` 不能额外获得此权限；历史补扫可能因此被拒绝。遇到此情况须实际核查最窄 GitHub App 权限并另行授权，不能回退构建最新 develop 或默默塞入 PAT。见 [官方 release API](https://docs.github.com/en/rest/releases/releases#create-a-release)。此权限路径未用真实云端发布验证。

## 人工 RC / 稳定版窗口

原有 [发布规则](git-workflow.md#发布规则) 的批准、分支、记录、签名、升级、硬件证据不变。自动测试版与 RC/正式版是同包名、同签名，版本空间必须共用。

现有人工发布工具**没有自动取得本通道持久锁**，因此人工发布负责人必须：

1. 暂停 Develop，并排空所有在途/待运行任务、确认 lease 空闲；在整个分配/公开窗口保持暂停，不能只改变量后立刻发。
2. 核对状态分支所有 allocation（含失败）、全部分发清单/人工候选/已安装主包与配置下限；新候选 versionCode 大于全部保留高水位。人工候选一经分配也永久占号，将其写入配置下限，避免随后被自动通道回收。
3. 按原规则验收与发布。长时间验收可在记录人工保留号并更新下限后恢复自动通道；公开前再暂停/排空/核验。若期间 Develop 已超过旧候选 code，必须重建更高版本并做相称验收，不能将旧 RC 原样晋升后宣称能覆盖 Develop。
4. 完成后将下限更新到所有已分配版本最高值，再恢复自动通道。后续正式自动化必须使用同 concurrency group 和持久账本/锁，不能另造独立 counter。

本 PR 不声称已改造旧人工工具为自动跨通道锁，也不声称线下绕过窗口的并发发布可被此脚本全面阻止。管理员蓄意改规则/伪造账本同样超出保证范围。出错通过更高版本或明确 revert PR 修复；不卸载、清数据或自动操作设备。
