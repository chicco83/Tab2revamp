// =============================================================================
// Tab2Monitor - SettingsActivity (pannello impostazioni)
// Versione: v0.9.2 - 2026-10-22 04:10
//   v0.9.2: interruttore "Capovolgi l'interfaccia di 180 gradi" (applicato subito anche qui)
//   v0.9.0: interruttore "Animazioni fluide"
//   v0.8.0: sezione "Carico" (riaccensione con CPU/GPU/RAM/VRAM oltre soglia) e sezione
//           "Allarmi temperatura (voce)": soglia generale + soglia per ogni sensore
//           (vuoto = generale, 0 = nessun allarme), pulsante "Prova voce"
//   v0.6.0: sezione "Presenza (fotocamera frontale)": interruttore, ritardo di spegnimento,
//           sensibilita', attivazione amministratore dispositivo, stato del rilevamento
//   v0.4.0: campo "cambio pagina ogni N secondi" (page_sec), pannello Ventole; si apre
//           anche toccando l'ingranaggio in alto a destra
//   v0.2.0 - 2026-10-22 00:10: prima versione
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Si apre toccando l'ingranaggio o tenendo premuto lo schermo della dashboard (oppure da PC:
//   adb shell am start -n it.tab2revamp.monitor/.SettingsActivity).
// Ogni interruttore scrive subito nelle SharedPreferences "tab2monitor";
// tornando alla dashboard (Indietro / "Chiudi") MainActivity.onResume rilegge
// tutto e riavvia il polling.
//   - Sorgenti dati: USB (127.0.0.1:8090 via adb reverse) e Wi-Fi (URL modificabile)
//   - Pannelli: uno per riquadro della dashboard; spenti = spazio ridistribuito
//   - Generale: avvio al boot, intervallo di aggiornamento
// Interfaccia costruita in codice (niente layout XML): poche View, si chiude e
// viene distrutta tornando indietro, quindi non pesa sulla RAM della dashboard.
// =============================================================================
package it.tab2revamp.monitor;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private EditText url, poll, pageSec, busyPct, busySec;   // v0.4.0: + pageSec, soglie GPU
    private EditText offSec, sens;                     // v0.6.0: presenza
    private EditText thCpu, thGpu, thRam, thVram, alarmDef;   // v0.8.0
    private final java.util.ArrayList<String> sensKeys = new java.util.ArrayList<String>();
    private final java.util.ArrayList<EditText> sensFields = new java.util.ArrayList<EditText>();
    private LinearLayout ll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(MainActivity.PREFS, 0);
        setTitle("Tab2Monitor - impostazioni");
        MainActivity.applyFlip(this, prefs);                 // v0.9.2

        ScrollView sv = new ScrollView(this);
        ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        ll.setPadding(p, dp(8), p, p);
        sv.addView(ll);

        header("Sorgenti dati");
        toggle("src_usb", "USB  -  " + MainActivity.USB_URL + "  (adb reverse tcp:8090 tcp:8090)");
        toggle("src_wifi", "Wi-Fi  -  URL del PC qui sotto");
        url = field(prefs.getString("wifi_url", ""), "http://192.168.1.237:8090",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        header("Pannelli della dashboard");
        for (int i = 0; i < MonitorView.PANEL_KEYS.length; i++)
            toggle(MonitorView.PANEL_KEYS[i], MonitorView.PANEL_LABELS[i]);

        header("Generale");
        toggle("autostart", "Avvio automatico all'accensione del tablet");
        toggle("anim_fans", "Ventole animate (velocita' come gli RPM reali; spente = meno CPU)");   // v0.4.0
        toggle("anim_smooth", "Animazioni fluide di valori e grafici (spente = aggiornamento a scatti, meno CPU)");   // v0.9.0
        toggle("gauge_check", "Gauge check all'accensione dello schermo (indicatori al massimo e ritorno)");     // v0.9.0
        toggle("flip180", "Capovolgi l'interfaccia di 180 gradi (tablet appoggiato al rovescio)", false);       // v0.9.2
        label("Intervallo di aggiornamento (ms, minimo 500)");
        poll = field(String.valueOf(prefs.getInt("poll_ms", 1000)), "1000", InputType.TYPE_CLASS_NUMBER);
        // v0.4.0
        label("Cambio pagina automatico ogni (secondi, 0 = resta sulla pagina; tocco = pagina successiva)");
        pageSec = field(String.valueOf(prefs.getInt("page_sec", MainActivity.DEF_PAGE_SEC)),
                String.valueOf(MainActivity.DEF_PAGE_SEC), InputType.TYPE_CLASS_NUMBER);
        label("Pagina GPU: una scheda e' \"sotto carico\" sopra questa percentuale");
        busyPct = field(String.valueOf(prefs.getInt("gpu_busy_pct", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        label("Pagina GPU sotto carico: resta visibile per (secondi)");
        busySec = field(String.valueOf(prefs.getInt("gpu_busy_sec", 60)), "60", InputType.TYPE_CLASS_NUMBER);

        // ---- v0.6.0: presenza con la fotocamera frontale
        header("Presenza (fotocamera frontale)");
        toggle("presence", "Spegni lo schermo quando non c'e' nessuno e riaccendilo al passaggio", false);
        label("Spegni dopo (secondi senza movimento ne' volti, minimo 10)");
        offSec = field(String.valueOf(prefs.getInt("presence_off_sec", 120)), "120", InputType.TYPE_CLASS_NUMBER);
        label("Sensibilita' al movimento (decimi di % dell'immagine: 20 = 2%; piu' basso = piu' sensibile)");
        sens = field(String.valueOf(prefs.getInt("presence_sens", 20)), "20", InputType.TYPE_CLASS_NUMBER);
        final boolean adm = PresenceService.adminActive(this);
        label(adm ? "Spegnimento reale: attivo (amministratore dispositivo)"
                  : "Spegnimento reale non attivo: senza, lo schermo viene solo oscurato");
        if (!adm) {
            Button adminBtn = new Button(this);
            adminBtn.setText("Attiva spegnimento reale (amministratore dispositivo)");
            adminBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            new android.content.ComponentName(SettingsActivity.this, AdminReceiver.class));
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Serve solo per spegnere lo schermo quando la fotocamera non vede nessuno.");
                    startActivity(i);
                }
            });
            ll.addView(adminBtn);
        }
        label("Stato: " + PresenceService.status);

        // ---- v0.8.0: carico
        header("Carico (riaccende lo schermo e lo tiene acceso)");
        toggle("load_wake", "Riaccendi lo schermo quando il PC e' sotto carico (anche senza nessuno davanti)");
        label("Soglie in %: CPU");
        thCpu = field(String.valueOf(prefs.getInt("th_cpu", 60)), "60", InputType.TYPE_CLASS_NUMBER);
        label("GPU (carico di elaborazione di una qualsiasi scheda)");
        thGpu = field(String.valueOf(prefs.getInt("th_gpu", 50)), "50", InputType.TYPE_CLASS_NUMBER);
        label("RAM");
        thRam = field(String.valueOf(prefs.getInt("th_ram", 90)), "90", InputType.TYPE_CLASS_NUMBER);
        label("VRAM (saturazione memoria di una qualsiasi scheda)");
        thVram = field(String.valueOf(prefs.getInt("th_vram", 90)), "90", InputType.TYPE_CLASS_NUMBER);
        if (PresenceService.loadStatus.length() > 0) label("Adesso: " + PresenceService.loadStatus);

        // ---- v0.8.0: allarmi vocali di temperatura
        header("Allarmi temperatura (voce)");
        toggle("alarm_on", "Avviso vocale quando una temperatura supera la soglia", false);
        label("Soglia generale (gradi C) per i sensori senza soglia propria");
        alarmDef = field(String.valueOf(prefs.getInt("alarm_def", 85)), "85", InputType.TYPE_CLASS_NUMBER);
        Button test = new Button(this);
        test.setText("Prova voce");
        test.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("alarm_on", true).putBoolean("alarm_test", true).apply();
                startService(new Intent(SettingsActivity.this, PresenceService.class));
            }
        });
        ll.addView(test);
        label("Soglia per sensore (vuoto = generale, 0 = nessun allarme):");
        MainActivity.Metrics lm = PresenceService.last != null ? PresenceService.last : MainActivity.lastMetrics;
        if (lm == null) label("  (nessun dato dal server ancora: riaprire le impostazioni tra qualche secondo)");
        else for (MainActivity.Metrics.Hw hw : lm.hw) for (int q = 0; q < hw.tName.length; q++) {
            String key = PresenceService.sensorKey(hw.name, hw.tName[q]);
            label(hw.name + "  -  " + hw.tName[q] + "  (ora " + Math.round(hw.tC[q]) + " C)");
            EditText e = field(prefs.contains(key) ? String.valueOf(prefs.getInt(key, 0)) : "",
                    "generale (" + prefs.getInt("alarm_def", 85) + ")", InputType.TYPE_CLASS_NUMBER);
            sensKeys.add(key); sensFields.add(e);
        }

        Button close = new Button(this);
        close.setText("Chiudi e torna alla dashboard");
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        ll.addView(close, lp);

        setContentView(sv);
    }

    // I campi di testo si salvano quando si esce dalla schermata
    @Override
    protected void onPause() {
        String u = url.getText().toString().trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.length() > 0 && !u.startsWith("http")) u = "http://" + u;
        int ms = 1000;
        try { ms = Math.max(500, Integer.parseInt(poll.getText().toString().trim())); }
        catch (NumberFormatException ignored) { }
        int ps = MainActivity.DEF_PAGE_SEC;                  // v0.4.0
        try { ps = Math.max(0, Integer.parseInt(pageSec.getText().toString().trim())); }
        catch (NumberFormatException ignored) { }
        // 2026-10-22 v0.4.0 (era: prefs.edit().putString("wifi_url", u).putInt("poll_ms", ms).apply();)
        SharedPreferences.Editor ed = prefs.edit();          // v0.8.0: soglie
        ed.putInt("th_cpu", num(thCpu, 60, 1, 100)).putInt("th_gpu", num(thGpu, 50, 1, 100))
          .putInt("th_ram", num(thRam, 90, 1, 100)).putInt("th_vram", num(thVram, 90, 1, 100))
          .putInt("alarm_def", num(alarmDef, 85, 1, 150));
        for (int i = 0; i < sensKeys.size(); i++) {
            String t = sensFields.get(i).getText().toString().trim();
            if (t.length() == 0) ed.remove(sensKeys.get(i));
            else ed.putInt(sensKeys.get(i), num(sensFields.get(i), 0, 0, 150));
        }
        ed.apply();
        prefs.edit().putInt("presence_off_sec", num(offSec, 120, 10, 86400))      // v0.6.0
                .putInt("presence_sens", num(sens, 20, 1, 500)).apply();
        prefs.edit().putString("wifi_url", u).putInt("poll_ms", ms).putInt("page_sec", ps)
                .putInt("gpu_busy_pct", num(busyPct, 30, 1, 100)).putInt("gpu_busy_sec", num(busySec, 60, 1, 3600))
                .apply();
        super.onPause();
    }

    private static int num(EditText e, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim()))); }
        catch (NumberFormatException ex) { return def; }
    }

    // ------------------------------------------------------------- costruzione
    private void header(String t) {
        TextView tv = new TextView(this);
        tv.setText(t.toUpperCase());
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(MonitorView.BLUE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(0, dp(22), 0, dp(6));
        ll.addView(tv);
    }

    private void label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setPadding(0, dp(10), 0, 0);
        ll.addView(tv);
    }

    // v0.6.0: interruttore con default esplicito (presence parte spento)
    private void toggle(String key, String text) { toggle(key, text, true); }

    private void toggle(final String key, String text, boolean def) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(prefs.getBoolean(key, def));           // v0.6.0: default dal chiamante (era true)
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(key, on).apply();
                if (key.equals("flip180")) MainActivity.applyFlip(SettingsActivity.this, prefs);   // v0.9.2
                // v0.6.0: la presenza richiede il permesso fotocamera (runtime, API 23+)
                if (on && key.equals("presence") && checkSelfPermission(android.Manifest.permission.CAMERA)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1);
            }
        });
        ll.addView(s);
    }

    private EditText field(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        ll.addView(e);
        return e;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
