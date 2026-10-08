# fsh — the FileSource Shell

fsh is a bash-like shell whose file system is any
[parley-files](https://github.com/tony-bringardner/parley-files) `FileSource`:
the local disk, memory, FTP, SFTP or a database. It implements a large part of
Bash/POSIX: variables, functions, pipelines, redirection, `if`/`for`/`while`/`case`,
traps, history and the usual built-ins.

fsh is built on Parley but is not part of it. It was called BjlShell
(`us.bringardner:bjl_shell`, command `fssh`).

## Requirements

- Java 21
- parley-files (compile), with parley-files-ftp and parley-files-sftp bundled at
  run time so FTP and SFTP work without any setup. parley-files-jdbc is included
  only when you build with `-Pjdbc`.

## Build and run

```sh
mvn package                 # jar in target/
mvn -Pjdbc package          # also bundle the JDBC file system
mvn -Pnative package        # GraalVM native binary named fsh
java -cp ... us.bringardner.fsh.Console
```

On macOS, `macos/compile.sh` builds the native keyboard library and
`macos/fsh.sh` starts the shell.

## Files

| File | Purpose |
|---|---|
| `~/.fshrc` | Run at start-up (`~/.fsshrc` is still read if `.fshrc` doesn't exist) |
| `~/.fsh_history` | Command history (`HISTFILE`) |

Scripts can start with `#!fsh`; `#!fssh` is still accepted.

## Layout

- `src/main/java/us/bringardner/fsh` — the shell (`Console` is the entry point)
- `.../fsh/syntax` — reads a script into its syntax tree, as bash does
- `.../fsh/expand` — word expansion (braces, parameters, `$( )`, splitting, globbing)
- `.../fsh/exec` — runs the syntax tree
- `.../fsh/commands` — the builtins

## The IDE

The script editor and debugger is a separate project,
[fsh-ide](https://github.com/tony-bringardner/fsh-ide). fsh exposes the debugger
hooks it uses (`DebugContext`, `DebugController`).

## License

Apache License 2.0. See [LICENSE](LICENSE).
