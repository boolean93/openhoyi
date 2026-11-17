# Mock 合成卡片视觉验收入口

- 范围：Mock专用Host的＋菜单新增固定推荐/修改两场景，每屏一张生产CardView；仅合成数据、纯内存，无用户存储、Key、网络、Service或机器调用。
- 来源/工作分支：既有feature/ai-chat（develop来源）；交付精确3文件overlay到冻结Mock141完整首页/历史基线，不覆盖整树。未来目标develop，本轮不PR、不push、不合并。
- 文件：AiOfflinePreviewHost.kt、AiCurveCardPreviewFixture.kt、testMock/AiCurveCardPreviewFixtureTest.kt。
- 接口：fixture.cards()固定两张纯数据卡；lines(index)固定场景；Host私有scene索引仅本实例，不写Bundle/Session，无外部JSON。
- 验证：静态依赖边界已检查；focused fixture测试、编译/lint及Astra独审由统一owner串行安排。未做Android验收，不把fixture当真实服务。
- 协议/安全：Proposal只构造展示数据，不建store/coordinator或注册permit；按钮只显示离线提示，无导航、存储、机器副作用。真实完整阶段和差异保留。
- 发布/升级：不自行构建/安装；142需独立同签名版本核验与新覆盖授权。不得沿用141许可。
- 回退：仅撤回以上3文件增量，保留所有用户数据和其他未提交更改。
