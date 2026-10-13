# Changelog

## 1.0.0 (unreleased)

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
- `connect FACTORY - MOUNT` reads its `name=value` settings from standard input separated by any
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
