<#
  编译 PC 端并发布，平时双击 PadLink.Gui.exe 就能用。

  用法（在仓库根目录下）：
      .\build.ps1                  # 默认发布到上一级的 app\（本机布局）
      .\build.ps1 -Output dist     # 发布到仓库内的 dist\
#>

param(
    [string]$Output = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
if ($Output) {
    $appDir = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path $repoRoot $Output }
} else {
    $appDir = Join-Path (Split-Path -Parent $repoRoot) 'app'
}
$project = Join-Path $repoRoot 'src\pc\PadLink.Gui\PadLink.Gui.csproj'

if (-not (Test-Path $project)) {
    throw "找不到项目文件：$project"
}

# 先清干净，免得上一版残留的 dll 混在里面（换手柄类型/依赖时最容易出这种鬼）。
if (Test-Path $appDir) {
    Get-ChildItem $appDir -Force | Remove-Item -Recurse -Force
} else {
    New-Item -ItemType Directory -Force -Path $appDir | Out-Null
}

Write-Host "发布到 $appDir"

dotnet publish $project -c Release -o $appDir --nologo

if ($LASTEXITCODE -ne 0) { throw "发布失败（退出码 $LASTEXITCODE）" }

# 卸载脚本也放一份进 app，用户拿到这个目录就能卸干净。
$uninstall = Join-Path $repoRoot 'tools\uninstall.bat'
if (Test-Path $uninstall) {
    Copy-Item $uninstall (Join-Path $appDir 'uninstall.bat') -Force
}

$exe = Join-Path $appDir 'PadLink.Gui.exe'
Write-Host ""
Write-Host "完成：$exe"
