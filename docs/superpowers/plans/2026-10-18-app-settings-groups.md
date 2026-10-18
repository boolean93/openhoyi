# 应用设置紧凑分组

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。依据app-settings-v1候选与现有本地偏好契约。

保留整页滚动/限宽，标题在分组外，16dp组间距/内边距，14/16/20/24sp字体。主题改原生Switch但保留写失败回滚；外观与语言同组，语言可点击值保留无障碍完整说明。反馈开关/试听同组，预览状态仍反映播放/停止且离页停止。应用信息绑定实际BuildConfig，不用示意占位；诊断导出与机器设置同组。默认关闭/显式文件选择/不启动owner和不操作机器均不变。

八语言资源、Mock/测试包构建，保留语言切换/失败回滚/音频生命周期检查；页面首尾滚动、深浅/大字需后续CI。普通批次push，不安装、不设备写入、不合并/发版，不改版本/签名/schema。回退展示代码即可。

实施候选已完成分组/开关/文字入口；八语言XML与diff/分支检查通过，Mock/MockAndroidTest构建45s成功（`/tmp/task-app-settings-groups-build.log`）。语言选择显示nativeName，无障碍描述保留完整原文；独立失败注入fixture仍保留完整label断言。主题回滚/音频/滚动实测待CI，未验收通过。
