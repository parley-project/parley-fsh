package us.bringardner.fsh.jna;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Starting programs and passing descriptors through libc, for macOS and Linux: capability C of
 * NATIVE-CONTRACT.md, as macos/ and linux/NativeKeyboard.cpp do it.
 *
 * posix_spawn takes its file actions and attributes as opaque objects that the C library's own
 * functions fill in, so no structure layout is needed for them (they are given more room than any
 * C library uses). Only sendmsg's msghdr and cmsghdr are laid out here, with the offsets of each
 * system's headers below.
 *
 * It is all in a class of its own: JNA looks up every function of a class as it loads it, and a
 * C library that lacks one (an old musl's posix_spawn_file_actions_addchdir_np) must only make
 * this unavailable, not the keyboard and the terminal.
 */
final class UnixSpawn {

	static final class L {
		static {
			Native.register("c");
		}
		static native int posix_spawn_file_actions_init(Pointer fa);
		static native int posix_spawn_file_actions_destroy(Pointer fa);
		static native int posix_spawn_file_actions_adddup2(Pointer fa, int fd, int newfd);
		static native int posix_spawn_file_actions_addclose(Pointer fa, int fd);
		static native int posix_spawn_file_actions_addchdir_np(Pointer fa, Pointer path);
		static native int posix_spawnattr_init(Pointer attr);
		static native int posix_spawnattr_destroy(Pointer attr);
		static native int posix_spawnattr_setflags(Pointer attr, short flags);
		static native int posix_spawnattr_setsigmask(Pointer attr, Pointer mask);
		static native int posix_spawnattr_setpgroup(Pointer attr, int group);
		static native int posix_spawn(Pointer pid, Pointer path, Pointer fa, Pointer attr, Pointer argv, Pointer envp);
		static native int sigemptyset(Pointer set);
		static native int pipe(Pointer fds);
		static native int socketpair(int domain, int type, int protocol, Pointer fds);
		static native int setsockopt(int fd, int level, int name, Pointer value, int len);
		static native long sendmsg(int fd, Pointer msg, int flags);
		static native long send(int fd, Pointer buf, long len, int flags);
		static native long read(int fd, Pointer buf, long len);
		static native long write(int fd, Pointer buf, long len);
		static native int close(int fd);
		static native int waitpid(int pid, Pointer status, int options);
		static native int kill(int pid, int sig);
		static native String strerror(int err);
		static boolean ok() {
			return true;
		}
	}

	/** the variadic functions, through an interface (see UnixOps.V) */
	interface V extends Library {
		int fcntl(int fd, int cmd, Object... args);
		int open(byte [] path, int flags, Object... mode);
		int ioctl(int fd, NativeLong request, Object... args);
	}

	private static final int EINTR = 4;
	private static final int EINVAL = 22;
	private static final int EPIPE = 32;
	private static final int FD_CLOEXEC = 1;
	private static final int F_GETFD = 1;
	private static final int F_SETFD = 2;

	// posix_spawn's flags (<spawn.h>)
	private static final short POSIX_SPAWN_SETPGROUP = 0x2;
	private static final short POSIX_SPAWN_SETSIGMASK = 0x8;
	private static final short MAC_START_SUSPENDED = 0x80;
	private static final short MAC_CLOEXEC_DEFAULT = 0x4000;

	private final boolean mac;
	private final V v;
	private final int fDupfdCloexec;
	private final int oCloexec;
	private final int oCreat;
	private final int oTrunc;
	private final int oAppend;
	private final long fionread;
	private final int msgNosignal;
	/**
	 * posix_spawn_file_actions_addclosefrom_np (glibc 2.34 and later), or null: what macOS's
	 * POSIX_SPAWN_CLOEXEC_DEFAULT does, in the child. Without it the descriptors to close are
	 * those open when the program is started, and one another thread opens in the meantime (the
	 * named pipe of the next program in a pipeline) reaches the program: it then holds the pipe
	 * open and the reader never sees its end.
	 */
	private final Function closeFrom;

	UnixSpawn(boolean mac) {
		// (fails here if the C library lacks one of the functions)
		L.ok();
		this.mac = mac;
		v = Native.load("c", V.class);
		fDupfdCloexec = mac ? 67 : 1030;
		oCloexec = mac ? 0x1000000 : 0x80000;
		oCreat = mac ? 0x200 : 0x40;
		oTrunc = mac ? 0x400 : 0x200;
		oAppend = mac ? 0x8 : 0x400;
		fionread = mac ? 0x4004667fL : 0x541BL;
		// (macOS sets SO_NOSIGPIPE on the socket; Linux sends with MSG_NOSIGNAL)
		msgNosignal = mac ? 0 : 0x4000;
		Function f = null;
		if( !mac ) {
			try {
				f = NativeLibrary.getInstance("c").getFunction("posix_spawn_file_actions_addclosefrom_np");
			} catch (UnsatisfiedLinkError e) {
				// (an older C library: closed one by one)
			}
		}
		closeFrom = f;
	}

	private static int errno() {
		return Native.getLastError();
	}

	/** s as a NUL-terminated C string */
	private static Memory cstring(byte [] s, List<Memory> keep) {
		Memory m = new Memory(s.length + 1L);
		m.write(0, s, 0, s.length);
		m.setByte(s.length, (byte) 0);
		keep.add(m);
		return m;
	}

	/** a NULL-terminated array of C strings */
	private static Memory cstrings(byte [][] a, List<Memory> keep) {
		int n = a == null ? 0 : a.length;
		Memory arr = new Memory(8L * (n + 1));
		arr.clear();
		for(int i = 0; i < n; i++) {
			arr.setPointer(8L * i, cstring(a[i], keep));
		}
		keep.add(arr);
		return arr;
	}

	/** fd is one of the program's (fds: pairs of its number and the shell's) */
	private static boolean isTarget(int [] fds, int fd) {
		for(int i = 0; fds != null && i + 1 < fds.length; i += 2) {
			if( fds[i] == fd ) {
				return true;
			}
		}
		return false;
	}

	/** the descriptors open here without close-on-exec, which a program must not get (Linux) */
	private int [] othersOpen(int [] fds) {
		String [] names = new File("/proc/self/fd").list();
		if( names == null ) {
			return new int[0];
		}
		List<Integer> close = new ArrayList<>();
		for(String name : names) {
			int fd;
			try {
				fd = Integer.parseInt(name);
			} catch (NumberFormatException e) {
				continue;
			}
			boolean kept = false;
			for(int i = 0; i + 1 < fds.length; i += 2) {
				kept |= fds[i] == fd;
			}
			// (a closed one: the listing's own descriptor)
			int flags = v.fcntl(fd, F_GETFD);
			if( kept || flags < 0 || (flags & FD_CLOEXEC) != 0 ) {
				continue;
			}
			close.add(fd);
		}
		return close.stream().mapToInt(Integer::intValue).toArray();
	}

	int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, int pgroup, boolean suspended) {
		int nfds = fds == null ? 0 : fds.length;
		// (each of the shell's descriptors copied above all the child's numbers first, so that one
		// mapping does not overwrite another's source)
		int top = 2;
		for(int i = 0; i + 1 < nfds; i += 2) {
			top = Math.max(top, fds[i]);
		}
		int [] temps = new int[nfds / 2];
		int err = 0;
		for(int i = 0; i + 1 < nfds; i += 2) {
			int t = v.fcntl(fds[i+1], fDupfdCloexec, top + 1);
			if( t < 0 ) {
				err = errno();
				temps[i/2] = -1;
				continue;
			}
			temps[i/2] = t;
		}
		List<Memory> keep = new ArrayList<>();
		Memory pid = new Memory(8);
		pid.clear();
		Memory fa = new Memory(1024);
		Memory at = new Memory(1024);
		fa.clear();
		at.clear();
		boolean haveFa = false;
		boolean haveAt = false;
		try {
			if( err == 0 ) {
				err = L.posix_spawn_file_actions_init(fa);
				haveFa = err == 0;
			}
			if( err == 0 ) {
				err = L.posix_spawnattr_init(at);
				haveAt = err == 0;
			}
			if( err == 0 ) {
				// (handlers are reset by exec itself; those the shell ignores stay ignored, as in bash)
				short flags = POSIX_SPAWN_SETSIGMASK;
				Memory none = new Memory(256);
				none.clear();
				L.sigemptyset(none);
				L.posix_spawnattr_setsigmask(at, none);
				if( pgroup >= 0 ) {
					flags |= POSIX_SPAWN_SETPGROUP;
					L.posix_spawnattr_setpgroup(at, pgroup);
				}
				if( mac ) {
					// (all other descriptors closed; a suspended program waits for SIGCONT)
					flags |= MAC_CLOEXEC_DEFAULT;
					if( suspended ) {
						flags |= MAC_START_SUSPENDED;
					}
				}
				L.posix_spawnattr_setflags(at, flags);
				for(int i = 0; i + 1 < nfds; i += 2) {
					L.posix_spawn_file_actions_adddup2(fa, temps[i/2], fds[i]);
				}
				if( !mac && closeFrom != null ) {
					// (no CLOEXEC_DEFAULT: in the child, after the copies, every descriptor that
					// isn't one of the program's is closed, those opened since too; a close of one
					// that isn't open is not an error)
					for(int fd = 3; fd <= top; fd++) {
						if( !isTarget(fds, fd)) {
							L.posix_spawn_file_actions_addclose(fa, fd);
						}
					}
					closeFrom.invokeInt(new Object[] {fa, top + 1});
				} else if( !mac ) {
					// (the others open now, one by one)
					for(int fd : othersOpen(fds)) {
						L.posix_spawn_file_actions_addclose(fa, fd);
					}
				}
				if( dir != null ) {
					L.posix_spawn_file_actions_addchdir_np(fa, cstring(dir, keep));
				}
				err = L.posix_spawn(pid, cstring(path, keep), fa, at, cstrings(argv, keep), cstrings(envp, keep));
			}
		} finally {
			for(int t : temps) {
				if( t >= 0 ) {
					L.close(t);
				}
			}
			if( haveFa ) {
				L.posix_spawn_file_actions_destroy(fa);
			}
			if( haveAt ) {
				L.posix_spawnattr_destroy(at);
			}
		}
		return err == 0 ? pid.getInt(0) : -err;
	}

	int [] pipe() {
		Memory p = new Memory(8);
		if( L.pipe(p) != 0 ) {
			return null;
		}
		int [] ret = { p.getInt(0), p.getInt(4) };
		v.fcntl(ret[0], F_SETFD, FD_CLOEXEC);
		v.fcntl(ret[1], F_SETFD, FD_CLOEXEC);
		return ret;
	}

	int open(byte [] path, int how) {
		int flags = how == 'w' ? 1 | oCreat | oTrunc : how == 'a' ? 1 | oCreat | oAppend : 0;
		byte [] z = java.util.Arrays.copyOf(path, path.length + 1);
		int fd = v.open(z, flags | oCloexec, 0666);
		return fd >= 0 ? fd : -errno();
	}

	void close(int fd) {
		L.close(fd);
	}

	int waitExit(int pid) {
		Memory status = new Memory(4);
		status.setInt(0, 0);
		while( L.waitpid(pid, status, 0) < 0 ) {
			if( errno() != EINTR ) {
				return -1;
			}
		}
		return status.getInt(0);
	}

	int kill(int pid, int sig) {
		return L.kill(pid, sig) == 0 ? 0 : -errno();
	}

	String strerror(int err) {
		return L.strerror(err);
	}

	int [] socketPair() {
		Memory p = new Memory(8);
		// (AF_UNIX, SOCK_STREAM)
		if( L.socketpair(1, 1, 0, p) != 0 ) {
			return null;
		}
		int [] ret = { p.getInt(0), p.getInt(4) };
		v.fcntl(ret[0], F_SETFD, FD_CLOEXEC);
		v.fcntl(ret[1], F_SETFD, FD_CLOEXEC);
		if( mac ) {
			// SOL_SOCKET 0xffff, SO_NOSIGPIPE 0x1022
			Memory on = new Memory(4);
			on.setInt(0, 1);
			L.setsockopt(ret[0], 0xffff, 0x1022, on, 4);
		}
		return ret;
	}

	int sendWithDescriptors(int sock, byte [] payload, int [] fds) {
		int n = payload.length;
		int nfds = fds == null ? 0 : fds.length;
		if( nfds > 16 ) {
			return -EINVAL;
		}
		Memory buf = new Memory(n + 4L);
		buf.setByte(0, (byte) (n >>> 24));
		buf.setByte(1, (byte) (n >>> 16));
		buf.setByte(2, (byte) (n >>> 8));
		buf.setByte(3, (byte) n);
		buf.write(4, payload, 0, n);

		// struct iovec { void *iov_base; size_t iov_len; }
		Memory iov = new Memory(16);
		iov.setPointer(0, buf);
		iov.setLong(8, n + 4L);

		// struct msghdr (LP64): msg_name 0, msg_namelen 8, msg_iov 16, msg_iovlen 24 (an int with
		// padding on macOS and musl, a size_t on glibc: little-endian either way), msg_control 32,
		// msg_controllen 40 (a socklen_t with padding on macOS and musl), msg_flags 48
		Memory msg = new Memory(64);
		msg.clear();
		msg.setPointer(16, iov);
		msg.setLong(24, 1);
		Memory control = null;
		if( nfds > 0 ) {
			// struct cmsghdr: cmsg_len, cmsg_level (SOL_SOCKET), cmsg_type (SCM_RIGHTS 1), then the
			// data: at 12 on macOS (4-byte alignment), at 16 on Linux (8)
			int header = mac ? 12 : 16;
			int space = header + (mac ? (nfds * 4 + 3) & ~3 : (nfds * 4 + 7) & ~7);
			control = new Memory(space);
			control.clear();
			control.setLong(0, header + nfds * 4L);
			control.setInt(mac ? 4 : 8, mac ? 0xffff : 1);
			control.setInt(mac ? 8 : 12, 1);
			for(int i = 0; i < nfds; i++) {
				control.setInt(header + 4L * i, fds[i]);
			}
			msg.setPointer(32, control);
			msg.setLong(40, space);
		}
		long off = 0;
		int err = 0;
		while( off < n + 4L ) {
			long w = off == 0 ? L.sendmsg(sock, msg, msgNosignal)
					: L.send(sock, buf.share(off), n + 4L - off, msgNosignal);
			if( w < 0 && errno() == EINTR ) {
				continue;
			}
			if( w <= 0 ) {
				err = w < 0 ? errno() : EPIPE;
				break;
			}
			off += w;
		}
		return -err;
	}

	/** a descriptor read through read(2) (closing it closes the descriptor) */
	final class In extends InputStream {
		private final int fd;
		private final Memory buf = new Memory(8192);
		private boolean closed;

		In(int fd) {
			this.fd = fd;
		}

		@Override
		public int read() throws IOException {
			byte [] one = new byte[1];
			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public synchronized int read(byte [] b, int off, int len) throws IOException {
			if( closed ) {
				throw new IOException("Stream Closed");
			}
			if( len == 0 ) {
				return 0;
			}
			int want = (int) Math.min(len, buf.size());
			while( true ) {
				long n = L.read(fd, buf, want);
				if( n < 0 ) {
					int e = errno();
					if( e == EINTR ) {
						continue;
					}
					throw new IOException(L.strerror(e));
				}
				if( n == 0 ) {
					return -1;
				}
				buf.read(0, b, off, (int) n);
				return (int) n;
			}
		}

		@Override
		public int available() throws IOException {
			if( closed ) {
				throw new IOException("Stream Closed");
			}
			Memory n = new Memory(4);
			n.setInt(0, 0);
			return v.ioctl(fd, new NativeLong(fionread), n) == 0 ? Math.max(0, n.getInt(0)) : 0;
		}

		@Override
		public void close() {
			synchronized (this) {
				if( !closed ) {
					closed = true;
					L.close(fd);
				}
			}
		}
	}

	/** a descriptor written through write(2) (closing it closes the descriptor) */
	final class Out extends OutputStream {
		private final int fd;
		private final Memory buf = new Memory(8192);
		private boolean closed;

		Out(int fd) {
			this.fd = fd;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public synchronized void write(byte [] b, int off, int len) throws IOException {
			if( closed ) {
				throw new IOException("Stream Closed");
			}
			while( len > 0 ) {
				int chunk = (int) Math.min(len, buf.size());
				buf.write(0, b, off, chunk);
				long n = L.write(fd, buf, chunk);
				if( n < 0 ) {
					int e = errno();
					if( e == EINTR ) {
						continue;
					}
					// (EPIPE: the program is gone, as Java's own pipe says it)
					throw new IOException(e == EPIPE ? "Broken pipe" : L.strerror(e));
				}
				off += (int) n;
				len -= (int) n;
			}
		}

		@Override
		public void close() {
			synchronized (this) {
				if( !closed ) {
					closed = true;
					L.close(fd);
				}
			}
		}
	}
}
