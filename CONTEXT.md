# CONTEXT — Tab2revamp
Versione: v0.1.1 — 2026-10-21 19:05 UTC

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

## Stato
- v0.1.0: server, dashboard e script scritti e testati in cloud (server+dashboard
  verificati con Chromium a 1280x800). **Non ancora provati sul tablet.**
- Prossimo passo: sessione locale con adb -> `diagnose`, backup EFS, poi flash ROM.

## Punti aperti
- ROM esatta e TWRP per espresso3g da verificare al momento del flash (fonti, versione, eventuale repartition).
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
