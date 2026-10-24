# 电子秤读数层级

对照scale-v2与390a7ae标准横屏ScaleActivity.png，主重量是300细体、大白卡包围，计时标题和值挤在一行。改重量为700字重（原64/48sp不变），移除装饰读数卡边框/白底；计时改20sp标题、40sp/700数字上下排，16/8dp留白。原本地计时含义、无数据—、去皮门禁/确认、采集粉量和连接入口均不变。次区仍滚动，字号不随空间缩小。

趋势图尚未实现，必须基于真实秤采样、缺口与来源标识另行实现和验证，不能以空态截图关闭此项。当前Mock无秤数据，不能伪造18g/电量/稳定状态来匹配稿件。

遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。双Mock APK及既有多语言/大字体/短高/独立秤业务检查与真实截图。纯样式，不加镜像测试；不安装/合并/发版、不改协议/数据/签名，普通push、追加revert回退。

本地双Mock APK40秒构建成功、diff检查通过；候选原生大字体/短高截图仍待验证。后续趋势图来源已定位ScaleObservation.receivedAtMs/hundredthsGram/evidence；当前ScaleActivity每250ms轮询快照，同一receivedAtMs不能重复当新采样，断开/过期必须断线，不得把轮询间隔当采样时间。
