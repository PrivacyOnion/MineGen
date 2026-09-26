$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jdk='C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot/bin'
$output=Join-Path $PSScriptRoot 'classes'
$dist=Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force $output,$dist | Out-Null
$engine=Join-Path $root 'Server/plugins/MineGen.jar'
$expected='CE4BA6536B50C2749F3D82F804AC53AEBAC5754F95CF6B3DDDF1FAE554DCE5B4'
if((Get-FileHash $engine).Hash -ne $expected){throw 'Le moteur ne correspond pas a la version validee.'}
$cp=$engine+';'+(Join-Path $root 'EterniumSMP/lib/*')
& "$jdk/javac.exe" -encoding UTF-8 --release 21 -cp $cp -d $output (Join-Path $PSScriptRoot 'src/mnw/plugin/MineGenPlugin.java')
if($LASTEXITCODE -ne 0){throw 'Compilation impossible'}
$jar=Join-Path $dist 'MineGen-1.0.0.jar'
Copy-Item -LiteralPath $engine -Destination $jar -Force
& "$jdk/jar.exe" --update --file $jar -C $output mnw/plugin/MineGenPlugin.class -C $PSScriptRoot plugin.yml -C $PSScriptRoot config.yml
if($LASTEXITCODE -ne 0){throw 'Assemblage impossible'}
Get-FileHash $jar
