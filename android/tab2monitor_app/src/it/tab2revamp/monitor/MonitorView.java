// =============================================================================
// Tab2Monitor - MonitorView (disegno su Canvas)
// Versione: v0.7.0 - 2026-10-22 02:30
//   v0.7.0: GRAFICA ispirata ai pannelli hardware commerciali (ricerca 2026-10-22: AIDA64
//           SensorPanel, NZXT Kraken LCD, Corsair Xeneon Edge, Lian Li Universal Screen,
//           Thermalright Trofeo Vision, temi Turing Smart Screen):
//           - un colore per componente (CPU ciano, GPU rosso/arancio, RAM viola, dischi
//             arancio, rete verde, Claude terracotta), striscia colorata a sinistra delle schede
//           - anelli spessi a gradiente verde->giallo->rosso con estremi arrotondati
//           - numero grande con unita' piccola; CPU con la temperatura al centro dell'anello
//             (come il display del Kraken); mini-grafico con area sfumata sotto CPU e RAM
//           - storico con area sfumata invece della sola linea
//   v0.5.0: pagina 2 = schede GPU (con tutti i loro sensori) + BOX PER HARDWARE (CPU, scheda
//           madre, RAM, dischi, ...) ognuno con le sue temperature e le sue ventole, disposti
//           su due sotto-colonne; sostituiscono i pannelli separati Temperature / Ventole
//   v0.4.0: PAGINE a rotazione (timer page_sec dalle impostazioni, tocco = pagina successiva),
//           icona ingranaggio -> impostazioni, tutte le GPU (una scheda per GPU),
//           temperature e ventole raggruppate per componente (campo "sensors");
//           ICONE disegnate: indicatore a lancetta (carichi), termometro (temperature),
//           ventola animata che gira in proporzione agli RPM reali; per ogni GPU due
//           indicatori affiancati: carico di elaborazione e saturazione VRAM
//   v0.3.0: pannello "Claude" (utilizzo token da Claude Code sul PC), id 9, colonna 2
//   v0.2.0: pannelli attivabili (PANEL_KEYS), colonne vuote eliminate, schermate
//           "nessuna sorgente" / "tutti i pannelli spenti"
//   v0.1.0 - 2026-10-21 23:50: layout fisso a 3 colonne
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Una sola View che disegna tutto: niente layout/TextView/RecyclerView, cosi'
// la gerarchia e' minima e un ridisegno al secondo costa pochissimo sull'OMAP4430.
// Pagine (v0.4.0), ognuna a 3 colonne con la palette della dashboard web:
//   1 "Sistema":        CPU, core, storico | RAM, Claude | rete, disco, processi
//   2 "GPU e sensori":  una scheda per GPU | temperature per componente | ventole
// (v0.1.0-v0.3.0: una sola pagina; col 2 era RAM, GPU, [Claude], temperature)
// Tutte le misure sono in "unita'" u = altezza/800, quindi il layout scala con
// lo schermo (1280x800 sul Tab 2). Paint e Path preallocati: in onDraw non si
// crea nessun oggetto tranne le stringhe dei valori.
// =============================================================================
package it.tab2revamp.monitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;

public class MonitorView extends View {
    // Palette (uguale alla dashboard web)
    static final int BG = 0xff0d1117, CARD = 0xff161b22, BORDER = 0xff30363d, TRACK = 0xff21262d;
    static final int FG = 0xffe6edf3, DIM = 0xff8b949e, BLUE = 0xff2f81f7, GREEN = 0xff3fb950;
    static final int YELLOW = 0xffd29922, RED = 0xfff85149;
    // v0.7.0: colori per componente (accento: striscia a sinistra + titolo della scheda)
    static final int CYAN = 0xff39c5f3, VIOLET = 0xffa371f7, ORANGE = 0xfff0883e;
    static final int TEAL = 0xff2ea89f, CLAUDE_C = 0xffd97757, AMD_RED = 0xffed4b4b;
    static final int[] GPU_ACC = {AMD_RED, ORANGE, VIOLET, CYAN};
    private int accent = BLUE;                           // accento della scheda che si sta disegnando
    private final Matrix mx = new Matrix();
    static final int HIST = 60;

