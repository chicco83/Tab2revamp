// =============================================================================
// Tab2Monitor - MonitorView (disegno su Canvas)
// Versione: v0.1.0 - 2026-10-21 23:50
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Una sola View che disegna tutto: niente layout/TextView/RecyclerView, cosi'
// la gerarchia e' minima e un ridisegno al secondo costa pochissimo sull'OMAP4430.
// Stessa impaginazione della dashboard web (3 colonne, palette scura):
//   col 1: CPU, core, storico 60 s (CPU blu / RAM verde)
//   col 2: RAM, GPU (LibreHardwareMonitor), temperature
//   col 3: rete, disco I/O + dischi, top processi
// Tutte le misure sono in "unita'" u = altezza/800, quindi il layout scala con
// lo schermo (1280x800 sul Tab 2). Paint e Path preallocati: in onDraw non si
// crea nessun oggetto tranne le stringhe dei valori.
// =============================================================================
package it.tab2revamp.monitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;

public class MonitorView extends View {
    // Palette (uguale alla dashboard web)
    static final int BG = 0xff0d1117, CARD = 0xff161b22, BORDER = 0xff30363d, TRACK = 0xff21262d;
    static final int FG = 0xffe6edf3, DIM = 0xff8b949e, BLUE = 0xff2f81f7, GREEN = 0xff3fb950;
    static final int YELLOW = 0xffd29922, RED = 0xfff85149;
    static final int HIST = 60;

    private final Paint fill = new Paint(), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Calendar cal = Calendar.getInstance();

    private MainActivity.Metrics m;          // ultimo campione valido
    private String src;                      // URL che ha risposto
    private String[] urls = new String[0];
    private long lastOk;                     // uptimeMillis dell'ultimo campione valido
    private final float[] hCpu = new float[HIST], hRam = new float[HIST];
    private int hLen;
    private float u = 1;                     // unita' di misura (altezza/800)

