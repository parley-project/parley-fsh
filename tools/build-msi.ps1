# The Windows installer: dist\fsh-VERSION-windows-ARCH.msi (and its .sha256, in sha256sum's
# format), made with jpackage: fsh with a Java runtime of its own (jlink: only the modules fsh
# needs), the keyboard library, and fsh.exe, a console program, with a Start menu entry. It
# installs to C:\Program Files\fsh (the installer asks), and a newer one replaces an older.
#
# Usage: tools\build-msi.ps1 [-Version 2.0.0] [-Arch x86_64]
#   Version  the pom's, without -SNAPSHOT
#
# Needs a JDK in JAVA_HOME (17 or later for the keychain), the WiX Toolset for jpackage (3, or 4
# and later with JDK 24 and later), and windows\nativekeyboard.dll (windows\compile.cmd, in an
# "x64 Native Tools Command Prompt"; built here when it isn't there and cl is on the PATH).
# fsh.exe is jpackage's launcher: Windows has no fshexec, so it is started as windows\fsh.cmd
# starts it. The installer does not put fsh on PATH: add C:\Program Files\fsh to it. The .msi is
# not signed. macOS: tools/build-jpackage.sh; Linux: tools/build-linux-packages.sh.
#
# The plugins built in (the file browser for browse, say), on fsh's class path: the jars
# FSH_PLUGINS names (separated by ;), else those in %USERPROFILE%\.parley\fsh\plugins and
# %ProgramData%\Parley\fsh\plugins; with none, fsh is built without.
param(
	[string] $Version = '',
	[string] $Arch = 'x86_64'
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
Set-Location $project

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set: set it to a JDK' }
$jdk = $env:JAVA_HOME
foreach ($tool in 'jlink', 'jdeps', 'jpackage', 'java') {
	if (-not (Test-Path (Join-Path $jdk "bin\$tool.exe"))) { throw "no $tool in JAVA_HOME ($jdk)" }
}
$major = [int]((Select-String -Path (Join-Path $jdk 'release') -Pattern '^JAVA_VERSION="(\d+)').Matches[0].Groups[1].Value)
if (-not $Version) {
	$Version = ([xml](Get-Content pom.xml)).project.version
}
$Version = $Version -replace '-SNAPSHOT$', '' -replace '^v', ''
Write-Output "JDK $major ($jdk), windows $Arch, fsh $Version"

# fsh.jar (with the keychain: its profile is on unless -Dparley.fsh.noKeychain)
& mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
if (-not (Test-Path target\fsh.jar)) { throw 'mvn package made no target\fsh.jar' }

# the keyboard library
if (-not (Test-Path windows\nativekeyboard.dll)) {
	& cmd /c windows\compile.cmd
	if ($LASTEXITCODE -ne 0) { throw 'windows\compile.cmd failed' }
}

# The modules: what fsh.jar uses (jdeps), and what is found only at run time (as
# tools/build-jlink.sh: elliptic curves for SSH, more encodings, every locale, zip file systems,
# DNS through JNDI)
$modules = (& (Join-Path $jdk 'bin\jdeps.exe') -q --multi-release $major --ignore-missing-deps --print-module-deps target\fsh.jar).Trim()
$available = (& (Join-Path $jdk 'bin\java.exe') --list-modules) | ForEach-Object { ($_ -split '@')[0] }
foreach ($m in 'jdk.crypto.ec', 'jdk.charsets', 'jdk.localedata', 'jdk.zipfs', 'jdk.naming.dns') {
	if ($available -contains $m) { $modules += ",$m" }
}
Write-Output "modules: $modules"

$work = Join-Path $project 'target\msi'
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force (Join-Path $work 'input'), dist | Out-Null
$compress = if ($major -ge 21) { 'zip-6' } else { '2' }
$jlinkArgs = @('--add-modules', $modules, '--output', (Join-Path $work 'runtime'),
	'--strip-debug', '--no-header-files', '--no-man-pages', "--compress=$compress")
if ($major -ge 17) { $jlinkArgs += '--generate-cds-archive' }
& (Join-Path $jdk 'bin\jlink.exe') @jlinkArgs
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

Copy-Item target\fsh.jar, windows\nativekeyboard.dll (Join-Path $work 'input')
# (jpackage puts every jar in the input on the class path, plugins\ too)
$plugins = if ($env:FSH_PLUGINS) { $env:FSH_PLUGINS -split ';' | Where-Object { $_ } } else {
	@((Join-Path $env:USERPROFILE '.parley\fsh\plugins'), (Join-Path $env:ProgramData 'Parley\fsh\plugins')) |
		Where-Object { Test-Path $_ } | ForEach-Object { Get-ChildItem (Join-Path $_ '*.jar') } | ForEach-Object { $_.FullName }
}
if ($plugins) {
	New-Item -ItemType Directory -Force (Join-Path $work 'input\plugins') | Out-Null
	Copy-Item $plugins (Join-Path $work 'input\plugins')
	Write-Output "plugins built in: $($plugins -join ' ')"
} else {
	Write-Output 'no plugins built in (FSH_PLUGINS, or the plugin folders)'
}

# (windows\fsh.cmd's options: deep recursion, the serial collector with a small young generation;
# headless: fsh runs in the console)
$options = @('-Djava.library.path=$APPDIR', '-Xss16m', '-XX:+UseSerialGC', '-Xmn16m',
	'-XX:MinHeapFreeRatio=10', '-XX:MaxHeapFreeRatio=20', '-Djava.awt.headless=true')
if ($major -ge 17) { $options += '--enable-native-access=ALL-UNNAMED' }
$jpackageArgs = @('--type', 'msi', '--dest', (Join-Path $work 'out'), '--name', 'fsh',
	'--app-version', $Version, '--vendor', 'Tony Bringardner',
	'--copyright', "Copyright 2004-$((Get-Date).Year) Tony Bringardner",
	'--description', 'fsh, the FileSource Shell: a bash-like shell on any parley-files file system',
	'--input', (Join-Path $work 'input'), '--main-jar', 'fsh.jar', '--main-class', 'us.bringardner.fsh.Console',
	'--runtime-image', (Join-Path $work 'runtime'), '--license-file', 'LICENSE',
	# (Parley's tower with the >_ badge: parley-brand's parley-icon-terminal PNGs, as one .ico)
	'--icon', 'windows\fsh.ico',
	'--win-console', '--win-dir-chooser', '--win-menu',
	# (the same for every version, so a newer installer replaces an older one)
	'--win-upgrade-uuid', '54bf2305-171a-559a-8f13-c0db046304c4',
	'--temp', (Join-Path $work 'temp'))
foreach ($o in $options) { $jpackageArgs += @('--java-options', $o) }
& (Join-Path $jdk 'bin\jpackage.exe') @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }

$name = "fsh-$Version-windows-$Arch.msi"
Move-Item -Force (Get-ChildItem (Join-Path $work 'out\*.msi'))[0].FullName (Join-Path 'dist' $name)
$hash = (Get-FileHash -Algorithm SHA256 (Join-Path 'dist' $name)).Hash.ToLower()
"$hash  $name" | Set-Content -Encoding ASCII (Join-Path 'dist' "$name.sha256")
Remove-Item -Recurse -Force $work
Write-Output (Join-Path $project "dist\$name")
