#!/usr/bin/env python3
"""
Measure fsh builds against each other (Parley 2.0.0 To-Do 6.16, the Crema spike): start-up time,
memory, whether the plugins loaded, and how browse feels.

  tools/crema-measure.py [-n RUNS] [--keys N] [--out FILE] LABEL=COMMAND ...

  tools/crema-measure.py jvm="env FSH_RUNTIME=jvm macos/fsh" native=target/native/fsh \
      crema=target/native-crema/fsh crema-jit=target/native-crema-jit/fsh

For each build:
  1. start-up: runs a one-line script RUNS times; the median and best wall time, and the largest
     resident set size (the process and its children: a JVM run through fshexec counts the JVM)
  2. plugins: what the plugins builtin reports (did the browser plugin load?)
  3. browse, in a 120x40 pseudo-terminal, on a folder of 2,000 files: the time to the first
     screen, then N cursor moves one at a time (time from the key to the screen's answer: median,
     95th percentile, worst), Enter into a folder and back, quit; done twice in one shell, so the
     second shows what warming up (Ristretto, the JVM's JIT) buys; and the shell's resident size
     while browsing
Python 3 only, no packages: runs on macOS and Linux.
"""
import argparse
import os
import pty
import resource
import select
import shlex
import shutil
import signal
import statistics
import subprocess
import sys
import tempfile
import time

ROWS, COLS = 40, 120
DOWN, ENTER, LEFT = b"\x1b[B", b"\r", b"\x1b[D"


def maxrss_children_kb():
	r = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
	return r // 1024 if sys.platform == "darwin" else r  # (bytes on macOS, KB on Linux)


def tree_rss_kb(pid):
	"""resident size of pid and its descendants (ps), in KB"""
	try:
		out = subprocess.run(["ps", "-A", "-o", "pid=,ppid=,rss="], capture_output=True, text=True).stdout
	except OSError:
		return 0
	kids, rss = {}, {}
	for line in out.splitlines():
		p = line.split()
		if len(p) == 3:
			kids.setdefault(int(p[1]), []).append(int(p[0]))
			rss[int(p[0])] = int(p[2])
	total, todo = 0, [pid]
	while todo:
		p = todo.pop()
		total += rss.get(p, 0)
		todo.extend(kids.get(p, []))
	return total


def startup(cmd, runs, script):
	times = []
	before = maxrss_children_kb()
	for _ in range(runs):
		t = time.perf_counter()
		r = subprocess.run(cmd + [script], capture_output=True, text=True)
		times.append(time.perf_counter() - t)
		if r.returncode != 0 or "started" not in r.stdout:
			return {"error": (r.stdout + r.stderr).strip()[-400:]}
	# (getrusage keeps the largest child so far: only meaningful when it grew)
	after = maxrss_children_kb()
	return {"median_ms": round(statistics.median(times) * 1000, 1), "best_ms": round(min(times) * 1000, 1),
			"max_rss_mb": round(after / 1024, 1) if after > before else None}


def plugins(cmd):
	r = subprocess.run(cmd, input="plugins\nbrowse --list\n", capture_output=True, text=True, timeout=120)
	return (r.stdout + r.stderr).strip()


class Pty:
	def __init__(self, cmd, cwd):
		self.pid, self.fd = pty.fork()
		if self.pid == 0:
			os.chdir(cwd)
			os.environ["TERM"] = "xterm-256color"
			os.environ["LINES"], os.environ["COLUMNS"] = str(ROWS), str(COLS)
			os.execvp(cmd[0], cmd)
		import fcntl, struct, termios
		fcntl.ioctl(self.fd, termios.TIOCSWINSZ, struct.pack("HHHH", ROWS, COLS, 0, 0))
		self.buf = b""

	def read(self, quiet, limit):
		"""read until nothing comes for quiet seconds (or limit passes); (first byte time, last byte time, data)"""
		start = time.perf_counter()
		first = last = None
		data = b""
		while True:
			now = time.perf_counter()
			if now - start > limit:
				break
			wait = quiet if last is not None else limit - (now - start)
			r, _, _ = select.select([self.fd], [], [], max(0.0, wait))
			if not r:
				if last is not None:
					break
				continue
			try:
				chunk = os.read(self.fd, 65536)
			except OSError:
				break
			if not chunk:
				break
			t = time.perf_counter()
			first = first or t
			last = t
			data += chunk
		return first, last, data

	def send(self, b):
		os.write(self.fd, b)

	def wait_for(self, text, limit):
		end = time.perf_counter() + limit
		got = b""
		while time.perf_counter() < end:
			_, _, d = self.read(0.05, max(0.05, end - time.perf_counter()))
			got += d
			if text in got:
				return True
		return False

	def close(self):
		try:
			os.kill(self.pid, signal.SIGKILL)
		except OSError:
			pass
		try:
			os.waitpid(self.pid, 0)
		except OSError:
			pass


