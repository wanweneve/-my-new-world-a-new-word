"""浣熊先生(Mr. Raccoon)小程序后端 API 封装(100% 标准库,无需 pip 安装任何东西)。

接口结构来源:开源项目 Lide4Code/Hxxy(华南农业大学同供应商实测)。
目前该后端无签名/加密参数,鉴权仅依赖请求头 token。
"""
import json
import urllib.error
import urllib.request

# 请求头模拟微信小程序内 XHR 环境(部分后端会校验 from-agent / UA)
BASE_HEADERS = {
    "Connection": "keep-alive",
    "xweb_xhr": "1",
    "from-agent": "wxmini",
    "api-version": "1.0.0",
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 "
        "MicroMessenger/7.0.20.1781(0x6700143B) NetType/WIFI "
        "MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat(0x63090a1b)XWEB/11275"
    ),
    "Content-Type": "application/json;charset=UTF-8",
    "Accept": "*/*",
    "Sec-Fetch-Site": "cross-site",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Dest": "empty",
    "Referer": "https://servicewechat.com/wxf3d088fc4d192e93/113/page-frame.html",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

# 命中这些关键词的失败信息视为登录态失效
_AUTH_HINTS = ("登录", "token", "身份", "授权", "login", "expired", "unauthorized")

TIMEOUT = 8


class ApiError(Exception):
    """业务失败。kind=auth 表示 token 失效,需要重新抓包。"""

    def __init__(self, msg, kind="biz"):
        super().__init__(msg)
        self.msg = msg
        self.kind = kind  # 'auth' | 'biz' | 'network'


def _classify(msg):
    text = str(msg or "").lower()
    return "auth" if any(h in text for h in _AUTH_HINTS) else "biz"


def _unwrap(resp_json):
    if not isinstance(resp_json, dict):
        raise ApiError("响应格式异常: %r" % (resp_json,))
    if resp_json.get("success"):
        return resp_json
    msg = str(resp_json.get("msg") or resp_json.get("ex") or "未知错误")
    raise ApiError(msg, kind=_classify(msg))


def _request(config, url, method="GET", payload=None):
    data = None
    headers = dict(BASE_HEADERS)
    headers["token"] = config.token
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        # 服务端也可能用非 200 状态码返回 JSON 错误,尽量解析出业务信息
        try:
            body = exc.read().decode("utf-8", errors="replace")
        except Exception:
            raise ApiError("HTTP %s" % exc.code, kind="network") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise ApiError("网络错误: %s" % exc, kind="network") from exc
    try:
        parsed = json.loads(body)
    except ValueError as exc:
        raise ApiError("响应非 JSON,请确认 api_host 是否正确", kind="network") from exc
    return _unwrap(parsed)


def load_washers(config, category=None):
    """GET /home/loadHomeWashList → 洗衣机列表。

    category:机器类型编号,1=洗衣机(已确认);烘干机/刷鞋机编号因校而异,
    抓包看小程序对应页面请求的 category 参数。缺省取 config.categories 第一个。
    """
    if category is None:
        cats = getattr(config, "categories", None) or [1]
        category = cats[0]
    url = "%s/home/loadHomeWashList?category=%s" % (
        config.api_host.rstrip("/"), category)
    data = _request(config, url).get("data") or []
    if not isinstance(data, list):
        data = data.get("list") or data.get("washers") or []
    return data


def save_order(config, wash_id, channel=None, bucket_number=None, wash_type_code=None):
    """POST /order/saveDorOrder → 对指定机器下单(抢)。返回 data(订单信息)。"""
    url = config.api_host.rstrip("/") + "/order/saveDorOrder"
    payload = {
        "bucketNumber": bucket_number or config.bucket_number,
        "channel": config.channel if channel is None else channel,
        "washId": wash_id,
        "washTypeCode": wash_type_code or config.wash_type_code,
    }
    return _request(config, url, method="POST", payload=payload).get("data") or {}


class RaccoonAPI:
    """持有 Config 引用的薄封装;配置对象原地热更新,无需重启。"""

    def __init__(self, config):
        self.config = config

    def load_washers(self, category=None):
        return load_washers(self.config, category)

    def save_order(self, wash_id, **overrides):
        return save_order(self.config, wash_id, **overrides)
