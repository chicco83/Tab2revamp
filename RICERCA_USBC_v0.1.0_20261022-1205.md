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

## Cavo di prova USB-C → 30 pin (progetto, 12:35, ⏳ non costruito né provato)
Idea: **non saldare sul tablet**. Il cavo 30 pin → USB-A originale ha già dentro VBUS, GND, D+, D− (e ID) cablati sul connettore
giusto. Si interpone tra caricatore USB-C e cavo un piccolo breakout che fa solo due cose: mette i 5,1 kΩ su CC e collega D+/D−.

### Componenti (tutti economici, da cercare in negozio di elettronica)
| # | Componente | Note |
|---|---|---|
| 1 | Cavo **30 pin → USB-A** originale Samsung (quello del caricatore ETA-P10 va bene) | già in uso; non tagliarlo |
| 2 | **Breakout USB-C maschio/femmina a 4+ pin** con pad CC1/CC2/D+/D−/VBUS/GND (tipo «USB-C breakout board 16 pin») | serve il pad **D+/D−**: le schede solo-alimentazione (6 pin) non li espongono |
| 3 | **Breakout/presa USB-A femmina** con pad (o adattatore USB-A femmina → pin) | per infilare il cavo 30 pin |
| 4 | 2 resistori **5,1 kΩ** 1/4 W | uno CC1→GND, uno CC2→GND (identifica il dispositivo come sink) |
| 5 | Spezzone di filo per il **corto D+/D−**; opzionale 2 resistori 33 kΩ + 10 kΩ per il partitore 1,2 V | provare prima il corto |
| 6 | Caricatore **USB-C 5 V ≥ 2 A** (anche PD: senza negoziazione dà 5 V) | deve poter dare 2 A a 5 V |
| 7 | Multimetro; il server Tab2Monitor per leggere la corrente | misure di verifica |

Alternativa senza saldature: **adattatore USB-C femmina → USB-A maschio** già pronto, ma i 5,1 kΩ sono dentro e il corto D+/D− non c'è:
va aperto o sostituito dal breakout.

### Schema
```
 Caricatore USB-C 5V/2A+
        │  (cavo USB-C)
 ┌──────┴────────────────────────────┐
 │ Breakout USB-C                     │        ┌─────────────────────────┐
 │  VBUS ───────────────────────────────────── │ USB-A femmina           │
 │  GND  ───────────────────────────────────── │  pin1 VBUS  pin4 GND    │ ── cavo originale ── 30 pin ── Tab
 │  CC1 ──[5,1kΩ]── GND               │        │  pin2 D−  pin3 D+       │      (D+ = pin 3, D− = pin 4,
 │  CC2 ──[5,1kΩ]── GND               │        │      └──corto──┘        │       VBUS = pin 7+8, GND = 1,2,15,16,30)
 │  (D+/D− del lato USB-C: non usati) │        └─────────────────────────┘
 └────────────────────────────────────┘
```
D+ e D− si uniscono **sul lato USB-A** (pin 2 e 3), cioè verso il tablet; i fili dati del caricatore USB-C non vanno collegati.
Variante B (partitore): 33 kΩ da VBUS a D+, 10 kΩ da D+ a GND, D− unito a D+.

### Sequenza di prova
1. Controlli a freddo (multimetro, tablet scollegato): VBUS–GND non in corto; resistenza D+↔D− ≈ 0 Ω (variante A).
2. Collegare caricatore → breakout: misurare 5 V su VBUS **prima** di inserire il tablet.
3. Collegare il tablet (spento o acceso) e leggere la corrente dal server Tab2Monitor: atteso ~1–2 A a batteria bassa; con «X rossa» o <500 mA passare alla variante B.
4. Confronto con il caricatore originale 2 A sulla stessa batteria.
5. Segnare i risultati in questa nota (✅/⛔), poi decidere se la mod sul flex ha senso.

### Rischi
- Nessuna saldatura sul tablet in questa fase: rischio basso, ma **non collegare VOUT_CHARGER (pin 9/10)**: nel cavo originale non servono.
- Un caricatore USB-C PD molto potente senza Rd corretto non eroga 5 V: ecco perché i 5,1 kΩ su ENTRAMBI CC1 e CC2.
- Pin e numerazione dei fili del cavo: verificare col multimetro quale filo va a quale pin del 30 pin (⚠️ la numerazione del pinout è «da sinistra»).

## Alimentare dai contatti della batteria, senza batteria (idea utente, ricerca 12:50, ⏳ niente provato)
**Cosa dice il service manual** (ManualsLib, «Level 3 Repair – Block Diagram» del GT-P5100; letto solo il testo estratto della pagina):
- caricabatterie **SMB347S** (switching, con ingressi TA_nCONNECTED / TA_nCHG / TA_EN / OTG_EN);
- misuratore di carica **MAX17042G** su I2C a 1,8 V (FUEL_SCL / FUEL_SDA, FUEL_ALERT);
- PMIC **TWL6032**;
- segnali del blocco batteria: **V_BATTERY−, VF, CONTACT, BAT_REMOVAL** (VF/CONTACT = probabile sensore di temperatura/identificazione, ⚠️ inferenza).
- Il manuale estratto **non dà** i numeri dei pin del connettore batteria né dice come il tablet rileva la batteria.

**Dove cercare il pinout** (non aperti, PDF): schema completo su Badcaps («Samsung Galaxy Tab 2 P5100 (GT-P5100) Schematic»,
badcaps.net/forum/…/3479329) e su s-manuals.com («samsung_gt-p5100_service_schematics.pdf»); service manual su ManualsLib
(manual 845254, 1155514). Scaricare un PDF richiede la tua conferma.

**Valutazione (da conoscenza generale, non verificata sul P5100)**
- Alimentare dai contatti batteria **salta lo SMB347**: niente più decisione di carica, quindi niente corto D+/D− né 5,1 kΩ per il caricatore.
- Il MAX17042 stima la carica anche dalla tensione: la percentuale sarebbe plausibile, non senza senso come avevo scritto prima; da provare.
- BAT_REMOVAL / VF: se il PMIC non li vede a posto, il tablet può non accendersi o spegnersi. Servono il valore giusto sul pin VF
  (misurare su una batteria originale) e **non mettere in corto** nulla senza averlo capito: quei contatti sono parte delle protezioni.
