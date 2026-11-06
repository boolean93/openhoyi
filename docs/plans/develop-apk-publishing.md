# Develop 测试 APK 自动发布

- 需求与范围：用户于 2026-11-06 明确批准：PR 合入 develop 后自动提供签名 Release APK 的 GitHub 预发布测试包；不是正式稳定版，不自动安装或操作设备。Sol 实现/测试，实际 Astra 独立方案及实现复核。
- 来源分支/基线：`origin/develop` / `67d8adef4fca919ec0e451ec17c26223d271152d`。
- 工作分支：`chore/develop-apk-publishing`，独立工作树，不干扰已有任务。
- PR 目标：`develop`；普通推送并走 PR，不自行合并或绕过必需 CI。
- 改动文件与步骤：新增独立 Develop 发布 workflow、发布脚本/纯校验与最小拒绝测试；复用现有 Gradle 外部签名预检和 APK 分发工具；更新规则源、测试标签保护配置和配置指南。不改 APP/BLE/数据库。

## 验收标准

1. 固定合入源码 SHA；只处理本仓库 develop 一阶历史中的提交，并要求同 SHA 的 push 事件 branch-policy、verify、lifecycle、upgrade 全部成功；PR/fork/标签/手动测试成功不冒充。PR 日常门禁只需 branch-policy/verify，lifecycle/upgrade 合入后运行，不改变本通道的四项同 SHA push 要求。
2. 连续合入不因 Actions pending 覆盖而遗漏：以 develop 一阶历史、受保护 CAS 分配账本、不可变 dev 标签及实际公开发布记录补扫待发布提交；工作流事件只负责唤醒。失败或未完成 CI 的提交不发布，后续成功提交可继续。运行与工件还绑定 run/attempt，整条重跑重新分配失败号，不混用旧产物。
3. 持久签名来自 Secrets，不生成临时证书；初始版本下限与既有公开 APK/已分发版本核对。跨通道共享版本高水位，保留失败分配，不复用已分配版本；公开前再次检查。正式发版窗口需暂停并排空 Develop 发布，正式版/RC 也必须高于此前已分发版本，不能原样晋升较低版本 RC。
4. 构建实际 release 变体并核对包名、签名、分配的版本、非 debuggable、哈希及原始源码声明；公开清单保留 `sourceCommitVerified=false`，安装/数据/硬件未验证，不虚构证明。
5. 只有附件齐备并回读校验后才将 draft 公开为 prerelease，`make_latest=false`；正式 `v*` 规则不放松，新增独立 `dev-*` 通道。现存标签、已公开附件不重写。未知发布结果先核对服务端状态，不盲重发。
6. 最小 token 权限；不从 fork/PR 工件执行提权代码，不自动引入 PAT。GitHub API 权限不足时停发，不回退到其他提交。密钥、密码不进库或日志。

## 配置与启用边界

已只读确认：仓库级 Secrets/Variables 均未配置；现有 environment 仅 github-pages。存在既有公开 RC 分发，须纳入签名与版本连续性，不假设新的云端 debug key 可覆盖它。末次资料核对补充：既有本机双包安装回执证明主包曾安装 53，与公开 52 的签名一致；最低已知版本改为 53，仍不代表当前全局最高安装/保留号已确认。本轮仅读旧回执，未操作设备。先实现/测试并提交 PR；没有确认并安全配置持久密钥前不得真正发包，不能将“流水线代码已合入”等同“自动分发已启用”。

## 验证证据

- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_develop_release*.py'`：16 项通过。涵盖四 CI 最新 attempt/实际 job、跨通道高水位、实际元数据拒绝、分页错误、CAS 并发、锁回收、漏事件补扫、失败附件不公开/不覆盖、公开响应丢失恢复、缺 key 不执行 Gradle、过期 attempt 不执行下载/签名、workflow 权限/工件边界。
- 既有 [Git 规则测试](../../scripts/test_git_policy.py) 8 项、[APK 分发测试](../../scripts/test_apk_distribution.py) 14 项通过；未放松旧手工版本 gate。
- `go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.7 -shellcheck= -pyflakes= .github/workflows/develop-apk.yml .github/workflows/verify.yml`：通过。首次发现 job-level env 不支持 runner context，已将 Gradle 临时目录配置移入签名 step 后复验；此校验不包含 shellcheck/pyflakes。
- 两份 workflow Ruby YAML 语法解析、三份 JSON 解析、当前分支 gate 与 `git diff --check` 通过；action SHA 通过只读 GitHub API 解析核对。
- 本地测试仅 mock/临时文件，不使用真实 key，不执行 Android 打包、上传、安装或设备操作，不重跑无关 Android/视觉矩阵。真实签名、云端 jobs/artifacts/CAS、GitHub token 对历史 workflow 修改源码的权限、最高已安装版本仍未验证；仓库配置与远端保护尚未部署，通道保持未启用。人工 RC/稳定版仍按暂停/排空窗口共用版本空间，不声称旧工具自动取得锁。

## 协议与设备风险

不改 BLE、控制门禁、未知记录或重试行为。预发布说明必须明确测试版及未实机验收；不以 CI 成功授予新增秤控制权限。

## 发布与回退

本 PR 只交付自动测试通道与配置说明，不发正式版、不安装、不改包名/schema/签名身份。启用后仅在用户批准的测试通道自动分发。可暂停通道并通过修复 PR 恢复；出错版本不移动标签或覆盖 APK，使用更高版本修复。保留历史和未知记录，不卸载清数据。

## 独立审查

实际 `openai/gpt-6-astra` 已完成方案与实现独立只读审查。方案指出默认 Actions concurrency 不是可靠逐次队列、跨通道版本高水位、dev 标签保护、release API 对 workflow 变更源码的权限及实际 tag 解引用，均纳入实现/明确限制。实现审查唯一 P1 为旧 SHA 的 skipped/missing 必需 job 毒化后续补扫；已仅将该 job 语义失败转为 not-ready，API/格式错误仍拒绝，并增加旧失败→后续健康源码继续的回归，Astra 局部复核通过（15 项离线测试）。随后 Sol 自查补充 attempt 隔离和 actionlint 修正，已增加第 16 项回归；实际 Astra 再次独立增量复核通过，并在清空环境的离线运行中确认 16 项全过。Astra 明确接受人工正式/RC 暂停排空窗口作为本次 scope，不把离线通过当启用/发版许可。
