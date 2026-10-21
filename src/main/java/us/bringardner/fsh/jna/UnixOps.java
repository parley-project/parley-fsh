package us.bringardner.fsh.jna;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * The terminal and the process through libc, for macOS and Linux (glibc and musl, 64-bit).
 *
 * What differs between the two (the layout of termios, siginfo_t, rusage and utsname, the numbers
 * of the ioctl requests and flags) is in the constants below, from their headers; everything else
 * is the same calls as macos/NativeKeyboard.cpp and linux/NativeKeyboard.cpp make.
 */
final class UnixOps extends NativeOps {

	/** libc's functions with fixed arguments (JNA direct mapping: no proxies, quick to start) */
	static final class C {
		static {
			Native.register("c");
		}
		static native int isatty(int fd);
		static native String ttyname(int fd);
		static native int tcgetattr(int fd, Pointer t);
		static native int tcsetattr(int fd, int action, Pointer t);
		static native int tcgetpgrp(int fd);
		static native int tcsetpgrp(int fd, int group);
		static native int setpgid(int pid, int group);
		static native int getpgrp();
		static native int poll(Pointer fds, long n, int timeout);
		static native long read(int fd, Pointer buf, long n);
		static native int open(String path, int flags);
		static native int close(int fd);
		static native int waitid(int idtype, int id, Pointer info, int options);
		static native int getrusage(int who, Pointer usage);
		static native int getrlimit(int resource, Pointer limit);
		static native int setrlimit(int resource, Pointer limit);
		static native int getuid();
		static native int getgid();
		static native int getgroups(int n, Pointer groups);
		static native int uname(Pointer name);
		static native int sigemptyset(Pointer set);
		static native int sigaddset(Pointer set, int signal);
		static native int pthread_sigmask(int how, Pointer set, Pointer old);
	}

	/** what only macOS has (a class of its own: JNA looks up all of a class's functions as it loads it) */
	static final class Mac {
		static {
			Native.register("c");
		}
		static native int sysctl(int [] name, int namelen, Pointer old, Pointer oldlen, Pointer newp, long newlen);
	}

	/**
	 * The variadic functions: through an interface, because a call of one must say which arguments
	 * are the variable ones (on Apple's arm64 they are passed on the stack, not in registers).
	 */
	interface V extends Library {
		int ioctl(int fd, NativeLong request, Object... args);
		int fcntl(int fd, int cmd, Object... args);
	}

	private static final int STDIN = 0;
	private static final int STDOUT = 1;
	private static final int EINTR = 4;
	private static final int SIGTTOU = 22;

	private final boolean mac;
	private final V v;
	/** starting programs and passing descriptors (null: this C library lacks something for it) */
	private final UnixSpawn spawn;
	private final String spawnProblem;

	// termios (see <termios.h>): where the local flags and the control characters are
	private final int lflagOffset;
	private final int ccOffset;
	private final int vmin;
	private final int vtime;
	private final long iCanon;
	private final long iSig;
	private static final long ECHO = 0x8;
	private static final int TCSANOW = 0;
	private static final int TCSADRAIN = 1;
	private static final int TCSAFLUSH = 2;

	private final long tiocgwinsz;
	private final long fionread;
	private final int sigBlock;
	private final int sigSetmask;

	/** the terminal's own settings, saved once (a program that leaves another mode does not change them) */
	private Memory orig;
	private boolean rawMode;
	private boolean keysNotSignals;
	private boolean lentRaw;
	private boolean hookAdded;

	private static final int BUFFER_SIZE = 300;
	private final byte [] buffer = new byte[BUFFER_SIZE];
	private final Memory readBuffer = new Memory(BUFFER_SIZE);
	private int cnt;
	private int idx;

