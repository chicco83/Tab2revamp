@echo off
REM ==========================================================================
REM Tab2Monitor - avvio su Windows
REM Versione: v0.1.0 - 2026-10-21 18:45 UTC
REM 1) installa psutil se manca  2) adb reverse (se il tablet e' collegato via
REM    USB e Android >= 5.0)  3) avvia il server su porta 8090
REM ==========================================================================
cd /d "%~dp0"
python -c "import psutil" 2>nul || python -m pip install -r requirements.txt
REM adb reverse: il tablet raggiunge http://localhost:8090 attraverso il cavo USB
where adb >nul 2>nul && adb reverse tcp:8090 tcp:8090
REM sceglie lo script server con versione piu' alta presente nella cartella
for /f "delims=" %%F in ('dir /b /o:n tab2monitor_server_v*.py') do set SRV=%%F
echo Avvio %SRV%
python "%SRV%" --port 8090
