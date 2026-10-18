# 导入页空态对齐

沿用已认可的curve-import-v2及整合规格，不新增功能。feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md；当前工作树无他人改动。不合并、发版、签名变化或安装设备；普通合批push，回退用追加revert。

1. CurveImportActivity在来源按钮前增加16sp引导句及8dp组内间距；输入框增加“在此粘贴曲线数据”hint，正文统一16sp。保留固定label、文本恢复、完整64KiB说明、所有原回调。
2. 八语言advanced_profiles.xml新增profiles_import_intro和profiles_import_placeholder，两者仅显示，不作为输入值或解析默认值。
3. XML解析、diff检查、assembleMock及assembleMockAndroidTest验证；仅可逆文案/布局调整，不另写镜像测试。既有页面/输入/业务CI保留，新实图核查空态、长文本、键盘和1.3字号，构建不作为视觉验收。