def browse(cmd, folder, keys):
	p = Pty(cmd, folder)
	res = {}
	try:
		# (the marker is in the output, not in the line echoed as it is typed)
		p.send(b"echo RE''ADY\r")
		if not p.wait_for(b"READY\r", 60):
			return {"error": "the shell did not start"}
		p.read(0.3, 2)
		for run in (1, 2):
			t = time.perf_counter()
			p.send(b"browse -1 .\r")
			first, last, d = p.read(0.25, 60)
			if first is None:
				return {"error": "browse drew nothing"}
			r = {"first_screen_ms": round((last - t) * 1000, 1)}
			lat = []
			for _ in range(keys):
				t = time.perf_counter()
				p.send(DOWN)
				f, _, _ = p.read(0.02, 5)
				if f is not None:
					lat.append((f - t) * 1000)
			lat.sort()
			if lat:
				r.update(key_median_ms=round(statistics.median(lat), 2),
						key_p95_ms=round(lat[int(len(lat) * 0.95) - 1], 2), key_worst_ms=round(lat[-1], 2),
						keys_answered=len(lat))
			p.send(b"\x1b[H")  # Home: the first entry, a folder
			p.read(0.05, 2)
			t = time.perf_counter()
			p.send(ENTER)
			f, l, _ = p.read(0.05, 10)
			r["enter_folder_ms"] = round(((l or t) - t) * 1000, 1)
			p.send(LEFT)
			p.read(0.05, 10)
			r["rss_mb"] = round(tree_rss_kb(p.pid) / 1024, 1)
			p.send(b"q")
			p.read(0.3, 10)
			res["browse%d" % run] = r
		p.send(b"exit\n")
		p.read(0.2, 5)
	finally:
		p.close()
	return res


def make_folder():
	d = tempfile.mkdtemp(prefix="crema-browse-")
	for i in range(20):
		os.mkdir(os.path.join(d, "a-folder-%02d" % i))
		for j in range(20):
			open(os.path.join(d, "a-folder-%02d" % i, "f%d.txt" % j), "w").write("x\n")
	for i in range(2000):
		with open(os.path.join(d, "file-%04d.txt" % i), "w") as f:
			f.write("line %d\n" % i * 20)
	return d


def main():
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("-n", "--runs", type=int, default=10)
	ap.add_argument("--keys", type=int, default=200)
	ap.add_argument("--out")
	ap.add_argument("builds", nargs="+", metavar="LABEL=COMMAND")
	a = ap.parse_args()
	folder = make_folder()
	script = os.path.join(folder, "start.sh")
	open(script, "w").write("echo started\n")
	lines = []

	def say(s=""):
		print(s, flush=True)
		lines.append(s)

	say("crema-measure %s  %s %s" % (time.strftime("%Y-%m-%d %H:%M"), sys.platform, os.uname().machine))
	try:
		for b in a.builds:
			label, _, command = b.partition("=")
			# (paths made absolute: browse runs the build in another folder)
			cmd = [os.path.abspath(w) if "/" in w and os.path.exists(w) else w for w in shlex.split(command)]
			say("\n== %s: %s" % (label, command))
			say("start-up: %s" % startup(cmd, a.runs, script))
			say("plugins:\n  " + plugins(cmd).replace("\n", "\n  "))
			for k, v in browse(cmd, folder, a.keys).items():
				say("%s: %s" % (k, v))
	finally:
		shutil.rmtree(folder, ignore_errors=True)
	if a.out:
		open(a.out, "w").write("\n".join(lines) + "\n")


if __name__ == "__main__":
	main()
