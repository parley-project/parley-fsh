# Changelog

## 1.0.0 (unreleased)

First release as fsh. Formerly BjlShell (`us.bringardner:bjl_shell`).

### Renamed
- Maven coordinates: `us.bringardner:bjl_shell` → `us.bringardner:fsh`.
- Packages: `us.bringardner.shell` → `us.bringardner.fsh`;
  the generated parser `us.bringardner.filesource.sh` → `us.bringardner.fsh.parser`
  (source folder `fssh/` → `generated/`).
- Classes: `FsshException` → `FshException`, `FsshList` → `FshList`,
  `BjlShellIDE` → `FshIDE`, `BjlShellIDETextArea` → `FshIDETextArea`,
  `BjlShellTreeViewPanel` → `FshTreeViewPanel`.
- Command name `fssh` → `fsh` (`$0`, the `\s`/`\l` prompt escapes, error messages).
- History file `~/.fssh_history` → `~/.fsh_history`.
- Start-up file `~/.fsshrc` → `~/.fshrc`.
- Dependencies moved from the Bjl libraries to Parley: `parley-files`;
  `parley-files-ftp` and `parley-files-sftp` at run time only; `parley-files-jdbc`
  only with `-Pjdbc`. The unused `bjl_net_framework` dependency was dropped.

### Still accepted
- `~/.fsshrc` is read when `~/.fshrc` doesn't exist.
- `#!fssh` scripts run as before.
- IDE settings saved under the old preferences node are copied to the new one on
  first start.

### Action needed
- macOS: rebuild the native keyboard library with `macos/compile.sh`. The JNI
  function names changed with the package, so the old `libnativekeyboard.dylib`
  no longer links.
- Rename `~/.fssh_history` to `~/.fsh_history` to keep your history.
