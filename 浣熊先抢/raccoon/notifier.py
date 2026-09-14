"""抢购成功通知:置顶弹窗 + 蜂鸣警报(纯标准库,仅 Windows)。

设计目标:人不在电脑前也绝不错过——
  1. MessageBox 系统模态置顶弹窗(自带提示音,压过所有窗口)
  2. 蜂鸣持续循环,直到用户点掉弹窗为止
"""
import ctypes
import os
import threading

# MessageBoxW 标志:系统模态 + 前台 + 置顶 + 感叹号图标
_MB_FLAGS = 0x1000 | 0x10000 | 0x40000 | 0x30

_user32 = ctypes.windll.user32 if os.name == "nt" else None


def _beep_loop(stop_event):
    """交替双频蜂鸣,直到弹窗被点掉。"""
    try:
        import winsound
    except ImportError:
        return
    while not stop_event.is_set():
        try:
            winsound.Beep(1800, 180)
            winsound.Beep(2500, 180)
        except Exception:
            break


def notify_success(machine_name):
    """弹置顶窗 + 循环蜂鸣,阻塞调用线程直到用户点确定。请放到子线程里调。"""
    if os.name != "nt" or _user32 is None:
        return
    stop = threading.Event()
    t = threading.Thread(target=_beep_loop, args=(stop,), daemon=True)
    t.start()
    try:
        _user32.MessageBoxW(
            0,
            "🎉🎉🎉  抢到 %s !!\n\n"
            "立刻打开【浣熊先生】小程序付款,超时可能被释放!" % machine_name,
            "浣熊先抢 · 抢到了!!",
            _MB_FLAGS)
    except Exception:
        pass
    finally:
        stop.set()
