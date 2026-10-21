# CONTEXT — Tab2revamp
Versione: v0.1.4 — 2026-10-21 23:45 UTC

## Obiettivo
Riusare un Galaxy Tab 2 10.1 (GT-P5100, lentissimo: ogni tocco richiede secondi)
come **display secondario fisso** accanto a un PC Windows, che mostra il carico
delle risorse (CPU, core, RAM, GPU, temperature, disco, rete, top processi).

## Decisione architetturale (2026-10-21)
| Opzione | Esito | Motivo |
|---|---|---|
| spacedesk (desktop esteso) | scartata | streaming H.264 continuo su 1 GB RAM / OMAP4430 = lag; inutile per una dashboard |
| postmarketOS / mobile Linux | scartata (per ora) | port espresso in stato "testing", SGX540 senza accelerazione -> browser in rendering software, piu' lento di Android |
| **Android leggero + dashboard web** | **scelta** | ROM custom senza GApps + pagina HTML minimale che legge un JSON dal PC |

L'utente ha confermato: dashboard dedicata (non serve trascinare finestre).

## Architettura
```
Windows PC                                       Galaxy Tab 2
LibreHardwareMonitor (web :8085, opz.) ─┐
psutil ── tab2monitor_server (:8090) ───┼── USB: adb reverse tcp:8090 ──► browser -> http://localhost:8090
                                        └── oppure Wi-Fi http://<IP-PC>:8090
```
- `windows/agent/` server Python (stdlib + psutil), endpoint `/api/metrics` e `/`.
- `dashboard/` pagina unica, polling 1 s, aggiornamento DOM minimo.
- `android/` script adb: `diagnose` (sola lettura) e `tune` (impostazioni, debloat, apertura dashboard).

## Stato (verificato sul tablet il 2026-10-21, sessione locale)
| Voce | Stato | Dettaglio |
|---|---|---|
| Diagnosi adb | ✅ | stock **4.2.2 JDQ39.P5100XXDMJ2**, SDK 17, CSC ITV, kernel 3.0.31 (ott 2013), **niente root**; log in `android/logs/` (ignorato da git: contiene seriale) |
| Server Windows | ✅ | `start_tab2monitor` avviato, `/api/metrics` ok; LHM non attivo (GPU/temperature "--") |
| Dashboard via Wi-Fi | ✅ | tablet 192.168.1.226 → PC 192.168.1.237:8090 (rete "Pubblica", regola firewall python.exe presente); Chrome 71 ok, browser stock con artefatti |
| Tuning impostazioni | ✅ | animazioni 0, stay-on 3, luminosità 90, landscape (`user_rotation 0`), wifi scan off |
| Debloat via adb | ⛔ senza root | su 4.2.2 `pm disable-user` si blocca; richiede root o disattivazione manuale da Impostazioni |
| Root dello stock | ⛔ scartato | l'utente (2026-10-21) ha scelto di passare direttamente alla ROM custom: il root dello stock sarebbe una deviazione |
| TWRP 3.4.0-0 | ✅ file verificato | `twrp-3.4.0-0-espresso3g.img.tar` nella cartella repo (non versionato), SHA256 `7e90f863…f789d` = quello ufficiale twrp.me |
| Odin 3.14.4 | ⚠️ | fornito dall'utente, exe **non firmato**, non verificabile; lo esegue l'utente |
| Backup stock | ✅ | `backup/stock_P5100XXDMJ2_20261021/` (escluso da git): EFS, SBL1/2, PARAM, KERNEL, MODEM, FACTORYFS, HIDDEN (MD5 tablet = PC, `MD5SUMS.txt`) + 665 file utente (517 MB) |
| TWRP 3.4.0-0 | ✅ installato | flash con Odin dall'utente 2026-10-21 |
| ROM custom | ✅ installata | LineageOS 14.1 `lineage_espresso3g-userdebug 7.1.2 NJH47F` (MD5 zip verificato); 1° tentativo E1001 cause 101 (`/cache` non montata dopo il wipe) → `twrp mount /cache` e reinstall OK |
| Debug USB / adb root | ✅ | attivato a mano dall'utente; build userdebug: `adb root` disponibile (accesso root "solo ADB") |
| Debloat LineageOS | ✅ | 21 app disattivate con `tune_v0.1.3` (Jelly tenuto come browser) |
| Dashboard via USB | ✅ | `adb reverse tcp:8090` + Jelly (WebView 81), landscape; va rifatto `adb reverse` a ogni riavvio del tablet |
| rild (RIL 3G) | ✅ disattivato | consumava ~50% CPU in un loop (`ReaderLoop IOCTL_MODEM_STATUS = 4`); aggiunto `disabled` in `/system/etc/init/rild.rc`. Rollback: `adb root; adb remount; adb push backup/lineage14.1_20200623/rild.rc.orig /system/etc/init/rild.rc` (MD5 orig `5ab3fa76…f462`) |
| SensorService | ⏳ aperto | dopo i riavvii il thread `SensorService` di system_server occupa ~50% (un core) senza sensori attivi; nella 1ª sessione dopo il setup era a 0%. Causa non trovata (non dipende da rild né dalla rotazione automatica) |

