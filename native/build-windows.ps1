param([string]$CMake = 'cmake')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $projectRoot 'build\native-source\whisper-jni'
$build = Join-Path $projectRoot 'build\native-windows'
$revision = '1b537f459d299d5c3ce4e12306aceac75009c253'
if (-not (Test-Path -LiteralPath $source)) {
    git clone --depth 1 --branch whisper_jni_1.7.1 --recurse-submodules --shallow-submodules https://github.com/GiviMAD/whisper-jni.git $source
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fetch pinned native sources' }
}
$actual = (git -C $source rev-parse HEAD).Trim()
if ($actual -ne $revision) { throw 'Unexpected native source revision' }
$jdkPath = Split-Path -Parent (Split-Path -Parent (Get-Command javac.exe).Source)
$cmakeSource = $source.Replace('\', '/')
$cmakeJdk = $jdkPath.Replace('\', '/')
& $CMake -S $PSScriptRoot -B $build -G 'Visual Studio 17 2022' -A x64 "-DWHISPER_JNI_SOURCE=$cmakeSource" "-DSAYITEM_JDK=$cmakeJdk"
if ($LASTEXITCODE -ne 0) { throw 'Native configure failed' }
& $CMake --build $build --config Release --parallel 4 --target sayitem-whisper
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
$destination = Join-Path $projectRoot 'src\main\resources\sayitem-speech\native\windows-x64'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$dll = Join-Path $destination 'sayitem-whisper.dll'
Copy-Item -LiteralPath (Join-Path $build 'output\sayitem-whisper.dll') -Destination $dll
$whisperRevision = (git -C (Join-Path $source 'src/main/native/whisper') rev-parse HEAD).Trim()
$manifest = [ordered]@{
    jniRevision = $revision
    whisperRevision = $whisperRevision
    sha256 = (Get-FileHash -LiteralPath $dll -Algorithm SHA256).Hash.ToLowerInvariant()
    cpu = 'x86_64 baseline SSE2; AVX, AVX2, FMA and F16C disabled'
    runtime = 'static MSVC runtime'
    patch = 'CPU scheduler receives per-request abort callback'
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'source.json') -Encoding utf8
Write-Output "Bundled baseline native engine: $dll"
