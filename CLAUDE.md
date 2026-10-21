# CLAUDE.md — Tab2revamp
Versione: v0.1.0 — 2026-10-21 18:45 UTC

## Regole di progetto
1. **Versioning obbligatorio**: ogni file di codice o manuale ha versione + data/ora
   nel nome file (`nome_vX.Y.Z_AAAAMMGG-HHMM.ext`) e in testa al contenuto.
   A ogni revisione si incrementa la versione e si crea il nuovo file
   (la dashboard/lo script piu' recente viene scelto per ordine di nome).
2. Quando si corregge codice, la sezione precedente resta come **commento datato**.
3. Spiegazioni sul funzionamento come commenti nelle sezioni del codice.
4. Aggiornare sempre **CONTEXT.md**, **CHANGELOG.md**, **MANUAL.md** a fine attivita'.

## Git (piu' sessioni locali e cloud sulla stessa repo)
1. `git pull --rebase` prima di committare.
2. `git add` solo dei file modificati da questa sessione, **per nome** (mai `-A` / `.`).
3. Messaggio descrittivo, poi `git push` subito.
4. Conflitto su un `.md`: unire a mano tenendo entrambe le modifiche
   (CHANGELOG: entrambe le voci, numerate in ordine).
5. Allineare sempre la copia locale con il commit pushato.

## Vincoli tecnici
- Dispositivo: Galaxy Tab 2 10.1 **GT-P5100 (3G)**, OMAP4430, 1 GB RAM, 1280x800.
- Dashboard: solo ES5 + XHR, niente librerie/transizioni (vedi commenti nel file).
- Prima di qualsiasi flash: **backup /efs (IMEI)**.
