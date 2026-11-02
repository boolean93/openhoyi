# 咖啡曲线

## 数据来源结论

咖啡曲线主要是本地数据，不从服务端更新。bundle模块 `0ffc` 硬编码了100条工厂曲线，四个分类各25条：`dark`、`medium`、`light`、`super`。当前工厂曲线版本是3。

首次运行时，`getFactoryChartList()` 深拷贝内置列表到 `globalData.chartList`。用户编辑后的列表保存在 `hy_chartLib`。启动时 `migrateSavedChartList()` 按 `hy_factoryChartVer` 将旧版本的工厂条目升级到当前字段值，同时尽量保留非工厂条目。恢复出厂会重新写入内置列表。

更新服务器只用于App APK和咖啡机OTA文件，没有发现下载曲线库的逻辑。要发布新的内置曲线，需要修改bundle并重新构建APK，或后续新增明确的曲线导入/同步功能。

## 曲线对象

典型对象字段如下：

| 字段 | 含义 | 编码说明 |
|---|---|---|
| `name`、`tips`、`icon`、`category` | 展示信息 | 只在App侧使用 |
| `flow` | 总目标水量/体积 | `0x02` 的bytes 3–4，大端 |
| `weight` | 目标杯重 | App结合电子秤自动停止；不直接进入机器曲线帧 |
| `wdelta` | 重量补偿 | App侧停止逻辑 |
| `time` | 闷蒸/预浸秒数 | `0x02` byte 5低7位 |
| `temp` | 萃取温度 | `0x02` byte 2，整数℃ |
| `press` | 压力跟随模式开关 | `0x02` byte 1 bit 7 |
| `chart` | 曲线/变流模式开关 | `0x02` byte 1 bit 6 |
| `seg` | 段数，0–4 | `0x02` byte 1 bits 3–5 |
| `press1..4` | 各段压力或流速目标 | 0.1单位，放入bytes 6–9 |
| `flow1..4` | 各段目标水量 | 乘10后分别编码为16位值 |
| `time1` | 第一段时间/切换值 | `0x02` byte 12 |
| `seg1FlowMode` | 第一段按流量切换标记 | 与byte 5高位组合 |
| `factory` | 工厂条目标记 | 控制迁移/恢复逻辑 |

字段名沿用原bundle命名，部分名字同时承载压力曲线和变流曲线语义。修改协议映射前应以编码函数为准。

## 下发到咖啡机

机器有5个曲线槽位。`startChart(slot, enabled)` 从 `chartList[slot-1]` 取出曲线，编码成20字节 `0x02` 报文。临时曲线由 `startTempChart()` 使用相同格式下发。停止/非曲线启动使用5字节 `0x02` 报文。

20字节帧末尾是前19字节异或校验。当前实现会校验槽位1–5是否存在，但曲线字段仍有不少 `parseInt` 和隐式截断，后续编辑器应增加范围校验，再进入编码层。

## 实时曲线与历史

机器用 `0x80` 返回萃取时间、压力、累计水量、温度、水流及状态位。若电子秤已连接，App将秤重减去萃取基线，生成当前杯重和重量趋势。

`common/brew-history.js` 只保存持续至少15秒的萃取。记录结构包含：

```text
id, createdAt, durationSec, chartSlot, profileName,
snapshot,
points: { t, press, flow, wFlow, wTrend }
```

历史保留30天，最多500条，单次内存采样最多约2000点。存储键为 `brew_history_v1`，没有云同步。
