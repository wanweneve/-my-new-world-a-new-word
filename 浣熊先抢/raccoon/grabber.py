"""抢购引擎:后台线程自适应轮询洗衣机列表,任意目标机器释放即抢,抢到即停。

策略(参考 Lide4Code/Hxxy 并优化):
  - 支持多种机器类型同盯(洗衣机/烘干机/刷鞋机),按勾选顺序优先
  - 发现"可用"机器 → 立即下单,失败记录熔断计数(连续 5 次失败暂时跳过该机)
  - 支持指定机器号(只抢这几台)与排除机器号(这些不抢)
  - 全部占用时按 min(remainTime) 自适应休眠:>5min→10s,>2min→5s,>1min→2s,≤1min→1s
  - token 失效 → 立即停止并提示重新抓包,不做无谓请求
"""
import threading
import time
from collections import deque
from datetime import datetime

try:
    from raccoon.notifier import notify_success
except Exception:  # 通知失败绝不能影响抢购引擎
    def notify_success(machine_name):
        pass

IDLE = "idle"
WATCHING = "watching"
GRABBING = "grabbing"
SUCCESS = "success"
STOPPED = "stopped"
ERROR = "error"

_STATE_TEXT = {
    IDLE: "空闲",
    WATCHING: "监控中",
    GRABBING: "抢购中",
    SUCCESS: "抢到了!快去小程序付款",
    STOPPED: "已停止",
    ERROR: "出错",
}

MAX_FAILURE_PER_WASHER = 5


def normalize_washer(raw: dict, category=None) -> dict:
    """统一洗衣机字段,供前端渲染。category=机器类型编号。"""
    remain = raw.get("remainTime") or 0
    try:
        remain = int(remain)
    except (TypeError, ValueError):
        remain = 0
    state = raw.get("state")
    state_name = raw.get("stateName") or ""
    if not state_name and state is not None:
        state_name = {0: "维护中", 1: "可用", 3: "洗涤中", 4: "漂洗中"}.get(state, "未知")
    return {
        "category": category,
        "washId": raw.get("washId"),
        "number": raw.get("washroomNumber"),
        "name": raw.get("washAllInfo") or "%s号机" % raw.get("washroomNumber", "?"),
        "room": raw.get("washRoomId"),
        "state": state,
        "stateName": state_name,
        "available": state_name == "可用" or state == 1,
        "remainTime": remain,
        "config": raw.get("washerConfig") or "",
        "fault": bool(raw.get("faultState")),
    }


