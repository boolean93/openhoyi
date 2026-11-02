# 萃取主操作对齐

按extraction-four-series-v3已认可稿，宽横屏停止位于右栏底部，不能继续跨整页成为视觉横条。沿用develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4，遵守docs/git-workflow.md。

新增只负责布局的TaskActionDock：大于等于840dp横向窗口且内容够624dp时，按钮宽度使用与支持栏相同的30%/280–320dp约束，沿逻辑尾端对齐；窄窗全宽。按钮保留root底部独立区域，不放入工具滚动区；短高时仍固定可见。通过实际文字测量决定高度，保留56dp最低目标，不限制行数，不更改停止状态、点击、通知/告警、启动确认或设备命令。采用现有字体/间距token。

检查双Mock APK、本地既有停止状态单测、CI正常/大字号/短高窗口与运行中截图。仅布局，不新增镜像实现测试、不降低现有可达门槛。不发版/安装/合并，不改签名/版本/存储，普通工作分支推送；追加revert回退。693f215单屏任务结束前不推送打断证据。

本地首编译暴露HoyiUi.dp仅接收Activity；组件改用自身resources density转换，避免强制转换Context。最终单测/双Mock APK成功46秒，297/0/0/1，diff check通过。新操作位置原生截图未执行。
