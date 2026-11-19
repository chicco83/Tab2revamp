# =============================================================================
# Tab2Monitor - server metriche Windows
# Versione: v0.2.7 - 2026-10-22 11:30 (ora locale)
# v0.2.7: campo "claude_plan" (limiti del piano dell'account Claude), autorizzato
#         dall'utente il 2026-10-22: token letto da ~/.claude/.credentials.json
#         (claudeAiOauth.accessToken) a ogni interrogazione, mai copiato/loggato;
#         endpoint NON documentato usato da /usage; ogni 5 min; errore -> campo omesso
#         e nessun nuovo tentativo finche' il file credenziali non cambia.
# v0.2.6: top processi, numero processi e thread dai contatori PDH Process(*)/System
#         invece di psutil.process_iter: CPU del server da ~31% di un core a pochi %
#         (misura 2026-10-22: process_iter con num_threads 1265 ms CPU, PDH 5 ms).
# v0.2.5: "hidden": true su ventole/temperature che l'utente ha nascosto in /config
#         (ventola: "non mostrare"; temperatura: casella "nascondi"), chiesto
#         dall'APK che disegna da hw[].fans/temps e non da pairs.
# v0.2.4: nomi e abbinamenti scelti dall'utente. Pagina http://localhost:8090/config
#         (file config_page_v*.html) -> POST /api/config -> fan_config.json (dati
#         utente, fuori da git). Ogni temperatura/ventola/hardware ha un "id" stabile
#         (SensorId di LHM); con un nome utente "name" diventa il nome scelto e
#         "orig" conserva quello originale; pairs con how = "user".
# v0.2.3: (verificato con admin) scartate le soglie (Warning/Critical/High Limit/
#         Resolution) dalle temperature; RAM come kind "ram"; fans[].on (rpm > 0);
#         le ventole della scheda madre ferme (header vuoti) escluse da pairs e le
#         altre abbinate di default alla CPU (curva predefinita ASUS); niente
#         SO_REUSEADDR (su Windows due server potevano ascoltare sulla stessa porta).
# v0.2.2: disks[].r / disks[].w (byte/s) e disks[].busy (% tempo attivo) per ogni
#         volume, dai contatori PDH LogicalDisk (stessa lettera di disks[].m).
# v0.2.1: campo "claude" = token usati con Claude Code, contati dai log locali
#         %USERPROFILE%\.claude\projects\**\*.jsonl (solo i contatori "usage":
#         nessun testo delle conversazioni, nessuna credenziale). Vedi ClaudeUsage.
# Progetto: Tab2revamp (Galaxy Tab 2 10.1 GT-P5100 come display risorse)
# =============================================================================
# Cosa fa:
#   - CPU / RAM / dischi / rete / uptime / top processi con psutil (come v0.1.0).
#   - NUOVO v0.2.0, senza admin e senza librerie extra (solo ctypes):
#       * contatori di Windows (PDH): frequenza reale per core, coda CPU,
#         carico GPU per scheda (stesso calcolo di Gestione attivita'), VRAM usata;
#       * DXGI: nome e VRAM totale di ogni GPU (anche la MI50 / "Radeon Pro VII");
#       * D3DKMT (la fonte di Gestione attivita'): temperatura GPU, ventola, potenza.
#   - LibreHardwareMonitor (LHM, web server 127.0.0.1:8085, richiede admin):
#     TUTTE le temperature e TUTTE le ventole, raggruppate per hardware, con
#     l'abbinamento ventola -> temperatura ("pairs") per mostrarle affiancate.
#   - Se LHM non c'e', l'oggetto "lhm" (letto da APK e dashboard v0.1.0) viene
#     riempito con i dati GPU di Windows (lhm.src = "win") invece di restare null.
#   - Sensori: 1) Tab2Sensors.exe (windows/agent/sensors/Tab2Sensors.cs), processo
#     figlio che usa LibreHardwareMonitorLib.dll della cartella windows/lhm/ del
#     progetto: nessuna app LHM da tenere aperta; 2) in alternativa l'app LHM
#     con web server su 127.0.0.1:8085. CPU/scheda madre richiedono admin (PawnIO).
#   - fan_map_*.json (facoltativo, accanto allo script): abbina a mano una ventola
#     alla temperatura che la governa (es. ventola su header scheda madre che
#     raffredda la MI50) -> "pairs" con how = "map".
#   - HTTP/1.1 keep-alive: il tablet riusa una sola connessione TCP.
#   - adb reverse automatico: ogni ADB_INTERVAL secondi controlla i dispositivi
#     adb e rifa' "adb reverse tcp:PORT tcp:PORT" quando il tablet ricompare.
#
# Schema JSON: i campi della v0.1.0 restano identici (li leggono l'APK, classe
# Metrics in MainActivity.java, e la dashboard web). Campi AGGIUNTI: vedi CONTEXT.md
# sezione "API /api/metrics".
#
# Dipendenze: Python 3.8+, psutil. Avvio:
#   python tab2monitor_server_v0.2.0_20261022-0015.py [--port 8090] [--adb <percorso adb.exe>]
#                                                     [--gpu <parte del nome>] [--no-adb]
# =============================================================================

import argparse
import ctypes
import glob
import json
import os
import platform
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from ctypes import wintypes as W
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import psutil

VERSION = "v0.2.7"

# --- Configurazione ----------------------------------------------------------
DEFAULT_PORT = 8090
LHM_URL = "http://127.0.0.1:8085/data.json"   # LibreHardwareMonitor web server
SAMPLE_INTERVAL = 1.0                          # secondi tra due campionamenti
PROC_INTERVAL = 3.0                            # top processi: costoso, ogni 3 s
LHM_INTERVAL = 2.0                             # LHM / sensori GPU ogni 2 s
ADB_INTERVAL = 5.0                             # controllo adb reverse ogni 5 s
TOP_N = 5
HTTP_IDLE_TIMEOUT = 30                         # s: chiude le connessioni keep-alive ferme

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DASHBOARD_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "..", "dashboard"))
CREATE_NO_WINDOW = 0x08000000                  # adb senza finestra console


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


# --- Utility -----------------------------------------------------------------
def latest_dashboard():
    """Restituisce il file dashboard_*.html con nome (versione/data) piu' alto."""
    files = sorted(glob.glob(os.path.join(DASHBOARD_DIR, "dashboard_*.html")))
    return files[-1] if files else None


def parse_num(text):
    """'45,3 %' / '62.0 °C' / '1234 RPM' -> float (None se non numerico)."""
    if isinstance(text, (int, float)):
        return float(text)
    if not isinstance(text, str):
        return None
    t = text.replace(",", ".").strip().split(" ")[0]
    try:
        return float(t)
    except ValueError:
        return None


def r1(x):
    return None if x is None else round(x, 1)


# =============================================================================
# Contatori di Windows (PDH) via ctypes
# =============================================================================
# Un'unica query PDH con contatori "wildcard" (*): a ogni PdhCollectQueryData
# Windows riespande le istanze (processi che compaiono/spariscono sulla GPU).
# I contatori "rate" (percentuali) richiedono due raccolte: la prima e' a vuoto.
PDH_FMT_DOUBLE = 0x00000200
PDH_FMT_NOCAP100 = 0x00008000
PDH_MORE_DATA = 0x800007D2


class _PdhValue(ctypes.Structure):
    _fields_ = [("CStatus", W.DWORD), ("doubleValue", ctypes.c_double)]


class _PdhItem(ctypes.Structure):
    _fields_ = [("szName", W.LPWSTR), ("FmtValue", _PdhValue)]


