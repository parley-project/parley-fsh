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
 * The process through libc, for macOS and Linux (glibc and musl, 64-bit); the keyboard and the
 * terminal are parley-term's (us.bringardner.parley.term.tty.jna).
 *
 * What differs between the two (the layout of siginfo_t, rusage and utsname, the numbers
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
	private static final int SIGTTOU = 22;

	private final boolean mac;
	private final V v;
	/** starting programs and passing descriptors (null: this C library lacks something for it) */
	private final UnixSpawn spawn;
	private final String spawnProblem;

	// pthread_sigmask's SIG_BLOCK and SIG_SETMASK
	private final int sigBlock;
	private final int sigSetmask;

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
		sigBlock = mac ? 1 : 0;
		sigSetmask = mac ? 3 : 2;
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
