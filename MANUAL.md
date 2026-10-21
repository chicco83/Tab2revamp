# MANUAL — Tab2revamp
Versione: v0.1.4 — 2026-10-21 23:45 UTC

Galaxy Tab 2 10.1 (GT-P5100) come display delle risorse di un PC Windows.

---
## 1. Lato Windows

### 1.1 Requisiti
- Python 3.8+ (`python` nel PATH) — https://www.python.org
- (Consigliato) **LibreHardwareMonitor** per GPU e temperature:
  avviarlo come amministratore → *Options → Remote Web Server → Run* (porta 8085).
  Senza LHM la dashboard funziona lo stesso, con GPU/temperature vuote.
- (Per USB) **Android Platform Tools** (`adb`) nel PATH. Su questo PC adb è in
  `%LOCALAPPDATA%\Android\Sdk\platform-tools` (non nel PATH): in Git Bash
  `export PATH="$PATH:/c/Users/<utente>/AppData/Local/Android/Sdk/platform-tools"`.
- Script `.sh` da **Git Bash**: dalla v0.1.2 impostano `MSYS_NO_PATHCONV=1` da soli
  (per comandi `adb shell` a mano con percorsi `/sys/...` va esportata anche nella shell).

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
bash android/diagnose_v0.1.2_20261021-2150.sh
```
Genera `android/logs/diag_<data>.txt` (modello, ROM, RAM, CPU, spazio, app). Da
conservare come baseline prima/dopo.

### 2.3 Ottimizzazione e apertura dashboard
```
bash android/tune_v0.1.2_20261021-2150.sh             # tuning + dashboard via USB
bash android/tune_v0.1.2_20261021-2150.sh --debloat   # anche disattivazione app inutili
URL=http://192.168.1.10:8090 bash android/tune_v0.1.2_20261021-2150.sh   # via Wi-Fi
```
- Animazioni a 0, schermo sempre acceso in carica, landscape, luminosità bassa.
- `adb reverse` richiede **Android 5.0+**: sullo stock 4.x usare la modalità Wi‑Fi.
- Ripristino: `adb shell pm enable <pacchetto>`, scale animazioni a 1.
- **Stock 4.2.2 senza root** (situazione verificata 2026-10-21): `pm disable-user` si blocca,
  lo script v0.1.2 salta il debloat. Alternative: disattivare a mano da
  *Impostazioni → Gestione applicazioni → Tutte → app → Disattiva*, oppure root (sez. 3).
- Con root (`su` presente) lo script usa `su -c pm disable <pacchetto>`.
- Wi‑Fi verificato: tablet e PC sulla stessa LAN, URL `http://<IP-PC>:8090`; il ping dal
  tablet fallisce (Windows blocca ICMP) ma l'HTTP funziona.
- Usare **Chrome** (lo script lo apre se installato): il browser stock 4.2 lascia artefatti.

---
## 3. Refactor Android (ROM custom) — da eseguire nella sessione locale

> ⚠️ Operazione a rischio (brick/perdita IMEI). Procedere solo dopo la diagnosi.
> Fonti, versioni di TWRP/ROM ed eventuale repartition **vanno verificate al
> momento** (i build per Tab 2 sono non ufficiali, codename `espresso3g`/`p5100`).

0. Verifica file: `sha256sum twrp-3.4.0-0-espresso3g.img.tar` deve dare
   `7e90f863da0d0a64e45acb3d4aba8a53bd32e17b36b77d965b8f1290f91f789d` (valore ufficiale twrp.me).
   I binari (TWRP, Odin, ROM, backup) stanno nella cartella repo ma sono esclusi da `.gitignore`.
1. **Backup**: foto/dati utente; poi in TWRP backup della partizione **EFS**
   (IMEI/rete mobile) e copiarlo sul PC. Senza EFS il 3G non funziona più.
   In TWRP (adb gira come root): `adb shell dd if=/dev/block/mmcblk0p1 of=/tmp/efs.img`
   e `adb pull /tmp/efs.img` — **verificare prima** che p1 sia EFS
   (`ls -l /dev/block/platform/omap/omap_hsmmc.1/by-name/`; mmcblk0p1 = 20 MB come /efs).
   Conservare anche il backup EFS fatto dall'interfaccia TWRP (Backup → EFS).
2. Batteria > 60 %.
3. Download mode (Power + Volume giù) → flash TWRP con **Odin** (Windows) o
   **Heimdall** (Linux). File verificato: `twrp-3.4.0-0-espresso3g.img.tar` (7,2 MB)
   da https://eu.dl.twrp.me/espresso3g/ (pagina: twrp.me → Samsung Galaxy Tab 2 GSM unified,
   p3100/p5100). Odin: slot PDA/AP, togliere "Auto Reboot", poi Volume su + Power
   direttamente in TWRP (al primo avvio normale lo stock sovrascrive la recovery).
   Il contatore download aumenta di 1.
3b. Root dello stock (opzionale): da TWRP flash dello zip SuperSU 2.82 (Magisk non supporta 4.2).
   Poi `bash android/tune_v0.1.2_20261021-2150.sh --debloat` usa `su`.
4. Avvio in recovery (Power + Volume su) → Wipe: system, data, cache, dalvik.
5. Flash ROM leggera — scelta: `lineage-14.1_espresso3g-7.1.2-20200623-1945.zip`
   (andi34, MD5 `b6df6ac6041d68a4ae3ac9a65b32027b`), **senza GApps**. Verificare l'MD5 sul PC
   (`md5sum`), poi in TWRP: Advanced → ADB Sideload → `adb sideload <zip>`
   (oppure `adb push <zip> /sdcard/` e Install). Android 7.1 consente `adb reverse`.
   Niente RIL: 3G e SMS non funzionano con questa ROM.
   Se l'installazione fallisce con **E1001 / cause 101** subito dopo il wipe: `/cache` non è
   montata → `adb shell twrp mount /cache` e reinstallare.
   Da PC, in TWRP, il wipe si può fare via adb: `adb shell twrp wipe system|data|cache|dalvik`
   (`data` non tocca i file utente) e `adb shell twrp install /sdcard/<zip>`.
6. Primo avvio (5–10 min). LineageOS parte con **debug USB spento**: Impostazioni → Info →
   7 tocchi su Numero build → Opzioni sviluppatore → Debug Android. Poi
   `bash android/tune_v0.1.3_20261021-2315.sh --debloat` (via USB con `adb reverse`, apre Jelly).
6b. rild (3G) in loop al 50% CPU: `adb root && adb remount`, salvare `/system/etc/init/rild.rc`,
   aggiungere la riga `    disabled` sotto `service ril-daemon`, riavviare. 3G/SMS non
   funzionano comunque con questa ROM; IMEI/EFS non vengono toccati.
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
| `adb devices` = unauthorized | accettare "Consentire debug USB?" sul tablet (spuntare "Consenti sempre") |
| adb non vede il tablet dopo `adb root` | PowerShell: `Get-Process adb \| Stop-Process -Force`, poi `adb devices` |
| Git Bash + `adb pull` su `/g/...` con `MSYS_NO_PATHCONV=1` | usare percorsi Windows `G:/...` |
| Dashboard in verticale | `adb shell settings put system user_rotation 0` (Tab 2 10.1 è nativamente orizzontale) |
| Script tune bloccato su "disable ..." | stock < 4.3 senza root; usare v0.1.2; uccidere i `pm` orfani: `adb shell ps` → `adb shell kill <pid>` degli `app_process` dell'utente shell |
