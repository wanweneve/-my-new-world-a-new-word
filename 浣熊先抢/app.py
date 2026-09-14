"""浣熊先抢 · 本地网页控制台(100% 标准库,零依赖)

运行: python app.py  →  浏览器打开 http://127.0.0.1:8090
"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from raccoon.api import RaccoonAPI
from raccoon.config import Config
from raccoon.grabber import GrabEngine

ROOT = Path(__file__).resolve().parent

config = Config.load()
api = RaccoonAPI(config)
engine = GrabEngine(api, config)


def _json_bytes(obj):
    return json.dumps(obj, ensure_ascii=False).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    server_version = "HuanXianXianQiang/1.0"

    def log_message(self, fmt, *args):  # 静默访问日志
        pass

    def _send(self, code, obj, content_type="application/json; charset=utf-8"):
        body = obj if isinstance(obj, bytes) else _json_bytes(obj)
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return {}

    # ---------- GET ----------

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            self._send(200, (ROOT / "templates" / "index.html").read_bytes(),
                       "text/html; charset=utf-8")
        elif path == "/api/config":
            self._send(200, {"success": True, "data": config.__dict__,
                             "ready": config.ready()})
        elif path == "/api/grab/status":
            self._send(200, {"success": True, "data": engine.status()})
        else:
            self._send(404, {"success": False, "msg": "not found"})

    # ---------- POST ----------

    def do_POST(self):
        try:
            if self.path == "/api/config":
                config.update(self._body())
                engine.log("💾 配置已保存")
                self._send(200, {"success": True, "data": config.__dict__,
                                 "ready": config.ready()})

            elif self.path == "/api/washers":
                try:
                    engine.refresh()
                except Exception as exc:
                    msg = ("token 已失效或未填写,请重新抓包获取"
                           if getattr(exc, "kind", "") == "auth" else str(exc))
                    self._send(200, {"success": False, "msg": msg})
                    return
                self._send(200, {"success": True, "data": engine.status()})

            elif self.path == "/api/grab/start":
                payload = self._body() or {}
                ok, msg = engine.start(payload.get("exclude") or [],
                                       payload.get("include") or [])
                self._send(200, {"success": ok, "msg": msg})

            elif self.path == "/api/grab/stop":
                engine.stop()
                self._send(200, {"success": True})

            else:
                self._send(404, {"success": False, "msg": "not found"})
        except Exception as exc:  # 兜底,避免 500 只吐 HTML
            self._send(500, {"success": False, "msg": str(exc)})


class Server(ThreadingHTTPServer):
    daemon_threads = True


def main():
    print("=" * 46)
    print("  浣熊先抢 控制台: http://127.0.0.1:8090")
    print("  按 Ctrl+C 停止")
    print("=" * 46, flush=True)
    try:
        Server(("127.0.0.1", 8090), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\n已退出")


if __name__ == "__main__":
    main()
