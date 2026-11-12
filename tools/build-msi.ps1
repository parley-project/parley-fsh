# The Windows installer: dist\fsh-VERSION-windows-ARCH.msi (and its .sha256, in sha256sum's
# format), made with jpackage: fsh with a Java runtime of its own (jlink: only the modules fsh
# needs), the keyboard library, and fsh.exe, a console program. It installs to
# C:\Program Files\fsh (the installer asks), and a newer one replaces an older.
#
# fsh is a command, not a desktop application: the installer makes no Start menu entry and no
# desktop shortcut. It adds the folder it installs to to the system's PATH (taken out again when
# fsh is removed), so fsh is there in a new terminal.
#
# Usage: tools\build-msi.ps1 [-Version 2.0.0] [-Arch x86_64] [-DbRepo ..\fshdb]
#   Version  the pom's, without -SNAPSHOT
#   DbRepo   a checkout of fshdb, the console debugger: fshdb.exe, a command beside fsh.exe on
#            the same Java runtime. Its jars (not the JavaFX ones, nor those fsh.jar has inside)
#            go in apps, on the class path.
#
# Needs a JDK in JAVA_HOME (17 or later for the keychain), the WiX Toolset for jpackage (3, or 4
# and later with JDK 24 and later), and windows\nativekeyboard.dll (windows\compile.cmd, in an
# "x64 Native Tools Command Prompt"; built here when it isn't there and cl is on the PATH).
# fsh.exe is jpackage's launcher: Windows has no fshexec, so it is started as windows\fsh.cmd
# starts it. The .msi is not signed. macOS: tools/build-macos-pkg.sh; Linux:
# tools/build-linux-packages.sh.
#
# PATH: jpackage has no option for it, so its WiX source for the installer (main.wxs, read from
# the JDK) is given back to it (--resource-dir) with one more component, an Environment entry.
# If a JDK's main.wxs is not as expected, the build stops and says so.
#
# The file browser (browse, view, edit, search) is in fsh.jar itself. The plugins built in, on
# fsh's class path: the jars FSH_PLUGINS names (separated by ;), else the Lanterna browser plugin
# from the Maven repository (fsh's version: mvn install in parley-files-viewer-tui), else those in
# %USERPROFILE%\.parley\fsh\plugins and %ProgramData%\Parley\fsh\plugins; with none, fsh is
# built without.
#
# The window programs, fsh-ide and parley-files-viewer, are not in this installer: each has one
# of its own, with its own Java runtime (parley-build's packaging).
param(
	[string] $Version = '',
	[string] $Arch = 'x86_64',
	[string] $DbRepo = ''
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
# (the pom's version: the plugins' too, as Parley's projects share one)
$fshVersion = ([xml](Get-Content pom.xml)).project.version
if (-not $Version) {
	$Version = $fshVersion
}
$Version = $Version -replace '-SNAPSHOT$', '' -replace '^v', ''
Write-Output "JDK $major ($jdk), windows $Arch, fsh $Version"

# fsh.jar (with the keychain: its profile is on unless -Dparley.fsh.noKeychain)
& mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
if (-not (Test-Path target\fsh.jar)) { throw 'mvn package made no target\fsh.jar' }

# fshdb's jars: its own, and its dependencies that fsh.jar doesn't have
$fshCp = @()
$apps = @()
foreach ($def in @(
		@{ Name = 'fshdb'; Repo = $DbRepo; Pattern = 'fshdb-*.jar'; Class = 'us.bringardner.fshdb.Fshdb';
			Description = 'fshdb, a debugger for shell scripts that runs in a terminal' })) {
	if (-not $def.Repo) { continue }
	if (-not $fshCp) {
		# (the jars fsh.jar has inside: fsh's own class path)
		& mvn -q dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target\runtime-classpath.txt'
		if ($LASTEXITCODE -ne 0) { throw "fsh's class path: mvn dependency:build-classpath failed" }
		$fshCp = (Get-Content (Join-Path $project 'target\runtime-classpath.txt') -Raw) -split '[;:\r\n]' |
			Where-Object { $_ } | ForEach-Object { Split-Path -Leaf $_ }
	}
	$repo = (Resolve-Path (Join-Path $def.Repo 'pom.xml')).Path | Split-Path -Parent
	Push-Location $repo
	& mvn -q package -DskipTests dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target\app-classpath.txt'
	if ($LASTEXITCODE -ne 0) { Pop-Location; throw "building $($def.Name) failed" }
	$cp = (Get-Content 'target\app-classpath.txt' -Raw) -split ';' | Where-Object { $_ }
	# (the jar this build made: its version from Maven's record of it, as target can hold older
	# ones, fsh-ide-1.0.0.jar beside fsh-ide-2.0.0-SNAPSHOT.jar; else the newest)
	$main = $null
	$record = 'target\maven-archiver\pom.properties'
	if (Test-Path $record) {
		$built = Join-Path (Get-Location) "target\$($def.Name)-$((Get-Content $record -Raw | ConvertFrom-StringData).version).jar"
		if (Test-Path $built) { $main = $built }
	}
	if (-not $main) {
		$main = (Get-ChildItem 'target' -Filter $def.Pattern | Where-Object { $_.Name -notmatch '-(sources|javadoc|tests|all)\.jar$' } |
			Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
	}
	Pop-Location
	if (-not $main) { throw "no $($def.Name) jar built in $repo" }
	# (not fsh itself, JavaFX and what only it needs: fshdb is a console program)
	$skip = '^(fsh-\d.*|javafx-.*|richtextfx-.*|flowless-.*|reactfx-.*|undofx-.*|wellbehavedfx-.*|bringardner-fx-widgets-.*|parley-files-fx-.*)\.jar$'
	$jars = @($main) + @($cp | Where-Object { $leaf = Split-Path -Leaf $_; ($fshCp -notcontains $leaf) -and ($leaf -notmatch $skip) })
	$apps += @{ Def = $def; Main = $main; Jars = $jars }
	Write-Output "$($def.Name): $main, and $($jars.Count - 1) more jars"
}
$appJars = @($apps | ForEach-Object { $_.Jars } | Sort-Object -Property { Split-Path -Leaf $_ } -Unique)

# the keyboard library
if (-not (Test-Path windows\nativekeyboard.dll)) {
	& cmd /c windows\compile.cmd
	if ($LASTEXITCODE -ne 0) { throw 'windows\compile.cmd failed' }
}

# The modules: what fsh.jar uses (jdeps), and what is found only at run time (as
# tools/build-jlink.sh: elliptic curves for SSH, more encodings, every locale, zip file systems,
# DNS through JNDI)
$jdepsInputs = @('target\fsh.jar')
if ($apps) { $jdepsInputs += $appJars }
$modules = (& (Join-Path $jdk 'bin\jdeps.exe') -q --multi-release $major --ignore-missing-deps --print-module-deps $jdepsInputs).Trim()
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
# (FSH_PLUGINS, else the Lanterna browser from the Maven repository: fsh's version of
# parley-files-viewer-tui-lanterna, mvn install there first; else the plugin folders' jars.
# Fetched, not dependencies: they depend on fsh)
$plugins = @()
if ($env:FSH_PLUGINS) {
	$plugins = @($env:FSH_PLUGINS -split ';' | Where-Object { $_ })
} else {
	if (Test-Path target\plugins) { Remove-Item -Recurse -Force target\plugins }
	& mvn -q dependency:copy -Dmdep.useBaseVersion=true -DoutputDirectory=target\plugins "-Dartifact=us.bringardner.parley:parley-files-viewer-tui-lanterna:${fshVersion}:jar:plugin" *> $null
	if ($LASTEXITCODE -eq 0) {
		$plugins = @(Get-ChildItem target\plugins\*.jar | ForEach-Object { $_.FullName })
	} else {
		Write-Output "the Lanterna browser $fshVersion is not in the Maven repository (mvn install in parley-files-viewer-tui): the plugin folders' jars instead"
		$plugins = @(@((Join-Path $env:USERPROFILE '.parley\fsh\plugins'), (Join-Path $env:ProgramData 'Parley\fsh\plugins')) |
			Where-Object { Test-Path $_ } | ForEach-Object { Get-ChildItem (Join-Path $_ '*.jar') } | ForEach-Object { $_.FullName })
	}
}
if ($plugins) {
	New-Item -ItemType Directory -Force (Join-Path $work 'input\plugins') | Out-Null
	Copy-Item $plugins (Join-Path $work 'input\plugins')
	Write-Output "plugins built in: $($plugins -join ' ')"
} else {
	Write-Output 'no plugins built in (FSH_PLUGINS, the Maven repository, or the plugin folders)'
}

# (windows\fsh.cmd's options: deep recursion, the serial collector with a small young generation;
# headless: fsh runs in the console)
$options = @('-Djava.library.path=$APPDIR', '-Xss16m', '-XX:+UseSerialGC', '-Xmn16m',
	'-XX:MinHeapFreeRatio=10', '-XX:MaxHeapFreeRatio=20', '-Djava.awt.headless=true')
if ($major -ge 17) { $options += '--enable-native-access=ALL-UNNAMED' }
$extraLauncher = @()
if ($apps) {
	$appDir = Join-Path $work 'input\apps'
	New-Item -ItemType Directory -Force $appDir | Out-Null
	Copy-Item $appJars $appDir
	foreach ($app in $apps) {
		$def = $app.Def
		# (a console program, as fsh.exe: the same options, no Start menu entry or shortcut.
		# jpackage puts every jar of the input on every launcher's class path)
		$props = Join-Path $work "$($def.Name).properties"
		@("main-jar=apps/$(Split-Path -Leaf $app.Main)", "main-class=$($def.Class)", 'win-console=true',
			'win-menu=false', 'win-shortcut=false', "description=$($def.Description)",
			"icon=$((Join-Path $project 'windows\fsh.ico').Replace('\', '/'))",
			"java-options=$($options -join ' ')") | Set-Content -Encoding ASCII $props
		$extraLauncher += @('--add-launcher', "$($def.Name)=$props")
	}
}

# PATH: jpackage's own WiX source (main.wxs, a resource of the JDK's jdk.jpackage module), with a
# component that adds the installation folder to the system's PATH, in the feature that is
# installed; jpackage takes it from --resource-dir in place of its own.
$resources = Join-Path $work 'resources'
New-Item -ItemType Directory -Force $resources | Out-Null
$extract = Join-Path $work 'ExtractWxs.java'
@'
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ExtractWxs {
	public static void main(String[] args) throws Exception {
		Path wxs = FileSystems.getFileSystem(URI.create("jrt:/"))
				.getPath("/modules/jdk.jpackage/jdk/jpackage/internal/resources/main.wxs");
		Files.copy(wxs, Path.of(args[0]), StandardCopyOption.REPLACE_EXISTING);
	}
}
'@ | Set-Content -Encoding ASCII $extract
$mainWxs = Join-Path $resources 'main.wxs'
& (Join-Path $jdk 'bin\java.exe') $extract $mainWxs
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $mainWxs)) { throw "PATH: could not read jpackage's main.wxs from the JDK ($jdk)" }
$wxs = Get-Content $mainWxs -Raw
$feature = [regex] '<Feature\s+Id="DefaultFeature"[^>]*>'
$end = [regex] '</(Product|Package)>\s*</Wix>'
if (-not $feature.IsMatch($wxs) -or -not $end.IsMatch($wxs) -or $wxs -notmatch 'INSTALLDIR') {
	throw "PATH: the JDK's main.wxs (JDK $major) is not as expected: no DefaultFeature, INSTALLDIR or closing Product/Package (see tools\build-msi.ps1)"
}
# (the component's Guid is the same for every version, as the upgrade code below)
$pathComponent = @'
    <DirectoryRef Id="INSTALLDIR">
      <Component Id="FshPath" Guid="8d5f6a0e-2c47-5b1d-9e63-4a7b1c2d3e05" KeyPath="yes">
        <Environment Id="FshPathEntry" Name="PATH" Value="[INSTALLDIR]" Action="set" Part="last" Permanent="no" System="yes"/>
      </Component>
    </DirectoryRef>

