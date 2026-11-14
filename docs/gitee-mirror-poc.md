# Gitee 国内镜像：离线 PoC 与上线门槛

## 当前边界

已有离线规划器、可注入后端的发布事务引擎和固定仓库 HTTP 适配器。仅做离线假服务测试，未实例化真实带凭据后端、未上传或更新远端清单。不是自动发布功能已完成。Gitee 公开专用仓库已由用户授权创建：<https://gitee.com/boolean93/openhoyi>。现有 GitHub APK 分发、签名和 Android 更新逻辑不改。

```sh
python3 -m unittest discover -s scripts -p 'test_gitee_mirror_plan.py'
python3 scripts/gitee_mirror_plan.py --index /path/to/verified-dev.json --assets /path/to/release-assets --previous-index /path/to/previous-published-index.json
```

输入目录包含原样 APK、distribution.json、SHA256SUMS.txt。index 应来自现有 `release_update_index.py` 验证流程，包含实际 APK 检查，不可仅凭自写 JSON 的哈希宣称通过签名校验。规划器再次验证来源、大小、哈希、元数据、附件清单、版本单调及签名身份，打印候选计划；不写生产 dev.json。首次镜像可省略 previous-index；后续真实执行器必须读取已公开状态，不能将读取失败当成首次发布。

候选 manifest **只用于 Gitee main 分支 updates/dev.json**，保留现有 GitHub downloadUrl 等字段，增加 mirrorDownloadUrls。GitHub Pages dev.json 必须完全保持旧schema：旧 App 严格拒绝额外字段。Android 镜像消费由另一独立工作目录实现，未在本分支修改，不能仅发布清单就宣称国内下载生效。

## 真实执行器下一步

- 固定源 GitHub boolean93/openhoyi 和目的 Gitee boolean93/openhoyi，禁止运行参数切任意仓库。
- 在 develop-apk.yml 的 publish 后追加隔离 mirror job；或经独立审查后做受保护的独立镜像 workflow。不能执行不可信 workflow_run.head_sha 或 PR 源码持有发布凭证。
- 使用已发布相同 APK，不重编译、不重签；先实际检查包名、非debuggable、版本、签名及SHA256。
- Gitee 空仓库需单独获准初始化元数据分支，不能误把 GitHub annotated tag 直接当成镜像仓库已有commit。
- 按tag查询，存在同名附件先下载核对；同名不同字节停止，不覆盖、不删除。缺失才上传；超时结果不确定时重新查询，不盲目重复POST。
- APK/分发元数据/校验清单全部匿名回读通过，再以文件SHA compare-and-swap更新 updates/dev.json；更新前复核版本及signer，禁止回退或同版改身份。
- 镜像失败保留上一个完整清单并让job失败，可单独重跑；GitHub成功发布不回滚。禁止将token附加到匿名下载请求或跨主机重定向。
- 先不自动清理历史Release，保留策略另定，不能以配额为由擅自删除。

## 凭据最小权限与安全交接（未执行）

所需能力：读取目标仓库、创建/读取目标Release、上传附件、创建/更新目标仓库 updates/dev.json；不需要账号资料、邮箱、组织管理、SSH/GPG密钥管理、仓库删除。

已在用户已登录 Tabbit 的实际 Gitee 新建表单核实（仅查看、未提交）：选择「仓库级私人令牌」后，仓库范围可选「选择仓库」，最多50个；应仅选 boolean93/openhoyi，不能保留默认「全部仓库」。权限 projects 的说明为「查看、创建、更新指定仓库」；另有 pull_requests、issues、notes、hook，不需勾选。平台强制 user_info 勾选且禁用，无法取消，这意味着仍有基础账号资料访问范围。建议仅 projects 加平台强制 user_info；Release/附件/contents API 在该scope下能否实际成功仍需真实受权验证，不能只根据权限名称声称已验证。期限菜单有7/30/60/90天及永不过期，建议初期30天。当前未生成或读取任何token值。

安全设置建议：用户自己在Gitee生成有期限的最小权限token，直接填入GitHub boolean93/openhoyi 的受保护environment `gitee-mirror` 的secret `GITEE_MIRROR_TOKEN`。先配置environment只允许develop分支及受控工作流，按需设置required reviewer，再放secret；不能仅在YAML写environment名称就视作保护生效。若使用repository secret，需要额外说明所有可获secret工作流的权限面。token不进入聊天、文件、命令参数、日志、APK、Git仓库或截图；不由助手中转。初次投产先由用户完成授权后再验证真实API，任何scope扩大需重新确认。

GitHub官方步骤：Settings → Environments → 指定environment → Environment secrets → Add secret。只核对名称存在与时间，不读取值。见 <https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets>。

## 无凭据已实证 / 未实证

- Gitee官方CLI附件匿名完整下载6.85MB，SHA256吻合；其他公开APK匿名HTTP200。
- Gitee raw原始文本匿名200，Mac条件请求If-None-Match返回304。
- Gitee普通附件单个100M、仓库累计附件1G；当前OpenHOYI dev120 APK约17.55MB。账号页面5GB是仓库存储，不可当成Release附件额度。
- 所测Gitee附件Range返回200，用户不要求续传，因此不作为阻碍。
- 尚未在本项目Gitee上传APK、跑自动同步、校验token权限或测Android新下载源。

## App有限整包重试验收

