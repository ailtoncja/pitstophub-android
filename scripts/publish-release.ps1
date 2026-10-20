<#
.SYNOPSIS
  Prepara os anexos do GitHub Release para o atualizador do app (nao compila nem publica nada).

.DESCRIPTION
  Le versionCode/versionName do proprio APK (aapt2 do Android SDK), confere a assinatura (apksigner), copia o APK
  como pitstophub-<versionName>.apk, calcula o SHA-256 e grava versao.json e notas.md na pasta de saida.
  No fim mostra o comando `gh release create` para publicar os dois anexos.

.EXAMPLE
  .\scripts\publish-release.ps1 -Apk app\build\outputs\apk\release\app-release.apk -Notes "Atualizacao automatica dentro do app."
#>
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Notes = "",
    [string]$NotesFile,
    [string]$OutDir
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$package = "com.pitstophub.app"
$repo = "https://github.com/ailtoncja/pitstophub-android"
if (-not $OutDir) { $OutDir = Join-Path $repoRoot "app\build\release-assets" }

$apkPath = (Resolve-Path $Apk).Path
if ($NotesFile) { $Notes = [IO.File]::ReadAllText((Resolve-Path $NotesFile).Path) }
$Notes = $Notes.Trim()
if ($Notes.Length -gt 1000) { throw "Novidades com mais de 1000 caracteres (o app corta o resto)." }

$size = (Get-Item $apkPath).Length
if ($size -gt 60MB) { throw "APK com $size bytes: o app recusa mais de 60 MB." }

# Ferramentas do SDK (build-tools mais novo)
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "build-tools do Android SDK nao encontrado em $sdk (defina ANDROID_HOME)." }
$aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

# Roda uma ferramenta do SDK sem que o aviso no stderr (Java) vire erro no PowerShell 5.
function Invoke-Tool([string]$exe, [string[]]$arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { return @(& $exe @arguments 2>&1 | ForEach-Object { "$_" }) } finally { $ErrorActionPreference = $previous }
}

# Versao lida do APK, nao do build.gradle.kts: o versao.json tem de bater com o arquivo publicado.
$badging = Invoke-Tool $aapt2 @("dump", "badging", $apkPath) | Where-Object { $_ -like "package:*" } | Select-Object -First 1
if ($badging -notmatch "name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") { throw "Nao consegui ler a versao do APK: $badging" }
$apkPackage = $Matches[1]
$versionCode = [long]$Matches[2]
$versionName = $Matches[3]
if ($apkPackage -ne $package) { throw "APK de outro app: $apkPackage" }
if ($versionName -notmatch '^[0-9A-Za-z._+-]{1,32}$') { throw "versionName invalido para o atualizador: $versionName" }

# Assinatura: o Android so instala por cima se for a mesma chave de sempre.
$certs = Invoke-Tool $apksigner @("verify", "--print-certs", $apkPath)
if ($LASTEXITCODE -ne 0) { throw "APK sem assinatura valida (faltou o keystore.properties?):`n$($certs -join "`n")" }
$certSha = ($certs | Select-String "certificate SHA-256 digest: (\w+)" | Select-Object -First 1).Matches.Groups[1].Value

New-Item -ItemType Directory -Force $OutDir | Out-Null
$assetName = "pitstophub-$versionName.apk"
$assetPath = Join-Path $OutDir $assetName
Copy-Item $apkPath $assetPath -Force
$sha256 = (Get-FileHash $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()

$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    apk         = "$repo/releases/download/v$versionName/$assetName"
    sha256      = $sha256
    novidades   = $Notes
}
$utf8 = New-Object System.Text.UTF8Encoding $false # sem BOM
$jsonPath = Join-Path $OutDir "versao.json"
[IO.File]::WriteAllText($jsonPath, ($manifest | ConvertTo-Json), $utf8)

$notesPath = Join-Path $OutDir "notas.md"
$notesText = @"
$Notes

- SHA-256 do APK: ``$sha256``
- SHA-256 do certificado de assinatura: ``$certSha``
"@
[IO.File]::WriteAllText($notesPath, $notesText.Trim() + "`n", $utf8)

Write-Host "versionCode $versionCode / versionName $versionName"
Write-Host "APK:         $assetPath"
Write-Host "SHA-256:     $sha256"
Write-Host "Certificado: $certSha"
Write-Host "versao.json: $jsonPath"
Write-Host ""
Write-Host "Publicar (o Release tem de ficar como 'Latest', sem draft/pre-release):"
Write-Host "  gh release create v$versionName `"$assetPath`" `"$jsonPath`" --title `"PitStopHub $versionName`" --notes-file `"$notesPath`""
