# =============================================================================
# Tab2Monitor - server metriche Windows
# Versione: v0.1.0 - 2026-10-21 18:45 UTC
# Progetto: Tab2revamp (Galaxy Tab 2 10.1 GT-P5100 come display risorse)
# =============================================================================
# Cosa fa:
#   - Legge CPU / RAM / dischi / rete / uptime / top processi con psutil.
#   - (Opzionale) legge GPU e temperature da LibreHardwareMonitor (LHM),
#     tramite il suo "Remote Web Server" (data.json). LHM deve girare con
#     il web server attivo sulla porta LHM_URL (default 8085).
#   - Espone:
#       GET /             -> dashboard HTML (file dashboard_*.html piu' recente)
#       GET /api/metrics  -> JSON compatto con tutte le metriche
#   - Ascolta su 0.0.0.0:8090 -> raggiungibile:
#       * via USB:  adb reverse tcp:8090 tcp:8090  (Android >= 5.0)
#       * via Wi-Fi: http://<IP-PC>:8090
#
# Dipendenze: Python 3.8+, psutil (vedi requirements.txt). Solo stdlib per HTTP.
# Avvio: python tab2monitor_server_v0.1.0_20261021-1845.py [--port 8090]
# =============================================================================

import argparse
import glob
import json
import os
import platform
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import psutil

VERSION = "v0.1.0"

# --- Configurazione ----------------------------------------------------------
DEFAULT_PORT = 8090
LHM_URL = "http://127.0.0.1:8085/data.json"   # LibreHardwareMonitor web server
SAMPLE_INTERVAL = 1.0                          # secondi tra due campionamenti
PROC_INTERVAL = 3.0                            # top processi: costoso, ogni 3 s
LHM_INTERVAL = 2.0                             # GPU/temperature ogni 2 s
TOP_N = 5

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DASHBOARD_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "..", "dashboard"))


# --- Utility -----------------------------------------------------------------
def latest_dashboard():
    """Restituisce il file dashboard_*.html con nome (versione/data) piu' alto."""
    files = sorted(glob.glob(os.path.join(DASHBOARD_DIR, "dashboard_*.html")))
    return files[-1] if files else None


def parse_num(text):
    """'45,3 %' / '62.0 °C' / '1234 MHz' -> float (None se non numerico)."""
    if not isinstance(text, str):
        return None
    t = text.replace(",", ".").strip().split(" ")[0]
    try:
        return float(t)
    except ValueError:
        return None


# --- LibreHardwareMonitor ----------------------------------------------------
# data.json e' un albero: {Text, Value, Children:[...], ImageURL, (Type, SensorId)}
# Livelli tipici: root -> PC -> Hardware (CPU/GPU/...) -> Gruppo (Load/Temp) -> Sensore
GPU_HINTS = ("nvidia", "geforce", "radeon", "intel arc", "gpu", "iris", "uhd graphics")
CPU_HINTS = ("ryzen", "threadripper", "epyc", "core i", "core ultra", "xeon", "processor", "cpu")
# 2026-10-21 v0.1.0 (prima stesura, sostituita): "amd" tra i GPU_HINTS faceva
# riconoscere una CPU Ryzen come GPU; ora si usa anche l'icona LHM (ImageURL).
# GPU_HINTS = ("nvidia", "geforce", "radeon", "amd", "intel arc", "gpu", "iris", "uhd")


def walk_lhm(node, path, out, hw_img=""):
    """Foglie -> (path, testo, valore, icona dell'hardware al livello 2)."""
    children = node.get("Children") or []
    if len(path) == 2:                     # questo nodo e' un hardware
        hw_img = (node.get("ImageURL") or "").lower()
    if not children:
        out.append((path, node.get("Text", ""), node.get("Value", ""), hw_img))
        return
    for c in children:
        walk_lhm(c, path + [node.get("Text", "")], out, hw_img)


def hw_is_gpu(name_l, img):
    if "cpu" in img:
        return False
    if any(k in img for k in ("nvidia", "ati", "gpu")):
        return True
    return any(h in name_l for h in GPU_HINTS) and not any(h in name_l for h in CPU_HINTS)


def read_lhm():
    """Estrae: gpu_load, gpu_temp, gpu_mem_load, cpu_temp, lista temperature."""
    try:
        with urllib.request.urlopen(LHM_URL, timeout=0.8) as r:
            data = json.loads(r.read().decode("utf-8", "replace"))
    except Exception:
        return None
    leaves = []
    walk_lhm(data, [], leaves)
    res = {"gpu_name": None, "gpu_load": None, "gpu_temp": None,
           "gpu_mem_load": None, "cpu_temp": None, "temps": []}
    for path, text, value, img in leaves:
        hw = path[2] if len(path) > 2 else ""          # nome hardware
        hw_l = hw.lower()
        txt_l = text.lower()
        num = parse_num(value)
        if num is None:
            continue
        is_temp = "°c" in value.lower() or "temperature" in " ".join(path).lower()
        is_gpu = hw_is_gpu(hw_l, img)
        if is_gpu:
            res["gpu_name"] = res["gpu_name"] or hw
            if "%" in value and txt_l in ("gpu core", "d3d 3d", "gpu"):
                res["gpu_load"] = num if res["gpu_load"] is None else max(res["gpu_load"], num)
            elif "%" in value and "memory" in txt_l and res["gpu_mem_load"] is None:
                res["gpu_mem_load"] = num
            elif is_temp and ("core" in txt_l or txt_l == "gpu") and res["gpu_temp"] is None:
                res["gpu_temp"] = num
        if is_temp:
            if res["cpu_temp"] is None and ("package" in txt_l or "tctl" in txt_l or "core (tctl/tdie)" in txt_l):
                res["cpu_temp"] = num
            if len(res["temps"]) < 8:
                res["temps"].append({"hw": hw, "name": text, "c": num})
    return res


