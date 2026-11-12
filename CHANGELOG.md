# Changelog

## 2.0.0 (unreleased)

### Changed (2026-11-11: no logging level set at startup, To-Do 19.5)

- `Console.main` no longer sets `parley.log.level=WARN` when it isn't set: parley-logging's loggers
  now let only `WARN` and `ERROR` through when no `parley.log.level` covers them, so fsh, fsh-ide and
  the files viewers get the same default without setting anything. `-Dparley.log.level=DEBUG` (or
  `parley.log.level.<module>`) still shows more.

### Changed (2026-11-10: logging through System.Logger, To-Do 19.5)

- **parley-logging** (To-Do 19.5): fsh includes parley-logging, so what Parley's objects log is
  written by its `ParleyLogger` (`parley.log.file`); `Console.main` sets `parley.log.level=WARN` when
  it isn't set (with no level, Parley's objects would log everything).

### Changed (2026-11-06: parley-ssh on parley-iam)

- The embedded SSH tests (`FshShellFactoryTest`, `EmbeddedSignalsTest`) give their server a parley-iam user
  registry (`server.setUserRegistry`, a `FileUserRegistry` with the test user holding `shell` and `exec`):
  parley-ssh 2.0.0 logs in only users its registry knows, and `setPasswordAuthenticator` / `SshPrincipal` are gone.
  fsh's own code is unchanged.

### Added

