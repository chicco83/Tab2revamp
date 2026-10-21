# CONTEXT — Tab2revamp
Versione: v0.1.0 — 2026-10-21 18:45 UTC

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
