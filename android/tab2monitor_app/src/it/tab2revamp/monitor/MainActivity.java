// =============================================================================
// Tab2Monitor - MainActivity + thread di polling
// Versione: v0.1.0 - 2026-10-21 23:50
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
// Configurazione da PC (niente schermate di impostazioni = meno codice e RAM):
//   adb shell am start -n it.tab2revamp.monitor/.MainActivity \
//       --es url http://192.168.1.237:8090   (URL Wi-Fi; "" per toglierlo)
//       --ei poll_ms 2000                    (intervallo di polling)
//       --ez autostart false                 (disattiva l'avvio al boot)
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    static final String PREFS = "tab2monitor";
    static final String USB_URL = "http://127.0.0.1:8090";

    private MonitorView view;
    private Poller poller;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, 0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new MonitorView(this);
        setContentView(view);
        applyIntent(getIntent());
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
        if (i.hasExtra("poll_ms")) e.putInt("poll_ms", Math.max(500, i.getIntExtra("poll_ms", 1000)));
        if (i.hasExtra("autostart")) e.putBoolean("autostart", i.getBooleanExtra("autostart", true));
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
        hideBars();
        restartPoller();
    }

    @Override
    protected void onPause() {
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
        String wifi = prefs.getString("wifi_url", "");
        String[] urls = wifi.length() > 0 ? new String[]{USB_URL, wifi} : new String[]{USB_URL};
        view.setUrls(urls);
        poller = new Poller(view, urls, prefs.getInt("poll_ms", 1000));
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
        private final String[] urls;
        private final int pollMs;
        private volatile boolean running = true;
        private int good = 0;                                  // indice dell'ultimo URL ok
        private final byte[] buf = new byte[8192];
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream(16384);

        Poller(MonitorView v, String[] u, int ms) {
            super("Tab2Poller");
            view = v; urls = u; pollMs = ms;
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
        String[] procN = new String[0]; float[] procCpu = new float[0]; int[] procMb = new int[0];

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
                for (int i = 0; i < k; i++) {
                    JSONObject o = ds.optJSONObject(i);
                    m.diskM[i] = o.optString("m"); m.diskPct[i] = f(o, "pct"); m.diskTot[i] = f(o, "tot_gb");
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
            return m;
        }
    }
}
