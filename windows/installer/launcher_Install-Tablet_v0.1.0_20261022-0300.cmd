@echo off
REM ==========================================================================
REM Tab2Monitor - installa l'app sul tablet via USB / install the app on the tablet over USB
REM Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)   (nel pacchetto: Install-Tablet.cmd)
REM ==========================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install_tablet.ps1" %*
pause