- Serve un convertitore 5 V → ~4,0 V con limite di corrente e picchi di 2 A; una tensione sbagliata può danneggiare la scheda.
- Vantaggio: nessuna batteria da stressare né da tenere tra 40% e 80%, rischio di rigonfiamento eliminato. Svantaggio: se la corrente cala,
  il tablet si spegne subito (nessun tampone).
- Il percorso resta alternativo alla mod USB-C: conviene aprire lo schema, trovare il connettore batteria, e decidere.

## Connettore batteria dallo schema (scaricato e letto 13:15, ✅ dallo schema; ⏳ non verificato col multimetro)
Fonte: `samsung_gt-p5100_service_schematics.pdf` da s-manuals.com (7,7 MB, 11 pagine; **non nel repo**, copia di lavoro nel
scratchpad). Pagina 7, blocco «BATTERY CONTACT», connettore **BTC700**, 9 pin (ritagli in `docs/p5100_schema_pag7_*.png`):

| Pin BTC700 | Rete | Funzione |
|---|---|---|
| 1, 2 | V_BATT | positivo batteria (alimentazione di tutto il tablet) |
| 3 | VF | identificazione/temperatura: va a **GPADC_IN0 del TWL6032** (via R769) e all'**ingresso AIN del MAX17042** (via R729) |
| 4, 5 | V_BATTERY− | negativo batteria (ritorno del fuel gauge) |
| 6, 7, 8, 9 | NC (a massa) | non usati |

- Sul pin VF **nello schema non c'è una resistenza di pull-up né di pull-down**: la tensione la fanno l'ADC del PMIC e il MAX17042 sulla
  resistenza/NTC dentro il pacco batteria. Per simulare la batteria serve quindi **misurare il valore sulla batteria originale** (tra pin 3 e 4/5)
  e metterne uno uguale. ⚠️ Non c'è ancora il valore.
- BAT_REMOVAL esce dal PMIC (pin J10): da capire come viene pilotato (⏳ cercare la rete nelle altre pagine).
- Fuel gauge MAX17042 (U717): nello schema i suoi VBATT e il resto stanno sulla linea V_BATT; il sense è su R755 (resistenza di misura corrente) →
  senza batteria la corrente misurata sarebbe quella di sistema, non importa per l'avvio.

### Prova con alimentatore da banco (progetto, ⏳)
1. Con batteria collegata, misurare: resistenza pin 3 ↔ pin 4 (VF verso massa) e tensione a riposo su pin 3.
2. Togliere la batteria; collegare: pin 1+2 → +4,0 V (alimentatore con limite 2 A), pin 4+5 → massa, pin 3 → resistenza del valore misurato verso massa.
3. Accendere e osservare avvio, percentuale (Tab2Monitor) e comportamento del caricatore (tolto il caricabatterie, il SMB347 non carica nulla).
4. Se parte: sostituire l'alimentatore da banco con 5 V USB-C → convertitore 4,0 V (limite di corrente), con condensatore di tampone vicino ai pin.

## Osservazione dell'utente sul connettore batteria (2026-11-12) e confronto con lo schema
L'utente riferisce che il contatto della batteria ha **5 pin/fili: 2 rossi, 2 neri, 1 blu**, e che la batteria porta la sigla
**GB/T 18287**.
- **Corrispondenza con lo schema (BTC700, pin attivi 1–5):** 2 rossi = V_BATT (pin 1 e 2), 1 blu = VF (pin 3, identificazione/temperatura),
  2 neri = V_BATTERY− (pin 4 e 5). I pin 6–9 dello schema sono NC: coerente con soli 5 fili. ⚠️ È un'ipotesi basata sui colori; il blu come
  VF e l'ordine vanno **verificati col multimetro** (tensione tra rossi e neri ≈ 3,7–4,2 V; il blu non deve dare 4 V ma una tensione/resistenza
  verso nero).
- **GB/T 18287** non è un modello: è la **norma cinese** per le batterie agli ioni di litio dei telefoni cellulari (specifica generale), quindi la
  sigla non dice capacità né pinout. Indica una batteria compatibile/di ricambio, non l'originale Samsung (SP3676B1A per P5100, da etichetta).
  Per la prova servono comunque l'etichetta con capacità/tensione e la misura di VF.
- **Cosa misurare per simulare la batteria:** con la batteria collegata o a banco, resistenza tra filo blu e nero (batteria scollegata dal tablet)
  e tensione blu→nero; se è un NTC varierà con la temperatura (scaldare con le dita). Quel valore, messo come resistenza fissa tra pin 3 e
  massa, è ciò che il tablet deve vedere senza batteria. ⏳ valore non ancora misurato.

## Misure dell'utente sulla batteria (2026-11-12) — ✅ misurato
- Etichetta: batteria **SP3676B1A** confermata (è la batteria originale del P5100).
- Tensione tra rosso e nero: **3,7 V** (batteria a riposo).
- Resistenza tra blu e nero: **9,83 kΩ** a temperatura ambiente; **scaldando diminuisce** → il blu è un **NTC da 10 kΩ** (coefficiente negativo).
- Conclusione: VF (pin 3) è il termistore da 10 kΩ verso massa. Per far credere al tablet che la batteria ci sia, senza batteria basta una
  **resistenza fissa da 10 kΩ (1%) tra VF (pin 3) e massa (pin 4/5)**: corrisponde a ~25 °C e quindi a «temperatura normale».
  Svantaggio accettato: nessun controllo di temperatura, ma senza batteria non c'è nulla da proteggere (la protezione della cella non serve).
- Stato: ⏳ la prova a banco non è ancora fatta. Alimentazione: 3,8–4,0 V con limite di corrente (2 A) su rossi, massa sui neri, 10 kΩ sul blu.
  Dubbio residuo ⚠️: come il PMIC usa BAT_REMOVAL (pin J10 del TWL6032): potrebbe bastare il VF, oppure leggere il livello del pin; si vede alla prova.

