// =============================================================================
// Tab2Monitor - PresenceService (presenza con la fotocamera frontale)
// Versione: v0.14.1 - 2026-11-19 14:00
//   v0.14.1: anche lo schermo del PC spento (campo srv.display_on = false in /api/metrics per piu' di 5 s)
//           spegne il tablet come il PC spento; al ritorno a true lo riaccende.
// v0.14.0 - 2026-11-19 13:00
//   v0.14.0: PC SPENTO = schermo spento e rilevamento movimento fermo. Se il server non risponde per
//           OFFLINE_SEC secondi (opzione "pc_sleep", default acceso) spegne lo schermo, chiude la fotocamera
//           e non si riaccende per movimento. Quando il server torna (una risposta a /api/metrics oppure il
//           pacchetto UDP "TAB2MON-UP" che il server invia in broadcast alla partenza, porta 8092)
//           riaccende lo schermo e riapre la fotocamera. A server assente cerca il PC in rete (UDP 8091)
//           come fa l'activity, perche' a schermo spento l'activity non gira.
// v0.8.0 - 2026-10-22 02:45
//   v0.8.0: (1) il servizio legge da solo /api/metrics ogni 2 s (anche a schermo spento):
//           CARICO oltre soglia (CPU, GPU, RAM, VRAM) = "presenza" -> riaccende e tiene acceso
//           finche' il carico resta; (2) ALLARME VOCALE (TextToSpeech, voce italiana Pico) quando
//           una temperatura supera la sua soglia (per sensore, o soglia generale), ripetuto ogni
//           60 s finche' resta sopra (isteresi 3 gradi), con riaccensione dello schermo.
//           Il servizio gira se e' attiva la presenza OPPURE gli allarmi; la fotocamera solo
//           con la presenza.
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Opzione "presence" (impostazioni, spenta di default). Servizio in primo piano che:
//   - apre la fotocamera FRONTALE (S5K6A1 sul GT-P5100) a 160x120, 5 fps, con una
//     SurfaceTexture fittizia (nessuna anteprima a video);
//   - "presenza" = MOVIMENTO (differenza tra fotogrammi sulla luminanza, campionata
//     ogni 4 pixel: 40x30 punti) oppure VOLTO (rilevamento volti hardware dell'HAL TI);
//   - dopo "presence_off_sec" secondi senza presenza spegne lo schermo:
//       * modo reale: DevicePolicyManager.lockNow() (serve l'app "amministratore
//         dispositivo": AdminReceiver, attivabile da Impostazioni o con
//         adb shell dpm set-active-admin it.tab2revamp.monitor/.AdminReceiver)
//       * modo ripiego: schermo nero + luminosita' minima (MainActivity.setDim)
//   - alla prima presenza riaccende (wakelock ACQUIRE_CAUSES_WAKEUP) e riporta la dashboard.
//   - anti falso allarme (provato 2026-10-22: spegnendo lo schermo cambia la luce della stanza
//     e l'esposizione automatica "vede" movimento -> si riaccendeva dopo 5 s): per GRACE_MS dopo
//     lo spegnimento il movimento e' ignorato (i volti no) e servono 2 fotogrammi consecutivi.
// Un PARTIAL_WAKE_LOCK tiene viva la CPU a schermo spento (il tablet e' sempre in carica).
// I callback della fotocamera arrivano su un HandlerThread, non sul thread UI.
// =============================================================================
package it.tab2revamp.monitor;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")       // android.hardware.Camera: l'HAL TI e' "Camera1" (API 1)
public class PresenceService extends Service {
    static final String TAG = "Tab2Presence";
    static final int W = 160, H = 120, STEP = 4;          // risoluzione e passo di campionamento
    static final int PIX_DIFF = 25;                       // differenza di luminanza "cambiata"
    static final long GRACE_MS = 5000;                    // dopo lo spegnimento: niente movimento
    static final int HITS = 2;                            // fotogrammi consecutivi con movimento

    // Stato letto dalla schermata impostazioni (diagnostica)
    static volatile String status = "spento";
    static volatile long lastPresenceAt;

