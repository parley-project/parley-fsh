"""Run real-world scripts with bash and with fsh (shell SCRIPT ARGS in a fresh scratch dir
each) and compare output and status. python3 -I cases.py [name ...]"""
import os, re, shutil, subprocess, sys, time
# scratch: FSH_WORK (bt/fsh, the fsh launcher, and tmp/ under it), as tools/bash-suite uses
SP = os.environ.get("FSH_WORK", "/Volumes/Data/claude-work/fsh-session")
HERE = os.path.dirname(os.path.abspath(__file__))
BASH = "/usr/local/bin/bash"
FSH = SP + "/bt/fsh"
GITCORE = "/Library/Developer/CommandLineTools/usr/libexec/git-core"
WORK = SP + "/rw/work"

GITREPO = """git init -q -b main . && git config user.email t@example.com && git config user.name T &&
mkdir -p lib && echo one > lib/a && echo top > b && git add . && git commit -qm one &&
echo two >> lib/a && git commit -qam two && echo three > b && git commit -qam three"""

# name: (setup run with /bin/sh in the dir, script, args, extra env)
CASES = {
  "libtool-version": ("", "/usr/local/bin/glibtool", ["--version"], {}),
  "libtool-help": ("", "/usr/local/bin/glibtool", ["--help"], {}),
  "libtool-config": ("", "/usr/local/bin/glibtool", ["--config"], {}),
  "libtool-features": ("", "/usr/local/bin/glibtool", ["--features"], {}),
  "libtool-compile": ("echo 'int f(void){return 1;}' > f.c", "/usr/local/bin/glibtool", ["--tag=CC", "--mode=compile", "cc", "-c", "f.c"], {}),
  "libtool-link": ("echo 'int f(void){return 1;}' > f.c && /usr/local/bin/glibtool --quiet --tag=CC --mode=compile cc -c f.c", "/usr/local/bin/glibtool", ["--tag=CC", "--mode=link", "cc", "-o", "libf.la", "f.lo", "-rpath", "/usr/local/lib"], {}),
  "libtool-dryrun": ("", "/usr/local/bin/glibtool", ["--dry-run", "--tag=CC", "--mode=compile", "cc", "-c", "x.c"], {}),
  "libtoolize": ("", "/usr/local/bin/glibtoolize", ["--copy", "--install"], {}),
  "libtoolize-help": ("", "/usr/local/bin/glibtoolize", ["--help"], {}),
  "manpath": ("", "/usr/bin/manpath", [], {}),
  "man-w": ("", "/usr/bin/man", ["-w", "ls"], {}),
  "man-a-w": ("", "/usr/bin/man", ["-a", "-w", "printf"], {}),
  "whatis": ("", "/usr/bin/whatis", ["ls"], {}),
  "apropos": ("", "/usr/bin/apropos", ["chmod"], {}),
  "brew-prefix": ("", "/usr/local/bin/brew", ["--prefix"], {"HOMEBREW_NO_AUTO_UPDATE": "1", "HOMEBREW_NO_ANALYTICS": "1"}),
  "brew-version": ("", "/usr/local/bin/brew", ["--version"], {"HOMEBREW_NO_AUTO_UPDATE": "1", "HOMEBREW_NO_ANALYTICS": "1"}),
  "brew-repository": ("", "/usr/local/bin/brew", ["--repository"], {"HOMEBREW_NO_AUTO_UPDATE": "1", "HOMEBREW_NO_ANALYTICS": "1"}),
  "autoconf-version": ("", "/usr/local/bin/autoconf", ["--version"], {}),
  "gettextize-help": ("", "/usr/local/bin/gettextize", ["--help"], {}),
  "autopoint-version": ("", "/usr/local/bin/autopoint", ["--version"], {}),
  "ssh-copy-id-help": ("", "/usr/bin/ssh-copy-id", ["-h"], {}),
  "xzgrep": ("printf 'alpha\\nbeta\\ngamma\\n' > t.txt && xz -k t.txt", "/usr/local/bin/xzgrep", ["-n", "a$", "t.txt", "t.txt.xz"], {}),
  "xzdiff": ("printf 'a\\nb\\n' > x && printf 'a\\nc\\n' > y && xz -k x", "/usr/local/bin/xzdiff", ["x.xz", "y"], {}),
  "curl-config": ("", "/usr/bin/curl-config", ["--libs", "--version", "--features"], {}),
  "freetype-config": ("", "/usr/local/bin/freetype-config", ["--cflags", "--libs"], {}),
  "ncurses-config": ("", "/usr/bin/ncurses5.4-config", ["--libs", "--version"], {}),
  "bashbug-version": ("", "/usr/local/bin/bashbug", ["--version"], {}),
  "git-subtree-split": (GITREPO, GITCORE + "/git-subtree", ["split", "--prefix=lib", "-q"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE}),
  "git-subtree-help": ("", GITCORE + "/git-subtree", ["-h"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE}),
  "git-submodule-status": (GITREPO, GITCORE + "/git-submodule", ["status"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE}),
  "git-filter-branch": (GITREPO, GITCORE + "/git-filter-branch", ["-f", "--msg-filter", "tr a-z A-Z", "HEAD"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE, "FILTER_BRANCH_SQUELCH_WARNING": "1"}),
  "git-mergetool-help": (GITREPO, GITCORE + "/git-mergetool", ["--tool-help"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE}),
  "git-completion": (GITREPO + " && git branch -q maint && git tag v1.0 && git remote add origin https://example.com/r.git", HERE + "/git-comp.sh", [], {"PATH": GITCORE + ":" + os.environ["PATH"], "HOME": WORK}),
  "other-completion": ("", HERE + "/other-comp.sh", [], {"HOMEBREW_NO_AUTO_UPDATE": "1"}),
  "git-web-browse-help": ("", GITCORE + "/git-web--browse", ["-h"], {"PATH": GITCORE + ":" + os.environ["PATH"], "GIT_EXEC_PATH": GITCORE}),
}