class Pdh:
    COUNTERS = {
        "perf": r"\Processor Information(*)\% Processor Performance",
        "basefreq": r"\Processor Information(*)\Processor Frequency",
        "queue": r"\System\Processor Queue Length",
        "gpu_eng": r"\GPU Engine(*)\Utilization Percentage",
        "gpu_ded": r"\GPU Adapter Memory(*)\Dedicated Usage",
        # v0.2.2: I/O per volume (istanze "C:", "D:", ...); busy = 100 - % Idle Time
        # (% Disk Time puo' superare 100 con code lunghe, % Idle Time no)
        "ld_r": r"\LogicalDisk(*)\Disk Read Bytes/sec",
        "ld_w": r"\LogicalDisk(*)\Disk Write Bytes/sec",
        "ld_idle": r"\LogicalDisk(*)\% Idle Time",
    }

    def __init__(self, counters=None):
        self.ok = False
        self.h = {}
        self.counters = counters or self.COUNTERS
        try:
            self.pdh = ctypes.WinDLL("pdh")
            self.q = ctypes.c_void_p()
            if self.pdh.PdhOpenQueryW(None, 0, ctypes.byref(self.q)) != 0:
                return
            for k, path in self.counters.items():
                hc = ctypes.c_void_p()
                if self.pdh.PdhAddEnglishCounterW(self.q, path, 0, ctypes.byref(hc)) == 0:
                    self.h[k] = hc
            self.pdh.PdhCollectQueryData(self.q)       # prima raccolta a vuoto
            self.ok = True
        except Exception as e:
            log("PDH non disponibile:", e)

    def collect(self):
        if self.ok:
            self.pdh.PdhCollectQueryData(self.q)

    def array(self, key):
        """{istanza: valore} del contatore wildcard (vuoto se non disponibile)."""
        hc = self.h.get(key)
        if hc is None:
            return {}
        size, count = W.DWORD(0), W.DWORD(0)
        fmt = PDH_FMT_DOUBLE | PDH_FMT_NOCAP100
        rc = self.pdh.PdhGetFormattedCounterArrayW(hc, fmt, ctypes.byref(size), ctypes.byref(count), None)
        if (rc & 0xFFFFFFFF) != PDH_MORE_DATA or size.value == 0:
            return {}
        buf = ctypes.create_string_buffer(size.value)
        rc = self.pdh.PdhGetFormattedCounterArrayW(hc, fmt, ctypes.byref(size), ctypes.byref(count), buf)
        if rc != 0:
            return {}
        items = ctypes.cast(buf, ctypes.POINTER(_PdhItem))
        out = {}
        for i in range(count.value):
            it = items[i]
            if it.FmtValue.CStatus in (0, 1):          # PDH_CSTATUS_VALID_DATA / NEW_DATA
                out[it.szName] = it.FmtValue.doubleValue
        return out

    def array_list(self, key):
        """[(istanza, valore o None)] nell'ordine di PDH, duplicati compresi
        (Process(*) ha piu' istanze con lo stesso nome, es. svchost)."""
        hc = self.h.get(key)
        if hc is None:
            return []
        size, count = W.DWORD(0), W.DWORD(0)
        fmt = PDH_FMT_DOUBLE | PDH_FMT_NOCAP100
        rc = self.pdh.PdhGetFormattedCounterArrayW(hc, fmt, ctypes.byref(size), ctypes.byref(count), None)
        if (rc & 0xFFFFFFFF) != PDH_MORE_DATA or size.value == 0:
            return []
        buf = ctypes.create_string_buffer(size.value)
        if self.pdh.PdhGetFormattedCounterArrayW(hc, fmt, ctypes.byref(size), ctypes.byref(count), buf) != 0:
            return []
        items = ctypes.cast(buf, ctypes.POINTER(_PdhItem))
        return [(items[i].szName, items[i].FmtValue.doubleValue if items[i].FmtValue.CStatus in (0, 1) else None)
                for i in range(count.value)]

    def single(self, key):
        hc = self.h.get(key)
        if hc is None:
            return None
        v = _PdhValue()
        if self.pdh.PdhGetFormattedCounterValue(hc, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100, None, ctypes.byref(v)) != 0:
            return None
        return v.doubleValue if v.CStatus in (0, 1) else None


def luid_of(instance):
    """'pid_12_luid_0x00000000_0x0001058D_phys_0_eng_3_engtype_3D' -> '0x0001058D'
    (si usa la sola LowPart: la HighPart e' 0 su tutte le schede viste)."""
    p = instance.split("_")
    try:
        i = p.index("luid")
        return p[i + 2].upper().replace("0X", "0x")
    except (ValueError, IndexError):
        return None


# =============================================================================
# GPU: nomi e VRAM da DXGI, temperatura/ventola/potenza da D3DKMT
# =============================================================================
class _LUID(ctypes.Structure):
    _fields_ = [("LowPart", W.DWORD), ("HighPart", W.LONG)]


class _GUID(ctypes.Structure):
    _fields_ = [("d1", ctypes.c_ulong), ("d2", ctypes.c_ushort), ("d3", ctypes.c_ushort),
                ("d4", ctypes.c_ubyte * 8)]


class _DXGI_DESC1(ctypes.Structure):
    _fields_ = [("Description", ctypes.c_wchar * 128), ("VendorId", ctypes.c_uint),
                ("DeviceId", ctypes.c_uint), ("SubSysId", ctypes.c_uint), ("Revision", ctypes.c_uint),
                ("DedicatedVideoMemory", ctypes.c_size_t), ("DedicatedSystemMemory", ctypes.c_size_t),
                ("SharedSystemMemory", ctypes.c_size_t), ("AdapterLuid", _LUID), ("Flags", ctypes.c_uint)]


class _OPENLUID(ctypes.Structure):
    _fields_ = [("AdapterLuid", _LUID), ("hAdapter", ctypes.c_uint)]


class _QAI(ctypes.Structure):
    _fields_ = [("hAdapter", ctypes.c_uint), ("Type", ctypes.c_int),
                ("pData", ctypes.c_void_p), ("Size", ctypes.c_uint)]


class _PERFDATA(ctypes.Structure):
    # D3DKMT_ADAPTER_PERFDATA (Windows 10 1809+): Temperature in decimi di °C,
    # Power in decimi di % del limite di scheda, FanRPM (0 se il driver non lo fornisce)
    _fields_ = [("PhysicalAdapterIndex", ctypes.c_uint32), ("MemoryFrequency", ctypes.c_ulonglong),
                ("MaxMemoryFrequency", ctypes.c_ulonglong), ("MaxMemoryFrequencyOC", ctypes.c_ulonglong),
                ("MemoryBandwidth", ctypes.c_ulonglong), ("PCIEBandwidth", ctypes.c_ulonglong),
                ("FanRPM", ctypes.c_ulong), ("Power", ctypes.c_ulong), ("Temperature", ctypes.c_ulong),
                ("PowerStateOverride", ctypes.c_ubyte)]


KMTQAITYPE_ADAPTERPERFDATA = 62
DXGI_ADAPTER_FLAG_SOFTWARE = 2


def _com_call(obj, index, restype, argtypes, *args):
    """Chiama il metodo n. index della vtable di un oggetto COM."""
    vt = ctypes.cast(obj, ctypes.POINTER(ctypes.POINTER(ctypes.c_void_p)))[0]
    fn = ctypes.WINFUNCTYPE(restype, ctypes.c_void_p, *argtypes)(vt[index])
    return fn(obj, *args)


