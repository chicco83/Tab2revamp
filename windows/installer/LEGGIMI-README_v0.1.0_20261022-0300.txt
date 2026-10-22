Tab2Monitor - pacchetto portabile per Windows / portable package for Windows
=============================================================================
Un tablet Android mostra il carico del PC (CPU, RAM, GPU, temperature, ventole,
dischi, rete, processi). / An Android tablet shows the PC load (CPU, RAM, GPU,
temperatures, fans, disks, network, processes).

ITALIANO
--------
Requisiti: Windows 10/11 a 64 bit. Tablet: Android 5.0 o successivo, stessa rete
Wi-Fi del PC (oppure cavo USB con Debug USB).

1. Estrarre lo zip in una cartella (es. Documenti\Tab2Monitor).
2. Doppio clic su Tab2Monitor.cmd. Windows chiede l'amministratore: serve per le
   temperature di CPU e scheda madre e per le ventole (senza, si vedono comunque GPU,
   carico, RAM, dischi e rete). Si apre la pagina http://localhost:8090/config per
   dare un nome alle ventole. Chiudere la finestra nera = fermare il server.
3. Tablet: dal browser del tablet aprire l'indirizzo "APK" mostrato nella finestra
   (http://<IP-del-PC>:8090/apk), installare il file (consentire "origini sconosciute").
   Oppure con il cavo: Install-Tablet.cmd.
4. Aprire Tab2Monitor sul tablet: trova il PC da solo sulla rete. Se non lo trova:
   tenere premuto lo schermo > Impostazioni > "Cerca PC" o scrivere l'indirizzo.
5. (Facoltativo) Setup.cmd: installazione in Programmi con avvio automatico
   (all'accesso, all'avvio del PC o senza amministratore: a scelta), regola firewall,
   driver PawnIO per i sensori, disinstallazione.

Se il tablet non vede il PC: la rete di Windows deve essere "Privata" e serve la
regola firewall (Setup.cmd, voce 3). Il server risponde solo alla rete locale.
Il server non ha password: usarlo solo su reti di cui ci si fida.

ENGLISH
-------
Requirements: 64-bit Windows 10/11. Tablet: Android 5.0 or later, same Wi-Fi
network as the PC (or USB cable with USB debugging).

1. Extract the zip to a folder (e.g. Documents\Tab2Monitor).
2. Double-click Tab2Monitor.cmd. Windows asks for administrator rights: they are
   needed for CPU/motherboard temperatures and fans (without them you still get GPU,
   load, RAM, disks and network). The page http://localhost:8090/config opens to name
   the fans. Closing the black window stops the server.
3. Tablet: in the tablet browser open the "APK" address shown in the window
   (http://<PC-IP>:8090/apk) and install the file (allow "unknown sources").
   Or with a cable: Install-Tablet.cmd.
4. Open Tab2Monitor on the tablet: it finds the PC on the network by itself. If not:
   long-press the screen > Settings > "Find PCs" or type the address.
5. (Optional) Setup.cmd: install into Program Files with autostart (at logon, at PC
   startup or without administrator: your choice), firewall rule, PawnIO sensor
   driver, uninstall.

If the tablet does not see the PC: the Windows network must be "Private" and the
firewall rule is needed (Setup.cmd, item 3). The server answers the local network only.
The server has no password: use it only on networks you trust.

Componenti di terze parti / third-party components: vedi / see THIRD-PARTY.txt
