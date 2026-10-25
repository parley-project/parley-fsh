# The Windows installer with the file browser plugin (browse, view, edit, search) built in:
# builds the jars of parley-files-viewer-tui, names them in FSH_PLUGINS, then runs build-msi.ps1.
#
# Usage: tools\build-msi-with-plugins.ps1 [-PluginsRepo ..\parley-files-viewer-tui] [-Version 2.0.0] [-Arch x86_64]
#   PluginsRepo  a checkout of parley-files-viewer-tui (default: beside this one)
#
# Needs what build-msi.ps1 needs (JAVA_HOME, WiX 3 on the PATH), and Maven. The plugin builds
# against fsh, so fsh is installed to the local Maven repository first (tests skipped).
param(
	[string] $PluginsRepo = '',
	[string] $Version = '',
	[string] $Arch = 'x86_64'
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (-not $PluginsRepo) { $PluginsRepo = Join-Path (Split-Path -Parent $project) 'parley-files-viewer-tui' }
if (-not (Test-Path (Join-Path $PluginsRepo 'pom.xml'))) { throw "no parley-files-viewer-tui checkout at $PluginsRepo (-PluginsRepo)" }
$PluginsRepo = (Resolve-Path $PluginsRepo).Path

Set-Location $project
& mvn -B -q -DskipTests install
if ($LASTEXITCODE -ne 0) { throw 'fsh build failed' }

Set-Location $PluginsRepo
& mvn -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'plugin build failed' }

# the plain jar and the Lanterna one (browse --with lanterna): not the -all jars, which run alone
$jars = @(Get-ChildItem (Join-Path $PluginsRepo 'parley-files-viewer-tui\target\parley-files-viewer-tui-*.jar'),
	(Join-Path $PluginsRepo 'parley-files-viewer-tui-lanterna\target\parley-files-viewer-tui-lanterna-*-plugin.jar') |
	Where-Object { $_.Name -notmatch '-(all|sources|javadoc|tests)\.jar$' })
if ($jars.Count -lt 2) { throw "expected the tui and lanterna plugin jars under $PluginsRepo, found $($jars.Count)" }
$env:FSH_PLUGINS = ($jars | ForEach-Object { $_.FullName }) -join ';'

Set-Location $project
& (Join-Path $PSScriptRoot 'build-msi.ps1') -Version $Version -Arch $Arch
