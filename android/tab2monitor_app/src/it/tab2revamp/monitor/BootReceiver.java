// =============================================================================
// Tab2Monitor - BootReceiver
// Versione: v0.1.0 - 2026-10-21 23:50
// Nota versioning: in Java il nome del file deve coincidere con la classe
// pubblica, quindi la versione sta qui in testa e nel versionName dell'APK.
// =============================================================================
// Cosa fa: a BOOT_COMPLETED (e dopo un aggiornamento dell'APK) apre
// MainActivity, se l'avvio automatico non e' stato disattivato
// (am start ... --ez autostart false).
// Android 7.1 consente ancora a un receiver di avviare un'Activity.
// =============================================================================
package it.tab2revamp.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ctx.getSharedPreferences(MainActivity.PREFS, 0).getBoolean("autostart", true)) return;
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
