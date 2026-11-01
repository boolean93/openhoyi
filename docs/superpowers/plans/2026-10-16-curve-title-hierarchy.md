# 曲线详情标题与工具层级

对照curve-library-v2和9744053正常态截图，详情名称应在工具前，工具不横占整行。将20sp标题与紧凑操作按钮放入同一行；标题占剩余空间，最多两行，完整文本详情入口保持；紧凑返回按钮保留。主选择操作仍固定右下。现有操作菜单和禁用门禁不变，不新增设备命令。

短高预算按整行实际高度计算，标题不再重复计入metadata；标题随工具行始终保留，避免隐藏后预算震荡。其他元数据和图的既有短高回退保留。

遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。双Mock APK、现有原生页面/长名/短高检查及同条件截图验证。此批为低影响排版，不增加镜像实现测试。不安装/合并/发版、不改签名/schema，普通push及追加revert回退。

本地双Mock APK构建40秒成功（/tmp/task-curve-title-build.log），git diff --check通过；实际长名称、大字体、短高及截图对照仍待原生CI。没有用旧截图证明本候选。
