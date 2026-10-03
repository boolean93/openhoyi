# 多语言详情与确认取消检查

延续用户已授权的原生功能对标/显示核验，依托现有真实页面矩阵，不另建产品架构或改协议。先测现有UI，真实失败后再最小修复。

## 范围与实现

- [ ] 在androidTest独立LanguageDetailChecks中检查CurveActivity实际show方法：每语言选择格式化文字最长的工厂曲线、一条已采集曲线，以及不加入曲线库的只读不可启动fixture。核对详情文字/资格按钮，滚动，compact固定详情操作行与导航分隔。不点击“使用”或槽位分配；曲线/槽位偏好不变。
- [ ] 在已有Mock Home的Service上暂时设置三种manualSafetyResource（真实hub必须null），仅触发原Home render；验证对应资源解析与真实警告文字fit，保留导航。finally恢复原字段并render，要求消息/杯状态/所有偏好不变。此项只证明合成提示显示，不证明真正未知结果的协议/恢复门禁。
- [ ] Extraction临时选择已验证capture-2（finally精确恢复curves map），调用原confirmStart但不点正向按钮。用公开UiAutomation访问真实原生AlertDialog，核对Mock标题、参数正文、开始/取消动作及取消按钮可见并仅点击取消。杯状态/历史/原消息不变。Accessibility正文完整不等于所有弹窗文字视觉fit；不测试真实控制确认分支。
- [ ] 现有LanguagePageChecks于每种页面正确绑定/显示后调用三个检查，复用真实两种窗口/八语言/双主题；新增独立最终标记，脚本两个配置都必须命中。原160页面和所有语言/音频标记不删。
- [ ] Mock测试APK/Mock单测/Lint、脚本syntax、diff --check、独立只读审查，提交并运行云端，读取每种配置产物；失败按产品与测试原因区分，不以编译代替运行。

不覆盖历史详情全部状态、机器设置/睡眠等真实确认对话框、母语质量、图轴单位、真实Service/BLE硬件。语言入口仍关闭；不得将少量代表曲线当成100条曲线全部布局已验收。

本地Mock测试APK、Mock单测和Lint成功（/private/tmp/hoyi-language-details-reviewed.log，107 tasks）；bash -n和diff --check通过。独立只读审查最新稿未发现实质问题，确认详情布局等待、Mock/hub双重隔离、仅取消button2以及Accessibility flags/曲线偏好/失败清理独立恢复；只是静态审查，Android运行待云端。新增最终标记不会替代原160页面和既有回归标记。生产UI/协议未改。

首轮8987993完整构建37117962991通过，Mock37117962959失败。compact中文两主题五页及新增检查先完成，英语浅色Extraction报missing positive action；未证明产品文字缺失。SDK34 TextView.getTextForAccessibility返回mTransformed而非mText，原断言按资源原文比较存在原生按钮AllCaps不匹配。最小测试修正：同Activity原生AlertDialog无回调probe只create不show/attach，使用实际transformationMethod得到精确显示预期；真实窗口通过button1/2 ID核对文字/可见/启用，仅取消。不用忽略大小写放宽断言，不改生产UI。Mock testAPK/单测/Lint通过（/private/tmp/hoyi-language-dialog-transformation.log，107 tasks），独立静态复审无问题，云端验证待补。旧失败产物/private/tmp/hoyi-language-details-37117962959。

2026-10-03：测试修正版d69de55已推送。新Mock任务37118581341经gh确认in_progress，watch exec session19514仍保存到/private/tmp/hoyi-language-dialog-native-ci-watch.log；新完整构建任务37118581368。下一步先查询这两个既有任务，不重启或重复dispatch；完成后下载37118581341产物，核对compact/wideFont各80 PAGE_START、页面marker、新DETAIL_DIALOG marker和原八语言/五音频marker，再按失败修正。当前没有复验成功证据。
