// =============================================================================
// Tab2Monitor - SettingsActivity (pannello impostazioni)
// Versione: v0.2.0 - 2026-10-22 00:10
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Si apre tenendo premuto lo schermo della dashboard (oppure da PC:
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
    private EditText url, poll;
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
        label("Intervallo di aggiornamento (ms, minimo 500)");
        poll = field(String.valueOf(prefs.getInt("poll_ms", 1000)), "1000", InputType.TYPE_CLASS_NUMBER);

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
        prefs.edit().putString("wifi_url", u).putInt("poll_ms", ms).apply();
        super.onPause();
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

    private void toggle(final String key, String text) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(prefs.getBoolean(key, true));          // tutto acceso di default
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(key, on).apply();
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
