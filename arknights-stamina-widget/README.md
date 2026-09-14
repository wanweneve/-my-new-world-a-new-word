# 明日方舟体力卡（Arknights Stamina Widget）

面向华为 Mate 70 Pro+（鸿蒙 4.x / 可安装 APK 的"半血鸿蒙"环境）的**桌面小组件（AppWidget）**：
在桌面 2×2 卡片上**实时显示博士剩余理智（体力）与回满倒计时**，数据来自鹰角官方「森空岛」接口。

> ⚠️ 非官方应用，仅供个人学习与使用。仅做**只读查询展示**，绝不修改任何游戏数据；请勿把凭证提供给任何第三方。

---

## 特性

- 📱 **桌面 2×2 小组件**：当前体力 / 上限、进度条、"回满还需 x 小时 y 分 / 已回满"、上次同步时间；
- 🔄 **真·实时数据**：绑定鹰角/森空岛账号后直连官方接口读取 `data.status.ap`（当前理智 + 服务器回满时刻）；
- 🔋 **低频同步 + 本地推算**：小组件默认每 15 分钟静默同步一次（可点按打开 App 手动刷新）。同步拿到「回满时刻」后，两次同步之间由本地按恢复速率 **1 理智 / 6 分钟** 推算，数值平滑且几乎不耗电；
- 🔐 **凭证本地加密**：登录凭证用 Android Keystore（AES/GCM）加密后落盘，卸载/解绑即清除；网络仅直连鹰角/森空岛官方域名；
- 🧩 **零第三方依赖**：全部使用 Android 系统框架 API（HttpURLConnection / org.json / 系统加密），APK 小巧，利于侧载；
- 🚀 **零广告、无多余权限**：仅申请 `INTERNET` 与开机重排闹钟权限。

## 适用性说明

- 华为 Mate 70 Pro+ 出厂鸿蒙 4.x（Android 兼容层，"半血鸿蒙半血安卓"）：**可安装本 APK 并添加桌面小组件**（推荐）。
- 已升级鸿蒙 NEXT（纯血鸿蒙）：**无法安装 APK**，本项目的 Android 小组件不可用；如需鸿蒙原生"服务卡片"，请参考 [docs/harmonyos-port.md](docs/harmonyos-port.md)（迁移思路）。
- 其他安卓 7.0+（API 24+）手机通用。

## 截图

（待补充：桌面卡片 ×1、主界面 ×1）

## 快速开始

### 1. 获取安装包

构建产物统一放在 `dist/`（`app/build/outputs/apk/…` 的副本，本仓库已生成两个可直接侧载的 APK）。三种方式任选：

| 方式 | 说明 |
| --- | --- |
| **直接下载** | 取 `dist/arknights-stamina-widget-v1.1.0-debug.apk`（或 `-release.apk`）安装到手机 |
| **GitHub Actions 云端出包** | 把本仓库推到 GitHub → Actions → `build-apk`（已内置 workflow）→ 下载 `apks` 构件 |
| **本机构建** | 见下文「从源码构建」，产物会自动复制进 `dist/` |

### 2. 安装到 Mate 70 Pro+

1. 用数据线/微信/网盘把 APK 传到手机，用「文件管理」打开；
2. 首次会提示"禁止安装未知来源应用"→ 点允许（华为：设置 → 应用与服务 → 应用管理 → 右上角设置 → 允许外部来源应用，或在弹窗中直接授权「文件管理」）；
3. 完成安装后打开 **明日方舟体力卡**。

### 3. 绑定账号（三选一）

**方式 A1：手机验证码登录（推荐）**
1. 输入你注册《明日方舟》的鹰角账号手机号，点「获取验证码」；
2. 填入短信验证码 → 「登录并绑定」。App 会自动完成：验证码换 token → OAuth 换森空岛凭证(cred) → 读取你的方舟角色列表并绑定**默认官服角色**。

**方式 A2：手机号 + 密码登录**
输入同一手机号与鹰角账号登录密码 → 「密码登录并绑定」。密码仅用于**一次性换取 token**：不落盘、不上传任何第三方（直连鹰角 `as.hypergryph.com`），无论成败界面都会自动清空密码框。后续逻辑与方式 A1 完全相同。

