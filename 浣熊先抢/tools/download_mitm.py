"""断点续传下载 mitmproxy(网络不稳,支持 Range 续传 + 无限重试)。"""
import os
import sys
import time
import urllib.request

URL = "https://downloads.mitmproxy.org/12.1.2/mitmproxy-12.1.2-windows-x86_64.zip"
DST = sys.argv[1] if len(sys.argv) > 1 else "tools/mitmproxy.zip"
TOTAL = 83864237  # 服务端报告的完整大小


def have() -> int:
    return os.path.getsize(DST) if os.path.exists(DST) else 0


def main():
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    attempt = 0
    while have() < TOTAL:
        attempt += 1
        part = have()
        req = urllib.request.Request(URL, headers={
            "Range": "bytes=%d-" % part,
            "User-Agent": "Mozilla/5.0",
        })
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                if r.status == 200 and part > 0:
                    # 服务端不支持 Range,重下
                    part = 0
                    with open(DST, "wb"):
                        pass
                with open(DST, "ab") as f:
                    while True:
                        chunk = r.read(262144)
                        if not chunk:
                            break
                        f.write(chunk)
        except Exception as exc:
            print("[retry %d] at %d/%d bytes (%s)" % (
                attempt, have(), TOTAL, str(exc)[:70]), flush=True)
            time.sleep(2)
    print("DONE %d bytes" % have(), flush=True)


if __name__ == "__main__":
    main()