def list_gpus():
    """[{luid, name, vram_mb, h}] delle GPU hardware (esclude 'Basic Render Driver')."""
    gpus = []
    try:
        dxgi = ctypes.WinDLL("dxgi")
        gdi = ctypes.WinDLL("gdi32")
        u = uuid.UUID("770aae78-f26f-4dba-a829-253c83d1b387")   # IID_IDXGIFactory1
        iid = _GUID(u.fields[0], u.fields[1], u.fields[2], (ctypes.c_ubyte * 8)(*u.bytes[8:]))
        fac = ctypes.c_void_p()
        if dxgi.CreateDXGIFactory1(ctypes.byref(iid), ctypes.byref(fac)) != 0:
            return gpus
        i = 0
        while True:
            ad = ctypes.c_void_p()
            # IDXGIFactory1::EnumAdapters1 = slot 12
            if _com_call(fac, 12, ctypes.c_long, [ctypes.c_uint, ctypes.POINTER(ctypes.c_void_p)],
                         i, ctypes.byref(ad)) != 0:
                break
            d = _DXGI_DESC1()
            _com_call(ad, 10, ctypes.c_long, [ctypes.POINTER(_DXGI_DESC1)], ctypes.byref(d))  # GetDesc1
            _com_call(ad, 2, ctypes.c_ulong, [])                                                # Release
            i += 1
            if d.Flags & DXGI_ADAPTER_FLAG_SOFTWARE:
                continue
            o = _OPENLUID(d.AdapterLuid, 0)
            h = o.hAdapter if gdi.D3DKMTOpenAdapterFromLuid(ctypes.byref(o)) == 0 else None
            gpus.append({"luid": "0x%08X" % d.AdapterLuid.LowPart, "name": d.Description,
                         "vram_mb": round(d.DedicatedVideoMemory / 1048576), "h": o.hAdapter if h else None})
        _com_call(fac, 2, ctypes.c_ulong, [])
    except Exception as e:
        log("DXGI/D3DKMT non disponibile:", e)
    return gpus


def gpu_perfdata(h):
    """(temperatura °C, ventola RPM, potenza %) da D3DKMT; None dove non fornito."""
    if h is None:
        return None, None, None
    try:
        p = _PERFDATA()
        q = _QAI(h, KMTQAITYPE_ADAPTERPERFDATA, ctypes.cast(ctypes.byref(p), ctypes.c_void_p),
                 ctypes.sizeof(p))
        if ctypes.WinDLL("gdi32").D3DKMTQueryAdapterInfo(ctypes.byref(q)) != 0:
            return None, None, None
        t = p.Temperature / 10.0 if p.Temperature else None
        return t, (p.FanRPM or None), (p.Power / 10.0 if p.Power else None)
    except Exception:
        return None, None, None


# =============================================================================
# LibreHardwareMonitor
# =============================================================================
# data.json e' un albero: {Text, Value, Children:[...], ImageURL, Type?, SensorId?}
# Livelli: root -> PC -> Hardware (CPU/GPU/Scheda madre) -> [sotto-hardware, es.
# chip SuperIO] -> Gruppo (Temperatures/Fans/Controls/Load) -> Sensore.
GPU_HINTS = ("nvidia", "geforce", "radeon", "intel arc", "gpu", "iris", "uhd graphics")
CPU_HINTS = ("ryzen", "threadripper", "epyc", "core i", "core ultra", "xeon", "processor", "cpu")
# 2026-10-21 v0.1.0 (prima stesura, sostituita): "amd" tra i GPU_HINTS faceva
# riconoscere una CPU Ryzen come GPU; ora si usa anche l'icona LHM (ImageURL).
# GPU_HINTS = ("nvidia", "geforce", "radeon", "amd", "intel arc", "gpu", "iris", "uhd")


# v0.2.3: nomi di "temperature" che sono soglie fisse (NVMe, DIMM), non letture
TEMP_LIMIT_WORDS = ("warning", "critical", "limit", "resolution")


def walk_lhm(node, path, out, hw_img=""):
    """Foglie -> (path, testo, valore, icona dell'hardware al livello 2, nodo)."""
    children = node.get("Children") or []
    if len(path) == 2:                     # questo nodo e' un hardware
        hw_img = (node.get("ImageURL") or "").lower()
    if not children:
        out.append((path, node.get("Text", ""), node.get("Value", ""), hw_img, node))
        return
    for c in children:
        walk_lhm(c, path + [node.get("Text", "")], out, hw_img)


def hw_is_gpu(name_l, img):
    if "cpu" in img:
        return False
    if any(k in img for k in ("nvidia", "ati", "gpu")):
        return True
    return any(h in name_l for h in GPU_HINTS) and not any(h in name_l for h in CPU_HINTS)


def hw_kind(hw_l, img, sensor_id):
    sid = (sensor_id or "").lower()
    if sid.startswith("/gpu") or hw_is_gpu(hw_l, img):
        return "gpu"
    if "cpu" in sid.split("/")[1:2] or sid.startswith(("/amdcpu", "/intelcpu")) or "cpu" in img:
        return "cpu"
    if sid.startswith(("/lpc", "/mainboard", "/motherboard")) or "mainboard" in img:
        return "mb"
    if sid.startswith(("/nvme", "/hdd", "/ssd", "/storage")) or "hdd" in img:
        return "disk"
    if sid.startswith(("/ram", "/memory")) or "ram" in img or "memory" in img:
        return "ram"
    return "other"


def sensor_type(node, path, value):
    """Temperature / Fan / Control / Load / ... dal campo Type (LHM recente),
    altrimenti dall'unita' del valore o dal nome del gruppo."""
    t = node.get("Type")
    if t:
        return t
    v = (value or "").lower()
    grp = (path[-1] if path else "").lower()
    if "°c" in v or grp.startswith("temperature"):
        return "Temperature"
    if "rpm" in v or grp.startswith("fan"):
        return "Fan"
    if grp.startswith("control"):
        return "Control"
    if "%" in v and grp.startswith("load"):
        return "Load"
    return grp


def read_lhm_raw():
    """data.json dal web server di LibreHardwareMonitor (solo se l'app LHM e' aperta)."""
    try:
        with urllib.request.urlopen(LHM_URL, timeout=0.8) as r:
            return json.loads(r.read().decode("utf-8", "replace"))
    except Exception:
        return None


def find_tab2sensors():
    """Tab2Sensors.exe (windows/agent/sensors/Tab2Sensors.cs compilato) accanto a
    LibreHardwareMonitorLib.dll nella cartella LHM del progetto (versione piu' alta)."""
    dirs = sorted(glob.glob(os.path.join(BASE_DIR, "..", "lhm", "LibreHardwareMonitor_v*")))
    for d in reversed(dirs):
        f = os.path.join(d, "Tab2Sensors.exe")
        if os.path.isfile(f):
            return os.path.normpath(f)
    return None


class SensorReader(threading.Thread):
    """Fonte sensori preferita (v0.2.0): processo figlio Tab2Sensors.exe che usa
    LibreHardwareMonitorLib.dll del progetto e scrive una riga JSON ogni LHM_INTERVAL.
    Nessuna app LHM aperta, nessuna porta 8085. Se il figlio muore viene rilanciato
    dopo 10 s. Con admin legge anche CPU e scheda madre (driver PawnIO)."""

    def __init__(self, exe):
        super().__init__(daemon=True)
        self.exe = exe
        self.data, self.t = None, 0

    def run(self):
        while True:
            try:
                p = subprocess.Popen([self.exe, str(int(LHM_INTERVAL * 1000))], stdin=subprocess.PIPE,
                                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                     cwd=os.path.dirname(self.exe), creationflags=CREATE_NO_WINDOW)
                log("Tab2Sensors avviato (pid %d)" % p.pid)
                for line in p.stdout:              # stdin resta aperto: se il server muore il figlio esce
                    try:
                        self.data, self.t = json.loads(line.decode("utf-8", "replace")), time.time()
                    except ValueError:
                        pass
                log("Tab2Sensors terminato (rc %s), rilancio tra 10 s" % p.wait())
            except Exception as e:
                log("Tab2Sensors errore:", e)
            self.data = None
            time.sleep(10)

    def get(self):
        return self.data if time.time() - self.t < 3 * LHM_INTERVAL + 2 else None


