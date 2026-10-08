#!/usr/bin/env python3
"""
Run bash's own test suite (the NAME.tests scripts in a bash source tree's tests/ directory) with
bash and with fsh, and rank how differently fsh does.

    python3 runall.py --tests BASH_SRC/tests --out DIR [--bash /path/to/bash] [--fsh CMD] [NAME ...]

--fsh is the command that runs fsh (default: macos/fsh-run next to this project, made from
target/classes). Each test runs in its own copy of the tests directory, with the helper programs
the suite needs (recho, zecho, printenv, xcase, from helpers/, compiled here) first in PATH.

Written for each test in DIR: NAME.bash, NAME.fsh (the outputs), NAME.diff; and summary.txt
(most different first) and first.txt (each test's first differences, usually the cause).
The bash sources are not part of this project: get them from https://ftp.gnu.org/gnu/bash/.
"""
import argparse
import concurrent.futures
import difflib
import os
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(os.path.dirname(HERE))


def build_helpers(out):
    """compile helpers/*.c into out/bin"""
    bindir = os.path.join(out, 'bin')
    os.makedirs(bindir, exist_ok=True)
    for name in ('recho', 'zecho', 'printenv', 'xcase'):
        target = os.path.join(bindir, name)
        source = os.path.join(HERE, 'helpers', name+'.c')
        if not os.path.exists(target) or os.path.getmtime(target) < os.path.getmtime(source):
            subprocess.run(['cc', '-O1', '-o', target, source], check=True)
    return bindir


def fsh_command(out):
    """a script that runs fsh from this project's target/classes (and macos/ for the native parts)"""
    cp_file = os.path.join(PROJECT, 'target', 'runtime-classpath.txt')
    if not os.path.exists(cp_file):
        subprocess.run(['mvn', '-q', 'dependency:build-classpath', '-Dmdep.includeScope=runtime',
                        '-Dmdep.outputFile='+cp_file], cwd=PROJECT, check=True)
    cp = os.path.join(PROJECT, 'target', 'classes')+':'+open(cp_file).read().strip()
    script = os.path.join(out, 'bin', 'fsh')
    with open(script, 'w') as f:
        f.write('#!/bin/sh\nexec java -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED '
                '-Djava.library.path=%s -cp "%s" us.bringardner.fsh.Console "$@"\n' % (os.path.join(PROJECT, 'macos'), cp))
    os.chmod(script, 0o755)
    return script


def run(args, shells, bindir, name, which):
    work = os.path.join(args.out, 'work', which, name)
    shutil.rmtree(work, ignore_errors=True)
    shutil.copytree(args.tests, work)
    tmp = os.path.join(args.out, 'tmp', which, name)
    os.makedirs(tmp, exist_ok=True)
    sh = shells[which]
    env = {'PATH': bindir+':.:/usr/bin:/bin:/usr/sbin:/sbin', 'THIS_SH': sh, 'TMPDIR': tmp, 'HOME': tmp,
           'LANG': 'en_US.UTF-8', 'BASH_TSTOUT': os.path.join(tmp, 'tstout'), 'BUILD_DIR': work}
    start = time.time()
    try:
        r = subprocess.run([sh, './'+name+'.tests'], cwd=work, env=env, stdin=subprocess.DEVNULL,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=args.timeout)
        text = r.stdout.decode('utf-8', 'replace')
    except subprocess.TimeoutExpired as e:
        text = (e.stdout or b'').decode('utf-8', 'replace') + '\n<<TIMEOUT>>\n'
    # the JVM's own warnings, and the paths that differ from run to run
    text = '\n'.join(l for l in text.split('\n') if 'WARNING' not in l and 'restricted method' not in l.lower())
    text = text.replace(sh, '$THIS_SH').replace(work, '$WORK').replace(tmp, '$TMP')
    with open(os.path.join(args.out, name+'.'+which), 'w') as f:
        f.write(text)
    shutil.rmtree(work, ignore_errors=True)
    return text, time.time()-start


def compare(args, shells, bindir, name):
    b, _ = run(args, shells, bindir, name, 'bash')
    f, seconds = run(args, shells, bindir, name, 'fsh')
    bl, fl = b.split('\n'), f.split('\n')
    unified = list(difflib.unified_diff(bl, fl, 'bash', 'fsh', lineterm=''))
    with open(os.path.join(args.out, name+'.diff'), 'w') as out:
        out.write('\n'.join(unified))
    changed = [l for l in unified[2:] if l[:1] in '+-']
    return name, len(bl), len(changed), '<<TIMEOUT>>' in f, round(seconds, 1), unified


def first_differences(unified, hunks=2, lines=6):
    ret, cur = [], None
    found = []
    for l in unified[2:]:
        if l.startswith('@@'):
            cur = []
            found.append(cur)
        elif cur is not None and l[:1] in '+-':
            cur.append(l)
    for h in found[:hunks]:
        ret.extend('    '+l[:160] for l in h[:lines])
        ret.append('    ..')
    return ret


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    p.add_argument('--tests', required=True, help="a bash source tree's tests directory")
    p.add_argument('--out', required=True, help='where the results go')
    p.add_argument('--bash', default=shutil.which('bash') or '/bin/bash')
    p.add_argument('--fsh', help='the command that runs fsh')
    p.add_argument('--timeout', type=int, default=240)
    p.add_argument('--jobs', type=int, default=6)
    p.add_argument('names', nargs='*')
    args = p.parse_args()
    args.tests = os.path.abspath(args.tests)
    args.out = os.path.abspath(args.out)
    os.makedirs(args.out, exist_ok=True)
    bindir = build_helpers(args.out)
    shells = {'bash': os.path.abspath(args.bash), 'fsh': os.path.abspath(args.fsh) if args.fsh else fsh_command(args.out)}
    names = args.names or sorted(f[:-6] for f in os.listdir(args.tests) if f.endswith('.tests'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as ex:
        results = list(ex.map(lambda n: compare(args, shells, bindir, n), names))
    results.sort(key=lambda r: -r[2])
    same = sum(1 for r in results if r[2] == 0)
    with open(os.path.join(args.out, 'summary.txt'), 'w') as s:
        for name, lines, changed, timeout, seconds, _ in results:
            s.write(f'{name:24} bash-lines={lines:5} diff-lines={changed:6} {"TIMEOUT" if timeout else "       "} fsh={seconds}s\n')
        s.write(f'\n{same} of {len(results)} the same as bash\n')
    with open(os.path.join(args.out, 'first.txt'), 'w') as f:
        for name, _, changed, _, _, unified in sorted(results):
            if changed:
                f.write('===== '+name+'\n'+'\n'.join(first_differences(unified))+'\n')
    sys.stdout.write(open(os.path.join(args.out, 'summary.txt')).read())


if __name__ == '__main__':
    main()
