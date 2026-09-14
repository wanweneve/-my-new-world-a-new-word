"""配置持久化:config.json 存在项目根目录,网页端保存后立即生效。"""
import json
import os
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_PATH = os.path.join(ROOT, "config.json")

DEFAULTS = {
    # 你学校的浣熊先生 API 地址,抓包时看请求的 Host(华农案例是 hxxs.mrrac.com)
    "api_host": "https://hxxs.mrrac.com",
    # 抓包获取的登录凭证,原样复制请求头里的 token 值(URL-encoded 形式,不要解码)
    "token": "",
    # 小程序"我的洗衣桶"里查到的桶编号
    "bucket_number": "",
    # 洗衣房渠道号,手动下单抓包确认(华农案例:2号楼=2,3号楼=3)
    "channel": 3,
    # 洗衣模式代码,手动下单抓包确认(已确认值:QUICK_WASH)
    "wash_type_code": "QUICK_WASH",
    # 机器类型编号列表(可多选同盯):1=洗衣机(已确认),烘干机/刷鞋机编号因校而异,
    # 抓包看小程序对应页面 loadHomeWashList 请求的 category 参数
    "categories": [1],
}

_lock = threading.Lock()


class Config:
    def __init__(self, data: dict):
        merged = dict(DEFAULTS)
        merged.update({k: v for k, v in (data or {}).items() if k in DEFAULTS})
        # 类型列表兜底:非空列表才接受,否则回退 [1]
        cats = merged.get("categories")
        if not isinstance(cats, list) or not cats:
            merged["categories"] = [1]
        self.__dict__.update(merged)

    @classmethod
    def load(cls) -> "Config":
        data = {}
        if os.path.exists(CONFIG_PATH):
            try:
                with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except (OSError, ValueError):
                data = {}
        # 兼容旧版单 category 字段 → 迁移为 categories 列表
        if "categories" not in data and data.get("category"):
            data["categories"] = [data["category"]]
        return cls(data)

    def save(self) -> None:
        with _lock:
            with open(CONFIG_PATH, "w", encoding="utf-8") as f:
                json.dump(self.__dict__, f, ensure_ascii=False, indent=2)

    def update(self, payload: dict) -> None:
        for key in DEFAULTS:
            if key not in payload:
                continue
            value = payload[key]
            if key == "categories":
                cats = []
                raw = value.split(",") if isinstance(value, str) else (value or [])
                for item in raw:
                    try:
                        num = int(item)
                    except (TypeError, ValueError):
                        continue
                    if num > 0 and num not in cats:
                        cats.append(num)
                if cats:
                    self.__dict__[key] = cats
            elif key == "channel":
                try:
                    self.__dict__[key] = int(value)
                except (TypeError, ValueError):
                    pass
            else:
                self.__dict__[key] = str(value).strip() if isinstance(value, str) else value
        self.save()

    def ready(self) -> bool:
        return bool(self.token and self.bucket_number)