class GrabEngine:
    def __init__(self, api, config):
        self.api = api
        self.config = config
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread = None
        self.state = IDLE
        self.exclude = []
        self.include = []
        self.washers = []
        self.washers_ts = None
        self.result = None
        self.error = None
        self.stats = {"polls": 0, "attempts": 0, "started_at": None, "elapsed": 0}
        self._logs = deque(maxlen=300)

    # ---------- 对外接口(Flask/http 线程调用) ----------

    def log(self, msg: str) -> None:
        with self._lock:
            self._logs.append({"t": datetime.now().strftime("%H:%M:%S"), "msg": msg})

    def is_running(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def start(self, exclude=None, include=None) -> tuple:
        with self._lock:
            if self.is_running():
                return False, "抢购已在进行中"
            if not self.config.ready():
                return False, "请先在配置里填写 token 和桶编号"
            self._stop.clear()
            self.exclude = [int(x) for x in (exclude or [])
                            if str(x).strip().lstrip("-").isdigit()]
            self.include = [int(x) for x in (include or [])
                            if str(x).strip().lstrip("-").isdigit()]
            self.state = WATCHING
            self.result = None
            self.error = None
            self.stats = {
                "polls": 0, "attempts": 0,
                "started_at": datetime.now().strftime("%H:%M:%S"), "elapsed": 0,
            }
            self._thread = threading.Thread(target=self._run, daemon=True)
            self._thread.start()
        scope = []
        if self.include:
            scope.append("只抢 %s 号机" % self.include)
        if self.exclude:
            scope.append("排除 %s 号机" % self.exclude)
        self.log("🚀 开始监控%s" % (",".join(scope) if scope else ""))
        return True, "ok"

    def stop(self) -> None:
        if self.is_running():
            self._stop.set()
            self._thread.join(timeout=5)
            with self._lock:
                if self.state not in (SUCCESS, ERROR):
                    self.state = STOPPED
            self.log("🛑 已手动停止")

    def _categories(self) -> list:
        cats = getattr(self.config, "categories", None) or [1]
        return [c for c in cats if isinstance(c, int) and c > 0] or [1]

    def refresh(self) -> None:
        """空闲状态下手动刷新洗衣机列表(拉取全部已选类型并合并)。"""
        if not self.config.token:
            raise RuntimeError("请先填写 token")
        merged = []
        for cat in self._categories():
            merged.extend(normalize_washer(w, cat)
                          for w in self.api.load_washers(category=cat))
        with self._lock:
            self.washers = merged
            self.washers_ts = time.time()

    def status(self) -> dict:
        with self._lock:
            return {
                "state": self.state,
                "stateText": _STATE_TEXT.get(self.state, self.state),
                "running": self.is_running(),
                "exclude": self.exclude,
                "include": self.include,
                "washers": self.washers,
                "washersTs": self.washers_ts,
                "result": self.result,
                "error": self.error,
                "stats": self.stats,
                "logs": list(self._logs)[-80:],
            }

    # ---------- 引擎主循环(后台线程) ----------

    def _run(self) -> None:
        started = time.time()
        failure_counts = {}
        last_watch_note = ""
        try:
            while not self._stop.is_set():
                with self._lock:
                    self.stats["elapsed"] = round(time.time() - started, 1)

                # 逐个类型拉取并合并;单个类型失败只告警跳过,auth 失败立即终止
                cats = self._categories()
                raw_pairs = []
                for cat in cats:
                    try:
                        items = self.api.load_washers(category=cat)
                    except Exception as exc:
                        if getattr(exc, "kind", "") == "auth":
                            raise
                        self.log("⚠️ 类型%s 获取失败:%s" % (cat, exc))
                        continue
                    raw_pairs.extend((cat, w) for w in items)
                if not raw_pairs:
                    self._sleep(3)
                    continue

                with self._lock:
                    self.stats["polls"] += 1
                    self.washers = [normalize_washer(w, cat) for cat, w in raw_pairs]
                    self.washers_ts = time.time()
                    washers = self.washers

                targets = [w for w in washers
                           if w["number"] is not None
                           and w["number"] not in self.exclude
                           and (not self.include or w["number"] in self.include)]
                # 按勾选的类型顺序优先,同类型内先到先得
                cat_rank = {c: i for i, c in enumerate(cats)}
                available = sorted(
                    (w for w in targets if w["available"] and not w["fault"]),
                    key=lambda w: cat_rank.get(w["category"], 99))

                if available:
                    with self._lock:
                        self.state = GRABBING
                    for washer in available:
                        if failure_counts.get(washer["washId"], 0) >= MAX_FAILURE_PER_WASHER:
                            continue
                        with self._lock:
                            self.stats["attempts"] += 1
                        self.log("🎯 发现可用:%s,尝试下单…" % washer["name"])
                        try:
                            order = self.api.save_order(washer["washId"])
                        except Exception as exc:
                            failure_counts[washer["washId"]] = failure_counts.get(washer["washId"], 0) + 1
                            self.log("❌ %s 下单失败:%s" % (washer["name"], exc))
                            if getattr(exc, "kind", "") == "auth":
                                raise
                        else:
                            self._finish_success(washer, order)
                            return
                        time.sleep(0.2)
                    self._sleep(0.3)
                else:
                    failure_counts = {}
                    remains = [w["remainTime"] for w in targets
                               if not w["fault"] and w["remainTime"] > 0]
                    min_remain = min(remains) if remains else None
                    if min_remain is None:
                        interval, note = 10, "暂无占用中的目标机器,慢速巡检(10s)"
                    elif min_remain > 5:
                        interval, note = 10, "最近一台还剩 %s 分钟(10s 轮询)" % min_remain
                    elif min_remain > 2:
                        interval, note = 5, "最近一台还剩 %s 分钟(5s 轮询)" % min_remain
                    elif min_remain > 1:
                        interval, note = 2, "最近一台还剩 %s 分钟(2s 轮询)" % min_remain
                    else:
                        interval, note = 1, "⚡ 最后 1 分钟,极速监控中…"
                    with self._lock:
                        self.state = WATCHING
                    if note != last_watch_note:
                        self.log("👀 %s" % note)
                        last_watch_note = note
                    self._sleep(interval)
        except Exception as exc:
            with self._lock:
                self.state = ERROR
                self.error = str(exc)
            hint = "token 已失效,请重新抓包获取!" if getattr(exc, "kind", "") == "auth" else str(exc)
            self.log("🛑 %s" % hint)
        finally:
            with self._lock:
                if self.state in (WATCHING, GRABBING):
                    self.state = STOPPED
                self.stats["elapsed"] = round(time.time() - started, 1)

    def _finish_success(self, washer: dict, order: dict) -> None:
        with self._lock:
            self.state = SUCCESS
            self.result = {
                "washer": washer,
                "order": order,
                "at": datetime.now().strftime("%H:%M:%S"),
            }
        self.log("🎉 抢到 %s!请立刻打开浣熊先生小程序付款,超时可能释放!" % washer["name"])
        # 置顶弹窗 + 蜂鸣警报(子线程,弹窗不挡引擎收尾)
        threading.Thread(target=notify_success, args=(washer["name"],),
                         daemon=True).start()

    def _sleep(self, seconds: float) -> None:
        self._stop.wait(seconds)