def parse_lhm(data):
    """-> (res compatibile v0.1.0, sensors: {hw:[...], pairs:[...]})."""
    leaves = []
    walk_lhm(data, [], leaves)
    res = {"gpu_name": None, "gpu_load": None, "gpu_temp": None,
           "gpu_mem_load": None, "cpu_temp": None, "temps": [], "src": "lhm"}
    groups = {}                                   # id hardware -> gruppo
    for path, text, value, img, node in leaves:
        hw = path[2] if len(path) > 2 else ""          # hardware di primo livello
        sub = path[-2] if len(path) >= 4 else hw       # hardware o sotto-hardware (SuperIO)
        hw_l = hw.lower()
        txt_l = text.lower()
        num = parse_num(value)
        if num is None:
            continue
        stype = sensor_type(node, path, value)
        is_temp = stype == "Temperature"
        if is_temp and num <= 0:                       # 0 °C = non leggibile (es. CPU senza admin)
            continue
        is_gpu = hw_is_gpu(hw_l, img)
        # --- campi v0.1.0 (stessa logica) ---
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
        # --- v0.2.0: tutte le temperature / ventole / controlli per hardware ---
        if stype not in ("Temperature", "Fan", "Control"):
            continue
        sid = node.get("SensorId") or ""
        gid = sid.rsplit("/", 2)[0] if sid.count("/") >= 3 else hw + "|" + sub
        g = groups.get(gid)
        if g is None:
            g = groups[gid] = {"id": gid, "name": sub or hw, "kind": hw_kind(hw_l, img, sid),
                               "temps": [], "fans": [], "_ctl": {}}
        if stype == "Temperature":
            if any(k in txt_l for k in TEMP_LIMIT_WORDS):    # soglie, non letture
                continue
            g["temps"].append({"id": sid or gid + "/" + text, "name": text, "c": r1(num)})
        elif stype == "Fan":
            g["fans"].append({"id": sid or gid + "/" + text, "name": text, "rpm": round(num), "on": num > 0})
        else:
            g["_ctl"][txt_l] = num
    hw_list = []
    for g in groups.values():
        for f in g["fans"]:                       # % di controllo con lo stesso nome della ventola
            pct = g["_ctl"].get(f["name"].lower())
            f["pct"] = r1(pct)
        del g["_ctl"]
        if g["temps"] or g["fans"]:
            hw_list.append(g)
    return res, {"src": "lhm", "hw": hw_list, "pairs": make_pairs(hw_list)}


def _main_temp(g):
    """Temperatura rappresentativa di un hardware (core/package/edge/hot spot)."""
    best = None
    for pref in ("package", "tctl", "core (tctl", "gpu core", "core", "edge", "hot spot", "junction"):
        for t in g["temps"]:
            if pref in t["name"].lower():
                return t
    for t in g["temps"]:
        best = t if best is None or (t["c"] or 0) > (best["c"] or 0) else best
    return best


FAN_MAP = {}          # "nome ventola" o "hardware|nome ventola" -> "hardware|nome temperatura"


def load_fan_map():
    """Legge il fan_map_*.json piu' recente accanto allo script (chiavi che iniziano
    con '_' = commenti). Esempio: {"Fan #3": "AMD Radeon Pro VII|GPU"}."""
    files = sorted(glob.glob(os.path.join(BASE_DIR, "fan_map_*.json")))
    if not files:
        return {}
    try:
        with open(files[-1], encoding="utf-8") as fh:
            m = json.load(fh)
        m = {k.lower(): v for k, v in m.items() if not k.startswith("_") and isinstance(v, str)}
        log("fan map:", os.path.basename(files[-1]), "(%d voci)" % len(m))
        return m
    except Exception as e:
        log("fan map non valida:", e)
        return {}


def _find_temp(hw_list, target):
    """'hardware|temperatura' -> (indice, gruppo, temperatura) oppure (None, None, None)."""
    hwn, _, tn = target.partition("|")
    for gi, g in enumerate(hw_list):
        if g["name"].lower() == hwn.lower():
            for t in g["temps"]:
                if not tn or t["name"].lower() == tn.lower():
                    return gi, g, t
    return None, None, None


# --- v0.2.4: configurazione utente (nomi e abbinamenti) -----------------------
USER_CFG_FILE = os.path.join(BASE_DIR, "fan_config.json")
USER_CFG = {"names": {}, "map": {}, "hidden": []}
USER_CFG_LOCK = threading.Lock()
NAME_MAX = 40


def load_user_cfg():
    try:
        with open(USER_CFG_FILE, encoding="utf-8") as fh:
            c = json.load(fh)
        USER_CFG["names"] = {k: v for k, v in (c.get("names") or {}).items() if isinstance(v, str) and v.strip()}
        USER_CFG["map"] = {k: v for k, v in (c.get("map") or {}).items() if isinstance(v, str) and v}
        USER_CFG["hidden"] = [k for k in (c.get("hidden") or []) if isinstance(k, str)]
        log("config utente: %d nomi, %d abbinamenti" % (len(USER_CFG["names"]), len(USER_CFG["map"])))
    except FileNotFoundError:
        pass
    except Exception as e:
        log("fan_config.json non valido:", e)


def save_user_cfg(c):
    """Valida e salva (scrittura atomica: file temporaneo + os.replace)."""
    names = {}
    for k, v in (c.get("names") or {}).items():
        if isinstance(k, str) and isinstance(v, str) and v.strip() and len(k) < 200:
            names[k] = v.strip()[:NAME_MAX]
    mp = {}
    for k, v in (c.get("map") or {}).items():
        if isinstance(k, str) and isinstance(v, str) and v and len(k) < 200 and len(v) < 200:
            mp[k] = v
    hidden = [k for k in (c.get("hidden") or []) if isinstance(k, str) and len(k) < 200][:500]
    data = {"_cosa": "nomi e abbinamenti ventola->temperatura scelti in http://localhost:8090/config",
            "names": names, "map": mp, "hidden": hidden}
    tmp = USER_CFG_FILE + ".tmp"
    with USER_CFG_LOCK:
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
        os.replace(tmp, USER_CFG_FILE)
        USER_CFG["names"], USER_CFG["map"], USER_CFG["hidden"] = names, mp, hidden
    log("config utente salvata: %d nomi, %d abbinamenti" % (len(names), len(mp)))


def apply_user_names(hw_list):
    """name -> nome utente (orig = nome originale) per hardware, temperature, ventole."""
    nm = USER_CFG["names"]
    hid = set(USER_CFG["hidden"])
    hid.update(k for k, v in USER_CFG["map"].items() if v == "none")   # ventola "non mostrare"
    if not nm and not hid:
        return
    for g in hw_list:
        for o in [g] + g["temps"] + g["fans"]:
            oid = o.get("id") or ""
            lab = nm.get(oid)
            if lab:
                o["orig"], o["name"] = o["name"], lab
            if oid in hid:
                o["hidden"] = True                  # v0.2.5: l'APK salta queste voci


def _find_temp_id(hw_list, tid):
    for gi, g in enumerate(hw_list):
        for t in g["temps"]:
            if t.get("id") == tid:
                return gi, g, t
    return None


def latest_config_page():
    files = sorted(glob.glob(os.path.join(BASE_DIR, "config_page_v*.html")))
    return files[-1] if files else None


