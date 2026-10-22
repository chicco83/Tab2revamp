# Ricerca: sostituire il connettore 30 pin del Galaxy Tab 2 (GT-P5100) con una USB-C
Versione: v0.1.0 — 2026-10-22 12:05 (ora del PC) — solo ricerca online, **niente provato sull'hardware**.

## Esito
- Nessun kit, scheda o guida per una USB-C su questo tablet. Si trovano solo flex di ricarica 30 pin di ricambio
  (venduti a volte come «micro-USB dock», ma il connettore è il 30 pin Samsung) e cavi 30 pin → USB-A.
- Opzione sicura: flex di ricarica originale (iFixit «Tab 2 10.1 Charger Port Replacement», Geek's Lab P5100/P5110).
- Opzione mod: breakout USB-C saldato al flex. ⏳ non documentata, da progettare e provare.

## Pinout (foto: `docs/pinout_30pin_galaxy_tab_droidangel.jpg`, da droidangel.blogspot.com, postata su XDA)
Numerazione «da sinistra a destra sui pin di saldatura»: verificare in continuità con un multimetro prima di saldare.

| Segnale | Pin |
|---|---|
| GND | 1, 2, 15, 16, 30 |
| USB D+ / D− | 3 / 4 |
| IF_CON_SENSE | 5 |
| V_ACCESSORY_5.0V | 6 |
| V_BUS_1 | 7, 8 |
| VOUT_CHARGER | 9, 10 |
| ACCESSORY_ID / USB_ID | 13 |
| ACCESSORY_INT | 14 |
| MHL D+ / D− / ID | 17 / 18 / 19 |
| IF_RXD / IF_TXD | 20 / 21 |
| AP_TV_OUT / REMOTE_SENSE | 23 / 24 |
| EAR_L / EAR_R_CRADLE | 27 / 28 |
| 3.5_INT_TEST | 29 |
| NC | 11, 12, 22, 25, 26 |

## Riconoscimento del caricatore (aggiunto 12:20, fonti = forum, non documenti Samsung ⚠️)
- Il Tab tratta una porta con D+/D− non «marcati» come porta PC da 500 mA: carica lentissima o «X rossa» (forum earlybird.club, iFixit Answers).
- Il caricatore da muro viene riconosciuto dalla tensione sulle linee dati:
  - **D+ e D− in corto** → il tablet lo prende per un caricatore (specifica USB BC1.2: ≤200 Ω). Un utente riporta una
    alimentazione 2 A modificata con il corto D+/D− che carica subito fino a 2 A.
  - **Partitore a ~1,2 V** (es. 33 kΩ da 5 V + 10 kΩ a GND, o 30 k/10 k, su entrambe le linee): usato dai caricatori Samsung/terzi
    per tablet vecchi; qualcuno dice che limita a ~1,2 A. Non verificato per il P5100.
- Nessuna fonte parla di resistenza sul pin 13 (ID) per la ricarica: il 301 kΩ della mia ricerca iniziale **non è confermato**, non usarlo.
- Il corto D+/D− non aumenta la corrente da una porta PC: il limite lo dà l'alimentatore.

## Piano di prova proposto (⏳ da fare, richiede hardware)
1. Misurare su un caricatore Samsung 2 A originale: continuità/resistenza tra D+ e D− (cavo 30 pin aperto o breakout).
2. Con cavo 30 pin → USB-A e un caricatore generico: provare corto D+/D−, poi partitore 1,2 V, e leggere la corrente dal server Tab2Monitor.
3. Solo dopo: breakout USB-C (5,1 kΩ su CC1/CC2, VBUS→pin 7/8, GND, corto D+/D−) su un cavo di prova prima di toccare il flex.
4. Ricordare: i caricatori USB-C PD «veri» danno 5 V solo se vedono Rd su CC; un caricatore USB-C non PD spesso ha già D+/D− in corto (da verificare).

## Altri punti aperti (⏳)
- VOUT_CHARGER (9, 10) è probabilmente un'uscita: non collegarla al VBUS del caricatore senza verifica.
- Prima di aprire il tablet: backup /efs (vedi CLAUDE.md).
