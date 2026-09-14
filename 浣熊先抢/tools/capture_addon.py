"""mitmdump 插件:只记录发往 mrrac.com(浣熊先生)的请求,连同请求头 token、
请求体、响应体一起追加写入 capture_log.jsonl,供主控端读取。"""
import json
import os

LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "capture_log.jsonl")


class Grab:
    @staticmethod
    def _txt(msg):
        try:
            return msg.get_text() or ""
        except Exception:
            return ""

    def response(self, flow):
        try:
            url = flow.request.pretty_url
            tok = flow.request.headers.get("token")
            if "mrrac" not in url and not tok:
                return
            entry = {
                "method": flow.request.method,
                "url": url,
                "token": tok or "",
                "req_headers": dict(flow.request.headers),
                "req_body": self._txt(flow.request)[:4000],
                "status": flow.response.status_code if flow.response else 0,
                "resp_body": self._txt(flow.response)[:20000],
            }
            with open(LOG, "a", encoding="utf-8") as f:
                f.write(json.dumps(entry, ensure_ascii=False) + "\n")
            print("CAPTURED %s %s token=%s..." % (
                entry["method"], url, (tok or "")[:16]), flush=True)
        except Exception as exc:
            print("addon error: %s" % exc, flush=True)


addons = [Grab()]
