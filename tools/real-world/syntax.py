"""bash -n and fsh -n on every sh and bash script on the machine: the ones they differ on."""
import os, subprocess, sys
dirs = ["/usr/bin","/usr/sbin","/usr/libexec","/usr/local/bin","/usr/local/sbin",
        "/Library/Developer/CommandLineTools/usr/libexec/git-core","/Library/Developer/CommandLineTools/usr/bin",
        "/usr/local/etc/bash_completion.d"]
# scratch: FSH_WORK (bt/fsh, the fsh launcher, and tmp/ under it), as tools/bash-suite uses
SP = os.environ.get("FSH_WORK", "/Volumes/Data/claude-work/fsh-session")
HERE = os.path.dirname(os.path.abspath(__file__))
files = []
for d in dirs:
    if not os.path.isdir(d): continue
    for n in sorted(os.listdir(d)):
        f = os.path.join(d, n)
        if not os.path.isfile(f): continue
        try:
            head = open(f, 'rb').read(64).split(b'\n')[0]
        except Exception:
            continue
        if d.endswith("bash_completion.d") or (head.startswith(b'#!') and (b'sh' in head) and b'zsh' not in head and b'ksh' not in head):
            files.append(f)
env = dict(os.environ, TMPDIR=SP + "/tmp")
def run(sh, f):
    try:
        r = subprocess.run([sh, "-n", f], capture_output=True, timeout=60, env=env)
        return r.returncode, r.stderr.decode('utf-8', 'replace').replace(f, "F")
    except subprocess.TimeoutExpired:
        return "timeout", ""
bad = 0
for f in files:
    b = run("/usr/local/bin/bash", f)
    s = run(SP + "/bt/fsh", f)
    if b != s:
        bad += 1
        print("==", f, "bash:", b, "fsh:", s)
print(len(files), "scripts,", bad, "differ")
