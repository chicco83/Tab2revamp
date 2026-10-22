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

## Punti aperti (⏳)
- Riconoscimento del caricatore da 2 A (senza, «X rossa»): corto D+/D−? resistenza sul pin 13? Da misurare su un cavo/caricatore originale.
- VOUT_CHARGER (9, 10) è probabilmente un'uscita: non collegarla al VBUS del caricatore senza verifica.
- Su USB-C servono 5,1 kΩ da CC1 e CC2 verso GND.
- Prima di aprire il tablet: backup /efs (vedi CLAUDE.md).
