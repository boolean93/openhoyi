# 秤工具设置分组

依据scale-settings-v1与39ac261用户平板未连接实图。来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。

本页宽屏限宽688dp（含左右24dp内边距），窄窗填满可用区，整页滚动。复用TaskSettingsGroup：显示精度、当前能力、使用说明；说明按本地计时/操作限制/协议支持分组。正文16sp/辅助14sp，8/16dp间距。精度短标签与关闭时精度拆行，但保存同一个one_decimal；renderScale协议能力/READY/非Mock判定完全不变，不新增设备命令。

八语言资源、Mock/测试包构建与差异检查；CI后验未连接/真实能力、深浅/大字/底部说明。无安装/设备操作/合并/发版，不改版本/签名/schema。普通批次push；回退本页布局及新增资源。
