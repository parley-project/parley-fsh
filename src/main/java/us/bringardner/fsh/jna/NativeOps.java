package us.bringardner.fsh.jna;

/**
 * What NativeKeyboard asks of the operating system, done through JNA instead of the JNI library
 * (macos/, linux/ and windows/ NativeKeyboard.cpp): the same functions, with the same results
 * (NATIVE-CONTRACT.md).
 *
 * The functions a platform has no equivalent for throw UnsatisfiedLinkError, as the JNI library's
 * missing functions do (the Windows library has no process groups): the Java side handles that
 * already.
 *
 * Starting programs and passing descriptors (capability C of the contract: spawn, pipe, open,
 * close, waitExit, kill, strerror, socketPair, sendWithDescriptors, the descriptor streams) are
 * all or none: canSpawn() says which.
 */
public abstract class NativeOps {

	// getChar() results that are not keys (must match NativeKeyboard.java)
	public static final int KEY_EOF = -1;
	public static final int KEY_NONE = -2;

	// special keys (must match NativeKeyboard.java)
	public static final int KEY_UP = 500;
	public static final int KEY_DN = 501;
	public static final int KEY_RT = 502;
	public static final int KEY_LF = 503;
	public static final int KEY_HOME = 504;
	public static final int KEY_PAGE_UP = 505;
	public static final int KEY_PAGE_DOWN = 506;
	public static final int KEY_DELETE = 507;
	public static final int KEY_END = 508;

	/** why create() gave none (JNA or its native library is not there), or null */
	public static volatile String failure;

	/** the platform's implementation, or null if there is none or JNA cannot be loaded */
	public static NativeOps create() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase();
			if( os.startsWith("windows") ) {
				return new WindowsOps();
			}
			if( os.startsWith("mac") || os.startsWith("darwin") ) {
				return new UnixOps(true);
			}
			if( os.startsWith("linux") ) {
				return new UnixOps(false);
			}
			failure = "no JNA layer for "+System.getProperty("os.name");
		} catch (Throwable e) {
			// (JNA or its native library is not there)
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			failure = cause.getClass().getSimpleName()+": "+cause.getMessage();
			if( Boolean.getBoolean("parley.fsh.native.debug") ) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private static UnsatisfiedLinkError no(String what) {
		return new UnsatisfiedLinkError(what+" is not done through JNA");
	}

	// the keyboard and the terminal

	/** the next byte (0-255), a special key, KEY_NONE if nothing was typed within a second, or KEY_EOF */
	public abstract int getChar();

	/** the number of bytes that can be read without blocking */
	public abstract int ready();

	/** on: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys instead of signalling the shell */
	public abstract void setSignalKeys(boolean on);

	/** on: a program gets the terminal in its own mode; off: the shell has it back */
	public abstract void setProgramMode(boolean on);

	/** standard input and output are a terminal */
	public abstract boolean isTerminal();

	/** standard input is a terminal */
	public abstract boolean isInputTerminal();

	/** the name of standard input's terminal, or null */
	public abstract String ttyName();

	/** standard input: 1 a pipe or socket, 2 a file, 0 something else */
	public abstract int inputKind();

	/** the descriptors from 3 to 255 open without close-on-exec: "fd mode path" each */
	public abstract String [] openDescriptors();

	/** {self user, self system, children user, children system} in microseconds */
	public abstract long [] resourceUsage();

	/** the terminal's width in columns (standard output's), or 0 */
	public abstract int columns();

	/** descriptor fd is a terminal */
	public abstract boolean isatty(int fd);

	// starting programs (capability C: all or none)

	/** why canSpawn() is false although this works (a C library without a function), or null */
	public String spawnProblem() {
		return null;
	}

	/** this platform starts programs and passes descriptors (all of the following) */
	public boolean canSpawn() {
		return false;
	}

	/** see NativeKeyboard.spawn0 */
	public int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, int pgroup, boolean suspended) {
		throw no("spawn");
	}

	/** {read, write}, both close-on-exec; null on failure */
	public int [] pipe() {
		throw no("pipe");
	}

	/** open path to read ('r'), write ('w') or append ('a'), close-on-exec: the descriptor or -errno */
	public int open(byte [] path, int how) {
		throw no("open");
	}

	public void close(int fd) {
		throw no("close");
	}

	/** wait until child pid ends: its wait status, or -1 if it is not a child */
	public int waitExit(int pid) {
		throw no("waitExit");
	}

	/** kill(2): 0 or -errno */
	public int kill(int pid, int sig) {
		throw no("kill");
	}

	public String strerror(int err) {
		throw no("strerror");
	}

	/** a connected pair of sockets, both close-on-exec; null on failure */
	public int [] socketPair() {
		throw no("socketPair");
	}

	/** send payload (after its length, 4 bytes in network order) with descriptors fds: 0 or -errno */
	public int sendWithDescriptors(int sock, byte [] payload, int [] fds) {
		throw no("sendWithDescriptors");
	}

	/** a stream that reads descriptor fd (closing it closes the descriptor) */
	public java.io.InputStream inputStream(int fd) {
		throw no("inputStream");
	}

	/** a stream that writes descriptor fd (closing it closes the descriptor) */
	public java.io.OutputStream outputStream(int fd) {
		throw no("outputStream");
	}

	// job control

	public void giveTerminalTo(long group) {
		throw no("giveTerminalTo");
	}

	public void setProcessGroup(long pid, long group) {
		throw no("setProcessGroup");
	}

	public int stoppedBy(long pid) {
		throw no("stoppedBy");
	}

	public void ttyTo(int group) {
		throw no("ttyTo");
	}

	public long pgrp() {
		throw no("pgrp");
	}

	// the process

	/** {uid, gid, the supplementary groups...} */
	public long [] ids() {
		throw no("ids");
	}

	/** the open-files soft limit (-1: unlimited): 0 or -errno */
	public int setOpenFilesLimit(long limit) {
		throw no("setOpenFilesLimit");
	}

	/** 1 if name was in the environment the process was started with, 0 if not, -1 if not known */
	public int startedWith(String name) {
		throw no("startedWith");
	}

	/** this process's argv[0], or null */
	public String argv0() {
		throw no("argv0");
	}

	/** the kernel's release (uname -r), or null */
	public String osRelease() {
		throw no("osRelease");
	}

	/**
	 * The special key of a terminal's escape sequence (ESC [ A ...) that is all of buf[0..cnt),
	 * or 0.
	 */
	protected static int escapeKey(byte [] buf, int cnt) {
		if( cnt == 3 && buf[0] == 27 && buf[1] == '[' ) {
			switch( buf[2] ) {
			case 'A': return KEY_UP;
			case 'B': return KEY_DN;
			case 'C': return KEY_RT;
			case 'D': return KEY_LF;
			case 'H': return KEY_HOME;
			case 'F': return KEY_END;
			}
		} else if( cnt == 4 && buf[0] == 27 && buf[1] == '[' && buf[3] == '~' ) {
			switch( buf[2] ) {
			case '3': return KEY_DELETE;
			case '5': return KEY_PAGE_UP;
			case '6': return KEY_PAGE_DOWN;
			}
		}
		return 0;
	}
}
