# 首页及实时曲线暗色修复整合

- 基线：develop `7fbfcbea69da126b2361c48fcf88e1d6acf80b07`。
- 工作分支：`fix/dark-theme-integration`，PR 目标 develop；用户已授权创建 PR 并在必需检查通过后合并，不手动发布。
- 遵守 [Git 工作流](../git-workflow.md)，整合两个独立工作树的未提交补丁，不修改原工作树。
- 范围：[首页修复](2026-11-12-home-dark-theme.md) 与 [其它页审查及实时曲线修复](2026-11-12-dark-page-completeness.md)，保留两边测试。无导航位置/曲线轴格子动效/采样/协议/门禁改动。
- 验证：同源码 Debug/Mock 构建、Mock 单测及 instrumentation 编译；设备安装和实际截图交由协调线程安排，不在本轮自行操作设备。
- 版本：本地验收沿用平板 versionCode 80，同包同签名，不占发布号。
- 回退：展示代码回退，无数据迁移，不卸载清数据。

## 本轮验证记录

- Astra 对非首页其它页面及实时曲线 diff 做独立只读复核，无阻塞问题。其它页暂未发现确定性暗色遗漏，不做无依据批量修改。
- 浅色图表/操作区/端点/分隔线/按钮色保持原值；新增端点断言从当前实际 chart 的 plot 读取 label 实例，不是新建测试替身。
- 本地 Mock 单测：556 项，555 通过，1 个既有跳过，0 失败/错误。
- Debug 与 Mock 均为 versionCode 80、versionName `0.2.0-dev.80-dark-fix`；证书 SHA256 均 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。
- 未安装或操控设备，未运行设备颜色断言及横竖屏截图验收；不将构建通过当视觉验收。

## 整合真机验证补充

- 2026-11-12 小米平板，确认前台 Mock 秤页且约 23 分钟无用户活动后，同签名同版本双包覆盖，安装后回读 APK 核对。
- 安装前后 Debug 82 个私有文件、Mock 69 个文件全部原哈希一致。测试后 Debug 全部不变；Mock 业务文件不变，仅正常 trace 轮转和 profileInstalled 变化，旧 trace 已完整备份。
- 新增 `darkThemeWindow` 窄范围入口，实际首页/实时页横竖屏×明暗四轮均通过颜色及萃取页布局断言。图表数据只注入实际附着 View，不开始萃取、不改业务记录、不发真实机器命令。
- 首轮操作区测试 tag 被既有 actions tag 覆盖，修正断言使用既有 tag 并移除冗余标记；最终四轮全部通过。
- 首页入场截图存在瞬时系统转场，另补稳定状态原始截图；已目视检查深色首页与实时页横竖屏、浅色实时页。截图不证明实际 BLE 萃取。
- 所有检查结束恢复原应用深色、锁定横屏（rotation=3、accelerometer_rotation=0）、Mock 秤页。真实 Debug 未启动。
- 安装 Debug SHA256 `8cf9fbbdc101fcd3edf5e9f2851c1d9b8e0e433e70cf4037ee83058a988c6821`；Mock SHA256 `659bb339523ce459ba85a5241af7a4c7805bd54c37bf3a724970a53ce93ce1ce`。

## 合入授权与范围

- 用户明确授权直接创建 PR 并合入，不重复设备验收。仅包含暗色展示及对应测试/规划，不包含 Lunar、温度或连接业务任务。
- 通过 `scripts/create-pr` 执行仓库要求的本地检查；等待精确 HEAD 的 branch-policy、verify 通过后合并。
- develop 合入可能触发仓库既有自动预发布，本任务不单独打标签或手动发布。