def make_pairs(hw_list):
    """Abbina ogni ventola alla temperatura che la governa, per mostrarle affiancate:
      - ventola elencata in fan_map_*.json -> la temperatura indicata ("map")
      - ventola della GPU          -> temperatura della stessa GPU ("same_hw")
      - ventola con 'cpu'/'pump'/'aio' nel nome (scheda madre) -> temperatura CPU ("cpu")
      - altre ventole della scheda madre -> temperatura piu' alta del chip SuperIO
        ("mb", stima: Windows/LHM non sanno a quale sensore e' legata una ventola)
    'g' e' un indice di gruppo: stesso g = stesso colore sull'APK."""
    cpu = next((g for g in hw_list if g["kind"] == "cpu"), None)
    cpu_t = _main_temp(cpu) if cpu else None
    pairs = []
    for gi, g in enumerate(hw_list):
        for f in g["fans"]:
            n = f["name"].lower()
            # v0.2.4: abbinamento scelto nella pagina /config (per id), ha la precedenza
            uid = USER_CFG["map"].get(f.get("id") or "")
            if uid == "none":                      # l'utente ha scelto "non mostrare"
                continue
            if uid:
                hit = _find_temp_id(hw_list, uid)
                if hit:
                    ugi, ug, ut = hit
                    pairs.append({"fan": f["name"], "rpm": f["rpm"], "pct": f.get("pct"), "fan_hw": g["name"],
                                  "temp": ut["name"], "c": ut["c"], "temp_hw": ug["name"], "how": "user", "g": ugi,
                                  "fan_id": f.get("id"), "temp_id": ut.get("id")})
                    continue
            target = FAN_MAP.get((g["name"] + "|" + f["name"]).lower()) or FAN_MAP.get(n)
            mgi, mg, mt = _find_temp(hw_list, target) if target else (None, None, None)
            if mt is not None:
                pairs.append({"fan": f["name"], "rpm": f["rpm"], "pct": f.get("pct"), "fan_hw": g["name"],
                              "temp": mt["name"], "c": mt["c"], "temp_hw": mg["name"], "how": "map", "g": mgi})
                continue
            if g["kind"] == "gpu":
                t, how, src = _main_temp(g), "same_hw", g
            elif not f["rpm"]:
                continue                            # v0.2.3: header vuoto o ventola ferma
            elif cpu_t and any(k in n for k in ("cpu", "pump", "aio")):
                t, how, src = cpu_t, "cpu", cpu
            elif cpu_t:
                # v0.2.3: sulle schede ASUS le ventole del case seguono di default la CPU
                t, how, src = cpu_t, "cpu_default", cpu
            elif g["temps"]:
                t, how, src = _main_temp(g), "mb", g
            else:
                continue
            # 2026-10-22 v0.2.0 (sostituito): ventole scheda madre -> temperatura piu'
            # alta del SuperIO ("mb"), anche se ferme; tutte finivano su "Temperature #3".
            pairs.append({"fan": f["name"], "rpm": f["rpm"], "pct": f.get("pct"), "fan_hw": g["name"],
                          "temp": t["name"] if t else None, "c": t["c"] if t else None,
                          "temp_hw": src["name"] if (t and src) else None, "how": how, "g": gi})
    return pairs


