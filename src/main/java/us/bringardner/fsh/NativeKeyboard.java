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
				String name = new NativeKeyboard().ttyName0();
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
	 * The process's standard input is a pipe or a file: commands are read from it (as a script)
	 * and shared with the programs. (Without this library: there is no console and no screen.)
	 */
	public static boolean inputIsPipeOrFile() {
		if( availible ) {
			try {
				return new NativeKeyboard().inputKind0() != 0;
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return System.console() == null && java.awt.GraphicsEnvironment.isHeadless();
	}

	/** the process's standard input is a terminal (without this library: there is a console) */
	public static boolean inputIsTerminal() {
		if( availible ) {
			try {
				return new NativeKeyboard().isInputTerminal0();
			} catch (UnsatisfiedLinkError e) {
				// an older library
			}
		}
		return System.console() != null;
	}

	private native void giveTerminalTo(long group);

	private native void setProcessGroup(long pid, long group);

	private native int stoppedBy(long pid);

	private static volatile String helper;
	/** the fsh.exec property helper was found with */
	private static volatile String helperProperty;
	private static volatile Boolean groups;

	/**
	 * fshexec, the helper that runs a program as bash would (in a process group, on the shell's
	 * file): next to this library, or the fsh.exec property. Null if there is none.
	 */
	public static String helper() {
		String prop = System.getProperty("fsh.exec");
		if( helper == null || !java.util.Objects.equals(prop, helperProperty)) {
			helperProperty = prop;
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
			helper = found;
		}
		return helper.isEmpty() ? null : helper;
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
					new NativeKeyboard().stoppedBy(-1);
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
		new NativeKeyboard().giveTerminalTo(group);
	}

	/** put child pid in process group (as the child does itself) */
	public static void processGroup(long pid, long group) {
		new NativeKeyboard().setProcessGroup(pid, group);
	}

	/** the signal that stopped child pid since the last call, or 0 */
	public static int stopSignal(long pid) {
		return new NativeKeyboard().stoppedBy(pid);
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
					t = new NativeKeyboard().isTerminal();
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
				new NativeKeyboard().setProgramMode(on);
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
				new NativeKeyboard().setSignalKeys(on);
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
			int key = getChar();
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
			while( poller.ready() > 0 ) {
				int key = poller.getChar();
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

	public static boolean isAvailible() {
		return availible;
	}


	static {
		try {
			System.loadLibrary("nativekeyboard");
			availible = true;
		} catch (Throwable e) {
			availible = false;
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
	

	public  String readLine(Console console) throws IOException {
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
			console.getStdOut().print(prompt);
		}

		StringBuffer ret = new StringBuffer();
		boolean done = false;
		InputStream in = console.getStdIn();
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
			} else if( ready() <= 0 ) {
				return KEY_NONE;
			} else {
				key = getChar();
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
			return pending.size()+typedAhead.size()+ready();
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
		return System.in;
	}
}
