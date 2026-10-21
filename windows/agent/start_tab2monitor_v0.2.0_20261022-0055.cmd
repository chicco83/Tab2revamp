@echo off
REM ==========================================================================
REM Tab2Monitor - avvio manuale su Windows (finestra visibile)
REM Versione: v0.2.0 - 2026-10-22 00:55 (ora locale)
REM 1) installa psutil se manca  2) avvia il server su porta 8090.
REM adb reverse non serve piu' qui: lo rifa' il server ogni 5 s (v0.2.0).
REM Per i sensori di CPU e scheda madre: tasto destro -> Esegui come amministratore.
REM 2026-10-21 v0.1.0 (sostituito): "where adb && adb reverse tcp:8090 tcp:8090"
REM   una sola volta all'avvio (e adb non era nel PATH, quindi non partiva).
REM ==========================================================================
cd /d "%~dp0"
python -c "import psutil" 2>nul || python -m pip install -r requirements.txt
REM sceglie lo script server con versione piu' alta presente nella cartella
for /f "delims=" %%F in ('dir /b /o:n tab2monitor_server_v*.py') do set SRV=%%F
echo Avvio %SRV%
python "%SRV%" --port 8090 %*
