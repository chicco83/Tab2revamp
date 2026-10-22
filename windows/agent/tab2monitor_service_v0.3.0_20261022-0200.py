# =============================================================================
# Tab2Monitor - supervisore per l'avvio automatico (attivita' pianificata)
# Versione: v0.3.0 - 2026-10-22 02:00 (ora locale)
# v0.3.0: (branch installer) se tra gli argomenti c'e' "--data <cartella>" il log va
#         in <cartella>\logs (pacchetto installato in Program Files: la cartella dei
#         programmi non e' scrivibile e non deve contenere dati). Il server viene
#         cercato nella cartella di questo file (pacchetto: server\, repo: windows\agent).
#         Python: quello accanto a pythonw.exe che esegue questo file (nel pacchetto e'
#         il Python "embeddable" incluso, cartella python\).
# 2026-10-22 v0.2.0 (sostituita): log sempre in windows/agent/logs.
# =============================================================================
# Lanciato da pythonw.exe (nessuna finestra) dall'attivita' pianificata. Cosa fa:
#   1) attende che la cartella del server sia disponibile (Google Drive G: viene
#      montato qualche secondo dopo l'accesso, se si usa il repository);
#   2) avvia il server tab2monitor_server_v*.py con versione piu' alta;
#   3) se il server termina lo riavvia dopo 10 s (anche dopo un aggiornamento:
#      basta chiudere il processo e riparte la versione nuova);
#   4) log ruotato oltre 1 MB.
# =============================================================================
import glob
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ARGS = sys.argv[1:]                              # passati al server (es. --data X --gpu 9070)
DATA = None
if "--data" in ARGS and ARGS.index("--data") + 1 < len(ARGS):
    DATA = ARGS[ARGS.index("--data") + 1]
LOG_DIR = os.path.join(DATA, "logs") if DATA else os.path.join(HERE, "logs")
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
    while True:
        srv = sorted(glob.glob(os.path.join(HERE, "tab2monitor_server_v*.py")))
        if not srv:                             # G: non ancora montato
            time.sleep(5)
            continue
        log("avvio " + os.path.basename(srv[-1]) + " " + " ".join(ARGS))
        with open(LOG, "a", encoding="utf-8") as out:
            p = subprocess.Popen([PY, "-u", srv[-1]] + ARGS, cwd=HERE, stdout=out,
                                 stderr=subprocess.STDOUT, stdin=subprocess.PIPE,
                                 creationflags=CREATE_NO_WINDOW)
            rc = p.wait()
        log("server terminato (rc %s), riavvio tra 10 s" % rc)
        time.sleep(10)


if __name__ == "__main__":
    main()
