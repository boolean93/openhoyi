# 设置页离开时停止本地预览

9744053生命周期PR任务38857880299失败：LOCAL_AUDIO_CHECKS_FAILED Native audio did not stabilize active=false，栈为verifyFeedbackService第423行（AppSettings请求导航Extraction后1秒内原生音频未停止）。代码证据：AppSettings只在onStop停止预览；onStop可晚于新页绘制，不匹配离开交互页立即停止的要求。

在onPause只停止本地预览并刷新按钮，不注销偏好观察者、不解绑service；原onStop/onDestroy清理保留。这样暂停后直接恢复无需onStart也仍可操作，不自动续播。测试原1秒稳定静音断言保持，不用加超时掩盖。音频核心与设备协议无改动。

遵守docs/git-workflow.md，develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。完整JVM/双APK，后续原生音频导航/切开关/焦点测试复验；本地构建不当原生静音证明。不安装发版合并，普通push/PR与revert回退。

本地完整JVM、Mock与instrumentation APK构建成功45秒（/tmp/task-settings-pause-build.log）；保留原生导航后1秒静音断言，需新CI验证，不能以编译替代。
