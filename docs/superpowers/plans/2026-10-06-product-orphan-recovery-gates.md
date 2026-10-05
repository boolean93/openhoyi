# 不完整恢复记录的产品入口验证

目标：SharedPreferences原字段经过真实MobileService加载后，同样保持控制门禁。纯JVM模型通过不足以证明默认false/null的Android适配接线。

扩展既有ServiceRecoveryGateChecks的13fixture，不改生产。新增5fixture：完整shot-only；shot false但地址残留（合法/非法）；machine kind缺失但地址残留（合法/非法）。共18fixture，每组六个已有机器控制入口，共108次；ACK attempt18后warning/raw prefs必须保持。正常空记录保留service_unavailable对照，避免缺owner误当恢复门禁。

每fixture明确原始字段和预期有效pending/kind；shot-only及shot残留要求确切restart文案，machine残留要求UNKNOWN文案。UUID偏好与detached Service、mock=null/hub=null、禁系统服务/权限/组件、Handler及TraceStore清理沿用。不是READY/真实GATT或全部设备操作验证；不把去皮、连接或紧急停止混为机器设置入口，也不改现有政策。

- [x] 增5fixture与明确预期，保持原13fixture断言；marker调整为18/108/18。
- [x] 测试APK/Lint101任务编译成功，shell语法与diff检查通过；独立只读复审无阻塞。日志 `/private/tmp/hoyi-product-orphan-gates-build.log`，实际运行未计通过。
- [ ] 提交推送，核对现有云端运行和新marker及原完整矩阵；未执行不计通过。