	UnixOps(boolean mac) {
		this.mac = mac;
		// (loading the library and the first call: if it cannot, create() has no JNA layer)
		C.isatty(STDIN);
		v = Native.load("c", V.class);
		UnixSpawn s = null;
		String problem = null;
		try {
			s = new UnixSpawn(mac);
		} catch (Throwable e) {
			// (without it fsh starts programs with ProcessBuilder)
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			problem = cause.getClass().getSimpleName()+": "+cause.getMessage();
			if( Boolean.getBoolean("parley.fsh.native.debug") ) {
				e.printStackTrace();
			}
		}
		spawn = s;
		spawnProblem = problem;
		if( mac ) {
			lflagOffset = 24;
			ccOffset = 32;
			vmin = 16;
			vtime = 17;
			iCanon = 0x100;
			iSig = 0x80;
			tiocgwinsz = 0x40087468L;
			fionread = 0x4004667fL;
			sigBlock = 1;
			sigSetmask = 3;
		} else {
			lflagOffset = 12;
			ccOffset = 17;
			vmin = 6;
			vtime = 5;
			iCanon = 0x2;
			iSig = 0x1;
			tiocgwinsz = 0x5413L;
			fionread = 0x541BL;
			sigBlock = 0;
			sigSetmask = 2;
		}
	}

	private long lflag(Pointer t) {
		return mac ? t.getLong(lflagOffset) : t.getInt(lflagOffset) & 0xffffffffL;
	}

	private void setLflag(Pointer t, long value) {
		if( mac ) {
			t.setLong(lflagOffset, value);
		} else {
			t.setInt(lflagOffset, (int) value);
		}
	}

	private void disableRawMode() {
		if( rawMode ) {
			C.tcsetattr(STDIN, TCSAFLUSH, orig);
			rawMode = false;
		}
	}

