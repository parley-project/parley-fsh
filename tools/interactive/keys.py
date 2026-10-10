"""keys.py [shell...]: type the same keys into bash and fsh at a terminal (a pty) and compare what
the edited commands did. Each test types a line (with editing keys) whose command writes its result
to the file o; o is compared. FSH_WORK: the scratch directory (bt/fsh, tmp/)."""
import fcntl, os, pty, re, select, shutil, struct, sys, termios, time

SP = os.environ.get("FSH_WORK", "/Volumes/Data/claude-work/fsh-session")
SHELLS = sys.argv[1:] or ["/usr/local/bin/bash", SP + "/bt/fsh"]
C = lambda ch: chr(ord(ch) & 0x1f)          # Ctrl-ch
M = lambda ch: "\x1b" + ch                  # Meta-ch (ESC ch)
TAB = "\t"
ENTER = "\r"

# name: (setup lines run first, the keys of the test) -> o holds the result
TESTS = {
    "ctrl-a-e": ([], ["bc", C("a"), "echo a", C("e"), " >o", ENTER]),
    "ctrl-b-f": ([], ["echo ac", C("b"), "b", C("f"), " >o", ENTER]),
    "ctrl-k": ([], ["echo abcXYZ", C("b"), C("b"), C("b"), C("k"), " >o", ENTER]),
    "ctrl-u": ([], ["garbage", C("u"), "echo abc >o", ENTER]),
    "ctrl-w": ([], ["echo abc junk", C("w"), ">o", ENTER]),
    "ctrl-d-char": ([], ["echo aXbc", C("b"), C("b"), C("b"), C("d"), C("e"), " >o", ENTER]),
    "ctrl-t": ([], ["echo acb", C("t"), " >o", ENTER]),
    "ctrl-y": ([], ["echo abc", C("w"), C("y"), C("y"), " >o", ENTER]),
    "meta-b-f": ([], ["echo abc def", M("b"), M("b"), "x", M("f"), M("f"), "y >o", ENTER]),
    "meta-d": ([], ["echo abc XYZ def", M("b"), M("b"), M("d"), C("e"), " >o", ENTER]),
    "meta-backspace": ([], ["echo abc XYZ", "\x1b\x7f", ">o", ENTER]),
    "meta-u": ([], ["echo abc", M("b"), M("u"), " >o", ENTER]),
    "meta-l": ([], ["echo ABC", M("b"), M("l"), " >o", ENTER]),
    "meta-c": ([], ["echo abc", M("b"), M("c"), " >o", ENTER]),
    "backspace": ([], ["echo abcX", "\x7f", " >o", ENTER]),
    "history-up": (["echo one >o"], ["echo zero >o", ENTER, C("p"), C("p"), ENTER]),
    "history-down": (["echo one >o", "echo two >o"], [C("p"), C("p"), C("n"), ENTER]),
    "history-arrows": (["echo one >o"], ["\x1b[A", ENTER]),
    "reverse-search": (["echo unique1 >o", "echo x >o"], [C("r"), "uniq", ENTER]),
    "bang-bang": (["echo hist >o", "echo other >p"], ["!-2", ENTER]),
    "yank-last-arg": (["echo hello"], ["echo ", M("."), " >o", ENTER]),
    "ctrl-c-line": ([], ["echo bad >o", C("c"), "echo good >o", ENTER]),
    "complete-file": (["touch alpha.txt"], ["echo alp", TAB, " >o", ENTER]),
    "complete-dir": (["mkdir -p subdir"], ["echo subd", TAB, "x >o", ENTER]),
    "complete-command": ([], ["ech", TAB, "zz >o", ENTER]),
    "complete-variable": (["XYZVAR=1"], ["echo $XYZV", TAB, " >o", ENTER]),
    "complete-common-prefix": (["touch apple1 apple2"], ["echo app", TAB, "X >o", ENTER]),
    "complete-tilde": ([], ["echo ~/", C("e"), " >o", ENTER]),
    "ctrl-x-ctrl-e-less": ([], ["echo abc", C("a"), C("k"), "echo k >o", ENTER]),
    "quoted-insert": ([], ["echo a", C("v"), TAB, "b | od -c | head -1 | tr -s ' ' >o", ENTER]),
    "complete-space-file": (["touch 'my file.txt'"], ["echo my", TAB, " >o", ENTER]),
    "complete-W": (["complete -W 'alpha beta' xcmd", "xcmd() { echo \"$@\" >o; }"], ["xcmd al", TAB, ENTER]),
    "complete-F": (["_zc() { COMPREPLY=(zeta); }", "complete -F _zc xcmd", "xcmd() { echo \"$@\" >o; }"], ["xcmd z", TAB, ENTER]),
    "complete-F-words": (["_zc() { COMPREPLY=(\"${COMP_WORDS[0]}-$COMP_CWORD\"); }", "complete -F _zc xcmd", "xcmd() { echo \"$@\" >o; }"], ["xcmd a ", TAB, ENTER]),
    "bang-dollar": (["echo a b last"], ["echo !$ >o", ENTER]),
    "caret-subst": (["echo foo >p"], ["^p^o", ENTER]),
    "undo": ([], ["echo abc", "XYZ", C("_"), " >o", ENTER]),
    "multiline": ([], ["for i in 1 2; do", ENTER, "echo $i", ENTER, "done >o", ENTER]),
    "vi-x-A": (["set -o vi"], ["echo abcX", "\x1b", "x", "A", " >o", ENTER]),
    "vi-dd": (["set -o vi"], ["garbage", "\x1b", "dd", "i", "echo vi >o", ENTER]),
    "vi-cw": (["set -o vi"], ["echo xyz", "\x1b", "b", "cw", "abc", "\x1b", "A", " >o", ENTER]),
    "alias-prompt": (["alias e1='echo aliased'"], ["e1 >o", ENTER]),
    "history-search-prefix": (["echo first >o", "true"], ["echo", "\x1bp", ENTER]),
    "meta-dot-twice": (["echo one", "echo two"], ["echo ", M("."), M("."), " >o", ENTER]),
    "ctrl-x-ctrl-r-comment": ([], ["echo abc >o", M("#")]),
    # screen: what the terminal shows (the lines that are not a prompt's), not o
    "screen-list-completions": (["touch a1 a2 a3"], ["echo a", TAB, TAB, C("c")]),
    "screen-list-commands": ([], ["xy", TAB, TAB, C("c")]),
    "screen-many-completions": (["touch f{01..120}"], ["echo f", TAB, TAB, "n", C("c")]),
    "screen-ctrl-c": ([], ["echo partial", C("c")]),
    "screen-history-list": (["echo one", "echo two"], ["history 3", ENTER]),
    "screen-ps1": (["PS1='[\\u:\\W]\\$ '"], ["echo hi", ENTER]),
    "screen-ps2": ([], ["for i in 1; do", ENTER, "echo $i", ENTER, "done", ENTER]),
    "screen-ps0": (["PS0='ps0\\n'"], ["echo hi", ENTER]),
    "screen-prompt-command": (["PROMPT_COMMAND='echo pc'"], [ENTER, "echo hi", ENTER]),
    "screen-pager": (["touch g{001..400}"], ["echo g", TAB, TAB, "y", " ", "q", C("c")]),
}

