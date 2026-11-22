import struct, gzip, io, sys, os
d=open(sys.argv[1],'rb').read()
assert d[:8]==b'ANDROID!'
ks,ka,rs,ra,ss,sa,ta,ps=struct.unpack('<8I',d[8:40])
print('kernel',ks,'ramdisk',rs,'second',ss,'page',ps, 'cmdline',d[64:576].split(b'\0')[0])
pg=lambda n:(n+ps-1)//ps*ps
ko=ps; ro=ko+pg(ks)
open('scratchpad/boot/kernel','wb').write(d[ko:ko+ks])
rd=d[ro:ro+rs]; open('scratchpad/boot/ramdisk.gz','wb').write(rd)
print('ramdisk magic',rd[:4].hex(),'tail',len(d)-(ro+pg(rs)))
