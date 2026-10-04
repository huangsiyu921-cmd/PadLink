using System.Runtime.InteropServices;

namespace PadLink.Gui;

/// <summary>
/// 让系统把标题栏画成深色（Windows 10 1809+ 支持）。
/// 不这么干的话，窗口边框那条白会和里面的深色底格格不入。
/// </summary>
internal static class DarkTitleBar
{
    private const int DwmwaUseImmersiveDarkMode = 20;

    public static void Apply(Form form)
    {
        if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 17763)) return;

        var enabled = 1;
        _ = DwmSetWindowAttribute(form.Handle, DwmwaUseImmersiveDarkMode, ref enabled, sizeof(int));
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
}
