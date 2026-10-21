# CONTEXT — Tab2revamp
Versione: v0.1.2 — 2026-10-21 21:50 UTC

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
| Root | ⏳ | richiesto dall'utente 2026-10-21; richiede flash TWRP → in attesa di conferma (vedi sotto) |
| ROM custom | ⏳ | nessun flash eseguito |

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

## Punti aperti
- Decidere: root dello stock (debloat) **oppure** direttamente ROM custom senza GApps
  (il debloat diventa superfluo). Il root dello stock è una deviazione se poi si cambia ROM.
- Fonte affidabile per Odin/Heimdall e SuperSU.
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
