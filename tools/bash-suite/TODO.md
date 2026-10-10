# bash-suite differences: todo

What is left between fsh and bash 5.3 in `runall.py`, test by test. Each item is checked off when
it is done, with notes on what happened along the way: what was tried, what turned out to be the
real cause, and anything that went wrong.

Baseline: 59 of 83 tests the same as bash (2026-10-10, master `b07208d`).

Key:
- `[ ]` open: fsh is wrong and should change
- `[x]` done
- **keep**: a deliberate difference (see "Deliberate differences" at the end), so no change
- **bash quirk**: fsh already matches bash's own expected output (`tests/*.right`); the installed
  bash differs from it, so there is nothing to fix

## Open

- [ ] **rsh**: in a restricted shell (`set -r`), `a -c 'echo hello'` runs `./a` because fsh runs
  programs in the current directory without `./`. bash says `a: command not found`. Restricted
  mode must turn that lookup off. (macOS then killed the copy of /bin/sh, which is why the diff
  shows "Killed: 9".)
- [ ] **array** (array32.sub, lines 57-68): a `$( )` inside an array subscript runs
  (`INJECTION!` is printed) in places where bash refuses it with "arithmetic syntax error:
  operand expected". This is a safety issue, not only a difference.
- [ ] **array**: in `declare -ai a; a[42]+=…`, `echo` shows `4+3` where bash shows `7`. The
  integer attribute isn't applied on that path.
- [ ] **array**: `declare -Au A` (associative, uppercase) prints nothing; bash prints `FOO` and
  `declare -Au A=([Darwin]="FOO" )`.
- [ ] **builtins** (usage lines): fsh's `help -s` lines leave out options it does support:
  - `readarray`/`mapfile` support `-O`, `-u`, `-C` and `-c`.
  - `read`: check `-E`.
  - `readonly`: check `-a`, `-A` and `-f`.

  The help texts are fsh's own, but they should list what works.
- [ ] **read** (read7.sub): `read -t 0.00001 -e var` with stdin on /dev/null returns 1 (EOF);
  bash returns >128 (the timeout fires before the read). It depends on timing, so it may not be
  worth matching. Decide.
- [ ] **array** (line 418): `echo "${letters["{2..6}"]}"`. bash brace-expands the `{2..6}` that sits
  between the inner quotes (its brace scan treats `"${letters["` and `"]}"` as two quoted strings)
  and prints `2 3 4 5 6`. fsh treats it as one subscript. This is an obscure lexical quirk; low
  priority.
- [ ] **exp** (exp8.sub): the error token for the key `x\001y\177z` is `"yz"` in fsh and `"z"` in
  bash. This comes from bash's internal CTLESC quoting; low priority.

## Keep (deliberate differences, no change)

- **assoc, appendop, casemod, varenv, quotearray, new-exp (part), array (part)**: associative-array
  keys are listed sorted; bash uses its hash order.
- **intl, glob (part)**: fsh is always UTF-8, so it has no byte-oriented locales such as
  fr_FR.ISO8859-1.
- **builtins (most), invocation, complete**: these use fsh's own help, usage and long-option
  texts, and its own builtins (ls, cp, mv, find, wc…), which also appear in completion lists.
- **type**: `mv` is an fsh builtin, and aliases expand in scripts.
- **comsub2**: aliases expand in scripts (`p` runs printf where bash says "p: command not
  found").
- **heredoc** (heredoc7.sub): fsh's line numbers after a multi-line `$( )`.
- **parser, arith-for, history (`5.3`), new-exp (`\v`, `\s` prompts)**: these depend on the name
  `bash` or on `BASH_VERSION`, which fsh does not set.

## Bash quirk (fsh already matches bash's expected output)

- **tilde, tilde2, more-exp, new-exp (`/homes/chet`), array (`HOME=/scratch/bash`,
  `/homes/cj`)**: after `HOME` is assigned, the installed bash still expands `~` to the old home
  (the test directory, shown as `$TMP`). bash's `.right` files have the new value, which is what
  fsh prints.

## Done

- [x] **history**: "cannot set terminal process group (N)" now shows the shell's process group
  (`getpgrp`), as bash does, instead of fsh's pid. The rest of history.diff is identity
  (`bash:` vs `fsh:`, `5.3`), so keep.
  - Notes: there was no native for the process group, so I added `NativeKeyboard.pgrp0()`
    (falling back to the pid with an older library). The first rebuild ran the GraalVM image
    script (`native/rebuild.sh`), which does not remake the JNI dylib; `nm` showed no `pgrp0`
    until I ran `macos/compile.sh`. After that, `history` shows the same number as bash (80051
    on both).
