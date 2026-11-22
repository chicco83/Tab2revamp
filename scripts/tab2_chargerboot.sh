#!/system/bin/sh
# Tab2Monitor - avvio automatico dalla ricarica da spento (modalita' "charger").
# Parte solo con ro.bootmode=charger (tablet spento con il caricatore collegato).
# Se la batteria SCENDE sotto 100 in questa sessione e poi ARRIVA al 100%, avvia Android.
# Se il tablet e' stato spento di proposito gia' al 100% non lo riaccende.
# Avvio Android da LPM (provato a mano il 2026-11-22): sys.boot_from_charger_mode=1 (class_stop charger +
# late-init) e poi vold.decrypt=trigger_restart_framework (class_start main: zygote). Senza il secondo
# passo late-init monta /data ma lo zygote non parte.
# Servizi A/B con seclabel diversi: la transizione init->su e' negata da SELinux (avc denied), vince il primo che parte.
mkdir /dev/tab2lock 2>/dev/null || exit 0
log() { echo "$(date +%T) $*" >> /dev/tab2.log; }
log "start uid=$(id)"
low=0
while :; do
  read c < /sys/class/power_supply/battery/capacity
  setprop tab2.chg.level "$c"
  [ "$c" -lt 100 ] && low=1
  if [ "$low" = 1 ] && [ "$c" -ge 100 ]; then
    log "100% -> avvio Android"
    setprop tab2.chg.state boot
    setprop sys.boot_from_charger_mode 1
    sleep 3
    setprop vold.decrypt trigger_restart_framework
    sleep 90
    log "ancora qui: reboot"
    setprop tab2.chg.state reboot
    setprop sys.powerctl reboot
    exit 0
  fi
  sleep 30
done