    // 2026-10-22 v0.2.0: pannelli attivabili dal pannello impostazioni.
    // Indici: 0 CPU, 1 core, 2 storico, 3 RAM, 4 GPU, 5 temperature, 6 rete, 7 disco, 8 processi
    // 2026-10-22 v0.3.0: + "p_claude" (id 9). Versione v0.2.0 sostituita:
    //   static final String[] PANEL_KEYS = {"p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu",
    //           "p_temps", "p_net", "p_disk", "p_procs"};
    // 2026-10-22 v0.4.0: + "p_fans" (id 10); etichette con la pagina. Versione v0.3.0 sostituita:
    //   static final String[] PANEL_KEYS = {"p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu",
    //           "p_temps", "p_net", "p_disk", "p_procs", "p_claude"};
    //   static final String[] PANEL_LABELS = {"CPU (totale e frequenza)", "Core (un istogramma per core)",
    //           "Storico 60 s (CPU / RAM)", "RAM e swap", "GPU (LibreHardwareMonitor)",
    //           "Temperature (LibreHardwareMonitor)", "Rete (download / upload)",
    //           "Disco I/O e spazio dischi", "Top processi per CPU",
    //           "Claude - utilizzo account (token da Claude Code sul PC)"};
    static final String[] PANEL_KEYS = {"p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu",
            "p_temps", "p_net", "p_disk", "p_procs", "p_claude", "p_fans"};
    static final String[] PANEL_LABELS = {"CPU (totale e frequenza)  [pag. 1]",
            "Core (un istogramma per core)  [pag. 1]", "Storico 60 s (CPU / RAM)  [pag. 1]",
            "RAM e swap  [pag. 1]", "GPU - tutte le schede  [pag. 2]",
            "Temperature (box per hardware)  [pag. 2]", "Rete (download / upload)  [pag. 1]",
            "Disco I/O e spazio dischi  [pag. 1]", "Top processi per CPU  [pag. 1]",
            "Claude - utilizzo account (token da Claude Code sul PC)  [pag. 1]",
            "Ventole (box per hardware)  [pag. 2]"};
    // Colonne come nella dashboard web
    // 2026-10-22 v0.3.0: Claude (9) tra GPU e temperature. Versione v0.2.0 sostituita:
    //   static final int[][] COLS = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
    //   private boolean[] panelOn = {true, true, true, true, true, true, true, true, true};
    // 2026-10-22 v0.4.0: pagine di colonne. Versione v0.3.0 sostituita:
    //   static final int[][] COLS = {{0, 1, 2}, {3, 4, 9, 5}, {6, 7, 8}};
    //   private boolean[] panelOn = {true, true, true, true, true, true, true, true, true, true};
    // 2026-10-22 v0.5.0: pagina 2 = GPU | box per hardware (id 11, largo 2 colonne, acceso se
    // p_temps o p_fans). Versione v0.4.0 sostituita:
    //   {{4}, {5}, {10}}};                       // 2 GPU e sensori
    //   private boolean[] panelOn = {true, true, true, true, true, true, true, true, true, true, true};
    static final int BOXES = 11;
    static final int[][][] PAGES = {
            {{0, 1, 2}, {3, 9}, {6, 7, 8}},          // 1 Sistema
            {{4}, {BOXES}}};                         // 2 GPU e sensori
    static final String[] PAGE_NAMES = {"Sistema", "GPU e sensori"};
    private boolean[] panelOn = {true, true, true, true, true, true, true, true, true, true, true, true};
    private int page;                        // pagina mostrata
    private long pageAt;                     // uptimeMillis dell'ultimo cambio pagina
    private int pageSec = 15;                // 0 = niente rotazione automatica
    // v0.4.0: la pagina GPU resta di piu' se almeno una GPU supera la soglia di carico
    static final int GPU_PAGE = 1;
    private int gpuBusyPct = 30, gpuBusySec = 60;
    private float gearX, gearY;              // centro dell'ingranaggio (per il tocco)
    private final GestureDetector gestures;
    // v0.4.0: animazione ventole. Un angolo per ventola (indice progressivo nel disegno);
    // ridisegno a ~10 fps solo se sulla pagina c'e' almeno una ventola che gira.
    // 2026-10-22 v0.4.0 (2a stesura): le ventole sono disegnate da FanOverlay, una View
    // trasparente sopra questa. Ridisegnare tutta la dashboard a 10 fps costava ~29% CPU
    // (2% senza animazione); ora a ogni frame si ridisegnano solo le icone.
    FanOverlay overlay;                      // impostato da MainActivity
    private final float[] fanAng = new float[64];
    private int fanIdx;                      // contatore ventole nel disegno corrente
    private boolean fanSpinning;             // almeno una ventola > 0 RPM disegnata
    private long lastFrame;                  // uptimeMillis del frame precedente
    private float frameDt;                   // secondi trascorsi dal frame precedente
    // 10 fps (prima stesura 66 ms = 15 fps: ~25% CPU del tablet sulla pagina 2)
    static final int FAN_FRAME_MS = 100;
    private boolean animFans = true;         // interruttore "anim_fans" nelle impostazioni

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
        // v0.4.0: tocco breve sull'ingranaggio -> impostazioni, altrove -> pagina successiva.
        // La pressione lunga (impostazioni) resta gestita da MainActivity (OnLongClickListener).
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (Math.abs(e.getX() - gearX) < 40 * u && Math.abs(e.getY() - gearY) < 40 * u) openSettings();
                else nextPage();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);
        return super.onTouchEvent(e) || true;     // super: pressione lunga
    }

    private void openSettings() {
        Context c = getContext();
        c.startActivity(new Intent(c, SettingsActivity.class));
    }

    private void nextPage() {
        int n = PAGES.length;
        for (int k = 1; k <= n; k++) {
            int p = (page + k) % n;
            if (pageOn(p)) { page = p; break; }
        }
        pageAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private boolean gpuBusy() {
        if (m == null) return false;
        for (MainActivity.Metrics.Gpu g : m.gpus)
            if (!Float.isNaN(g.load) && g.load >= gpuBusyPct) return true;
        return false;
    }

    private boolean pageOn(int p) {
        for (int[] col : PAGES[p]) for (int id : col) if (panelOn[id]) return true;
        return false;
    }

    // 2026-10-22 v0.2.0 (era: void setUrls(String[] u) { urls = u; })
    // 2026-10-22 v0.4.0: + secondi per pagina (era: void setConfig(String[] u, boolean[] on))
    void setConfig(String[] u, boolean[] on, int sec, boolean anim, int busyPct, int busySec) {
        // v0.5.0: panelOn ha una voce in piu' (BOXES) calcolata da temperature/ventole
        boolean[] all = new boolean[BOXES + 1];
        System.arraycopy(on, 0, all, 0, Math.min(on.length, BOXES));
        all[BOXES] = all[5] || all[10];
        on = all;
        urls = u; panelOn = on; pageSec = sec; pageAt = SystemClock.uptimeMillis(); animFans = anim;
        gpuBusyPct = busyPct; gpuBusySec = busySec;
        if (u.length == 0) { m = null; src = null; }       // nessuna sorgente: schermata d'avviso
        invalidate();
    }

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
        long now = SystemClock.uptimeMillis();               // v0.4.0: animazione ventole
        frameDt = lastFrame == 0 ? 0 : Math.min(0.5f, (now - lastFrame) / 1000f);
        lastFrame = now; fanIdx = 0; fanSpinning = false;

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
        // 2026-10-22 v0.4.0: testo spostato a sinistra dell'ingranaggio. Versione v0.3.0 sostituita:
        //   cv.drawText(where + "   " + clk, W - pad, hy, text);
        //   float dotX = W - pad - text.measureText(where + "   " + clk) - 12 * u;
        gearX = W - pad - 13 * u; gearY = hy - 6 * u;
        drawGear(cv, gearX, gearY);
        float tr = W - pad - 44 * u;
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(where + "   " + clk, tr, hy, text);
        float dotX = tr - text.measureText(where + "   " + clk) - 12 * u;
        fill.setColor(fresh ? GREEN : RED);
        cv.drawCircle(dotX, hy - 6 * u, 7 * u, fill);

        if (m == null) { drawWaiting(cv, W, H); return; }

        float top = pad + 40 * u, bottom = H - pad;
        // 2026-10-22 v0.2.0: layout a pannelli attivabili (vedi layoutColumn / drawPanel).
        // Colonne vuote (tutti i pannelli spenti) spariscono e le altre si allargano.
        // 2026-10-22 v0.4.0: le colonne sono quelle della pagina corrente; rotazione a tempo.
        // Versione v0.2.0/v0.3.0 sostituita:
        //   int ncol = 0;
        //   for (int c = 0; c < COLS.length; c++) if (colOn(c)) ncol++;
        //   ... for (int c = 0; c < COLS.length; c++) { if (!colOn(c)) continue;
        //           layoutColumn(cv, COLS[c], x, top, cw, bottom - top, gap); x += cw + gap; }
        int npages = 0;
        for (int p = 0; p < PAGES.length; p++) if (pageOn(p)) npages++;
        if (npages == 0) { drawCentered(cv, W, H, "Tutti i pannelli sono spenti",
                "tocca l'ingranaggio o tieni premuto per aprire le impostazioni"); return; }
        if (!pageOn(page)) nextPage();
        // v0.4.0: durata della pagina GPU allungata se una scheda e' sotto carico
        // (prima stesura: sempre pageSec)
        int dur = page == GPU_PAGE && gpuBusy() ? Math.max(pageSec, gpuBusySec) : pageSec;
        if (pageSec > 0 && npages > 1 && SystemClock.uptimeMillis() - pageAt >= dur * 1000L) nextPage();
        drawPageIndicator(cv, W, hy);
        int[][] cols = PAGES[page];
        // v0.5.0: colonne con peso (il box per hardware vale 2). Era: larghezza uguale per tutte.
        int ncol = 0, weight = 0;
        for (int[] col : cols) if (colOn(col)) { ncol++; weight += colWeight(col); }
        float unit = (W - 2 * pad - (weight - 1) * gap) / weight, x = pad;
        for (int[] col : cols) {
            if (!colOn(col)) continue;
            int wgt = colWeight(col);
            float cw = unit * wgt + (wgt - 1) * gap;
            layoutColumn(cv, col, x, top, cw, bottom - top, gap);
            x += cw + gap;
        }

        // ---- Layout fisso v0.1.0 (2026-10-21 23:50), sostituito dal blocco sopra:
        //float top = pad + 40 * u, bottom = H - pad;
        //float cw = (W - 2 * pad - 2 * gap) / 3f;
        //float x1 = pad, x2 = pad + cw + gap, x3 = pad + 2 * (cw + gap);
        //
        //// ---- colonna 1
        //float y = top;
        //y = bigCard(cv, x1, y, cw, "CPU", pct(m.cpu), Float.isNaN(m.mhz) ? "" : (int) m.mhz + " MHz", m.cpu) + gap;
        //y = coresCard(cv, x1, y, cw, 170 * u) + gap;
        //histCard(cv, x1, y, cw, bottom - y);
        //
        //// ---- colonna 2
        //y = top;
        //y = bigCard(cv, x2, y, cw, "RAM", pct(m.ram),
        //        num1(m.ramUsed) + " / " + num1(m.ramTot) + " GB  -  swap " + pct(m.swap), m.ram) + gap;
        //boolean gpu = !Float.isNaN(m.gpuLoad);
        //String gsub = !gpu ? "LibreHardwareMonitor non attivo"
        //        : (Float.isNaN(m.gpuTemp) ? "" : num1(m.gpuTemp) + " °C")
        //        + (Float.isNaN(m.gpuMem) ? "" : "  -  VRAM " + pct(m.gpuMem));
        //y = bigCard(cv, x2, y, cw, m.gpuName != null ? "GPU - " + m.gpuName : "GPU",
        //        gpu ? pct(m.gpuLoad) : "--", gsub, gpu ? m.gpuLoad : 0) + gap;
        //float th = bottom - y;
        //card(cv, x2, y, cw, th, "Temperature");
        //int nt = Math.min(m.tempName.length, (int) ((th - 44 * u) / (26 * u)));
        //if (nt == 0) row(cv, x2, y + 60 * u, cw, "--", "");
        //for (int i = 0; i < nt; i++)
        //    row(cv, x2, y + (60 + 26 * i) * u, cw, m.tempName[i], num1(m.tempC[i]) + " °C");
        //
        //// ---- colonna 3
        //y = top;
        //card(cv, x3, y, cw, 104 * u, "Rete");
        //row(cv, x3, y + 60 * u, cw, "↓ download", rate(m.rx));
        //row(cv, x3, y + 86 * u, cw, "↑ upload", rate(m.tx));
        //y += 104 * u + gap;
        //int nd = Math.min(m.diskM.length, 6);
        //float dh = (104 + 26 * nd) * u;
        //card(cv, x3, y, cw, dh, "Disco I/O");
        //row(cv, x3, y + 60 * u, cw, "lettura", rate(m.dr));
        //row(cv, x3, y + 86 * u, cw, "scrittura", rate(m.dw));
        //for (int i = 0; i < nd; i++)
        //    row(cv, x3, y + (112 + 26 * i) * u, cw, m.diskM[i], pct(m.diskPct[i]) + " di " + num1(m.diskTot[i]) + " GB");
        //y += dh + gap;
        //float ph = bottom - y;
        //card(cv, x3, y, cw, ph, "Top processi (CPU)");
        //int np = Math.min(m.procN.length, (int) ((ph - 44 * u) / (26 * u)));
        //for (int i = 0; i < np; i++)
        //    row(cv, x3, y + (60 + 26 * i) * u, cw, m.procN[i], num1(m.procCpu[i]) + "%  " + m.procMb[i] + " MB");

        // dati vecchi (> 5 s): velo scuro sopra le schede
        if (!fresh) { fill.setColor(0x990d1117); cv.drawRect(0, top, W, H, fill); }
        // v0.4.0: ventole che girano -> prossimo frame tra ~66 ms (solo su questa pagina)
        // (prima stesura: if (fanSpinning && fresh) postInvalidateDelayed(FAN_FRAME_MS);)
        if (overlay != null) overlay.commit(fanIdx, animFans && fresh);
    }

    private void drawWaiting(Canvas cv, float W, float H) {
        if (urls.length == 0) {                              // v0.2.0
            drawCentered(cv, W, H, "Nessuna sorgente dati attiva",
                    "tieni premuto lo schermo per aprire le impostazioni e attivare USB o Wi-Fi");
            return;
        }
        big.setTextSize(34 * u); big.setColor(FG); big.setTextAlign(Paint.Align.CENTER);
        cv.drawText("In attesa del server Tab2Monitor…", W / 2, H / 2 - 40 * u, big);
        text.setTextSize(19 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < urls.length; i++)
            cv.drawText(urls[i] + "/api/metrics", W / 2, H / 2 + (10 + 30 * i) * u, text);
        // 2026-10-22 v0.2.0 (era: "USB: adb reverse ...   -   Wi-Fi: am start ... --es url ...")
        cv.drawText("USB: adb reverse tcp:8090 tcp:8090   -   tieni premuto per le impostazioni",
                W / 2, H / 2 + (30 + 30 * urls.length) * u, text);
    }

    private void drawCentered(Canvas cv, float W, float H, String t1, String t2) {
        big.setTextSize(34 * u); big.setColor(FG); big.setTextAlign(Paint.Align.CENTER);
        cv.drawText(t1, W / 2, H / 2 - 20 * u, big);
        text.setTextSize(19 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
        cv.drawText(t2, W / 2, H / 2 + 24 * u, text);
    }

    // ------------------------------------------------- pannelli (v0.2.0)
    // 2026-10-22 v0.4.0 (era: colOn(int c) su COLS[c])
    private static int colWeight(int[] col) {           // v0.5.0
        for (int id : col) if (id == BOXES) return 2;
        return 1;
    }

    private boolean colOn(int[] col) {
        for (int id : col) if (panelOn[id]) return true;
        return false;
    }

    // Pallini delle pagine + nome della pagina, al centro dell'intestazione (v0.4.0)
    private void drawPageIndicator(Canvas cv, float W, float hy) {
        int n = PAGES.length;
        float r = 5 * u, step = 18 * u, x0 = W / 2 - (n - 1) * step / 2 - 80 * u;
        for (int p = 0; p < n; p++) {
            if (!pageOn(p)) continue;
            fill.setColor(p == page ? FG : BORDER);
            cv.drawCircle(x0 + p * step, hy - 6 * u, r, fill);
        }
        text.setTextSize(17 * u); text.setColor(FG); text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(PAGE_NAMES[page], x0 + (n - 1) * step + 16 * u, hy, text);
    }

    // Ingranaggio disegnato: 8 denti + anello (nessuna risorsa grafica)
    private void drawGear(Canvas cv, float cx, float cy) {
        line.setColor(DIM);
        float old = line.getStrokeWidth();
        line.setStrokeWidth(4 * u);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            cv.drawLine(cx + c * 8 * u, cy + s * 8 * u, cx + c * 13 * u, cy + s * 13 * u, line);
        }
        line.setStrokeWidth(3 * u);
        cv.drawCircle(cx, cy, 8 * u, line);
        line.setStrokeWidth(old);
        fill.setColor(DIM);
        cv.drawCircle(cx, cy, 2.5f * u, fill);
    }

    // Altezza fissa del pannello, oppure -1 = "elastico" (prende lo spazio che resta)
    private float fixedH(int id) {
        switch (id) {
            // 2026-10-22 v0.4.0: GPU (4) e Claude (9) diventano elastici. Versione v0.3.0:
            //   case 0: case 1: case 3: case 4: return 170 * u;  ...  case 9: return 210 * u;
            case 0: case 1: case 3: return 170 * u;
            case 6: return 104 * u;
            // v0.4.0: con I/O per disco, due righe per disco (era sempre: (104 + 26 * nd) * u)
            case 7: return m.diskPerIo ? (66 + 54 * Math.min(m.diskM.length, 6)) * u
                    : (104 + 26 * Math.min(m.diskM.length, 6)) * u;
            default: return -1;           // storico, GPU, temperature, processi, Claude, ventole
        }
    }

    // Impila i pannelli accesi di una colonna; lo spazio libero va ai pannelli elastici
    private void layoutColumn(Canvas cv, int[] ids, float x, float y, float w, float h, float gap) {
        int n = 0, flex = 0; float fixed = 0;
        for (int id : ids) {
            if (!panelOn[id]) continue;
            n++;
            float f = fixedH(id);
            if (f < 0) flex++; else fixed += f;
        }
        float flexH = flex > 0 ? Math.max(100 * u, (h - fixed - (n - 1) * gap) / flex) : 0;
        for (int id : ids) {
            if (!panelOn[id]) continue;
            float f = fixedH(id), ph = f < 0 ? flexH : f;
            drawPanel(cv, id, x, y, w, ph);
            y += ph + gap;
        }
    }

    private void drawPanel(Canvas cv, int id, float x, float y, float w, float h) {
        switch (id) {
            // 2026-10-22 v0.7.0: heroCard (anello + mini-grafico) e colori per componente.
            // Versione v0.6.0: bigCard(cv, x, y, w, "CPU", pct(m.cpu), "<MHz>", m.cpu) e
            //                  bigCard(cv, x, y, w, "RAM", pct(m.ram), "<GB> - swap", m.ram)
            case 0:
                accent = CYAN;
                heroCard(cv, x, y, w, "CPU - carico", m.cpu, Float.isNaN(m.mhz) ? "" : (int) m.mhz + " MHz",
                        hCpu, Float.isNaN(m.cpuTemp) ? null : Math.round(m.cpuTemp) + "\u00b0",
                        Float.isNaN(m.cpuTemp) ? "carico" : "temp. CPU");
                break;
            case 1:
                accent = CYAN;
                coresCard(cv, x, y, w, h);
                break;
            case 2:
                accent = CYAN;
                histCard(cv, x, y, w, h);
                break;
            case 3:
                accent = VIOLET;
                heroCard(cv, x, y, w, "RAM - occupata", m.ram,
                        num1(m.ramUsed) + " / " + num1(m.ramTot) + " GB  -  swap " + pct(m.swap),
                        hRam, num1(m.ramUsed), "GB usati");
                break;
            case 4:                                          // v0.4.0: tutte le GPU
                gpuStack(cv, x, y, w, h);
                break;
            case 10:                                         // v0.4.0
                fansCard(cv, x, y, w, h);
                break;
            case BOXES:                                      // v0.5.0
                hwBoxes(cv, x, y, w, h);
                break;
            case -4: {   // v0.3.0, sostituito da gpuStack (una sola GPU da "lhm")
                boolean gpu = !Float.isNaN(m.gpuLoad);
                String gsub = !gpu ? "LibreHardwareMonitor non attivo"
                        : (Float.isNaN(m.gpuTemp) ? "" : num1(m.gpuTemp) + " °C")
                        + (Float.isNaN(m.gpuMem) ? "" : "  -  VRAM " + pct(m.gpuMem));
                bigCard(cv, x, y, w, m.gpuName != null ? "GPU - " + m.gpuName : "GPU",
                        gpu ? pct(m.gpuLoad) : "--", gsub, gpu ? m.gpuLoad : 0);
                break;
            }
            case 5:                                          // v0.4.0: per componente
                tempsCard(cv, x, y, w, h);
                break;
            case -5: {   // v0.3.0, sostituito da tempsCard
                card(cv, x, y, w, h, "Temperature");
                int nt = Math.min(m.tempName.length, (int) ((h - 44 * u) / (26 * u)));
                if (nt == 0) row(cv, x, y + 60 * u, w, "--", "");
                for (int i = 0; i < nt; i++)
                    row(cv, x, y + (60 + 26 * i) * u, w, m.tempName[i], num1(m.tempC[i]) + " °C");
                break;
            }
            case 6:
                accent = GREEN;                              // v0.7.0
                card(cv, x, y, w, h, "Rete");
                row(cv, x, y + 60 * u, w, "\u2193 download", rate(m.rx));
                row(cv, x, y + 86 * u, w, "\u2191 upload", rate(m.tx));
                break;
            case 7:
                accent = ORANGE;                             // v0.7.0
                if (m.diskPerIo) { disksCard(cv, x, y, w, h); break; }   // v0.4.0
                // senza dati per disco (server vecchio): totali come prima
            case -7: {
                card(cv, x, y, w, h, "Disco I/O");
                row(cv, x, y + 60 * u, w, "lettura", rate(m.dr));
                row(cv, x, y + 86 * u, w, "scrittura", rate(m.dw));
                int nd = Math.min(m.diskM.length, 6);
                for (int i = 0; i < nd; i++)
                    row(cv, x, y + (112 + 26 * i) * u, w, m.diskM[i],
                            pct(m.diskPct[i]) + " di " + num1(m.diskTot[i]) + " GB");
                break;
            }
            case 9:                                          // v0.3.0
                accent = CLAUDE_C;                           // v0.7.0
                claudeCard(cv, x, y, w, h);
                break;
            case 8: {
                accent = BLUE;                               // v0.7.0
                card(cv, x, y, w, h, "Top processi (CPU)");
                int np = Math.min(m.procN.length, (int) ((h - 44 * u) / (26 * u)));
                for (int i = 0; i < np; i++)
                    row(cv, x, y + (60 + 26 * i) * u, w, m.procN[i],
                            num1(m.procCpu[i]) + "%  " + m.procMb[i] + " MB");
                break;
            }
        }
    }

    // Riquadro con titolo; restituisce nulla, il chiamante conosce l'altezza
    // 2026-10-22 v0.7.0: striscia colorata (accento del componente) e titolo colorato in grassetto,
    // bordo piu' discreto. Versione v0.1.0-v0.6.0: bordo BORDER, titolo grigio DIM.
    private void card(Canvas cv, float x, float y, float w, float h, String title) {
        rf.set(x, y, x + w, y + h);
        fill.setColor(CARD); cv.drawRoundRect(rf, 8 * u, 8 * u, fill);
        stroke.setColor(0xff252b33); cv.drawRoundRect(rf, 8 * u, 8 * u, stroke);
        fill.setColor(accent);
        rf.set(x, y + 10 * u, x + 4 * u, y + h - 10 * u);
        cv.drawRoundRect(rf, 2 * u, 2 * u, fill);
        text.setTextSize(15 * u); text.setColor(accent); text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(true);
        cv.drawText(fit(title.toUpperCase(), w - 24 * u), x + 14 * u, y + 26 * u, text);
        text.setFakeBoldText(false);
    }

    // Scheda con numero grande, sottotitolo e barra; restituisce il bordo inferiore
    // v0.7.0: numero grande + unita' piccola, sottotitolo, mini-grafico con area sfumata a sinistra;
    // a destra anello a gradiente con al centro "center" (es. temperatura CPU) e sotto "centerLab".
    private void heroCard(Canvas cv, float x, float y, float w, String title, float val, String sub,
                          float[] hist, String center, String centerLab) {
        float h = 170 * u, r = 54 * u, gx = x + w - r - 22 * u, gy = y + 92 * u;
        card(cv, x, y, w, h, title);
        // numero + unita'
        String num = Float.isNaN(val) ? "--" : String.valueOf(Math.round(val));
        big.setTextSize(64 * u); big.setColor(FG); big.setTextAlign(Paint.Align.LEFT);
        cv.drawText(num, x + 14 * u, y + 88 * u, big);
        float nw = big.measureText(num);
        big.setTextSize(28 * u); big.setColor(DIM);
        cv.drawText("%", x + 18 * u + nw, y + 88 * u, big);
        text.setTextSize(16 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.LEFT);
        float leftW = gx - r - 16 * u - x - 14 * u;
        cv.drawText(fit(sub, leftW), x + 14 * u, y + 116 * u, text);
        spark(cv, hist, x + 14 * u, y + 126 * u, leftW, 32 * u, accent);
        ring(cv, gx, gy, r, val, center, centerLab);
    }

    // Anello a gradiente (verde -> giallo -> rosso), estremi arrotondati, testo al centro
    private void ring(Canvas cv, float cx, float cy, float r, float p, String center, String lab) {
        if (Float.isNaN(p)) p = 0;
        p = Math.max(0, Math.min(100, p));
        float old = line.getStrokeWidth();
        line.setStrokeWidth(r * 0.2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        line.setColor(TRACK); cv.drawArc(rf, 135, 270, false, line);
        line.setShader(sweep(cx, cy)); line.setColor(0xffffffff);
        if (p > 0.5f) cv.drawArc(rf, 135, 270 * p / 100f, false, line);
        line.setShader(null); line.setStrokeCap(Paint.Cap.BUTT); line.setStrokeWidth(old);
        big.setTextSize(r * 0.5f); big.setColor(FG); big.setTextAlign(Paint.Align.CENTER);
        cv.drawText(center != null ? center : pct(p), cx, cy + r * 0.18f, big);
        if (lab != null) {
            text.setTextSize(13 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
            cv.drawText(lab, cx, cy + r * 0.62f, text);
        }
    }

    // Gradiente angolare: posizione 0 = 135 gradi (inizio dell'arco), 0.75 = fine (405 gradi)
    private SweepGradient sweep(float cx, float cy) {
        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{GREEN, GREEN, YELLOW, RED, RED}, new float[]{0f, 0.35f, 0.55f, 0.72f, 1f});
        mx.setRotate(135, cx, cy);
        g.setLocalMatrix(mx);
        return g;
    }

    // Mini-grafico (sparkline) con area sfumata del colore del componente
    private void spark(Canvas cv, float[] a, float gx, float gy, float gw, float gh, int col) {
        if (a == null || hLen < 2) return;
        path.rewind();
        float x0 = 0, px = 0;
        for (int i = 0; i < hLen; i++) {
            float v = Float.isNaN(a[i]) ? 0 : Math.max(0, Math.min(100, a[i]));
            px = gx + (HIST - hLen + i) * gw / (HIST - 1);
            float py = gy + gh - v * gh / 100f;
            if (i == 0) { path.moveTo(px, py); x0 = px; } else path.lineTo(px, py);
        }
        line.setColor(col); line.setStrokeWidth(2 * u);
        cv.drawPath(path, line);
        path.lineTo(px, gy + gh); path.lineTo(x0, gy + gh); path.close();
        fill.setShader(new LinearGradient(0, gy, 0, gy + gh, (col & 0x00ffffff) | 0x70000000,
                col & 0x00ffffff, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    // (v0.1.0-v0.6.0) Scheda con numero grande, sottotitolo e barra; ora usata solo come ripiego
    private float bigCard(Canvas cv, float x, float y, float w, String title, String val, String sub, float p) {
        float h = 170 * u;
        card(cv, x, y, w, h, title);
        big.setTextSize(60 * u); big.setColor(FG); big.setTextAlign(Paint.Align.LEFT);
        cv.drawText(val, x + 12 * u, y + 92 * u, big);
        text.setTextSize(17 * u); text.setColor(DIM);
        cv.drawText(fit(sub, w - 24 * u), x + 12 * u, y + 124 * u, text);
        bar(cv, x + 12 * u, y + 140 * u, w - 24 * u, 14 * u, p);
        gauge(cv, x + w - 58 * u, y + 72 * u, 38 * u, p, null);   // v0.4.0
        return y + h;
    }

    // ------------------------------------------------------- icone (v0.4.0)
    // Indicatore a lancetta: arco di 270 gradi (traccia + valore colorato a soglie), lancetta,
    // valore % al centro se label != null (sotto, l'etichetta).
    private void gauge(Canvas cv, float cx, float cy, float r, float p, String label) {
        if (Float.isNaN(p)) p = 0;
        p = Math.max(0, Math.min(100, p));
        float old = line.getStrokeWidth();
        line.setStrokeWidth(r * 0.22f);
        line.setStrokeCap(Paint.Cap.BUTT);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        line.setColor(TRACK); cv.drawArc(rf, 135, 270, false, line);
        line.setColor(level(p)); cv.drawArc(rf, 135, 270 * p / 100f, false, line);
        double a = Math.toRadians(135 + 270 * p / 100f);
        line.setStrokeWidth(Math.max(2, r * 0.07f)); line.setColor(FG);
        // lancetta corta (0.6 r) e valore nell'apertura in basso: non si sovrappongono
        // (prima stesura: lancetta 0.72 r, valore a cy + 0.62 r -> coperto dalla lancetta)
        cv.drawLine(cx, cy, cx + (float) Math.cos(a) * r * 0.6f, cy + (float) Math.sin(a) * r * 0.6f, line);
        line.setStrokeWidth(old);
        fill.setColor(FG); cv.drawCircle(cx, cy, r * 0.09f, fill);
        if (label != null) {
            big.setTextSize(r * 0.42f); big.setColor(FG); big.setTextAlign(Paint.Align.CENTER);
            cv.drawText(pct(p), cx, cy + r * 0.98f, big);
            text.setTextSize(16 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
            cv.drawText(label, cx, cy + r + 24 * u, text);
        }
    }

    // Termometro: bulbo + stelo riempito in proporzione (0-100 °C), colore a soglie
    // (< 60 blu, < 80 giallo, oltre rosso). (x, y) = linea di base del testo della riga.
    private void thermo(Canvas cv, float x, float y, float c) {
        float cx = x + 6 * u, top = y - 18 * u, bulbY = y - 2 * u, sw = 5 * u;
        int col = Float.isNaN(c) ? DIM : c >= 80 ? RED : c >= 60 ? YELLOW : BLUE;
        rf.set(cx - sw / 2, top, cx + sw / 2, bulbY);
        fill.setColor(TRACK); cv.drawRoundRect(rf, sw / 2, sw / 2, fill);
        float f = Float.isNaN(c) ? 0 : Math.max(0, Math.min(1, c / 100f));
        rf.set(cx - sw / 2, bulbY - (bulbY - top) * f, cx + sw / 2, bulbY);
        fill.setColor(col); cv.drawRect(rf, fill);
        cv.drawCircle(cx, bulbY, 5 * u, fill);
    }

    // Ventola a 3 pale che ruota: velocita' visiva proporzionale agli RPM
    // (1 giro reale = 1/10 di giro disegnato per 1000 RPM -> fino a ~1 giro/s a 3000 RPM)
    private void fan(Canvas cv, float cx, float cy, float r, float rpm) {
        if (overlay != null) {                       // 2a stesura: solo registrazione
            if (fanIdx < FanOverlay.MAX) overlay.set(fanIdx++, cx, cy, r, rpm);
            return;
        }
        // --- prima stesura (senza overlay): disegno diretto con ridisegno di tutta la vista
        int k = fanIdx < fanAng.length ? fanIdx : fanAng.length - 1;
        fanIdx++;
        float degPerSec = Float.isNaN(rpm) ? 0 : Math.min(rpm, 4000) * 0.12f;
        if (degPerSec > 0 && animFans) { fanAng[k] = (fanAng[k] + degPerSec * frameDt) % 360; fanSpinning = true; }
        int col = Float.isNaN(rpm) || rpm <= 0 ? DIM : BLUE;
        stroke.setColor(BORDER); cv.drawCircle(cx, cy, r, stroke);
        fill.setColor(col);
        cv.save();
        cv.rotate(fanAng[k], cx, cy);
        for (int i = 0; i < 3; i++) {
            rf.set(cx - r * 0.28f, cy - r * 0.92f, cx + r * 0.28f, cy - r * 0.12f);
            cv.drawOval(rf, fill);
            cv.rotate(120, cx, cy);
        }
        cv.restore();
        fill.setColor(CARD); cv.drawCircle(cx, cy, r * 0.18f, fill);
    }

    // ---------------------------------------------------------------------------
    // FanOverlay (v0.4.0): View trasparente, non cliccabile (i tocchi passano a MonitorView),
    // che disegna solo le ventole e si anima da sola a ~10 fps finche' almeno una gira.
    static final class FanOverlay extends View {
        static final int MAX = 64;
        private final float[] cx = new float[MAX], cy = new float[MAX], r = new float[MAX];
        private final float[] rpm = new float[MAX], ang = new float[MAX];
        private int n;
        private boolean anim;
        private long last;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();

        FanOverlay(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            setClickable(false); setFocusable(false);
        }

        void set(int i, float x, float y, float rr, float v) { cx[i] = x; cy[i] = y; r[i] = rr; rpm[i] = v; }

        // Chiamato a fine onDraw di MonitorView con il numero di ventole sulla pagina
        void commit(int count, boolean animate) { n = count; anim = animate; invalidate(); }

        @Override
        protected void onDraw(Canvas cv) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.5f, (now - last) / 1000f);
            last = now;
            boolean spinning = false;
            stroke.setStrokeWidth(Math.max(1, getHeight() / 800f));
            for (int i = 0; i < n; i++) {
                float v = rpm[i], degPerSec = Float.isNaN(v) ? 0 : Math.min(v, 4000) * 0.12f;
                if (degPerSec > 0 && anim) { ang[i] = (ang[i] + degPerSec * dt) % 360; spinning = true; }
                stroke.setColor(BORDER); cv.drawCircle(cx[i], cy[i], r[i], stroke);
                fill.setColor(Float.isNaN(v) || v <= 0 ? DIM : BLUE);
                cv.save();
                cv.rotate(ang[i], cx[i], cy[i]);
                for (int b = 0; b < 3; b++) {
                    rf.set(cx[i] - r[i] * 0.28f, cy[i] - r[i] * 0.92f, cx[i] + r[i] * 0.28f, cy[i] - r[i] * 0.12f);
                    cv.drawOval(rf, fill);
                    cv.rotate(120, cx[i], cy[i]);
                }
                cv.restore();
                fill.setColor(CARD); cv.drawCircle(cx[i], cy[i], r[i] * 0.18f, fill);
            }
            if (spinning) postInvalidateDelayed(100);       // 10 fps (66 ms = 15 fps: ~17% CPU)
            else last = 0;
        }
    }

    // Riga con icona a sinistra (etichetta spostata di 22u)
    private void rowIcon(Canvas cv, float x, float y, float w, String l, String v) {
        row(cv, x + 22 * u, y, w - 22 * u, l, v);
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
        card(cv, x, y, w, h, "Storico 60 s (CPU ciano / RAM viola)");   // v0.7.0 (era: blu / verde)
        float gx = x + 12 * u, gw = w - 24 * u, gy = y + 40 * u, gh = h - 52 * u;
        stroke.setColor(BORDER);
        cv.drawLine(gx, gy + gh / 2, gx + gw, gy + gh / 2, stroke);
        // v0.7.0: aree sfumate con i colori dei componenti (era: poly() GREEN / BLUE, solo linea)
        spark(cv, hRam, gx, gy, gw, gh, VIOLET);
        spark(cv, hCpu, gx, gy, gw, gh, CYAN);
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

    // ------------------------------------------- GPU, temperature, ventole (v0.4.0)
    // Una scheda per GPU, impilate nella colonna (max 280u ciascuna)
    private void gpuStack(Canvas cv, float x, float y, float w, float h) {
        int n = m.gpus.length;
        if (n == 0) {
            card(cv, x, y, w, h, "GPU");
            row(cv, x, y + 60 * u, w, "nessun dato GPU dal server", "");
            return;
        }
        // v0.5.0: max 360u (era 280u: con tutti i sensori la 9070 XT perdeva ventola e potenza)
        float gap = 10 * u, gh = Math.min(360 * u, (h - (n - 1) * gap) / n);
        for (int i = 0; i < n; i++) gpuCard(cv, x, y + i * (gh + gap), w, gh, i);
    }

    private void gpuCard(Canvas cv, float x, float y, float w, float h, int i) {
        MainActivity.Metrics.Gpu g = m.gpus[i];
        accent = GPU_ACC[i % GPU_ACC.length];            // v0.7.0
        card(cv, x, y, w, h, "GPU " + (i + 1) + " - " + g.name);
        // Carico di elaborazione e saturazione VRAM, affiancati (richiesta utente 2026-10-22)
        float gr = Math.min(46 * u, Math.min(w / 4 - 10 * u, (h - 120 * u) / 2));
        float gy = y + 44 * u + gr;
        // v0.7.0: anelli a gradiente con il valore al centro (era: gauge(...) a lancetta)
        ring(cv, x + w / 4, gy, gr, g.load, pct(g.load), null);
        ring(cv, x + 3 * w / 4, gy, gr, g.memPct, pct(g.memPct), null);
        text.setTextSize(16 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
        cv.drawText("Carico", x + w / 4, gy + gr + 24 * u, text);
        cv.drawText("VRAM", x + 3 * w / 4, gy + gr + 24 * u, text);
        text.setTextSize(16 * u); text.setColor(DIM); text.setTextAlign(Paint.Align.CENTER);
        if (!Float.isNaN(g.memTotMb))
            cv.drawText(num1(g.memMb / 1024f) + " / " + num1(g.memTotMb / 1024f) + " GB",
                    x + 3 * w / 4, gy + gr + 44 * u, text);
        float ry = gy + gr + 72 * u, end = y + h - 8 * u;
        if (g.lab.length > 0) {                          // v0.5.0: tutti i sensori della GPU
            ry = sensorRows(cv, g, x, ry, w, end);
            if (ry <= end) rowIcon(cv, x, ry, w, "Potenza", pct(g.powerPct));
            return;
        }
        if (ry <= end) {
            thermo(cv, x + 12 * u, ry, g.temp);
            rowIcon(cv, x, ry, w, "Temperatura", Float.isNaN(g.temp) ? "--" : num1(g.temp) + " °C");
            ry += 28 * u;
        }
        if (ry <= end) {
            fan(cv, x + 20 * u, ry - 6 * u, 9 * u, g.fanRpm);
            rowIcon(cv, x, ry, w, "Ventola", Float.isNaN(g.fanRpm) ? "--" : Math.round(g.fanRpm) + " RPM");
            ry += 28 * u;
        }
        if (ry <= end) rowIcon(cv, x, ry, w, "Potenza", pct(g.powerPct));
        // (v0.4.0 prima stesura, sostituita: numero grande del carico + barra + "VRAM x / y GB")
    }

    // Righe raggruppate per componente: intestazione (nome hw) + una riga per sensore
    private void tempsCard(Canvas cv, float x, float y, float w, float h) {
        card(cv, x, y, w, h, "Temperature");
        float ry = y + 60 * u, end = y + h - 8 * u;
        boolean any = false;
        for (MainActivity.Metrics.Hw hw : m.hw) {
            if (hw.tName.length == 0) continue;
            if (ry > end) break;
            hwHeader(cv, x, ry, w, hw.name); ry += 26 * u;
            for (int q = 0; q < hw.tName.length && ry <= end; q++, ry += 28 * u) {
                thermo(cv, x + 14 * u, ry, hw.tC[q]);
                rowIcon(cv, x, ry, w, hw.tName[q], num1(hw.tC[q]) + " °C");
            }
            any = true;
        }
        if (!any) row(cv, x, ry, w, "nessuna temperatura dal server", "");
    }

    private void fansCard(Canvas cv, float x, float y, float w, float h) {
        card(cv, x, y, w, h, "Ventole");
        float ry = y + 60 * u, end = y + h - 8 * u;
        boolean any = false;
        for (MainActivity.Metrics.Hw hw : m.hw) {
            if (hw.fName.length == 0) continue;
            if (ry > end) break;
            hwHeader(cv, x, ry, w, hw.name); ry += 26 * u;
            for (int q = 0; q < hw.fName.length && ry <= end; q++, ry += 34 * u) {
                String v = (Float.isNaN(hw.fRpm[q]) ? "--" : Math.round(hw.fRpm[q]) + " RPM")
                        + (Float.isNaN(hw.fPct[q]) ? "" : "  " + pct(hw.fPct[q]));
                fan(cv, x + 24 * u, ry - 6 * u, 13 * u, hw.fRpm[q]);
                row(cv, x + 30 * u, ry, w - 30 * u, hw.fName[q], v);
            }
            any = true;
        }
        if (!any) row(cv, x, ry, w, "nessuna ventola dal server", "");
    }

    // Dischi con lettura/scrittura propria (richiesta utente 2026-10-22: capire quale disco
    // e' attivo). Pallino verde = disco con I/O in questo secondo.
    private void disksCard(Canvas cv, float x, float y, float w, float h) {
        card(cv, x, y, w, h, "Dischi - I/O per disco");
        int nd = Math.min(m.diskM.length, 6);
        for (int i = 0; i < nd; i++) {
            float ry = y + (60 + 54 * i) * u;
            long r = m.diskR[i], wr = m.diskW[i];
            boolean act = r > 0 || wr > 0;
            fill.setColor(act ? GREEN : BORDER);
            cv.drawCircle(x + 18 * u, ry - 6 * u, 6 * u, fill);
            text.setTextSize(17 * u); text.setColor(act ? FG : DIM); text.setTextAlign(Paint.Align.LEFT);
            cv.drawText(m.diskM[i], x + 32 * u, ry, text);
            text.setTextAlign(Paint.Align.RIGHT); text.setColor(DIM);
            cv.drawText(pct(m.diskPct[i]) + " di " + num1(m.diskTot[i]) + " GB", x + w - 12 * u, ry, text);
            row(cv, x + 20 * u, ry + 24 * u, w - 20 * u,
                    "↓ " + (r < 0 ? "--" : rate(r)), "↑ " + (wr < 0 ? "--" : rate(wr)));
        }
    }

    // ------------------------------------------------ box per hardware (v0.5.0)
    // Box impilati su due sotto-colonne: ognuno va nella sotto-colonna piu' bassa.
    // Un box che non ci sta per intero mostra le righe che entrano.
    private final float[] subY = new float[2];
    private void hwBoxes(Canvas cv, float x, float y, float w, float h) {
        if (m.boxes.length == 0) {
            card(cv, x, y, w, h, "Sensori");
            row(cv, x, y + 60 * u, w, "nessun sensore dal server", "");
            return;
        }
        float gap = 10 * u, sw = (w - gap) / 2, bottom = y + h;
        subY[0] = subY[1] = y;
        for (MainActivity.Metrics.Box b : m.boxes) {
            int vis = 0, fans = 0;
            for (int i = 0; i < b.lab.length; i++) if (rowOn(b.fan[i])) { vis++; if (b.fan[i]) fans++; }
            if (vis == 0) continue;
            float bh = (44 + 28 * (vis - fans) + 32 * fans + 4) * u;
            int c = subY[0] <= subY[1] ? 0 : 1;
            float by = subY[c], avail = bottom - by;
            if (avail < 80 * u) continue;                  // sotto-colonna piena
            bh = Math.min(bh, avail);
            float bx = x + c * (sw + gap);
            // v0.7.0: colore per tipo di hardware
            accent = b.title.startsWith("CPU") ? CYAN : b.title.startsWith("Scheda madre") ? TEAL
                    : b.title.equals("RAM") ? VIOLET : b.title.equals("Dischi") ? ORANGE : BLUE;
            card(cv, bx, by, sw, bh, b.title);
            sensorRows(cv, b, bx, by + 60 * u, sw, by + bh - 8 * u);
            subY[c] = by + bh + gap;
        }
    }

    private boolean rowOn(boolean isFan) { return isFan ? panelOn[10] : panelOn[5]; }

    // Righe temperatura (termometro) e ventola (icona animata); restituisce la y successiva
    private float sensorRows(Canvas cv, MainActivity.Metrics.Rows r, float x, float ry, float w, float end) {
        for (int i = 0; i < r.lab.length && ry <= end; i++) {
            if (!rowOn(r.fan[i])) continue;
            if (r.fan[i]) {
                float v = r.val[i];
                fan(cv, x + 20 * u, ry - 6 * u, 11 * u, v);
                rowIcon(cv, x + 6 * u, ry, w - 6 * u, r.lab[i],
                        (Float.isNaN(v) ? "--" : Math.round(v) + " RPM") + (Float.isNaN(r.pct[i]) ? "" : "  " + pct(r.pct[i])));
                ry += 32 * u;
            } else {
                thermo(cv, x + 14 * u, ry, r.val[i]);
                rowIcon(cv, x, ry, w, r.lab[i], num1(r.val[i]) + " °C");
                ry += 28 * u;
            }
        }
        return ry;
    }

    private void hwHeader(Canvas cv, float x, float y, float w, String name) {
        text.setTextSize(16 * u); text.setColor(BLUE); text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(fit(name, w - 24 * u), x + 12 * u, y, text);
    }

    // ------------------------------------------------- pannello Claude (v0.3.0)
    // Dati dal campo "claude" di /api/metrics (server Windows, che legge i log locali di
    // Claude Code: ~/.claude/projects/*.jsonl). Sono token consumati da Claude Code su quel PC,
    // non la percentuale del limite del piano (non esposta da nessuna API pubblica).
    private void claudeCard(Canvas cv, float x, float y, float w, float h) {
        MainActivity.Metrics.Claude c = m.claude;
        card(cv, x, y, w, h, "Claude - utilizzo (Claude Code)");
        if (c == null) {
            row(cv, x, y + 60 * u, w, "dati non disponibili", "");
            row(cv, x, y + 86 * u, w, "il server non invia ancora il campo \"claude\"", "");
            return;
        }
        String blk = "Blocco 5 h";
        if (c.blockEnd > 0) {
            cal.setTimeInMillis(c.blockEnd * 1000L);
            blk += "  (fino alle " + two(cal.get(Calendar.HOUR_OF_DAY)) + ":" + two(cal.get(Calendar.MINUTE)) + ")";
        }
        row(cv, x, y + 60 * u, w, blk, c.blockEnd > 0 ? tok(c.blockTok) : "nessuno attivo");
        row(cv, x, y + 86 * u, w, "Oggi  (" + c.todayMsgs + " risposte)", tok(c.todayTok));
        row(cv, x, y + 112 * u, w, "Oggi - output", tok(c.todayOut));
        row(cv, x, y + 138 * u, w, "Oggi - letti da cache", tok(c.todayCacheR));
        row(cv, x, y + 164 * u, w, "Ultimi 7 giorni", tok(c.weekTok));
        row(cv, x, y + 190 * u, w, c.topModel != null ? c.topModel : "modello: --",
                c.topModel != null ? tok(c.topModelTok) : "");
    }

    private static String tok(long t) {
        if (t < 0) return "--";
        if (t >= 1000000) return (Math.round(t / 100000f) / 10f) + " M";
        if (t >= 1000) return Math.round(t / 1000f) + " k";
        return String.valueOf(t);
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
