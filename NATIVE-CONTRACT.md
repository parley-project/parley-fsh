# The native contract of `NativeKeyboard`

`us.bringardner.fsh.NativeKeyboard` has 32 `native` methods. This is what the application
relies on from each of them, taken from how the Java side calls them. **Every implementation must
honor it**, whichever it is:

| Implementation | Where |
|---|---|
| JNI, macOS | `macos/NativeKeyboard.cpp` (the reference) |
| JNI, Linux | `linux/NativeKeyboard.cpp` |
| JNI, Windows | `windows/NativeKeyboard.cpp` |
| JNA, macOS / Linux | `src/main/java/us/bringardner/fsh/jna/UnixOps.java` |
| JNA, Windows | `src/main/java/us/bringardner/fsh/jna/WindowsOps.java` |

`NativeKeyboardContractTest` checks the parts that can be checked without a terminal, against
whichever implementation is loaded (`-Dparley.fsh.native=jna` or `jni`, see below).

## Conventions

- **Errors.** A function that can fail for a reason the caller shows returns `-errno` (`spawn0`,
  `open0`, `kill0`, `setOpenFilesLimit0`, `sendWithDescriptors0`). The others return a value
  that says "none" (`null`, `0`, `-1`) and never throw.
- **"Not provided".** A platform with no such thing either gives the *not known* answer the
  contract lists for the function, or does not provide the function at all, in which case it
  throws `UnsatisfiedLinkError` and **every caller catches that** (or is behind the capability
  that implies it). Nothing else may happen: no crash, no wrong value.
- **Threads.** `getChar`, `ready` and the terminal-mode functions are called from one reader at a
  time (under `keyLock`); the implementation keeps its own buffer between calls.
- **Descriptors** are the process's small integers. Only 0–2 mean the same everywhere.

## Capabilities

The application asks the platform for three things. Each function belongs to one, and the
capability is what the caller checks, not the function.

| Capability | Check | Required of |
|---|---|---|
| **A. Keyboard and terminal** | `NativeKeyboard.isAvailible()` | every platform, every function below |
| **B. Process information** | `UnsatisfiedLinkError` is caught | provide it, or say "not known" |
| **C. Starting programs** | `NativeProcess.usable()` (`NativeKeyboard.canSpawn()`) | POSIX only: all ten or none |

`canSpawn()` is true only on a Unix (`File.separatorChar == '/'`) and only when one implementation
has **all** of C: JNA (`canSpawn()` on its layer), or else the JNI library with `strerror0` (the
probe that tells a library from before C was added). It never depends on a function that happens to
be present. Callers use `NativeKeyboard.spawn`, `pipe`, `open`, `close`, `waitExit`, `kill`,
`strerror`, `socketPair`, `sendWithDescriptors`, `inputStream`, `outputStream`, which go to one
implementation or the other, never a mix.

## A. Keyboard and terminal (every platform)

| Function | Contract |
|---|---|
| `int getChar()` | The next byte `0..255`; a special key `500..508` (UP DN RT LF HOME PAGE_UP PAGE_DOWN DELETE END); `-2` (`KEY_NONE`) if nothing was typed within about a second or the wait was interrupted; `-1` (`KEY_EOF`) at the end of input or if it cannot be read. Raw mode (no echo, no line buffering) is entered on first use and undone when the process ends. An escape sequence is a special key only when it is **all** of what one read returned (`ESC [ A..D H F`, `ESC [ 3 5 6 ~`); otherwise its bytes come one at a time. Bytes read together are returned one by one, before anything is read again. |
| `int ready()` | The bytes that can be read without blocking, `>= 0` (the buffered ones, then the system's). Callers only test `> 0`. |
| `void setSignalKeys(boolean on)` | `on`: Ctrl-C, Ctrl-Z, Ctrl-\ are read as keys `3`, `26`, `28` and do not signal. Takes effect at once and does not drop what was typed. |
| `void setProgramMode(boolean on)` | `on`: the terminal is as it was when the process started (a program reads it itself; Ctrl-C signals it); whether the shell had raw mode is remembered. `off`: the shell has the terminal back, in raw mode again if it was. |
| `boolean isTerminal()` | Standard input **and** output are terminals. |
| `boolean isInputTerminal0()` | Standard input is a terminal. |
| `String ttyName0()` | The device of standard input (`/dev/ttys003`, `/dev/pts/0`, `CON`), or `null`. |
| `int inputKind0()` | Standard input is `1` a pipe or socket, `2` a regular file, `0` anything else (a terminal, `/dev/null`). |
| `String[] openDescriptors0()` | Descriptors `3..255` open **without** close-on-exec, each `"fd mode path"`: `mode` is the access mode (`0` read, `1` write, `2` both), `path` empty if not known. `[]` where a program gets none. Never `null`. |
| `long[] resourceUsage0()` | Four values in microseconds, `>= 0`: `{this process user, system, ended children user, system}`. A platform that keeps no total for children gives `0` there. |
| `int columns0()` | The width of standard output's terminal, or `0` (not a terminal, or not known). |
| `boolean isatty0(int fd)` | Descriptor `fd` is a terminal. `false` for a descriptor that is not open. |

## B. Process information (provide, or "not known")

| Function | Contract | If there is none |
|---|---|---|
| `void giveTerminalTo(long group)` | Process group `group` becomes the terminal's foreground group (this may be called from a background shell: no `SIGTTOU` stop). | no-op |
| `void setProcessGroup(long pid, long group)` | `setpgid`; the child does it too, either may be first; errors ignored. | no-op |
| `int stoppedBy(long pid)` | The signal that stopped child `pid` since the last call, else `0`. Only a *stop* is reported: an exit is left for `waitExit0`. `-1` must be accepted and give `0` (it is the capability probe). | `0` |
| `void ttyTo0(int group)` | The terminal (`/dev/tty`) becomes process group `group`'s (`0`: the shell's). No effect without a terminal. | no-op |
| `long pgrp0()` | This process's process group. | the process id |
| `long[] ids0()` | `{uid, gid, supplementary groups...}`, at least two elements. | `null` |
| `int startedWith0(String name)` | `1` if `name` was in the environment the process was **started** with, `0` if not, `-1` if that cannot be told. | `-1` |
| `String argv0()` | This process's `argv[0]` as it was started (not its path), or `null`. | `null` |
| `String osRelease0()` | The kernel's release (`uname -r`, `25.6.0`), or `null`. | `null` |
| `int setOpenFilesLimit0(long limit)` | Sets the soft open-files limit (`-1`: unlimited). `0`, or `-errno`. | `-ENOSYS` |

