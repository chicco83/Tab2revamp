# MANUAL — Tab2revamp
Versione: v0.1.0 — 2026-10-21 18:45 UTC

Galaxy Tab 2 10.1 (GT-P5100) come display delle risorse di un PC Windows.

---
## 1. Lato Windows

### 1.1 Requisiti
- Python 3.8+ (`python` nel PATH) — https://www.python.org
- (Consigliato) **LibreHardwareMonitor** per GPU e temperature:
  avviarlo come amministratore → *Options → Remote Web Server → Run* (porta 8085).
  Senza LHM la dashboard funziona lo stesso, con GPU/temperature vuote.
- (Per USB) **Android Platform Tools** (`adb`) nel PATH.

### 1.2 Avvio
Doppio clic su `windows/agent/start_tab2monitor_v0.1.0_20261021-1845.cmd`:
installa `psutil` se manca, esegue `adb reverse tcp:8090 tcp:8090` se trova adb,
avvia il server su `http://0.0.0.0:8090`.
Verifica dal PC: aprire `http://localhost:8090` nel browser.

Firewall: per l'uso via Wi‑Fi consentire Python sulle reti private (porta 8090).

### 1.3 Avvio automatico (opzionale)
Utilità di pianificazione → Crea attività → Trigger "All'accesso" → Azione: il file `.cmd`.

---
## 2. Lato tablet

### 2.1 Preparazione
1. Impostazioni → Info sul dispositivo → toccare 7 volte "Numero build".
2. Opzioni sviluppatore → **Debug USB** attivo; collegare al PC e autorizzare.
3. Opzioni sviluppatore → *Limite processi in background* = 2.

### 2.2 Diagnosi (sola lettura)
```
bash android/diagnose_v0.1.0_20261021-1845.sh
```
Genera `android/logs/diag_<data>.txt` (modello, ROM, RAM, CPU, spazio, app). Da
conservare come baseline prima/dopo.

### 2.3 Ottimizzazione e apertura dashboard
```
bash android/tune_v0.1.0_20261021-1845.sh             # tuning + dashboard via USB
bash android/tune_v0.1.0_20261021-1845.sh --debloat   # anche disattivazione app inutili
URL=http://192.168.1.10:8090 bash android/tune_v0.1.0_20261021-1845.sh   # via Wi-Fi
```
- Animazioni a 0, schermo sempre acceso in carica, landscape, luminosità bassa.
- `adb reverse` richiede **Android 5.0+**: sullo stock 4.x usare la modalità Wi‑Fi.
- Ripristino: `adb shell pm enable <pacchetto>`, scale animazioni a 1.

---
## 3. Refactor Android (ROM custom) — da eseguire nella sessione locale

> ⚠️ Operazione a rischio (brick/perdita IMEI). Procedere solo dopo la diagnosi.
> Fonti, versioni di TWRP/ROM ed eventuale repartition **vanno verificate al
> momento** (i build per Tab 2 sono non ufficiali, codename `espresso3g`/`p5100`).

1. **Backup**: foto/dati utente; poi in TWRP backup della partizione **EFS**
   (IMEI/rete mobile) e copiarlo sul PC. Senza EFS il 3G non funziona più.
2. Batteria > 60 %.
3. Download mode (Power + Volume giù) → flash TWRP con **Odin** (Windows) o
   **Heimdall** (Linux).
4. Avvio in recovery (Power + Volume su) → Wipe: system, data, cache, dalvik.
5. Flash ROM leggera — candidato: **LineageOS 14.1 (Android 7.1) unofficial per espresso3g**,
   **senza GApps**. Android 7.1 consente `adb reverse` e WebView aggiornata.
6. Primo avvio, poi ripetere 2.1 → 2.3.
7. Controllo IMEI: Impostazioni → Info → Stato.

---
## 4. Risoluzione problemi
| Sintomo | Causa / Rimedio |
|---|---|
| Pallino rosso in alto a destra | server non raggiungibile: verificare `.cmd` aperto, `adb reverse`, firewall |
| GPU "--" | LibreHardwareMonitor non avviato o web server spento (porta 8085) |
| Temperature CPU assenti | LHM va eseguito come amministratore |
| Pagina lenta | ridurre `POLL_MS` a 2000 nella dashboard; valutare app nativa |
| `adb reverse` errore | Android < 5.0 → usare Wi‑Fi |
