// =============================================================================
// Tab2Monitor - SettingsActivity (pannello impostazioni)
// Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
//   v0.10.0: pulsante "Cerca PC sulla rete" (Discovery, UDP 8090) con elenco dei server
//            trovati; scelta lingua (sistema / italiano / English); testi IT/EN (L.t);
//            permesso CAMERA chiesto solo da API 23 (minSdk 21)
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
        setTitle(L.t("Tab2Monitor - impostazioni", "Tab2Monitor - settings"));

        ScrollView sv = new ScrollView(this);
        ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        ll.setPadding(p, dp(8), p, p);
        sv.addView(ll);

        header(L.t("Sorgenti dati", "Data sources"));
        toggle("src_usb", "USB  -  " + MainActivity.USB_URL + "  (adb reverse tcp:8090 tcp:8090)");
        toggle("src_wifi", L.t("Wi-Fi  -  URL del PC qui sotto (vuoto = ricerca automatica)",
                "Wi-Fi  -  PC address below (empty = automatic search)"));
        // v0.10.0: suggerimento generico (era l'IP del PC di sviluppo, http://192.168.1.237:8090)
        url = field(prefs.getString("wifi_url", ""), "http://192.168.1.x:8090",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        discoveryUi();                                       // v0.10.0

        header(L.t("Pannelli della dashboard", "Dashboard panels"));
        for (int i = 0; i < MonitorView.PANEL_KEYS.length; i++)
            toggle(MonitorView.PANEL_KEYS[i], MonitorView.panelLabel(i));

        header(L.t("Generale", "General"));
        languageUi();                                        // v0.10.0
        toggle("autostart", L.t("Avvio automatico all'accensione del tablet", "Start automatically when the tablet boots")
                + (android.os.Build.VERSION.SDK_INT >= 29 ? L.t("  (Android 10+: funziona solo come schermata Home)",
                "  (Android 10+: works only as Home screen)") : ""));
        toggle("home", L.t("Usa come schermata Home (kiosk: riparte da sola dopo un riavvio; poi scegliere Tab2Monitor come Home)",
                "Use as Home screen (kiosk: restarts by itself after a reboot; then choose Tab2Monitor as Home)"), false);   // v0.10.0
        toggle("anim_fans",L.t("Ventole animate (velocita' come gli RPM reali; spente = meno CPU)", "Animated fans (speed follows the real RPM; off = less CPU)"));   // v0.4.0
        toggle("anim_smooth", L.t("Animazioni fluide di valori e grafici (spente = aggiornamento a scatti, meno CPU)", "Smooth animation of values and charts (off = step updates, less CPU)"));   // v0.9.0
        toggle("gauge_check", L.t("Gauge check all'accensione dello schermo (indicatori al massimo e ritorno)", "Gauge check when the screen turns on (needles to max and back)"));     // v0.9.0
        label(L.t("Intervallo di aggiornamento (ms, minimo 500)", "Refresh interval (ms, minimum 500)"));
        poll = field(String.valueOf(prefs.getInt("poll_ms", 1000)), "1000", InputType.TYPE_CLASS_NUMBER);
        // v0.4.0
        label(L.t("Cambio pagina automatico ogni (secondi, 0 = resta sulla pagina; tocco = pagina successiva)", "Automatic page change every (seconds, 0 = stay; tap = next page)"));
        pageSec = field(String.valueOf(prefs.getInt("page_sec", MainActivity.DEF_PAGE_SEC)),
                String.valueOf(MainActivity.DEF_PAGE_SEC), InputType.TYPE_CLASS_NUMBER);
        label(L.t("Pagina GPU: una scheda e' \"sotto carico\" sopra questa percentuale", "GPU page: a card is \"busy\" above this percentage"));
        busyPct = field(String.valueOf(prefs.getInt("gpu_busy_pct", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        label(L.t("Pagina GPU sotto carico: resta visibile per (secondi)", "Busy GPU page: stays visible for (seconds)"));
        busySec = field(String.valueOf(prefs.getInt("gpu_busy_sec", 60)), "60", InputType.TYPE_CLASS_NUMBER);

        // ---- v0.6.0: presenza con la fotocamera frontale
        header(L.t("Presenza (fotocamera frontale)", "Presence (front camera)"));
        toggle("presence", L.t("Spegni lo schermo quando non c'e' nessuno e riaccendilo al passaggio", "Turn the screen off when nobody is there and back on when someone passes"), false);
        label(L.t("Spegni dopo (secondi senza movimento ne' volti, minimo 10)", "Turn off after (seconds without motion or faces, minimum 10)"));
        offSec = field(String.valueOf(prefs.getInt("presence_off_sec", 120)), "120", InputType.TYPE_CLASS_NUMBER);
        label(L.t("Sensibilita' al movimento (decimi di % dell'immagine: 20 = 2%; piu' basso = piu' sensibile)", "Motion sensitivity (tenths of % of the image: 20 = 2%; lower = more sensitive)"));
        sens = field(String.valueOf(prefs.getInt("presence_sens", 20)), "20", InputType.TYPE_CLASS_NUMBER);
        final boolean adm = PresenceService.adminActive(this);
        label(adm ? L.t("Spegnimento reale: attivo (amministratore dispositivo)", "Real screen off: active (device administrator)")
                  : L.t("Spegnimento reale non attivo: senza, lo schermo viene solo oscurato", "Real screen off not active: without it the screen is only dimmed"));
        if (!adm) {
            Button adminBtn = new Button(this);
            adminBtn.setText(L.t("Attiva spegnimento reale (amministratore dispositivo)", "Enable real screen off (device administrator)"));
            adminBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            new android.content.ComponentName(SettingsActivity.this, AdminReceiver.class));
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            L.t("Serve solo per spegnere lo schermo quando la fotocamera non vede nessuno.", "Only used to turn the screen off when the camera sees nobody."));
                    startActivity(i);
                }
            });
            ll.addView(adminBtn);
        }
        label(L.t("Stato: ", "Status: ") + PresenceService.status);

        // ---- v0.8.0: carico
        header(L.t("Carico (riaccende lo schermo e lo tiene acceso)", "Load (turns the screen on and keeps it on)"));
        toggle("load_wake", L.t("Riaccendi lo schermo quando il PC e' sotto carico (anche senza nessuno davanti)", "Turn the screen on when the PC is under load (even with nobody in front)"));
        label(L.t("Soglie in %: CPU", "Thresholds in %: CPU"));
        thCpu = field(String.valueOf(prefs.getInt("th_cpu", 60)), "60", InputType.TYPE_CLASS_NUMBER);
        label(L.t("GPU (carico di elaborazione di una qualsiasi scheda)", "GPU (compute load of any card)"));
        thGpu = field(String.valueOf(prefs.getInt("th_gpu", 50)), "50", InputType.TYPE_CLASS_NUMBER);
        label("RAM");
        thRam = field(String.valueOf(prefs.getInt("th_ram", 90)), "90", InputType.TYPE_CLASS_NUMBER);
        label(L.t("VRAM (saturazione memoria di una qualsiasi scheda)", "VRAM (memory use of any card)"));
        thVram = field(String.valueOf(prefs.getInt("th_vram", 90)), "90", InputType.TYPE_CLASS_NUMBER);
        if (PresenceService.loadStatus.length() > 0) label(L.t("Adesso: ", "Now: ") + PresenceService.loadStatus);

        // ---- v0.8.0: allarmi vocali di temperatura
        header(L.t("Allarmi temperatura (voce)", "Temperature alarms (voice)"));
        toggle("alarm_on", L.t("Avviso vocale quando una temperatura supera la soglia", "Voice warning when a temperature exceeds its threshold"), false);
        label(L.t("Soglia generale (gradi C) per i sensori senza soglia propria", "General threshold (degrees C) for sensors without their own"));
        alarmDef = field(String.valueOf(prefs.getInt("alarm_def", 85)), "85", InputType.TYPE_CLASS_NUMBER);
        Button test = new Button(this);
        test.setText(L.t("Prova voce", "Test voice"));
        test.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("alarm_on", true).putBoolean("alarm_test", true).apply();
                startService(new Intent(SettingsActivity.this, PresenceService.class));
            }
        });
        ll.addView(test);
        label(L.t("Soglia per sensore (vuoto = generale, 0 = nessun allarme):", "Threshold per sensor (empty = general, 0 = no alarm):"));
        MainActivity.Metrics lm = PresenceService.last != null ? PresenceService.last : MainActivity.lastMetrics;
        if (lm == null) label(L.t("  (nessun dato dal server ancora: riaprire le impostazioni tra qualche secondo)", "  (no data from the server yet: reopen the settings in a few seconds)"));
        else for (MainActivity.Metrics.Hw hw : lm.hw) for (int q = 0; q < hw.tName.length; q++) {
            String key = PresenceService.sensorKey(hw.name, hw.tName[q]);
            label(hw.name + "  -  " + hw.tName[q] + L.t("  (ora ", "  (now ") + Math.round(hw.tC[q]) + " C)");
            EditText e = field(prefs.contains(key) ? String.valueOf(prefs.getInt(key, 0)) : "",
                    L.t("generale (", "general (") + prefs.getInt("alarm_def", 85) + ")", InputType.TYPE_CLASS_NUMBER);
            sensKeys.add(key); sensFields.add(e);
        }

        Button close = new Button(this);
        close.setText(L.t("Chiudi e torna alla dashboard", "Close and return to the dashboard"));
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
                if (key.equals("home")) {                       // v0.10.0: modalita' Home (kiosk)
                    MainActivity.setHome(SettingsActivity.this, on);
                    if (on) try { startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)); }
                            catch (Exception ignored) { }    // scelta della Home nelle impostazioni di sistema
                }
                // v0.6.0: la presenza richiede il permesso fotocamera (runtime, API 23+)
                // v0.10.0: solo da API 23 (minSdk 21: sotto il permesso e' gia' concesso)
                if (on && key.equals("presence") && !MainActivity.hasCamera(SettingsActivity.this))
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

    // ---- v0.10.0: ricerca dei PC con Tab2Monitor sulla rete (Discovery, UDP 8090)
    private void discoveryUi() {
        final Button find = new Button(this);
        find.setText(L.t("Cerca PC con Tab2Monitor sulla rete", "Find PCs running Tab2Monitor on the network"));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final String saved = prefs.getString("server_name", "");
        if (saved.length() > 0) label(L.t("PC scelto: ", "Chosen PC: ") + saved);
        find.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                find.setEnabled(false);
                find.setText(L.t("Ricerca in corso\u2026", "Searching\u2026"));
                list.removeAllViews();
                new Thread(new Runnable() {
                    public void run() {
                        final java.util.ArrayList<Discovery.Server> found = Discovery.find(SettingsActivity.this, 2000);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                find.setEnabled(true);
                                find.setText(L.t("Cerca di nuovo", "Search again"));
                                if (found.isEmpty()) {
                                    TextView tv = new TextView(SettingsActivity.this);
                                    tv.setText(L.t("Nessun PC trovato: Tab2Monitor deve essere avviato sul PC, sulla stessa rete; "
                                            + "il firewall di Windows deve consentirlo (Setup del PC, voce firewall).",
                                            "No PC found: Tab2Monitor must be running on the PC, on the same network; "
                                            + "the Windows firewall must allow it (PC Setup, firewall item)."));
                                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                                    list.addView(tv);
                                }
                                for (final Discovery.Server s : found) {
                                    Button b = new Button(SettingsActivity.this);
                                    b.setText(s.toString());
                                    b.setAllCaps(false);
                                    b.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            url.setText(s.url);
                                            prefs.edit().putString("wifi_url", s.url).putString("server_name", s.name)
                                                    .putBoolean("src_wifi", true).apply();
                                            list.removeAllViews();
                                            TextView tv = new TextView(SettingsActivity.this);
                                            tv.setText(L.t("Scelto: ", "Chosen: ") + s.name + "  (" + s.url + ")");
                                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                                            list.addView(tv);
                                        }
                                    });
                                    list.addView(b);
                                }
                            }
                        });
                    }
                }, "Tab2Discovery").start();
            }
        });
        ll.addView(find);
        ll.addView(list);
    }

    // ---- v0.10.0: lingua (preferenza "lang": "" = sistema, "it", "en")
    private void languageUi() {
        label(L.t("Lingua", "Language"));
        final String[] codes = {"", "it", "en"};
        final String[] names = {L.t("come il sistema", "same as system"), "Italiano", "English"};
        LinearLayout row = new LinearLayout(this);
        String cur = prefs.getString("lang", "");
        for (int i = 0; i < codes.length; i++) {
            final String c = codes[i];
            Button b = new Button(this);
            b.setText((c.equals(cur) ? "\u2713 " : "") + names[i]);
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    prefs.edit().putString("lang", c).apply();
                    L.set(c);
                    recreate();                                  // testi nella nuova lingua
                }
            });
            row.addView(b);
        }
        ll.addView(row);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
