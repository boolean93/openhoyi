# 四阶段分享失败取证

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。4bd173c frame仅两阶段，用户平板四阶段证据需advanced business补齐。仅修改AdvancedBusinessUiChecks：把既有curve-share-four-stages截图移到表格断言之前，保存屏幕dp/fontScale、图尺寸、表显示/测量值。保留四阶段每行完整可见、文件分享禁用及复制内容不变量；不改产品、不降图表阈值、不操作外部分享或设备。先AndroidTest编译与diff检查，再与已验证导入空态合批普通push；不合并/发版/签名改变，回退追加revert。新配置实际CI结果与图片须另验。
