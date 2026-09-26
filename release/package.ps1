$ErrorActionPreference='Stop'
$dist=Join-Path $PSScriptRoot 'dist'
$jar=Join-Path $dist 'MineGen-1.0.0.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Lancez build.ps1 avant package.ps1.'}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.txt') -Destination $dist -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'VALIDATION.txt') -Destination $dist -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../CREDITS.md') -Destination $dist -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config.yml') -Destination (Join-Path $dist 'config-exemple.yml') -Force
$hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath (Join-Path $dist 'SHA256.txt') -Value ($hash+'  MineGen-1.0.0.jar') -Encoding ascii
$files=@('MineGen-1.0.0.jar','README.txt','VALIDATION.txt','CREDITS.md','config-exemple.yml','SHA256.txt') | ForEach-Object {Join-Path $dist $_}
Compress-Archive -LiteralPath $files -DestinationPath (Join-Path $dist 'MineGen-1.0.0.zip') -Force
Get-ChildItem -LiteralPath $dist | Select-Object Name,Length
