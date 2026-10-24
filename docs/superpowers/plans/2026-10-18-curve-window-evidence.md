# 曲线选中态实际窗口取证

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。3c0019c整页是未选中态，viewport夹具为360dp局部Canvas，不可证明平板双栏。保留三个原夹具和两字号检查，恢复实际尺寸后等待两帧，用UiAutomation保存selected-window.png并输出字号/间距JSON；再次核对曲线偏好和使用统计不变。仅预览，不点击使用曲线，不新增数据、不发BLE。

验证测试包构建、diff/分支检查；实际两栏与动作完整性须读CI图片。与机器设置/应用信息整批推送，不新增手动工作流。无设备/安装/合并/发布；回退该新增取证即可。
