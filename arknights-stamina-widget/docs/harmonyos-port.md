# 鸿蒙 NEXT（纯血鸿蒙）迁移思路

华为 Mate 70 系列出厂为鸿蒙 4.x（带 Android 兼容层，可装本项目的 APK）。
如果你已升级到鸿蒙 NEXT（纯血鸿蒙，**无法运行 Android APK**），需要把本工具重写为鸿蒙原生形态：

## 形态选择

| 形态 | 说明 |
| --- | --- |
| **服务卡片（万能卡片）2×2** | 桌面卡片，ArkTS 开发，体验与本项目最接近。注意：鸿蒙卡片是**快照式刷新**，无"每秒自绘"能力，同样采用**低频同步 + 显示回满时刻**策略，系统定时刷新 + 点按刷新（点按可拉起应用做即时同步）。 |
| 原子化服务（元服务） | 免安装、桌面加卡片；适合工具型应用，但需上架华为应用市场（AGC）审核。 |

## 所需工具与门槛

- 开发工具：DevEco Studio（Windows/macOS），当前建议 HarmonyOS 5.x 与 API 12+；
- 真机调试/侧载：需要在 [华为 AGC](https://developer.huawei.com/consumer/cn/agconnect/) 注册开发者并配置签名（自动签名需要华为账号）；
- 纯血鸿蒙无法使用微信扫码快捷登录等安卓生态能力，网络权限在 module.json5 中声明 `ohos.permission.INTERNET`。

## 复用清单

本项目的以下设计可**直接平移**（逻辑与鸿蒙无关）：

- `StaminaSnapshot.java` 的推算逻辑（1 理智 / 360 秒；以回满时刻钳制）→ 建议抽成纯 TS/ArkTS 模块；
- 凭证链与 Skland 签名（见 `skland/SklandClient.java`）→ 原样移植，HMAC-SHA256 等系统库 ArkTS 可直接调用；
- 凭证存储：鸿蒙用 `@ohos.security.asset`（Asset Store）或 Keystore 等价能力加密存放。

## 建议架构

```
App(ArkTS)
 ├ 登录/绑定页   —— 手机验证码 → token → cred（同本项目链路）
 ├ 数据服务      —— 定时(建议不短于30min)+前台触发拉取 player/info
 ├ 数据持久化    —— Asset 存 cred；Preferences 存快照
 └ 桌面卡片 2×2  —— 渲染 current/max + “回满还需” 文本
```

> 由于无鸿蒙签名环境、也无鸿蒙设备可验证，本仓库暂不直接提供 ArkTS 工程；如你已升级 NEXT 并希望继续，可在此文档基础上再开一个 `harmonyos/` 目录继续移植。