## Prova a banco senza batteria — lista componenti, schema, sequenza (progetto 2026-11-12, ⏳ NON eseguita)
Obiettivo: verificare che il Tab 2 si avvii e funzioni alimentato direttamente ai contatti della batteria (pin 1-2 V_BATT, 3 VF, 4-5 GND dello
schema BTC700; fili rossi / blu / neri), con il blu sostituito da una resistenza fissa da 10 kΩ (il NTC originale misurato 9,83 kΩ a ~25 °C).

### Componenti
| # | Componente | Note |
|---|---|---|
| 1 | Alimentatore da banco regolabile **con limite di corrente** (CV/CC), almeno 5 V / 3 A | tensione impostata a **3,9 V**, limite a **0,5 A** per la prima prova |
| 2 | Resistenza **10 kΩ 1 %** (1/4 W) | sostituisce il NTC, tra VF (blu) e massa |
| 3 | Condensatore elettrolitico **470–1000 µF / 10 V** | tampone sui contatti, vicino al tablet (rispetta la polarità) |
| 4 | Multimetro (anche come amperometro in serie) | misura tensione e corrente |
| 5 | Fili sottili / morsetti a coccodrillo piccoli, nastro isolante | contatti sul connettore: nessun corto tra i pin vicini |
| 6 | Un modo per **contattare i 5 pin** senza saldare sul tablet: usare la **spina originale della batteria** (cavetti/plug del pacco) oppure pin/aghi isolati sul connettore BTC700 | se si usa la spina del pacco, separarla dalla cella: **mai** lasciare la cella collegata e **mai** mettere in corto i suoi fili |
| 7 | Fusibile **3 A** in serie sul positivo (opzionale ma consigliato) | protezione se l'alimentatore non limita bene |
| 8 | Cavo USB tra tablet e PC (con Tab2Monitor/adb) | **non** collegare un caricatore durante la prova |
| 9 (alt.) | Se manca il banco: modulo **buck regolabile** (LM2596/XL4015, 5 V → 3,9 V, ≥3 A) da caricatore USB-C 5 V/3 A | impostare e **misurare 3,9 V a vuoto prima** di collegare il tablet |

### Schema
```
 Alimentatore 3,9 V (limite 0,5 A) ──[amperometro]──[fusibile 3A]──┬── fili ROSSI  (pin 1+2, V_BATT)
                                                                    │
                                                               [470-1000 µF]
                                                                    │
 Alimentatore GND ──────────────────────────────────────────────────┴── fili NERI   (pin 4+5, GND)

 filo BLU (pin 3, VF) ──[10 kΩ 1 %]── fili NERI (GND)
```