Misure (am start -W, TotalTime ms, avvio a freddo):
| | prima del tuning | dopo |
|---|---|---|
| Impostazioni | 1472 / 1409 | 3258 / 1902 |
| Browser stock | 1820 / 1110 | 3132 / 1642 |
| Chrome con dashboard | — | 3277 |
Nessun miglioramento misurabile: il "dopo" è stato preso con Chrome+dashboard in RAM
(MemFree ~33–50 MB su 800 MB, swap zram in uso). Il collo di bottiglia è la **RAM**
(GMS ~80 MB, Chrome ~125 MB, decine di servizi Samsung), non la CPU (idle ~90%).
Le animazioni a 0 migliorano la percezione ma non il tempo di avvio.

eMMC: MAG2GA, CID PRV 0x07 (non è la revisione 0x19 del "brick bug" Samsung) — rischio basso,
comunque usare solo TWRP 3.x per i wipe.

## Root e flash — proposta (NON eseguita, serve conferma esplicita)
Fonti verificate il 2026-10-21:
- TWRP ufficiale espresso3g (p3100/p5100): https://twrp.me/samsung/samsunggalaxytab2gmsunified.html,
  file `twrp-3.4.0-0-espresso3g.img.tar` (7,2 MB, 2020-06-11) da https://eu.dl.twrp.me/espresso3g/
  (recovery = `/dev/block/mmcblk0p6`).
- Strumento di flash: **Odin** non ha una fonte ufficiale (tool Samsung trapelato);
  Heimdall (open source) non ha release su GitHub → scelta della fonte da fare con l'utente.
- Root su 4.2.2: SuperSU 2.82 (Chainfire) — sito originale non più attivo, fonte da concordare.
  Magisk attuale non supporta Android < 5.0.
- ROM: Unlegacy-Android (AOSP 7.1/espresso3g) — builds.unlegacy-android.org **non raggiungibile**
  il 2026-10-21; LineageOS 14.1 unofficial citato su community.e.foundation, link non verificati.

Procedura proposta: Download mode → flash TWRP (contatore download +1) → avvio diretto in
recovery (altrimenti lo stock la sovrascrive) → **backup EFS** in TWRP + `adb pull` sul PC →
(root) SuperSU zip, oppure (refactor) wipe + ROM senza GApps.
Alternativa senza flash per il debloat: Impostazioni → Gestione applicazioni → Disattiva.

