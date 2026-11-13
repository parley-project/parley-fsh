# fsh commands

<!-- Made from the builtins' help text; don't edit it by hand. After changing a help text, run
     mvn test -Dtest=TestCommandsDoc -Dparley.fsh.writeCommandsDoc=true -->

Everything here is also in the shell: `help NAME` shows a command's text, and `help` lists them all.

- [fsh's own commands](#fshs-own-commands): connecting file systems and managing their passwords
- [File commands](#file-commands): fsh's versions of ls, cp, mv and the rest, which work on every
  connected file system
- [Other commands](#other-commands): commands that are separate programs in bash
- [Bash builtins](#bash-builtins): the builtins that work as they do in bash

## fsh's own commands

### connect

```
connect FACTORY [-f FILE] [name=value ...] [-] PATH
```

```text
Connect to a file system at PATH (a path such as /prod), where fsh's file
builtins (ls, cp, mv, rm, find, ...) can use it. FACTORY is the file system's id, such as memory,
sftp, ftp or jdbc (jdbc when fsh is built with it).
Settings, applied in order (a later one replaces an earlier one):
-f FILE, -fFILE, -f=FILE   name=value lines from a properties file
name=value                 one setting
-                          one line of name=value settings read from standard input
A secret setting such as password can name its secret instead of holding it: @env:NAME,
@file:PATH, @keychain:SERVICE/ACCOUNT, @keystore:NAME, @prompt: or @cmd:COMMAND (see cred).
On success it prints "FACTORY connected as PATH"; disconnect undoes it.
With no arguments it lists the shell's connections: PATH -> FILE SYSTEM.
Examples:
connect memory /mem
connect sftp host=example.com user=deploy password=@keychain:parley/sftp/deploy@example.com /prod
connect ftp -f ~/.parley/ftp.properties /ftp
```

### disconnect

```
disconnect PATH
```

```text
Remove the file system connected at PATH, and close its connection (unless
another path uses the same connection). The status is 1 if nothing is connected there.
Example:
disconnect /prod
```

### cred

```
cred set|get|list|rm|test|providers|enable|disable [ARGUMENTS]
```

```text
Manage secrets that connect settings name by reference, such as password=@keychain:parley/ftp
cred set REF            store a secret; asks for it twice, without echo
cred get REF            say whether REF can be resolved (never prints the secret)
cred list [SCHEME]      the references a store holds (where it can list them)
cred rm REF             remove a secret
cred test FACTORY name=value ...  connect with those settings, then disconnect
cred providers          the credential stores and whether each works here
cred enable|disable cmd allow @cmd: references for this session (off by default)
REF is scheme:locator, with or without the leading @: keychain:parley/ftp, keystore:prod-db,
file:~/.parley/db.pw. The secret is never taken as an argument, so it stays out of the history.
At a terminal a spinner turns as you type (the length doesn't show); set FSH_SECRET_MASK=* to see a * per
character, or FSH_SECRET_MASK= (empty) to see nothing.
```

## File commands

These are fsh's own versions, so they work on every connected file system (see `connect`), and
between them: `cp /prod/app.log /mem/` copies a file from one connected file system to another. They take the options listed for
each; any other option is an error. The system's command of the same name still runs by its path
(`/bin/cp`), or after `enable -n cp`, and works only on the local disk.

### ls

```
ls [-1aCdgGhlLQrRStuxX] [--] [PATH ...]
```

```text
List files: the current directory, or each PATH (files first, then each directory's contents).
-a       include names that start with . and hidden files
-1       one name per line
-C, -x   in columns (the default at a terminal)
-l       long format: permissions, links, owner, group, size, date and name
-g       long format without the owner
-G       long format without the group
-h       with -l, sizes such as 1.5K, 234M and 2.0G
-d       list directories themselves, not their contents
-L       show the files symbolic links point to, not the links
-Q       with -l, put names in double quotes
-R       list subdirectories recursively
-r       reverse the order
-S       sort by size, largest first
-t       sort by modification time, newest first
-u       with -l show access times; sort by them
-X       sort by extension
Otherwise names are sorted alphabetically.
```

### cp

```
cp [-Rafinpvx] SOURCE TARGET   or   cp [-Rafinpvx] SOURCE ... DIRECTORY
```

```text
Copy files. With two operands SOURCE is copied to TARGET; when the last operand is an existing
directory, each SOURCE is copied into it. Works between any connected file systems.
-R, -r   copy directories and everything in them; with SOURCE/ (a trailing slash) only what is in it
-a       the same as -Rp
-f       replace existing files without asking (the default)
-i       ask before replacing an existing file
-n       never replace an existing file
-p       keep the files' modification and access times
-v       show each file as it is copied
-x       when copying recursively, skip directories on a different kind of file system
The last of -f, -i and -n given wins. -H, -L and -P are accepted; links are followed.
```

### mv

```
mv [-f | -i | -n] [-v] SOURCE TARGET   or   mv [-f | -i | -n] [-v] SOURCE ... DIRECTORY
```

```text
Move or rename files. Within one file system it renames; between file systems it copies, then
removes the source.
-f   overwrite an existing file without asking
-i   ask before overwriting an existing file
-n   never overwrite an existing file
-v   show each file as it is moved
The last of -f, -i and -n given wins.
```

### rm

```
rm [-f | -i | -I] [-dRrvx] FILE ...
```

```text
Remove files, and directories with -r or -d. It refuses /, . and ..
-d       remove empty directories
-f       ignore files that don't exist, and never ask
-i       ask before removing each file
-I       ask once before removing more than three files, or a directory recursively
-r, -R   remove directories and everything in them
-v       show each file as it is removed
-x       don't go into a connected file system of a different kind
A symbolic link is removed itself, never the file it points to.
```

### mkdir

```
mkdir [-pv] [-m MODE] DIRECTORY ...
```

```text
Create directories.
-m MODE   the new directory's permissions, octal (755) or symbolic (u=rwx,go=rx); local files only
-p        also create missing parent directories; a directory that exists isn't an error
-v        show each directory as it is created
Without -m the shell's umask applies.
```

### touch

```
touch [-acm] [-r FILE | -t TIME | -d DATE | -A OFFSET] FILE ...
```

```text
Set each file's access and modification times to now, or to the time given; a file that doesn't
exist is created, empty, unless -c is given.
-a          change only the access time
-m          change only the modification time
-c          don't create files that don't exist
-r FILE     use FILE's times
-t TIME     [[CC]YY]MMDDhhmm[.SS], local time
-d DATE     YYYY-MM-DD[Thh:mm[:SS[.frac]]], local time; or UTC with a trailing Z, or
another zone with +hh:mm or -hh:mm
-A OFFSET   move the existing times by [-][[hh]mm]SS
```

### ln

```
ln [-s] [-f | -i] [-hnv] [-L | -P] SOURCE TARGET   or   ln [options] SOURCE ... DIRECTORY
```

```text
Make links: hard links, or symbolic links with -s. Works on connected file systems that support
links.
-s       make symbolic links; SOURCE is stored as given and need not exist
-f       replace an existing TARGET
-i       ask before replacing an existing TARGET
-v       show each link as it is made
-h, -n   if TARGET is a symbolic link to a directory, replace it instead of linking inside it
-L       a hard link to a symbolic link links to the file it points to (the default)
-P       a hard link to a symbolic link links to the link itself
```

### find

```
find [-H | -L | -P] PATH ... [EXPRESSION]
```

```text
Search the directory trees at each PATH and print the paths that match EXPRESSION (all of them
when there is none). Works on every connected file system.
Tests:
-name PATTERN, -iname PATTERN       the name matches a shell pattern (-iname ignores case)
-path PATTERN, -wholename PATTERN   the path matches a shell pattern (* also matches /)
-type f|d|l                         a regular file, a directory or a symbolic link
-empty                              an empty file or directory
-mmin N, -amin N                    modified or accessed N minutes ago
-mtime N, -atime N                  modified or accessed N days (24-hour periods) ago
-size N[cwbkMG]                     N bytes, 2-byte words, 512-byte blocks (the default),
KiB, MiB or GiB, rounded up
+N means more than N, -N less than N, and N exactly N.
Operators: ( EXPRESSION ) (quoted, as \( \)); ! or -not; -a or -and (implied); -o or -or.
Options: -maxdepth N, -mindepth N; -L follows symbolic links (-H and -P don't).
Actions:
-print           print the path (the default)
-print0          end each path with a NUL instead of a newline
-exec CMD {} \;  run CMD for each file, with {} replaced by its path
-exec CMD {} +   run CMD once, with all the paths
```

### wc

```
wc [-clmwL] [FILE ...]
```

```text
Count the lines, words and bytes in each FILE, or in standard input when there is none; with
several files a total follows.
-l   lines
-w   words
-c   bytes
-m   characters
-L   the length of the longest line
With no option it counts as -lwc does.
```

## Other commands

In bash these are separate programs; fsh has them built in.

### sleep

```
sleep NUMBER[UNIT] ...
```

```text
Wait for the sum of the intervals. NUMBER may have a fraction (0.5); UNIT is s for seconds (the
default), m minutes, h hours, d days or M milliseconds.
Example:
sleep 1.5m
```

### expr

```
expr EXPRESSION
```

```text
Print the value of EXPRESSION. Operators, from lowest to highest precedence:
ARG1 | ARG2       ARG1 if it is neither empty nor 0, otherwise ARG2
ARG1 & ARG2       ARG1 if neither argument is empty or 0, otherwise 0
ARG1 < <= = == != >= > ARG2   1 or 0 (compared as integers if both are, otherwise as text)
ARG1 + - ARG2     integer sum or difference
ARG1 * / % ARG2   integer product, quotient or remainder
STRING : REGEX    anchored match: the \(..\) group, or the number of characters matched
match STRING REGEX, substr STRING POS LENGTH, index STRING CHARS, length STRING
+ TOKEN           TOKEN as text, even if it is a keyword such as length
( EXPRESSION )
Exit status: 0 if the value is neither empty nor 0, 1 if it is, 2 if EXPRESSION is invalid.
```

### clear

```
clear
```

```text
Clear the terminal screen.
```

## Bash builtins

These work as they do in bash; the [bash manual](https://www.gnu.org/software/bash/manual/bash.html#Shell-Builtin-Commands)
describes them in full, and `help NAME` shows fsh's summary.

| Command | Usage | What it does |
|---|---|---|
| `.` | `. filename [arguments]` | Read and execute commands from filename in the current shell environment and return the exit status of the last command executed from filename. |
| `:` | `: [arguments]` | Do nothing, successfully (the arguments are expanded and ignored). |
| `[` | `[ expression ]` | The same as test, with a ] as its last argument. |
| `alias` | `alias [-p] [name[=value] …]` | Without arguments or with the -p option, alias prints the list of aliases on the standard output in a form that allows them to be reused as input. |
| `base64` | `base64 [-di] [-w COLS] [FILE]` | Encode FILE (standard input with no FILE or -) as base64, or with -d decode it. |
| `basename` | `basename NAME [SUFFIX]` | Print NAME without its leading directories and, if given, without SUFFIX. |
| `bg` | `bg [jobspec ...]` | Resume each suspended job jobspec in the background, as if it had been started with ‘&’. |
| `bind` | `bind [-lpsvPSVX] [-m keymap] [-f filename] [-q name] [-u name] [-r keyseq] [-x keyseq:shell-command] [keyseq:readline-function or readline-command]` | Set what the line editor's keys do, or show it. |
| `break` | `break [n]` | Leave the innermost loop, or the n enclosing loops. |
| `browse` | `browse [-1] [-c] [--with NAME] [DIR [DIR2]]  or  browse --list` | Browse the shell's file tree (connections included) with DIR (default .) on the left and DIR2 (default $HOME) on the right, in fsh's own full-screen terminal browser, or in one a plugin provides (a window when there is a display). |
| `builtin` | `builtin name [args]` | Run the shell builtin name (not a function or program with that name). |
| `caller` | `caller [n]` | In a function, print where it was called from: with n, "line function file" for the call n frames up (0 is the current function's caller); without, "line file". |
| `cat` | `cat [-AbeEnstTuv] [FILE ...]` | Concatenate FILEs to standard output. |
| `cd` | `cd [-L\|-P] [dir]` | Change the current directory (to $HOME without dir). |
| `chmod` | `chmod [-Rv] MODE FILE...` | Change the permissions of each FILE (local files only). |
| `cmp` | `cmp [-ls] [-n LIMIT] FILE1 FILE2` | Compare two files byte by byte (- is standard input) and say where the first difference is. |
| `comm` | `comm [-123i] FILE1 FILE2` | Compare the sorted files FILE1 and FILE2 line by line. |
| `command` | `command [-v\|-V] name [args]` | Run name, skipping functions. |
| `compgen` | `compgen [-V varname] [-abcdefgjksuv] [-o option] [-A action] [-G globpat] [-W wordlist] [-F function] [-C command] [-X filterpat] [-P prefix] [-S suffix] [word]` | The completions of word, one a line, as complete's options say (see complete). |
| `complete` | `complete [-abcdefgjksuv] [-pr] [-DEI] [-o option] [-A action] [-G globpat] [-W wordlist] [-F function] [-C command] [-X filterpat] [-P prefix] [-S suffix] [name ...]` | How Tab completes each name's arguments. |
| `compopt` | `compopt [-o\|+o option] [-DEI] [name ...]` | Turn completion options of each name on (-o) or off (+o); with no option, show them. |
| `continue` | `continue [n]` | Go on with the next pass of the innermost loop, or of the nth enclosing loop. |
| `curl` | `curl [OPTIONS] URL...` | Transfer the URL (http, https or file) to standard output. |
| `cut` | `cut OPTION... [FILE ...]` | Print the selected parts of each line of each FILE (standard input with no FILE or -). |
| `date` | `date [-u] [-d STRING] [-r FILE] [-I[FMT]] [-R] [+FORMAT]` | Print the current date and time in FORMAT (default: like Fri Nov 13 16:43:21 PST 2026). |
| `declare` | `declare [-aAfFgiIlnrtux] [name[=value] ...] or declare -p [-aAfFilnrtux] [name ...]` | Set variables and their attributes, or show them (-p). |
| `df` | `df [-hkT] [FILE ...]` | Show the space on the file system of each FILE (every local file system with no FILE). |
| `diff` | `diff [OPTION]... FILE1 FILE2` | Compare two files line by line, or the files of two directories. |
| `dirname` | `dirname [-z] NAME...` | Print NAME without its last component: the directory it is in (. |
| `dirs` | `dirs [-clpv] [+N] [-N]` | Print the directory stack, the current directory first: -l without ~, -p one per line, -v numbered, +N or -N one entry (counted from the left or right); -c clears it. |
| `disown` | `disown [-ar] [-h] [id ...]` | Without options, remove each id from the table of active jobs. |
| `du` | `du [-abcdhksx] [FILE ...]` | Show the space used by each FILE and, for a directory, by each directory under it. |
| `echo` | `echo [-neE] [string ...]` | Write arguments to the standard output |
| `edit` | `edit FILE` | Edit FILE with $VISUAL, else $EDITOR, else vi. |
| `enable` | `enable [-a] [-dnps] [-f filename] [name ...]` | Turn on the builtins named, or turn them off with -n (the name then runs a program of that name). |
| `env` | `env [-0] [-u NAME] [NAME=VALUE]... [COMMAND [ARG]...]` | With no COMMAND print the environment, one NAME=VALUE a line (with NAME=VALUE operands added to it). |
| `eval` | `eval [arguments]` | The arguments are concatenated together into a single command, separated by spaces. |
| `exec` | `exec [-cl] [-a name] [command [arguments]]` | If command is supplied, it replaces the shell without creating a new process. |
| `exit` | `exit [n]` | End the shell, with status n (the last command's status without n). |
| `export` | `export [-fn] [-p] [name[=value] ...]` | Mark each name to be passed to child processes in the environment. |
| `false` | `false [arguments]` | Do nothing, unsuccessfully. |
| `fc` | `fc [-e ename] [-lnr] [first] [last] or fc -s [pat=rep] [command]` | List (-l, -n without numbers, -r in reverse) or edit and run again the commands from first to last of the history (a number, a negative offset, or the start of a command). |
| `fg` | `fg [jobspec]` | Resume the job jobspec in the foreground and make it the current job. |
| `file` | `file [-bi] FILE...` | Guess what kind of data each FILE holds, from its first bytes. |
| `fold` | `fold [-bs] [-w WIDTH] [FILE ...]` | Wrap the lines of each FILE (standard input with no FILE or -) to WIDTH columns (default 80). |
| `getopts` | `getopts optstring name [arg ...]` | Read the next option from the positional parameters (or the args) into name, its argument (for a letter followed by : in optstring) into OPTARG, and the index of the next parameter into OPTIND. |
| `grep` | `grep [OPTION]... PATTERNS [FILE ...]` | Print the lines of each FILE (or standard input) that match PATTERNS. |
| `gunzip` | `gunzip [-cfkv] [FILE ...]` | Decompress each FILE.gz to FILE and remove FILE.gz (same as gzip -d; options as gzip). |
| `gzip` | `gzip [-cdfkv] [-1..-9] [FILE ...]` | Compress each FILE to FILE.gz and remove FILE (standard input to standard output with no FILE or -). |
| `hash` | `hash [-lr] [-p pathname] [-dt] [name ...]` | Remember or display program locations. |
| `head` | `head [-qv] [-n [-]NUM \| -c [-]NUM] [FILE ...]` | Print the first 10 lines of each FILE (standard input with no FILE or -). |
| `help` | `help [-dms] [pattern ...]` | Show what the builtins matching pattern (a glob, or the start of a name) do; with no pattern, list them. |
| `hexdump` | `hexdump [-Cv] [-n LENGTH] [-s OFFSET] [FILE ...]` | Show the bytes of each FILE (standard input with no FILE or -) in hex. |
| `history` | `history [n]` | With no options, display the history list with line numbers. |
| `hostname` | `hostname [-sfi]` | Print the name of this computer. |
| `jobs` | `jobs [-rs] [jobspec]` | The first form lists the active jobs. |
| `join` | `join [OPTION]... FILE1 FILE2` | For each pair of lines with identical join fields, write a line with the join field, the rest of the line of FILE1 and the rest of the line of FILE2. |
| `kill` | `kill [-s sigspec] [-n signum] [-sigspec] id [...]` | Send a signal specified by sigspec or signum to the processes named by each id. |
| `let` | `let arg [arg ...]` | Each arg is an arithmetic expression to be evaluated, as in $((arg)); name=expression assigns the value to name. |
| `local` | `local [option] name[=value] ...` | Make each name a variable of the function it is used in (and of the functions it calls), with declare's options. |
| `logout` | `logout [n]` | Exit a login shell with status n (the last command's if n is not given). |
| `mapfile` | `mapfile [-d delim] [-n count] [-O origin] [-s count] [-t] [-u fd] [-C callback] [-c quantum] [array]` | Read lines from standard input (or fd with -u) into the indexed array (MAPFILE if none is named). |
| `md5sum` | `md5sum [-bt] [--quiet] [--status] [-c] [FILE ...]` | Print the MD5 checksum of each FILE (standard input with no FILE or -). |
| `nl` | `nl [OPTION]... [FILE ...]` | Write each FILE (standard input with no FILE or -) with line numbers added. |
| `od` | `od [OPTION]... [FILE ...]` | Dump the bytes of each FILE (standard input with no FILE or -), 16 to a line, in octal 2 byte words. |
| `paste` | `paste [-s] [-d LIST] [FILE ...]` | Write the corresponding lines of the FILEs, separated by tabs (- is standard input). |
| `plugins` | `plugins` | List fsh's plugin folders, in the order they are searched, and each jar in them: loaded (a plugin or a library), skipped and why, or shadowed by a jar of the same name found first. |
| `popd` | `popd [-n] [+N \| -N]` | Take the top directory off the stack and go to the new top, or take off entry N (counted from the left or right of dirs' list). |
| `printenv` | `printenv [-0] [NAME...]` | Print the value of each environment variable NAME, or with none the whole environment. |
| `printf` | `printf [-v var] format [arguments]` | Write the arguments under control of format: %s %b (with backslash escapes) %q (quoted) %c %d %i %u %o %x %X %f %e %E %g %G %%, with flags - + space 0 #, a width and a precision (* takes them from the arguments). |
| `pushd` | `pushd [-n] [+N \| -N \| dir]` | Add dir to the top of the directory stack (going there), or rotate the stack (+N, -N, counted from the left or right of dirs' list) so that entry is on top. |
| `pwd` | `pwd [-LP]` | Print the current directory. |
| `read` | `read [-Eers] [-a array] [-d delim] [-i text] [-n nchars] [-N nchars] [-p prompt] [-t timeout] [-u fd] [name ...]` | One line is read from the standard input, or from the file descriptor fd supplied as an argument to the -u option, split into words as described under Word Splitting, and the first word is assigned to the first name, the	second word to the second name, and so on. |
| `readarray` | `readarray [-d delim] [-n count] [-O origin] [-s count] [-t] [-u fd] [-C callback] [-c quantum] [array]` | Read lines from standard input (or fd with -u) into the indexed array (MAPFILE if none is named). |
| `readlink` | `readlink [-fenmqs] FILE...` | Print where the symbolic link FILE points to (nothing and status 1 if it isn't a link). |
| `readonly` | `readonly [-aAf] [name[=value] ...] or readonly -p` | Make each name readonly, after setting it to value if one is given (-a an indexed array, -A an associative one, -f functions). |
| `realpath` | `realpath [-emqs] FILE...` | Print the absolute path of each FILE with . |
| `return` | `return [n]` | End a function or a sourced file, with status n (the last command's status without n). |
| `rev` | `rev [FILE ...]` | Copy each FILE (standard input with no FILE or -) reversing the characters of every line. |
| `search` | `search [-i] [-a] [-l] [-n NAME] [-t TEXT] [DIR ...]` | List the files below each DIR (default .) whose name matches the glob NAME and that contain TEXT; with neither, every file and directory. |
| `sed` | `sed [OPTION]... {SCRIPT-ONLY-IF-NO-OTHER-SCRIPT} [FILE ...]` | Edit a stream: for each line of FILE (or standard input) run the SCRIPT and print the result. |
| `seq` | `seq [OPTION]... [FIRST [INCREMENT]] LAST` | Print the numbers from FIRST (1) to LAST in steps of INCREMENT (1). |
| `set` | `set [-abCefhmnuvx] [argument...]` | If no options or arguments are supplied, set displays the names and values of all shell variables and functions, sorted according to the current locale, in a format that may be reused as input for setting or resetting the currently-set variables. |
| `sha1sum` | `sha1sum [-bt] [--quiet] [--status] [-c] [FILE ...]` | Print the SHA-1 checksum of each FILE (standard input with no FILE or -). |
| `sha256sum` | `sha256sum [-bt] [--quiet] [--status] [-c] [FILE ...]` | Print the SHA-256 checksum of each FILE (standard input with no FILE or -). |
| `sha512sum` | `sha512sum [-bt] [--quiet] [--status] [-c] [FILE ...]` | Print the SHA-512 checksum of each FILE (standard input with no FILE or -). |
| `shift` | `shift [n]` | The positional parameters shall be shifted. |
| `shopt` | `shopt [-s\|-u] [-pq] [optname ...]` | Set (-s) or unset (-u) shell options, or show them (-p as shopt commands). |
| `sort` | `sort [OPTION]... [FILE ...]` | Write the sorted lines of all the FILEs (standard input with no FILE or -). |
| `source` | `source filename [arguments]` | Read and execute commands from filename in the current shell environment and return the exit status of the last command executed from filename. |
| `split` | `split [OPTION]... [FILE [PREFIX]]` | Write the pieces of FILE (standard input with no FILE or -) to PREFIXaa, PREFIXab, ... |
| `stat` | `stat [-L] [-c FORMAT] FILE...` | Show the status of each FILE. |
| `suspend` | `suspend [-f]` | Suspend the shell until it receives a SIGCONT. |
| `tac` | `tac [FILE ...]` | Print each FILE (standard input with no FILE or -) with its lines in reverse order. |
| `tail` | `tail [-fqv] [-n [+]NUM \| -c [+]NUM] [FILE ...]` | Print the last 10 lines of each FILE (standard input with no FILE or -). |
| `tar` | `tar -c\|-x\|-t [-zvO] [-f ARCHIVE] [-C DIR] [--strip-components=N] [FILE ...]` | Make (-c), extract (-x) or list (-t) a tar archive. |
| `tee` | `tee [-ai] [FILE ...]` | Copy standard input to standard output and to each FILE. |
| `test` | `test [expr]` | Exit with status 0 (true) or 1 (false) depending on expr; 2 if expr is invalid. |
| `times` | `times` | Print the user and system time used by the shell, then by the commands it ran. |
| `tr` | `tr [-cdst] SET1 [SET2]` | Translate, squeeze or delete the characters of standard input. |
| `trap` | `trap [-lpP] [action] [sigspec ...]` | The action is a command that is read and executed when the shell receives any of the signals sigspec. |
| `true` | `true [arguments]` | Do nothing, successfully. |
| `truncate` | `truncate -s SIZE [-c] FILE...` | Shrink or extend (with zeros) each FILE to SIZE bytes; a file that doesn't exist is created. |
| `type` | `type [-afptP] name [name ...]` | Say how each name would be run: alias, keyword, function, builtin or file. |
| `typeset` | `typeset [-aAfFgiIlnrtux] [name[=value] ...] or typeset -p [-aAfFilnrtux] [name ...]` | Set variables and their attributes, or show them (-p). |
| `ulimit` | `ulimit [-SHa] [-cdflmnstuv] [limit]` | Print a resource limit (-a: all of them), or set it: -S the soft limit, -H the hard one (both without either). |
| `umask` | `umask [-p] [-S] [mode]` | Print the file creation mask (-S: as u=rwx,g=rx,o=rx), or set it to mode (octal or symbolic). |
| `unalias` | `unalias [-a] [name …]` | Remove one or more aliases or all with -a option. |
| `uname` | `uname [-asnrvmpo]` | Print facts about the system (the kernel name with no option). |
| `uniq` | `uniq [OPTION]... [INPUT [OUTPUT]]` | Filter the adjacent matching lines of INPUT (standard input with no INPUT or -) to OUTPUT (standard output). |
| `unset` | `unset [-f] [-v] [name ...]` | Remove each variable or function name. |
| `unzip` | `unzip [-lonpq] [-d DIR] ZIPFILE [FILE ...]` | Extract the files of the archive ZIPFILE (the ones that match the FILEs, if any are given). |
| `view` | `view [-c] [-x] FILE ...` | Show each FILE, on any connected file system, in a full-screen viewer (Esc or q to close). |
| `wait` | `wait [-fn] [-p varname] [id ...]` | Wait until the child process specified by each id exits and return the exit status of the last id. |
| `wget` | `wget [OPTION]... URL...` | Download each URL to a file named like the end of the URL (index.html for a directory). |
| `which` | `which [-a] COMMAND...` | Print the full path of each COMMAND, the file that would run: the first in the directories of the PATH (on Windows with the extensions of PATHEXT). |
| `whoami` | `whoami` | Print the name of the user who is running the shell. |
| `xargs` | `xargs [OPTION]... [COMMAND [INITIAL-ARGS]...]` | Run COMMAND (echo by default) with the items read from standard input added as arguments. |
| `xxd` | `xxd [-pru] [-c COLS] [-g BYTES] [-l LEN] [-s SEEK] [FILE]` | Make a hex dump of FILE (standard input with no FILE or -). |
| `yes` | `yes [STRING ...]` | Print STRING (the arguments joined by spaces; y if there are none) on a line, over and over, until the command is stopped or what reads its output (head, for one) is done. |
| `zcat` | `zcat [FILE ...]` | Decompress each FILE.gz to standard output (same as gzip -dc). |
| `zip` | `zip [-rjq] [-0..9] ZIPFILE FILE...` | Add the FILEs to the archive ZIPFILE (.zip is added to the name if it has no extension), creating it if it doesn't exist. |