# --- Campionatore in background ---------------------------------------------
class Sampler(threading.Thread):
    """Campiona periodicamente e tiene l'ultimo snapshot pronto per l'HTTP."""

    def __init__(self):
        super().__init__(daemon=True)
        self.lock = threading.Lock()
        self.snapshot = {}
        self._last_net = psutil.net_io_counters()
        self._last_disk = psutil.disk_io_counters()
        self._last_t = time.time()
        self._procs = []
        self._procs_t = 0
        self._lhm = None
        self._lhm_t = 0
        psutil.cpu_percent(percpu=True)               # prima lettura "a vuoto"

    def top_procs(self):
        # cpu_percent per processo richiede due letture: usiamo il valore
        # accumulato dall'ultima chiamata (ogni PROC_INTERVAL secondi).
        ncpu = psutil.cpu_count() or 1
        rows = []
        for p in psutil.process_iter(["name", "memory_info"]):
            try:
                c = p.cpu_percent(None) / ncpu
                m = p.info["memory_info"].rss if p.info["memory_info"] else 0
                rows.append((c, m, p.info["name"] or "?"))
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        rows = [r for r in rows if r[2].lower() not in ("system idle process", "idle")]
        rows.sort(key=lambda r: r[0], reverse=True)
        return [{"n": n, "cpu": round(c, 1), "mb": round(m / 1048576)} for c, m, n in rows[:TOP_N]]

    def sample(self):
        now = time.time()
        dt = max(now - self._last_t, 0.001)
        per = psutil.cpu_percent(percpu=True)
        vm = psutil.virtual_memory()
        sw = psutil.swap_memory()
        freq = psutil.cpu_freq()

        net = psutil.net_io_counters()
        dio = psutil.disk_io_counters()
        net_rx = (net.bytes_recv - self._last_net.bytes_recv) / dt
        net_tx = (net.bytes_sent - self._last_net.bytes_sent) / dt
        d_r = d_w = 0
        if dio and self._last_disk:
            d_r = (dio.read_bytes - self._last_disk.read_bytes) / dt
            d_w = (dio.write_bytes - self._last_disk.write_bytes) / dt
        self._last_net, self._last_disk, self._last_t = net, dio, now

        disks = []
        for part in psutil.disk_partitions(all=False):
            if "cdrom" in part.opts or not part.fstype:
                continue
            try:
                u = psutil.disk_usage(part.mountpoint)
            except (PermissionError, OSError):
                continue
            disks.append({"m": part.mountpoint, "pct": u.percent,
                          "used_gb": round(u.used / 1e9, 1), "tot_gb": round(u.total / 1e9, 1)})

        if now - self._procs_t >= PROC_INTERVAL:
            self._procs = self.top_procs()
            self._procs_t = now
        if now - self._lhm_t >= LHM_INTERVAL:
            self._lhm = read_lhm()
            self._lhm_t = now

        snap = {
            "v": VERSION,
            "ts": int(now),
            "host": platform.node(),
            "cpu": {"pct": round(sum(per) / len(per), 1) if per else 0,
                    "cores": [round(x) for x in per],
                    "mhz": round(freq.current) if freq else None},
            "ram": {"pct": vm.percent, "used_gb": round((vm.total - vm.available) / 1e9, 1),
                    "tot_gb": round(vm.total / 1e9, 1)},
            "swap": {"pct": sw.percent},
            "disk_io": {"r": round(d_r), "w": round(d_w)},
            "disks": disks,
            "net": {"rx": round(net_rx), "tx": round(net_tx)},
            "uptime_s": int(now - psutil.boot_time()),
            "procs": self._procs,
            "lhm": self._lhm,
        }
        with self.lock:
            self.snapshot = snap

    def run(self):
        while True:
            t0 = time.time()
            try:
                self.sample()
            except Exception as e:                    # mai far morire il thread
                print("sample error:", e)
            time.sleep(max(0.05, SAMPLE_INTERVAL - (time.time() - t0)))

    def get(self):
        with self.lock:
            return self.snapshot


SAMPLER = Sampler()


# --- HTTP --------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/metrics":
            body = json.dumps(SAMPLER.get(), separators=(",", ":")).encode("utf-8")
            self._send(200, body, "application/json; charset=utf-8")
        elif path in ("/", "/index.html"):
            f = latest_dashboard()
            if not f:
                self._send(404, b"dashboard non trovata", "text/plain")
                return
            with open(f, "rb") as fh:
                self._send(200, fh.read(), "text/html; charset=utf-8")
        else:
            self._send(404, b"not found", "text/plain")

    def log_message(self, fmt, *args):            # silenzia il log per richiesta
        pass


def main():
    ap = argparse.ArgumentParser(description="Tab2Monitor server " + VERSION)
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    ap.add_argument("--bind", default="0.0.0.0")
    a = ap.parse_args()
    SAMPLER.start()
    print("Tab2Monitor %s su http://%s:%d  (dashboard: %s)" % (VERSION, a.bind, a.port, latest_dashboard()))
    ThreadingHTTPServer((a.bind, a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
