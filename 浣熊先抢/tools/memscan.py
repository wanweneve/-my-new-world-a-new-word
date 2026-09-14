"""从微信小程序进程(WeChatAppEx/Weixin)内存中提取浣熊先生(mrrac.com)凭证。

原理:小程序是 XWeb(Chromium)内核,发过的 HTTP 请求头/响应体会留在堆内存里。
本脚本以只读方式扫描本用户进程,仅提取四类目标信息:
  1. token 候选(特征:长 URL-encoded 串 / 紧跟 token 关键字的长串)
  2. API 域名(*.mrrac.com)
  3. bucketNumber(桶编号)
  4. washTypeCode / channel(下单参数线索)
最后逐个用候选 token 调 loadHomeWashList 验证,服务端确认哪个有效。
仅限自用调试,请勿扫描他人进程。
"""
import ctypes
import json
import re
import sys
import urllib.request
from ctypes import wintypes

# ---------- Windows API ----------
PROCESS_QUERY_INFORMATION = 0x0400
PROCESS_VM_READ = 0x0010
MEM_COMMIT = 0x1000
PAGE_NOACCESS = 0x01
PAGE_GUARD = 0x100
MAX_REGION = 256 * 1024 * 1024   # 跳过超大映射区
CHUNK = 8 * 1024 * 1024          # 分块读取
OVERLAP = 4096

_k32 = ctypes.WinDLL("kernel32", use_last_error=True)


class MBI(ctypes.Structure):
    _fields_ = [
        ("BaseAddress", ctypes.c_void_p),
        ("AllocationBase", ctypes.c_void_p),
        ("AllocationProtect", wintypes.DWORD),
        ("_pad1", wintypes.DWORD),
        ("RegionSize", ctypes.c_size_t),
        ("State", wintypes.DWORD),
        ("Protect", wintypes.DWORD),
        ("Type", wintypes.DWORD),
        ("_pad2", wintypes.DWORD),
    ]


def scan_process(pid, found):
    h = _k32.OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, False, pid)
    if not h:
        return 0
    scanned = 0
    try:
        mbi = MBI()
        size = ctypes.sizeof(mbi)
        addr = 0
        while _k32.VirtualQueryEx(h, ctypes.c_void_p(addr), ctypes.byref(mbi), size):
            base = mbi.BaseAddress or 0
            rsize = mbi.RegionSize or 0
            if base + rsize > 0x7FFFFFFEFFFF:
                break
            addr = base + rsize
            if mbi.State != MEM_COMMIT or rsize == 0 or rsize > MAX_REGION:
                continue
            if mbi.Protect == PAGE_NOACCESS or (mbi.Protect & PAGE_GUARD):
                continue
            # 分块读
            left, cur = rsize, base
            while left > 0:
                take = min(CHUNK + OVERLAP, left)
                buf = (ctypes.c_char * take)()
                got = ctypes.c_size_t(0)
                if not _k32.ReadProcessMemory(h, ctypes.c_void_p(cur), buf,
                                              take, ctypes.byref(got)) or got.value == 0:
                    break
                data = buf.raw[:got.value]
                scanned += got.value
                _extract(data, found)
                if left <= CHUNK:
                    break
                cur += CHUNK
                left -= CHUNK
    finally:
        _k32.CloseHandle(h)
    return scanned


# ---------- 提取规则 ----------
# 兼容两种形态:URL-encoded(%2F..)与解码后的 base64(含 + / =)
RUN_RE = re.compile(rb"[A-Za-z0-9_\-.%+/=]{80,700}")
TOKCTX_RE = re.compile(rb"token[\r\n\t :\"'=\\]{1,8}([A-Za-z0-9_\-.%+/=]{50,700})")
HOST_RE = re.compile(rb"[a-z0-9][a-z0-9\-]{1,30}\.mrrac\.com")
BUCKET_RE = re.compile(rb"bucketNumber[\r\n\t :\"'=\\]{1,8}(\d{5,15})")
WTC_RE = re.compile(rb"washTypeCode[\r\n\t :\"'=\\]{1,8}([A-Z][A-Z_]{3,29})")
CHAN_RE = re.compile(rb'["\']?channel["\']?\s*[:=]\s*\\?"?(\d{1,2})')
PCT_RE = re.compile(rb"%[0-9A-Fa-f]{2}")


