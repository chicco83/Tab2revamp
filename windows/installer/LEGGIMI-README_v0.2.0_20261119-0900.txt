Tab2Monitor - pacchetto portabile per Windows / portable package for Windows
=============================================================================
Un tablet Android mostra il carico del PC (CPU, RAM, GPU, temperature, ventole,
dischi, rete, processi). / An Android tablet shows the PC load (CPU, RAM, GPU,
temperatures, fans, disks, network, processes).

ITALIANO
--------
Requisiti: Windows 10/11 a 64 bit. Tablet: Android 7.1 o successivo, stessa rete
Wi-Fi del PC (oppure cavo USB con Debug USB). Pacchetto v0.2.0 (server v0.4.4, app v0.12.0).

1. Estrarre lo zip in una cartella (es. Documenti\Tab2Monitor).
2. Doppio clic su Tab2Monitor.cmd. Windows chiede l'amministratore: serve per le
   temperature di CPU e scheda madre e per le ventole (senza, si vedono comunque GPU,
   carico, RAM, dischi e rete). Si apre la pagina http://localhost:8090/config per
   dare un nome alle ventole. Chiudere la finestra nera = fermare il server.
3. Tablet: dal browser del tablet aprire l'indirizzo "APK" mostrato nella finestra
   (http://<IP-del-PC>:8090/apk), installare il file (consentire "origini sconosciute").
   Oppure con il cavo: Install-Tablet.cmd.
4. Aprire Tab2Monitor sul tablet: trova il PC da solo sulla rete. Se non lo trova:
   Impostazioni dell'app > "Cerca il PC in rete" o scrivere l'indirizzo mostrato
   nella finestra del server (es. http://192.168.1.20:8090).
5. (Facoltativo) Setup.cmd: installazione in Programmi con avvio automatico
   (all'accesso, all'avvio del PC o senza amministratore: a scelta), regola firewall,
   driver PawnIO per i sensori, disinstallazione.

Tre modi di collegare il tablet:
- USB: cavo + Debug USB; Install-Tablet.cmd imposta "adb reverse" e il tablet legge
  http://127.0.0.1:8090 (comodo per un tablet fisso, serve il cavo).
- Wi-Fi con ricerca automatica (consigliato): stessa rete, l'app trova il PC da sola.
- Wi-Fi con indirizzo a mano: quando la ricerca non funziona (vedi sotto).

Se il tablet non vede il PC: la rete di Windows deve essere "Privata" e serve la
regola firewall (Setup.cmd, voce 3: TCP 8090 e UDP 8091). Alcuni router (rete ospiti,
"isolamento client") bloccano la ricerca: scrivere l'indirizzo a mano.
Il server risponde solo alla rete locale.
Il server non ha password: usarlo solo su reti di cui ci si fida.

ENGLISH
-------
Requirements: 64-bit Windows 10/11. Tablet: Android 7.1 or later, same Wi-Fi
network as the PC (or USB cable with USB debugging). Package v0.2.0 (server v0.4.4, app v0.12.0).
The tablet app is in Italian only for now.

1. Extract the zip to a folder (e.g. Documents\Tab2Monitor).
2. Double-click Tab2Monitor.cmd. Windows asks for administrator rights: they are
   needed for CPU/motherboard temperatures and fans (without them you still get GPU,
   load, RAM, disks and network). The page http://localhost:8090/config opens to name
   the fans. Closing the black window stops the server.
3. Tablet: in the tablet browser open the "APK" address shown in the window
   (http://<PC-IP>:8090/apk) and install the file (allow "unknown sources").
   Or with a cable: Install-Tablet.cmd.
4. Open Tab2Monitor on the tablet: it finds the PC on the network by itself. If not:
   app Settings > "Cerca il PC in rete" (find the PC) or type the address shown
   in the server window (e.g. http://192.168.1.20:8090).
5. (Optional) Setup.cmd: install into Program Files with autostart (at logon, at PC
   startup or without administrator: your choice), firewall rule, PawnIO sensor
   driver, uninstall.

Three ways to connect the tablet:
- USB: cable + USB debugging; Install-Tablet.cmd sets up "adb reverse" and the tablet
  reads http://127.0.0.1:8090 (handy for a fixed tablet, needs the cable).
- Wi-Fi with automatic search (recommended): same network, the app finds the PC.
- Wi-Fi with a typed address: when the search does not work (see below).

If the tablet does not see the PC: the Windows network must be "Private" and the
firewall rule is needed (Setup.cmd, item 3: TCP 8090 and UDP 8091). Some routers (guest
network, "client isolation") block the search: type the address by hand.
The server answers the local network only.
The server has no password: use it only on networks you trust.

Componenti di terze parti / third-party components: vedi / see THIRD-PARTY.txt