## ROM scelta (2026-10-21)
**LineageOS 14.1 (Android 7.1.2) unofficial di Android-Andi (andi34)**, ultima build:
`lineage-14.1_espresso3g-7.1.2-20200623-1945.zip`, 283,3 MB, MD5 `b6df6ac6041d68a4ae3ac9a65b32027b`
- Catena di fiducia: andi34 è il manutentore del TWRP ufficiale espresso3g (gerrit.twrp.me);
  la sua pagina https://andi34.github.io/roms_tab2_lineage.html rimanda alla cartella
  AndroidFileHost `espresso3g-LineageOS-14.1` (flid 209384), uploader "Android-Andi".
- Perché questa e non altre:
  | Opzione | Esito |
  |---|---|
  | LineageOS 14.1 andi34 (2020) | **scelta**: maintainer noto, 7.1 → `adb reverse`, WebView/Chrome recenti, nessuna repartition |
  | LineageOS 16.0 unofficial (XDA, 2024-25) | scartata: "ALPHA / UNSTABLE", non daily-driver |
  | /e/OS Oreo/Pie custom (SourceForge) | scartata: build di terzi, segnalazioni di boot bloccato |
  | Unlegacy-Android 7.1 | scartata: sito build irraggiungibile il 2026-10-21 |
  | LineageOS 13 (6.0.1) andi34 | riserva se la 14.1 non avvia |
- Limiti noti: **niente RIL** (3G/SMS non funzionano: irrilevante per un display Wi‑Fi),
  fotocamera incerta; se non avvia, provare il kernel userdebug della stessa data
  (`lineage-14.1-espresso3g-userdebug-kernel-20200421.zip`) o la build precedente.
- Niente GApps (meno RAM occupata: oggi i servizi Google pesano ~80 MB).

Misure su LineageOS (am start -W, ms):
| | stock 4.2.2 | LineageOS prima del tuning | LineageOS dopo tuning |
|---|---|---|---|
| Impostazioni | 1409–3258 | 698 / 698 | 799 / 679 |
| Browser (Jelly) | 1110–3132 (stock) | 2073 / 7896 | 1148 / 1265 |
RAM: MemTotal 937 MB (stock 819), Jelly con dashboard ~105–120 MB RSS.

## Punti aperti
- SensorService al 50% dopo il boot: indagare (log HAL sensori) o conviverci.
- `adb reverse` va ripristinato a ogni riavvio del tablet (o usare Wi‑Fi).
- Proposta utente 2026-10-21: **APK nativa** al posto del browser (meno RAM, avvio al boot,
  schermo intero senza barra URL). Da fare in una sessione dedicata.
- Valutare app nativa Android (Canvas) solo se la pagina web risultasse lenta.

## Handoff sessione locale (v0.1.1 — 2026-10-21 19:05 UTC)
La sessione cloud non può raggiungere il PC/USB: la sessione locale va avviata dall'utente.
1. PC Windows con tablet in USB (Debug USB autorizzato), `adb` e Git for Windows installati.
2. `git clone https://github.com/chicco83/Tab2revamp` → `cd Tab2revamp` → `git checkout ccr-734baad4-t02ry6`
3. Nella cartella: `claude remote-control` (o Claude Desktop sulla cartella).
4. Prompt da incollare:
```
Leggi CLAUDE.md, CONTEXT.md, MANUAL.md. Tablet GT-P5100 collegato via adb.
1) adb devices
2) bash android/diagnose_v0.1.0_20261021-1845.sh e analizza il log
3) avvia windows/agent/start_tab2monitor_v0.1.0_20261021-1845.cmd
4) URL=http://<IP-PC>:8090 bash android/tune_v0.1.0_20261021-1845.sh --debloat  (stock 4.x -> Wi-Fi), misura la reattivita'
5) NON flashare nulla senza mia conferma: proponi TWRP/ROM esatte per espresso3g con fonti verificate e procedura di backup EFS.
Aggiorna CONTEXT/CHANGELOG/MANUAL, commit per nome dei file toccati, pull --rebase, push.
```
