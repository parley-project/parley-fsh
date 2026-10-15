package us.bringardner.fsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class NativeKeyboard extends InputStream implements KeyboardReader, InteractiveInput 	{
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
		if( availible ) {
			try {
				String name = new NativeKeyboard().opsTtyName0();
				if( name != null ) {
					return name.substring(name.lastIndexOf('/')+1);
				}
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return "tty";
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
		if( availible ) {
			try {
				return new NativeKeyboard().opsInputKind0() != 0;
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return System.console() == null && java.awt.GraphicsEnvironment.isHeadless();
	}

	/** standard input: 1 a pipe or socket, 2 a file, 0 something else, -1 not known */
	public static int inputKind() {
		if( availible ) {
			try {
				return new NativeKeyboard().opsInputKind0();
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return -1;
	}

	/** the process's standard input is a terminal (without this library: there is a console) */
	public static boolean inputIsTerminal() {
		if( availible ) {
			try {
				return new NativeKeyboard().opsIsInputTerminal0();
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return System.console() != null;
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
		if( !availible ) {
			return false;
		}
		try {
			return ops != null ? ops.isatty(fd) : isatty0(fd);
		} catch (UnsatisfiedLinkError e) {
			// (a library from before it had it: as before, input and output together)
			return terminal();
		}
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

	/** {the fsh.exec property it was found with, the helper ("" if none)} */
	private static volatile String [] helper;
	private static volatile Boolean groups;

	/**
	 * fshexec, the helper that runs a program as bash would (in a process group, on the shell's
	 * file): next to this library, or the fsh.exec property. Null if there is none.
	 */
	public static String helper() {
		String prop = System.getProperty("fsh.exec");
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

	/** the programs that have the terminal now (see lendTerminal) */
	private static final java.util.Set<Object> terminalUsers = new java.util.HashSet<>();
	private static Boolean terminal;

	/** standard input and output are a terminal (and this class can work it) */
	public static boolean terminal() {
		if( terminal == null ) {
			boolean t = false;
			if( availible ) {
				try {
					t = new NativeKeyboard().opsIsTerminal();
				} catch (UnsatisfiedLinkError e) {
					// an older library
				}
			}
			terminal = t;
		}
		return terminal;
	}

	/**
	 * A program reads the terminal itself, as under bash: it gets it in the terminal's own mode
	 * (where it echoes, edits lines, and Ctrl-C and Ctrl-Z signal the program), until each user
	 * has given it back.
	 */
	public static void lendTerminal(Object user) {
		synchronized (terminalUsers) {
			if( terminalUsers.add(user) && terminalUsers.size() == 1 ) {
				programMode(true);
			}
		}
	}

	/** user is done with the terminal (it ended, or was stopped) */
	public static void reclaimTerminal(Object user) {
		synchronized (terminalUsers) {
			if( terminalUsers.remove(user) && terminalUsers.isEmpty()) {
				programMode(false);
			}
		}
	}

	/** a program has the terminal now */
	public static boolean isTerminalLent() {
		synchronized (terminalUsers) {
			return !terminalUsers.isEmpty();
		}
	}

	private static void programMode(boolean on) {
		if( availible ) {
			try {
				new NativeKeyboard().opsSetProgramMode(on);
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
	}

	/** what a key that stops or interrupts a job is read as when the shell has taken it */
	public static final int KEY_INTR = -3;
	public static final int CTRL_C = 3;
	public static final int CTRL_Z = 26;
	public static final int CTRL_BACKSLASH = 28;

	/** told of Ctrl-C, Ctrl-Z and Ctrl-\; true if it took the key */
	public interface ControlKeys {
		boolean typed(int key);
	}

	/** set by the interactive shell while a job runs in the foreground */
	public static volatile ControlKeys controlKeys;

	/** keys read while a job ran (to find Ctrl-C and Ctrl-Z) that a reader has not had yet */
	private static final ArrayDeque<Integer> typedAhead = new ArrayDeque<>();
	private static final Object keyLock = new Object();
	private static NativeKeyboard poller;

	/** Ctrl-C, Ctrl-Z and Ctrl-\ as keys (on) or as signals (off, as without a shell) */
	public static void signalKeys(boolean on) {
		if( availible ) {
			try {
				new NativeKeyboard().opsSetSignalKeys(on);
			} catch (UnsatisfiedLinkError e) {
				// an older library: they stay signals
			}
		}
	}

	/** the next key: what was typed ahead first; one the shell took is KEY_INTR (Ctrl-C) or KEY_NONE */
	private int key() {
		synchronized (keyLock) {
			Integer k = typedAhead.poll();
			if( k != null ) {
				return k;
			}
			int key = opsGetChar();
			if( taken(key)) {
				return key == CTRL_C ? KEY_INTR : KEY_NONE;
			}
			return key;
		}
	}

	private static boolean taken(int key) {
		ControlKeys ck = controlKeys;
		return (key == CTRL_C || key == CTRL_Z || key == CTRL_BACKSLASH) && ck != null && ck.typed(key);
	}

	/**
	 * Read what has been typed, without waiting, so Ctrl-C and Ctrl-Z reach the shell while a
	 * job that does not read the keyboard runs; the other keys are kept for whoever reads next.
	 */
	public static void pollTyped() {
		if( !availible || isTerminalLent()) {
			// a program reads the terminal itself
			return;
		}
		synchronized (keyLock) {
			if( poller == null ) {
				poller = new NativeKeyboard();
			}
			while( poller.opsReady() > 0 ) {
				int key = poller.opsGetChar();
				if( key == KEY_EOF || key == KEY_NONE ) {
					break;
				}
				if( !taken(key)) {
					typedAhead.add(key);
				}
			}
		}
	}

	/** getChar() result at the end of input */
	public static final int KEY_EOF = -1;
	/** getChar() result when nothing was typed (yet) */
	public static final int KEY_NONE = -2;
	private static final int CTRL_D = 4;

	/** bytes of a special key's escape sequence still to be returned by read() */
	private final ArrayDeque<Integer> pending = new ArrayDeque<>();


	// https://espterm.github.io/docs/VT100%20escape%20codes.html


	/** Ctrl-C at the prompt: the line typed so far is dropped */
	public static class LineCancelled extends InterruptedIOException {
		private static final long serialVersionUID = 1L;
	}

	private  String readLineNative(Console console) throws LineCancelled {
		if( prompt == null ) {
			prompt = "";
		}

		System.out.print(prompt);
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

				if(key>=0 && maxBytes_N<0 && (!escaped && key == lineTerminator)) {
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
							print(prompt);
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
						print(prompt);
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
						print(prompt);
						print(buf.toString());
						for(int i = buf.length(); i > pos; i-- ) {
							print(CURSOR_LEFT);
						}
					}
					break;
				case END:print(CLEAR_LINE);
				print(prompt);
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

						if( key == lineTerminator) {
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
					key = lineTerminator;
				} else if(maxBytes_N>0 && maxBytes_N== buf.length() || maxBytes_n>0 && buf.length()>= maxBytes_n) {
					key = lineTerminator;
				} else {

					key = key();
				}
			}
		

	}

	/**
	 * Reads a secret: each character typed is shown as mask (a password prompt's *), Backspace
	 * removes the last one and Ctrl-U all of them; arrow and history keys are ignored. Ends at
	 * the line terminator. Typed bytes are read as UTF-8, so a character of several bytes shows
	 * one mask and is removed by one Backspace.
	 *
	 * @return the secret (the caller zeroes it), or null at end of input or Ctrl-C in a job
	 * @throws LineCancelled at Ctrl-C
	 */
	public char[] readMasked(char mask) throws LineCancelled {
		return readSecret(mask, false);
	}

	/**
	 * Reads a secret showing one spinner character (| / - \) that turns with every key,
	 * Backspace included: it shows the typing lands without showing how long the secret is.
	 */
	public char[] readSpinning() throws LineCancelled {
		return readSecret('|', true);
	}

	/** Reads a secret showing nothing, as sudo, ssh and passwd do. */
	public char[] readSilent() throws LineCancelled {
		return readSecret('\0', false);
	}

	private static final char[] SPINNER = {'|', '/', '-', '\\'};

	private char[] readSecret(char mask, boolean spin) throws LineCancelled {
		int frame = -1;
		System.out.print(prompt == null ? "" : prompt);
		System.out.flush();
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		// where each character starts in bytes, so Backspace removes a whole one
		java.util.ArrayDeque<Integer> starts = new java.util.ArrayDeque<>();
		try {
			while( true ) {
				int key = nextKey();
				if( key == KEY_EOF || (key == CTRL_D && starts.isEmpty())) {
					System.out.print('\n');
					return starts.isEmpty() ? null : decodeSecret(bytes);
				}
				if( key == KEY_INTR ) {
					return null;
				}
				if( key == CTRL_C ) {
					System.out.print("^C\n");
					System.out.flush();
					throw new LineCancelled();
				}
				if( key == lineTerminator || key == '\r' || key == '\n' ) {
					if( spin && frame >= 0 ) {
						System.out.print("\b \b");
					}
					System.out.print('\n');
					System.out.flush();
					return decodeSecret(bytes);
				}
				if( key == 127 || key == 8 ) {
					if( !starts.isEmpty()) {
						truncate(bytes, starts.pop());
						if( !spin && mask != '\0' ) {
							System.out.print("\b \b");
						}
					}
					frame = turn(spin, frame);
				} else if( key == 21 ) {
					// Ctrl-U: erase everything typed
					if( !spin && mask != '\0' ) {
						for(int i = starts.size(); i > 0; i-- ) {
							System.out.print("\b \b");
						}
					}
					starts.clear();
					truncate(bytes, 0);
					frame = turn(spin, frame);
				} else if( key >= UP && key <= END || key < 32 ) {
					// arrows, Home, End, Delete, and other control keys: nothing to do in a secret
				} else if( key < 0x80 || (key & 0xC0) == 0xC0 && key < 0x100 ) {
					// ASCII, or the first byte of a UTF-8 character: one mask
					starts.push(bytes.size());
					bytes.write(key);
					frame = spin ? turn(true, frame) : show(mask);
				} else if( key < 0x100 ) {
					// a UTF-8 continuation byte
					bytes.write(key);
				} else {
					// already a character
					starts.push(bytes.size());
					byte[] b = new String(Character.toChars(key)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
					bytes.write(b, 0, b.length);
					frame = spin ? turn(true, frame) : show(mask);
				}
				System.out.flush();
			}
		} finally {
			truncate(bytes, 0);
		}
	}

	/** Turns the spinner one step (drawing it the first time); returns the new frame. */
	private static int turn(boolean spin, int frame) {
		if( !spin ) {
			return frame;
		}
		if( frame >= 0 ) {
			System.out.print('\b');
		}
		int next = (frame+1) % SPINNER.length;
		System.out.print(SPINNER[next]);
		return next;
	}

	private static int show(char mask) {
		if( mask != '\0' ) {
			System.out.print(mask);
		}
		return -1;
	}

	private static char[] decodeSecret(java.io.ByteArrayOutputStream bytes) {
		byte[] b = bytes.toByteArray();
		java.nio.CharBuffer cb = java.nio.charset.StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(b));
		char[] ret = java.util.Arrays.copyOfRange(cb.array(), cb.position(), cb.limit());
		java.util.Arrays.fill(cb.array(), '\0');
		java.util.Arrays.fill(b, (byte) 0);
		return ret;
	}

	/** Cuts bytes back to size, zeroing what was cut. */
	private static void truncate(java.io.ByteArrayOutputStream bytes, int size) {
		byte[] keep = bytes.toByteArray();
		bytes.reset();
		bytes.write(keep, 0, Math.min(size, keep.length));
		java.util.Arrays.fill(keep, (byte) 0);
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

	/**
	 * @return the next key, waiting until one is typed (or KEY_EOF)
	 */
	private int nextKey() {
		int key = key();
		while( key == KEY_NONE ) {
			key = key();
		}
		return key;
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
	 * The fsh.native property chooses: "jni" never uses JNA; "jna" uses it for what it has;
	 * "auto" (the default) uses JNA only where the JNI library is not there (and not in a native
	 * image), so a system the library was not built for still has the keyboard and the terminal, and
	 * one that has it starts as quickly as before.
	 */
	private static final us.bringardner.fsh.jna.NativeOps ops;

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
		return us.bringardner.fsh.jna.NativeOps.failure;
	}

	/** why JNA cannot start programs although it does the rest, or null */
	public static String spawnProblem() {
		return ops == null ? null : ops.spawnProblem();
	}

	/** the keyboard and terminal are done through JNA (not the JNI library) */
	public static boolean usesJna() {
		return ops != null;
	}


	/** fsh runs as a native image (GraalVM), not in a JVM */
	public static final boolean NATIVE_IMAGE = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));

	static {
		try {
			System.loadLibrary("nativekeyboard");
			availible = true;
		} catch (Throwable e) {
			availible = false;
			jniProblem = e.getClass().getSimpleName()+": "+e.getMessage();
		}
		if( NATIVE_IMAGE ) {
			// (no launcher gives a native image its library path: the library and fshexec are next
			// to it, or (a copy of it elsewhere) where it was built: fsh.libdir, set as it is built)
			String exe = ProcessHandle.current().info().command().orElse(null);
			java.util.List<java.io.File> dirs = new java.util.ArrayList<>();
			if( exe != null && new java.io.File(exe).getParentFile() != null ) {
				dirs.add(new java.io.File(exe).getParentFile());
			}
			if( System.getProperty("fsh.libdir") != null ) {
				dirs.add(new java.io.File(System.getProperty("fsh.libdir")));
			}
			StringBuilder path = new StringBuilder();
			for(java.io.File dir : dirs) {
				java.io.File lib = new java.io.File(dir, System.mapLibraryName("nativekeyboard"));
				if( !availible && lib.isFile()) {
					try {
						System.load(lib.getPath());
						availible = true;
					} catch (Throwable e) {
					}
				}
				path.append(dir.getPath()).append(java.io.File.pathSeparator);
			}
			// (where helper() finds fshexec)
			System.setProperty("java.library.path", path+System.getProperty("java.library.path", ""));
		}
		jniLoaded = availible;
		us.bringardner.fsh.jna.NativeOps jna = null;
		String mode = System.getProperty("fsh.native", "auto");
		// (a native image has no reflection and JNI metadata for JNA: only if asked for)
		if( !"jni".equals(mode) && ("jna".equals(mode) || (!jniLoaded && !NATIVE_IMAGE)) ) {
			try {
				jna = us.bringardner.fsh.jna.NativeOps.create();
			} catch (Throwable e) {
				// (no JNA, or its library: as without it)
			}
		}
		ops = jna;
		availible = jniLoaded || ops != null;
	}

	// the functions that JNA does when it can, and the JNI library's otherwise

	private int opsGetChar() {
		return ops != null ? ops.getChar() : getChar();
	}

	private int opsReady() {
		return ops != null ? ops.ready() : ready();
	}

	private void opsSetSignalKeys(boolean on) {
		if( ops != null ) {
			ops.setSignalKeys(on);
		} else {
			setSignalKeys(on);
		}
	}

	private void opsSetProgramMode(boolean on) {
		if( ops != null ) {
			ops.setProgramMode(on);
		} else {
			setProgramMode(on);
		}
	}

	private boolean opsIsTerminal() {
		return ops != null ? ops.isTerminal() : isTerminal();
	}

	private boolean opsIsInputTerminal0() {
		return ops != null ? ops.isInputTerminal() : isInputTerminal0();
	}

	private String opsTtyName0() {
		return ops != null ? ops.ttyName() : ttyName0();
	}

	private int opsInputKind0() {
		return ops != null ? ops.inputKind() : inputKind0();
	}

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

	private int opsColumns0() {
		return ops != null ? ops.columns() : columns0();
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


	public static final int UP = 500;
	public static final int DN = 501;
	public static final int RT = 502;
	public static final int LF = 503;
	public static final int HOME = 504;
	public static final int PAGE_UP = 505;
	public static final int PAGE_DOWN = 506;
	public static final int DELETE = 507;
	public static final int END = 508;
	private long timeout = 0;
	private boolean echo = true;
	private boolean honorEscape = true;
	private char lineTerminator = '\n';
	private int maxBytes_N = -1;
	private int maxBytes_n = -1;
	private String editLineText ;
	private String prompt = "prompt";



	public String getPrompt() {
		return prompt;
	}
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
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
	public char getLineTerminator() {
		return lineTerminator;
	}
	public void setLineTerminator(char lineTerminator) {
		this.lineTerminator = lineTerminator;
	}
	

	/** the interactive shell's prompt: read with the line editor (else the plain reader, as read uses) */
	private boolean lineEditing;

	public void setLineEditing(boolean on) {
		lineEditing = on;
	}

	private native int columns0();

	/** the terminal's width (else $COLUMNS, else 80) */
	public static int columns() {
		if( availible ) {
			try {
				int c = new NativeKeyboard().opsColumns0();
				if( c > 0 ) {
					return c;
				}
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		try {
			return Integer.parseInt(System.getenv().getOrDefault("COLUMNS", "80"));
		} catch (NumberFormatException e) {
			return 80;
		}
	}

	/** the keys typed, for the line editor */
	private final LineEditor.Keys editorKeys = new LineEditor.Keys() {
		@Override
		public int next() {
			int k = key();
			while( k == KEY_NONE ) {
				k = key();
			}
			return k;
		}

		@Override
		public int nextWithin(long millis) {
			long until = System.currentTimeMillis()+millis;
			while( true ) {
				synchronized (keyLock) {
					Integer k = typedAhead.poll();
					if( k != null ) {
						return k;
					}
					if( opsReady() > 0 ) {
						return getChar();
					}
				}
				if( System.currentTimeMillis() >= until ) {
					return KEY_NONE;
				}
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					return KEY_NONE;
				}
			}
		}
	};

	public  String readLine(Console console) throws IOException {
		if( lineEditing && availible && System.in == Console.System_in) {
			java.util.List<String> history = new java.util.ArrayList<>();
			for(Console.HistoryEntry e : console.history) {
				history.add(e.command);
			}
			LineEditor editor = new LineEditor(editorKeys, System.out, history);
			editor.setColumns(columns());
			editor.setCompleter(new Completion(console));
			// set -o vi: vi's keys
			editor.setViMode(console.isOptionEnabled(Console.Option.Vi));
			// (bind -x)
			LineEditor.setCommandRunner(console::runBindCommand);
			String text = editLineText;
			editLineText = null;
			String ret = editor.readLine(prompt == null ? "" : prompt);
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
		if( prompt !=null) {
			// (on standard error, as bash writes its prompts)
			console.getStdErr().print(prompt);
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
			String text = editor.readLine(prompt == null ? "" : prompt);
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
						if( c == lineTerminator) {
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
	public int read() throws IOException {
		if( !pending.isEmpty()) {
			return pending.poll();
		}
		if( !availible ) {
			return System.in.read();
		}
		while(true) {
			int key = key();
			if( key == KEY_INTR ) {
				return -1;
			} else if( key == KEY_NONE ) {
				if( Thread.currentThread().isInterrupted()) {
					throw new InterruptedIOException();
				}
			} else if( key == KEY_EOF ) {
				return -1;
			} else if( key > 255 ) {
				// give the program the escape sequence the terminal sent
				byte [] seq = escapeSequence(key);
				for(int idx=1; idx < seq.length; idx++ ) {
					pending.add(seq[idx] & 0xff);
				}
				return seq[0];
			} else {
				return key;
			}
		}
	}	

	/**
	 * A byte that was typed, without waiting: KEY_NONE if there is none (or the shell took it,
	 * Ctrl-Z), KEY_EOF at the end of input. For a program's input, so a job that is stopped
	 * meanwhile does not wait in here for the next key.
	 */
	public int readTyped() {
		if( !pending.isEmpty()) {
			return pending.poll();
		}
		if( !availible ) {
			return KEY_NONE;
		}
		int key;
		synchronized (keyLock) {
			Integer k = typedAhead.poll();
			if( k != null ) {
				key = k;
			} else if( opsReady() <= 0 ) {
				return KEY_NONE;
			} else {
				key = opsGetChar();
				if( taken(key)) {
					return KEY_NONE;
				}
			}
		}
		if( key > 255 ) {
			byte [] seq = escapeSequence(key);
			for(int idx=1; idx < seq.length; idx++ ) {
				pending.add(seq[idx] & 0xff);
			}
			return seq[0];
		}
		return key;
	}

	@Override
	public int available() throws IOException {
		if( !availible ) {
			return pending.size()+System.in.available();
		}
		synchronized (keyLock) {
			return pending.size()+typedAhead.size()+opsReady();
		}
	}

	private static byte [] escapeSequence(int key) {
		String seq;
		switch (key) {
		case UP: seq = "\033[A"; break;
		case DN: seq = "\033[B"; break;
		case RT: seq = "\033[C"; break;
		case LF: seq = "\033[D"; break;
		case HOME: seq = "\033[H"; break;
		case END: seq = "\033[F"; break;
		case DELETE: seq = "\033[3~"; break;
		case PAGE_UP: seq = "\033[5~"; break;
		case PAGE_DOWN: seq = "\033[6~"; break;
		default: seq = ""+((char)(key & 0xff));
		}
		return seq.getBytes();
	}

	@Override
	public void close() throws IOException {
		//  do nothing...

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
