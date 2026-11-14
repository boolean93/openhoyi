# Gitee 国内镜像：离线 PoC 与上线门槛

## 当前边界

仅有离线规划器 `scripts/gitee_mirror_plan.py`，不会访问网络、读取凭据、上传、创建发行版或更新远端清单。不是自动发布功能已完成。Gitee 公开专用仓库已由用户授权创建：<https://gitee.com/boolean93/openhoyi>。现有 GitHub APK 分发、签名和 Android 更新逻辑不改。

```sh
python3 -m unittest discover -s scripts -p 'test_gitee_mirror_plan.py'
python3 scripts/gitee_mirror_plan.py --index /path/to/verified-dev.json --assets /path/to/release-assets --previous-index /path/to/previous-published-index.json
```

输入目录包含原样 APK、distribution.json、SHA256SUMS.txt。index 应来自现有 `release_update_index.py` 验证流程，包含实际 APK 检查，不可仅凭自写 JSON 的哈希宣称通过签名校验。规划器再次验证来源、大小、哈希、元数据、附件清单、版本单调及签名身份，打印候选计划；不写生产 dev.json。首次镜像可省略 previous-index；后续真实执行器必须读取已公开状态，不能将读取失败当成首次发布。

候选 manifest 保留现有 GitHub downloadUrl 等字段，增加 mirrorDownloadUrls。旧 App 仍走 GitHub；新字段还未接入 Android，不能仅发布清单就宣称国内下载生效。

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

当前真实 Gitee UI 的私人令牌页同时列出「私人令牌」「仓库级私人令牌」。必须优先确认仓库级令牌能否覆盖 Release 附件与文件 API，并仅选 boolean93/openhoyi。此次只读打开生成入口后页面没有显示作用域表单，因此**具体权限标识及仓库级token的API兼容性尚未核实**；不要根据旧资料直接让用户选所有项目权限。当前未生成或读取任何token值。

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
