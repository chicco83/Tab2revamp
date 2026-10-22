// =============================================================================
// Tab2Monitor - Discovery (scoperta del server Tab2Monitor sulla rete locale)
// Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
// =============================================================================
// Protocollo (server tab2monitor_server_v0.3.0+, classe Discovery):
//   tablet -> broadcast UDP porta 8090:  "TAB2MON?"
//   server -> risposta al mittente:      {"app":"tab2monitor","name":"PC","port":8090,"ver":"v0.3.0"}
// L'IP del server e' l'indirizzo da cui arriva la risposta. Si invia sia a
// 255.255.255.255 sia al broadcast della sottorete del Wi-Fi (alcuni router
// scartano il primo). MulticastLock: su alcuni tablet il driver Wi-Fi scarta i
// pacchetti broadcast in arrivo senza il lock (qui servono solo le risposte unicast,
// ma il lock costa nulla per 1,5 s).
// Uso: Discovery.find(context, 1500) da un thread che non sia quello dell'interfaccia.
// =============================================================================
package it.tab2revamp.monitor;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

final class Discovery {
    static final int PORT = 8090;
    static final byte[] ASK = "TAB2MON?".getBytes();

    /** Un server trovato: nome del PC, URL completo (http://ip:porta), versione. */
    static final class Server {
        final String name, url, ver;
        Server(String n, String u, String v) { name = n; url = u; ver = v; }
        public String toString() { return name + "  -  " + url + "  (" + ver + ")"; }
    }

    private Discovery() { }

    static ArrayList<Server> find(Context ctx, int timeoutMs) {
        ArrayList<Server> out = new ArrayList<Server>();
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        DatagramSocket s = null;
        try {
            if (wm != null) { lock = wm.createMulticastLock("tab2discovery"); lock.acquire(); }
            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(300);
            ArrayList<InetAddress> dst = new ArrayList<InetAddress>();
            dst.add(InetAddress.getByName("255.255.255.255"));
            if (wm != null && wm.getDhcpInfo() != null) {
                int ip = wm.getDhcpInfo().ipAddress, mask = wm.getDhcpInfo().netmask;
                if (ip != 0 && mask != 0) {
                    int b = (ip & mask) | ~mask;                  // little-endian come DhcpInfo
                    dst.add(InetAddress.getByAddress(new byte[]{(byte) b, (byte) (b >> 8),
                            (byte) (b >> 16), (byte) (b >> 24)}));
                }
            }
            long end = System.currentTimeMillis() + timeoutMs, nextSend = 0;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() >= nextSend) {          // 3 invii: UDP puo' perdere pacchetti
                    for (InetAddress a : dst) {
                        try { s.send(new DatagramPacket(ASK, ASK.length, a, PORT)); } catch (Exception ignored) { }
                    }
                    nextSend = System.currentTimeMillis() + timeoutMs / 3;
                }
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { s.receive(p); } catch (SocketTimeoutException e) { continue; }
                try {
                    JSONObject j = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8"));
                    if (!"tab2monitor".equals(j.optString("app"))) continue;
                    String url = "http://" + p.getAddress().getHostAddress() + ":" + j.optInt("port", PORT);
                    boolean dup = false;
                    for (Server x : out) if (x.url.equals(url)) dup = true;
                    if (!dup) out.add(new Server(j.optString("name", "PC"), url, j.optString("ver", "?")));
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        } finally {
            if (s != null) s.close();
            if (lock != null) try { lock.release(); } catch (Exception ignored) { }
        }
        return out;
    }
}
