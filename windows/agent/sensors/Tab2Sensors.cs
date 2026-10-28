// =============================================================================
// Tab2Sensors - lettore sensori per il server Tab2Monitor
// Versione: v0.2.0 - 2026-10-28 (ora locale)
// v0.2.0: CONTROLLO VENTOLE. Comandi su stdin, una riga ciascuno:
//           SET <SensorId del controllo> <percentuale 0-100>
//           AUTO <SensorId del controllo>      (torna alla curva del BIOS)
//           AUTOALL                            (tutte le ventole al BIOS)
//         I controlli in modo software sono nel JSON con "Mode":"Software".
//         Sicurezze: (1) watchdog: se per WATCHDOG_S secondi non arriva nessun SET
//         (server bloccato) tutte le ventole tornano al BIOS; (2) alla chiusura dello
//         stdin (server terminato) e all'uscita del processo: SetDefault + Close.
//         I comandi si eseguono nel ciclo principale (stesso thread di Update).
// v0.1.0 - 2026-10-22 00:40: solo lettura.
// Progetto: Tab2revamp
// =============================================================================
// Cosa fa: usa direttamente LibreHardwareMonitorLib.dll (nessuna finestra LHM,
// nessun web server sulla porta 8085) e ogni N ms scrive su stdout UNA riga
// JSON con lo stesso albero del data.json di LHM:
//   {"Text":..,"Value":..,"Type":..,"SensorId":..,"ImageURL":..,"Children":[..]}
// cosi' il server Python lo legge con la stessa funzione parse_lhm().
// Richiede i diritti di amministratore (driver PawnIO per CPU/scheda madre);
// senza admin legge comunque cio' che non serve il driver (es. GPU AMD via ADL).
//
// Build (nessun download: compilatore C# del .NET Framework di Windows):
//   bash windows/agent/sensors/build_tab2sensors_v0.2.0_20261028-1200.sh
// Uso: Tab2Sensors.exe [intervallo_ms]     (default 2000)
// Nota: versione solo in testa al file (come i sorgenti Java dell'APK); il nome
// dell'exe resta fisso perche' lo cerca il server.
// =============================================================================
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Threading;
using LibreHardwareMonitor.Hardware;

class Tab2Sensors
{
    static readonly CultureInfo Inv = CultureInfo.InvariantCulture;
    const int WATCHDOG_S = 20;                         // v0.2.0: senza SET per 20 s -> BIOS
    static readonly ConcurrentQueue<string> Cmds = new ConcurrentQueue<string>();
    static readonly Dictionary<string, IControl> Controls = new Dictionary<string, IControl>();
    static readonly HashSet<string> Owned = new HashSet<string>();   // controlli messi in Software da noi
    static DateTime lastSet = DateTime.UtcNow;
    static volatile bool stdinClosed;
    static Computer pc;

    static void Main(string[] args)
    {
        int ms = 2000;
        if (args.Length > 0) int.TryParse(args[0], out ms);
        pc = new Computer
        {
            IsCpuEnabled = true, IsGpuEnabled = true, IsMotherboardEnabled = true,
            IsControllerEnabled = true, IsStorageEnabled = true, IsMemoryEnabled = true,
            IsPsuEnabled = true
        };
        Console.OutputEncoding = new UTF8Encoding(false);
        pc.Open();
        Console.Out.Flush();
        // 2026-10-22 v0.1.0 (sostituito): lo stdin si leggeva solo per sapere quando uscire
        //   var watcher = new Thread(() => { try { Console.In.ReadToEnd(); } catch { } Environment.Exit(0); });
        // v0.2.0: lo stdin porta i comandi delle ventole; a EOF (server terminato) si
        // rimettono le ventole al BIOS e si esce dal ciclo principale.
        var watcher = new Thread(() =>
        {
            try { string l; while ((l = Console.In.ReadLine()) != null) Cmds.Enqueue(l.Trim()); } catch { }
            stdinClosed = true;
        });
        watcher.IsBackground = true;
        watcher.Start();
        AppDomain.CurrentDomain.ProcessExit += (o, e) => RestoreAll();
        DateTime nextOut = DateTime.UtcNow;
        while (true)
        {
            // --- comandi e sicurezze (ogni 100 ms) ---
            if (stdinClosed) { RestoreAll(); try { pc.Close(); } catch { } return; }
            string cmd;
            while (Cmds.TryDequeue(out cmd)) Exec(cmd);
            if (Owned.Count > 0 && (DateTime.UtcNow - lastSet).TotalSeconds > WATCHDOG_S)
            {
                Console.Error.WriteLine("watchdog: nessun comando da " + WATCHDOG_S + " s, ventole al BIOS");
                RestoreAll();
            }
            if (DateTime.UtcNow < nextOut) { Thread.Sleep(100); continue; }
            nextOut = DateTime.UtcNow.AddMilliseconds(ms);
            var sb = new StringBuilder(16384);
            sb.Append("{\"Text\":\"Sensor\",\"Children\":[{\"Text\":");
            Str(sb, Environment.MachineName);
            sb.Append(",\"Children\":[");
            bool first = true;
            foreach (var hw in pc.Hardware)
            {
                if (!first) sb.Append(',');
                first = false;
                Hardware(sb, hw);
            }
            sb.Append("]}]}");
            try { Console.Out.WriteLine(sb.ToString()); Console.Out.Flush(); }
            catch { RestoreAll(); return; }         // pipe chiusa: il server e' terminato
            // 2026-10-22 v0.1.0 (sostituito): Thread.Sleep(ms);
        }
    }

