@echo off
REM ==========================================================================
REM Tab2Monitor - Setup (installazione, avvio automatico, firewall, driver, tablet)
REM Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)   (nel pacchetto: Setup.cmd)
REM ==========================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup.ps1" %*
if errorlevel 1 pause
