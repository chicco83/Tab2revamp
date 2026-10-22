// =============================================================================
// Tab2Monitor - MainActivity + thread di polling
// Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
//   v0.10.0: minSdk 21 (Android 5.0+): permesso CAMERA chiesto a runtime solo da API 23;
//           scoperta automatica del server (Discovery, UDP 8090) quando nessuna sorgente
//           risponde: si salva il server trovato (wifi_url + server_name) e lo si ritrova
//           per nome anche se cambia IP; lingua italiano/inglese (L, chiave "lang")
//   v0.9.0: animazioni fluide (chiave anim_smooth, durata = poll_ms); gauge check all'accensione
//           dello schermo (chiave gauge_check, receiver SCREEN_ON + fine oscuramento)
//   v0.8.0: servizio avviato anche solo per gli allarmi vocali (alarm_on); chiavi load_wake,
//           alarm_on; extra interi th_cpu, th_gpu, th_ram, th_vram, alarm_def
//   v0.7.0: Metrics.cpuTemp (prima temperatura dell'hardware "cpu") per l'anello della CPU
//   v0.6.0: presenza con fotocamera frontale (PresenceService): avvia/ferma il servizio
//           secondo l'opzione "presence", oscuramento di ripiego (dim) e finestra visibile
//           sopra la schermata di blocco (dopo lockNow lo schermo si riaccende sulla dashboard)
//   v0.5.1: voci con "hidden": true (scelta "non mostrare" in http://<PC>:8090/config) saltate
//   v0.5.0: "box per hardware": temperature e ventole raggruppate per componente
//           (CPU, scheda madre, RAM, dischi, ...) usando anche sensors.pairs per spostare
//           ogni ventola nel box dell'hardware che raffredda; GPU con tutti i propri sensori
//   v0.4.1: scartate le "temperature" che sono soglie fisse dei sensori (Limit, Resolution,
//           Warning/Critical Temperature)
//   v0.4.0: piu' GPU (campo "gpus"), sensori per componente con ventole ("sensors"),
//           pagine a rotazione (page_sec), pannello Ventole (p_fans)
//   v0.3.0: campo "claude" di /api/metrics (pannello utilizzo Claude), chiave p_claude
//   v0.2.0: sorgenti USB/Wi-Fi e pannelli attivabili dal pannello impostazioni
//           (SettingsActivity, si apre tenendo premuto lo schermo)
//   v0.1.0 - 2026-10-21 23:50: prima versione
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Cosa fa:
//   - schermo intero "immersive sticky" (niente barra di stato/navigazione),
//     schermo sempre acceso (FLAG_KEEP_SCREEN_ON) finche' l'app e' in primo piano;
//   - un thread legge /api/metrics dal server Windows ogni POLL_MS (default 1000),
//     analizza il JSON fuori dal thread UI e passa un oggetto Metrics alla vista;
//   - indirizzi provati in ordine, partendo dall'ultimo che ha risposto:
//       1) http://127.0.0.1:8090   (USB: adb reverse tcp:8090 tcp:8090)
//       2) URL Wi-Fi salvato nelle preferenze (es. http://192.168.1.237:8090)
//     cosi' dopo un riavvio del tablet (adb reverse perso) passa da solo al Wi-Fi.
//   - in pausa (schermo spento / altra app) il polling si ferma: zero CPU.
//
// Configurazione: tenere premuto lo schermo -> SettingsActivity, oppure da PC:
//   adb shell am start -n it.tab2revamp.monitor/.MainActivity \
//       --es url http://192.168.1.237:8090   (URL Wi-Fi; "" per toglierlo)
//       --ei poll_ms 2000                    (intervallo di polling)
//       --ez autostart false                 (disattiva l'avvio al boot)
//       --ez src_usb false / --ez p_gpu false ... (sorgenti e pannelli, v0.2.0)
//       --ei page_sec 20                     (cambio pagina, 0 = fermo; v0.4.0)
// =============================================================================
package it.tab2revamp.monitor;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    static final String PREFS = "tab2monitor";
    static final String USB_URL = "http://127.0.0.1:8090";
    // Interruttori booleani (default true) impostabili dal pannello o con "am start --ez"
    // 2026-10-22 v0.3.0: + "p_claude". Versione v0.2.0 sostituita:
    //   static final String[] BOOL_KEYS = {"autostart", "src_usb", "src_wifi",
    //           "p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu", "p_temps", "p_net", "p_disk", "p_procs"};
    // 2026-10-22 v0.4.0: + "p_fans". Versione v0.3.0 sostituita:
    //   static final String[] BOOL_KEYS = {"autostart", "src_usb", "src_wifi",
    //           "p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu", "p_temps", "p_net", "p_disk", "p_procs",
    //           "p_claude"};
    static final String[] BOOL_KEYS = {"autostart", "src_usb", "src_wifi",
            "p_cpu", "p_cores", "p_hist", "p_ram", "p_gpu", "p_temps", "p_net", "p_disk", "p_procs",
            "p_claude", "p_fans", "anim_fans", "presence",       // v0.6.0: + presence (default false)
            "load_wake", "alarm_on",                             // v0.8.0 (alarm_on default false)
            "anim_smooth", "gauge_check"};                       // v0.9.0
    static final int DEF_PAGE_SEC = 15;   // v0.4.0: secondi per pagina (0 = niente rotazione)

    private MonitorView view;
    private View blackout;                              // v0.6.0: velo nero dell'oscuramento
    private static MainActivity current;                // v0.6.0: per PresenceService.dim()
    static volatile Metrics lastMetrics;                 // v0.8.0: per l'elenco sensori nelle impostazioni
    private Poller poller;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, 0);
        if (getIntent() != null && getIntent().hasExtra("lang"))           // v0.10.0
            prefs.edit().putString("lang", getIntent().getStringExtra("lang")).apply();
        L.set(prefs.getString("lang", ""));
        // v0.6.0: + SHOW_WHEN_LOCKED / DISMISS_KEYGUARD / TURN_SCREEN_ON: quando PresenceService
        // riaccende lo schermo dopo lockNow() si vede subito la dashboard, non il blocco schermo.
        // (v0.5.x: solo KEEP_SCREEN_ON | FULLSCREEN)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        view = new MonitorView(this);
        // pressione lunga ovunque -> impostazioni (un tocco breve non fa nulla:
        // evita aperture accidentali su un display fisso)
        view.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
        });
        // v0.4.0: livello trasparente per le ventole animate sopra la dashboard
        // (era: setContentView(view);)
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        MonitorView.FanOverlay ov = new MonitorView.FanOverlay(this);
        view.overlay = ov;
        root.addView(view);
        root.addView(ov);
        blackout = new View(this);                       // v0.6.0
        blackout.setBackgroundColor(0xff000000);
        blackout.setVisibility(View.GONE);
        blackout.setOnClickListener(new View.OnClickListener() {   // tocco = riaccendi
            public void onClick(View v) { dim(false); PresenceService.lastPresenceAt = SystemClock.uptimeMillis(); }
        });
        root.addView(blackout);
        setContentView(root);
        applyIntent(getIntent());
        // v0.9.0: gauge check a ogni accensione dello schermo
        registerReceiver(screenOn, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        applyIntent(i);
        restartPoller();                      // nuova configurazione subito attiva
    }

    // Legge gli extra di "am start" e li salva nelle preferenze
    private void applyIntent(Intent i) {
        if (i == null) return;
        SharedPreferences.Editor e = prefs.edit();
        if (i.hasExtra("url")) e.putString("wifi_url", trimUrl(i.getStringExtra("url")));
        if (i.hasExtra("lang")) { e.putString("lang", i.getStringExtra("lang")); L.set(i.getStringExtra("lang")); }   // v0.10.0
        if (i.hasExtra("home")) setHome(this, i.getBooleanExtra("home", false));                                   // v0.10.0
        if (i.hasExtra("poll_ms")) e.putInt("poll_ms", Math.max(500, i.getIntExtra("poll_ms", 1000)));
        if (i.hasExtra("page_sec")) e.putInt("page_sec", Math.max(0, i.getIntExtra("page_sec", DEF_PAGE_SEC)));
        if (i.hasExtra("gpu_busy_pct")) e.putInt("gpu_busy_pct", i.getIntExtra("gpu_busy_pct", 30));
        if (i.hasExtra("gpu_busy_sec")) e.putInt("gpu_busy_sec", i.getIntExtra("gpu_busy_sec", 60));
        if (i.hasExtra("presence_off_sec")) e.putInt("presence_off_sec", Math.max(10, i.getIntExtra("presence_off_sec", 120)));   // v0.6.0
        if (i.hasExtra("presence_sens")) e.putInt("presence_sens", Math.max(1, i.getIntExtra("presence_sens", 20)));
        for (String k : new String[]{"th_cpu", "th_gpu", "th_ram", "th_vram", "alarm_def"})   // v0.8.0
            if (i.hasExtra(k)) e.putInt(k, i.getIntExtra(k, 0));
        // 2026-10-22 v0.2.0: oltre ad "autostart" si accettano tutti gli interruttori del
        // pannello impostazioni (src_usb, src_wifi, p_cpu, p_gpu, ...), es. --ez p_gpu false
        // (riga v0.1.0 sostituita:
        //  if (i.hasExtra("autostart")) e.putBoolean("autostart", i.getBooleanExtra("autostart", true));)
        for (String k : BOOL_KEYS)
            if (i.hasExtra(k)) e.putBoolean(k, i.getBooleanExtra(k, true));
        e.apply();
    }

    private static String trimUrl(String u) {
        if (u == null) return "";
        u = u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = this;
        hideBars();
        restartPoller();
        // v0.6.0: servizio di presenza acceso/spento secondo l'opzione (serve il permesso CAMERA)
        // v0.8.0: il servizio serve per la presenza (con permesso CAMERA) oppure per gli allarmi.
        // Versione v0.6.0: solo presence && CAMERA
        Intent ps = new Intent(this, PresenceService.class);
        // v0.10.0: checkSelfPermission esiste da API 23; sotto, il permesso e' dato all'installazione
        boolean cam = prefs.getBoolean("presence", false) && hasCamera(this);
        if (!cam && prefs.getBoolean("presence", false)) prefs.edit().putBoolean("presence", false).apply();
        if (cam || prefs.getBoolean("alarm_on", false)) startService(ps);
        else stopService(ps);
    }

    // v0.10.0: modalita' Home (kiosk). L'app attiva/disattiva da se' il proprio HomeAlias: da
    // Android 11 circa "adb shell pm enable <componente>" e' vietato alla shell per le app non
    // debuggable (SecurityException vista sull'emulatore API 34). Poi la Home va scelta:
    // adb "cmd role add-role-holder android.app.role.HOME" (API 29+) o il selettore di sistema.
    static void setHome(android.content.Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(
                new android.content.ComponentName(c, "it.tab2revamp.monitor.HomeAlias"),
                on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                   : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP);
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("home", on).apply();
    }

    // v0.10.0: permesso fotocamera, compatibile con API 21-22 (permessi concessi all'installazione)
    static boolean hasCamera(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT < 23
                || c.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private final android.content.BroadcastReceiver screenOn =new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context c, Intent i) { view.gaugeCheck(); }
    };

    @Override
    protected void onDestroy() {
        unregisterReceiver(screenOn);                       // v0.9.0
        super.onDestroy();
    }

    // v0.6.0: oscuramento di ripiego (senza amministratore dispositivo): velo nero + luminosita'
    // minima della finestra; chiamato da PresenceService sul thread UI
    static void dim(boolean on) {
        MainActivity a = current;
        if (a == null) return;
        a.blackout.setVisibility(on ? View.VISIBLE : View.GONE);
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        lp.screenBrightness = on ? 0f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        a.getWindow().setAttributes(lp);
        if (on) a.stopPoller(); else if (a.poller == null) a.restartPoller();
        if (!on) a.view.gaugeCheck();                       // v0.9.0
    }

    @Override
    protected void onPause() {
        if (current == this) current = null;            // v0.6.0
        stopPoller();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();             // dopo uno swipe le barre si richiudono
    }

    private void hideBars() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void restartPoller() {
        stopPoller();
        // 2026-10-22 v0.2.0: ogni sorgente si puo' spegnere dal pannello impostazioni.
        // Versione v0.1.0 sostituita:
        //   String wifi = prefs.getString("wifi_url", "");
        //   String[] urls = wifi.length() > 0 ? new String[]{USB_URL, wifi} : new String[]{USB_URL};
        String wifi = prefs.getString("wifi_url", "");
        boolean usb = prefs.getBoolean("src_usb", true);
        boolean wf = prefs.getBoolean("src_wifi", true) && wifi.length() > 0;
        String[] urls = usb && wf ? new String[]{USB_URL, wifi}
                : usb ? new String[]{USB_URL} : wf ? new String[]{wifi} : new String[0];
        boolean[] on = new boolean[MonitorView.PANEL_KEYS.length];
        for (int i = 0; i < on.length; i++) on[i] = prefs.getBoolean(MonitorView.PANEL_KEYS[i], true);
        // 2026-10-22 v0.4.0 (era: view.setConfig(urls, on);)
        view.setAnim(prefs.getBoolean("anim_smooth", true), prefs.getInt("poll_ms", 1000));   // v0.9.0
        view.setGaugeCheck(prefs.getBoolean("gauge_check", true));
        view.setConfig(urls, on, prefs.getInt("page_sec", DEF_PAGE_SEC), prefs.getBoolean("anim_fans", true),
                prefs.getInt("gpu_busy_pct", 30), prefs.getInt("gpu_busy_sec", 60));
        // v0.10.0: con il Wi-Fi attivo ma senza URL si parte lo stesso: il poller cerca il server
        // sulla rete (Discovery). Versione v0.9.0:
        //   if (urls.length == 0) return;          // nessuna sorgente: niente thread, solo il messaggio
        //   poller = new Poller(view, urls, prefs.getInt("poll_ms", 1000));
        boolean disc = prefs.getBoolean("src_wifi", true);
        view.discovery = disc;
        if (urls.length == 0 && !disc) return; // nessuna sorgente: niente thread, solo il messaggio
        poller = new Poller(view, urls, prefs.getInt("poll_ms", 1000), disc ? getApplicationContext() : null, usb);
        poller.start();
    }

    private void stopPoller() {
        if (poller != null) { poller.quit(); poller = null; }
    }

    // -------------------------------------------------------------------------
    // Thread di polling: una richiesta alla volta (come la dashboard web),
    // timeout brevi, buffer di lettura riusato per non creare garbage.
    // -------------------------------------------------------------------------
    static final class Poller extends Thread {
        private final MonitorView view;
        private String[] urls;                                 // v0.10.0: non final (scoperta)
        private final int pollMs;
        private volatile boolean running = true;
        private int good = 0;                                  // indice dell'ultimo URL ok
        private final byte[] buf = new byte[8192];
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream(16384);
        // v0.10.0: scoperta automatica (ctx == null = disattivata)
        private final android.content.Context ctx;
        private final boolean usb;
        private int fails = 0;
        private long lastDisc = -60000;
        static final int DISC_AFTER_FAILS = 3;                 // ~10 s senza risposta (3 s a tentativo)
        static final long DISC_EVERY_MS = 30000;

        Poller(MonitorView v, String[] u, int ms, android.content.Context c, boolean usbOn) {
            super("Tab2Poller");
            view = v; urls = u; pollMs = ms; ctx = c; usb = usbOn;
        }

        // v0.10.0: nessuna sorgente risponde -> broadcast UDP. Si accetta il server con lo stesso
        // nome di quello salvato (cambio di IP) oppure, se nessun nome e' salvato, l'unico trovato.
        // Con piu' server e nessun nome salvato si sceglie dalle impostazioni ("Cerca server").
        private void discover() {
            lastDisc = SystemClock.uptimeMillis();
            SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
            String name = p.getString("server_name", "");
            java.util.ArrayList<Discovery.Server> found = Discovery.find(ctx, 1500);
            Discovery.Server pick = null;
            for (Discovery.Server s : found) if (name.length() > 0 && s.name.equals(name)) pick = s;
            if (pick == null && name.length() == 0 && found.size() == 1) pick = found.get(0);
            if (pick == null) return;
            p.edit().putString("wifi_url", pick.url).putString("server_name", pick.name).apply();
            urls = usb ? new String[]{USB_URL, pick.url} : new String[]{pick.url};
            good = urls.length - 1;
        }

        void quit() { running = false; interrupt(); }

        @Override
        public void run() {
            while (running) {
                long t0 = SystemClock.uptimeMillis();
                Metrics m = null;
                String src = null;
                for (int k = 0; k < urls.length && running && m == null; k++) {
                    int i = (good + k) % urls.length;
                    try {
                        m = Metrics.parse(new JSONObject(get(urls[i] + "/api/metrics")));
                        good = i; src = urls[i];
                    } catch (Exception ignored) { /* prova l'URL successivo */ }
                }
                if (!running) break;
                fails = m != null ? 0 : fails + 1;        // v0.10.0
                if (m == null && ctx != null && (fails >= DISC_AFTER_FAILS || urls.length == 0)
                        && SystemClock.uptimeMillis() - lastDisc >= DISC_EVERY_MS) {
                    try { discover(); } catch (Exception ignored) { }
                    if (!running) break;
                }
                if (m != null) lastMetrics = m;           // v0.8.0
                final Metrics fm = m; final String fs = src;
                view.post(new Runnable() { public void run() { view.update(fm, fs); } });
                // se il server non risponde si riprova piu' piano (3 s)
                long wait = (m != null ? pollMs : 3000) - (SystemClock.uptimeMillis() - t0);
                if (wait > 0) {
                    try { Thread.sleep(wait); } catch (InterruptedException e) { break; }
                }
            }
        }

        private String get(String u) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(2500);
            c.setUseCaches(false);
            try {
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                InputStream in = c.getInputStream();
                bos.reset();
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();                    // keep-alive: la connessione viene riusata
                return bos.toString("UTF-8");
            } catch (Exception e) {
                c.disconnect();
                throw e;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Metriche gia' convertite in tipi semplici (stesso schema di /api/metrics,
    // vedi windows/agent/tab2monitor_server_*.py). NaN = valore assente.
    // -------------------------------------------------------------------------
    static final class Metrics {
        String host = "--";
        long uptime;
        float cpu, mhz, ram, ramUsed, ramTot, swap;
        int[] cores = new int[0];
        String gpuName; float gpuLoad = Float.NaN, gpuTemp = Float.NaN, gpuMem = Float.NaN;
        String[] tempName = new String[0]; float[] tempC = new float[0];
        long rx, tx, dr, dw;
        String[] diskM = new String[0]; float[] diskPct = new float[0]; float[] diskTot = new float[0];
        long[] diskR = new long[0], diskW = new long[0];   // v0.4.0: I/O per disco (-1 = assente)
        boolean diskPerIo;                                 // v0.4.0: il server invia r/w per disco
        String[] procN = new String[0]; float[] procCpu = new float[0]; int[] procMb = new int[0];
        Claude claude;                                     // v0.3.0: null se il server non lo invia
        Gpu[] gpus = new Gpu[0];                           // v0.4.0
        Hw[] hw = new Hw[0];                               // v0.4.0
        Box[] boxes = new Box[0];                          // v0.5.0: un box per hardware
        float cpuTemp = Float.NaN;                         // v0.7.0: es. "Core (Tctl/Tdie)"

        // Utilizzo Claude (schema concordato con la sessione "Sorgente dati Windows");
        // -1 = campo assente
        static final class Claude {
            long todayTok = -1, todayOut = -1, todayCacheR = -1, weekTok = -1, blockTok = -1;
            long blockStart, blockEnd;
            int todayMsgs;
            String topModel; long topModelTok = -1;
        }

        // v0.5.1: temperatura da mostrare = misura vera e non nascosta dall'utente
        static boolean shown(JSONObject x) {
            return x != null && !x.optBoolean("hidden", false) && isTemp(x.optString("name"));
        }

        static boolean isTemp(String n) {                  // v0.4.1
            String l = n.toLowerCase();
            // anche "Warning/Critical Temperature" degli SSD NVMe: soglie, non misure
            return !(l.contains("limit") || l.contains("resolution") || l.startsWith("warning") || l.startsWith("critical"));
        }

        static float f(JSONObject o, String k) { return o == null ? Float.NaN : (float) o.optDouble(k); }

        static Metrics parse(JSONObject j) {
            Metrics m = new Metrics();
            m.host = j.optString("host", "--");
            m.uptime = j.optLong("uptime_s");
            JSONObject c = j.optJSONObject("cpu");
            m.cpu = f(c, "pct"); m.mhz = f(c, "mhz");
            JSONArray ca = c != null ? c.optJSONArray("cores") : null;
            if (ca != null) {
                m.cores = new int[ca.length()];
                for (int i = 0; i < m.cores.length; i++) m.cores[i] = ca.optInt(i);
            }
            JSONObject r = j.optJSONObject("ram");
            m.ram = f(r, "pct"); m.ramUsed = f(r, "used_gb"); m.ramTot = f(r, "tot_gb");
            m.swap = f(j.optJSONObject("swap"), "pct");
            JSONObject n = j.optJSONObject("net");
            if (n != null) { m.rx = n.optLong("rx"); m.tx = n.optLong("tx"); }
            JSONObject d = j.optJSONObject("disk_io");
            if (d != null) { m.dr = d.optLong("r"); m.dw = d.optLong("w"); }

            JSONArray ds = j.optJSONArray("disks");
            if (ds != null) {
                int k = ds.length();
                m.diskM = new String[k]; m.diskPct = new float[k]; m.diskTot = new float[k];
                m.diskR = new long[k]; m.diskW = new long[k];
                for (int i = 0; i < k; i++) {
                    JSONObject o = ds.optJSONObject(i);
                    m.diskM[i] = o.optString("m"); m.diskPct[i] = f(o, "pct"); m.diskTot[i] = f(o, "tot_gb");
                    m.diskR[i] = o.optLong("r", -1); m.diskW[i] = o.optLong("w", -1);   // v0.4.0
                    if (m.diskR[i] >= 0 || m.diskW[i] >= 0) m.diskPerIo = true;
                }
            }
            JSONArray ps = j.optJSONArray("procs");
            if (ps != null) {
                int k = ps.length();
                m.procN = new String[k]; m.procCpu = new float[k]; m.procMb = new int[k];
                for (int i = 0; i < k; i++) {
                    JSONObject o = ps.optJSONObject(i);
                    m.procN[i] = o.optString("n"); m.procCpu[i] = f(o, "cpu"); m.procMb[i] = o.optInt("mb");
                }
            }
            JSONObject cl = j.optJSONObject("claude");         // v0.3.0
            if (cl != null) {
                Claude k = new Claude();
                k.todayTok = cl.optLong("today_tok", -1); k.todayOut = cl.optLong("today_out", -1);
                k.todayCacheR = cl.optLong("today_cache_r", -1); k.weekTok = cl.optLong("week_tok", -1);
                k.todayMsgs = cl.optInt("today_msgs");
                k.blockTok = cl.optLong("block_tok", -1);
                k.blockStart = cl.optLong("block_start"); k.blockEnd = cl.optLong("block_end");
                JSONArray ms = cl.optJSONArray("models");
                JSONObject m0 = ms != null && ms.length() > 0 ? ms.optJSONObject(0) : null;
                if (m0 != null) { k.topModel = m0.optString("m"); k.topModelTok = m0.optLong("tok", -1); }
                m.claude = k;
            }
            JSONObject l = j.optJSONObject("lhm");             // null se LHM non attivo
            if (l != null) {
                m.gpuName = l.isNull("gpu_name") ? null : l.optString("gpu_name");
                m.gpuLoad = f(l, "gpu_load"); m.gpuTemp = f(l, "gpu_temp"); m.gpuMem = f(l, "gpu_mem_load");
                JSONArray t = l.optJSONArray("temps");
                if (t != null) {
                    int k = t.length();
                    m.tempName = new String[k]; m.tempC = new float[k];
                    for (int i = 0; i < k; i++) {
                        JSONObject o = t.optJSONObject(i);
                        m.tempName[i] = o.optString("name"); m.tempC[i] = f(o, "c");
                    }
                }
            }
            // v0.4.0: tutte le GPU (server >= v0.2.0). Server vecchi: una GPU dai campi "lhm".
            JSONArray gs = j.optJSONArray("gpus");
            if (gs != null) {
                int k = gs.length();
                m.gpus = new Gpu[k];
                for (int i = 0; i < k; i++) {
                    JSONObject o = gs.optJSONObject(i);
                    Gpu g = new Gpu();
                    g.name = o.optString("name", "GPU " + (i + 1));
                    g.load = f(o, "load"); g.memMb = f(o, "mem_mb"); g.memTotMb = f(o, "mem_tot_mb");
                    g.memPct = f(o, "mem_pct"); g.temp = f(o, "temp"); g.fanRpm = f(o, "fan_rpm");
                    g.powerPct = f(o, "power_pct");
                    m.gpus[i] = g;
                }
            } else if (!Float.isNaN(m.gpuLoad)) {
                Gpu g = new Gpu();
                g.name = m.gpuName != null ? m.gpuName : "GPU";
                g.load = m.gpuLoad; g.temp = m.gpuTemp; g.memPct = m.gpuMem;
                m.gpus = new Gpu[]{g};
            }
            // v0.4.0: sensori per componente (temperature + ventole). Senza "sensors"
            // si ripiega sulle temperature piatte di "lhm".
            JSONObject se = j.optJSONObject("sensors");
            JSONArray hw = se != null ? se.optJSONArray("hw") : null;
            if (hw != null) {
                int k = hw.length();
                m.hw = new Hw[k];
                for (int i = 0; i < k; i++) {
                    JSONObject o = hw.optJSONObject(i);
                    Hw h = new Hw();
                    h.name = o.optString("name"); h.kind = o.optString("kind");
                    JSONArray t = o.optJSONArray("temps");
                    int nt = t != null ? t.length() : 0;
                    // v0.4.1: "Thermal Sensor High Limit", "... Critical High Limit",
                    // "Temperature Sensor Resolution" (moduli RAM) sono costanti, non misure:
                    // si scartano per lasciare spazio alle temperature vere.
                    // (v0.4.0 le copiava tutte: h.tName = new String[nt]; ... for q < nt ...)
                    int keep = 0;
                    // v0.5.1: anche "hidden": true -> scartata (vedi shown())
                    for (int q = 0; q < nt; q++) if (shown(t.optJSONObject(q))) keep++;
                    h.tName = new String[keep]; h.tC = new float[keep];
                    for (int q = 0, z = 0; q < nt; q++) {
                        JSONObject x = t.optJSONObject(q);
                        if (!shown(x)) continue;                 // v0.5.1 (era: !isTemp(name))
                        h.tName[z] = x.optString("name"); h.tC[z] = f(x, "c"); z++;
                    }
                    JSONArray fa = o.optJSONArray("fans");
                    int nf0 = fa != null ? fa.length() : 0, nf = 0;
                    // v0.5.1: ventole con "hidden": true saltate (prima: tutte, nf = fa.length())
                    for (int q = 0; q < nf0; q++) if (!fa.optJSONObject(q).optBoolean("hidden", false)) nf++;
                    h.fName = new String[nf]; h.fRpm = new float[nf]; h.fPct = new float[nf];
                    for (int q = 0, z = 0; q < nf0; q++) {
                        JSONObject x = fa.optJSONObject(q);
                        if (x.optBoolean("hidden", false)) continue;
                        h.fName[z] = x.optString("name"); h.fRpm[z] = f(x, "rpm"); h.fPct[z] = f(x, "pct"); z++;
                    }
                    m.hw[i] = h;
                }
            } else if (m.tempName.length > 0) {
                Hw h = new Hw();
                h.name = "LibreHardwareMonitor"; h.kind = "";
                h.tName = m.tempName; h.tC = m.tempC;
                h.fName = new String[0]; h.fRpm = new float[0]; h.fPct = new float[0];
                m.hw = new Hw[]{h};
            }
            buildBoxes(m, se != null ? se.optJSONArray("pairs") : null);   // v0.5.0
            return m;
        }

        // ---------------------------------------------------------------------
        // v0.5.0: raggruppa temperature e ventole in box per hardware (fatto qui, nel thread
        // di polling, cosi' onDraw non alloca nulla).
        //  - GPU (kind "gpu" con lo stesso nome di una voce di "gpus"): i sensori vanno nella
        //    scheda della GPU; senza corrispondenza diventano un box normale
        //  - CPU -> "CPU - <nome>", scheda madre -> "Scheda madre - <nome>"
        //  - RAM (kind "ram", o "other" con temperature "DIMM ...") -> un solo box "RAM"
        //  - dischi (kind "disk") -> un solo box "Dischi" (riga = nome del disco)
        //  - tutto il resto -> un box con il nome dell'hardware
        // Ventole: se sensors.pairs collega la ventola a una temperatura di un ALTRO hardware
        // (es. ventola della scheda madre -> CPU), la ventola va nel box di quell'hardware.
        static void buildBoxes(Metrics m, JSONArray pairs) {
            HashMap<String, String> fanTo = new HashMap<String, String>();     // "hw|fan" -> hw temp
            if (pairs != null) for (int i = 0; i < pairs.length(); i++) {
                JSONObject p = pairs.optJSONObject(i);
                if (p == null) continue;
                String fh = p.optString("fan_hw"), th = p.optString("temp_hw");
                if (th.length() > 0 && !th.equals(fh)) fanTo.put(fh + "|" + p.optString("fan"), th);
            }
            LinkedHashMap<String, Box> byKey = new LinkedHashMap<String, Box>();
            HashMap<String, Object> owner = new HashMap<String, Object>();       // nome hw -> Box o Gpu
            // ordine dei box: CPU, scheda madre, RAM, dischi, altri
            String[] order = {"cpu", "mb", "ram", "disk", ""};
            for (String ord : order) for (Hw h : m.hw) {
                String k = group(h);
                if (!k.equals(ord) && !(ord.length() == 0 && !k.equals("cpu") && !k.equals("mb")
                        && !k.equals("ram") && !k.equals("disk"))) continue;
                Gpu g = "gpu".equals(h.kind) ? gpuNamed(m, h.name) : null;
                if (g != null) { owner.put(h.name, g); continue; }
                // v0.10.0: titoli tradotti (L.t); la chiave interna resta "Dischi"
                String key = k.equals("ram") ? "RAM" : k.equals("disk") ? "Dischi" : h.name;
                Box b = byKey.get(key);
                if (b == null) {
                    b = new Box();
                    b.title = k.equals("cpu") ? "CPU - " + h.name : k.equals("mb") ? L.t("Scheda madre - ", "Motherboard - ") + h.name
                            : key.equals("RAM") ? key : key.equals("Dischi") ? L.t("Dischi", "Disks") : h.name;
                    byKey.put(key, b);
                }
                owner.put(h.name, b);
            }
            // temperature
            for (Hw h : m.hw) {
                Object o = owner.get(h.name);
                for (int q = 0; q < h.tName.length; q++) {
                    String lab = h.tName[q];
                    if ("disk".equals(group(h)))                 // "Dischi": riga = nome del disco
                        lab = q == 0 ? h.name : "  " + h.tName[q];
                    if (o instanceof Gpu) ((Gpu) o).add(false, lab, h.tC[q], Float.NaN);
                    else if (o instanceof Box) ((Box) o).add(false, lab, h.tC[q], Float.NaN);
                }
            }
            // ventole, eventualmente spostate nel box dell'hardware collegato
            for (Hw h : m.hw) for (int q = 0; q < h.fName.length; q++) {
                String to = fanTo.get(h.name + "|" + h.fName[q]);
                Object o = to != null && owner.containsKey(to) ? owner.get(to) : owner.get(h.name);
                boolean moved = to != null && owner.containsKey(to);
                String lab = moved && "mb".equals(h.kind) ? h.fName[q] + L.t(" (scheda madre)", " (motherboard)") : h.fName[q];   // v0.10.0: IT/EN
                if (o instanceof Gpu) ((Gpu) o).add(true, lab, h.fRpm[q], h.fPct[q]);
                else if (o instanceof Box) ((Box) o).add(true, lab, h.fRpm[q], h.fPct[q]);
            }
            for (Hw h : m.hw)                              // v0.7.0
                if ("cpu".equals(h.kind) && h.tC.length > 0 && Float.isNaN(m.cpuTemp)) m.cpuTemp = h.tC[0];
            ArrayList<Box> out = new ArrayList<Box>();
            for (Box b : byKey.values()) if (b.n() > 0) { b.seal(); out.add(b); }
            m.boxes = out.toArray(new Box[out.size()]);
            for (Gpu g : m.gpus) g.seal();
        }

        static String group(Hw h) {
            String k = h.kind == null ? "" : h.kind;
            if (k.equals("other") && h.tName.length > 0 && h.tName[0].startsWith("DIMM")) return "ram";
            return k;
        }

        static Gpu gpuNamed(Metrics m, String name) {
            for (Gpu g : m.gpus) if (g.name.equals(name)) return g;
            return null;
        }

        // Righe di un box (temperatura o ventola), accumulate in liste e poi "sigillate" in array
        static class Rows {
            final ArrayList<Object[]> tmp = new ArrayList<Object[]>();
            boolean[] fan = new boolean[0]; String[] lab = new String[0];
            float[] val = new float[0], pct = new float[0];
            void add(boolean isFan, String l, float v, float p) { tmp.add(new Object[]{isFan, l, v, p}); }
            int n() { return tmp.size() + lab.length; }
            int fans() { int c = 0; for (boolean b : fan) if (b) c++; return c; }
            void seal() {
                int k = tmp.size();
                fan = new boolean[k]; lab = new String[k]; val = new float[k]; pct = new float[k];
                for (int i = 0; i < k; i++) {                       // prima le temperature, poi le ventole
                    Object[] r = tmp.get(i);
                    fan[i] = (Boolean) r[0]; lab[i] = (String) r[1]; val[i] = (Float) r[2]; pct[i] = (Float) r[3];
                }
                tmp.clear();
            }
        }

        static final class Box extends Rows { String title; }

        // v0.4.0: GPU (campo "gpus") e componenti con sensori (campo "sensors.hw")
        static final class Gpu extends Rows {               // v0.5.0: + righe dei sensori
            String name;
            float load, memMb = Float.NaN, memTotMb = Float.NaN, memPct = Float.NaN;
            float temp = Float.NaN, fanRpm = Float.NaN, powerPct = Float.NaN;
        }

        static final class Hw {
            String name, kind;
            String[] tName; float[] tC;
            String[] fName; float[] fRpm, fPct;
        }
    }
}