**方式 B：粘贴已有 token**
1. 从森空岛网页版抓取**鹰角 token**：电脑浏览器登录 [www.skland.com](https://www.skland.com) → F12 → Network → 任意 `web-api.skland.com` 请求的响应里 `data.content` 即 token（整段 JSON 或 content 值均可粘贴）；参考 [nonebot-plugin-skland 凭证获取说明](https://docs.qq.com/doc/p/2f705965caafb3ef342d4a979811ff3960bb3c17)；
2. 粘贴到"方式二"输入框 → 「使用此凭证」。

> 凭证只会保存在你手机本地（Keystore 加密）。服务器端不经过任何第三方代理，App 直连 `as.hypergryph.com` / `zonai.skland.com`。

### 4. 添加桌面小组件

1. 回到桌面，**长按空白处**（或用双指捏合）进入桌面编辑；
2. 选择底部/右侧 **服务卡片 / 窗口小工具 / 卡片**；
3. 找到 **体力卡 · 明日方舟**（图标为深蓝底金色沙漏），按住拖到桌面；
4. 拖动四角调成 **2×2** 大小。稍等片刻即会显示体力；点按卡片任意位置可打开 App（进入自动刷新）。

### 5.（可选）省电设置建议

华为系统对后台管理较激进，若发现长时间不同步：
- 设置 → 应用 → 应用启动管理 → **明日方舟体力卡** → 关闭"自动管理" → 手动允许**自启动 / 关联启动 / 后台活动**；
- 设置 → 应用 → 明日方舟体力卡 → 电池 → **允许后台运行**（不限制）；
- 若你希望更及时的"满体"更新，可把 App 加入后台锁定（多任务卡片下拉加锁）。

## 界面说明

- **主界面**：绑定状态；体力当前值/上限；回满还需；同步时间；"立即刷新体力"（手动同步）；"解绑"（清除本地凭证）。
- **小组件**：
  - 第一行：博士昵称（未绑定则显示 App 名）；
  - 中部大数字：当前体力，右侧 `/上限`；
  - 进度条：当前体力占比（上限按游戏机制/接口推算）；
  - 底行：`回满还需 x 小时 y 分 · z 分钟前`（已满显示 `已回满`）。

## 从源码构建

环境要求：JDK 17+、Android SDK（platform 35 + build-tools 35.0.0）。项目零第三方依赖。

```bash
# 使用项目自带 gradle wrapper（首次会自动下载）
./gradlew assembleDebug          # 产物: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # 签名在 app/build.gradle 中配置(默认 debug 签名即可侧载)
```

Windows 命令行：

```bat
gradlew.bat assembleDebug
```

也可以直接用 Android Studio 打开根目录（`File → Open`），等待同步后 `Build → Build APK(s)`。

> 提示：如你使用华为/国内网络拉不动依赖，可在 `settings.gradle` 的仓库里追加阿里云镜像：
> ```groovy
> maven { url 'https://maven.aliyun.com/repository/google' }
> maven { url 'https://maven.aliyun.com/repository/public' }
> ```

## 项目结构

```
app/src/main/java/com/arknights/stamina/
├── MainActivity.java        # 主界面：绑定/解绑/手动刷新
├── StaminaWidgetProvider.java # 桌面小组件
├── WidgetRenderer.java      # 用快照渲染 RemoteViews
├── SyncEngine.java          # 同步编排（后台线程 → 持久化 → 刷组件 → 回调）
├── SyncScheduler.java       # 周期闹钟（默认 15 分钟）
├── SyncAlarmReceiver.java   # 闹钟接收器
├── BootReceiver.java        # 开机重排闹钟
├── Store.java               # SharedPreferences + Keystore 加密凭证
├── StaminaSnapshot.java     # 体力快照模型与本地推算
└── skland/
    ├── SklandClient.java    # 森空岛/鹰角接口客户端（凭证链、签名、查询）——移植自 skland-kit@0.3.5
    ├── DeviceProfile.java   # 数美设备指纹 dId（与签名配套）——移植自 skland-kit@0.3.5
    └── SklandException.java # 接口异常（中文消息）

reference/skland-kit-0.3.5/  # 协议移植依据的 MIT 源码存档
dist/                        # 构建产物（可直接侧载）
docs/install-guide.md        # 华为手机图文安装/使用教程
```

## 技术要点 / 原理

1. **数据源**：官方「森空岛」App 的游戏数据接口。
   - 获取绑定角色：`GET https://zonai.skland.com/api/v1/game/player/binding`（header: `Cred`）
   - 玩家数据：`GET https://zonai.skland.com/api/v1/game/player/info?uid=<角色uid>`（header: `Cred`）
   - 体力字段：`data.status.ap.current`（当前理智）、`data.status.ap.completeRecoveryTime`（回满 Unix 时间戳）；其余还可用 `data.status.name/level` 等。
2. **凭证链**：`phone+短信码 → as.hypergryph.com token → oauth2 grant → 森空岛 cred`；凭证需附带请求签名（社区统称 Skland Sign），详见 `SklandClient.java` 注释与文末致谢。
3. **本地推算**：`current(t) = min(按快照递增值, 由回满时刻反推值)`，恢复速率 1 理智 / 360 秒，回满即钳制到上限。
4. **更新节流**：手动刷新最小间隔 20 秒；周期同步默认 15 分钟（`Store.setIntervalMs` 可改，暂未开放 UI）。

## 隐私与安全

- 凭证（cred/token）仅存本机，Android Keystore AES/GCM 加密；界面不显示明文。
- 无任何统计/上报/云服务；`allowBackup=true` 但加密串备份后无密钥不可用（Android Keystore 不随备份迁移）。
- 建议：不要把带登录态的手机随意 Root/刷机后恢复备份。

## 免责声明

本项目与《明日方舟》、鹰角网络、森空岛无任何关联，未获官方授权。游戏内数据与接口可能随时变更，本项目按现状提供且不保证可用性；因使用本项目产生的一切后果由使用者自行承担。**请遵守鹰角网络用户协议，勿将本项目用于商业用途。**

## 致谢 / 开源许可

- 森空岛接口文档与字段解读：[@ProbiusOfficial/Skland_API](https://github.com/ProbiusOfficial/Skland_API)（MIT）
- 森空岛浏览器扩展（签名与展示逻辑参考）：[@AEtherside/rhodes-headquarters](https://github.com/AEtherside/rhodes-headquarters)（MIT）
- 签名/请求细节最终以 `SklandClient.java` 头部注释为准；详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 本项目自身代码：MIT，见 [LICENSE](LICENSE)。
