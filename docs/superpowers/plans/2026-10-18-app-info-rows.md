# 应用信息标签和值布局

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。依据app-settings-v1及3c0019c实图，改应用信息为名称/版本/模式/包名四行，14sp标签、16sp值、8dp分隔；窄窗及大字纵排，包名可选。所有值继续绑定资源/BuildConfig，不新增配置或操作。

新增八语言四标签及版本格式，现有完整版本/包名用于无障碍描述。TaskFrameChecks更新定位为真实包名/新版显示版本，仍检查滚动可见、textFits、偏好未改变与不导出。构建Mock/AndroidTest，diff与资源XML检查；运行待CI，不以构建替代视觉验收。与机器设置整批推送；无协议/设备/安装/合并/发版/签名版本schema变化。回退本布局及文案和相应定位。
