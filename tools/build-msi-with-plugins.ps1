# The Windows installer with the Lanterna browser plugin built in (fsh has its own file browser:
# browse, view, edit, search), and fshdb beside fsh: builds the jars of parley-files-viewer-tui,
# names the plugin in FSH_PLUGINS, installs what fshdb builds against, then runs build-msi.ps1.
#
# The installer holds commands only, fsh and fshdb. The window programs, parley-fsh-studio and
# parley-files-viewer, have installers of their own (parley-build's packaging).
#
# Usage: tools\build-msi-with-plugins.ps1 [-PluginsRepo ..\parley-files-viewer-tui] [-Version 2.0.0] [-Arch x86_64]
#            [-DbRepo ..\fshdb | -NoDb] [-IdeRepo ..\parley-fsh-studio] [-LoupeRepo ..\fsh-loupe]
#   PluginsRepo  a checkout of parley-files-viewer-tui (default: beside this one)
#   DbRepo       a checkout of fshdb, the console debugger (default: beside this one; -NoDb leaves it
#                out)
#   IdeRepo      a checkout of parley-fsh-studio, which fshdb builds against, so it is installed to the local
#                Maven repository first (default: beside this one). parley-fsh-studio itself is not in the
#                installer.
#   LoupeRepo    a checkout of fsh-loupe, which parley-fsh-studio needs (default: beside this one)
#
# Needs what build-msi.ps1 needs (JAVA_HOME, the WiX Toolset on the PATH), and Maven. The plugin
# and fshdb build against fsh, so fsh is installed to the local Maven repository first (tests
# skipped).
param(
	[string] $PluginsRepo = '',
	[string] $Version = '',
	[string] $Arch = 'x86_64',
	[string] $LoupeRepo = '',
	[string] $IdeRepo = '',
	[string] $DbRepo = '',
	[switch] $NoDb
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$beside = Split-Path -Parent $project
if (-not $PluginsRepo) { $PluginsRepo = Join-Path $beside 'parley-files-viewer-tui' }
if (-not (Test-Path (Join-Path $PluginsRepo 'pom.xml'))) { throw "no parley-files-viewer-tui checkout at $PluginsRepo (-PluginsRepo)" }
$PluginsRepo = (Resolve-Path $PluginsRepo).Path

function Install-Module([string] $dir, [string] $what) {
	Push-Location $dir
	& mvn -B -q -DskipTests install
	$code = $LASTEXITCODE
	Pop-Location
	if ($code -ne 0) { throw "$what build failed" }
}

# (fsh builds the file browser in, so the browser's library is installed first; the Lanterna
# plugin builds against fsh, so it is built after fsh)
Set-Location $PluginsRepo
& mvn -B -q -DskipTests -pl parley-files-viewer-tui install
if ($LASTEXITCODE -ne 0) { throw 'file browser build failed' }

Set-Location $project
& mvn -B -q -DskipTests install
if ($LASTEXITCODE -ne 0) { throw 'fsh build failed' }

Set-Location $PluginsRepo
& mvn -B -q -DskipTests -pl parley-files-viewer-tui-lanterna package
if ($LASTEXITCODE -ne 0) { throw 'plugin build failed' }

# the Lanterna plugin (browse --with lanterna): not the -all jar, which runs alone
$jars = @(Get-ChildItem (Join-Path $PluginsRepo 'parley-files-viewer-tui-lanterna\target\parley-files-viewer-tui-lanterna-*-plugin.jar') |
	Where-Object { $_.Name -notmatch '-(all|sources|javadoc|tests)\.jar$' })
if ($jars.Count -lt 1) { throw "expected the lanterna plugin jar under $PluginsRepo, found none" }
$env:FSH_PLUGINS = ($jars | ForEach-Object { $_.FullName }) -join ';'

Set-Location $project
# fshdb goes in the same installer, from a checkout beside this one
if (-not $DbRepo -and -not $NoDb) { $DbRepo = Join-Path $beside 'fshdb' }
if ($NoDb) { $DbRepo = '' }
if ($DbRepo) {
	if (-not (Test-Path (Join-Path $DbRepo 'pom.xml'))) { throw "no fshdb checkout at $DbRepo (-DbRepo, or -NoDb)" }
	# (parley-fsh-studio's dependency, built against fsh: not in a Maven repository until it is installed)
	$loupe = if ($LoupeRepo) { $LoupeRepo } else { Join-Path $beside 'fsh-loupe' }
	if (Test-Path (Join-Path $loupe 'pom.xml')) { Install-Module $loupe 'fsh-loupe' }
	# (fshdb is built against parley-fsh-studio)
	$ide = if ($IdeRepo) { $IdeRepo } else { Join-Path $beside 'parley-fsh-studio' }
	if (-not (Test-Path (Join-Path $ide 'pom.xml'))) { throw "no parley-fsh-studio checkout at $ide (-IdeRepo: fshdb builds against it; or -NoDb)" }
	Install-Module $ide 'parley-fsh-studio'
}
& (Join-Path $PSScriptRoot 'build-msi.ps1') -Version $Version -Arch $Arch -DbRepo $DbRepo
