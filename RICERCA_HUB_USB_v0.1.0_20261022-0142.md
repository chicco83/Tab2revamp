# Ricerca: alimentazione comandabile per limitare la carica del Galaxy Tab 2 (GT-P5100)
Versione: v0.1.0 — 2026-10-22 01:42 (ora del PC) — sessione «Ricerca hub USB comandabile per la carica del tablet»
Stato: **solo ricerca online, nessun acquisto, nessun codice modificato.** Niente è stato provato sull'hardware: dove una cosa non è
confermata da una fonte lo scrivo (⚠️).

## 1. Obiettivo e vincoli
Tenere la batteria del tablet tra ~40% e ~80% (carica/scarica alternate) comandando l'alimentazione dal server Python
Tab2Monitor (PC Windows 11, porta 8090). Fatti già verificati nel progetto (vedi CONTEXT.md):

- il kernel del tablet non ha un nodo per sospendere la carica → serve un controllo **esterno** dell'alimentazione;
- sulla USB del PC (porta dati SDP, 500 mA) il tablet a schermo acceso **si scarica** (−730 mA), a schermo spento carica solo +80…+156 mA;
- con il caricatore Samsung 2 A carica normalmente;
- `uhubctl` non funziona su Windows (winusb.sys).

## 2. Cosa si sa del lato tablet (punto critico: quale "firma" di carica riconosce)
- Il device tree mainline del Tab 2 10.1 (`samsung-espresso10`) descrive il caricatore come **Summit SMB347**, con limite di corrente USB di
  **1,8 A** ([patch v9](https://lkml.iu.edu/hypermail/linux/kernel/2603.0/05062.html)). Il PMIC è un TWL6032.
- Summit dichiara per SMB346/SMB347 il supporto USB Battery Charging 1.2 e il **rilevamento automatico del tipo di sorgente**
  (host/hub/caricatore) ([comunicato Summit](https://eepower.com/new-industry-products/summit-introduces-tiny-programmable-usbac-switch-mode-battery-chargers-with-integrated-currentpath-manager/)).
  ⚠️ Non ho trovato il datasheet dell'SMB347 né una fonte che dica *quali* firme (DCP, CDP, divisore Samsung) il Tab 2 accetta e a
  quanti mA. Non è documentato in fonti pubbliche che ho raggiunto.
- BC1.2: un DCP (caricatore dedicato) si riconosce perché **D+ e D− sono in corto** (max 200 Ω); un CDP risponde alla seconda fase di
  rilevamento ([Analog Devices, panoramica BC1.2](https://www.analog.com/en/resources/technical-articles/overview-of-usb-battery-charging-revision-12-and-the-important-role-of-charger-detectors.html)).
  Testimonianze di forum (non verificate) dicono che per un Tab 2 basta cortocircuitare D+/D− per fargli assorbire fino a ~1 A, mentre
  alcuni caricatori Samsung usano un partitore e il tablet assorbe di più su quelli
  ([forum](https://forum.earlybird.club/goto/post?id=3975109), [TI E2E](https://e2e.ti.com/support/power-management-group/power-management/f/power-management-forum/180904/about-tps2543)).
- **Conclusione operativa**: una porta dati di hub (anche alimentato) è SDP/CDP; sul CDP il tablet *potrebbe* assorbire fino a ~1,5 A, ma
  ⚠️ non è provato. Il caricatore Samsung 2 A è l'unica sorgente **già verificata** (carica normalmente). Quindi la strada sicura è
  **lasciare il caricatore Samsung e interrompere a monte** (presa o relè), non spostare la carica su una porta di hub.

## 3. Confronto delle opzioni

| # | Opzione | Prezzo indicativo (IT/UE) | Windows / API verificata | Corrente e firma di carica | Come lo comanda il server Python | Giudizio |
|---|---|---|---|---|---|---|
| A | **Presa smart Shelly Plug S Gen3** + caricatore Samsung 2 A attuale | 23,68 € su shelly.com/it; 29,99 € MediaWorld | Nessun driver: HTTP in LAN, API RPC documentata ([doc Shelly](https://shelly-api-docs.shelly.cloud/gen2/Devices/Gen3/ShellyPlugSG3)) | Tutta la corrente del caricatore Samsung (firma D+/D− intatta) → **carica come oggi** | `urllib.request.urlopen("http://<ip>/rpc/Switch.Set?id=0&on=false")` (anche POST JSON); se l'autenticazione è attiva serve digest ([nota](https://taphome.com/en/compatibility/shelly-1pm-gen3/)) | ⭐ **Raccomandata** |
| B | **Presa Tasmota** (Sonoff S26, Athom PG01 preflashata) + caricatore Samsung | ~10–20 € (⚠️ non verificato in Italia) | HTTP `http://<ip>/cm?cmnd=Power%20Off` ([esempio](https://community.octoprint.org/t/how-do-i-control-my-tasmota-sonoff-with-psu-control-plugin/15068)); la risposta è JSON | Come A | `urlopen(".../cm?cmnd=Power%20Off")` | Valida e più economica; richiede firmware Tasmota (flash su S26) |
| C | **Relè USB seriale LCUS-1** (CH340) in serie al **solo filo +5 V** del cavo del caricatore Samsung | pochi € (⚠️ prezzo non trovato, schede ~3–6 € su marketplace) | pyserial a 9600 baud, frame `A0 01 01 A2` (on) / `A0 01 00 A1` (off) ([fonte](https://ifuturetech.org/product/lcus-1-5v-usb-plc-serial-control-relay-module/)); serve il driver CH340 su Windows | Contatto da 10 A: regge i 2 A; D+/D− intatti, quindi firma Samsung invariata (⚠️ non provato) | `serial.Serial("COMx",9600).write(bytes([0xA0,1,0,0xA1]))` | Economica e sempre sul PC, ma **modifica del cavo** (taglio/giunzione) e COM da gestire; usare i contatti **NC** così senza comando il tablet resta in carica |
| D | **Yepkit YKUSH3** (hub 3 porte) | 124,99 € (Yepkit, esaurito al momento della ricerca); 149 € + IVA su multi-com.eu | ✅ `ykushcmd.exe ykush3 -d 1` / `-u 1`, solo hidapi.dll, nessun driver ([guida Windows](https://www.yepkit.com/learn/setup-guide-ykush3_windows)); alternativa `pip install yepkit-pykush` ([pykush](https://github.com/Yepkit/pykush)) | Alimentazione esterna 5 V consigliata per carichi alti. ⚠️ Un progetto di terzi indica: CDP 1,5 A con dati, SDP 0,5 A, uscita 5 V commutata 6 A ([ykush3cmd](https://github.com/gerhard-schuster/ykush3cmd)); non confermato da Yepkit. Se la porta è CDP il Tab 2 potrebbe caricare ma non è provato | `subprocess.run(["ykushcmd.exe","ykush3","-d","1"])` o `pykush` | Funziona ma costa 5× la presa e non garantisce la corrente; unico vantaggio: porta dati e alimentazione sullo stesso cavo |
| E | **Acroname USBHub3+** | ~825–845 $ (listino Acroname) | Libreria BrainStem in Python, HubTool su Windows ([Acroname](https://acroname.com/node/849)) | Limite di corrente per porta programmabile fino a 4 A | `brainstem` API Python | Esagerato per il caso (prezzo) |
| F | **Cambrionix** (PDSync-C4, ThunderSync3-16 …) | da £399,99 (PDSync-C4) | API/CLI con licenza a pagamento su alcuni modelli ([docs](https://docs.cambrionix.com/products)); dettagli Python/Windows non verificati | CDP fino a 2,4 A (ThunderSync3-16) con profili per dispositivo | CLI/API del Toolkit | Esagerato per il caso (prezzo) |
| G | **uhubctl** | — | ❌ Non funziona su Windows (winusb.sys) — già verificato nel progetto | — | — | Scartata |

Note sui prezzi: sono quelli letti nelle pagine il 2026-10-22; vanno riconfermati al momento dell'acquisto.

## 4. Raccomandazione
1. **Shelly Plug S Gen3 + caricatore Samsung 2 A (opzione A)**, ~24 €. Motivi: carica già verificata col caricatore Samsung, nessuna
   modifica a cavi, nessun driver, API HTTP semplicissima dal server Python, funziona anche se in futuro il tablet cambia.
   Se l'utente preferisce un prodotto già "aperto": opzione B (Tasmota).
2. Se si vuole il minimo costo e non dispiace modificare un cavo: **relè LCUS-1 sul +5 V, contatti NC (opzione C)**.
3. **Sconsigliato per questo scopo**: YKUSH3 e gli hub professionali (D–F): costano 5–35 volte la presa e il punto critico (la firma/corrente
   di carica sulla porta dati) non è documentato per il Tab 2.

### Logica di controllo suggerita (server, non implementata)
- L'APK già legge il livello (ACTION_BATTERY_CHANGED); il server decide con **isteresi**: spegni la presa a ≥ 80%, riaccendi a ≤ 40%.
- **Fail-safe**: se il server o il Wi-Fi si fermano con la presa spenta il tablet si scarica. Mitigazioni: stato di avvio "acceso" della presa e
  un timer di riaccensione automatica sulla presa stessa (⚠️ da verificare sulla documentazione Shelly: Switch ha un'impostazione di auto-on), e la
  regola sul server "dopo N minuti senza dati dall'APK riaccendi".
- Se la presa resta spenta, il caricatore non alimenta nulla: il tablet va a batteria anche a schermo acceso (il Tab 2 consuma
  ~300–700 mA con lo schermo acceso: ~1 ora ogni 10–15% di batteria, ⚠️ stima dalle misure del progetto, −730 mA sulla USB). Un ciclo
  80→40% richiede quindi alcune ore a schermo spento e meno di un'ora a schermo acceso.
- Salvaguardia suggerita: ricaricare comunque quando la batteria < 20%, qualunque sia la modalità.

### Test consigliati prima di costruire (a costo quasi zero)
1. Con la prima presa smart: spegnere il caricatore a 80% e misurare il tempo di scarica con `adb shell dumpsys battery` (verifica isteresi e fail-safe).
2. (Solo se si vuole davvero l'hub) provare su una porta CDP/DCP vera se il Tab 2 assorbe >500 mA: un'unica misura con `dumpsys battery`
   mostra "AC powered" vs "USB powered" e la corrente reale.

## 5. Domande aperte
- Prezzo reale del LCUS-1 su marketplace UE e disponibilità in Italia (⚠️ non trovato).
- Datasheet SMB347: quali firme D+/D− accetta e a quanti mA (⚠️ non trovato). Utile solo se si sceglie un hub.
- Auto-on/timer di sicurezza del Shelly Plug S Gen3: confermare il nome esatto della configurazione nella documentazione.
- Autenticazione sul Shelly (digest) e rete: il tablet e la presa devono essere raggiungibili dal PC; scegliere IP fisso/prenotazione DHCP.

## 6. Fonti
- [Device tree Galaxy Tab 2 10.1 (SMB347, 1,8 A)](https://lkml.iu.edu/hypermail/linux/kernel/2603.0/05062.html)
- [Summit SMB346/SMB347, BC1.2 e rilevamento sorgente](https://eepower.com/new-industry-products/summit-introduces-tiny-programmable-usbac-switch-mode-battery-chargers-with-integrated-currentpath-manager/)
- [Analog Devices — USB BC1.2 e rilevatori](https://www.analog.com/en/resources/technical-articles/overview-of-usb-battery-charging-revision-12-and-the-important-role-of-charger-detectors.html)
- [Forum: Tab 2, D+/D− in corto → caricatore](https://forum.earlybird.club/goto/post?id=3975109) · [TI E2E TPS2543](https://e2e.ti.com/support/power-management-group/power-management/f/power-management-forum/180904/about-tps2543)
- [Yepkit/ykush (ykushcmd, hidapi su Windows)](https://github.com/Yepkit/ykush) · [YKUSH3 guida Windows](https://www.yepkit.com/learn/setup-guide-ykush3_windows) · [pykush](https://github.com/Yepkit/pykush) · [YKUSH3 prodotto](https://www.yepkit.com/product/300110/YKUSH3) · [multi-com.eu](https://multi-com.eu/,details,id_pr,22648,key,ykush-xs-yepkit-usb-switchable-hub.html) · [ykush3cmd (specifiche non ufficiali)](https://github.com/gerhard-schuster/ykush3cmd)
- [Acroname USBHub3+ in Python](https://acroname.com/node/849) · [Acroname scheda](https://acroname.com/comment/3)
- [Cambrionix docs](https://docs.cambrionix.com/products) · [PDSync-C4](https://cambrionix.com/products/pdsync-4)
- [Shelly Plug S Gen3 API](https://shelly-api-docs.shelly.cloud/gen2/Devices/Gen3/ShellyPlugSG3) · [Shelly IT](https://www.shelly.com/it/products/shelly-plug-s-gen3-2) · [MediaWorld](https://www.mediaworld.it/it/product/_presa-intelligente-shelly-plug-s-gen3-wifi-24-ghz-controllo-remoto-tramite-app-171450276.html)
- [Tasmota HTTP Power Off](https://community.octoprint.org/t/how-do-i-control-my-tasmota-sonoff-with-psu-control-plugin/15068)
- [Relè USB LCUS-1](https://ifuturetech.org/product/lcus-1-5v-usb-plc-serial-control-relay-module/)

Rollback: `git rm RICERCA_HUB_USB_v0.1.0_20261022-0142.md` (nessun altro file di codice toccato).