def norm(text, d, shell):
    text = text.replace(d, "DIR")
    text = text.replace(shell, "SHELL")
    text = re.sub(r"(?m)^" + re.escape(os.path.basename(shell)) + r":", "SHELL:", text)
    # (commit ids and times differ between the runs)
    text = re.sub(r"\b[0-9a-f]{40}\b", "SHA", text)
    return text

def run(name, shell):
    setup, script, args, extra = CASES[name]
    d = os.path.join(WORK, name + "-" + os.path.basename(shell))
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    env = dict(os.environ, TMPDIR=SP + "/tmp", GIT_AUTHOR_DATE="2026-01-01T00:00:00", GIT_COMMITTER_DATE="2026-01-01T00:00:00", **extra)
    env.pop("BASH_ENV", None)
    if setup:
        subprocess.run(["/bin/sh", "-c", setup], cwd=d, env=env, capture_output=True, timeout=120)
    t = time.time()
    try:
        r = subprocess.run([shell, script] + args, cwd=d, env=env, capture_output=True, timeout=300, stdin=subprocess.DEVNULL)
        out, err, rc = r.stdout, r.stderr, r.returncode
    except subprocess.TimeoutExpired as e:
        out, err, rc = e.stdout or b"", e.stderr or b"", "timeout"
    took = time.time() - t
    files = sorted(os.path.relpath(os.path.join(p, f), d) for p, _, fs in os.walk(d) if "/.git" not in p + "/" for f in fs)
    return (norm(out.decode('utf-8', 'replace'), d, shell), norm(err.decode('utf-8', 'replace'), d, shell), rc, files), took

names = sys.argv[1:] or list(CASES)
same = 0
DIFFS = SP + "/rw/diffs-" + time.strftime("%H%M%S")
os.makedirs(DIFFS, exist_ok=True)
print("diffs in", DIFFS)
for name in names:
    b, tb = run(name, BASH)
    f, tf = run(name, FSH)
    ok = b == f
    same += ok
    print(f"{name:24} {'same' if ok else 'DIFF'}  bash={tb:.1f}s fsh={tf:.1f}s  rc {b[2]}/{f[2]}")
    if not ok:
        with open(DIFFS + "/" + name + ".txt", "w") as o:
            for label, x, y in (("stdout", b[0], f[0]), ("stderr", b[1], f[1]), ("rc", str(b[2]), str(f[2])), ("files", "\n".join(b[3]), "\n".join(f[3]))):
                if x != y:
                    import difflib
                    o.write(f"== {label}\n")
                    o.writelines(difflib.unified_diff(x.splitlines(True), y.splitlines(True), "bash", "fsh"))
                    o.write("\n")
print(f"{same} of {len(names)} the same")
