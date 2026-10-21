// =============================================================================
// Tab2Monitor - AdminReceiver (amministratore dispositivo)
// Versione: v0.6.0 - 2026-10-22 02:00
// =============================================================================
// Serve solo per DevicePolicyManager.lockNow(): spegnere davvero lo schermo quando
// la fotocamera non vede nessuno (policy "force-lock", res/xml/device_admin.xml).
// Attivazione: Impostazioni Tab2Monitor -> "Attiva spegnimento reale", oppure
//   adb shell dpm set-active-admin it.tab2revamp.monitor/.AdminReceiver
// Revoca: Impostazioni Android -> Sicurezza -> Amministratori dispositivo, oppure
//   adb shell dpm remove-active-admin it.tab2revamp.monitor/.AdminReceiver
// =============================================================================
package it.tab2revamp.monitor;

import android.app.admin.DeviceAdminReceiver;

public class AdminReceiver extends DeviceAdminReceiver {
}