## C. Starting programs (POSIX; all or none)

Used only when `NativeProcess.usable()`. They share the meaning of descriptors, process groups
and signals, so a platform has all ten or none.

| Function | Contract |
|---|---|
| `int spawn0(byte[] path, byte[][] argv, byte[][] envp, byte[] dir, int[] fds, int pgroup, boolean suspended)` | Start `path` with `argv`, `envp` (raw bytes), in `dir` (`null`: here). `fds` is pairs `{program's fd, shell's fd}`: the program's descriptor `n` is the shell's, **all other descriptors are closed** in it. No signals blocked; those the shell ignores stay ignored. `pgroup`: `-1` the shell's group (nothing changed), `0` a group of its own, `>0` join that group (`-EPERM`/`-ESRCH` if it cannot). `suspended`: **the program does not run until it holds the terminal as its group's** (macOS: started stopped, the shell sends `SIGCONT` after `ttyTo0`; Linux, JNI: it takes the terminal itself before `exec`, so that `SIGCONT` is harmless; Linux, JNA: started through `fshexec -g PGID 1`, which does the same, and without `fshexec` it starts at once and is stopped by `SIGTTIN` if it reads the terminal before the shell hands it over, until that same `SIGCONT`). Returns the process id, or `-errno` and no child remains. |
| `int[] pipe0()` | `{read, write}`, both close-on-exec; `null` on failure. |
| `int open0(byte[] path, int how)` | `'r'` read, `'w'` write (create, truncate), `'a'` append (create); close-on-exec, mode `0666`. The descriptor, or `-errno`. |
| `void close0(int fd)` | Closes it; errors ignored. |
| `int waitExit0(int pid)` | Blocks until child `pid` ends and returns its **wait status** in the Unix encoding: `exit<<8`, or the signal number (`| 0x80` if it dumped core). `-1` if it is not a child. Restarts after a signal. Does not report stops. |
| `FileDescriptor fd0(int fd)` | A `FileDescriptor` that owns `fd` (closing a stream on it closes `fd`). JNA has no such thing (a `FileDescriptor`'s `fd` field is private): callers use `NativeKeyboard.inputStream(fd)` and `outputStream(fd)`, which JNA gives as streams that read and write the descriptor directly, and the JNI library as `FileInputStream`/`FileOutputStream` on `fd0`. Closing the stream closes `fd`; `EPIPE` is an `IOException("Broken pipe")`. |
| `int kill0(int pid, int sig)` | `kill(2)`: `0` or `-errno`. `sig 0` only tests that it exists. Negative `pid` is a process group. |
| `String strerror0(int err)` | The system's text for `errno` `err`. |
| `int[] socketPair0()` | A connected `AF_UNIX` stream pair, both close-on-exec; `null` on failure. A write to a closed peer gives `EPIPE`, not a `SIGPIPE`. |
| `int sendWithDescriptors0(int sock, byte[] payload, int[] fds)` | Sends a 4-byte big-endian length, then `payload`, with `fds` (at most 16) as `SCM_RIGHTS` on the first send; waits for all of it. `0`, or `-errno` (`-EINVAL` for more than 16, `-EPIPE` for a closed peer). Never `SIGPIPE`. |

## What each implementation provides

| | JNI macOS | JNI Linux | JNI Windows | JNA macOS / Linux | JNA Windows |
|---|---|---|---|---|---|
| A. keyboard and terminal (12) | all | all | all | all | all |
| B. process information (10) | all | all | all (answers per the "none" column) | all | all (same answers) |
| C. starting programs (10) | all | all | none (by design) | all, if the C library has `posix_spawn_file_actions_addchdir_np` (glibc 2.29, macOS 10.15, musl 1.2.?) | none (by design) |

C is **all or none per implementation**. Where it is none, `canSpawn()` is false and
`ProcessBuilder` starts programs. What cannot be done through either JNI or JNA, and stays C:
`fshexec` (it runs between `fork` and `exec`, and as a stand-in process) and the native image's
launcher (`fsh-native.c`, before any runtime exists).

## Choosing an implementation

`-Dparley.fsh.native=` is `auto` (the default: the JNI library when it is loaded, else JNA, never JNA in
a native image), `jni`, or `jna`.
