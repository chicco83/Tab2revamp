/* smbdump_v0.1.0_20261023-0905.c — v0.1.0 — 2026-10-23 09:05
 * Compilazione (NDK 26): clang --target=armv7a-linux-androideabi21 -O2 -o smbdump smbdump_v0.1.0_20261023-0905.c
 *   (non -static: con -static Bionic rifiuta il segmento TLS). Uso sul tablet, dopo 'adb root':
 *   /data/local/tmp/smbdump 5 0x06 0x00 0x0E  (config SMB347)   /data/local/tmp/smbdump 5 0x06 0x30 0x3F (comandi/stato)
 * Solo lettura: nessuna scrittura sui registri. */
/* smbdump: lettura (sola lettura) dei registri di un chip I2C, es. SMB347 su bus 5 addr 0x06.
 * uso: smbdump <bus> <addr> <reg_da> <reg_a> */
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/i2c.h>
#include <linux/i2c-dev.h>

static int rd(int fd, int reg)
{
	union i2c_smbus_data d;
	struct i2c_smbus_ioctl_data a;
	a.read_write = I2C_SMBUS_READ;
	a.command = (unsigned char)reg;
	a.size = I2C_SMBUS_BYTE_DATA;
	a.data = &d;
	if (ioctl(fd, I2C_SMBUS, &a) < 0)
		return -1;
	return d.byte & 0xff;
}

int main(int argc, char **argv)
{
	char dev[32];
	int fd, r, from, to, addr;
	if (argc < 5) {
		fprintf(stderr, "uso: %s bus addr da a\n", argv[0]);
		return 1;
	}
	snprintf(dev, sizeof dev, "/dev/i2c-%s", argv[1]);
	addr = strtol(argv[2], 0, 0);
	from = strtol(argv[3], 0, 0);
	to = strtol(argv[4], 0, 0);
	fd = open(dev, O_RDWR);
	if (fd < 0) { perror(dev); return 1; }
	if (ioctl(fd, I2C_SLAVE_FORCE, addr) < 0) { perror("I2C_SLAVE_FORCE"); return 1; }
	for (r = from; r <= to; r++) {
		int v = rd(fd, r);
		if (v < 0) printf("%02X=ERR\n", r);
		else printf("%02X=%02X\n", r, v);
	}
	close(fd);
	return 0;
}
