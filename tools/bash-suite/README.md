# bash's test suite, run with fsh

`runall.py` runs the test scripts of a bash source tree (`tests/NAME.tests`) with bash and with fsh
and compares what they print, to find where fsh still differs from bash.

```sh
# a bash source tree (not part of this project)
curl -O https://ftp.gnu.org/gnu/bash/bash-5.3.tar.gz && tar xzf bash-5.3.tar.gz
mvn -q compile
python3 tools/bash-suite/runall.py --tests bash-5.3/tests --out /tmp/bash-suite --bash "$(command -v bash)"
```

The bash should be the same version as the sources. fsh runs from `target/classes` (with `macos/` for
the native parts) unless `--fsh` names another command. Names after the options run only those
tests (`arith array`).

In `--out`: `summary.txt` (the tests, most different first), `first.txt` (each test's first
differences, usually what to fix), and for each test `NAME.bash`, `NAME.fsh` and `NAME.diff`.

`helpers/` has the small programs the suite runs (`recho`, `zecho`, `printenv`, `xcase`), written
for this from what bash's `support/` versions print; they are compiled into `--out`/bin.
