// =============================================================================
// Tab2Monitor - L (testi in italiano o inglese)
// Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
// =============================================================================
// Perche' non le risorse strings.xml: l'interfaccia e' tutta costruita in codice e i
// testi sono decine di frasi brevi accanto a dove vengono disegnati; una coppia
// L.t("italiano", "english") nel punto d'uso resta leggibile e non richiede R.string.
// Lingua: quella del sistema del tablet; italiano se la lingua e' "it", altrimenti
// inglese. Si puo' forzare con la chiave "lang" ("it" / "en" / "" = sistema),
// es. adb shell am start -n it.tab2revamp.monitor/.MainActivity --es lang en
// =============================================================================
package it.tab2revamp.monitor;

import java.util.Locale;

final class L {
    static volatile boolean it = "it".equals(Locale.getDefault().getLanguage());

    private L() { }

    /** Imposta la lingua dalla preferenza "lang" ("" o null = lingua del sistema). */
    static void set(String lang) {
        if ("it".equals(lang)) it = true;
        else if ("en".equals(lang)) it = false;
        else it = "it".equals(Locale.getDefault().getLanguage());
    }

    static String t(String italiano, String english) { return it ? italiano : english; }

    static Locale locale() { return it ? Locale.ITALIAN : Locale.ENGLISH; }
}