'@
$wxs = $feature.Replace($wxs, { param($m) $m.Value + "`n      <ComponentRef Id=`"FshPath`"/>" }, 1)
$wxs = $end.Replace($wxs, { param($m) $pathComponent + '  ' + $m.Value }, 1)
[System.IO.File]::WriteAllText($mainWxs, $wxs)

$jpackageArgs = @('--type', 'msi', '--dest', (Join-Path $work 'out'), '--name', 'fsh',
	'--app-version', $Version, '--vendor', 'Tony Bringardner',
	'--copyright', "Copyright 2004-$((Get-Date).Year) Tony Bringardner",
	'--description', 'fsh, the FileSource Shell: a bash-like shell on any parley-files file system',
	'--input', (Join-Path $work 'input'), '--main-jar', 'fsh.jar', '--main-class', 'us.bringardner.fsh.Console',
	'--runtime-image', (Join-Path $work 'runtime'), '--license-file', 'LICENSE',
	# (Parley's tower with the >_ badge: parley-brand's parley-icon-terminal PNGs, as one .ico)
	'--icon', 'windows\fsh.ico',
	# (a command: no Start menu entry, no shortcut; PATH from main.wxs in --resource-dir)
	'--win-console', '--win-dir-chooser', '--resource-dir', $resources,
	# (the same for every version, so a newer installer replaces an older one)
	'--win-upgrade-uuid', '54bf2305-171a-559a-8f13-c0db046304c4',
	'--temp', (Join-Path $work 'temp'))
foreach ($o in $options) { $jpackageArgs += @('--java-options', $o) }
$jpackageArgs += $extraLauncher
& (Join-Path $jdk 'bin\jpackage.exe') @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }

$name = "fsh-$Version-windows-$Arch.msi"
Move-Item -Force (Get-ChildItem (Join-Path $work 'out\*.msi'))[0].FullName (Join-Path 'dist' $name)
$hash = (Get-FileHash -Algorithm SHA256 (Join-Path 'dist' $name)).Hash.ToLower()
"$hash  $name" | Set-Content -Encoding ASCII (Join-Path 'dist' "$name.sha256")
Remove-Item -Recurse -Force $work
Write-Output (Join-Path $project "dist\$name")