    // v0.2.0: esegue un comando ventole. Valori fuori dai limiti del controllo vengono
    // riportati dentro (MinSoftwareValue..MaxSoftwareValue).
    static void Exec(string line)
    {
        lock (Owned) try
        {
            var p = line.Split(' ');
            if (p[0] == "AUTOALL") { RestoreAll(); return; }
            IControl c;
            if (p.Length < 2 || !Controls.TryGetValue(p[1], out c)) return;
            if (p[0] == "SET" && p.Length >= 3)
            {
                float v;
                if (!float.TryParse(p[2], NumberStyles.Float, Inv, out v)) return;
                v = Math.Max(c.MinSoftwareValue, Math.Min(c.MaxSoftwareValue, v));
                c.SetSoftware(v);
                Owned.Add(p[1]);
                lastSet = DateTime.UtcNow;
            }
            else if (p[0] == "AUTO")
            {
                c.SetDefault();
                Owned.Remove(p[1]);
            }
        }
        catch (Exception e) { Console.Error.WriteLine("comando '" + line + "': " + e.Message); }
    }

    static void RestoreAll()
    {
        lock (Owned)
        {
            foreach (var id in new List<string>(Owned))
            {
                IControl c;
                if (Controls.TryGetValue(id, out c)) { try { c.SetDefault(); } catch { } }
            }
            Owned.Clear();
        }
    }

    static void Hardware(StringBuilder sb, IHardware hw)
    {
        hw.Update();
        sb.Append("{\"Text\":");
        Str(sb, hw.Name);
        sb.Append(",\"ImageURL\":");
        Str(sb, "images_icon/" + hw.HardwareType.ToString().ToLowerInvariant() + ".png");
        sb.Append(",\"HardwareId\":");
        Str(sb, hw.Identifier.ToString());
        sb.Append(",\"Children\":[");
        bool first = true;
        foreach (var sub in hw.SubHardware)              // es. chip SuperIO della scheda madre
        {
            if (!first) sb.Append(',');
            first = false;
            Hardware(sb, sub);
        }
        // gruppi per tipo di sensore, come nell'albero di LHM
        var groups = new Dictionary<SensorType, List<ISensor>>();
        foreach (var s in hw.Sensors)
        {
            List<ISensor> l;
            if (!groups.TryGetValue(s.SensorType, out l)) groups[s.SensorType] = l = new List<ISensor>();
            l.Add(s);
        }
        foreach (var kv in groups)
        {
            if (!first) sb.Append(',');
            first = false;
            sb.Append("{\"Text\":");
            Str(sb, kv.Key.ToString() + "s");
            sb.Append(",\"Children\":[");
            for (int i = 0; i < kv.Value.Count; i++)
            {
                var s = kv.Value[i];
                if (i > 0) sb.Append(',');
                sb.Append("{\"Text\":");
                Str(sb, s.Name);
                sb.Append(",\"Type\":");
                Str(sb, s.SensorType.ToString());
                sb.Append(",\"SensorId\":");
                Str(sb, s.Identifier.ToString());
                if (s.Control != null)                   // v0.2.0: controllo ventola
                {
                    Controls[s.Identifier.ToString()] = s.Control;
                    sb.Append(",\"Mode\":");
                    Str(sb, s.Control.ControlMode.ToString());
                }
                sb.Append(",\"Value\":");
                Str(sb, s.Value.HasValue ? s.Value.Value.ToString("0.0", Inv) + " " + Unit(s.SensorType) : "");
                sb.Append(",\"Children\":[]}");
            }
            sb.Append("]}");
        }
        sb.Append("]}");
    }

    static string Unit(SensorType t)
    {
        switch (t)
        {
            case SensorType.Temperature: return "°C";
            case SensorType.Fan: return "RPM";
            case SensorType.Load:
            case SensorType.Control:
            case SensorType.Level: return "%";
            case SensorType.Clock: return "MHz";
            case SensorType.Power: return "W";
            case SensorType.Voltage: return "V";
            default: return "";
        }
    }

    static void Str(StringBuilder sb, string s)
    {
        sb.Append('"');
        foreach (char c in s ?? "")
        {
            if (c == '"' || c == '\\') { sb.Append('\\'); sb.Append(c); }
            else if (c < 0x20) sb.AppendFormat("\\u{0:x4}", (int)c);
            else sb.Append(c);
        }
        sb.Append('"');
    }
}
