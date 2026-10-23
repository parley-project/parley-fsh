package us.bringardner.fsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.term.line.LineEditor;
import us.bringardner.parley.term.tty.Keyboard;
import us.bringardner.parley.term.tty.TtyOps;
import us.bringardner.parley.term.tty.jna.JnaTty;

/**
 * fsh's keyboard and native functions. The keyboard and the terminal are parley-term's
 * {@link Keyboard} (raw mode, keys, Ctrl-C and Ctrl-Z, lending the terminal, the size); this
 * class gives it fsh's JNI library (libnativekeyboard) when that is loaded, adds the shell's line
 * reading (the line editor with history and completion, read's own reader), and starts programs,
 * passes descriptors and works process groups, through the JNI library or JNA.
 */
public class NativeKeyboard extends Keyboard implements KeyboardReader, InteractiveInput 	{
	/**
	 * @return the next byte (0-255), a special key (UP ...), KEY_NONE if nothing was typed within a second, or KEY_EOF.
	 */
	private native int getChar();
	/**
	 * @return the number of bytes that can be read without blocking.
	 */
	private native int ready();

	/**
	 * on: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys instead of signalling the shell (the
	 * interactive shell gives them to its foreground job).
	 */
	private native void setSignalKeys(boolean on);

	/** on: a program gets the terminal in its own mode; off: the shell has it back */
	private native void setProgramMode(boolean on);

	/** standard input and output are a terminal */
	private native boolean isTerminal();

	/** standard input is a terminal */
	private native boolean isInputTerminal0();

	private native String ttyName0();

	/** the terminal's name without /dev/ (ttys003), as \\l shows it; "tty" if there is none */
	public static String ttyName() {
		return Keyboard.ttyName();
	}

	/** standard input: 1 a pipe or socket, 2 a file, 0 something else */
	private native int inputKind0();

	/**
	 * The descriptors from 3 to 255 that are open and not closed when a program starts (the JVM
	 * opens most of its own with close-on-exec): "fd mode path" each (mode 0 read, 1 write, 2
	 * both; path empty if it is not known).
	 */
	private native String [] openDescriptors0();

	/**
	 * The descriptors the process was given (fsh script 3&lt;file): {fd, mode} in turn, the JVM's
	 * own (its modules and the class path's jars, open too) left out.
	 */
	/** the descriptors open as fsh started (see openAtStart), or null */
	private static volatile String [] openAtStart;

	/**
	 * Note the descriptors open now, at the start, before the runtime opens its own (a native
	 * image's signal pipe and /dev/random, at 3 and up): inheritedDescriptors are among them.
	 */
	public static void noteOpenAtStart() {
		if( availible && openAtStart == null ) {
			try {
				openAtStart = new NativeKeyboard().opsOpenDescriptors0();
			} catch (UnsatisfiedLinkError e) {
			}
		}
	}

	public static int [] inheritedDescriptors() {
		if( !availible ) {
			return new int[0];
		}
		String [] open = openAtStart;
		if( open == null ) {
			try {
				open = new NativeKeyboard().opsOpenDescriptors0();
			} catch (UnsatisfiedLinkError e) {
				// an older library
				return new int[0];
			}
		}
		java.util.Set<String> jvm = new java.util.HashSet<>();
		for(String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
			if( !entry.isEmpty()) {
				jvm.add(new java.io.File(entry).getAbsolutePath());
			}
		}
		String home = System.getProperty("java.home", "/nonexistent");
		java.util.List<Integer> ret = new java.util.ArrayList<>();
		for(String d : open) {
			String [] f = d.split(" ", 3);
			String path = f.length > 2 ? f[2] : "";
			if( path.startsWith(home) || jvm.contains(path) || path.endsWith(".jar") || path.endsWith(".jmod")) {
				continue;
			}
			ret.add(Integer.parseInt(f[0]));
			ret.add(Integer.parseInt(f[1]));
		}
		return ret.stream().mapToInt(Integer::intValue).toArray();
	}

	/** getrusage: {the shell's user, system, its children's user, system} in microseconds */
	private native long [] resourceUsage0();

