using System.Diagnostics;
using System.IO;

class VibePlayerLauncher
{
    static void Main()
    {
        string jar = Path.Combine(
            System.AppDomain.CurrentDomain.BaseDirectory, "VibePlayer.jar");
        if (!File.Exists(jar)) return;
        Process.Start(new ProcessStartInfo
        {
            FileName = "javaw",
            Arguments = "-jar \"" + jar + "\"",
            UseShellExecute = true
        });
    }
}
