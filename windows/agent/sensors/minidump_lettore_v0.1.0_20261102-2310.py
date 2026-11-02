"""Lettore minimale di minidump di Windows (solo stdlib). Versione: v0.1.0 - 2026-11-02 23:10. Uso: python -I minidump_lettore_v0.1.0_20261102-2310.py <file.dmp> (i dump di Tab2Sensors stanno in %LOCALAPPDATA%\CrashDumps). Mostra eccezione, moduli e i moduli sullo stack del thread in errore."""
import struct, sys, os, collections

f = open(sys.argv[1], "rb")
data = f.read()
sig, ver, nstreams, dirrva = struct.unpack_from("<4sIII", data, 0)
assert sig == b"MDMP", sig
streams = {}
for i in range(nstreams):
    st, size, rva = struct.unpack_from("<III", data, dirrva + 12 * i)
    streams.setdefault(st, []).append((size, rva))
print("stream tipi:", sorted(streams))


def wstr(rva):
    n, = struct.unpack_from("<I", data, rva)
    return data[rva + 4: rva + 4 + n].decode("utf-16le")


# --- moduli (tipo 4) ---
mods = []
if 4 in streams:
    size, rva = streams[4][0]
    n, = struct.unpack_from("<I", data, rva)
    for i in range(n):
        base, msize, chk, ts, namerva = struct.unpack_from("<QIIII", data, rva + 4 + 108 * i)
        mods.append((base, msize, os.path.basename(wstr(namerva)), ts))
mods.sort()


def which(addr):
    for base, msize, name, ts in mods:
        if base <= addr < base + msize:
            return "%s+0x%x" % (name, addr - base)
    return None


# --- eccezione (tipo 6) ---
tid = None
if 6 in streams:
    size, rva = streams[6][0]
    tid, _ = struct.unpack_from("<II", data, rva)
    code, flags, rec, addr, nparams = struct.unpack_from("<IIQQI", data, rva + 8)
    params = struct.unpack_from("<15Q", data, rva + 8 + 32)
    print("thread in errore:", tid)
    print("eccezione 0x%08x flags %d indirizzo 0x%x -> %s" % (code, flags, addr, which(addr)))
    if code == 0xC0000005:
        print("  accesso %s all'indirizzo 0x%x" % ({0: "in LETTURA", 1: "in SCRITTURA", 8: "DEP"}.get(params[0], params[0]), params[1]))
    ctxsize, ctxrva = struct.unpack_from("<II", data, rva + 8 + 32 + 15 * 8 + 4 - 4 + 0) if False else (0, 0)

# --- elenco moduli ---
print("\nmoduli (%d):" % len(mods))
for base, msize, name, ts in mods:
    if any(k in name.lower() for k in ("libre", "pawn", "ram", "disk", "atiad", "amd", "adl", "d3d", "nvapi", "hid", "kernel", "ntdll", "clr", "mscor", "tab2", "system.", "blacksharp", "nvml", "dxgi")):
        print("  0x%016x %9d %s" % (base, msize, name))

# --- thread (tipo 3) ---
threads = []
if 3 in streams:
    size, rva = streams[3][0]
    n, = struct.unpack_from("<I", data, rva)
    for i in range(n):
        t, susp, prio_c, prio, teb, stack_start, stack_size, stack_rva, ctx_size, ctx_rva = struct.unpack_from("<IIIIQQIIII", data, rva + 4 + 48 * i)
        threads.append((t, teb, stack_start, stack_size, stack_rva, ctx_size, ctx_rva))
print("\nthread: %d" % len(threads))


def regs(ctx_rva):
    # CONTEXT x64: Rsp a 0x98, Rbp 0xA0, Rip 0xF8
    rsp, = struct.unpack_from("<Q", data, ctx_rva + 0x98)
    rbp, = struct.unpack_from("<Q", data, ctx_rva + 0xA0)
    rip, = struct.unpack_from("<Q", data, ctx_rva + 0xF8)
    return rsp, rbp, rip


def stack_scan(t):
    tid_, teb, ss, sz, srva, csz, crva = t
    rsp, rbp, rip = regs(crva)
    out = ["RIP 0x%x -> %s   RSP 0x%x" % (rip, which(rip), rsp)]
    seen = 0
    # la memoria dello stack parte da stack_start = (rsp tipicamente)
    off = srva
    for i in range(0, min(sz, 0x4000) // 8):
        v, = struct.unpack_from("<Q", data, off + i * 8)
        w = which(v)
        if w:
            out.append("  [rsp+0x%x] %s" % (ss + i * 8 - rsp, w))
            seen += 1
            if seen >= 40:
                break
    return out


for t in threads:
    if t[0] == tid:
        print("\n== thread in errore %d ==" % tid)
        print("\n".join(stack_scan(t)))
print("\nnomi thread con stack che toccano moduli di interesse (primi):")
for t in threads[:40]:
    if t[0] == tid:
        continue
    try:
        sc = stack_scan(t)
    except Exception:
        continue
    mods_hit = collections.Counter(x.split("+")[0].split()[-1] for x in sc[1:])
    print("  thread %-6d rip=%s  moduli sullo stack: %s" % (t[0], sc[0].split("->")[1].split()[0] if "->" in sc[0] else "?", dict(mods_hit.most_common(4))))
