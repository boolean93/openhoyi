# BOOKOO 电量显示在连接状态旁

- 范围：设置 → 设备连接 → 电子秤，已连接文字后同行显示小电池图标及整数电量，不增加状态行或挤占操作。没有当前有效读数时不显示。
- 来源：`develop`（`efeb4635`）；工作分支 `fix/bookoo-battery-status`；目标 `develop`。遵循 [Git 工作流](../git-workflow.md)，由协调线程集成、检查后走 PR。
- 数据：[BOOKOO MINI 官方协议](https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md#receiving-weight)及 [ULTRA 官方协议](https://github.com/BooKooCode/OpenSource/blob/main/bookoo_ultra_scale/protocols.md#receiving-weight)均给出既有 20 字节 `03 0B` 秤数据第 14 字节为剩余电量。沿用帧长、类型、XOR、符号、连接所有权及采样新鲜度检查；大于 100 的字节只忽略电量，不丢弃重量/流速。设置通过当前连接适配器的可信电量统一展示（包含原本已有电量的 Timemore），不改动其他型号的解码或能力。不新增读写、连接握手、重新轮询或控制权限。
- 验证：协议核心检查、连接适配器测试、设置展示条件测试、Mock 构建；需要在托管 Android 模拟器用实布局验收手机/平板。不宣称原生 BLE 或实机已验收。
- 发布和签名：不发版、不安装、不动包名、签名或升级路径。
- 回退：回滚本功能的解析、展示和测试即可；无持久数据迁移、无设备改动。
