# 业务截图等待系统浮层消退

d3f81bc真实业务截图中剪贴板预览遮住导入表格左下角。只修截图采集，不清剪贴板、不点系统控件、不改产品行为。来源develop66ebda9+ed01de0，feature/task-first-ui→develop，现有PR#4。

AdvancedBusinessUiChecks.captureState在原布局/动作检查之后，临时启用交互窗口读取，读取应用内容矩形与其他窗口中可见可点击节点的屏幕范围；只要系统窗口可操作节点与应用内容相交，就等待自然消退。最多15秒，超时失败并记录节点诊断；不把系统状态栏/任务栏在内容区域之外的控件当遮挡。finally恢复accessibility flags。保留真实全屏截图和四张业务产物尺寸断言，不用重新绘制/裁剪替代。

验证：test APK构建；既有advanced业务CI与import-new/import-conflict实际图。仅编译不证明自然消退检查成功。普通功能分支push，不重启旧CI；不安装、不发版、不合并、不写设备/用户数据。回退追加revert测试改动。
