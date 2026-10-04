<#
  编译 PC 端并发布到 ..\app，平时双击 app\PadLink.Gui.exe 就能用。

  用法（在 working tree 目录下）：
      .\build.ps1
#>

$ErrorActionPreference = 'Stop'

$workingTree = $PSScriptRoot
$appDir = Join-Path (Split-Path -Parent $workingTree) 'app'
$project = Join-Path $workingTree 'src\pc\PadLink.Gui\PadLink.Gui.csproj'

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

$exe = Join-Path $appDir 'PadLink.Gui.exe'
Write-Host ""
Write-Host "完成：$exe"
