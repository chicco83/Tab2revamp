// =============================================================================
// Tab2Monitor - PresenceService (presenza con la fotocamera frontale)
// Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
//   v0.10.0: risoluzione della fotocamera = la piu' piccola supportata con larghezza >= 160
//            (altri tablet possono non avere 160x120); testi e voce IT/EN (L)
// Versione precedente: v0.8.0 - 2026-10-22 02:45
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
    static final int W = 160, H = 120, STEP = 4;
    private int cw = W, ch = H;                           // v0.10.0: risoluzione scelta davvero          // risoluzione e passo di campionamento
    static final int PIX_DIFF = 25;                       // differenza di luminanza "cambiata"
    static final long GRACE_MS = 5000;                    // dopo lo spegnimento: niente movimento
    static final int HITS = 2;                            // fotogrammi consecutivi con movimento

    // Stato letto dalla schermata impostazioni (diagnostica)
    static volatile String status = L.t("spento", "off");
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
        // notifica obbligatoria per un servizio in primo piano
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic)
                .setContentTitle(L.t("Tab2Monitor - rilevamento presenza", "Tab2Monitor - presence detection"))
                .setContentText(L.t("Fotocamera frontale attiva per accendere/spegnere lo schermo", "Front camera active to turn the screen on/off"))
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
        String wifi = p.getString("wifi_url", "");
        boolean usb = p.getBoolean("src_usb", true), wf = p.getBoolean("src_wifi", true) && wifi.length() > 0;
        urls = usb && wf ? new String[]{MainActivity.USB_URL, wifi}
                : usb ? new String[]{MainActivity.USB_URL} : wf ? new String[]{wifi} : new String[0];
        h.post(new Runnable() { public void run() {
            if (presenceOn && cam == null) openCamera();
            if (!presenceOn && cam != null) { closeCamera(); status = L.t("fotocamera spenta (presenza disattivata)", "camera off (presence disabled)"); }
        } });
        if (alarmOn && tts == null) {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                public void onInit(int st) {
                    if (st == TextToSpeech.SUCCESS) {
                        // v0.10.0: voce nella lingua dell'app (era sempre Locale.ITALIAN); se il motore
                        // non ha quella lingua resta la sua lingua predefinita
                        tts.setLanguage(L.locale());
                        ttsReady = true;
                    } else Log.w(TAG, "TextToSpeech non disponibile: " + st);
                }
            });
        }
        if (p.getBoolean("alarm_test", false)) {            // pulsante "Prova voce" delle impostazioni
            p.edit().remove("alarm_test").apply();
            net.postDelayed(new Runnable() { public void run() { say(L.t("Prova allarme Tab2Monitor. Voce attiva.", "Tab2Monitor alarm test. Voice active.")); } }, 1500);
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
        if (screenOffByUs) wake();                       // non lasciare lo schermo spento
        if (cpuLock.isHeld()) cpuLock.release();
        status = L.t("spento", "off");
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
            if (front < 0) { status = L.t("nessuna fotocamera frontale", "no front camera"); return; }
            cam = Camera.open(front);
            Camera.Parameters p = cam.getParameters();
            // v0.10.0: la risoluzione piu' piccola tra quelle supportate con larghezza >= 160
            // (160x120 sul GT-P5100; altri tablet possono non averla). Era: p.setPreviewSize(W, H);
            Camera.Size best = null;
            for (Camera.Size z : p.getSupportedPreviewSizes())
                if (z.width >= W && (best == null || z.width * z.height < best.width * best.height)) best = z;
            cw = best != null ? best.width : W; ch = best != null ? best.height : H;
            prev = null;
            p.setPreviewSize(cw, ch);
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
            buf = new byte[cw * ch * 3 / 2];
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
            status = L.t("attivo", "active");
            Log.i(TAG, "fotocamera frontale " + front + " aperta, " + cw + "x" + ch);
        } catch (Exception e) {
            status = L.t("errore fotocamera: ", "camera error: ") + e.getMessage();
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
        // v0.10.0: griglia sulla risoluzione scelta (cw x ch), passo proporzionale: ~40x30 punti
        int st = Math.max(STEP, cw / 40);
        if (prev == null) prev = new byte[(cw / st + 1) * (ch / st + 1)];
        for (int y = 0, k = 0; y < ch; y += st)
            for (int x = 0; x < cw; x += st, k++) {
                int v = d[y * cw + x] & 0xff;
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
            if (cam != null) status = screenOffByUs ? L.t("schermo spento, in attesa", "screen off, waiting") : L.t("attivo, nessuno da ", "active, nobody for ") + idle + " s";
            h.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------- carico e allarmi (v0.8.0)
    private final byte[] nbuf = new byte[8192];
    private final ByteArrayOutputStream nbos = new ByteArrayOutputStream(16384);

    private final Runnable poll = new Runnable() {
        public void run() {
            MainActivity.Metrics m = null;
            for (String u : urls) {
                try { m = MainActivity.Metrics.parse(new JSONObject(get(u + "/api/metrics"))); break; }
                catch (Exception ignored) { }
            }
            if (m != null) { last = m; check(m); }
            net.postDelayed(this, 2000);
        }
    };

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
            if (g.load >= thGpu) { why = g.name + L.t(" carico ", " load ") + Math.round(g.load) + "%"; break; }
            if (g.memPct >= thVram) { why = g.name + " VRAM " + Math.round(g.memPct) + "%"; break; }
        }
        loadStatus = why == null ? L.t("nessun carico oltre soglia", "no load above threshold") : L.t("sotto carico: ", "under load: ") + why;
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
                    say(L.t("Attenzione. ", "Warning. ") + hw.tName[q] + ", " + hw.name + ", " + Math.round(c) + L.t(" gradi.", " degrees."));
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
