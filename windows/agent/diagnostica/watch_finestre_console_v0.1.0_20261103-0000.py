"""Versione: v0.1.0 - 2026-11-03 00:00. Registra le finestre di console VISIBILI che compaiono (classe ConsoleWindowClass / Windows Terminal),
con i processi creati negli ultimi 3 s e la loro catena di genitori. Uso: python -I watch_finestre_console_v0.1.0_20261103-0000.py <minuti>
Il log (watch_visible.log) viene scritto accanto allo script."""
import ctypes, ctypes.wintypes as W, time, os, sys, psutil
u = ctypes.windll.user32
EnumProc = ctypes.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
CLS = ("ConsoleWindowClass", "CASCADIA_HOSTING_WINDOW_CLASS", "PseudoConsoleWindow")
LOG = os.path.join(os.path.dirname(os.path.abspath(sys.argv[0])), "watch_visible.log")
seen = set(); end = time.time() + float(sys.argv[1]) * 60
def out(s):
    with open(LOG, "a", encoding="utf-8") as f: f.write(s + "\n")
def chain(p):
    r = []
    for _ in range(5):
        try:
            c = " ".join(p.cmdline())[:110]; r.append("%s[%d] %s" % (p.name(), p.pid, c)); p = p.parent()
            if p is None: break
        except Exception: break
    return "  <=  ".join(r)
out("inizio %s" % time.strftime("%H:%M:%S"))
def scan():
    found = []
    def cb(h, lp):
        if not u.IsWindowVisible(h): return True
        cls = ctypes.create_unicode_buffer(80); u.GetClassNameW(h, cls, 80)
        if cls.value in CLS or cls.value.startswith("CASCADIA"):
            pid = W.DWORD(); u.GetWindowThreadProcessId(h, ctypes.byref(pid))
            r = W.RECT(); u.GetWindowRect(h, ctypes.byref(r))
            found.append((h, pid.value, cls.value, r.right - r.left, r.bottom - r.top))
        return True
    u.EnumWindows(EnumProc(cb), 0); return found
while time.time() < end:
    for h, pid, cls, w, hgt in scan():
        if (h, pid) in seen: continue
        seen.add((h, pid)); now = time.time()
        title = ctypes.create_unicode_buffer(150); u.GetWindowTextW(h, title, 150)
        out("%s FINESTRA VISIBILE hwnd=%d pid=%d classe=%s %dx%d titolo=%r" % (time.strftime("%H:%M:%S"), h, pid, cls, w, hgt, title.value))
        for p in psutil.process_iter(["name", "create_time"]):
            try:
                if now - p.info["create_time"] < 3 and p.info["name"].lower() in ("cmd.exe", "powershell.exe", "pwsh.exe", "adb.exe", "conhost.exe", "wscript.exe", "cscript.exe", "python.exe", "pythonw.exe", "git.exe", "schtasks.exe", "node.exe", "bash.exe"):
                    out("      recente %.2fs fa: %s" % (now - p.info["create_time"], chain(p)))
            except Exception: pass
    time.sleep(0.02)
out("fine %s" % time.strftime("%H:%M:%S"))
