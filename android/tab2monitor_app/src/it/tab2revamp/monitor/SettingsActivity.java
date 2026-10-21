// =============================================================================
// Tab2Monitor - SettingsActivity (pannello impostazioni)
// Versione: v0.6.0 - 2026-10-22 02:00
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
    private LinearLayout ll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(MainActivity.PREFS, 0);
        setTitle("Tab2Monitor - impostazioni");

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
