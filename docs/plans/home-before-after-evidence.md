# 首页改前改后截图取证

- 需求：同条件 3:2、16:9 真实 Android 截图及并排原图，不修改 UI。
- 基线：develop fa17e28a；工作分支 chore/home-before-after；仅证据分支，不合入、不发布。
- 源码：before e46e41da（PR119 父提交），after fa17e28a（PR119 合并）。
- 复用 task-frame-ui 手动工作流及已有新鲜 Linux CI AVD 保护；两项矩阵分别构建精确源码。先将同一截图脚本复制到 runner 临时目录，再 checkout 应用提交，身份记录取实际 git HEAD。
- 条件：1800×1200、1920×1080；density 200；应用标准字号；fresh Mock 默认数据；各比例顺序一致；不触槽位或设备。
- 检查：已有 capture guards 单测、脚本编译、差异检查；远端仅手动截图，不创建 PR。
- 无协议、持久用户数据、签名升级或设备影响；不在 Mac 使用 AVD，不接触平板。保留原图，拼图仅加标题与边框。
- 回退：删除证据分支即可，无产品代码变化。
