# 🦝 浣熊先抢 · 浣熊先生洗衣自动抢洗衣机控制台

本地网页控制台:实时查看宿舍洗衣机状态,一键开始监控,**任意目标机器一释放立刻自动下单,抢到即停并提醒你付款**。

> 仅供个人自用学习交流。请适度使用:低频轮询、抢到自己要用的那台就收手,别影响其他同学,也别把 token 给别人。

## 快速开始

**零依赖**:只用 Python 标准库,不需要 pip 安装任何东西(需 Python 3.8+)。

```bash
python app.py
```

浏览器打开 **http://127.0.0.1:8090** ,按下面步骤填好配置即可。

## 使用步骤(共 4 步)

> **✅ 你学校的参数已全部抓包确认并写入 config.json(2026-09-02),开箱即用。**
> 下面步骤仅在 token 失效后重抓时才需要再看。

### 第 1 步:抓包获取 token

token 是小程序的登录凭证,**有效期未知(几天到几周)**,失效后控制台会自动停下并提示你重抓。

**方式 0:本机自带工具(推荐,已验证可用)**

- **内存扫描**:`python tools/memscan.py` 后按提示带上进程 PID 运行——先在微信里打开浣熊先生小程序并进入洗衣页,再扫描 WeChatAppEx 进程内存即可拿到新 token(脚本会自动逐个候选请求服务器验证)。
- **代理抓包**:`tools/` 里已备好 mitmproxy 12.1.2 + 抓包插件(`capture_addon.py`),流程 = 启动 mitmdump(8080)→ 装 CA 证书 → 系统代理指向 8080 → 重开小程序 → 从 `capture_log.jsonl` 里取 token。

**方式 A:Windows 电脑微信 + Charles**

1. 下载安装 [Charles](https://www.charlesproxy.com/download/)(免费试用即可,不限时)
2. Charles 菜单 `Help → SSL Proxying → Install Charles Root Certificate`,把证书装进"受信任的根证书颁发机构"
3. `Proxy → SSL Proxying Settings` 勾选 Enable,添加一条 `*:*(所有域名所有端口)`
4. `Proxy → Proxy Settings`:HTTP Proxy 端口默认 8888,再勾选 **SOCKS Proxy**(开一个 8889 端口)——微信小程序部分流量走 SOCKS,不开这个经常抓不到
5. Windows 设置里把系统代理指向 `127.0.0.1:8888`
6. PC 版微信打开"浣熊先生"小程序,随便进一个宿舍楼层页面
7. 回到 Charles,找到发往 `xxx.mrrac.com` 的请求(Structure 里按域名找),点开任意一条 → Headers → Request 里复制 **`token`** 的完整值(一长串 URL-encoded,原样复制,不要解码)

> Fiddler 对 Windows 微信小程序经常抓不到(SOCKS/证书问题),直接用 Charles 省心。想看得舒服可以再把 Charles 外部代理转发给 Burp,但对本工具来说没必要。

**方式 B:Android 手机**

1. 安装抓包 App:**HttpCanary(黄鸟)**(免 root,需装证书;安卓 7+ 用户证书对微信可能无效,可配合 magisk 模块把证书装入系统区,或用 VirtualApp 类容器跑微信)
2. 或者电脑 Fiddler + 手机 Wi-Fi 代理:手机和电脑同网,手机 Wi-Fi 手动代理填电脑 IP:8888,浏览器访问电脑 IP:8888 下载并信任 Fiddler 证书(安卓 7+ 同样有用户证书限制)
3. 手机微信打开"浣熊先生"小程序,进宿舍楼层页
4. 在抓包记录里找 `*.mrrac.com` 请求,复制请求头 `token` 值

### 第 2 步:查桶编号

小程序 →「我的洗衣桶」→ 复制自己的**桶编号**(形如 `0003328992`)。没有桶的(滚筒机宿舍)按小程序实际展示填。

### 第 3 步:确认 API 地址、channel、洗衣模式

✅ 已全部抓包确认:

| 参数 | 值 |
|---|---|
| API 地址 | `https://hxxs.mrrac.com` |
| channel | `3` |
| 标准模式 washTypeCode | `GENERAL`(注意:不是通用的 QUICK_WASH!) |
| 洗衣机 category | `1` = 洗衣机(1~4号机) |
| 大件洗 category | `2` = 6/7号机 |
| 洗鞋机 category | `3` = 8号机 |
| 烘干机 category | `4` = 5号机 |

### 第 4 步:开抢

控制台点「**测试连接**」确认能看到洗衣机列表 → 点「**开始抢**」→ 挂着即可:

- 有可用机器 → 立即下单,失败自动换下一台(单机连续失败 5 次熔断,防死刷)
- 全被占用 → 按"最快洗完的那台"剩余时间自适应轮询(剩 1 分钟时切极速模式)
- 抢到 → **三重提醒,人不在电脑前也错不过**:
  1. 系统置顶弹窗(压过所有窗口)+ 蜂鸣警报循环响,直到你点掉为止
  2. 网页急促蜂鸣音 + 标题栏闪烁 2 分钟
  3. Windows 桌面通知(点「开始抢」时授权一次即可)
- token 失效 → 自动停止并提示重抓,不会傻刷

### 可选配置说明

| 配置项 | 说明 |
|---|---|
| 机器类型 | 可多选同盯:洗衣机(1)/大件洗(2)/洗鞋机(3)/烘干机(4),任意一台释放即抢、按勾选顺序优先。四种编号均已抓包确认 |
| 排除机器号 | 如 `4,7`:坏机器、离得远的不抢 |
| 指定机器号 | 如 `1,3,5`:只抢这几台,留空则除排除外全抢 |

## 项目结构

```
app.py               入口:标准库 http 服务器(端口 8090,零依赖)
raccoon/
  config.py          配置持久化(config.json,网页保存即生效)
  api.py             浣熊先生接口封装(列表/下单/失效识别)
  grabber.py         抢购引擎(后台线程·自适应轮询·熔断·抢到即停)
  notifier.py        抢到提醒(置顶弹窗+蜂鸣,纯标准库)
templates/index.html 网页控制台
config.example.json  配置模板(复制为 config.json 填入自己的参数)
tools/
  memscan.py         token 过期时:从微信小程序进程内存重扫 token
  download_mitm.py   下载 mitmproxy 便携版(代理抓包用,断点续传)
  capture_addon.py   抓包插件(记录 mrrac.com 请求到 capture_log.jsonl)
config.json          本机配置(含 token,已被 .gitignore 排除,勿外传!)
```

## 原理与参考

- 供应商:浣熊先生 Mr. Raccoon(mrrac.com),"一人一桶"共享洗衣,入口为微信小程序
- 接口结构来源:开源项目 [Lide4Code/Hxxy](https://github.com/Lide4Code/Hxxy)(华南农业大学同供应商实测):`GET /home/loadHomeWashList` 拉状态、`POST /order/saveDorOrder` 下单,鉴权仅靠请求头 token
- 详细分析见 [浣熊先生-抢洗衣机-分析报告.md](浣熊先生-抢洗衣机-分析报告.md)
- 供应商若升级接口(加签名/风控),只需改 `raccoon/api.py` 适配
