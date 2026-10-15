# fsh — the FileSource Shell

fsh is a bash-compatible shell written in Java. It runs bash scripts and works as an
interactive shell, and its file system is any
[parley-files](https://github.com/tony-bringardner/parley-files) `FileSource`: the local
disk, memory, FTP, SFTP or a database. A remote server can be mounted into the shell's
directory tree, and from then on `cd`, globbing, redirection and fsh's file builtins work
there as they do on the local disk.

fsh is built on Parley but is not part of it: it is **an example of what
[Parley](https://github.com/tony-bringardner/parley-parent#what-parley-can-help-you-build) can help
you build**, using the library the same way your own application would. It was called BjlShell
(`us.bringardner:bjl_shell`, command `fssh`).

<img src="https://raw.githubusercontent.com/tony-bringardner/parley-brand/master/src/main/resources/us/bringardner/parley/brand/parley-hero.png" alt="Margot the magpie on the Parley tower" width="160">

- [What fsh is](#what-fsh-is)
- [Why a shell in Java](#why-a-shell-in-java)
- [Why bash is the target](#why-bash-is-the-target)
- [How close to bash it is, and how that is tested](#how-close-to-bash-it-is-and-how-that-is-tested)
- [Known differences from bash](#known-differences-from-bash)
- [Performance](#performance)
- [Requirements](#requirements), [Build and run](#build-and-run), [Files](#files), [Layout](#layout)

## What fsh is

- **A bash-compatible shell.** It has the bash language: variables and arrays (indexed and
  associative), functions, pipelines, redirection, here-documents, `$( )` and `<( )`, `[[ ]]`
  and `(( ))`, `if`/`for`/`while`/`case`/`select`, traps, job control, history with `!`
  expansion, programmable completion (`complete`, `compgen`), and bash's builtins and `shopt`
  options. Scripts written for bash run unchanged; the [measurements below](#how-close-to-bash-it-is-and-how-that-is-tested)
  show how closely.
- **A shell over any file system.** `connect` mounts a `FileSource` (FTP, SFTP, a database,
  memory) at a path, and `unmount` removes it. fsh's own file builtins (`ls`, `cp`, `mv`,
  `rm`, `mkdir`, `touch`, `ln`, `find`, `wc`) work on every mounted file system.
- **No passwords in scripts.** A connect setting can name its secret instead of holding it, and
  `cred` stores and checks those secrets ([below](#passwords-in-startup-scripts)).
- **An interactive shell** with a bash-style line editor (emacs keys, history search,
  completion, the "Display all N possibilities?" pager).
- **Embeddable.** It is a Java library: a program can run scripts in it, and the
  [fsh-ide](https://github.com/tony-bringardner/fsh-ide) script editor and debugger use its
  debugger hooks.

## Why a shell in Java

- **One file-system interface.** Everything fsh does with files goes through `FileSource`, so
  a script can work on a remote or virtual file system with the same commands it uses on the
  local disk. A native shell can reach a remote file system only through programs (`scp`,
  `sftp`, `lftp`) or a kernel mount; fsh does it inside the shell.
- **Running inside Java programs.** An application can embed fsh as its scripting language,
  hand it its own file systems and stream objects, and debug scripts step by step, which the
  fsh-ide project does.
- **Java's libraries.** FTP, SFTP and JDBC come from existing Java libraries, and new file
  systems are Parley plug-ins rather than shell code.

Where Java gets in the way (programs started as children of a subshell, signals, terminal
control, raw bytes in arguments), fsh uses a small native library (`macos/NativeKeyboard.cpp`)
and helper (`macos/fshexec.c`) so that it behaves as bash does.

## Why bash is the target

- **It is what scripts are written for.** bash is the default shell for scripts on Linux, in
  CI systems and in container images, and the shell most shell scripts on the web, in package
  managers and in build tools assume. A shell that runs bash's language runs the most existing
  code.
- **It is a superset of POSIX `sh`.** Targeting bash also covers `#!/bin/sh` scripts.
- **It can be checked.** bash has a reference manual and a large test suite of its own, and
  plenty of real software is written in it (Homebrew, git's completion, bash-completion, the
  bats test framework). That makes "behaves like bash" something that can be measured, test
  by test, rather than a goal.
- **zsh and fish are less suitable targets.** zsh by default does not split unquoted words,
  numbers arrays from 1 and has other non-POSIX defaults, so bash scripts often behave
  differently in it. fish has its own language and doesn't run sh or bash scripts at all.
  ksh93 and dash are close to POSIX but have far fewer scripts written specifically for them.

## How close to bash it is, and how that is tested

As of 2026-10-10:

| Check | What it compares | Result |
|---|---|---|
| bash's own test suite (bash 5.3, `tools/bash-suite/runall.py`) | the output of each of the 83 test files, run by bash and by fsh | **96.5% of bash's output lines** are the same in fsh (13,216 of 13,691); **59 of 83** files match exactly |
| Syntax check (`tools/real-world/syntax.py`) | `bash -n` against `fsh -n` on every sh and bash script on the machine | **276 of 276** agree |
| Real-world scripts (`tools/real-world/cases.py`) | output, errors and status of Homebrew, git completion and prompt, git subtree and filter-branch, libtool, man, xzgrep, bash-completion and others | **46 of 47** identical (the other differs only in fsh's own help texts) |
| bats-core 1.14 test suite, run with fsh as `bash` | bats' own 482 tests | **468** pass; bash passes 469 here (its 13 failures are environment-specific, and fsh fails the same 13). The remaining one is an intermittent failure, see below |
| bash's `configure` script | run under both shells | identical results |
| Interactive editing (`tools/interactive/keys.py`) | key sequences typed into a pseudo-terminal: editing, history, completion, the pager | **56 of 56** identical |
| Unit tests (`mvn test`) | fsh's own JUnit tests | **996** pass |

Every remaining difference in bash's test suite has been examined and falls into one of
three groups, listed in [tools/bash-suite/TODO.md](tools/bash-suite/TODO.md):

- **deliberate differences** (next section), which account for most of the remaining lines;
- **quirks of the installed bash** where fsh already matches bash's own expected-output
  files (`tests/*.right`);
- **bash internals leaking through,** such as bash's internal quoting bytes in arithmetic
  errors, which fsh doesn't copy.

None of the remaining differences in bash's test suite is an open bug. Most of the work was
done in rounds: run the suites, take the differences one by one, fix fsh and run everything
again. The todo file records what each fix involved.

## Known differences from bash

**Deliberate:**

- **fsh's own file builtins.** `ls`, `cp`, `mv`, `rm`, `mkdir`, `touch`, `ln`, `find`, `wc`,
  `sleep`, `expr`, `clear`, `connect`, `cred` and `unmount` are builtins (so they work on mounted file
  systems), with their own help texts. Programs of the same names are still reachable by path or
  with `enable -n`.
- **fsh's own name, help and version texts,** startup files (`~/.fshrc`, `~/.fsh_logout`), and
  no bash identity variables (`BASH_VERSION`, `BASH_VERSINFO`, `BASH`). Scripts that refuse to run
  without them (Homebrew does) can be given them in an `FSH_ENV` file.
- **Associative-array keys are listed sorted**, not in bash's hash order.
- **Always UTF-8,** whatever `LANG` says; byte-oriented locales such as `fr_FR.ISO8859-1`
  aren't supported.
- **Aliases expand in scripts** and in `-c`, not only interactively.
- **Programs in the current directory run without `./`** (except in a restricted shell).
- **`~` follows `$HOME`;** `extglob` is always on; `>( )` is waited for.

**Limitations (not by choice):**

- **Background jobs end with the script.** fsh runs subshells, including `&` jobs, inside its
  own process, so when a script exits, the jobs it started in the background end too. In bash
  they keep running.
- **File names that aren't valid UTF-8** can't be created or opened (Java's file layer needs
  valid text).
- **Loadable builtins:** `enable -f` finds and checks a library as bash does, but fsh can't run
  bash's C builtins.
- **Under investigation:** a rare hang, seen in two of eight full bats runs (none since the
  latest fixes), where a program at the end of a pipeline never sees end-of-file.

## Performance

Measured on a 3.3 GHz Intel Core i5-10600 (macOS 26.6), median of three runs (nine for
start-up), in seconds. The scripts are POSIX `sh` so every shell runs the same code;
lower is better.

| Test | bash 5.3 | bash 3.2 (macOS) | zsh 5.9 | dash | ksh93 | fsh (JVM) | fsh (native) |
|---|---|---|---|---|---|---|---|
| Start-up (`-c :`) | 0.016 | 0.004 | 0.007 | 0.003 | 0.004 | 0.141 | 0.033 |
| Loop, 100,000 iterations | 0.80 | 1.07 | 0.39 | 0.50 | 0.14 | 0.89 | 0.86 |
| Loop, 1,000,000 iterations | 11.7 | — | 5.0 | 7.2 | 2.0 | **4.9** | 11.2 |
| 20,000 function calls | 0.34 | 0.38 | 0.32 | 0.18 | 0.06 | 0.59 | 0.29 |
| 20,000 string trims | 0.39 | 0.45 | 0.13 | 0.23 | 0.08 | 0.67 | 0.37 |
| 1,000 `$(echo hi)` | 0.91 | 0.62 | 0.64 | 0.49 | 0.03 | 0.38 | 0.09 |
| `read` loop, 20,000 lines | 0.23 | 0.22 | 0.17 | 0.16 | 0.03 | 0.54 | 0.28 |
| 200 external programs | 0.53 | 0.48 | 0.50 | 0.47 | 0.50 | 0.74 | 0.50 |
| 100 three-stage pipelines | 0.37 | 0.32 | 0.37 | 0.30 | 0.34 | 0.98 | 0.77 |

What this shows:

- **Start-up:** the JVM build takes about 0.14 s to start, which shows in short scripts run
  many times. The native build (GraalVM) starts in about 0.03 s.
- **Short scripts:** both builds are within about 2× of bash on most tests and faster than
  bash at command substitution, because fsh runs `$( )` in-process instead of forking.
- **Long-running scripts:** the JVM build's just-in-time compiler pays off; on a million-step
  loop it is more than twice as fast as bash 5.3 and on a par with zsh. The native build has no
  JIT and stays at bash's speed.
- **Starting programs and pipelines** costs fsh more than a native shell: starting a program
  from the JVM is slower than bash's `fork` and `exec`, and fsh moves data between pipeline
  stages itself.
- **Memory:** bash uses about 4 MB. fsh's JVM build peaks at about 88 MB on a busy script, the
  native build at about 27 MB.

ksh93 is the fastest shell here at almost everything; among other things it runs `$( )` of
builtins without forking.

The benchmark scripts are simple POSIX loops; to reproduce, time `SHELL script.sh` for each
shell (the scripts are in the table's terms: `while [ "$i" -lt N ]` with `$((i+1))`, a function
call, `${s#abc}`/`${t%hij}`, `v=$(echo hi)`, `while read -r`, `/usr/bin/true`,
`echo x | cat | cat`).

## Requirements

- Java 11 or later, to run it and to build it, like the rest of the Parley library. It starts
  fastest on JDK 25 or later (its AOT cache) or as the native image (built with GraalVM).
- parley-files (compile), with parley-files-ftp and parley-files-sftp bundled at
  run time so FTP and SFTP work without any setup. parley-files-jdbc is included
  only when you build with `-Pjdbc`.

## Download

Each [release](https://github.com/tony-bringardner/fsh/releases) has a native binary for every
platform as a separate download (a GraalVM native image, which starts in about 0.03 s, with its
keyboard library); nothing else needs to be installed:

| Platform | File |
|---|---|
| macOS, Apple silicon | `fsh-VERSION-macos-aarch64.tar.gz` |
| macOS, Intel | `fsh-VERSION-macos-x86_64.tar.gz` |
| Linux, x86-64 | `fsh-VERSION-linux-x86_64.tar.gz` |
| Linux, ARM64 | `fsh-VERSION-linux-aarch64.tar.gz` |
| Windows, x64 | `fsh-VERSION-windows-x86_64.zip` |

Unpack it and run `fsh` (`fsh.exe`); keep the files in the folder together. Each file has a
`.sha256` beside it. macOS marks a download as quarantined and the files are not notarized: once,
after unpacking, `xattr -dr com.apple.quarantine .`

The binaries are built by `.github/workflows/release-binaries.yml` when a release is published,
from `macos/`, `linux/` or `windows/` (`compile`, then `build-native`) and packaged by
`tools/package-native.sh` (`.ps1` on Windows). The behaviour of the native library each one
carries is specified in [NATIVE-CONTRACT.md](NATIVE-CONTRACT.md).

## Native support

fsh's terminal handling and program starting use native code, and keeps working without it. What
you get depends on which of three pieces are present, from most to least:

| Present | Keyboard, terminal, job control | Starting programs | What is limited |
|---|---|---|---|
| **The JNI library and `fshexec`** (the download; `macos/`, `linux/`, `windows/` `compile`) | all | `posix_spawn` | nothing |
| **JNA, and `fshexec`** (the jar, with `fshexec` beside the library path) | all | `posix_spawn` through JNA | nothing |
| **JNA, no `fshexec`** (the plain jar on a platform nobody built for) | all | `posix_spawn` through JNA | programs share the shell's process group; no `exec -a` for programs or shared stdin position; no subshell stand-in; no user and group name completion; no `enable -f` |
| **Nothing** (no JNI library, JNA cannot load) | none | Java's `ProcessBuilder` | no raw keyboard (each typed line is echoed twice, arrow keys act only after Enter), Ctrl-C may not interrupt a builtin, no Ctrl-Z, no terminal width; plus the row above |

Windows has no `fshexec` and no process groups, by design: its first row is the JNI DLL or JNA, and
it starts programs with `ProcessBuilder`.

JNA brings its own small library inside its jar for the usual platforms (macOS, Linux on x86-64,
ARM64 and others, Windows). It is extracted to the temporary directory, so a `noexec` or read-only
`java.io.tmpdir` is the usual reason it is not there.

**Finding out which you have.** An interactive shell that is limited says so in one line before
the first prompt:

```
fsh: fshexec not found (fsh --native-status says more): programs share the shell's process group ...
fsh: no native support (fsh --native-status says why): line editing and job control are limited
```

`fsh --native-status` prints what was found, where it looked and why something failed to load
(`--help`'s usage is bash's, and does not list it). To silence the line, set `FSH_NATIVE_NOTICE=0`
(or `-Dfsh.native.notice=false`).

**Choosing.** `-Dfsh.native=` is `auto` (the default: the JNI library when it loads, else JNA; a
native image never tries JNA), `jni`, or `jna`. What every implementation must do is in
[NATIVE-CONTRACT.md](NATIVE-CONTRACT.md).

## Build and run

```sh
mvn package                 # target/fsh.jar: fsh and everything it needs, in one jar
mvn -Pjdbc package          # also bundle the JDBC file system
mvn -Pnative package        # GraalVM native binary named fsh
java -jar target/fsh.jar
```

Use `target/fsh.jar` rather than an IDE's "export runnable jar": an export packs whatever the
IDE's classpath held at the time, and can quietly leave out a library the pom added since (the
OS keychain, say). `cred providers` lists the keychain either way, and says why when it's missing.

On macOS and Linux, `compile.sh` (in `macos/` or `linux/`) builds the native keyboard library,
and `fsh` there starts the shell (`fsh.sh` starts it with a debugger port on localhost:8001).

`fsh` runs the native image (`build-native.sh`, in `target/native/`) when there is one as new as
the jar, and otherwise the jar on Java. The native image starts faster (about 0.03 s, against
0.14 s); the JVM is faster on long-running scripts (see [Performance](#performance)). To choose
for one run, set `FSH_RUNTIME`:

```sh
FSH_RUNTIME=jvm macos/fsh long-job.sh     # the jar on Java, even if there is a native image
FSH_RUNTIME=native macos/fsh              # the native image (an error if none is built)
```

`auto`, or leaving it unset, is the default choice above.

## Passwords in startup scripts

A `connect` setting that holds a secret (an SFTP, FTP or JDBC password) can name where the secret
is kept instead, so `~/.fshrc` is safe to commit and share:

```bash
connect sftp host=prod.example.com user=deploy auth=Agent /prod
connect ftp  host=files.example.com user=uploader secure=true password=@keychain:parley/ftp /ftp
connect jdbc url=jdbc:postgresql://db/app user=app password=@env:APP_DB_PW /db
```

| Reference | Where the secret is |
|---|---|
| `@keychain:service/account` | The OS keychain: macOS Keychain, Linux Secret Service, Windows Credential Manager (the Java build, on Java 17 or later; not the native binary yet) |
| `@keystore:name` | `~/.parley/credentials.p12`, encrypted with a master password |
| `@file:path` | A file only its owner can read |
| `@env:NAME` | An environment variable |
| `@prompt:` | Asked for on the terminal, once per session |
| `@cmd:command` | What a command prints (`op read ...`); off until `cred enable cmd` |

`cred set keychain:parley/ftp` stores a secret (it asks twice and never takes the secret as an
argument); `cred get`, `cred list`, `cred rm`, `cred test` and `cred providers` do the rest. See
`help cred`. While a secret is typed a spinner turns, so you can see the keys land without the length
showing; `FSH_SECRET_MASK='*'` shows a `*` per character instead, and `FSH_SECRET_MASK=` nothing. A
keystore asks for its master password before the secret. Without a terminal (an IDE's console), `cred
set` and `@prompt:` ask in a password dialog; in a script nobody is watching, they fail at once.
A typed `connect` line that holds a plain password is kept out of the history, with a warning.

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
- `tools/bash-suite`, `tools/real-world`, `tools/interactive` — the comparisons with bash
  described above

## The IDE

The script editor and debugger is a separate project,
[fsh-ide](https://github.com/tony-bringardner/fsh-ide). fsh exposes the debugger
hooks it uses (`DebugContext`, `DebugController`).

## License

Apache License 2.0. See [LICENSE](LICENSE).
