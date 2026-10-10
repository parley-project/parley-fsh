# bash-suite differences: todo

What is left between fsh and bash 5.3 in `runall.py`, test by test. Each item is checked off when
it is done, with notes on what happened along the way: what was tried, what turned out to be the
real cause, and anything that went wrong.

Baseline: 59 of 83 tests the same as bash (2026-10-10, master `b07208d`).

Key:
- `[ ]` open: fsh is wrong and should change
- `[x]` done
- **keep**: a deliberate difference (see "Keep" below), so no change
- **bash quirk**: fsh already matches bash's own expected output (`tests/*.right`); the installed
  bash differs from it, so there is nothing to fix

## Open

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
- [x] **rsh**: a restricted shell no longer runs programs from the current directory without
  `./`; it runs only what PATH (or hash) finds, so `a -c 'echo hello'` gives
  `a: command not found`, as in bash.
  - Notes: the test copies /bin/sh to `./a`. fsh found and ran it, and macOS killed the copied
    binary ("Killed: 9", code signing), which hid what the real difference was. The fix is a
    `!restricted` check on fsh's current-directory lookup in `Programs.run`. `hash` already
    refused `/` paths in restricted mode.
- [x] **array** (array32.sub): with `shopt -s array_expand_once`, a subscript is no longer
  expanded a second time, so a `$( )` that came from a variable is not run; it is an
  "operand expected" arithmetic error, as in bash.
  - Notes: there were three separate causes.
    1. fsh kept `array_expand_once` and `assoc_expand_once` as two options. bash 5.3 treats them
       as two names for one option, but fsh's checks only looked at `assoc_expand_once`, and the
       test sets `array_expand_once`. Fixed in `shopt`, which now sets both. That fixed
       `test -v` and `[ -v`.
    2. `let`: `Arithmetic.dequote` expanded a subscript that still had `$` in it (meant for
       `(( $expr ))`); it now skips that when the option is on.
    3. `declare a["$s"]=v`: the quotes inside the subscript stop the parser seeing an
       assignment, so declare gets text, splits off the subscript and parsed it as a word again.
       With the option on, the subscript is now quoted first. That path also never applied
       `-i`; it now does before the element is assigned, so `a` stays `declare -ai` even when
       the element fails, as in bash.
- [x] **array** (array30.sub): `${a[i]=v}` and `${A[k]=v}` now give the element's value as stored,
  so `declare -ai` makes `4+3` into 7 and `declare -Au` makes `foo` into FOO. `-u`, `-l` and `-c`
  now also apply to the element itself.
  - Notes: the todo first described this as two bugs (`a[42]+=` and `declare -Au` printing
    nothing). Reading array30.sub showed both are `${name[sub]=word}`, which echoed the word as
    written. `Expander.assign` now returns what was stored. It also didn't apply the case
    attribute to an element (the assignment code calls `sc.cased`; this path didn't). array.diff
    went from 253 to 221 lines.
- [x] **array** (array33.sub): declare's "cannot convert associative to indexed array" (and the
  reverse) is said as bash says it when a value is given: inside a function by the function
  (`f: A: ...`) and then by `declare`; outside one only `A: ...`. `read -a` into an associative
  array is now an error (`read: A: not an indexed array`, status 1).
  - Notes: fsh already used bash's two-message rule for indexed-to-associative inside a
    function, so I combined both directions into one check. bash's top-level message has no
    `declare:` prefix, which is easy to miss in the diff. array.diff: 221 to 202 lines.
- [x] **builtins** (usage lines): `help -s read*` now lists the options fsh supports, in bash's
  words: `read [-Eers] [-a array] ...`, `readarray`/`mapfile` with `-O -u -C -c`, and
  `readonly [-aAf] ... or readonly -p`. `read -E` now works (it was "invalid option"); fsh's line
  editor is used, as with `-e`. The rest of builtins.diff is fsh's own help text (keep).
  - Notes: before changing any usage line I ran each option under both shells. mapfile's `-O`,
    `-u`, `-C` and `-c` and readonly's `-a`, `-A` and `-f` already worked; only `read -E` was
    missing, though fsh's own error message listed it. builtins.diff: 328 to 320 lines.