    public MonitorView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        setBackgroundColor(BG);
        setKeepScreenOn(true);
    }

    void setUrls(String[] u) { urls = u; }

    // Chiamato dal thread UI: m == null -> server non raggiungibile
    void update(MainActivity.Metrics nm, String from) {
        if (nm != null) {
            m = nm; src = from; lastOk = SystemClock.uptimeMillis();
            if (hLen == HIST) {
                System.arraycopy(hCpu, 1, hCpu, 0, HIST - 1);
                System.arraycopy(hRam, 1, hRam, 0, HIST - 1);
                hLen--;
            }
            hCpu[hLen] = nm.cpu; hRam[hLen] = nm.ram; hLen++;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        u = h / 800f;
        stroke.setStrokeWidth(Math.max(1, u));
        line.setStrokeWidth(2 * u);
    }

    // ---------------------------------------------------------------- disegno
    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight(), pad = 12 * u, gap = 10 * u;
        boolean fresh = m != null && SystemClock.uptimeMillis() - lastOk < 5000;

        // Intestazione: host, uptime | stato, sorgente, ora
        float hy = pad + 22 * u;
        big.setTextSize(20 * u); big.setColor(FG); big.setTextAlign(Paint.Align.LEFT);
        String host = m != null ? m.host : "Tab2Monitor";
        cv.drawText(host, pad, hy, big);
        text.setTextSize(17 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.LEFT);
        if (m != null) cv.drawText("uptime " + upt(m.uptime), pad + big.measureText(host) + 16 * u, hy, text);
        cal.setTimeInMillis(System.currentTimeMillis());
        String clk = two(cal.get(Calendar.HOUR_OF_DAY)) + ":" + two(cal.get(Calendar.MINUTE));
        String where = src == null ? "" : (src.equals(MainActivity.USB_URL) ? "USB" : "Wi-Fi");
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(where + "   " + clk, W - pad, hy, text);
        float dotX = W - pad - text.measureText(where + "   " + clk) - 12 * u;
        fill.setColor(fresh ? GREEN : RED);
        cv.drawCircle(dotX, hy - 6 * u, 7 * u, fill);

        if (m == null) { drawWaiting(cv, W, H); return; }

        float top = pad + 40 * u, bottom = H - pad;
        float cw = (W - 2 * pad - 2 * gap) / 3f;
        float x1 = pad, x2 = pad + cw + gap, x3 = pad + 2 * (cw + gap);

        // ---- colonna 1
        float y = top;
        y = bigCard(cv, x1, y, cw, "CPU", pct(m.cpu), Float.isNaN(m.mhz) ? "" : (int) m.mhz + " MHz", m.cpu) + gap;
        y = coresCard(cv, x1, y, cw, 170 * u) + gap;
        histCard(cv, x1, y, cw, bottom - y);

        // ---- colonna 2
        y = top;
        y = bigCard(cv, x2, y, cw, "RAM", pct(m.ram),
                num1(m.ramUsed) + " / " + num1(m.ramTot) + " GB  -  swap " + pct(m.swap), m.ram) + gap;
        boolean gpu = !Float.isNaN(m.gpuLoad);
        String gsub = !gpu ? "LibreHardwareMonitor non attivo"
                : (Float.isNaN(m.gpuTemp) ? "" : num1(m.gpuTemp) + " °C")
                + (Float.isNaN(m.gpuMem) ? "" : "  -  VRAM " + pct(m.gpuMem));
        y = bigCard(cv, x2, y, cw, m.gpuName != null ? "GPU - " + m.gpuName : "GPU",
                gpu ? pct(m.gpuLoad) : "--", gsub, gpu ? m.gpuLoad : 0) + gap;
        float th = bottom - y;
        card(cv, x2, y, cw, th, "Temperature");
        int nt = Math.min(m.tempName.length, (int) ((th - 44 * u) / (26 * u)));
        if (nt == 0) row(cv, x2, y + 60 * u, cw, "--", "");
        for (int i = 0; i < nt; i++)
            row(cv, x2, y + (60 + 26 * i) * u, cw, m.tempName[i], num1(m.tempC[i]) + " °C");

        // ---- colonna 3
        y = top;
        card(cv, x3, y, cw, 104 * u, "Rete");
        row(cv, x3, y + 60 * u, cw, "↓ download", rate(m.rx));
        row(cv, x3, y + 86 * u, cw, "↑ upload", rate(m.tx));
        y += 104 * u + gap;
        int nd = Math.min(m.diskM.length, 6);
        float dh = (104 + 26 * nd) * u;
        card(cv, x3, y, cw, dh, "Disco I/O");
        row(cv, x3, y + 60 * u, cw, "lettura", rate(m.dr));
        row(cv, x3, y + 86 * u, cw, "scrittura", rate(m.dw));
        for (int i = 0; i < nd; i++)
            row(cv, x3, y + (112 + 26 * i) * u, cw, m.diskM[i], pct(m.diskPct[i]) + " di " + num1(m.diskTot[i]) + " GB");
        y += dh + gap;
        float ph = bottom - y;
        card(cv, x3, y, cw, ph, "Top processi (CPU)");
        int np = Math.min(m.procN.length, (int) ((ph - 44 * u) / (26 * u)));
        for (int i = 0; i < np; i++)
            row(cv, x3, y + (60 + 26 * i) * u, cw, m.procN[i], num1(m.procCpu[i]) + "%  " + m.procMb[i] + " MB");

        // dati vecchi (> 5 s): velo scuro sopra le schede
        if (!fresh) { fill.setColor(0x990d1117); cv.drawRect(0, top, W, H, fill); }
    }

    private void drawWaiting(Canvas cv, float W, float H) {
        big.setTextSize(34 * u); big.setColor(FG); big.setTextAlign(Paint.Align.CENTER);
        cv.drawText("In attesa del server Tab2Monitor…", W / 2, H / 2 - 40 * u, big);
        text.setTextSize(19 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < urls.length; i++)
            cv.drawText(urls[i] + "/api/metrics", W / 2, H / 2 + (10 + 30 * i) * u, text);
        cv.drawText("USB: adb reverse tcp:8090 tcp:8090   -   Wi-Fi: am start ... --es url http://<IP-PC>:8090",
                W / 2, H / 2 + (30 + 30 * urls.length) * u, text);
    }

    // Riquadro con titolo; restituisce nulla, il chiamante conosce l'altezza
    private void card(Canvas cv, float x, float y, float w, float h, String title) {
        rf.set(x, y, x + w, y + h);
        fill.setColor(CARD); cv.drawRoundRect(rf, 6 * u, 6 * u, fill);
        stroke.setColor(BORDER); cv.drawRoundRect(rf, 6 * u, 6 * u, stroke);
        text.setTextSize(15 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(fit(title.toUpperCase(), w - 24 * u), x + 12 * u, y + 26 * u, text);
    }

    // Scheda con numero grande, sottotitolo e barra; restituisce il bordo inferiore
    private float bigCard(Canvas cv, float x, float y, float w, String title, String val, String sub, float p) {
        float h = 170 * u;
        card(cv, x, y, w, h, title);
        big.setTextSize(60 * u); big.setColor(FG); big.setTextAlign(Paint.Align.LEFT);
        cv.drawText(val, x + 12 * u, y + 92 * u, big);
        text.setTextSize(17 * u); text.setColor(DIM);
        cv.drawText(fit(sub, w - 24 * u), x + 12 * u, y + 124 * u, text);
        bar(cv, x + 12 * u, y + 140 * u, w - 24 * u, 14 * u, p);
        return y + h;
    }

    private void bar(Canvas cv, float x, float y, float w, float h, float p) {
        if (Float.isNaN(p)) p = 0;
        p = Math.max(0, Math.min(100, p));
        fill.setColor(TRACK); cv.drawRect(x, y, x + w, y + h, fill);
        fill.setColor(level(p)); cv.drawRect(x, y, x + w * p / 100f, y + h, fill);
    }

    // Soglie come nella dashboard web: >= 70% giallo, >= 90% rosso
    private static int level(float p) { return p >= 90 ? RED : p >= 70 ? YELLOW : BLUE; }

    // Colonnine dei core: fino a 16 per riga, poi si va a capo
    private float coresCard(Canvas cv, float x, float y, float w, float h) {
        card(cv, x, y, w, h, "Core (" + m.cores.length + ")");
        int n = m.cores.length;
        if (n > 0) {
            int perRow = Math.min(n, 16), rows = (n + 15) / 16;
            float ax = x + 12 * u, aw = w - 24 * u, ay = y + 40 * u, ah = h - 52 * u, rg = 6 * u;
            float bh = (ah - (rows - 1) * rg) / rows, slot = aw / perRow, bw = slot * 0.8f;
            for (int i = 0; i < n; i++) {
                float bx = ax + (i % 16) * slot + (slot - bw) / 2, by = ay + (i / 16) * (bh + rg);
                float p = Math.max(0, Math.min(100, m.cores[i]));
                fill.setColor(TRACK); cv.drawRect(bx, by, bx + bw, by + bh, fill);
                fill.setColor(level(p)); cv.drawRect(bx, by + bh * (1 - p / 100f), bx + bw, by + bh, fill);
            }
        }
        return y + h;
    }

    private void histCard(Canvas cv, float x, float y, float w, float h) {
        card(cv, x, y, w, h, "Storico 60 s (CPU blu / RAM verde)");
        float gx = x + 12 * u, gw = w - 24 * u, gy = y + 40 * u, gh = h - 52 * u;
        stroke.setColor(BORDER);
        cv.drawLine(gx, gy + gh / 2, gx + gw, gy + gh / 2, stroke);
        poly(cv, hRam, gx, gy, gw, gh, GREEN);
        poly(cv, hCpu, gx, gy, gw, gh, BLUE);
    }

    private void poly(Canvas cv, float[] a, float gx, float gy, float gw, float gh, int col) {
        if (hLen < 2) return;
        path.rewind();
        for (int i = 0; i < hLen; i++) {
            float v = Float.isNaN(a[i]) ? 0 : Math.max(0, Math.min(100, a[i]));
            float px = gx + (HIST - hLen + i) * gw / (HIST - 1), py = gy + gh - v * gh / 100f;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        line.setColor(col);
        cv.drawPath(path, line);
    }

    // Riga "etichetta ........ valore" (etichetta accorciata se non ci sta)
    private void row(Canvas cv, float x, float y, float w, String l, String v) {
        text.setTextSize(17 * u); text.setColor(FG); text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(v, x + w - 12 * u, y, text);
        float vw = text.measureText(v);
        text.setColor(DIM); text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(fit(l, w - 36 * u - vw), x + 12 * u, y, text);
    }

    private String fit(String s, float max) {
        if (s == null) return "";
        int n = text.breakText(s, true, Math.max(0, max), null);
        return n >= s.length() ? s : s.substring(0, Math.max(0, n - 1)) + "…";
    }

    // ---------------------------------------------------------------- formati
    private static String pct(float p) { return Float.isNaN(p) ? "--" : Math.round(p) + "%"; }
    private static String num1(float v) { return Float.isNaN(v) ? "--" : String.valueOf(Math.round(v * 10) / 10f); }
    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private static String rate(long b) {
        if (b > 1048576) return (Math.round(b / 104857.6f) / 10f) + " MB/s";
        if (b > 1024) return Math.round(b / 1024f) + " KB/s";
        return b + " B/s";
    }

    private static String upt(long s) {
        long d = s / 86400, h = s % 86400 / 3600, mi = s % 3600 / 60;
        return (d > 0 ? d + "g " : "") + h + "h " + mi + "m";
    }
}