	/**
	 * The CPU time used (microseconds): {the shell's user, system, the user and system time of
	 * the programs it ran that have ended}, or null without this library.
	 */
	public static long [] resourceUsage() {
		if( availible ) {
			try {
				return new NativeKeyboard().opsResourceUsage0();
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return null;
	}

	/**
	 * The process's standard input is a pipe or a file: commands are read from it (as a script)
	 * and shared with the programs. (Without this library: there is no console and no screen.)
	 */
	public static boolean inputIsPipeOrFile() {
		int kind = inputKind();
		if( kind >= 0 ) {
			return kind != 0;
		}
		return System.console() == null && java.awt.GraphicsEnvironment.isHeadless();
	}

	/** standard input: 1 a pipe or socket, 2 a file, 0 something else, -1 not known */
	public static int inputKind() {
		return Keyboard.inputKind();
	}

	/** the process's standard input is a terminal (without a terminal library: there is a console) */
	public static boolean inputIsTerminal() {
		return Keyboard.inputIsTerminal();
	}

	private native void giveTerminalTo(long group);

	private native void setProcessGroup(long pid, long group);

	private native int stoppedBy(long pid);

	// starting programs without Java's jspawnhelper (see exec.NativeProcess)

	/**
	 * Start path with argv and envp (bytes, as given) in dir (null: here); fds are pairs of the
	 * program's descriptor and the shell's it is to be (all others closed); pgroup: the process
	 * group (-1: the shell's, 0: a new one); suspended: it does not run until it gets SIGCONT.
	 * @return its process id, or -errno
	 */
	public static native int spawn0(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, int pgroup, boolean suspended);

	/** the terminal (/dev/tty) is process group group's (0: the shell's) */
	public static native void ttyTo0(int group);

	/** {read, write}, both close-on-exec; null on failure */
	public static native int [] pipe0();

	/** open path to read ('r'), write ('w': created or truncated) or append ('a'), close-on-exec: the descriptor or -errno */
	public static native int open0(byte [] path, int how);

	public static native void close0(int fd);

	/** wait until child pid ends: its wait status, or -1 if it is not a child */
	public static native int waitExit0(int pid);

	/** fd as a FileDescriptor (closing a stream on it closes it) */
	public static native java.io.FileDescriptor fd0(int fd);

	/** kill(2): 0 or -errno */
	public static native int kill0(int pid, int sig);

	public static native String strerror0(int err);

	/** descriptor fd of this process is a terminal */
	public static native boolean isatty0(int fd);

	/** descriptor fd of this process is a terminal (false where that cannot be told) */
	public static boolean isatty(int fd) {
		return Keyboard.isatty(fd);
	}


	/** set the open-files soft limit (-1: unlimited): 0 or -errno */
	public static native int setOpenFilesLimit0(long limit);

	/** this process's process group */
	public static native long pgrp0();

	/** {uid, gid, the supplementary groups...} of this process */
	public static native long [] ids0();

	/** 1 if name was in the environment this process was started with, 0 if not, -1 if not known */
	public static native int startedWith0(String name);

	/** this process's argv[0] (as it was started, not its path), or null */
	public static native String argv0();

	/** the kernel's release (uname -r), or null */
	public static native String osRelease0();

	/** a connected pair of sockets, both close-on-exec; null on failure */
	public static native int [] socketPair0();

	/** send payload (after its length, 4 bytes in network order) with descriptors fds: 0 or -errno */
	public static native int sendWithDescriptors0(int sock, byte [] payload, int [] fds);

	/** {the parley.fsh.exec property it was found with, the helper ("" if none)} */
	private static volatile String [] helper;
	private static volatile Boolean groups;

	/**
	 * fshexec, the helper that runs a program as bash would (in a process group, on the shell's
	 * file): next to this library, or the parley.fsh.exec property. Null if there is none.
	 */
	public static String helper() {
		String prop = System.getProperty("parley.fsh.exec");
		String [] known = helper;
		if( known == null || !java.util.Objects.equals(prop, known[0])) {
			String found = "";
			java.util.List<String> dirs = new java.util.ArrayList<>();
			if( prop != null ) {
				dirs.add(new java.io.File(prop).getAbsoluteFile().getParent());
			}
			dirs.addAll(java.util.List.of(System.getProperty("java.library.path", "").split(java.io.File.pathSeparator)));
			for(String dir : dirs) {
				java.io.File f = new java.io.File(dir == null ? "." : dir, "fshexec");
				if( f.isFile() && f.canExecute()) {
					found = f.getAbsolutePath();
					break;
				}
			}
			// (the two at once: another thread never sees the property with the old helper)
			known = new String[] {prop, found};
			helper = known;
		}
		return known[1].isEmpty() ? null : known[1];
	}


	/**
	 * The helper, when a job's programs can be a process group that has the terminal (on a
	 * terminal, with this library); else null: they share the shell's process group.
	 */
	public static String groupHelper() {
		if( groups == null ) {
			boolean ok = terminal() && helper() != null;
			if( ok ) {
				try {
					new NativeKeyboard().opsStoppedBy(-1);
				} catch (UnsatisfiedLinkError e) {
					ok = false;
				}
			}
			groups = ok;
		}
		return groups ? helper() : null;
	}

	/** a job's process group gets the terminal (fg) */
	public static void giveTerminal(long group) {
		new NativeKeyboard().opsGiveTerminalTo(group);
	}

	/** put child pid in process group (as the child does itself) */
	public static void processGroup(long pid, long group) {
		new NativeKeyboard().opsSetProcessGroup(pid, group);
	}

	/** the signal that stopped child pid since the last call, or 0 */
	public static int stopSignal(long pid) {
		return new NativeKeyboard().opsStoppedBy(pid);
	}

	/** standard input and output are a terminal (and this class can work it) */
	public static boolean terminal() {
		return Keyboard.terminal();
	}

	/**
	 * A program reads the terminal itself, as under bash: it gets it in the terminal's own mode
	 * (where it echoes, edits lines, and Ctrl-C and Ctrl-Z signal the program), until each user
	 * has given it back.
	 */
	public static void lendTerminal(Object user) {
		Keyboard.lendTerminal(user);
	}

	/** user is done with the terminal (it ended, or was stopped) */
	public static void reclaimTerminal(Object user) {
		Keyboard.reclaimTerminal(user);
	}

	/** a program has the terminal now */
	public static boolean isTerminalLent() {
		return Keyboard.isTerminalLent();
	}

	/** Ctrl-C, Ctrl-Z and Ctrl-\\ as keys (on) or as signals (off, as without a shell) */
	public static void signalKeys(boolean on) {
		Keyboard.signalKeys(on);
	}

	/**
	 * Read what has been typed, without waiting, so Ctrl-C and Ctrl-Z reach the shell while a
	 * job that does not read the keyboard runs; the other keys are kept for whoever reads next.
	 */
	public static void pollTyped() {
		Keyboard.pollTyped();
	}

	// https://espterm.github.io/docs/VT100%20escape%20codes.html

	private  String readLineNative(Console console) throws LineCancelled {
		if( getPrompt() == null ) {
			setPrompt("");
		}

		System.out.print(getPrompt());
		System.out.flush();
		int line = console.history.size();
		List<String> lines = new ArrayList<>();
		StringBuilder buf = new StringBuilder();
		long start = System.currentTimeMillis();
		boolean escaped = false;

		if( editLineText!=null) {
			buf.append(editLineText);
			System.out.print(editLineText);
		}

		int pos = buf.length();
		int key = key();

		while(true) {
			
				if( key == KEY_EOF || (key == CTRL_D && buf.length()==0 && lines.isEmpty())) {
					// end of input: null if nothing was typed
					if( buf.length()==0 && lines.isEmpty()) {
						return null;
					}
					return join(lines, buf);
				}
				if( key == KEY_INTR ) {
					// Ctrl-C stopped the job that is reading
					return null;
				}
				if( key == CTRL_C ) {
					// at the prompt, as bash does
					System.out.print("^C\n");
					System.out.flush();
					throw new LineCancelled();
				}
				if( key == CTRL_Z || key == CTRL_BACKSLASH ) {
					key = key();
					continue;
				}

				if(key>=0 && maxBytes_N<0 && (!escaped && key == getLineTerminator())) {
					StringBuilder tmp = new StringBuilder();
					for(String l : lines) {
						tmp.append(l);
					}
					tmp.append(buf);
					print(((char)key));
					return  tmp.toString();

				} 
				escaped = false;
				switch (key) {
				case KEY_NONE: break;
				case 0: break;

				case PAGE_UP:break;
				case PAGE_DOWN:break;

				case UP:
				case DN:
					if( console.history.size()>0 ) {

						if( key==UP) {
							if(line>0) {
								line --;
							}
						} else {
							if( line < (console.history.size()-1)) {
								line ++;
							}
						}

						if( line >=0 && line < console.history.size()) {
							String tmp = console.history.get(line).command;
							buf = new StringBuilder(tmp);

							print(CLEAR_LINE);
							print(getPrompt());
							print(tmp);
							pos = tmp.length();
						}
					}
					break;

				case LF:
					if( pos >0) {
						print(CURSOR_LEFT);
						pos--;
					}
					break;
				case RT:
					if( pos < buf.length()) {
						print(CURSOR_RIGHT);
						pos++;
					}
					break;
				case HOME:print(CURSOR_HOME);
				pos = 0;
				break;
				case 127:// delete key
					int target = pos-1;
					if( target >= 0 && target <= buf.length()) {
						pos--;
						buf.deleteCharAt(target);
						print(CLEAR_LINE);
						print(getPrompt());
						print(buf.toString());
						for(int i = buf.length(); i > pos; i-- ) {
							print(CURSOR_LEFT);
						}
					}
					break;
				case DELETE:
					if( pos < buf.length()) {
						buf.deleteCharAt(pos);
						print(CLEAR_LINE);
						print(getPrompt());
						print(buf.toString());
						for(int i = buf.length(); i > pos; i-- ) {
							print(CURSOR_LEFT);
						}
					}
					break;
				case END:print(CLEAR_LINE);
				print(getPrompt());
				print(buf.toString());
				pos = buf.length();
				break;

				case '\\':
					// escaped char goes to buffer

					if( honorEscape) {
						escaped = true;
						print(((char)key));
						key = nextKey();
						if( key == KEY_EOF) {
							return join(lines, buf);
						}

						if( key == getLineTerminator()) {
							print(CLEAR_LINE);
							print(buf.toString());					
							lines.add(buf.toString());
							buf.setLength(0);
							pos=0;					
							print(((char)key));
							break;
						} 
					}
					// fall through
				default:	
					if( pos>=buf.length()) {
						buf.append((char)key);
						pos++;
					} else {
						buf.insert(pos++, (char)key);
					}
					print(((char)key));
					// typing starts a new line, history browsing starts again from the end
					line = console.history.size();
					for(int idx=pos; idx < buf.length(); idx++) {
						print(buf.charAt(idx));
					}
					for(int idx=pos; idx < buf.length(); idx++) {
						print(CURSOR_LEFT);
					}
				}
				if(timeout>0 && (System.currentTimeMillis()-start)>=timeout ) {
					key = getLineTerminator();
				} else if(maxBytes_N>0 && maxBytes_N== buf.length() || maxBytes_n>0 && buf.length()>= maxBytes_n) {
					key = getLineTerminator();
				} else {

					key = key();
				}
			}
		

	}

	private void print(char c) {
		if( echo ) {
			System.out.print(c);
		}

	}
	private void print(String obj) {
		if( echo ) {
			System.out.print(obj);
		}

	}
	private void print(char[] obj) {
		if( echo ) {
			System.out.print(obj);
		}

	}


	private static String join(List<String> lines, StringBuilder buf) {
		StringBuilder tmp = new StringBuilder();
		for(String l : lines) {
			tmp.append(l);
		}
		tmp.append(buf);
		return tmp.toString();
	}

	private static Boolean availible=false;

	/** the JNI library (libnativekeyboard) was loaded */
	private static boolean jniLoaded;

	/** why the JNI library was not loaded (see NativeStatus), or null */
	private static String jniProblem;

	/**
	 * The native functions done through JNA (jna.NativeOps), or null: then the JNI library does
	 * them. Starting programs and passing descriptors (spawn ...) are all JNA's when it has them
	 * (spawnOps), else all the JNI library's.
	 *
	 * The parley.fsh.native property chooses: "jni" never uses JNA; "jna" uses it for what it has;
	 * "auto" (the default) uses JNA only where the JNI library is not there (and not in a native
	 * image), so a system the library was not built for still has the keyboard and the terminal, and
	 * one that has it starts as quickly as before.
	 */
	private static final us.bringardner.fsh.jna.NativeOps ops;

	/** the keyboard and terminal are done through parley-term's JNA (not the JNI library) */
	private static final boolean ttyJna;

	public static boolean isAvailible() {
		return availible;
	}

	/** the JNI library, which starts programs and passes descriptors, was loaded */
	public static boolean isJniAvailible() {
		return jniLoaded;
	}

	/** why the JNI library was not loaded, or null (it was, or it was not looked for) */
	public static String jniProblem() {
		return jniProblem;
	}

	/** why JNA is not in use although it was asked for or the library is missing, or null */
	public static String jnaProblem() {
		String p = us.bringardner.fsh.jna.NativeOps.failure;
		return p != null ? p : JnaTty.failure;
	}

	/** why JNA cannot start programs although it does the rest, or null */
	public static String spawnProblem() {
		return ops == null ? null : ops.spawnProblem();
	}

	/** the keyboard and terminal are done through JNA (not the JNI library) */
	public static boolean usesJna() {
		return ttyJna;
	}


	static {
		if( Boolean.getBoolean("parley.fsh.native.debug") && System.getProperty("parley.term.native.debug") == null ) {
			System.setProperty("parley.term.native.debug", "true");
		}
		try {
			System.loadLibrary("nativekeyboard");
			availible = true;
		} catch (Throwable e) {
			availible = false;
			jniProblem = e.getClass().getSimpleName()+": "+e.getMessage();
		}
		jniLoaded = availible;
		us.bringardner.fsh.jna.NativeOps jna = null;
		TtyOps tty = null;
		String mode = System.getProperty("parley.fsh.native", "auto");
		if( !"jni".equals(mode) && ("jna".equals(mode) || !jniLoaded) ) {
			try {
				jna = us.bringardner.fsh.jna.NativeOps.create();
			} catch (Throwable e) {
				// (no JNA, or its library: as without it)
			}
			try {
				tty = JnaTty.create();
			} catch (Throwable e) {
				// (no JNA, or its library: as without it)
			}
		}
		ops = jna;
		ttyJna = tty != null;
		if( tty == null && jniLoaded ) {
			tty = new Jni();
		}
		Keyboard.install(tty);
		availible = jniLoaded || ops != null || tty != null;
	}

	/** The keyboard and the terminal through the JNI library, for parley-term's Keyboard. */
	private static final class Jni implements TtyOps {
		private final NativeKeyboard kb = new NativeKeyboard();
		/** (for rows(), which the JNI library has no call for: JNA's, where it works) */
		private TtyOps size;
		private boolean sizeTried;

		@Override
		public int getChar() {
			return kb.getChar();
		}

		@Override
		public int ready() {
			return kb.ready();
		}

		@Override
		public void setSignalKeys(boolean on) {
			kb.setSignalKeys(on);
		}

		@Override
		public void setProgramMode(boolean on) {
			kb.setProgramMode(on);
		}

		@Override
		public boolean isTerminal() {
			return kb.isTerminal();
		}

		@Override
		public boolean isInputTerminal() {
			return kb.isInputTerminal0();
		}

		@Override
		public String ttyName() {
			return kb.ttyName0();
		}

		@Override
		public int inputKind() {
			return kb.inputKind0();
		}

		@Override
		public int columns() {
			return kb.columns0();
		}

		@Override
		public synchronized int rows() {
			if( !sizeTried ) {
				sizeTried = true;
				// (not on Windows, where making JNA's console changes the console's modes too)
				if( java.io.File.separatorChar == '/' ) {
					try {
						size = JnaTty.create();
					} catch (Throwable e) {
						// (no JNA)
					}
				}
			}
			return size == null ? 0 : size.rows();
		}

		@Override
		public boolean isatty(int fd) {
			return isatty0(fd);
		}
	}

	// the process functions that JNA does when it can, and the JNI library's otherwise

	private String [] opsOpenDescriptors0() {
		return ops != null ? ops.openDescriptors() : openDescriptors0();
	}

	private long [] opsResourceUsage0() {
		return ops != null ? ops.resourceUsage() : resourceUsage0();
	}

	private void opsGiveTerminalTo(long group) {
		if( ops != null ) {
			ops.giveTerminalTo(group);
		} else {
			giveTerminalTo(group);
		}
	}

	private void opsSetProcessGroup(long pid, long group) {
		if( ops != null ) {
			ops.setProcessGroup(pid, group);
		} else {
			setProcessGroup(pid, group);
		}
	}

	private int opsStoppedBy(long pid) {
		return ops != null ? ops.stoppedBy(pid) : stoppedBy(pid);
	}

	/** this process's process group */
	public static long pgrp() {
		return ops != null ? ops.pgrp() : pgrp0();
	}

	/** {uid, gid, the supplementary groups...} of this process */
	public static long [] ids() {
		return ops != null ? ops.ids() : ids0();
	}

	/** the open-files soft limit (-1: unlimited): 0 or -errno */
	public static int setOpenFilesLimit(long limit) {
		return ops != null ? ops.setOpenFilesLimit(limit) : setOpenFilesLimit0(limit);
	}

	/** 1 if name was in the environment this process was started with, 0 if not, -1 if not known */
	public static int startedWith(String name) {
		return ops != null ? ops.startedWith(name) : startedWith0(name);
	}

	/** this process's argv[0] (as it was started, not its path), or null */
	public static String argv() {
		return ops != null ? ops.argv0() : argv0();
	}

	/** the kernel's release (uname -r), or null */
	public static String osRelease() {
		return ops != null ? ops.osRelease() : osRelease0();
	}

	// starting programs and passing descriptors (NATIVE-CONTRACT.md, C): all through JNA, or all
	// through the JNI library

	/** JNA does C (ops has it all): else the JNI library does */
	private static final boolean spawnOps;

	static {
		boolean s = false;
		try {
			s = ops != null && ops.canSpawn();
		} catch (Throwable e) {
		}
		spawnOps = s;
	}

	/**
	 * Programs can be started here with spawn(), and descriptors passed (the capability that
	 * exec.NativeProcess.usable() is): a Unix, and JNA or the JNI library having all of it.
	 */
	public static boolean canSpawn() {
		if( java.io.File.separatorChar != '/' ) {
			return false;
		}
		if( spawnOps ) {
			return true;
		}
		if( !jniLoaded ) {
			return false;
		}
		try {
			// (a library from before it had these has no strerror0)
			strerror0(0);
			return true;
		} catch (UnsatisfiedLinkError e) {
			return false;
		}
	}

	/**
	 * Start path with argv and envp (bytes, as given) in dir (null: here); fds are pairs of the
	 * program's descriptor and the shell's it is to be (all others closed); pgroup: the process
	 * group (-1: the shell's, 0: a new one); suspended: it does not run until it holds the
	 * terminal (macOS: until it gets SIGCONT).
	 * @return its process id, or -errno
	 */
	public static int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, int pgroup, boolean suspended) {
		if( !spawnOps ) {
			return spawn0(path, argv, envp, dir, fds, pgroup, suspended);
		}
		String helper = null;
		if( suspended && pgroup >= 0 && System.getProperty("os.name", "").toLowerCase().startsWith("linux") ) {
			// (no POSIX_SPAWN_START_SUSPENDED on Linux: fshexec takes the terminal before it runs
			// the program, as the JNI library's own fork does; without it the program may run
			// first, and is stopped by SIGTTIN until the shell gives the terminal and continues it)
			helper = helper();
		}
		if( helper == null ) {
			return ops.spawn(path, argv, envp, dir, fds, pgroup, suspended);
		}
		// fshexec -g PGID 1 -0 ARGV0 -- PATH ARGV1...: in the group, the terminal's, then the program
		java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
		java.util.List<byte []> wrapped = new java.util.ArrayList<>();
		wrapped.add(helper.getBytes(utf8));
		wrapped.add("-g".getBytes(utf8));
		wrapped.add(String.valueOf(pgroup).getBytes(utf8));
		wrapped.add("1".getBytes(utf8));
		wrapped.add("-0".getBytes(utf8));
		wrapped.add(argv.length > 0 ? argv[0] : path);
		wrapped.add("--".getBytes(utf8));
		wrapped.add(path);
		for(int k = 1; k < argv.length; k++) {
			wrapped.add(argv[k]);
		}
		// (the group is fshexec's to make: -1 here)
		return ops.spawn(helper.getBytes(utf8), wrapped.toArray(new byte[0][]), envp, dir, fds, -1, false);
	}

	/** {read, write}, both close-on-exec; null on failure */
	public static int [] pipe() {
		return spawnOps ? ops.pipe() : pipe0();
	}

	/** open path to read ('r'), write ('w': created or truncated) or append ('a'), close-on-exec: the descriptor or -errno */
	public static int open(byte [] path, int how) {
		return spawnOps ? ops.open(path, how) : open0(path, how);
	}

	public static void close(int fd) {
		if( spawnOps ) {
			ops.close(fd);
		} else {
			close0(fd);
		}
	}

	/** wait until child pid ends: its wait status, or -1 if it is not a child */
	public static int waitExit(int pid) {
		return spawnOps ? ops.waitExit(pid) : waitExit0(pid);
	}

	/** a stream that reads descriptor fd (closing it closes the descriptor) */
	public static java.io.InputStream inputStream(int fd) {
		return spawnOps ? ops.inputStream(fd) : new java.io.FileInputStream(fd0(fd));
	}

	/** a stream that writes descriptor fd (closing it closes the descriptor) */
	public static java.io.OutputStream outputStream(int fd) {
		return spawnOps ? ops.outputStream(fd) : new java.io.FileOutputStream(fd0(fd));
	}

	/** a connected pair of sockets, both close-on-exec; null on failure */
	public static int [] socketPair() {
		return spawnOps ? ops.socketPair() : socketPair0();
	}

	/** send payload (after its length, 4 bytes in network order) with descriptors fds: 0 or -errno */
	public static int sendWithDescriptors(int sock, byte [] payload, int [] fds) {
		return spawnOps ? ops.sendWithDescriptors(sock, payload, fds) : sendWithDescriptors0(sock, payload, fds);
	}

	/** kill(2): 0 or -errno */
	public static int kill(int pid, int sig) {
		return spawnOps ? ops.kill(pid, sig) : kill0(pid, sig);
	}

	public static String strerror(int err) {
		return spawnOps ? ops.strerror(err) : strerror0(err);
	}

	/** the terminal (/dev/tty) is process group group's (0: the shell's) */
	public static void ttyTo(int group) {
		if( ops != null ) {
			ops.ttyTo(group);
		} else {
			ttyTo0(group);
		}
	}

	public static final char [] CLEAR_LINE = {27,'[','2','K',27,'[','0','G'};
	public static final char [] CURSOR_HOME = {27,'[','0','G'};
	public static final char [] CURSOR_LEFT = {27,'[','D'};
	public static final char [] CURSOR_RIGHT = {27,'[','C'};
	public static final char [] DEL_CH = {27,'[','3'};

	private long timeout = 0;
	private boolean echo = true;
	private boolean honorEscape = true;
	private int maxBytes_N = -1;
	private int maxBytes_n = -1;
	private String editLineText ;

	public String getEditLineText() {
		return editLineText;
	}
	public void setEditLineText(String editLineText) {
		this.editLineText = editLineText;
	}
	public int getMaxBytes_N() {
		return maxBytes_N;
	}
	public void setMaxBytes_N(int maxBytes_N) {
		this.maxBytes_N = maxBytes_N;
	}
	public int getMaxBytes_n() {
		return maxBytes_n;
	}
	public void setMaxBytes_n(int maxBytes_n) {
		this.maxBytes_n = maxBytes_n;
	}
	public boolean isHonorEscape() {
		return honorEscape;
	}
	public void setHonorEscape(boolean honorEscape) {
		this.honorEscape = honorEscape;
	}
	public boolean isEcho() {
		return echo;
	}
	public void setEcho(boolean echo) {
		this.echo = echo;
	}

	public long getTimeout() {
		return timeout;
	}
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	

	/** the interactive shell's prompt: read with the line editor (else the plain reader, as read uses) */
	private boolean lineEditing;

	public void setLineEditing(boolean on) {
		lineEditing = on;
	}

	private native int columns0();

	/** the terminal's width (else $COLUMNS, else 80) */
	public static int columns() {
		return Keyboard.columns();
	}

	/** the terminal's height (else $LINES, else 24), for a full-screen program */
	public static int rows() {
		return Keyboard.rows();
	}

	public  String readLine(Console console) throws IOException {
		if( lineEditing && availible && System.in == Console.System_in) {
			java.util.List<String> history = new java.util.ArrayList<>();
			for(Console.HistoryEntry e : console.history) {
				history.add(e.command);
			}
			LineEditor editor = new LineEditor(editorKeys(), System.out, history);
			editor.setColumns(columns());
			editor.setCompleter(new Completion(console));
			// set -o vi: vi's keys
			editor.setViMode(console.isOptionEnabled(Console.Option.Vi));
			// (bind -x)
			LineEditor.setCommandRunner(console::runBindCommand);
			String text = editLineText;
			editLineText = null;
			String ret = editor.readLine(getPrompt() == null ? "" : getPrompt());
			return text == null || ret == null ? ret : ret;
		}
		if( availible && System.in == Console.System_in) {
			return readLineNative(console);
		} else {
			return readLineConsole(console);
		}		
	}



	/**
	 * Use STDIN. no editing or history available
	 * @param console 
	 * @return
	 * @throws IOException
	 */
	private String readLineConsole(Console console) throws IOException {
		if( getPrompt() !=null) {
			// (on standard error, as bash writes its prompts)
			console.getStdErr().print(getPrompt());
			console.getStdErr().flush();
		}

		InputStream in = console.getStdIn();
		if( lineEditing ) {
			// the shell's commands from what is not a terminal: read by the line editor too, as
			// bash's readline reads them (Ctrl-R, Ctrl-O, vi's keys ...), and the line shown after the
			// prompt as readline shows it
			LineEditor.Keys keys = new LineEditor.Keys() {
				@Override
				public int next() throws IOException {
					int b = in.read();
					return b < 0 ? LineEditor.KEY_EOF : b;
				}

				@Override
				public int nextWithin(long millis) throws IOException {
					long until = System.currentTimeMillis()+millis;
					while( in.available() <= 0 && System.currentTimeMillis() < until ) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							break;
						}
					}
					return in.available() > 0 ? next() : LineEditor.KEY_NONE;
				}
			};
			java.util.List<String> history = new java.util.ArrayList<>();
			for(Console.HistoryEntry e : console.history) {
				history.add(e.command);
			}
			LineEditor editor = new LineEditor(keys, new PrintStream(java.io.OutputStream.nullOutputStream()), history);
			editor.setCompleter(new Completion(console));
			editor.setViMode(console.isOptionEnabled(Console.Option.Vi));
			String text = editor.readLine(getPrompt() == null ? "" : getPrompt());
			if( text == null ) {
				return null;
			}
			console.getStdErr().print(text+"\n");
			console.getStdErr().flush();
			return text;
		}
		StringBuffer ret = new StringBuffer();
		boolean done = false;
		int i = in.read();
		if( i < 0 ) {
			// end of input
			return null;
		}
		boolean escape=false;
		boolean eof = false;
		if( !eof) {
			while(i>=0 && !done) {
				/*
				if( isEcho()) {
					print((char)i);
				}
				*/
				char c = (char)i;
				if( c =='\\') {
					// escape next char
					escape = true;
				} else {
					if(escape) {
						ret.append(c);
						escape = false;
					} else {
						if( c == getLineTerminator()) {
							done = true;
						} else {
							ret.append(c);
						}
					}
				}
				if( !done) {
					i = in.read();
				}
			}
			if( i == -1) {
				eof = true;
			}
		}
		return ret.toString();
	}

	@Override
	public PrintStream getStdErr() {
		return System.err;
	}
	@Override
	public PrintStream getStdOut() {
		return System.out;
	}
	@Override
	public InputStream getStdIn() {
		// (the shell's own: a pipe or file is read unbuffered, so the programs it runs read the rest)
		return Console.System_in;
	}
}