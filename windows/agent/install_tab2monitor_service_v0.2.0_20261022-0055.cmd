@echo off
REM ==========================================================================
REM Tab2Monitor - installa l'avvio automatico (doppio clic)
REM Versione: v0.2.0 - 2026-10-22 00:55 (ora locale)
REM Chiede l'elevazione (UAC) e lancia lo .ps1 omonimo. Per togliere l'avvio
REM automatico: install_tab2monitor_service_v0.2.0_20261022-0055.cmd -Remove
REM ==========================================================================
cd /d "%~dp0"
powershell -NoProfile -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -NoExit -File \"%~dp0install_tab2monitor_service_v0.2.0_20261022-0055.ps1\" %*'"