def _looks_like_token(s: bytes) -> bool:
    """URL-encoded(≥6 个 %XX)或 base64 形态(含 + / = 且大小写数字混合)。"""
    if len(PCT_RE.findall(s)) >= 6:
        return True
    if b"=" in s or b"+" in s or b"/" in s:
        has_upper = any(65 <= c <= 90 for c in s)
        has_lower = any(97 <= c <= 122 for c in s)
        has_digit = any(48 <= c <= 57 for c in s)
        return has_upper and has_lower and has_digit
    return False


def _extract(data, found):
    # 快速预筛:不含任何目标痕迹的字节块直接跳过
    if not (b"%" in data or b"rrac" in data or b"ucketNumber" in data
            or b"ashTypeCode" in data or b"annel" in data or b"oken" in data):
        return
    if b"rrac" in data:
        found["hosts"].update(m.group().decode() for m in HOST_RE.finditer(data))
    for m in BUCKET_RE.finditer(data):
        found["buckets"][m.group(1).decode()] += 1
    for m in WTC_RE.finditer(data):
        found["wash_types"][m.group(1).decode()] += 1
    for m in CHAN_RE.finditer(data):
        found["channels"][m.group(1).decode()] += 1
    for m in TOKCTX_RE.finditer(data):
        if _looks_like_token(m.group(1)):
            found["tokens"][m.group(1).decode()] += 2  # 带上下文的候选加权
    for m in RUN_RE.finditer(data):
        s = m.group()
        if _looks_like_token(s):
            found["tokens"][s.decode()] += 1


# ---------- 服务端验证 ----------
def try_token(host, token):
    req = urllib.request.Request(
        "%s/home/loadHomeWashList?category=1" % host.rstrip("/"),
        headers={"token": token, "from-agent": "wxmini", "api-version": "1.0.0",
                 "User-Agent": "Mozilla/5.0 MicroMessenger MiniProgramEnv/Windows"})
    try:
        with urllib.request.urlopen(req, timeout=6) as resp:
            body = json.loads(resp.read().decode("utf-8", "replace"))
    except Exception:
        return None
    if isinstance(body, dict) and body.get("success"):
        return body.get("data")
    return None


def main():
    import collections
    found = {"hosts": set(), "tokens": collections.Counter(),
             "buckets": collections.Counter(), "wash_types": collections.Counter(),
             "channels": collections.Counter()}
    pids = [int(x) for x in sys.argv[1:]]
    for pid in pids:
        n = scan_process(pid, found)
        print("[pid %s] scanned %.1f MB" % (pid, n / 1048576), flush=True)

    hosts = sorted(found["hosts"]) or ["https://hxxs.mrrac.com"]
    # 候选排序:上下文加权 > 更长;去重截断
    cands = [t for t, _ in found["tokens"].most_common(40)]
    print("hosts: %s" % hosts, flush=True)
    print("token candidates: %d, buckets: %s, washTypes: %s, channels: %s" % (
        len(cands), dict(found["buckets"]), dict(found["wash_types"]),
        dict(found["channels"])), flush=True)

    result = {"hosts": hosts, "buckets": dict(found["buckets"]),
              "wash_types": dict(found["wash_types"]),
              "channels": dict(found["channels"]),
              "candidates": cands, "valid": None}
    for host in hosts:
        scheme = host if host.startswith("http") else "https://" + host
        for tok in cands:
            data = try_token(scheme, tok)
            if data is not None:
                result["valid"] = {"host": scheme, "token": tok,
                                   "machines": len(data) if isinstance(data, list) else "?"}
                print("VALID! host=%s token=%s... machines=%s" % (
                    scheme, tok[:24], result["valid"]["machines"]), flush=True)
                break
        if result["valid"]:
            break
    with open("scan_result.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print("done -> scan_result.json", flush=True)


if __name__ == "__main__":
    main()
