# Package the native build (target\native: windows\build-native.cmd) as the download for Windows:
# dist\fsh-VERSION-windows-ARCH.zip and its .sha256 (in sha256sum's format).
#
# Usage: tools\package-native.ps1 -Arch x86_64 -Version v1.0.0
#
# One folder with fsh.exe and nativekeyboard.dll together, so it runs from wherever it is
# unpacked. (macOS and Linux are packaged by tools/package-native.sh.)
param(
	[Parameter(Mandatory = $true)] [string] $Arch,
	[Parameter(Mandatory = $true)] [string] $Version
)
$ErrorActionPreference = 'Stop'
$Version = $Version -replace '^v', ''
$project = Split-Path -Parent $PSScriptRoot
$native = Join-Path $project 'target\native'
foreach ($f in 'fsh.exe', 'nativekeyboard.dll') {
	if (-not (Test-Path (Join-Path $native $f))) {
		throw "$native\$f is not there: run windows\build-native.cmd first"
	}
}
$name = "fsh-$Version-windows-$Arch"
$dist = Join-Path $project 'dist'
$stage = Join-Path $dist $name
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item (Join-Path $native 'fsh.exe') $stage
Copy-Item (Join-Path $native 'nativekeyboard.dll') $stage
Copy-Item (Join-Path $project 'LICENSE') $stage
@"
fsh $Version for Windows ($Arch)

Run fsh.exe from a console (Windows Terminal, or cmd). Keep fsh.exe and
nativekeyboard.dll together. Put this folder on PATH to run it from anywhere.

Source and documentation: https://github.com/tony-bringardner/fsh
"@ | Set-Content -Encoding ASCII (Join-Path $stage 'README.txt')

$zip = Join-Path $dist "$name.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $stage -DestinationPath $zip
Remove-Item -Recurse -Force $stage
$hash = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
"$hash  $name.zip" | Set-Content -Encoding ASCII "$zip.sha256"
Write-Output $zip
