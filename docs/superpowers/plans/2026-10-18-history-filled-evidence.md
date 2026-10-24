# 历史详情有复盘内容的同屏证据

> 使用 superpowers:executing-plans 在当前工作树执行。

**Goal:** 补齐历史详情图表与完整长复盘同时存在的截图，检验局部滚动不挤压图表和固定入口。

**Architecture:** 在AdvancedBusinessUiChecks已有本次唯一UUID记录与七字段复盘保存后，写入仅属于该ID的31个合成样本并打开HistoryDetailActivity；不修改既有用户记录或全局偏好。源文件明确synthetic，不冒充硬件数据。

**Tech Stack:** Kotlin instrumentation / ShotSamplesRepository / 既有截图捕获。

来源develop66ebda9+ed01de0，feature/task-first-ui→develop draft PR#4；遵守docs/git-workflow.md。

- [x] 生成0–30s、31点的四指标夹具，结束重量36g与已存在observation一致；仅在新UUID样本为空时写入，不清数据。
- [x] 等待详情异步读完同样31点；核对七字段存储值及全部正文存在，保存上部截图。将最后一项完整滚入局部视口后保存下部截图，检查图表可见区域未变化，复盘按钮仍可见。
- [x] 保存synthetic来源说明；打开关闭前后journal观察与notes、库存/准备记录不变化；维持原18业务路径计数，不把附加取证冒充新业务路径。
- [x] 构建Mock/instrumentation：`/tmp/task-history-filled-evidence-build.log` 确认 BUILD SUCCESSFUL in 26s；`git diff --check` 与 `python3 scripts/git_policy.py branch` 通过。
- [ ] 精确新源码CI截图待后续批次推送与运行，编译不代替视觉通过。

不设备写入/安装/发版/合并，不改版本/签名/schema；普通push工作分支，回退夹具代码即可，不删除未知记录或重发状态。
