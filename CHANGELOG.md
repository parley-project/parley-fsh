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

### Still accepted
- `~/.fsshrc` is read when `~/.fshrc` doesn't exist.
- `#!fssh` scripts run as before.

### Action needed
- macOS: rebuild the native keyboard library with `macos/compile.sh`. The JNI
  function names changed with the package, so the old `libnativekeyboard.dylib`
  no longer links.
- Rename `~/.fssh_history` to `~/.fsh_history` to keep your history.