def screen_lines(text):
    """the lines a terminal shows for text: \r goes back to the start of the line, ESC[nC moves
    right, ESC[K / ESC[J clear to the end; other escape sequences show nothing"""
    lines, line, col = [], [], 0
    i = 0
    while i < len(text):
        c = text[i]
        if c == "\x1b":
            m = re.match(r"\x1b\[([0-9;?]*)([A-Za-z])|\x1b[()][0-9A-B]|\x1b[=>]", text[i:])
            if m:
                if m.group(2) == "C":
                    col += int(m.group(1) or "1")
                elif m.group(2) in ("K", "J"):
                    del line[col:]
                i += m.end()
                continue
        if c == "\n":
            lines.append("".join(line).rstrip())
            line, col = [], 0
        elif c == "\r":
            col = 0
        elif c == "\x07":
            pass
        else:
            while len(line) < col:
                line.append(" ")
            if col < len(line):
                line[col] = c
            else:
                line.append(c)
            col += 1
        i += 1
    lines.append("".join(line).rstrip())
    return lines

def run(shell, setup, keys, work, screen=False):
    env = {"HOME": work, "PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "TERM": "xterm", "PS1": "$ ",
           "INPUTRC": "/dev/null", "HISTFILE": work + "/.hist", "TMPDIR": SP + "/tmp", "LANG": "en_US.UTF-8"}
    args = [shell, "--norc", "--noprofile", "-i"] if shell.endswith("bash") else [shell, "--norc", "-i"]
    pid, fd = pty.fork()
    if pid == 0:
        os.chdir(work)
        os.execve(shell, args, env)
    # (24 lines of 80: completion lists and the pager as on a terminal of that size)
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 80, 0, 0))
    out = b""
    def pump(t):
        nonlocal out
        end = time.time() + t
        while time.time() < end:
            r, _, _ = select.select([fd], [], [], 0.05)
            if r:
                try:
                    out += os.read(fd, 65536)
                except OSError:
                    return
    pump(1.5 if "fsh" in shell else 0.3)
    for line in setup:
        os.write(fd, (line + "\r").encode())
        pump(0.5)
    for k in keys:
        os.write(fd, k.encode())
        pump(0.25)
    pump(0.6)
    os.write(fd, b"exit\r")
    pump(0.5)
    try:
        os.kill(pid, 9)
    except OSError:
        pass
    os.waitpid(pid, 0)
    if screen:
        text = out.decode("utf-8", "replace").replace(os.path.basename(work), "W")
        return "\n".join(l for l in screen_lines(text) if l and not l.startswith("$ ") and l != "exit")
    try:
        return open(os.path.join(work, "o")).read().replace(work, "W").replace(os.path.basename(work), "W")
    except OSError:
        return "(no o)"

names = [n for n in TESTS if not os.environ.get("ONLY") or n in os.environ["ONLY"].split(",")]
same = 0
for name in names:
    setup, keys = TESTS[name]
    results = []
    for sh in SHELLS:
        work = os.path.join(SP, "tmp", "keys", name + "-" + os.path.basename(sh))
        shutil.rmtree(work, ignore_errors=True)
        os.makedirs(work)
        results.append(run(sh, setup, keys, work, name.startswith("screen-")))
    ok = all(r == results[0] for r in results)
    same += ok
    print(f"{name:24} {'same' if ok else 'DIFF'}  " + "  ".join(repr(r) for r in results))
print(f"{same} of {len(names)} the same")
