# 功能地图

## 页面

应用注册了5个页面：

| 页面 | 作用 | 主要逻辑 |
|---|---|---|
| `pages/index/index` | 启动页、隐私确认、进入设备搜索 | `pages/index/index.vue` |
| `pages/index/list` | 扫描并连接HOYI咖啡机，等待 `0x83` 初始化 | `pages/index/list.vue` |
| `pages/home/home` | 主控制台、设备状态、萃取与设置入口 | `pages/home/home.vue` |
| `pages/history/list` | 本地萃取历史列表 | `common/brew-history.js`及历史页面模块 |
| `pages/history/detail` | 历史曲线详情 | 历史页面模块、图表组件 |

## 主要能力

| 功能域 | 能力 | 实现位置或数据 |
|---|---|---|
| 咖啡机连接 | BLE权限、扫描HOYI名称、连接、服务发现、通知重试、断线状态 | `common/bluetooth.js` |
| 实时仪表 | 萃取温度、蒸汽温度、萃取压力、蒸汽压力、加热/休眠状态 | `components/home.vue`、`0x40/0x80` |
| 萃取 | 手动、5个机器槽位、智能/曲线模式、开始停止 | `pages/home/home.vue`、`startChart()` |
| 曲线库 | 100条内置配方、分类、复制、编辑、删除、保存 | 工厂曲线模块、曲线组件、本地键 `hy_chartLib` |
| 实时图表 | 时间、压力、水流、咖啡流、重量和目标参数 | `components/chart.vue`、`components/chartlib.vue`、uCharts |
| 萃取历史 | 保存不少于15秒的记录；最多500条；保留30天 | `common/brew-history.js`、键 `brew_history_v1` |
| 电子秤 | Acaia系、Felicita、DiFluid/Microbalance、BOOKOO；重量、流速、去皮；前台自动重连 | `common/blescale.js`、`pages/home/home.vue` |
| 温控 | 萃取温度、温差补偿、蒸汽温度、加热开关 | 系统组件及 `0x04–0x06/0x0c/0x0d` 命令 |
| 机器设置 | 灯光、运行模式、水源、待机、睡眠计划、密码、杯数复位 | `components/system.vue`及其子组件 |
| 维护 | 拉杆零点校准、恢复出厂、自动排水、报警提示 | 设置组件及 `0x16–0x18` 等命令 |
| OTA | 查询应用/固件版本、下载固件、BLE分块升级 | `components/update.vue`、`0xa0`系列命令 |
| 多语言 | 简中、繁中、英语及多种其他语言 | `app-config-service.js` 和bundle语言模块 |
| 主题 | 深色/浅色资源与运行时切换 | `appDark`、`themeClass`、`static/hoyi*` |
| UI Mock | 模拟机器发现、连接、状态、萃取和电子秤数据 | `variants/mock/runtime.js` |

## 持久化数据

主要使用 `uni.getStorage*` / `uni.setStorage*`，没有本地数据库。已确认的键包括：

| 键 | 内容 |
|---|---|
| `bleInfo` | 最近设备信息 |
| `sacleMac`、`sacleName` | 最近一次成功连接的电子秤；原应用键名包含此拼写 |
| `hy_chartLib` | 当前曲线库 |
| `hy_chartSave` | 曲线库是否已保存/初始化 |
| `hy_factoryChartVer` | 工厂曲线迁移版本，当前为3 |
| `brew_history_v1` | 萃取历史及采样点 |
| `hoyiAutoMode` | HOYI自动压力/曲线模式 |
| `tankFilterEn_<deviceId>` | 每台机器的水箱滤芯设置 |
| `lang`、`sysPwd` | 语言和本地设置密码 |
| `brewTips`、`brewTipsLed`、`ledSw` | 提示与灯光偏好 |
| `runMode`、`waterMode` | 运行和供水偏好 |

应用包名变化后，Android沙箱不同，这些数据不会从旧包自动迁移。

## 外部网络

常规机器控制和曲线库不依赖服务端。明确的网络用途集中在更新页：

- `http://www.newcoffeetech.com.cn/app/hoyi`：查询应用安装包列表。
- `http://www.newcoffeetech.com.cn/ota/hoyi`：查询和下载咖啡机固件。
- `https://www.newcoffeetech.com`：厂商网页入口。

这些接口使用明文HTTP的部分已由Manifest的 `usesCleartextTraffic=true` 放行。后续改版应优先确认服务端是否支持HTTPS，并给更新响应增加格式、版本和文件完整性校验。