# =============================================================================
# adb reverse automatico
# =============================================================================
def find_adb(explicit=None):
    cands = [explicit, shutil.which("adb"),
             os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"),
             os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb.exe")]
    for c in cands:
        if c and os.path.isfile(c):
            return c
    return None


class AdbReverse(threading.Thread):
    """Ogni ADB_INTERVAL s: per ogni dispositivo in stato 'device' senza la regola
    tcp:PORT la (ri)crea. Cosi' dopo un riavvio del tablet l'APK torna da sola
    sulla via USB, senza rilanciare lo script .cmd."""

    def __init__(self, adb, port):
        super().__init__(daemon=True)
        self.adb, self.port = adb, port
        self.state = {}                           # seriale -> "ok" / "errore"

    def run_adb(self, *args):
        try:
            r = subprocess.run([self.adb] + list(args), capture_output=True, text=True,
                               timeout=10, creationflags=CREATE_NO_WINDOW)
            return r.returncode, r.stdout
        except Exception as e:
            return -1, str(e)

    def check(self):
        rc, out = self.run_adb("devices")
        if rc != 0:
            self.state = {}
            return
        rule = "tcp:%d" % self.port
        seen = {}
        for line in out.splitlines()[1:]:
            parts = line.split()
            if len(parts) < 2 or parts[1] != "device":
                continue
            s = parts[0]
            rc, lst = self.run_adb("-s", s, "reverse", "--list")
            if rc == 0 and rule in lst:
                seen[s] = "ok"
                continue
            rc, msg = self.run_adb("-s", s, "reverse", rule, rule)
            seen[s] = "ok" if rc == 0 else "errore"
            log("adb reverse %s su %s: %s" % (rule, s, seen[s] if rc == 0 else msg.strip()))
        self.state = seen

    def run(self):
        while True:
            try:
                self.check()
            except Exception as e:
                log("adb error:", e)
            time.sleep(ADB_INTERVAL)


# =============================================================================
# Campionatore in background
# =============================================================================
# Processi di sistema senza ".exe" nel nome (come li riporta psutil)
SYS_PROCS = ("System", "Registry", "Memory Compression", "Secure System", "vmmem", "vmmemWSL")


class Sampler(threading.Thread):
    """Campiona periodicamente e tiene l'ultimo snapshot pronto per l'HTTP."""

    def __init__(self, gpu_pref=None):
        super().__init__(daemon=True)
        self.lock = threading.Lock()
        self.snapshot = {}
        self._last_net = psutil.net_io_counters()
        self._last_disk = psutil.disk_io_counters()
        self._last_t = time.time()
        self._procs = []
        self._procs_t = 0
        self._lhm_raw = None
        self._lhm_src = None
        self._lhm_t = 0
        self.sensors = None                       # SensorReader, impostato da main()
        self._gpu_sens = {}
        self._sys = {"procs": None, "threads": None}
        self.gpu_pref = (gpu_pref or "").lower()
        self.pdh = Pdh()
        # v0.2.6: query separata per i processi, raccolta ogni PROC_INTERVAL s
        # (cosi' la % CPU e' la media sugli ultimi 3 s, come con psutil)
        self.pdh_proc = Pdh({"cpu": r"\Process(*)\% Processor Time",
                             "ws": r"\Process(*)\Working Set",
                             "thr": r"\System\Threads",
                             "prc": r"\System\Processes"})
        self.gpus = list_gpus()
        self._gpus_t = time.time()
        self.adb = None                           # AdbReverse, impostato da main()
        log("GPU:", ", ".join("%s (%s, %d MB)" % (g["name"], g["luid"], g["vram_mb"]) for g in self.gpus) or "nessuna")
        psutil.cpu_percent(percpu=True)               # prima lettura "a vuoto"

    def top_procs(self):
        """v0.2.6: dai contatori PDH. Nomi con ".exe" come psutil (tranne i processi
        di sistema); % CPU divisa per il numero di CPU logiche come nella v0.1.0;
        mb = Working Set (= RSS di psutil)."""
        P = self.pdh_proc
        if not P.ok:
            return self.top_procs_psutil()
        P.collect()
        cpu, ws = P.array_list("cpu"), P.array_list("ws")
        if not cpu or len(cpu) != len(ws):
            return self.top_procs_psutil()
        ncpu = psutil.cpu_count() or 1
        rows = []
        for (n, c), (n2, m) in zip(cpu, ws):
            if n != n2 or n in ("_Total", "Idle") or c is None:
                continue
            name = n if n in SYS_PROCS else n + ".exe"
            rows.append((c / ncpu, m or 0, name))
        thr, prc = P.single("thr"), P.single("prc")
        self._sys = {"procs": int(prc) if prc is not None else len(rows),
                     "threads": int(thr) if thr is not None else None}
        rows.sort(key=lambda r: r[0], reverse=True)
        return [{"n": n, "cpu": round(c, 1), "mb": round(m / 1048576)} for c, m, n in rows[:TOP_N]]

    def top_procs_psutil(self):
        # 2026-10-22 v0.2.5 (sostituito, resta come ripiego se PDH non e' disponibile):
        # psutil.process_iter con num_threads costava ~1,3 s di CPU ogni 3 s.
        # cpu_percent per processo richiede due letture: usiamo il valore
        # accumulato dall'ultima chiamata (ogni PROC_INTERVAL secondi).
        # v0.2.0: nello stesso giro si contano processi e thread (num_threads).
        ncpu = psutil.cpu_count() or 1
        rows = []
        nthr = 0
        for p in psutil.process_iter(["name", "memory_info", "num_threads"]):
            try:
                c = p.cpu_percent(None) / ncpu
                m = p.info["memory_info"].rss if p.info["memory_info"] else 0
                nthr += p.info["num_threads"] or 0
                rows.append((c, m, p.info["name"] or "?"))
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        self._sys = {"procs": len(rows), "threads": nthr}
        rows = [r for r in rows if r[2].lower() not in ("system idle process", "idle")]
        rows.sort(key=lambda r: r[0], reverse=True)
        return [{"n": n, "cpu": round(c, 1), "mb": round(m / 1048576)} for c, m, n in rows[:TOP_N]]

    def cpu_extra(self):
        """Frequenza reale per core (MHz) = frequenza base x % Processor Performance."""
        perf = self.pdh.array("perf")
        base = self.pdh.array("basefreq")
        cores = []
        for name, v in perf.items():
            if "_Total" in name:
                continue
            try:
                grp, idx = name.split(",")
                key = (int(grp), int(idx))
            except ValueError:
                continue
            b = base.get(name)
            if b:
                cores.append((key, b * v / 100.0))
        cores.sort()
        mhz = [round(x) for _, x in cores]
        tot = perf.get("_Total")
        btot = base.get("_Total") or (base and max(base.values()))
        q = self.pdh.single("queue")
        return {"mhz_cores": mhz,
                "mhz_now": round(btot * tot / 100.0) if (tot and btot) else None,
                "mhz_max": max(mhz) if mhz else None,
                "queue": None if q is None else int(q)}

    def gpu_list(self, now):
        """Una voce per GPU: carico (PDH, come Gestione attivita': massimo tra i motori,
        ognuno somma dei processi), VRAM, temperatura/ventola/potenza (D3DKMT)."""
        if now - self._gpus_t > 60:                # nuove schede / driver riavviato
            self.gpus, self._gpus_t = list_gpus(), now
        eng = {}
        for inst, v in self.pdh.array("gpu_eng").items():
            lu = luid_of(inst)
            if lu is None:
                continue
            k = (lu, inst.split("_eng_")[-1].split("_engtype_")[0] if "_eng_" in inst else "?")
            eng[k] = eng.get(k, 0.0) + v
        load = {}
        for (lu, _), v in eng.items():
            load[lu] = max(load.get(lu, 0.0), v)
        ded = {}
        for inst, v in self.pdh.array("gpu_ded").items():
            lu = luid_of(inst)
            if lu:
                ded[lu] = ded.get(lu, 0.0) + v
        out = []
        for g in self.gpus:
            if now - self._lhm_t < 0.01 or g["luid"] not in self._gpu_sens:
                self._gpu_sens[g["luid"]] = gpu_perfdata(g["h"])
            t, fan, pw = self._gpu_sens[g["luid"]]
            used = ded.get(g["luid"])
            used_mb = round(used / 1048576) if used is not None else None
            out.append({"name": g["name"], "luid": g["luid"],
                        "load": r1(min(load.get(g["luid"], 0.0), 100.0)),
                        "mem_mb": used_mb, "mem_tot_mb": g["vram_mb"],
                        "mem_pct": r1(100.0 * used_mb / g["vram_mb"]) if (used_mb is not None and g["vram_mb"]) else None,
                        "temp": r1(t), "fan_rpm": fan, "power_pct": r1(pw), "src": "win"})
        return out

    def primary_gpu(self, gpus):
        """GPU mostrata nei campi lhm.gpu_* : --gpu <nome> se dato, altrimenti la prima
        elencata da DXGI (= la scheda che pilota il monitor principale)."""
        if not gpus:
            return None
        if self.gpu_pref:
            for g in gpus:
                if self.gpu_pref in g["name"].lower():
                    return g
        return gpus[0]

    def sample(self):
        now = time.time()
        dt = max(now - self._last_t, 0.001)
        per = psutil.cpu_percent(percpu=True)
        vm = psutil.virtual_memory()
        sw = psutil.swap_memory()
        freq = psutil.cpu_freq()
        self.pdh.collect()

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
        ld_r, ld_w, ld_idle = self.pdh.array("ld_r"), self.pdh.array("ld_w"), self.pdh.array("ld_idle")
        for part in psutil.disk_partitions(all=False):
            if "cdrom" in part.opts or not part.fstype:
                continue
            try:
                u = psutil.disk_usage(part.mountpoint)
            except (PermissionError, OSError):
                continue
            dk = {"m": part.mountpoint, "pct": u.percent,
                  "used_gb": round(u.used / 1e9, 1), "tot_gb": round(u.total / 1e9, 1)}
            inst = part.mountpoint.rstrip("\\")          # "C:\\" -> "C:" (istanza PDH)
            if inst in ld_r and inst in ld_w:             # volumi senza contatori (es. G: Drive): r/w omessi
                dk["r"], dk["w"] = round(ld_r[inst]), round(ld_w[inst])
                if inst in ld_idle:
                    dk["busy"] = r1(max(0.0, min(100.0, 100.0 - ld_idle[inst])))
            disks.append(dk)

        if now - self._procs_t >= PROC_INTERVAL:
            self._procs = self.top_procs()
            self._procs_t = now
        if now - self._lhm_t >= LHM_INTERVAL:
            # 1) Tab2Sensors (libreria LHM nel progetto)  2) app LHM con web server
            self._lhm_raw = self.sensors.get() if self.sensors else None
            self._lhm_src = "lib" if self._lhm_raw else None
            if self._lhm_raw is None:
                self._lhm_raw = read_lhm_raw()
                self._lhm_src = "lhm" if self._lhm_raw else None
            self._lhm_t = now

        gpus = self.gpu_list(now)
        lhm, sensors = None, None
        if self._lhm_raw:
            try:
                lhm, sensors = parse_lhm(self._lhm_raw)
            except Exception as e:
                log("LHM parse error:", e)
        pg = self.primary_gpu(gpus)
        if lhm is None and pg:
            # Senza LHM: campi v0.1.0 riempiti con i dati di Windows della GPU principale
            temps = [{"hw": g["name"], "name": "GPU", "c": g["temp"]} for g in gpus if g["temp"] is not None]
            lhm = {"gpu_name": pg["name"], "gpu_load": pg["load"], "gpu_temp": pg["temp"],
                   "gpu_mem_load": pg["mem_pct"], "cpu_temp": None, "temps": temps[:8], "src": "win"}
        elif lhm is not None and pg and lhm["gpu_load"] is None:
            lhm["gpu_name"], lhm["gpu_load"] = pg["name"], pg["load"]
        if sensors is None:
            # Senza LHM: solo le GPU (temperatura e ventola da D3DKMT)
            hw = [{"id": g["luid"], "name": g["name"], "kind": "gpu",
                   "temps": [{"id": "win/" + g["luid"] + "/temp", "name": "GPU", "c": g["temp"]}] if g["temp"] is not None else [],
                   "fans": [{"id": "win/" + g["luid"] + "/fan", "name": "GPU Fan", "rpm": g["fan_rpm"], "pct": None, "on": True}] if g["fan_rpm"] else []}
                  for g in gpus]
            hw = [h for h in hw if h["temps"] or h["fans"]]
            sensors = {"src": "win", "hw": hw}
        else:
            # GPU che LHM non vede (es. MI50 / "Radeon Pro VII" con driver Pro): da Windows
            known = set(h["name"].lower() for h in sensors["hw"])
            for g in gpus:
                if g["name"].lower() not in known and (g["temp"] is not None or g["fan_rpm"]):
                    sensors["hw"].append({"id": g["luid"], "name": g["name"], "kind": "gpu", "src": "win",
                                          "temps": [{"id": "win/" + g["luid"] + "/temp", "name": "GPU", "c": g["temp"]}] if g["temp"] is not None else [],
                                          "fans": [{"id": "win/" + g["luid"] + "/fan", "name": "GPU Fan", "rpm": g["fan_rpm"], "pct": None, "on": True}] if g["fan_rpm"] else []})
            sensors["src"] = self._lhm_src
            if lhm is not None:
                lhm["src"] = self._lhm_src
                if pg and not lhm["gpu_mem_load"] and pg["mem_pct"] is not None:
                    lhm["gpu_mem_load"] = pg["mem_pct"]     # LHM a volte da' 0: uso Windows

        apply_user_names(sensors["hw"])              # v0.2.4: nomi scelti dall'utente
        sensors["pairs"] = make_pairs(sensors["hw"])

        snap = {
            "v": VERSION,
            "ts": int(now),
            "host": platform.node(),
            "cpu": dict({"pct": round(sum(per) / len(per), 1) if per else 0,
                         "cores": [round(x) for x in per],
                         "mhz": round(freq.current) if freq else None},
                        **self.cpu_extra()),
            "ram": {"pct": vm.percent, "used_gb": round((vm.total - vm.available) / 1e9, 1),
                    "tot_gb": round(vm.total / 1e9, 1)},
            "swap": {"pct": sw.percent},
            "disk_io": {"r": round(d_r), "w": round(d_w)},
            "disks": disks,
            "net": {"rx": round(net_rx), "tx": round(net_tx)},
            "uptime_s": int(now - psutil.boot_time()),
            "procs": self._procs,
            "lhm": lhm,
            # --- campi aggiunti in v0.2.0 ---
            "sys": self._sys,
            "gpus": gpus,
            "sensors": sensors,
            "claude": CLAUDE.result if CLAUDE else None,      # v0.2.1
            "srv": {"lhm": self._lhm_src,
                    "adb": dict(self.adb.state) if self.adb else None,
                    "http": "1.1"},
        }
        if PLAN is not None and PLAN.result:          # v0.2.7: omesso se non disponibile
            snap["claude_plan"] = PLAN.result
        if PLAN is not None:
            snap["srv"]["plan"] = PLAN.state
        with self.lock:
            self.snapshot = snap

    def run(self):
        while True:
            t0 = time.time()
            try:
                self.sample()
            except Exception as e:                    # mai far morire il thread
                log("sample error:", e)
            time.sleep(max(0.05, SAMPLE_INTERVAL - (time.time() - t0)))

    def get(self):
        with self.lock:
            return self.snapshot


SAMPLER = None


# =============================================================================
# v0.2.1 - Utilizzo Claude Code dai log locali
# =============================================================================
CLAUDE_DIR = os.path.join(os.path.expanduser("~"), ".claude", "projects")
CLAUDE_INTERVAL = 45.0                         # s tra due scansioni (mai nel ciclo da 1 s)
CLAUDE_KEEP_S = 8 * 86400                      # si tengono in memoria solo 8 giorni
BLOCK_S = 5 * 3600                             # finestra di utilizzo di 5 ore


def _iso_epoch(ts):
    """'2026-10-21T22:05:39.292Z' (UTC) -> epoch float, None se non valido."""
    try:
        from datetime import datetime, timezone
        return datetime.strptime(ts[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp()
    except Exception:
        return None


class ClaudeUsage(threading.Thread):
    """Scansione incrementale dei .jsonl di Claude Code: per ogni file ricorda
    l'offset gia' letto e legge solo le righe complete aggiunte dopo. Per ogni riga
    "type":"assistant" tiene (ora, token, output, cache_read, modello), deduplicando
    per message.id + requestId (lo stesso messaggio compare piu' volte nei log).
    Del contenuto si usano SOLO i campi usage/model/timestamp/id."""

    def __init__(self, root=CLAUDE_DIR):
        super().__init__(daemon=True)
        self.root = root
        self.offsets = {}                          # file -> byte gia' letti
        self.recs = {}                             # chiave -> (ts, tok, out, cache_r, model)
        self.result = None

    def scan_file(self, f):
        try:
            size = os.path.getsize(f)
        except OSError:
            return
        off = self.offsets.get(f, 0)
        if size < off:                             # file riscritto: si rilegge da capo
            off = 0
        if size == off:
            return
        with open(f, "rb") as fh:
            fh.seek(off)
            data = fh.read(size - off)
        end = data.rfind(b"\n")                   # solo righe complete
        if end < 0:
            return
        self.offsets[f] = off + end + 1
        cutoff = time.time() - CLAUDE_KEEP_S
        for line in data[:end].split(b"\n"):
            if b'"assistant"' not in line or b'"usage"' not in line:
                continue
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if d.get("type") != "assistant":
                continue
            m = d.get("message") or {}
            u = m.get("usage") or {}
            ts = _iso_epoch(d.get("timestamp") or "")
            if ts is None or ts < cutoff:
                continue
            key = (m.get("id") or "") + "|" + (d.get("requestId") or "")
            if key == "|":
                key = "%s|%s" % (f, ts)
            tok = (u.get("input_tokens") or 0) + (u.get("output_tokens") or 0) + \
                  (u.get("cache_creation_input_tokens") or 0)
            self.recs[key] = (ts, tok, u.get("output_tokens") or 0,
                              u.get("cache_read_input_tokens") or 0, m.get("model") or "?")

    def compute(self):
        now = time.time()
        lt = time.localtime(now)
        midnight = time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, 0, 0, 0, 0, 0, -1))
        cutoff = now - CLAUDE_KEEP_S
        self.recs = {k: v for k, v in self.recs.items() if v[0] >= cutoff}
        rows = sorted(self.recs.values())
        res = {"today_tok": 0, "today_out": 0, "today_cache_r": 0, "today_msgs": 0,
               "block_tok": 0, "week_tok": 0, "models": [], "scan_ts": int(now)}
        models = {}
        for ts, tok, out, cr, mdl in rows:
            if ts >= now - 7 * 86400:
                res["week_tok"] += tok
            if ts >= midnight:
                res["today_tok"] += tok
                res["today_out"] += out
                res["today_cache_r"] += cr
                res["today_msgs"] += 1
                if not mdl.startswith("<"):        # es. "<synthetic>": non e' un modello
                    models[mdl] = models.get(mdl, 0) + tok
        # Finestra di 5 h (logica tipo ccusage): un blocco inizia all'ora intera del
        # primo messaggio e dura 5 h; si apre un blocco nuovo se un messaggio cade
        # oltre la fine del blocco o dopo una pausa >= 5 h dall'ultimo messaggio.
        bstart, bend, btok, last = None, None, 0, None
        for ts, tok, out, cr, mdl in rows:
            if bstart is None or ts >= bend or (last is not None and ts - last >= BLOCK_S):
                bstart = ts - (ts % 3600)
                bend, btok = bstart + BLOCK_S, 0
            btok += tok
            last = ts
        if bstart is not None and now < bend:
            res["block_tok"], res["block_start"], res["block_end"] = btok, int(bstart), int(bend)
        res["models"] = [{"m": m, "tok": t} for m, t in
                         sorted(models.items(), key=lambda kv: kv[1], reverse=True)[:3]]
        return res

    def run(self):
        while True:
            t0 = time.time()
            try:
                if os.path.isdir(self.root):
                    for f in glob.glob(os.path.join(self.root, "**", "*.jsonl"), recursive=True):
                        self.scan_file(f)
                    self.result = self.compute()
                else:
                    self.result = None
            except Exception as e:
                log("claude usage error:", e)
            if t0 and not getattr(self, "_logged", False) and self.result is not None:
                log("Claude usage: prima scansione in %.1f s, %d risposte" % (time.time() - t0, len(self.recs)))
                self._logged = True
            time.sleep(CLAUDE_INTERVAL)


CLAUDE = None


# =============================================================================
# v0.2.7 - Limiti del piano Claude (account collegato al PC)
# =============================================================================
CRED_FILE = os.path.join(os.path.expanduser("~"), ".claude", ".credentials.json")
PLAN_URL = "https://api.anthropic.com/api/oauth/usage"      # non documentato: puo' cambiare
PLAN_INTERVAL = 300.0                                        # 5 minuti, cache in mezzo


def _reset_epoch(v):
    """'2026-10-22T15:00:00.123+00:00' / '...Z' / epoch -> epoch int (None se non valido)."""
    if isinstance(v, (int, float)):
        return int(v)
    if not isinstance(v, str):
        return None
    try:
        from datetime import datetime, timezone
        d = datetime.fromisoformat(v.replace("Z", "+00:00"))   # Python 3.11+: frazioni ok
        if d.tzinfo is None:
            d = d.replace(tzinfo=timezone.utc)
        return int(d.timestamp())
    except Exception:
        return None


class ClaudePlan(threading.Thread):
    """Ogni PLAN_INTERVAL s: legge il token dal file credenziali (solo in memoria,
    variabile locale), chiama l'endpoint e tiene il risultato. Il token non viene
    mai scritto in log, JSON, config o repository. Se manca il token: nessuna
    chiamata. Se la chiamata fallisce (401, formato cambiato, rete): campo omesso
    e stop finche' il file credenziali non cambia (data di modifica)."""

    def __init__(self):
        super().__init__(daemon=True)
        self.result = None
        self.blocked_mtime = None                  # mtime del file al momento dell'errore
        self.state = "init"

    def _token(self):
        with open(CRED_FILE, encoding="utf-8") as fh:
            c = (json.load(fh) or {}).get("claudeAiOauth") or {}
        return c.get("accessToken"), c.get("subscriptionType")

    def poll(self):
        try:
            mtime = os.path.getmtime(CRED_FILE)
        except OSError:
            self.result, self.state = None, "no_file"
            return
        if self.blocked_mtime is not None and mtime == self.blocked_mtime:
            return                                   # errore precedente: niente tentativi
        self.blocked_mtime = None
        try:
            tok, sub = self._token()
        except Exception:
            self.result, self.state = None, "file_illeggibile"
            return
        if not tok:
            self.result, self.state = None, "no_token"
            return
        req = urllib.request.Request(PLAN_URL, headers={
            "Authorization": "Bearer " + tok, "anthropic-beta": "oauth-2025-04-20",
            "Accept": "application/json", "User-Agent": "tab2monitor/" + VERSION})
        tok = None
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                d = json.loads(r.read().decode("utf-8"))
            res = {"ts": int(time.time())}
            if sub:
                res["plan"] = str(sub).capitalize()
            for src, dst in (("five_hour", "session"), ("seven_day", "weekly"),
                             ("seven_day_opus", "weekly_opus")):
                w = d.get(src)
                if isinstance(w, dict) and w.get("utilization") is not None:
                    res[dst] = {"pct": round(float(w["utilization"])), "reset": _reset_epoch(w.get("resets_at"))}
            if "session" not in res and "weekly" not in res:
                raise ValueError("formato inatteso")
            self.result, self.state = res, "ok"
        except Exception as e:
            # niente dettagli della richiesta nel log (contiene l'intestazione col token)
            code = getattr(e, "code", None)
            self.result, self.state = None, "errore %s" % (code or type(e).__name__)
            self.blocked_mtime = mtime
            log("claude_plan: %s, sospeso finche' le credenziali non cambiano" % self.state)

    def run(self):
        while True:
            try:
                self.poll()
            except Exception as e:
                log("claude_plan errore:", type(e).__name__)
            time.sleep(PLAN_INTERVAL)


PLAN = None


# --- HTTP --------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    # v0.2.0: HTTP/1.1 = connessione persistente (keep-alive). Funziona perche'
    # ogni risposta ha Content-Length; il client riusa lo stesso socket.
    # 2026-10-21 v0.1.0: protocol_version di default "HTTP/1.0" (una connessione TCP
    # per richiesta).
    protocol_version = "HTTP/1.1"
    timeout = HTTP_IDLE_TIMEOUT                   # chiude i socket keep-alive inattivi

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
        elif path == "/config":                    # v0.2.4: pagina nomi/abbinamenti
            f = latest_config_page()
            if not f:
                self._send(404, b"pagina config non trovata", "text/plain")
                return
            with open(f, "rb") as fh:
                self._send(200, fh.read(), "text/html; charset=utf-8")
        elif path == "/api/config":
            body = json.dumps(USER_CFG, ensure_ascii=False).encode("utf-8")
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

    def do_POST(self):
        # v0.2.4: salva la configurazione; accettata solo da 127.0.0.1 (il PC stesso,
        # o il tablet via adb reverse), non dalla rete Wi-Fi
        if self.path.split("?")[0] != "/api/config":
            self._send(404, b"not found", "text/plain")
            return
        if self.client_address[0] != "127.0.0.1":
            self._send(403, b"solo da localhost", "text/plain")
            return
        try:
            n = int(self.headers.get("Content-Length") or 0)
            if n <= 0 or n > 65536:
                raise ValueError("dimensione")
            save_user_cfg(json.loads(self.rfile.read(n).decode("utf-8")))
            self._send(200, b'{"ok":true}', "application/json")
        except Exception as e:
            self._send(400, ('{"ok":false,"err":%s}' % json.dumps(str(e))).encode("utf-8"), "application/json")

    def log_message(self, fmt, *args):            # silenzia il log per richiesta
        pass


def main():
    global SAMPLER, CLAUDE, PLAN
    ap = argparse.ArgumentParser(description="Tab2Monitor server " + VERSION)
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    ap.add_argument("--bind", default="0.0.0.0")
    ap.add_argument("--adb", default=None, help="percorso di adb.exe (default: PATH o Android SDK)")
    ap.add_argument("--no-adb", action="store_true", help="non gestire adb reverse")
    ap.add_argument("--no-sensors", action="store_true", help="non avviare Tab2Sensors.exe")
    ap.add_argument("--no-claude-plan", action="store_true", help="non leggere i limiti del piano Claude")
    ap.add_argument("--no-claude", action="store_true", help="non leggere i log di Claude Code")
    ap.add_argument("--gpu", default=None, help="parte del nome della GPU principale (es. 9070)")
    a = ap.parse_args()
    FAN_MAP.update(load_fan_map())
    load_user_cfg()
    SAMPLER = Sampler(a.gpu)
    exe = None if a.no_sensors else find_tab2sensors()
    if exe:
        SAMPLER.sensors = SensorReader(exe)
        SAMPLER.sensors.start()
    else:
        log("Tab2Sensors.exe non trovato o disattivato: provo l'app LHM su", LHM_URL)
    if not a.no_adb:
        adb = find_adb(a.adb)
        if adb:
            SAMPLER.adb = AdbReverse(adb, a.port)
            SAMPLER.adb.start()
            log("adb reverse automatico con", adb)
        else:
            log("adb non trovato: adb reverse automatico disattivato")
    SAMPLER.start()
    if not a.no_claude:
        CLAUDE = ClaudeUsage()
        CLAUDE.start()
    if not a.no_claude_plan:
        PLAN = ClaudePlan()
        PLAN.start()
    log("Tab2Monitor %s su http://%s:%d  (dashboard: %s)" % (VERSION, a.bind, a.port, latest_dashboard()))
    # v0.2.3: niente SO_REUSEADDR: su Windows permette a un secondo server di mettersi
    # in ascolto sulla stessa porta (successo il 2026-10-22 tra servizio e avvio a mano);
    # cosi' la seconda istanza fallisce e il supervisore riprova ogni 10 s.
    # 2026-10-22 v0.2.2: ThreadingHTTPServer((a.bind, a.port), Handler).serve_forever()
    # v0.2.3: avviato dal supervisore (stdin = pipe) -> se il supervisore termina
    # (es. "schtasks /End /TN Tab2Monitor") la pipe si chiude e il server esce,
    # invece di restare orfano sulla porta (successo il 2026-10-22 con la v0.2.2).
    if sys.stdin is not None and not sys.stdin.isatty():
        def _watch_parent():
            try:
                sys.stdin.read()
            except Exception:
                pass
            log("stdin chiuso (supervisore terminato): esco")
            os._exit(0)
        threading.Thread(target=_watch_parent, daemon=True).start()
    ThreadingHTTPServer.allow_reuse_address = False
    ThreadingHTTPServer((a.bind, a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