### Sequenza
1. Tablet spento; **scollegare e togliere la batteria**, nessun caricatore/USB di alimentazione.
2. Senza collegare il tablet: impostare 3,9 V, limite 0,5 A; verificare con multimetro la tensione e la polarità sui capi dei fili.
3. Spegnere l'alimentatore, collegare rossi/neri/blu come da schema (blu con 10 kΩ), ricontrollare che nessun pin tocchi i vicini.
4. Accendere l'alimentatore: la corrente a riposo deve essere ~0 (o poche decine di mA). Se sale al limite subito: spegnere e cercare il corto.
5. Premere il tasto di accensione: osservare avvio, corrente (picco all'avvio), logo.
6. Se non parte: alzare il limite a 1 A, poi a 2 A (un passo alla volta); se ancora no, sospettare BAT_REMOVAL / VF (⏳ capire l'altra rete).
7. Se parte: annotare corrente a schermo spento/acceso/luminosità massima, percentuale batteria (Tab2Monitor/`adb shell dumpsys battery`), riavvii, spegnimenti spontanei.
8. Fine: spegnere il tablet, poi l'alimentatore, poi scollegare.

### Cosa registrare e criteri
| Prova | ✅ buono | ⛔ problema |
|---|---|---|
| Avvio con 10 kΩ sul blu | arriva ad Android | resta spento / si spegne: VF o BAT_REMOVAL non soddisfatti |
| Corrente a regime | <1 A, picchi <2 A senza cali di tensione | tensione cala o riavvii: più capacità/alimentatore più robusto |
| Percentuale batteria | plausibile (3,9 V ≈ 80 %) e stabile | salti o 0 %/100 % a caso: il fuel gauge non è affidabile senza cella |
| Spegnimento per «batteria scarica» | assente | presente: Android spegne per la lettura del fuel gauge |

### Rischi e decisioni
- La V_BATT non va superata oltre **4,2 V**: oltre si rischia la scheda; 3,9 V scelti per restare in zona sicura e avere una percentuale media-alta.
- Il MAX17042 senza cella può dare percentuale incoerente: non è un blocco per la prova, ma per l'uso reale col monitor va valutato.
- Senza batteria il tablet si spegne **subito** se l'alimentazione cade: per l'uso reale servirebbe un alimentatore stabile (e, volendo, un UPS).
- Rollback: rimettere la batteria originale (nessuna modifica permanente al tablet con questa prova).

## Come far credere al tablet che la batteria ci sia (2026-11-12, dallo schema scaricato di nuovo in `scratchpad/p5100_docs/`, non committato)
- **Cosa legge il tablet dal connettore batteria:** solo V_BATT (pin 1-2, alimentazione di tutto il sistema, vista anche dal MAX17042 e dal PMIC) e
  **VF** (pin 3): VF va al GPADC_IN0 del TWL6032 (via R769) e all'AIN del MAX17042 (via R729) e serve a misurare la temperatura/presenza
  del pacco tramite il NTC da 10 kΩ misurato. Nessun altro filo (niente linea dati), i pin 6-9 sono NC.
- **Inganno:** alimentare i pin 1-2 a ~3,9 V + 10 kΩ 1 % tra pin 3 e GND (pin 4-5). Il NTC a ~25 °C vale 9,83 kΩ: 10 kΩ = «temperatura normale».
- **BAT_REMOVAL:** nello schema è una rete tra il pin BATREMOVAL del TWL6032 (J10) e il processore (pagine 6-7); **non passa dal connettore
  batteria** e non ha componenti sul percorso → il rilevamento della rimozione è probabilmente interno al PMIC (da VBAT/GPADC), non da un pin dedicato
  sul connettore. ⚠️ Inferenza dallo schema, non dal datasheet del TWL6032: si conferma con la prova.

## Idea dell'utente: due condensatori al posto delle celle, scheda BMS e connettore originali (2026-11-12) — valutazione, non provata
Idea: togliere le celle del pacco SP3676B1A, tenere la scheda di protezione (BMS/PCM) con il suo connettore a 5 fili, mettere due condensatori
al posto delle celle e alimentare così il tablet dal connettore originale.
- **I condensatori non sono una sorgente:** accumulano poca energia. Esempio: una supercap da 10 F che cala di 1 V a 0,5 A dura ~20 s
  (t = C·ΔV/I); un tablet che assorbe 0,5–2 A scarica qualunque condensatore ragionevole in secondi. Servono comunque un alimentatore
  esterno a ~3,9 V sui pad delle celle (B+/B−) o direttamente sui fili rossi/neri. I condensatori sarebbero solo un tampone per i picchi.
- **Il tampone serve già, ma più piccolo:** 470–1000 µF elettrolitico vicino ai pin (già nella lista della prova a banco).
  Una supercap (qualche F, ≤2,7 V l'una, due in serie = ≤5,4 V con bilanciamento) dà solo qualche secondo di autonomia e ha resistenza
  interna che fa calare la tensione ai picchi: non vale la complicazione.
- **Se invece si lascia caricare dal tablet (caricatore SMB347 dal cavo USB) con i condensatori come «batteria»:** sconsigliato ⛔. Il caricatore
  carica a CV 4,2 V e termina quando la corrente cala; con un condensatore la corrente cala subito, poi il tablet scarica la cella finta in
  secondi e riparte a caricare: cicli continui, percentuale senza senso, riavvii. Non è un modo per «alimentare il tablet».
- **Rischio sulle celle:** togliere le celle da un pacco richiede di tagliare/dissaldare le linguette; un cortocircuito su una cella Li-Po
  può incendiarla. Meglio **non toccare le celle**: staccare il pacco intero e alimentare i fili del connettore del tablet.
- **Cosa darebbe di buono la scheda BMS:** protezione da sovracorrente/sovratensione/sottotensione e il NTC originale. Lo stesso si ottiene
  con alimentatore a limite di corrente (+ fusibile 3 A) e la resistenza da 10 kΩ sul filo blu.
- **Decisione:** si procede con la prova a banco (alimentatore su rossi/neri, 10 kΩ sul blu, 470–1000 µF), non con i condensatori al posto delle
  celle. Si riconsidera solo se la prova mostra cadute di tensione ai picchi.

## Prova senza alimentatore da banco: caricabatterie USB sul connettore 30 pin, batteria scollegata elettricamente (2026-11-12) — ⏳ non provata
Richiesta dell'utente: l'alimentatore da banco è scomodo; usare invece il connettore di carica e un caricabatterie USB.
**Idea (reversibile, senza saldature sul tablet):** lasciare il pacco SP3676B1A al suo posto e col suo connettore inserito, ma **interrompere i due
fili rossi** (positivo): tagliare e isolare ciascun capo, oppure inserire un interruttore/jumper sul rosso. Così il NTC (blu–nero, 9,83 kΩ)
resta visto dal tablet, e la cella **non** alimenta più il tablet (nessuna corrente nei fili rossi). Il tablet è poi alimentato solo dal
caricabatterie sul 30 pin (cavo originale, caricatore Samsung 2 A, che il tablet riconosce).
- **Cosa non so (⚠️ dallo schema, pagina 7, blocco «SWITCHING CHARGING»):**
  - Lo SMB347S (U716) ha uscita VSYS e pilota un FET (Q700, via FETDRV) verso il nodo V_BATT: è un caricatore con percorso di alimentazione.
    Se **senza cella** mantiene V_BATT a ~4,2 V e dà corrente al sistema (fino al limite d'ingresso ~1,5–2 A) il tablet si avvia; se invece
    rileva «batteria mancante» e spegne il FET, V_BATT cade e il tablet non parte. Non risolvibile dallo schema: **si prova**.
  - Il pin THERM dello SMB347S sembra andare a una resistenza locale (R732) e non al filo VF: quindi il caricatore **non** legge il NTC della
    batteria (⚠️ lettura dell'immagine dello schema).
  - Android/kernel Samsung possono segnalare «batteria rimossa/assente» e spegnere dopo qualche secondo, anche se l'hardware regge.
- **Cosa osservare:** avvio (logo, Android); corrente dal caricatore (misuratore USB in linea); tensione ai capi dei fili rossi lato tablet
  (4,0–4,2 V attesi se il percorso di alimentazione tiene); percentuale e stato carica (Tab2Monitor / `adb shell dumpsys battery`);
  riavvii a schermo acceso e luminosità massima.
- **Esiti possibili e cosa fare:** parte e resta acceso → soluzione senza banco (⏳ da valutare a lungo termine: nessun tampone, spegnimento
  immediato se manca la rete); non parte → il FET non si chiude senza cella: tornare alla prova a banco; parte ma si spegne → problema software
  di «batteria assente»: valutare, solo allora, di tenere la cella (o i condensatori) come simulatore.
- **Rollback:** riunire i due fili rossi (saldatura o nastro conduttivo) e controllare con il multimetro 3,7–4,2 V sui rossi–neri.
- **Rischi:** i due capi dei fili rossi tagliati vanno isolati subito e mai toccare i neri o altri contatti della cella; non lasciare il tablet
  acceso con la cella scollegata senza controllare le temperature del tablet.

### Chiarimento dell'utente (2026-11-12) e seconda strada, la cella piccola
L'utente precisa l'obiettivo: il tablet deve solo **sentire una batteria presente, anche appena carica**, e **alimentarsi direttamente dal connettore**
(caricabatterie USB sul 30 pin). La prova sopra (fili rossi interrotti, NTC visto) è la prima da fare. Se il tablet non parte senza una cella vera
collegata ai rossi (⚠️ dipende dallo SMB347S, vedi sopra), seconda strada:
- **Cella piccola come «batteria di presenza»:** al posto del pacco da 7000 mAh, una cella Li-ion/Li-Po di recupero da telefono (~500–1500 mAh)
  **con la sua scheda di protezione (PCM)** e un **NTC/resistenza 10 kΩ** sul filo VF (pin 3). Il tablet la vede come batteria, il caricatore la porta a 4,2 V
  in pochi minuti, poi alimenta il sistema dal caricabatterie (percorso di alimentazione) e la cella resta quasi ferma.
- **Pro:** MAX17042 e PMIC vedono una batteria reale; piccolo tampone ai picchi; protezione PCM.
- **Contro / rischi:** la cella resta a 4,2 V fissi (invecchia e può gonfiarsi se è vecchia): usare una cella sana, ispezionarla spesso, non lasciare il
  tablet incustodito a lungo; la percentuale resta ~100 %; il limite 40–80 % non si ottiene (il kernel non ha un nodo per sospendere la carica, verificato).
- **Decisione:** prima la prova con i fili rossi interrotti; solo se fallisce, la cella piccola. Il pacco da 7000 mAh resta intatto in entrambi i casi.

### Esito della prova coi fili rossi interrotti (2026-11-12, riferito dall'utente) — ⛔ non funziona
- L'utente ha isolato i due fili rossi del pacco (NTC blu–nero ancora collegato), caricabatterie USB sul 30 pin: **il tablet non si accende**.
  (Un primo «si è acceso» era dovuto a un rosso ancora collegato; l'utente ha poi corretto.) Non misurato altro (tensioni, corrente del caricatore).
- Conclusione: il caricatore interno (SMB347S) **non alimenta il sistema senza una cella collegata ai pin 1-2** (V_BATT), almeno in queste condizioni:
  probabile che serva batteria presente/carica oltre una soglia per chiudere il percorso di alimentazione, oppure che il tablet non parta con il solo
  ingresso USB. ⚠️ causa non verificata (nessuna misura su V_BATT con il caricatore collegato).
- **Passi successivi, in ordine di semplicità:**
  1. (opzionale, 5 minuti) con caricatore collegato misurare la tensione sul lato tablet dei rossi: se è ~0 V il FET Q700 non si chiude senza cella;
     se è > 3 V ma il tablet non parte, il problema è un altro (PMIC/software).
  2. **Modulo buck regolabile** (5 V → 3,9 V, ≥3 A) alimentato dal caricabatterie USB e collegato ai rossi: non serve il banco, sta in una scatolina,
     il tablet vede V_BATT e il NTC (10 kΩ o il pacco). Il 30 pin resta scollegato.
  3. **Cella piccola con PCM** al posto del pacco (vedi sopra): il caricatore la porta a 4,2 V e poi alimenta il sistema.

### Misura dell'utente (2026-11-12): con cavo di carica collegato e senza cella sui rossi ci sono ~4,7 V che «oscillano» — ✅ misurato, causa ⚠️ ipotesi
- Condizioni: fili rossi del pacco interrotti; misura sul lato tablet dei rossi (rispetto ai neri) con il caricabatterie sul 30 pin; il tablet non parte.
- Lettura: il caricatore (SMB347S) porta il nodo V_BATT/VSYS a ~4,7 V (cioè ~ ingresso 5 V meno la caduta del FET, più di 4,2 V di una cella)
  e la tensione **non è stabile**. Un multimetro non mostra la forma d'onda; il comportamento è tipico di un caricatore che senza batteria entra in
  **ripetizione di avvio (hiccup)** o non ha nessun carico/capacità sull'uscita.
- Rischio: 4,7 V sono sopra il massimo di una cella Li-ion (4,2 V); non tenere il tablet acceso a lungo a questa tensione.
- **Prova economica da fare (5 minuti, ⏳):** collegare un condensatore **470–1000 µF / ≥10 V** (polarità!) tra rossi (lato tablet) e neri, caricatore
  collegato, e misurare di nuovo: se la tensione diventa stabile (e scende verso 4,2 V) provare ad accendere. In seconda battuta un carico fittizio
  (resistenza ~100 Ω 0,5 W, ~45 mA) in parallelo. Se non basta: modulo buck 3,9 V sui rossi o cella piccola con PCM.

### Misura dell'utente (2026-11-12): senza cella, col caricatore sul 30 pin, sui rossi lato tablet ~4,7 V che oscillano — ✅ misurato (valore a multimetro), causa ⚠️ ipotesi
- 4,7 V è **sopra il massimo di una cella Li-ion (4,2 V; V_BATT del PMIC tollerato ~4,5 V circa, ⚠️ non verificato sul TWL6032)**. L'oscillazione fa pensare
  che lo SMB347S, senza cella (batteria mancante), faccia un tentativo/ripresa continui («hiccup») e non regoli la tensione in modo stabile: ecco perché il tablet non parte.
  Il tablet non va avviato con 4,7 V sui rossi: rischio per PMIC/fuel gauge.
- **Decisione:** abbandonata la strada «caricatore del tablet senza cella». ⛔ (motivo: V_BATT instabile e troppo alta senza cella.)
- **Strada scelta: modulo buck 5 V → 3,9 V sui rossi** (il 30 pin non si usa). Poiché il pacco resta collegato coi soli neri e il blu, il **NTC originale
  è già visto dal tablet**: non serve nemmeno la resistenza da 10 kΩ. Lista: caricatore USB 5 V ≥2 A (o presa USB-A/C breakout), modulo buck regolabile
  ≥3 A (tipo MP1584/XL4015, con regolazione), condensatore 470–1000 µF/10 V sull'uscita, fusibile 3 A, multimetro.
  Collegamenti: ingresso del buck dal USB 5 V; uscita 3,9 V → lato **tablet** dei due fili rossi (rossi lato cella restano isolati), GND uscita → neri.
  **Regolare e misurare 3,9 V a vuoto prima di collegare il tablet.** Alimentare il buck solo da un caricatore che regga 2 A.

## Lettura via adb del tablet sotto carica (2026-11-12, adb Wi-Fi 192.168.1.208:5555 con `adb root`) — ✅ misurato
Condizioni: Android LineageOS 14.1 (kernel 3.0.101), pacco originale collegato, caricabatterie USB sul 30 pin (tipo non annotato: l'utente ne ha provati
diversi, anche da 3 A), batteria ~8–9 %. Letture da `/sys/class/power_supply/battery` (richiedono root: `adb root`) e da `dumpsys battery`.
| Condizione | `current_avg` (MAX17042) | Tensione cella | Significato |
|---|---|---|---|
| Schermo **acceso** | **−850 mA** (stabile −847…−853) | 3,48–3,51 V | la batteria si **scarica** anche con il caricatore collegato |
| Schermo **spento** (25 s dopo `input keyevent 223`) | **+142 → +212 mA** (in salita) | 3,74 V | carica, ma con soli ~0,2 A verso la cella |
- Il sistema vede il caricatore come **AC** (`AC powered: true`, `status Charging`, `power_supply/ac/online=1`, `type Mains`); `usb/online=0`. dmesg:
  «Charger type is [AC], adc = 2743», «smb347-charger 5-0006: charging current limit set to 1.8A for all power supplies» (patch del kernel LineageOS).
- Quindi **il riconoscimento del caricatore NON è il problema**: il tablet lo prende per AC e imposta 1,8 A. Eppure l'energia che arriva davvero è poca:
  il consumo del tablet a schermo acceso supera ciò che entra di circa 0,85 A. Con lo schermo spento resta ~0,2 A per caricare.
- Ipotesi (⚠️ da verificare con un misuratore USB in linea, non è stata misurata la corrente d'ingresso): la sorgente non regge la corrente (cavo 30 pin lungo/sottile con
  caduta di tensione, caricatore che non tiene i 5 V sotto carico), oppure il limite effettivo dello SMB347S è più basso di quello scritto. Il consumo totale è circa
  corrente d'ingresso + 0,85 A a schermo acceso.
- L'uptime del tablet era di soli 126 s durante la lettura: il tablet si era riavviato da poco (non so se per scelta dell'utente o per un calo di alimentazione).
- Lo schermo è stato riacceso a fine prova (`keyevent 224`). `adbd` resta in modalità root finché non si riavvia il tablet.

## Misura con misuratore USB in linea (2026-11-12, foto dell'utente) — ✅ misurato
Lettura sul misuratore tra caricabatterie e cavo 30 pin (tablet acceso, Android, schermo acceso al momento della lettura via adb): **5,072 V, 0,529 A**
(2,68 W), 9,58 Ω di carico, 117 mAh e 0,613 Wh accumulati in 10 min 23 s, 25 °C. Caricatore usato: non annotato (scatola nera con presa a muro).
Nello stesso momento via adb: corrente cella **−895 mA**, 3,42–3,43 V, capacità 7 %.
- **La tensione d'ingresso regge (5,07 V): il caricatore e il cavo non cadono sotto carico.** Il problema è che il tablet **assorbe solo ~0,53 A**: è il limite
  d'ingresso di ~500 mA di una porta USB standard, **non** quello di 1,8 A scritto dal kernel.
- Bilancio a schermo acceso: ingresso 2,7 W (~0,5 A a 5 V) contro un consumo totale di ~4,8–5 W → la cella copre la differenza (~0,85–0,9 A). Per non scaricarsi a
  schermo acceso servono almeno ~1,1–1,2 A in ingresso; per caricare, di più.
- **Causa probabile (⚠️ ipotesi):** il software (driver Samsung/Lineage) legge il tipo di caricatore dal **pin ID del 30 pin** (dmesg: «Charger type is [AC], adc = 2743»)
  e scrive 1,8 A nel registro dello SMB347S, ma il chip ha **anche un rilevamento hardware su D+/D−** (APSD): se il caricatore **non mette D+ e D− in corto** (o a
  ~1,2 V), lo SMB347S lo tratta come porta USB standard e limita l'ingresso a 500 mA, qualunque cosa dica il software. Coerente con 0,529 A.
- **Prossimi test, in ordine:**
  1. Stesso cavo e stesso misuratore con il **caricatore Samsung originale 2 A**: deve dare >1 A a schermo acceso. Se sì, è il caricatore generico a non marcare D+/D−.
  2. Con il caricatore generico: **mettere in corto D+ e D−** sul lato USB-A (adattatore «charge‑only» con dati cortocircuitati, oppure saldatura/morsetto sui due pin
     dati di una presa USB‑A di appoggio) e rileggere la corrente.
  3. Se non cambia: provare il partitore a ~1,2 V su D+/D− (33 kΩ / 10 kΩ).
- Per far ricaricare il pacco nel frattempo: **tenere lo schermo spento**.

### Prova D+/D− in corto con una prolunga USB-A (2026-11-12, utente senza caricatore Samsung originale) — ⏳ da fare
Procedura senza saldature: prendere una **prolunga USB-A maschio→femmina** (o un cavo USB‑A di scarto); sul cavo si aprono 2–3 cm di guaina, si trovano i 4 fili
(rosso = 5 V, nero = massa, **verde = D+, bianco = D−**; ⚠️ colori standard, da verificare col multimetro), si **tagliano solo il verde e il bianco** e si **uniscono tra loro
tutti e quattro i capi** (D+ e D− di entrambi i lati), isolando con nastro; rosso e nero restano intatti. Si inserisce tra caricatore e cavo 30 pin, con il misuratore USB in linea.
- **Verifica prima di collegare il tablet:** resistenza tra i due pin dati < 200 Ω (deve essere ~0 Ω), nessun contatto con rosso/nero.
- **Atteso:** corrente d'ingresso a schermo acceso > 1 A (oggi 0,53 A) e `current_avg` della cella ≥ 0 mA. Se resta ~0,5 A, passare al partitore a ~1,2 V (33 kΩ da 5 V a D+,
  10 kΩ da D+ a massa, D− unito a D+) o escludere il caricatore usato.
- Rischio: basso; mai collegare D+/D− a 5 V o a massa. Rollback: togliere la prolunga.

## Verifica bug di ricarica in Lineage / recovery (2026-11-12) — ✅ Lineage verificato, ⏳ recovery non provata
Risultato: **nessun bug di configurazione trovato nell'immagine LineageOS 14.1 (andi34, kernel 3.0.101)**; la recovery TWRP non è stata provata.
- Ricerca online: nessuna segnalazione di bug di ricarica per espresso3g/P5100 su Lineage 14.1 o TWRP (le uniche note generali sono limiti di temperatura e, su altri
  telefoni, flag di ricarica rapida Samsung, non applicabili qui).
- Riuso del lavoro del 2026-10-23 (CHANGELOG voce 20, CONTEXT «Analisi driver»): il kernel imposta 1,8 A d'ingresso per qualsiasi caricatore
  («charging current limit set to 1.8A for all power supplies, html6405»).
- **Registri SMB347 riletti oggi** (bus 5, addr 0x06, sola lettura, `/data/local/tmp/smbdump` già presente sul tablet; schermo acceso, cella −888 mA, 3,37 V, 6 %):
  `00=DD` (corrente di carica 2,2 A), `01=66` (limite d'ingresso 1,8 A), `03=63` (tensione di fine carica 4,2 V), `05=1A`, `06=60`, `31=03` (modo ad alta corrente),
  `30=80`, `3D=05` (carica rapida attiva), `3B=A3`, `3C=3E`, `3E=00`, `3F=86` (questi ultimi non decodificati: serve il datasheet). Nessun errore evidente.
  ⚠️ La lettura dei registri di stato 3B/3C/3F non è stata interpretata: non si sa se uno indichi un limite effettivo più basso.
- Quindi: il driver chiede la corrente giusta e il chip è configurato per 1,8 A, ma il misuratore USB indica **0,53 A in ingresso** (5,07 V). Dato che l'ingresso era
  già ~0,5 A sia con la USB del PC sia con due caricatori da muro (CONTEXT, 2026-10-22/23), il limite **non dipende dal caricatore** e **non dal software**.
- Sospetti rimasti: percorso fisico (cavo 30 pin, **contatti del connettore 30 pin usurati/sporchi** che fanno cadere la tensione sul lato tablet),
  oppure una limitazione interna dello SMB347S (dello stato non decodificato). Non misurata la tensione sul lato tablet.
- **Test decisivo, da fare (richiede il riavvio del tablet in recovery, ⏳ solo con conferma dell'utente):** in TWRP (dove non gira il driver Lineage)
  leggere con il misuratore USB la corrente a schermo acceso: ~1 A o più = problema software di Lineage; ~0,5 A anche lì = hardware/cavo/connettore.

### Esito del test in recovery (2026-11-12, riferito dall'utente con misuratore USB) — ✅ misurato
- In **TWRP** (senza driver Lineage) l'ingresso è ancora **~0,5 A**, uguale a Lineage.
- Conclusione: **non è un bug del sistema Lineage né della recovery**; il limite è a monte del software: caricatore/cavo/connettore oppure il rilevamento hardware
  del caricatore dello SMB347S (APSD su D+/D−). Coerente con: stesso ~0,5 A con porta PC e con due caricatori da muro (CONTEXT, 2026-10-22/23).
- **Prossimi test, in ordine di costo:** (1) D+ e D− in corto con la prolunga USB-A (procedura sopra); (2) pulizia dei contatti del connettore 30 pin e prova con un altro cavo 30 pin;
  (3) caricatore Samsung originale 2 A, se se ne trova uno; (4) partitore a ~1,2 V. La prova 1 è la più probabile e la più economica.

### Cambio cavo 30 pin (2026-11-12, riferito dall'utente) — ✅ misurato
- Con un **altro cavo** 30 pin l'assorbimento in recovery sale da ~0,5 A a **~0,7 A** (+40 %). Tensione/altro non annotati.
- Lettura: il **cavo conta** (a parità di caricatore e di recovery cambia la corrente), quindi il limite non è solo il rilevamento D+/D− del chip. Ipotesi (⚠️ non provate):
  resistenza del cavo/contatti (il misuratore è sul lato caricatore e non vede la caduta sul lato tablet) → sotto un certo livello di tensione d'ingresso il caricatore del tablet
  riduce la corrente; oppure il nuovo cavo ha D+/D− o il pin ID cablati diversamente.
- Dal 0,5 A al 0,7 A non è ancora l'1–1,8 A atteso: probabilmente il nuovo cavo è migliore ma non ottimo, oppure restano contatti sporchi/D+/D− non marcati.
- **Prossimo:** cavo **corto e spesso** (30 pin→USB-A in 20 AWG, come i cavi originali Samsung/ricambi «20AWG»), contatti del connettore del tablet puliti (alcol isopropilico, stuzzicadenti), e poi D+/D− in corto.

### Misuratore USB con il nuovo cavo (2026-11-12, foto dell'utente) — ✅ misurato
Tablet in recovery, schermo acceso: **5,097 V, 0,680 A** (~3,4 W, 7,49 Ω di carico, 1 h 01 min di prova), ~28 °C. Prima, con il vecchio cavo: 5,072 V e 0,529 A.
- La tensione **sul lato caricatore** è stabile a ~5,1 V in entrambi i casi: il caricatore non cede. La caduta di cavo e contatti non si vede da qui (misuratore lato caricatore).
- Ipotesi più coerente (⚠️ non provata): ai due pin di alimentazione del 30 pin (7 e 8, V_BUS) il **contatto è cattivo** (connettore del tablet o spine del cavo): con corrente di 0,5–0,7 A
  basterebbero 1–3 Ω di resistenza di contatto per far crollare la tensione sul lato tablet; il caricatore interno riduce allora l'assorbimento (limite sulla tensione d'ingresso).
  Un cavo con contatti migliori dà più corrente: coerente con 0,53 → 0,68 A.
- Cosa si può misurare per confermarlo (senza strumenti nuovi): tensione tra V_BUS e massa **sul lato tablet** a carico (sonda sui pin 7/8 e 1/2 del connettore del tablet a spina inserita: difficile) oppure
  provare cavi diversi e la pulizia dei contatti e vedere se la corrente sale ancora.

### Ricerca di un cavo/caricatore migliore (2026-11-12, solo ricerca, nessun acquisto) — ⏳
Trovati online (dati dai negozi, non da schede tecniche; ⚠️ la sezione dei fili è dichiarata solo dal venditore):
- **Cavo originale Samsung ECC1DP0U**, 30 pin → USB-A, 1 m (rivenditori in Irlanda e Australia); altre inserzioni lo danno 3 m: scegliere **1 m** o meno.
- **JuicEBitz «Super Core 20AWG Tip-Top»** 30 pin → USB-A (Regno Unito/desertcart), conduttori di rame stagnato (inserzione: 20*2C+28*2D); ne esiste anche uno da 0,5 m a 22 AWG.
- **Cellonic** cavo 1 m (subtel) e **caricatore 5 V / 2 A con cavo 30 pin integrato da 1,2 m** (subtel): provare anche questo come caricatore completo per il Tab.
- **Samsung ETA-P10JBEGSTA** (caricatore 2 A con cavo 30 pin staccabile, elencato da un rivenditore di ricambi) = l'alimentatore originale del tablet.
- Criteri: cavo **corto** (≤1 m), fili di alimentazione **spessi**, spine del 30 pin con contatti puliti; uno stesso cavo non garantisce la corrente se il limite è sul connettore del tablet.

### Dopo la pulizia dei contatti con alcol isopropilico (2026-11-12, riferito dall'utente) — ✅ misurato / ⚠️ spiegazione ipotetica
- Pulito il connettore del tablet: **nessun cambiamento, sempre 0,7 A**. I contatti del 30 pin non sono quindi il problema principale.
- **Decodifica dei registri SMB347 letti** (driver Linux upstream): `3B=A3` → tensione di fine carica 4,2 V; `3C=3E` → corrente di carica in uso 2,2 A (tabella FCC); `3D=05` → carica abilitata, stato
  «fast»; `00=DD`/`01=66` → 2,2 A di carica e 1,8 A d'ingresso. Cioè **il chip crede di essere in carica rapida con 1,8 A permessi**, ma dal caricatore entrano solo 0,5–0,7 A.
  Il datasheet Summit (con i bit di `3F`) non è stato trovato online: `3F=86` resta non decodificato.
- **Ipotesi di lavoro (non provata): resistenza serie del percorso di alimentazione.** Se il chip riduce l'assorbimento per tenere la tensione sul proprio ingresso sopra ~4,3 V
  (soglia tipica, ⚠️ non verificata per lo SMB347), la corrente massima è circa I = (5,1 V − 4,3 V) / R. Con R ≈ 1,2–1,5 Ω si ottengono proprio 0,53–0,68 A.
  Una resistenza di 1,2–1,5 Ω è realistica per un cavo sottile (28–30 AWG, ~0,4–0,7 Ω andata e ritorno su 1 m) più le spine USB-A, il misuratore in linea e il 30 pin.
  Un cavo **20 AWG corto** porterebbe R sotto 0,3 Ω e la corrente verso >1,5 A, se l'ipotesi è giusta.
- **Test economici prima di comprare:** (a) togliere il misuratore dal percorso e leggere solo `current_avg` via adb (nei valori di lunedì: schermo spento +142…+212 mA con il misuratore); (b) prova con un cavo molto corto
  (~0,3 m) se se ne ha uno; (c) misurare la tensione tra V_BUS e massa sul lato tablet a carico, se si riesce a raggiungere i contatti.
- **Acquisto consigliato (⏳):** cavo 30 pin → USB-A 20 AWG di ≤1 m (vedi ricerca sopra).

### Lettura adb con il nuovo cavo, schermo spento (2026-11-12) — ✅ misurato
Tablet in Android, **schermo spento** (`mWakefulness=Asleep`), caricatore AC riconosciuto (`AC powered: true`, `status Charging`), appena riavviato (uptime 77 s), `adb root` rifatto.
Sei letture in 15 s da `/sys/class/power_supply/battery`: `current_avg` **+439, +443, +444, +446, +449, +448 mA** (carica), tensione 3,74 V, capacità indicata **1 %** (non coerente con 3,74 V: il fuel gauge si
è azzerato/ricalibrato dopo il riavvio, ⚠️ da tenere d'occhio).
- Confronto a schermo spento: vecchio cavo **+142…+212 mA** → nuovo cavo **~+445 mA**: la carica netta nella cella è **più che raddoppiata** cambiando solo il cavo.
  Conferma che il cavo/percorso di alimentazione è il fattore principale (ipotesi della resistenza serie).
- Resta da vedere a schermo acceso (prima −850…−900 mA) e quale corrente d'ingresso dà il misuratore ora a schermo spento.

### Lettura adb con il nuovo cavo, schermo acceso (2026-11-12) — ✅ misurato
Schermo acceso via `input keyevent 224` (luminosità 226/255 dalla lettura del 22 ottobre), caricatore AC, nuovo cavo. `current_avg` è una media mobile: parte da +23 mA e
scende in ~20 s fino a **−622 … −656 mA** (stabile, ultime 10 letture: −622, −632, −637, −634, −638, −638, −648, −656, −644, −652), tensione cella 3,45–3,50 V, capacità indicata ancora 1 %.
- Confronto a schermo acceso: vecchio cavo **−850…−900 mA** → nuovo cavo **~−640 mA**: miglioramento di ~**230 mA**, ma la cella si **scarica ancora**.
- Schermo spento nuovo cavo: +445 mA (vedi sopra). Differenza tra schermo spento e acceso ≈ 1,09 A (consumo dello schermo + app).
- Quadro: l'ingresso è cresciuto (~0,53 → ~0,7 A + ancora un po' dal cavo), ma per pareggiare a schermo acceso servirebbe ~+0,65 A in più nella cella, cioè circa 1,2 A d'ingresso totali.
  Ipotesi: cavo ancora troppo resistivo; oppure ridurre il consumo (luminosità più bassa, meno risvegli).

### Prova con luminosità ridotta (2026-11-12) — ✅ misurato
Nuovo cavo, schermo acceso, luminosità abbassata da 226 a 110 (su 255), timeout schermo portato a 10 min durante la prova e poi ripristinato a 60 s, luminosità riportata a 226.
`current_avg` si assesta a **+20 … +32 mA** (tensione 3,63–3,65 V) contro −640 mA a luminosità 226: la cella non si scarica più, ma nemmeno si ricarica (pareggio).
Conclusione: la retroilluminazione pesa circa 0,65 A; con luminosità ~110 il tablet regge sul caricatore da 0,7 A. Per ricaricare serve luminosità più bassa o schermo spento.
Nota: una prima misura a luminosità 110 era falsa (schermo spento per timeout, +443 mA): controllare sempre `dumpsys power | grep mWakefulness`.