	/** Turn off echo and line buffering. */
	private void enableRawMode() {
		if( rawMode || C.isatty(STDIN) == 0 ) {
			return;
		}
		if( orig == null ) {
			Memory m = new Memory(128);
			m.clear();
			if( C.tcgetattr(STDIN, m) == -1 ) {
				return;
			}
			orig = m;
		}
		if( !hookAdded ) {
			hookAdded = true;
			// (what atexit does in the library: the terminal is as it was when the shell ends)
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				synchronized (UnixOps.this) {
					disableRawMode();
				}
			}, "fsh-restore-terminal"));
		}
		Memory raw = new Memory(128);
		raw.write(0, orig.getByteArray(0, 128), 0, 128);
		long flags = lflag(raw) & ~(ECHO | iCanon);
		if( keysNotSignals ) {
			flags &= ~iSig;
		}
		setLflag(raw, flags);
		raw.setByte(ccOffset + vmin, (byte) 1);
		raw.setByte(ccOffset + vtime, (byte) 0);
		if( C.tcsetattr(STDIN, TCSADRAIN, raw) == 0 ) {
			rawMode = true;
		}
	}

	@Override
	public synchronized void setSignalKeys(boolean on) {
		keysNotSignals = on;
		Memory t = new Memory(128);
		t.clear();
		if( rawMode && C.tcgetattr(STDIN, t) == 0 ) {
			// now, without dropping what was typed
			long flags = lflag(t);
			setLflag(t, on ? flags & ~iSig : flags | iSig);
			C.tcsetattr(STDIN, TCSANOW, t);
		}
	}

	@Override
	public synchronized void setProgramMode(boolean on) {
		if( on ) {
			lentRaw = rawMode;
			if( rawMode ) {
				C.tcsetattr(STDIN, TCSADRAIN, orig);
				rawMode = false;
			}
		} else {
			// the shell's process group has the terminal again (if a job's group had it)
			takeTerminal(C.getpgrp());
			if( lentRaw ) {
				lentRaw = false;
				enableRawMode();
			}
		}
	}

	/** make group the terminal's foreground group (the shell may be in the background for this) */
	private void takeTerminal(int group) {
		takeTerminal(STDIN, group);
	}

	private void takeTerminal(int fd, int group) {
		if( C.isatty(fd) == 0 || C.tcgetpgrp(fd) == group ) {
			return;
		}
		// (SIGTTOU blocked: a background shell would be stopped by tcsetpgrp)
		Memory block = new Memory(256);
		Memory old = new Memory(256);
		block.clear();
		old.clear();
		C.sigemptyset(block);
		C.sigaddset(block, SIGTTOU);
		C.pthread_sigmask(sigBlock, block, old);
		try {
			C.tcsetpgrp(fd, group);
		} finally {
			C.pthread_sigmask(sigSetmask, old, null);
		}
	}

	// starting programs and passing descriptors (UnixSpawn)

	@Override
	public boolean canSpawn() {
		return spawn != null;
	}

	@Override
	public String spawnProblem() {
		return spawnProblem;
	}

	private UnixSpawn spawn() {
		if( spawn == null ) {
			throw new UnsatisfiedLinkError("starting programs is not done through JNA here");
		}
		return spawn;
	}

	@Override
	public int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, int pgroup, boolean suspended) {
		return spawn().spawn(path, argv, envp, dir, fds, pgroup, suspended);
	}

	@Override
	public int [] pipe() {
		return spawn().pipe();
	}

	@Override
	public int open(byte [] path, int how) {
		return spawn().open(path, how);
	}

	@Override
	public void close(int fd) {
		spawn().close(fd);
	}

	@Override
	public int waitExit(int pid) {
		return spawn().waitExit(pid);
	}

	@Override
	public int kill(int pid, int sig) {
		return spawn().kill(pid, sig);
	}

	@Override
	public String strerror(int err) {
		return spawn().strerror(err);
	}

	@Override
	public int [] socketPair() {
		return spawn().socketPair();
	}

	@Override
	public int sendWithDescriptors(int sock, byte [] payload, int [] fds) {
		return spawn().sendWithDescriptors(sock, payload, fds);
	}

	@Override
	public java.io.InputStream inputStream(int fd) {
		return spawn().new In(fd);
	}

	@Override
	public java.io.OutputStream outputStream(int fd) {
		return spawn().new Out(fd);
	}

	@Override
	public void giveTerminalTo(long group) {
		takeTerminal((int) group);
	}

	@Override
	public void setProcessGroup(long pid, long group) {
		C.setpgid((int) pid, (int) group);
	}

	@Override
	public int stoppedBy(long pid) {
		Memory info = new Memory(128);
		info.clear();
		// (WSTOPPED: macOS 0x8, Linux 0x2) | WNOHANG
		int options = (mac ? 0x8 : 0x2) | 0x1;
		int pidOffset = mac ? 12 : 16;
		int statusOffset = mac ? 20 : 24;
		final int CLD_STOPPED = 5;
		final int P_PID = 1;
		if( C.waitid(P_PID, (int) pid, info, options) == 0 && info.getInt(pidOffset) == (int) pid
				&& info.getInt(8) == CLD_STOPPED ) {
			return info.getInt(statusOffset);
		}
		return 0;
	}

	@Override
	public void ttyTo(int group) {
		if( group == 0 ) {
			group = C.getpgrp();
		}
		// (O_RDWR | O_CLOEXEC)
		int tty = C.open("/dev/tty", 2 | (mac ? 0x01000000 : 0x80000));
		if( tty < 0 ) {
			return;
		}
		try {
			takeTerminal(tty, group);
		} finally {
			C.close(tty);
		}
	}

	@Override
	public long pgrp() {
		return C.getpgrp();
	}

	@Override
	public int columns() {
		Memory ws = new Memory(8);
		ws.clear();
		if( v.ioctl(STDOUT, new NativeLong(tiocgwinsz), ws) == 0 ) {
			int cols = ws.getShort(2) & 0xffff;
			if( cols > 0 ) {
				return cols;
			}
		}
		return 0;
	}

	@Override
	public String ttyName() {
		return C.ttyname(STDIN);
	}

	@Override
	public int inputKind() {
		// st_mode of what descriptor 0 is: the unix view's mode is the same on both systems, where
		// struct stat is not
		try {
			Object mode = Files.getAttribute(Path.of("/dev/stdin"), "unix:mode");
			int type = ((Integer) mode) & 0170000;
			if( type == 0010000 || type == 0140000 ) {
				// a FIFO or a socket
				return 1;
			}
			return type == 0100000 ? 2 : 0;
		} catch (IOException | RuntimeException e) {
			throw new UnsatisfiedLinkError("inputKind: "+e);
		}
	}

	@Override
	public boolean isInputTerminal() {
		return C.isatty(STDIN) != 0;
	}

	@Override
	public boolean isTerminal() {
		return C.isatty(STDIN) != 0 && C.isatty(STDOUT) != 0;
	}

	@Override
	public boolean isatty(int fd) {
		return C.isatty(fd) != 0;
	}

	@Override
	public synchronized int ready() {
		if( idx < cnt ) {
			return cnt - idx;
		}
		enableRawMode();
		Memory n = new Memory(4);
		n.setInt(0, 0);
		if( v.ioctl(STDIN, new NativeLong(fionread), n) == -1 ) {
			return 0;
		}
		return n.getInt(0);
	}

	@Override
	public synchronized int getChar() {
		if( idx < cnt ) {
			return buffer[idx++] & 0xff;
		}

		enableRawMode();

		// struct pollfd { int fd; short events; short revents; }
		Memory p = new Memory(8);
		p.setInt(0, STDIN);
		p.setShort(4, (short) 1);
		p.setShort(6, (short) 0);
		int r = C.poll(p, 1, 1000);
		if( r == 0 ) {
			return KEY_NONE;
		}
		if( r < 0 ) {
			int err = Native.getLastError();
			// (a signal ended the wait: nothing was typed; anything else, the input is gone)
			return err == EINTR || err == 0 ? KEY_NONE : KEY_EOF;
		}

		long n;
		int tries = 0;
		do {
			n = C.read(STDIN, readBuffer, BUFFER_SIZE);
		} while( n < 0 && (Native.getLastError() == EINTR || Native.getLastError() == 0) && ++tries < 100 );

		if( n <= 0 ) {
			// 0 is end of file
			cnt = idx = 0;
			return KEY_EOF;
		}
		readBuffer.read(0, buffer, 0, (int) n);
		cnt = (int) n;
		idx = 0;

		int key = escapeKey(buffer, cnt);
		if( key != 0 ) {
			cnt = 0;
			return key;
		}

		return buffer[idx++] & 0xff;
	}

	@Override
	public String [] openDescriptors() {
		java.util.List<String> lines = new java.util.ArrayList<>();
		for(int fd = 3; fd < 256; fd++) {
			int flags = v.fcntl(fd, 1);
			if( flags < 0 || (flags & 1) != 0 ) {
				continue;
			}
			int status = v.fcntl(fd, 3);
			if( status < 0 ) {
				continue;
			}
			String path = "";
			if( mac ) {
				// F_GETPATH
				Memory buf = new Memory(1024);
				buf.clear();
				if( v.fcntl(fd, 50, buf) == 0 ) {
					path = buf.getString(0);
				}
			} else {
				try {
					// (what /proc/self/fd/N points to: a path, or pipe:[...] and socket:[...], which are not)
					String link = Files.readSymbolicLink(Path.of("/proc/self/fd/"+fd)).toString();
					path = link.startsWith("/") ? link : "";
				} catch (IOException | RuntimeException e) {
				}
			}
			lines.add(fd+" "+(status & 3)+" "+path);
		}
		return lines.toArray(new String[0]);
	}

	@Override
	public long [] resourceUsage() {
		long [] values = new long[4];
		Memory ru = new Memory(512);
		ru.clear();
		// (tv_usec is an int on macOS, a long on Linux; RUSAGE_SELF 0, RUSAGE_CHILDREN -1)
		if( C.getrusage(0, ru) == 0 ) {
			values[0] = micros(ru, 0);
			values[1] = micros(ru, 16);
		}
		ru.clear();
		if( C.getrusage(-1, ru) == 0 ) {
			values[2] = micros(ru, 0);
			values[3] = micros(ru, 16);
		}
		return values;
	}

	private long micros(Pointer ru, int offset) {
		long usec = mac ? ru.getInt(offset + 8) : ru.getLong(offset + 8);
		return ru.getLong(offset)*1000000L + usec;
	}

	@Override
	public long [] ids() {
		int n = C.getgroups(0, null);
		if( n < 0 ) {
			n = 0;
		}
		Memory g = new Memory(4L*(n+1));
		g.clear();
		n = C.getgroups(n, g);
		if( n < 0 ) {
			n = 0;
		}
		long [] ret = new long[n+2];
		ret[0] = C.getuid() & 0xffffffffL;
		ret[1] = C.getgid() & 0xffffffffL;
		for(int i = 0; i < n; i++) {
			ret[i+2] = g.getInt(4L*i) & 0xffffffffL;
		}
		return ret;
	}

	@Override
	public String osRelease() {
		// (struct utsname: five fields of 256 bytes on macOS, six of 65 on Linux: sysname, nodename, release)
		Memory u = new Memory(2048);
		u.clear();
		if( C.uname(u) != 0 ) {
			return null;
		}
		return u.getString(mac ? 512 : 130);
	}

	@Override
	public int setOpenFilesLimit(long limit) {
		final int RLIMIT_NOFILE = mac ? 8 : 7;
		final long RLIM_INFINITY = mac ? Long.MAX_VALUE : -1L;
		Memory r = new Memory(16);
		r.clear();
		if( C.getrlimit(RLIMIT_NOFILE, r) != 0 ) {
			return -Native.getLastError();
		}
		r.setLong(0, limit < 0 ? RLIM_INFINITY : limit);
		return C.setrlimit(RLIMIT_NOFILE, r) == 0 ? 0 : -Native.getLastError();
	}

	/** this process's exec path, argv and environment as it was started (macOS), or null */
	private byte [] procArgs() {
		int [] mib = { 1 /* CTL_KERN */, 49 /* KERN_PROCARGS2 */, (int) ProcessHandle.current().pid() };
		Memory size = new Memory(8);
		size.setLong(0, 0);
		if( Mac.sysctl(mib, 3, null, size, null, 0) != 0 || size.getLong(0) < 4 ) {
			return null;
		}
		Memory buf = new Memory(size.getLong(0));
		if( Mac.sysctl(mib, 3, buf, size, null, 0) != 0 ) {
			return null;
		}
		return buf.getByteArray(0, (int) size.getLong(0));
	}

	private static byte [] proc(String file) {
		try {
			return Files.readAllBytes(Path.of(file));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static int skipString(byte [] b, int p) {
		while( p < b.length && b[p] != 0 ) {
			p++;
		}
		return p;
	}

	@Override
	public String argv0() {
		if( !mac ) {
			byte [] b = proc("/proc/self/cmdline");
			if( b == null || b.length == 0 ) {
				return null;
			}
			return new String(b, 0, skipString(b, 0), StandardCharsets.UTF_8);
		}
		byte [] b = procArgs();
		if( b == null ) {
			return null;
		}
		// the exec path, then NULs up to argv[0]
		int p = skipString(b, 4);
		while( p < b.length && b[p] == 0 ) {
			p++;
		}
		return p < b.length ? new String(b, p, skipString(b, p) - p, StandardCharsets.UTF_8) : null;
	}

	@Override
	public int startedWith(String name) {
		byte [] b;
		int p;
		if( mac ) {
			b = procArgs();
			if( b == null ) {
				return -1;
			}
			int argc = (b[0] & 0xff) | (b[1] & 0xff) << 8 | (b[2] & 0xff) << 16 | (b[3] & 0xff) << 24;
			// the exec path, then NULs, then argc arguments
			p = skipString(b, 4);
			while( p < b.length && b[p] == 0 ) {
				p++;
			}
			for(int i = 0; i < argc && p < b.length; i++) {
				p = skipString(b, p) + 1;
			}
		} else {
			b = proc("/proc/self/environ");
			if( b == null ) {
				return -1;
			}
			p = 0;
		}
		byte [] key = (name+"=").getBytes(StandardCharsets.UTF_8);
		while( p < b.length && b[p] != 0 ) {
			boolean match = p + key.length <= b.length;
			for(int i = 0; match && i < key.length; i++) {
				match = b[p+i] == key[i];
			}
			if( match ) {
				return 1;
			}
			p = skipString(b, p) + 1;
		}
		return 0;
	}
}
