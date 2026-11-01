# 入库日期日历入口

依据已认可bean-entry-v2，保持develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4及docs/git-workflow.md。本次只补日期入口，不把未完成模式选择/间距宣告完成。

- BeanEntryActivity每个日期行的输入区域改为FrameLayout，保留原EditText及权重；右侧48dp日历按钮，24dp向量图标，输入end padding避开按钮。行高和标签布局不额外增加。
- 点击使用DatePickerDialog，初值为有效输入或今天；今天只作日历焦点，取消不写字段，确认写ISO LocalDate。保留键盘编辑、空值及原严格保存验证。Activity销毁时dismiss，避免泄漏。
- AdvancedBusinessUiChecks在既有购入日期填写后真实点击入口，核对初始年月日、取消保持原值、确认改变值，再还原预期日期并走原重建/持久化断言。窗口检查要求三按钮全显和48dp。
- 构建Mock/test APK，CI验证后核对实图；不会把编译视为日历运行通过。普通push，不安装/合并/发版或设备操作。无协议/schema/签名变化；追加revert回退。
