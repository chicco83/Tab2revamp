// =============================================================================
// Tab2Sensors - lettore sensori per il server Tab2Monitor
// Versione: v0.1.0 - 2026-10-22 00:40 (ora locale)
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
//   bash windows/agent/sensors/build_tab2sensors_v0.1.0_20261022-0040.sh
// Uso: Tab2Sensors.exe [intervallo_ms]     (default 2000)
// Nota: versione solo in testa al file (come i sorgenti Java dell'APK); il nome
// dell'exe resta fisso perche' lo cerca il server.
// =============================================================================
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Threading;
using LibreHardwareMonitor.Hardware;

class Tab2Sensors
{
    static readonly CultureInfo Inv = CultureInfo.InvariantCulture;

    static void Main(string[] args)
    {
        int ms = 2000;
        if (args.Length > 0) int.TryParse(args[0], out ms);
        var pc = new Computer
        {
            IsCpuEnabled = true, IsGpuEnabled = true, IsMotherboardEnabled = true,
            IsControllerEnabled = true, IsStorageEnabled = true, IsMemoryEnabled = true,
            IsPsuEnabled = true
        };
        Console.OutputEncoding = new UTF8Encoding(false);
        pc.Open();
        Console.Out.Flush();
        // Esce quando il server Python chiude lo stdin (processo padre terminato)
        var watcher = new Thread(() => { try { Console.In.ReadToEnd(); } catch { } Environment.Exit(0); });
        watcher.IsBackground = true;
        watcher.Start();
        while (true)
        {
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
            catch { return; }                       // pipe chiusa: il server e' terminato
            Thread.Sleep(ms);
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