- `fsh --wait-for-debugger [address:]port script [arguments]`: the script is started as
  `fsh script` starts it, and waits at that port for a debugger to attach before its first
  command (remote debugging: attach to a script). fsh has no debugger protocol of its own: a
  `us.bringardner.fsh.spi.DebuggerProvider` on the class path or in a plugin folder waits and
  attaches (fsh-loupe's, a Loupe debugger); without one the shell says so and ends with status 2.
  The debugger is told the script's status when it ends, however it ends (`exit` included).

- Watchpoints for debuggers: `DebugContext.variableRead(name, ctx)` and
  `variableWritten(name, ctx)` are told of each variable the script reads and each it sets or
  unsets (an array's name for its elements), and returning true stops the script there, as at a
  breakpoint, until the debugger lets it go on. Only the script's own reads and writes are told:
  not the lookups the shell makes to carry one out (what `x=1` replaces, a nameref followed, the
  attributes `declare` keeps). `ShellContext.peekVariable` reads a variable that way, without
  telling the debugger. fsh-ide uses them for its watchpoints.

- Run by parley-launcher: the build writes `META-INF/parley/launcher.list` into the jar (fsh's
  options and every jar it needs, with its SHA-256, by parley-launcher-maven-plugin) and attaches
  `fsh-VERSION-PLATFORM.jar`, the platform's keyboard library and `fshexec`, where `compile.sh`
  has built them.
- `fsh-VERSION-windows-x86_64.jar` (the `native-windows-x86_64` profile): `windows
ativekeyboard.dll`, rebuilt
  with `windowsmpile.cmd`, for parley-launcher to unpack on Windows. There is no `fshexec` there.
- Installers, each with fsh's own Java runtime (`tools/build-jlink.sh`): a disk image for macOS
  (`tools/build-jpackage.sh`: `fsh.app`, which opens a Terminal window running fsh from the
  Finder, with the command for a terminal in `fsh.app/Contents/app/fsh`), a `.deb` and an `.rpm` for Linux
  (`tools/build-linux-packages.sh`: `/opt/fsh`, `/usr/bin/fsh`, added to `/etc/shells`), and an
  `.msi` for Windows (`tools/build-msi.ps1`). `release-binaries.yml` builds them for a release,
  besides the native binaries. The installers carry plugins too, on fsh's class path: the jars
  `FSH_PLUGINS` names, else the file browser (parley-files-viewer-tui and its Lanterna plugin)
  from the Maven repository, else those in the plugin folders (for `browse`, `view`, `edit` and
  `search`). fsh finds each plugin class once, so
  the same plugin in a plugin folder as well isn't there twice.
- `build-native --builtin` (`macos/`, `linux/`, `windows/`): the native image with plugin jars
  built into it (`FSH_PLUGINS`, else the plugin folders' jars), since a native image can't load
  them at run time. The release's native binaries have the terminal file browser built in.

### Changed

- The terminal file browser is built into fsh: `browse`, `view`, `edit` and `search` are
  builtins (from parley-files-viewer-tui, which fsh now depends on; Parley's own terminal code,
  no UI library), so they work with no plugins installed and in every native image. Browsers from
  plugins (the Lanterna one, `browse --with lanterna`) are still found at run time; `plugins`
  lists only what plugins added. The installers carry only the Lanterna plugin, and
  parley-files-viewer-tui's library is built before fsh.

### Changed (needs a configuration change)

- **Property names** (Parley 2.0.0; the old names are no longer read): `fsh.*` is `parley.fsh.*`:
  `parley.fsh.native`, `parley.fsh.native.notice`, `parley.fsh.native.debug`, `parley.fsh.exec`,
  `parley.fsh.libdir`, `parley.fsh.spawn`, `parley.fsh.credentials.dialog`, `parley.fsh.argv0`, and those the
  native launcher passes, `parley.fsh.ignoredAtStart` and `parley.fsh.cfAbsent` (rebuild `fshexec` with the
  new `fshexec.c`). The build's `fsh.noKeychain`, `fsh.writeCommandsDoc` and `fsh.test.reportDir` are
  `parley.fsh.noKeychain`, `parley.fsh.writeCommandsDoc` and `parley.fsh.test.reportDir`.
- The parley-ssh shell is named with `parley.ssh.shellFactory`.

### Added

- **Plugins**: fsh loads the jars in its plugin folders as it starts (`/usr/local/lib/parley/fsh/plugins`,
  `/usr/local/lib/parley/plugins`, `~/.parley/fsh/plugins`, `~/.parley/plugins`, in that order; `fsh -r`
  leaves out the user's), with parley-core's `Plugins`. A plugin adds commands
  (`us.bringardner.fsh.spi.CommandProvider`; a builtin or an earlier plugin keeps its name), a browser
  (`us.bringardner.fsh.spi.BrowserProvider`), file systems, credential stores and JDBC drivers.
- `plugins`: the plugin folders, what was loaded from them, skipped or shadowed, and what that added.
- `browse [-1] [-c] [--with NAME] [DIR [DIR2]]`, `browse --list`: a file browser from a plugin, the one
  of the highest priority that can run here (a window when there is a display, else a terminal one).

### Changed

- **No "mount" in fsh's words or code** (fsh connects, as Linux mounts): `connect` and `disconnect` take a
  `PATH` (was `MOUNT_POINT`) and their messages say "connect"; `Console.mount` is `connectAt`,
  `getMountFactory` / `setMountFactory` are `getTree` / `setTree`, and the tree's `mount` / `unmount`
  are `connectAt` / `disconnectAt`. `TestMountedFactory` is `TestConnectionTree`.
- **The shell's file tree is parley-files'** (`us.bringardner.parley.files.tree`: `VirtualFileSourceFactory`,
  `TreeFile`, `LocalFiles`, moved there from `us.bringardner.fsh`), so the console browser on its own
  (pfv) has the same tree. Nothing else changes.
- **Raw mode turns IEXTEN off** in the keyboard library (`NativeKeyboard.cpp`, as `cfmakeraw`
  does): on macOS the terminal driver kept Ctrl-V and Ctrl-O for itself even in raw mode, so the
  line editor's Ctrl-V (the next key as it is) and Ctrl-O never worked there, nor Ctrl-V in the
  browser. Rebuild `libnativekeyboard` (`macos/compile.sh`, `linux/compile.sh`).
- **Connections** (connect, disconnect) are new code: a table of the shell's connections that
  is replaced, never changed, so a listing or a Tab completion that runs while a browser
  connects or disconnects sees it whole; and one kind of file for the shell's tree (`TreeFile`,
  in place of `RootFile`, `FileSourceDynamicProxy` and a proxy per file). `LocalFiles.of(file)` is
  the local file a file of the tree is (null for a connected file system's): what used to be
  `file instanceof FileProxy`.
- A connected file system is listed in the folder it's connected at, as a folder there: after
  `connect memory /mem`, `ls /`, `echo /*`, Tab completion and `cd /mem; cd ..; ls` show `mem` (it
  hides a local entry of the same name, and gives it back on disconnect). Before, `/mem` could be
  used but wasn't listed. A file system can be connected in another one (`connect memory
  /mem/a/more`), and `cd ..` goes back up through both.
- `connect` with no arguments lists the shell's connections (`/mem -> memory`), and `ls -l` shows
  what is connected at one, as it shows a link's target (`mem -> memory`,
  `sftp-build -> sftp tony@build:/home/tony`). A connection's time is its connected folder's (it
  showed Dec 31 1969).
- The keyboard, the terminal and the line editor are in **parley-term**, a new Parley module that fsh
  now depends on, so other Parley applications (the console file browser) share them:
  `us.bringardner.fsh.LineEditor` is `us.bringardner.parley.term.line.LineEditor`, Tab completion's
  `Completion.Result` and `Completion.Candidate` are parley-term's `Completions.Result` and
  `Completions.Candidate`, and the JNA terminal code (`jna.UnixOps` and `jna.WindowsOps`' keyboard
  and console part) is parley-term's `tty.jna`. `NativeKeyboard` keeps its methods and extends
  parley-term's `Keyboard`; the JNI library (`libnativekeyboard`) is unchanged and still the
  default, given to parley-term as its terminal. `parley.fsh.native.debug` also turns on
  `parley.term.native.debug`.
- `NativeKeyboard.rows()`: the terminal's height (with the JNI library, through JNA where it loads;
  else `$LINES`, else 24).

### Fixed

- A pipe stage's own variables are listed there: `set`, `declare -p`, `${!prefix*}`, completion
  and debuggers (`ShellContext.getVariables`) left out what a stage had set (or showed what it had
  unset), such as the variables of `cmd | while read ...; do ...; done`, though the stage itself
  read them correctly.
- An SSH server whose sessions are fsh (parley-ssh with `FshShellFactory`) now stops on SIGTERM, as
  a service manager stops it: fsh took the process's OS signals (TERM, INT, HUP ...) as soon as it
  loaded, so the server ignored them. The new system property `parley.fsh.osSignals=false` keeps
  fsh from taking them where it runs inside another program; `FshShellFactory` sets it. A session's
  `trap` no longer sees the server's signals.

## 1.1.0 (unreleased)

Needs swing-widgets 1.1.0.

### Changed

- fsh's own window (`ConsoleFrame`, opened when fsh has no terminal) has the gradient look of the
  other Parley applications, from swing-widgets' `GradientLook`. It is installed only when fsh opens
  that window, so a shell in a terminal never loads Swing for it.

## 1.0.0 (2026-10-21)

First release as fsh. Formerly BjlShell (`us.bringardner:bjl_shell`).

### Renamed
- Maven coordinates: `us.bringardner:bjl_shell` → `us.bringardner:fsh`.
- Packages: `us.bringardner.shell` → `us.bringardner.fsh`;
  the generated parser `us.bringardner.filesource.sh` → `us.bringardner.fsh.parser`
  (source folder `fssh/` → `generated/`).
- Classes: `FsshException` → `FshException`, `FsshList` → `FshList`.
- Command name `fssh` → `fsh` (`$0`, the `\s`/`\l` prompt escapes, error messages).
- History file `~/.fssh_history` → `~/.fsh_history`.
- Start-up file `~/.fsshrc` → `~/.fshrc`.
- Dependencies moved from the Bjl libraries to Parley: `parley-files`;
  `parley-files-ftp` and `parley-files-sftp` at run time only; `parley-files-jdbc`
  only with `-Pjdbc`. The unused `bjl_net_framework` dependency was dropped.

### Moved out
- The IDE (`us.bringardner.shell.ide`) moved to its own project,
  [fsh-ide](https://github.com/tony-bringardner/fsh-ide), with its images and
  spell-check dictionary. fsh no longer depends on the RSyntaxTextArea
  autocomplete/spellchecker libraries, JAXB or the full ANTLR tool (only
  `antlr4-runtime`).
- `DebugControlPanel.DebugController` is now `us.bringardner.fsh.DebugController`.

### Added
- `tools/build-jlink.sh`: fsh with a Java runtime of its own (`target/jlink/fsh`), cut down by
  jlink to the modules fsh uses, with a class cache from `tools/aot-training.sh` (AOT cache on
  JDK 25+, CDS archive before); `--package VERSION` makes a download of it.
- `tools/build-jpackage.sh`: installers of that build with jpackage (pkg or dmg on macOS, deb or
  rpm on Linux, or an app image).

### Changed
- `macos/fsh` and `linux/fsh` take `FSH_RUNTIME`: `jvm` runs the jar on Java even when there is a
  native image, `native` runs the native image (an error if none is built), and `auto` (or unset)
  chooses as before: the native image when it is as new as the jar.
- Runs on Java 11 or later (was Java 21), like the other components of the library: records,
  pattern matching, switch expressions and the Java 12-21 library methods were rewritten in Java 11
  terms, with no change in behaviour. The launchers pass `--enable-native-access` only to Java 17
  or later, which Java 11 doesn't start with (`java-options.sh`, `java-options.cmd`).
- All of `NativeKeyboard`'s native functions are also done through JNA (`us.bringardner.fsh.jna`:
  libc on macOS and Linux, kernel32 on Windows), so a system the JNI library (`macos/`, `linux/`,
  `windows/`) was not built for still has raw keys, the terminal's width, job control and, on Unix,
  starting programs with `posix_spawn` and passing descriptors. The JNI library is used when it is
  loaded, as before (and always in a native image); `-Dfsh.native=jna` uses JNA, `-Dfsh.native=jni`
  never. Only `fshexec` and the native image's launcher stay C. New dependency:
  `net.java.dev.jna:jna`. What every implementation must do is in `NATIVE-CONTRACT.md`.
- Releases carry a native binary for each platform as a separate download: macOS (Apple silicon
  and Intel), Linux (x86-64 and ARM64) and Windows (x64), built by
  `.github/workflows/release-binaries.yml` when a release is published. New:
  `windows/build-native.cmd`, `tools/package-native.sh`, `tools/package-native.ps1`.
- An interactive shell with limited native support (no keyboard and terminal library, or no
  `fshexec`) says so in one line before its first prompt, and `fsh --native-status` reports what
  was found, where it looked and why something did not load. `FSH_NATIVE_NOTICE=0` silences the
  line. The README has a "Native support" section on what each combination provides.
- The GUI console's workings are in `ConsoleIO`, which uses no UI toolkit, so consoles in
  other toolkits (JavaFX) can share them; `ConsolePanel` is now its Swing view. Its
  methods are unchanged.
- In the GUI console, a line typed while a script runs goes to the script's standard
  input, so `read` gets it (it used to wait for ever). Interrupting a thread waiting for
  that input ends the wait.
- The GUI console no longer waits on the UI for every write: output is decoded as UTF-8
  (a character split across writes, or a non-ASCII one written a byte at a time, is no
  longer garbled) and shown in batches. Debug messages it printed to `System.out` and
  its status labels are gone.

### Fixed
- On Linux a pipeline of programs could hang (`printf 'b\na\n' | sort | head -n 1`, about one
  time in 300): a program got the named pipe the next program's input was being opened on, so
  that program never saw the end of its input. A program now gets only its own descriptors, as
  on macOS (with glibc 2.34 or later; with an older one, as before).
- Code run as a string (`Console.executeScript(ShellContext, code)`, as fsh-ide, fshdb and jobs
  run scripts) lost its leading blank lines, so `$LINENO`, `BASH_LINENO` and error messages
  were too low by that many lines in a script that starts with blank lines. They're kept now.
- Inside a function, `ShellContext.getVariables()` gave `$0` as the function's name (`$0`
  itself expands to the script's, as in bash), so debuggers showed it. And
  `getAllPositionalParameters()` inserted the function's `$1 ...` in front of the script's (or
  failed with an index error when the script had fewer), which made a debugger's "set $1" change
  the script's parameters. It now returns `$0 $1 ...` as they expand: the script's `$0`, then the
  function's parameters.
- Inside a function, its `local` variables were missing from the shell's list of variables, and
  the outer values showed instead (a local that was `unset` still showed the global). So `set`,
  `declare -p`, `${!prefix*}`, completion and debuggers (fsh-ide's variables view, fshdb) didn't
  see them. `ShellContext.getVariables()` now includes them as names are looked up: the innermost
  function's local wins and an unset local hides the variable outside, as in bash.
- `connect FACTORY - PATH` reads its `name=value` settings from standard input separated by any
  white space; it split on single spaces, so two spaces or a tab between settings was an error
  (`Invalid arg =`).

### Still accepted
- `~/.fsshrc` is read when `~/.fshrc` doesn't exist.
- `#!fssh` scripts run as before.

### Action needed
- macOS: rebuild the native keyboard library with `macos/compile.sh`. The JNI
  function names changed with the package, so the old `libnativekeyboard.dylib`
  no longer links.
- Rename `~/.fssh_history` to `~/.fsh_history` to keep your history.
