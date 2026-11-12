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
