# 萃取配方摘要来源

按已认可萃取v3右栏摘要，遵守docs/git-workflow.md；develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。只读展示，不改变设备命令/门禁/库存，不发版安装合并，不改签名版本；追加revert回退。

开始被现有逻辑接受并建立series后，记录纯值快照traceId/name/temperature/target，Mock和真实路径一致。显示必须匹配当前traceId，手动新杯/旧杯不复用；只是请求配方不是机器读回成功。粉量与豆款仅从该traceId对应journal笔记读取，不拿下一杯准备顶替。缺失显示—，零重量目标保留原无重量目标含义。预览参数来自当前待选profile，未关联用豆不伪称本杯已确认；完整确认流程不变。8语言标签保留。

防串杯与库编辑测试先运行，确认因未实现ShotRecipeSnapshot编译失败；再实现。需完整JVM/双APK、后续原生同杯断言与视觉截图。当前不把摘要出现等同真实设备验收。

完整本地JVM/双Mock APK51秒成功，298 tests/0 failures/0 errors/1既有skip，diff check通过。真实起杯快照绑定与原生摘要截图尚未验证，不当已完成视觉验收。旧0cd05cc大字体补读：334/336、14恢复通过，仅复盘两主题IME长框98px可见77px，已纳入e6eedb8双栏候选，不再有搜索框失败。
