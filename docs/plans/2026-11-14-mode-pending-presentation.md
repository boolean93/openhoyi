# 首页模式正常回读等待不显示历史错误

- 基线：develop e0f957e4；分支fix/mode-pending-ui；PR目标develop。
- 现场：local124多次模式切换约370ms取得匹配回读并confirmed；即时红字来自持久保护记录被当作上次未确认，并非实际超时。
- 范围：仅显示层识别原owner、同地址READY、SETTING记录且LeverMode处于WRITING/WAITING_READBACK，隐藏历史未确认红条及恢复按钮；控件继续禁用等待真实回读。首页Fragment与兼容Activity同步。
- 安全：不修改machineWriteSafetyMessage或任何控制门禁；不清记录、不乐观确认。UNKNOWN/断连/重启/换设备/旧owner/清记录失败仍显示警告，ShotSafetyAlert、manual warning及机器告警不受影响。
- 验证：纯状态矩阵覆盖全部tracker状态、连接状态、owner与地址、无/非模式change；独立Astra审查。未操作真实机器、不安装。
- 流程：scripts/create-pr完整检查后普通推送PR，必需检查绿后合入，不绕保护；不主动发版。回退代码，不删除用户数据或恢复记录。

实际验证：JBR21 `:mobile:testDebugUnitTest --tests '*MachineModePendingPresentationTest' :mobile:compileMockKotlin --offline --max-workers=1` 成功，3项矩阵测试0失败。覆盖所有tracker状态（包括已确认/失败但持久记录仍在）与连接状态、身份、owner、change类型。Astra独立生产diff复核通过，无控制权限放宽。