最多3次完整尝试，例如Gitee两次、GitHub一次；每次截断临时文件，禁止将200拼到残包。短暂网络/5xx重试并退避；取消、磁盘不足不重试；404/权限错误尽早切源。仅最终大小/SHA256/签名/包名/版本全部通过才安装。日志记录App版本、来源名、阶段、HTTP状态和下载字节，不记录带凭证/签名参数的完整URL。默认每日检查与手动检查既有语义保持。

## 资料

- <https://help.gitee.com/repository/release/create>
- <https://gitee.com/sdk/gitee5j/blob/main/docs/RepositoriesApi.md>
- <https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments>

离线测试已纳入 `scripts/verify-pr-checks full`。同版重跑允许已发布清单携带精确匹配本仓库的 `mirrorDownloadUrls`；仅此传输扩展被归一化，版本身份、签名及其他字段仍严格比较。新增恶意目的地址与已发布清单重跑回归。这里只运行相关 Python 检查，不代表完整 Android CI 或自动发布验收通过。

## 事务引擎与 HTTP 适配器（新增，未投产）

- `gitee_mirror_publish.publish`：把本地文件复制到私有临时目录后验证；真实 aapt/apksigner 是默认检验器；测试才注入替身。先校验再写，已有同名附件只匿名回读校验，绝不覆盖或删除。
- 未知创建/上传/清单写入结果只重新读取，不盲目 POST 重试；查询失败不能当不存在。首次清单需要显式 bootstrap 标志，Gitee main 分支必须先经授权初始化。
- 所有附件匿名回读一致后，再读取最新文件版本并进行 SHA compare-and-swap。最后验证公开 raw 清单；CDN 尚未刷新时返回未验证错误，不宣称成功、不回滚，重跑只读验证。
- `gitee_mirror_http.GiteeBackend`：目的仓库与 main 固定；所有 GET 匿名，token只用于写请求body；认证写请求不跟随任何重定向。附件仅允许同仓库 numeric attach_files 跳转以及 foruda.gitee.com/attach_file/数字/同名文件；清单只允许同路径 raw.giteeusercontent.com。
- 后续工作流必须使用固定 concurrency group `openhoyi-gitee-mirror` 且 cancel-in-progress=false，避免两个受权写入者同时创建相同附件。不能允许外部任意运行参数改变仓库、分支或来源。
- 已增加 `gitee_mirror_runner.py` 和 `gitee-mirror.yml`，后者默认禁用，只有仓库变量 GITEE_MIRROR_ENABLED=true 才运行；本次未设置变量、Secret或推送。runner不接受任意index文件，直接调用现有 `release_update_index.build_index` 对最新公开GitHub release进行来源、标签预留、签名检查，并捕获其已验证原始附件字节。实际启用仍必须经过独立审查、权限作用域实证和明确发布授权。
- 新增18项事务与HTTP故障测试，加既有10项规划器，共28项镜像测试。覆盖未知结果、签名失败、资产冲突、并发身份变化、公开清单缓存延迟、CAS请求、匿名重定向、令牌不进URL和认证请求禁止重定向。

实测重定向证据（公开非本仓库样本）：Gitee droidvm/app v1.59 APK 从 releases/download 跳转仓库 attach_files/3148237/download/同名，再至 foruda.gitee.com/attach_file/数字/同名，最终200；oschina/gitee-cli raw/main/go.mod 跳至 raw.giteeusercontent.com/oschina/gitee-cli/raw/main/go.mod 后200。实际本镜像清单仍待发布后验证。

## 待授权上线步骤

1. 先完成代码独立审查与完整CI；本地相关镜像测试33项通过，不等于完整Android CI。
2. 用户明确批准初始化Gitee专用镜像 main 元数据分支（空仓库不能直接作为Release目标），不修改其他仓库。
3. 用户核实Gitee仓库级token所需Release/附件/contents API权限，自行直接存入受保护GitHub environment secret，不通过聊天中转。权限不支持时再决定是否扩大，不自动扩大。
4. 配置环境分支限制/审批，随后由用户批准启用repo变量GITEE_MIRROR_ENABLED=true。第一次workflow_dispatch才可显式allow_first_manifest=true；自动事件永远不能bootstrap。
5. 人工批准第一次真实镜像发布范围：最新已发布GitHub APK、distribution.json、SHA256SUMS.txt及Gitee updates/dev.json。检查公开下载及清单、Android完整下载验证和回退后，再认为投产完成。
6. 后续Develop APK prerelease完成事件自动执行同一安全流程；镜像作业失败不破坏GitHub发布或原Pages清单。禁止随意重跑未知写入，事务引擎先回读。

### 给用户的最短凭据操作

1. Gitee 设置 → 私人令牌 → 生成新令牌 → 仓库级 → 选择仓库 boolean93/openhoyi；只保留 projects（user_info是平台强制项），选30天。用户自行提交并复制，不发给助手。
2. GitHub boolean93/openhoyi → Settings → Environments → gitee-mirror，先限制部署分支仅develop，再把令牌直接添加为 Environment secret `GITEE_MIRROR_TOKEN`。初次测试可启用required reviewer；如果环境功能不可用，不擅自降级成全仓库Secret。
3. 告知助手“Secret已放好”，无需发值。之后在首次真实发布范围获得批准后再启用 `GITEE_MIRROR_ENABLED`，只核对Secret名称/环境保护，不读取值。

`projects`只是一项UI核实结果，不替代实际API授权验收。若API返回401/403，应停止并报告端点/状态，不能自动要求所有权限或创建更大范围token。
