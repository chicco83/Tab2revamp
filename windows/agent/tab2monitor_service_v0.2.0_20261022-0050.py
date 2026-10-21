# =============================================================================
# Tab2Monitor - supervisore per l'avvio automatico (attivita' pianificata)
# Versione: v0.2.0 - 2026-10-22 00:50 (ora locale)
# =============================================================================
# Lanciato da pythonw.exe (nessuna finestra) dall'attivita' "Tab2Monitor" creata
# da install_tab2monitor_service_*.cmd. Cosa fa:
#   1) attende che la cartella del progetto sia disponibile (Google Drive G: viene
#      montato qualche secondo dopo l'accesso);
#   2) avvia il server tab2monitor_server_v*.py con versione piu' alta;
#   3) se il server termina lo riavvia dopo 10 s (anche dopo un aggiornamento:
#      basta chiudere il processo e riparte la versione nuova);
#   4) log in windows/agent/logs/tab2monitor_service.log (ignorato da git),
#      ruotato oltre 1 MB.
# =============================================================================
import glob
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
LOG_DIR = os.path.join(HERE, "logs")
LOG = os.path.join(LOG_DIR, "tab2monitor_service.log")
PY = os.path.join(os.path.dirname(sys.executable), "python.exe")   # figlio con console nascosta
CREATE_NO_WINDOW = 0x08000000


def log(msg):
    os.makedirs(LOG_DIR, exist_ok=True)
    if os.path.isfile(LOG) and os.path.getsize(LOG) > 1048576:
        os.replace(LOG, LOG + ".1")
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(time.strftime("%Y-%m-%d %H:%M:%S ") + msg + "\n")


def main():
    args = sys.argv[1:]                         # passati al server (es. --gpu 9070)
    while True:
        srv = sorted(glob.glob(os.path.join(HERE, "tab2monitor_server_v*.py")))
        if not srv:                             # G: non ancora montato
            time.sleep(5)
            continue
        log("avvio " + os.path.basename(srv[-1]) + " " + " ".join(args))
        with open(LOG, "a", encoding="utf-8") as out:
            p = subprocess.Popen([PY, "-u", srv[-1]] + args, cwd=HERE, stdout=out,
                                 stderr=subprocess.STDOUT, stdin=subprocess.PIPE,
                                 creationflags=CREATE_NO_WINDOW)
            rc = p.wait()
        log("server terminato (rc %s), riavvio tra 10 s" % rc)
        time.sleep(10)


if __name__ == "__main__":
    main()