    private HandlerThread thread;
    private Handler h;
    private Camera cam;
    private SurfaceTexture tex;
    private byte[] buf, prev;
    private PowerManager pm;
    private PowerManager.WakeLock cpuLock;
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private int offSec = 120;
    private float sens = 2f;                             // % di punti cambiati per dire "movimento"
    private boolean screenOffByUs;
    // v0.8.0: carico, allarmi, polling proprio
    private boolean presenceOn, loadWake, alarmOn;
    private int thCpu = 60, thGpu = 50, thRam = 90, thVram = 90, alarmDef = 85;
    private String[] urls = new String[0];
    private HandlerThread netThread;
    private Handler net;
    private TextToSpeech tts;
    private boolean ttsReady;
    private final HashMap<String, Long> alarmAt = new HashMap<String, Long>();   // ultima voce per sensore
    static volatile String loadStatus = "";
    static volatile MainActivity.Metrics last;            // ultimo campione (per le impostazioni)
    private volatile long graceUntil;                    // uptimeMillis fino a cui ignorare il movimento
    private int hits;                                    // fotogrammi consecutivi con movimento
    private final Handler main = new Handler(Looper.getMainLooper());
    // v0.14.0: PC spento
    static final long OFFLINE_SEC = 30;                   // senza risposta per tanto = PC spento
    static final int WAKE_PORT = 8092;                    // il server vi invia "TAB2MON-UP" quando parte
    private boolean pcSleep = true;
    private volatile boolean pcOff;                       // vero = abbiamo spento tutto perche' il PC non c'e'
    private volatile long lastOk;                         // uptimeMillis dell'ultima risposta del server
    private int pollFails;
    private long lastDisc;
    private SharedPreferences prefs;
    private Thread udp;
    private volatile boolean udpRun;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private android.net.wifi.WifiManager.MulticastLock mcLock;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tab2monitor:presence");
        cpuLock.acquire();
        prefs = getSharedPreferences(MainActivity.PREFS, 0);
        lastOk = SystemClock.uptimeMillis();
        try {                                              // v0.14.0: Wi-Fi sveglio a schermo spento + ricezione broadcast
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tab2monitor:wifi");
            wifiLock.acquire();
            mcLock = wm.createMulticastLock("tab2monitor:mc");
            mcLock.acquire();
        } catch (Exception e) { Log.w(TAG, "wifi lock", e); }
        udpRun = true;
        udp = new Thread(new Runnable() { public void run() { udpLoop(); } }, "Tab2WakeUdp");
        udp.start();
        // notifica obbligatoria per un servizio in primo piano
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic)
                .setContentTitle("Tab2Monitor - rilevamento presenza")
                .setContentText("Presenza, allarmi e schermo (spento se il PC e' spento)")
                .setContentIntent(pi).setOngoing(true).build();
        startForeground(1, n);
        thread = new HandlerThread("Tab2Camera");
        thread.start();
        h = new Handler(thread.getLooper());
        lastPresenceAt = SystemClock.uptimeMillis();
        // v0.8.0: la fotocamera si apre in onStartCommand solo se la presenza e' attiva
        // (era: h.post(openCamera) sempre)
        h.postDelayed(tick, 1000);
        netThread = new HandlerThread("Tab2SvcNet");
        netThread.start();
        net = new Handler(netThread.getLooper());
        net.postDelayed(poll, 2000);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, 0);
        offSec = Math.max(10, p.getInt("presence_off_sec", 120));
        sens = Math.max(0.2f, p.getInt("presence_sens", 20) / 10f);   // decimi di %
        // v0.8.0
        presenceOn = p.getBoolean("presence", false);
        loadWake = p.getBoolean("load_wake", true);
        alarmOn = p.getBoolean("alarm_on", false);
        thCpu = p.getInt("th_cpu", 60); thGpu = p.getInt("th_gpu", 50);
        thRam = p.getInt("th_ram", 90); thVram = p.getInt("th_vram", 90);
        alarmDef = p.getInt("alarm_def", 85);
        pcSleep = p.getBoolean("pc_sleep", true);      // v0.14.0
        String wifi = p.getString("wifi_url", "");
        boolean usb = p.getBoolean("src_usb", true), wf = p.getBoolean("src_wifi", true) && wifi.length() > 0;
        urls = usb && wf ? new String[]{MainActivity.USB_URL, wifi}
                : usb ? new String[]{MainActivity.USB_URL} : wf ? new String[]{wifi} : new String[0];
        h.post(new Runnable() { public void run() {
            if (presenceOn && cam == null && !pcOff) openCamera();      // v0.14.0: + !pcOff
            if (!presenceOn && cam != null) { closeCamera(); status = "fotocamera spenta (presenza disattivata)"; }
        } });
        if (alarmOn && tts == null) {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                public void onInit(int st) {
                    if (st == TextToSpeech.SUCCESS) {
                        tts.setLanguage(Locale.ITALIAN);
                        ttsReady = true;
                    } else Log.w(TAG, "TextToSpeech non disponibile: " + st);
                }
            });
        }
        if (p.getBoolean("alarm_test", false)) {            // pulsante "Prova voce" delle impostazioni
            p.edit().remove("alarm_test").apply();
            net.postDelayed(new Runnable() { public void run() { say("Prova allarme Tab2Monitor. Voce attiva."); } }, 1500);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        net.removeCallbacksAndMessages(null);              // v0.8.0
        netThread.quitSafely();
        if (tts != null) { tts.shutdown(); tts = null; }
        h.removeCallbacksAndMessages(null);
        h.post(new Runnable() { public void run() { closeCamera(); } });
        thread.quitSafely();
        udpRun = false;                                  // v0.14.0
        if (udp != null) udp.interrupt();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (mcLock != null && mcLock.isHeld()) mcLock.release();
        if (screenOffByUs) wake();                       // non lasciare lo schermo spento
        if (cpuLock.isHeld()) cpuLock.release();
        status = "spento";
        super.onDestroy();
    }

    // ------------------------------------------------------------- fotocamera
    private void openCamera() {
        try {
            int front = -1;
            Camera.CameraInfo ci = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, ci);
                if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { front = i; break; }
            }
            if (front < 0) { status = "nessuna fotocamera frontale"; return; }
            cam = Camera.open(front);
            Camera.Parameters p = cam.getParameters();
            p.setPreviewSize(W, H);
            p.setPreviewFormat(ImageFormat.NV21);
            List<int[]> fps = p.getSupportedPreviewFpsRange();
            if (fps != null && !fps.isEmpty()) {             // il range piu' lento (5 fps)
                int[] lo = fps.get(0);
                for (int[] r : fps) if (r[1] < lo[1]) lo = r;
                p.setPreviewFpsRange(lo[0], lo[1]);
            }
            cam.setParameters(p);
            tex = new SurfaceTexture(10);
            cam.setPreviewTexture(tex);
            buf = new byte[W * H * 3 / 2];
            cam.addCallbackBuffer(buf);
            cam.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                public void onPreviewFrame(byte[] data, Camera c) { frame(data); c.addCallbackBuffer(data); }
            });
            cam.setFaceDetectionListener(new Camera.FaceDetectionListener() {
                public void onFaceDetection(Camera.Face[] faces, Camera c) {
                    if (faces != null && faces.length > 0) presence("volto");
                }
            });
            cam.startPreview();
            if (p.getMaxNumDetectedFaces() > 0) {
                try { cam.startFaceDetection(); } catch (RuntimeException e) { Log.w(TAG, "face detection", e); }
            }
            status = "attivo";
            Log.i(TAG, "fotocamera frontale " + front + " aperta, " + W + "x" + H);
        } catch (Exception e) {
            status = "errore fotocamera: " + e.getMessage();
            Log.e(TAG, "openCamera", e);
            closeCamera();
            h.postDelayed(new Runnable() { public void run() { openCamera(); } }, 30000);   // riprova
        }
    }

    private void closeCamera() {
        if (cam != null) {
            try { cam.setPreviewCallbackWithBuffer(null); cam.stopFaceDetection(); } catch (RuntimeException ignored) { }
            try { cam.stopPreview(); } catch (RuntimeException ignored) { }
            cam.release();
            cam = null;
        }
        if (tex != null) { tex.release(); tex = null; }
    }

    // Movimento: % di punti (griglia 40x30 sulla luminanza Y) cambiati di oltre PIX_DIFF
    private void frame(byte[] d) {
        int n = 0, changed = 0;
        if (prev == null) prev = new byte[(W / STEP) * (H / STEP)];
        for (int y = 0, k = 0; y < H; y += STEP)
            for (int x = 0; x < W; x += STEP, k++) {
                int v = d[y * W + x] & 0xff;
                if (Math.abs(v - (prev[k] & 0xff)) > PIX_DIFF) changed++;
                prev[k] = (byte) v;
                n++;
            }
        // (prima stesura: if (changed * 100f / n >= sens) presence("movimento");)
        if (SystemClock.uptimeMillis() < graceUntil) { hits = 0; return; }
        hits = changed * 100f / n >= sens ? hits + 1 : 0;
        if (hits >= HITS) presence("movimento");
    }

    private void presence(String why) {
        if (pcOff) return;                               // v0.14.0: PC spento, nessuno accende lo schermo
        lastPresenceAt = SystemClock.uptimeMillis();
        if (screenOffByUs) {
            Log.i(TAG, "presenza (" + why + "): accendo");
            main.post(new Runnable() { public void run() { wake(); } });
        }
    }

    // Ogni secondo: spegne se nessuno da offSec secondi
    private final Runnable tick = new Runnable() {
        public void run() {
            long idle = (SystemClock.uptimeMillis() - lastPresenceAt) / 1000;
            if (presenceOn && !screenOffByUs && idle >= offSec) {   // v0.8.0: + presenceOn
                Log.i(TAG, "nessuno da " + idle + " s: spengo");
                main.post(new Runnable() { public void run() { sleep(); } });
            }
            if (cam != null) status = screenOffByUs ? "schermo spento, in attesa" : "attivo, nessuno da " + idle + " s";
            h.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------- carico e allarmi (v0.8.0)
    private final byte[] nbuf = new byte[8192];
    private final ByteArrayOutputStream nbos = new ByteArrayOutputStream(16384);

    private final Runnable poll = new Runnable() {
        public void run() {
            MainActivity.Metrics m = null;
            boolean dispOn = true;                                   // v0.14.1: schermo del PC acceso?
            // v0.14.0: l'URL Wi-Fi puo' essere cambiato (ricerca automatica): si rilegge a ogni ciclo
            // (era: for (String u : urls) fissato in onStartCommand)
            for (String u : currentUrls()) {
                try {
                    JSONObject jo = new JSONObject(get(u + "/api/metrics"));
                    JSONObject sv = jo.optJSONObject("srv");         // v0.14.1
                    if (sv != null) dispOn = sv.optBoolean("display_on", true);
                    m = MainActivity.Metrics.parse(jo);
                    break;
                } catch (Exception ignored) { }
                // (v0.14.0: m = MainActivity.Metrics.parse(new JSONObject(get(u + "/api/metrics"))); break;)
            }
            if (m != null) {
                last = m; pollFails = 0; lastOk = SystemClock.uptimeMillis();
                long nowU = SystemClock.uptimeMillis();
                if (!dispOn) {                                       // v0.14.1: schermo del PC spento
                    if (dispOffSince == 0) dispOffSince = nowU;
                    if (pcSleep && !pcOff && nowU - dispOffSince >= 5000) serverGone("schermo del PC spento");
                } else {
                    dispOffSince = 0;
                    if (pcOff) serverBack("risposta del server");
                }
                if (!pcOff) check(m);
            } else {
                pollFails++;
                if (pollFails >= 3 && wifiOn() && SystemClock.uptimeMillis() - lastDisc > 10000) {
                    lastDisc = SystemClock.uptimeMillis();
                    String f = MainActivity.Poller.discover();           // cerca il PC (UDP 8091)
                    if (f != null && !f.equals(prefs.getString("wifi_url", ""))) {
                        prefs.edit().putString("wifi_url", f).apply();
                        pollFails = 0;
                    }
                }
                if (pcSleep && !pcOff && (SystemClock.uptimeMillis() - lastOk) / 1000 >= OFFLINE_SEC) serverGone("server assente da " + OFFLINE_SEC + " s");
            }
            net.postDelayed(this, pcOff ? 3000 : 2000);
        }
    };

    // ------------------------------------------------------- PC spento / acceso (v0.14.0)
    private boolean wifiOn() { return prefs.getBoolean("src_wifi", true); }

    private String[] currentUrls() {
        String wifi = prefs.getString("wifi_url", "");
        boolean usb = prefs.getBoolean("src_usb", true), wf = wifiOn() && wifi.length() > 0;
        return usb && wf ? new String[]{MainActivity.USB_URL, wifi}
                : usb ? new String[]{MainActivity.USB_URL} : wf ? new String[]{wifi} : new String[0];
    }

    private long dispOffSince;                            // v0.14.1

    private void serverGone(String why) {
        Log.i(TAG, why + ": PC spento, spengo schermo e fotocamera");
        pcOff = true;
        loadStatus = "PC spento (" + why + ")";
        h.post(new Runnable() { public void run() {
            closeCamera(); status = "fermo: PC spento";
        } });
        main.post(new Runnable() { public void run() { if (!screenOffByUs) sleep(); } });
    }

    private void serverBack(String why) {
        if (!pcOff) return;
        Log.i(TAG, "server tornato (" + why + "): riaccendo schermo e fotocamera");
        pcOff = false;
        pollFails = 0; lastOk = SystemClock.uptimeMillis();
        lastPresenceAt = SystemClock.uptimeMillis();
        h.post(new Runnable() { public void run() {
            if (presenceOn && cam == null) openCamera();
        } });
        main.post(new Runnable() { public void run() { wake(); } });
    }

    // Segnale di risveglio: il server, partendo, manda "TAB2MON-UP" in broadcast sulla porta 8092.
    // Cosi' non si aspetta il prossimo ciclo di polling (e funziona anche se l'IP del PC e' cambiato:
    // il nuovo indirizzo e' quello del mittente).
    private void udpLoop() {
        java.net.DatagramSocket sk = null;
        try {
            sk = new java.net.DatagramSocket(null);
            sk.setReuseAddress(true);
            sk.bind(new java.net.InetSocketAddress(WAKE_PORT));
            byte[] b = new byte[512];
            while (udpRun) {
                java.net.DatagramPacket pk = new java.net.DatagramPacket(b, b.length);
                try { sk.receive(pk); } catch (java.io.IOException e) { if (!udpRun) break; continue; }
                try {
                    JSONObject o = new JSONObject(new String(pk.getData(), 0, pk.getLength(), "UTF-8"));
                    if (!"tab2monitor-up".equals(o.optString("app"))) continue;
                    String url = "http://" + pk.getAddress().getHostAddress() + ":" + o.optInt("port", 8090);
                    if (wifiOn() && !url.equals(prefs.getString("wifi_url", "")))
                        prefs.edit().putString("wifi_url", url).apply();
                    pollFails = 0;
                    serverBack("segnale di avvio del server");
                } catch (Exception ignored) { /* pacchetto di altro tipo */ }
            }
        } catch (Exception e) {
            Log.w(TAG, "ascolto UDP " + WAKE_PORT, e);
        } finally {
            if (sk != null) sk.close();
        }
    }

    private String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(1500); c.setReadTimeout(2500); c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            nbos.reset();
            int n;
            while ((n = in.read(nbuf)) > 0) nbos.write(nbuf, 0, n);
            in.close();
            return nbos.toString("UTF-8");
        } catch (Exception e) { c.disconnect(); throw e; }
    }

    private void check(MainActivity.Metrics m) {
        // 1) carico: conta come presenza (riaccende e tiene acceso finche' dura)
        String why = null;
        if (m.cpu >= thCpu) why = "CPU " + Math.round(m.cpu) + "%";
        else if (m.ram >= thRam) why = "RAM " + Math.round(m.ram) + "%";
        else for (MainActivity.Metrics.Gpu g : m.gpus) {
            if (g.load >= thGpu) { why = g.name + " carico " + Math.round(g.load) + "%"; break; }
            if (g.memPct >= thVram) { why = g.name + " VRAM " + Math.round(g.memPct) + "%"; break; }
        }
        loadStatus = why == null ? "nessun carico oltre soglia" : "sotto carico: " + why;
        if (loadWake && why != null) presence("carico: " + why);
        // 2) allarmi temperatura
        if (!alarmOn) return;
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, 0);
        long now = SystemClock.uptimeMillis();
        for (MainActivity.Metrics.Hw hw : m.hw) for (int q = 0; q < hw.tName.length; q++) {
            String key = sensorKey(hw.name, hw.tName[q]);
            int thr = p.getInt(key, alarmDef);
            float c = hw.tC[q];
            if (thr <= 0 || Float.isNaN(c)) continue;          // 0 = allarme disattivato per il sensore
            Long at = alarmAt.get(key);
            if (c >= thr) {
                if (at == null || now - at > 60000) {
                    alarmAt.put(key, now);
                    presence("allarme " + hw.tName[q]);
                    say("Attenzione. " + hw.tName[q] + ", " + hw.name + ", " + Math.round(c) + " gradi.");
                }
            } else if (at != null && c < thr - 3) alarmAt.remove(key);   // isteresi 3 gradi
        }
    }

    static String sensorKey(String hw, String name) { return "thr|" + hw + "|" + name; }

    private void say(String s) {
        Log.i(TAG, "voce: " + s);
        if (tts == null || !ttsReady) return;
        Bundle b = new Bundle();
        b.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);   // suona anche a volume media 0
        tts.speak(s, TextToSpeech.QUEUE_ADD, b, "tab2alarm" + SystemClock.uptimeMillis());
    }

    // ------------------------------------------------------- schermo on / off
    private void sleep() {
        screenOffByUs = true;
        graceUntil = SystemClock.uptimeMillis() + GRACE_MS;
        if (dpm.isAdminActive(admin)) dpm.lockNow();             // spegnimento reale
        else MainActivity.dim(true);                             // ripiego: schermo nero
    }

    private void wake() {
        screenOffByUs = false;
        MainActivity.dim(false);
        if (!pm.isInteractive()) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "tab2monitor:wake");
            wl.acquire(3000);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    static boolean adminActive(Context c) {
        DevicePolicyManager d = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return d.isAdminActive(new ComponentName(c, AdminReceiver.class));
    }
}
