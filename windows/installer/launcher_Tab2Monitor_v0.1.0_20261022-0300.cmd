@echo off
REM ==========================================================================
REM Tab2Monitor - avvio del server / start the server   (nel pacchetto: Tab2Monitor.cmd)
REM Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
REM Chiede l'amministratore (sensori CPU/scheda madre); se gia' attivo apre /config.
REM Asks for administrator (CPU/motherboard sensors); if already running opens /config.
REM ==========================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start.ps1" %*
if errorlevel 1 pause
