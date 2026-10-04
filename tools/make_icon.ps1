<#
  生成 PC 端图标（padlink.ico），图形与 Android 端一致：
  深底 + 蓝色胶囊 + 左侧深色摇杆点 + 右侧亮白按钮点。

  用法（项目根目录下）：
      .\tools\make_icon.ps1

  产物：src\pc\PadLink.Gui\padlink.ico（多尺寸，含 256）
#>

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outPath = Join-Path $root 'src\pc\PadLink.Gui\padlink.ico'

# 与 Android 的 ic_launcher_foreground.xml 同一套坐标（108 的 viewport）
$viewport = 108.0

function New-PadLinkBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $k = $size / $viewport

    # 圆角方形底
    $r = 24 * $k
    $d = $r * 2
    $bgPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $bgPath.AddArc(0, 0, $d, $d, 180, 90)
    $bgPath.AddArc($size - $d, 0, $d, $d, 270, 90)
    $bgPath.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $bgPath.AddArc(0, $size - $d, $d, $d, 90, 90)
    $bgPath.CloseFigure()

    $bgBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(255, 18, 21, 28))
    $g.FillPath($bgBrush, $bgPath)

    # 蓝色胶囊：x 29..79，y 43..65，圆角半径 11
    $capR = 11 * $k
    $capD = $capR * 2
    $capLeft = 29 * $k
    $capRight = 79 * $k
    $capTop = 43 * $k
    $capBottom = 65 * $k

    $capPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $capPath.AddArc($capLeft, $capTop, $capD, $capD, 90, 180)
    $capPath.AddArc($capRight - $capD, $capBottom - $capD, $capD, $capD, 270, 180)
    $capPath.CloseFigure()

    $capBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(255, 76, 141, 255))
    $g.FillPath($capBrush, $capPath)

    # 左圆（挖成底色，像摇杆凹陷）
    $darkBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(255, 18, 21, 28))
    $g.FillEllipse($darkBrush, (41 - 6) * $k, (54 - 6) * $k, 12 * $k, 12 * $k)

    # 右圆（亮白）
    $lightBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(255, 234, 242, 255))
    $g.FillEllipse($lightBrush, (67 - 4.5) * $k, (54 - 4.5) * $k, 9 * $k, 9 * $k)

    $g.Dispose()
    return $bmp
}

function Save-Ico([int[]]$sizes, [string]$path) {
    $pngs = @()
    foreach ($size in $sizes) {
        $bmp = New-PadLinkBitmap $size
        $stream = New-Object System.IO.MemoryStream
        $bmp.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngs += , $stream.ToArray()
        $stream.Dispose()
        $bmp.Dispose()
    }

    # ICO：6 字节头 + 每张 16 字节目录项 + 各张 PNG 数据（Vista+ 支持 PNG 内嵌）
    $fs = [System.IO.File]::Create($path)
    $bw = New-Object System.IO.BinaryWriter -ArgumentList $fs

    $bw.Write([UInt16]0)
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]$sizes.Count)

    $offset = 6 + 16 * $sizes.Count
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $size = $sizes[$i]
        $dim = if ($size -ge 256) { 0 } else { $size }   # 256 要写成 0
        $bw.Write([Byte]$dim)
        $bw.Write([Byte]$dim)
        $bw.Write([Byte]0)
        $bw.Write([Byte]0)
        $bw.Write([UInt16]1)
        $bw.Write([UInt16]32)
        $bw.Write([UInt32]$pngs[$i].Length)
        $bw.Write([UInt32]$offset)
        $offset += $pngs[$i].Length
    }

    foreach ($png in $pngs) { $bw.Write($png) }

    $bw.Close()
    $fs.Close()
}

Save-Ico @(16, 24, 32, 48, 64, 128, 256) $outPath

Write-Host "已生成 $outPath"
Get-Item $outPath | Select-Object Name, @{n = 'KB'; e = { [math]::Round($_.Length / 1KB, 1) } }
