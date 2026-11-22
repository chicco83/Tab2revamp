import struct, gzip, hashlib, re
base=r"G:\Il mio Drive\CRISTIANO\VIBE CODING\tab2revamp"
src=base+r"\backup\pre_charger_20261122\mmcblk0p5.img"
d=open(src,'rb').read()
ks,ka,rs,ra,ss,sa,ta,ps=struct.unpack('<8I',d[8:40])
pg=lambda n:(n+ps-1)//ps*ps
ko=ps; ro=ko+pg(ks)
kernel=d[ko:ko+ks]; rd=gzip.decompress(d[ro:ro+rs])
# ---- cpio newc
ents=[]; i=0
while True:
    h=rd[i:i+110]; assert h[:6]==b'070701'
    f=[int(h[6+8*k:14+8*k],16) for k in range(13)]
    ino,mode,uid,gid,nl,mt,fs,dmaj,dmin,rmaj,rmin,ns,ck=f
    name=rd[i+110:i+110+ns-1].decode(); i=(i+110+ns+3)&~3
    data=rd[i:i+fs]; i=(i+fs+3)&~3
    if name=='TRAILER!!!': break
    ents.append(dict(f=f,name=name,data=data))
rc=[e for e in ents if e['name']=='init.espresso.rc'][0]
t=rc['data'].decode()
add='''
# Tab2Monitor: avvio automatico dalla ricarica da spento al 100% (vedi /sbin/tab2_chargerboot.sh)
service tab2chgboot /system/bin/sh -c "low=0; while :; do read c < /sys/class/power_supply/battery/capacity; setprop debug.tab2.level $$c; [ $$c -lt 100 ] && low=1; if [ $$low = 1 ] && [ $$c -ge 100 ]; then setprop debug.tab2.boot 1; sleep 25; setprop debug.tab2.fw 1; exit 0; fi; sleep 30; done"
    user root
    group root
    oneshot
    disabled
    seclabel u:r:shell:s0

# SELinux (enforcing) nega a init l'esecuzione di sh e il passaggio a su/permissive: il polling gira come
# shell e segnala con una proprieta' debug.*, l'avvio vero lo fa l'init (che puo' impostare ogni proprieta')
on property:ro.bootmode=charger
    start tab2chgboot

on property:debug.tab2.boot=1
    setprop sys.boot_from_charger_mode 1

# secondo passo dopo 25 s (late-init deve prima aver letto /system/etc/init, altrimenti installd non parte)
on property:debug.tab2.fw=1
    setprop vold.decrypt trigger_restart_framework
'''
import os
DIAG=os.environ.get('DIAG')=='1'
if DIAG:
    dp=[e for e in ents if e['name']=='default.prop'][0]
    dp['data']=dp['data'].replace(b'ro.adb.secure=1',b'ro.adb.secure=0').replace(b'ro.secure=1',b'ro.secure=0')   # SOLO DIAGNOSI
assert 'tab2chgboot' not in t
# sepolicy: dominio 'shell' (tipo valore 628) permissive -> bit 52 del nodo ebitmap che parte da 576 (permissive_map a offset 56)
import struct as _st
sp=[e for e in ents if e['name']=='sepolicy'][0]
pd=bytearray(sp['data'])
assert _st.unpack_from('<I',pd,0)[0]==0xf97cff8c and _st.unpack_from('<I',pd,16)[0]==30
assert _st.unpack_from('<III',pd,56)==(64,640,3) and _st.unpack_from('<I',pd,92)[0]==576
m=_st.unpack_from('<Q',pd,96)[0]; assert not m>>52&1
_st.pack_into('<Q',pd,96,m|(1<<52)); sp['data']=bytes(pd)
rc['data']=(t.rstrip('\n')+'\n'+add).encode()
sh=open(base+r"\scripts\tab2_chargerboot.sh",'rb').read()
ref=[e for e in ents if e['name']=='sbin/healthd'][0]['f']
# CORRETTO 2026-11-22: la versione precedente aveva 14 campi (ref[5] doppio) e rompeva il cpio -> bootloop
nf=list(ref); nf[0]=0x20000+len(ents); nf[1]=0o100750; nf[4]=1
ents.append(dict(f=nf,name='sbin/tab2_chargerboot.sh',data=sh))
# ordine: sbin/ prima dei file contenuti non e' necessario (la cartella esiste)
out=b''
def emit(name,f,data):
    global out
    f=list(f); f[6]=len(data); f[11]=len(name)+1
    assert len(f)==13
    hdr=b'070701'+b''.join(b'%08X'%v for v in f)
    out+=hdr+name.encode()+b'\0'; out+=b'\0'*((4-len(out)%4)%4); out+=data; out+=b'\0'*((4-len(out)%4)%4)
for e in ents: emit(e['name'],e['f'],e['data'])
emit('TRAILER!!!',[0]*13,b'')
new=gzip.compress(out,9,mtime=0)
nrs=len(new)
body=kernel+b'\0'*(pg(ks)-ks)+new+b'\0'*(pg(nrs)-nrs)
hdr=bytearray(d[:ps]); hdr[16:20]=struct.pack('<I',nrs)
sha=hashlib.sha1(); sha.update(kernel); sha.update(struct.pack('<I',ks)); sha.update(new); sha.update(struct.pack('<I',nrs)); sha.update(struct.pack('<I',0)); hdr[576:596]=sha.digest()
img=bytes(hdr)+body
assert len(img)<=len(d)
img+=b'\0'*(len(d)-len(img))
open(base+r"\scratchpad\boot_new.img",'wb').write(img)
print('ok ramdisk',rs,'->',nrs,'img',len(img))
