# 分段字号取证补齐

沿用develop66ebda9+ed01de0来源的feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。仅补TaskTypographyEvidence的MetricAffectingSpan分段字号/字重/范围记录，保留原基础字号字段，不修改产品行为或放松现有断言。已有萃取数字与小单位混排需要此证据，不能凭基字号或图像猜测实际字号。

验证：测试包编译、差异检查；实际JSON待CI生成。与设置页整批推送，不为单个取证补丁追加CI。无协议/设备/安装/合并/发布，版本签名数据不变；回退该测试记录字段即可。
