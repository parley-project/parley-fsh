# The Windows installer with the file browser plugin (browse, view, edit, search) built in, and
# fsh-ide and fshdb beside fsh: builds the jars of parley-files-viewer-tui, names them in
# FSH_PLUGINS, then runs build-msi.ps1.
#
# Usage: tools\build-msi-with-plugins.ps1 [-PluginsRepo ..\parley-files-viewer-tui] [-Version 2.0.0] [-Arch x86_64]
#            [-IdeRepo ..\fsh-ide | -NoIde] [-DbRepo ..\fshdb | -NoDb] [-MenuGroup fsh]
#   PluginsRepo  a checkout of parley-files-viewer-tui (default: beside this one)
#   IdeRepo      a checkout of fsh-ide, whose Swing IDE is installed beside fsh (default: beside
#                this one; -NoIde leaves it out). fsh-loupe, beside this one too, is installed first.
#   DbRepo       a checkout of fshdb, the console debugger (default: beside this one; -NoDb leaves it
#                out). It builds against fsh-ide, so that is installed first.
#   ViewerRepo   a checkout of parley-files-viewer, the Swing file browser (default: beside this one;
#                -NoViewer leaves it out)
#   MenuGroup    the Start menu folder the programs are in (default: fsh)
#
# Needs what build-msi.ps1 needs (JAVA_HOME, WiX 3 on the PATH), and Maven. The plugin builds
# against fsh, so fsh is installed to the local Maven repository first (tests skipped).
param(
	[string] $PluginsRepo = '',
	[string] $Version = '',
	[string] $Arch = 'x86_64',
	[string] $IdeRepo = '',
	[switch] $NoIde,
	[string] $DbRepo = '',
	[string] $ViewerRepo = '',
	[switch] $NoDb,
	[switch] $NoViewer,
	[string] $MenuGroup = 'fsh'
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
# fsh-ide (the Swing IDE) and fshdb go in the same installer, from checkouts beside this one
if (-not $IdeRepo -and -not $NoIde) { $IdeRepo = Join-Path $beside 'fsh-ide' }
if ($NoIde) { $IdeRepo = '' }
if (-not $DbRepo -and -not $NoDb) { $DbRepo = Join-Path $beside 'fshdb' }
if ($NoDb) { $DbRepo = '' }
if (-not $ViewerRepo -and -not $NoViewer) { $ViewerRepo = Join-Path $beside 'parley-files-viewer' }
if ($NoViewer) { $ViewerRepo = '' }
if ($IdeRepo -or $DbRepo) {
	# (fsh-ide's dependency, built against fsh: not in a Maven repository until it is installed)
	$loupe = Join-Path $beside 'fsh-loupe'
	if (Test-Path (Join-Path $loupe 'pom.xml')) { Install-Module $loupe 'fsh-loupe' }
}
if ($DbRepo) {
	# (fshdb is built against fsh-ide, wherever that comes from)
	$ide = if ($IdeRepo) { $IdeRepo } else { Join-Path $beside 'fsh-ide' }
	if (Test-Path (Join-Path $ide 'pom.xml')) { Install-Module $ide 'fsh-ide' }
}
& (Join-Path $PSScriptRoot 'build-msi.ps1') -Version $Version -Arch $Arch -IdeRepo $IdeRepo -DbRepo $DbRepo -ViewerRepo $ViewerRepo -MenuGroup $MenuGroup
