// =============================================================================
// Tab2Monitor - PresenceService (presenza con la fotocamera frontale)
// Versione: v0.6.0 - 2026-10-22 02:00
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
import android.util.Log;

import java.util.List;

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
                .setContentTitle("Tab2Monitor - rilevamento presenza")
                .setContentText("Fotocamera frontale attiva per accendere/spegnere lo schermo")
                .setContentIntent(pi).setOngoing(true).build();
        startForeground(1, n);
        thread = new HandlerThread("Tab2Camera");
        thread.start();
        h = new Handler(thread.getLooper());
        lastPresenceAt = SystemClock.uptimeMillis();
        h.post(new Runnable() { public void run() { openCamera(); } });
        h.postDelayed(tick, 1000);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, 0);
        offSec = Math.max(10, p.getInt("presence_off_sec", 120));
        sens = Math.max(0.2f, p.getInt("presence_sens", 20) / 10f);   // decimi di %
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        h.post(new Runnable() { public void run() { closeCamera(); } });
        thread.quitSafely();
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
            if (!screenOffByUs && idle >= offSec) {
                Log.i(TAG, "nessuno da " + idle + " s: spengo");
                main.post(new Runnable() { public void run() { sleep(); } });
            }
            if (cam != null) status = screenOffByUs ? "schermo spento, in attesa" : "attivo, nessuno da " + idle + " s";
            h.postDelayed(this, 1000);
        }
    };

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
