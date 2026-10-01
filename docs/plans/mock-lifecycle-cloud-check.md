# Mock 云端生命周期检查与品牌资源声明

已有云端模拟器可验证Android真实回调，无需用户平板。先核对任务终态：976b227的Capture Mock UI（36859468417）成功；Verify native app同提交失败，前一提交38818f8的36858212439也是app_name MissingTranslation。之前离线完整命令未包含Lint，这不是协议失败，也不能声称云端全检查通过。

修复只给Alpha/Mock品牌资源添加translatable=false，默认XML哈希同步到source.json；所有模板/文字值逐项核对不变。七语言仍排除共享品牌，无全局lint抑制。最初本机offline Lint因lint-gradle未缓存而失败，补依赖后两个变体Lint成功，0错误各11警告。

新增独立Verify Mock lifecycle工作流，构建/安装Mock、Android34模拟器执行scripts/check_mock_lifecycle.sh，只收集OpenHoyiLifecycle日志/错误/Activity状态，不截图。Mock-only Application观察者输出visible/hidden，回调报告started/recreating和配置destroy；Alpha无这些日志，观察不发送命令也不改变模型状态。

检查五主页面导航没有假后台，实际主题Switch保存dark且框架收到Home配置stop；Home真实后台、后台wm size往返无额外可见边界，返回只产生一次visible。初始Application观察者hidden计入1次。后台配置变化可能延迟至前台：background-resize.json明确记录是否观察到后台Home配置destroy，不把wm size成功当作后台重建已运行。重建中断、锁屏和多窗口仍未覆盖。

静态审查曾发现循环漏Extraction，已补；另记录了后台resize不等于实际重建的证据限制。Android runtime结果以工作流终态和日志为准，尚未执行本轮新脚本时不能标作通过。

## 2026-10-01 云端首轮结果

78e3140的完整Verify native app任务36860976218成功，含Lint和编译资源核验；Verify Mock lifecycle任务36860976201成功。后者artifact已读取（本机/private/tmp/hoyi-mock-lifecycle-36860976201），result.txt通过，日志五个主页面started齐全、Home主题配置stop/destroy后仍单一可见会话，两次真实Home后台各只新增一个hidden，返回只新增一个visible。

background-resize.json为homeConfigurationDestroyObservedWhileBackground=false：系统没有在本次后台尺寸往返中立即重建Home，该路径不算已验收。仅证明尺寸变化期间没有额外可见会话。初始密度配置还触发过一次Home重建；随后加强主题测试为Switch前后配置stop计数必须增加并输出theme-recreation.json，避免只匹配早先的marker而误通过。加强版运行结果须另查对应新提交。

本地完整协议/会话回归及三构建成功；最后Mock诊断增量双变体测试145项（144通过、1缺真实导出跳过）、两构建与Lint再次通过，两变体各0错误11警告。19Python/生成一致性、6AAPT转义fixture与两APK各6969编译值匹配。独立审查发现并修复导航漏Extraction，另记录后台resize覆盖限制。没有改协议、Hub、重连策略或生命周期状态模型；没有操作咖啡机。

## 加强版复跑

ad6488f的Verify Mock lifecycle任务36862089510与Verify native app任务36862089698均成功。生命周期artifact读取于本机/private/tmp/hoyi-mock-lifecycle-36862089510：theme-recreation.json为Home配置stop从0增至1，result.txt通过；后台立即配置destroy仍为false，不提高该路径验收结论。所有软件控制源文件与78e3140一致，仅加强脚本/文档与旧版静态证据。

运行链接：https://github.com/boolean93/openhoyi/actions/runs/36862089510 、https://github.com/boolean93/openhoyi/actions/runs/36862089698 。78e3140原有Capture Mock UI任务36860976197也已成功，不代表逐张视觉审查或真实设备测试。尚缺重建中断、锁屏、多窗口和实际BLE窗口/代次运行时验收。
