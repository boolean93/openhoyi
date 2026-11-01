# 页面返回超时诊断

693e696的大字号矩阵在ScaleConnectionActivity进入HomeActivity后返回超时；当前日志只证明等待条件失败，不能判断哪一端生命周期未恢复。仅在SingleScreenListStateChecks.detailReturn原await失败时采集主线程Activity状态并带原因抛出。保留原等待时限、所有页面和恢复断言，不新增重试、不改变应用行为。

工作分支feature/task-first-ui，沿用develop66ebda9+ed01de0依赖，PR #4目标develop，遵守docs/git-workflow.md。验证为测试APK构建、diff检查和后续精确提交CI；诊断编译不等于超时已修复。不合并、发布或安装实机，不改签名/schema/协议；普通push，追加revert回退，不清记录。
