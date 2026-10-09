package us.bringardner.fsh.commands;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

public class Read extends ShellCommand{
	enum Options {e,r,s,a,d,i,n,N,p,t,u};

	static String name = "read";
	static String help = " read [-ers] [-a aname] [-d delim] [-i text] [-n nchars] [-N  nchars] [-p prompt] "
			+ "[-t timeout] [-u fd] [name ...]\n"
			+ "	One line is read from the standard input, or from the file descriptor fd supplied as an argument to the -u option,\n"
			+ "	split into words as described under Word Splitting, and the first word is assigned to the first name,\n"
			+ " the	second word to the second name, and so on.  If there are\n"
			+ "	more words than names, the remaining words and their\n"
			+ "	intervening delimiters are assigned to the last name.  If\n"
			+ "	there are fewer words read from the input stream than\n"
			+ "	names, the remaining names are assigned empty values.  \n"
			+ "The characters in IFS are used to split the line into words using the same rules the shell uses for expansion\n"
			+ "	(described under Word Splitting).  The backslash\n"
			+ "	character (\\) may be used to remove any special meaning for\n"
			+ "	the next character read and for line continuation."
			+ "\n"
			+ " If no names are supplied, the line read, without the ending\n"
			+ "              delimiter but otherwise unmodified, is assigned to the\n"
			+ "              variable REPLY.  The exit status is zero, unless end-of-\n"
			+ "              file is encountered, read times out (in which case the\n"
			+ "              status is greater than 128), a variable assignment error\n"
			+ "              (such as assigning to a readonly variable) occurs, or an\n"
			+ "              invalid file descriptor is supplied as the argument to -u."
			+ "\tsee 'man read' for more information\";"
			;

	private static final java.util.regex.Pattern ELEMENT = java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z_0-9]*)\\[(.+)\\]");

	static final String USAGE = "read: usage: read [-Eers] [-a array] [-d delim] [-i text] [-n nchars] [-N nchars] [-p prompt] [-t timeout] [-u fd] [name ...]";

	public Read() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		List<Options> options = new ArrayList<>();
		String editLineText="";
		String prompt= "";

		char lineDelim = '\n';

		String arrayName = null;
		List<String> names = new ArrayList<>();
		int n = -1;
		int N = -1;
		int timeout = -1;
		timeoutSeconds = -1;
		timedOut = false;
		int fromFd = 0;
		
		/*
		 * e & i are for command line editing
		 */

		// as getopt reads them: -ru3 is -r -u 3, -rd: is -r -d :
		boolean options_done = false;
		for(int idx1=0; idx1 < args.length; idx1++) {
			String tmp = ""+args[idx1].getValue(ctx);
			if( options_done || !tmp.startsWith("-") || tmp.length() == 1 ) {
				names.add(tmp);
				options_done = true;
				continue;
			}
			if( tmp.equals("--")) {
				options_done = true;
				continue;
			}
			for(int idx2=1;idx2< tmp.length(); idx2++ ) {
				char c = tmp.charAt(idx2);
				String value = null;
				if( "adinNptu".indexOf(c) >= 0 ) {
					if( idx2+1 < tmp.length()) {
						value = tmp.substring(idx2+1);
					} else if( idx1+1 < args.length ) {
						value = ""+args[++idx1].getValue(ctx);
					} else {
						ctx.error("read: -"+c+": option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					idx2 = tmp.length();
				}
				switch (c) {
				case 'a':arrayName = value; break;
				case 'd':
					// -d '': up to a NUL (find -print0)
					lineDelim = value.isEmpty() ? '\0' : value.charAt(0);
					break;
				case 'e':options.add(Options.e);break;
				case 'i':editLineText = value; break;
				case 'n':
				case 'N': {
					int count;
					try {
						count = Integer.parseInt(value.trim());
					} catch (NumberFormatException e) {
						count = -1;
					}
					if( count < 0 ) {
						ctx.error("read: "+value+": invalid number");
						return 1;
					}
					if( c == 'n' ) {
						n = count;
					} else {
						N = count;
					}
					break;
				}
				case 'p':prompt = value; break;
				case 'r':options.add(Options.r);break;
				case 's':options.add(Options.s);break;
				case 't':
					// seconds, maybe with a fraction: -t 0.5
					try {
						timeoutSeconds = Double.parseDouble(value);
					} catch (NumberFormatException e) {
						timeoutSeconds = -1;
					}
					if( timeoutSeconds < 0 ) {
						ctx.error("read: "+value+": invalid timeout specification");
						return 1;
					}
					timeout = (int) Math.ceil(timeoutSeconds);
					break;
				case 'u':
					// read -u 3: from descriptor 3 (exec 3<file, {fd}<file, done 3<file)
					try {
						fromFd = Integer.parseInt(value.trim());
					} catch (NumberFormatException e) {
						fromFd = -1;
					}
					if( fromFd < 0 ) {
						ctx.error("read: "+value+": invalid file descriptor specification");
						return 1;
					}
					break;
				default:
					ctx.error("read: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}

		// names that are no variable's (a[1] is an element)
		for(String nm : arrayName == null ? names : List.of(arrayName)) {
			if( !ShellContext.validReferenceName(nm) || arrayName != null && nm.contains("[")) {
				ctx.error("read: `"+nm+"': not a valid identifier");
				return 1;
			}
		}
		java.io.InputStream callerIn = ctx.stdin;
		if( fromFd != 0 ) {
			Console.FileDiscriptor fd = ctx.console.getFileDistcriptor(fromFd);
			if( fd == null || fd.getIn() == null ) {
				ctx.error("read: "+fromFd+": invalid file descriptor: Bad file descriptor");
				return 1;
			}
			ctx.stdin = fd.getIn();
		}
		try {
			return read(ctx, prompt, lineDelim, timeout, editLineText, n, N, options, arrayName, names);
		} finally {
			ctx.stdin = callerIn;
		}
	}

	private int read(ShellContext ctx, String prompt, char lineDelim, int timeout, String editLineText, int n, int N,
			List<Options> options, String arrayName, List<String> names) throws IOException {
		int ret = 0;
		String line = "";
		if( timeoutSeconds == 0 ) {
			// read -t 0: whether there is input, without reading it
			return ctx.stdin.available() > 0 || !mayBlock(ctx.stdin) ? 0 : 1;
		}
		try {
			// as bash: the prompt only when reading from a terminal
			boolean terminal = Console.isKeyboard(ctx.stdin) || ctx.stdin instanceof NativeKeyboard;
			line = readLine(ctx,terminal ? prompt : "",lineDelim,timeout,editLineText,n, N, options);	
		} catch (EOFException e2) {
			// nothing left: as in bash the names are set to empty (so read x || [ -n "$x" ] ends)
			line = "";
			eof = true;
		}
		if( timedOut ) {
			// as in bash: what was read is kept, and the status is 128+SIGALRM
			ret = 142;
		}
		if( eof ) {
			ret = 1;
		}
		
		if(arrayName == null &&  names.size()==0) {
			ctx.setVariable("REPLY", line);
		} else { 
			Object tmp = ctx.getVariable(Console.IFS);
			String ifs = tmp == null ? " \t\n" : tmp.toString();
			if( arrayName !=null ) {
				// the whole array at once, so a local one (local a; read -ra a) gets it
				List<String> words = split(line, ifs, Integer.MAX_VALUE, escaped);
				us.bringardner.fsh.FshList list = new us.bringardner.fsh.FshList();
				list.addAll(words);
				ctx.setVariable(arrayName, list);
			} else {
				List<String> values = split(line, ifs, names.size(), escaped);
				for(int idx=0; idx < names.size(); idx++ ) {
					String value = values.size() > idx ? values.get(idx) : "";
					java.util.regex.Matcher m = ELEMENT.matcher(names.get(idx));
					if( m.matches()) {
						// read a[1]: one element
						Object cur = ctx.getVariable(m.group(1));
						Object key = cur instanceof java.util.Map<?,?> ? m.group(2)
								: (Object) us.bringardner.fsh.expand.Arithmetic.evaluate(m.group(2), ctx).intValue();
						ctx.setVariable(m.group(1), key, value);
					} else if( ctx.console.isReadonly(ctx.readonlyName(names.get(idx)))) {
						// (said; the rest are not set, and the status is 2, as bash's)
						ctx.error(ctx.readonlyName(names.get(idx))+": readonly variable");
						return 2;
					} else {
						ctx.setVariable(names.get(idx), value);
					}
				}
			}
		}
		
		return ret;
	}

	/**
	 * Split a line into at most max fields as bash's read does: IFS whitespace at the ends is
	 * dropped, runs of it separate fields, each other IFS character ends one field (a::b has an
	 * empty field), and the last field is the rest of the line as written (a:b:c into two names
	 * gives a and b:c).
	 */
	public static List<String> split(String line, String ifs, int max) {
		return split(line, ifs, max, new java.util.BitSet());
	}

	/** split, the characters at the positions in escaped being no separators */
	public static List<String> split(String line, String ifs, int max, java.util.BitSet escaped) {
		String plain = ifs;
		// (an escaped character is matched against nothing)
		java.util.function.IntPredicate isIfs = i -> !escaped.get(i) && plain.indexOf(line.charAt(i)) >= 0;
		java.util.function.IntPredicate isSpace = i -> isIfs.test(i) && Character.isWhitespace(line.charAt(i));
		List<String> ret = new ArrayList<>();
		if( ifs.isEmpty()) {
			ret.add(line);
			return ret;
		}
		int n = line.length();
		int pos = 0;
		while( pos < n && isSpace.test(pos)) {
			pos++;
		}
		while( pos < n ) {
			if( ret.size() == max-1 ) {
				// the rest, without trailing IFS whitespace; but if the rest is one field and its
				// delimiter (a: or just :), only the field (bash's read)
				int end = n;
				// (one name: an escaped blank at the end goes too, as bash's)
				while( end > pos && (isSpace.test(end-1) || max == 1 && Character.isWhitespace(line.charAt(end-1)) && plain.indexOf(line.charAt(end-1)) >= 0)) {
					end--;
				}
				int w = pos;
				while( w < end && !isIfs.test(w) ) {
					w++;
				}
				int after = w;
				while( after < end && isSpace.test(after)) {
					after++;
				}
				if( after < end && isIfs.test(after) && !Character.isWhitespace(line.charAt(after))) {
					after++;
					while( after < end && isSpace.test(after)) {
						after++;
					}
				}
				ret.add(after >= end ? line.substring(pos, w) : line.substring(pos, end));
				return ret;
			}
			int start = pos;
			while( pos < n && !isIfs.test(pos) ) {
				pos++;
			}
			ret.add(line.substring(start, pos));
			while( pos < n && isSpace.test(pos)) {
				pos++;
			}
			if( pos < n && isIfs.test(pos) && !Character.isWhitespace(line.charAt(pos))) {
				pos++;
				while( pos < n && isSpace.test(pos)) {
					pos++;
				}
			}
		}
		return ret;
	}

	private static boolean isIfsSpace(char c, String ifs) {
		return ifs.indexOf(c) >= 0 && Character.isWhitespace(c);
	}

	/** the characters of the line read that came after a \ (no IFS character then) */
	private final java.util.BitSet escaped = new java.util.BitSet();

	/** the last read ended at the end of the input, not at a delimiter */
	private boolean eof;

	/** read -t: seconds to wait for input (-1: no limit) */
	private double timeoutSeconds = -1;
	/** the last read ran out of time */
	private boolean timedOut;

	/** a pipe or terminal may wait for input; a file or text never does (and available() is 0 at its end) */
	private static boolean mayBlock(java.io.InputStream in) {
		if( in instanceof us.bringardner.fsh.ProcessStdin ) {
			// (the shell's own: a file or /dev/null has its end at once)
			return us.bringardner.fsh.ProcessStdin.mayBlock();
		}
		return !(in instanceof java.io.FileInputStream || in instanceof java.io.ByteArrayInputStream);
	}

	/** wait until there is input; false if the time ran out first */
	private boolean waitForInput(ShellContext ctx, long deadline) throws IOException {
		if( deadline < 0 || !mayBlock(ctx.stdin)) {
			return true;
		}
		while( ctx.stdin.available() <= 0 ) {
			if( System.currentTimeMillis() >= deadline ) {
				return false;
			}
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	public String readLine(ShellContext ctx,String prompt) throws IOException {
		List<Options> options = new ArrayList<>();
		String editLineText="";
		char lineDelim = '\n';

		int n = -1;
		int N = -1;
		int timeout = -1;
		
		return readLine(ctx, prompt, lineDelim, timeout, editLineText, n, N, options);
	}
	
	public String readLine(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText,int n,int N, List<Options> options) throws IOException {
		if(NativeKeyboard.isAvailible() && Console.isKeyboard(ctx.stdin) || ctx.stdin instanceof NativeKeyboard) {
			return readLineNative(ctx,prompt, lineDelim, timeout, editLineText, n, N, options);
		} else {
			return readLineConsole(ctx,prompt, lineDelim, timeout, editLineText, n, N, options);
		}
	}
	
	@SuppressWarnings("resource")
	private String readLineNative(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText, int n, int N,List<Options> options) throws IOException {
		NativeKeyboard kb = new NativeKeyboard();
		kb.setLineTerminator(lineDelim);
		kb.setEcho(!options.contains(Options.s));
		kb.setEditLineText(editLineText);
		kb.setHonorEscape(!options.contains(Options.r));
		kb.setMaxBytes_n(n);
		kb.setMaxBytes_N(N);
		kb.setTimeout(timeout);
		kb.setPrompt(prompt);
		
		String ret = kb.readLine(ctx.console);
		if( ret == null ) {
			throw new EOFException();
		}
		return ret;
	}

	private String readLineConsole(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText,int n,int N, List<Options> options) throws IOException {
		if(prompt!=null && !prompt.isEmpty()) {
			// on standard error, as in bash
			ctx.stderr.print(prompt);
			ctx.stderr.flush();
		}
		
		StringBuilder buf = new StringBuilder();
		escaped.clear();

		int i = 0;
		eof = false;
		timedOut = false;
		long deadline = timeoutSeconds > 0 ? System.currentTimeMillis()+(long)(timeoutSeconds*1000) : -1;
		while( true ) {
			if( !waitForInput(ctx, deadline)) {
				timedOut = true;
				return buf.toString();
			}
			if( (i=ctx.stdin.read()) == -1 ) {
				break;
			}
			if(i == '\\' && !options.contains(Options.r)) {
				int next = ctx.stdin.read();
				if( next == '\n' ) {
					// backslash-newline: the line goes on
					continue;
				}
				if( next >= 0 ) {
					// (an escaped character is never a separator)
					escaped.set(buf.length());
					buf.append((char)next);
				}
			} else if(N<0 && i == lineDelim ) {
				break;
			} else  {
				// a character of UTF-8 text: its bytes
				buf.append(utf8(i, ctx.stdin));
			}
			int chars = buf.codePointCount(0, buf.length());
			if(N>0 && N == chars || n>0 && chars >= n) {
				break;
			}
		}
		if( i<0 && buf.length()==0) {
			throw new EOFException();
		}
		// a last line with no delimiter is read, but read fails (as in bash)
		eof = i < 0;
		
		String line = buf.toString();
		return line;
	}


	/**
	 * The character whose first byte is first, the rest read from in (only bytes that go on a
	 * UTF-8 character are taken); a byte that does not start one is the character of its value.
	 */
	static String utf8(int first, java.io.InputStream in) throws IOException {
		if( first < 0x80 ) {
			return String.valueOf((char) first);
		}
		int more = first >= 0xf0 && first < 0xf8 ? 3 : first >= 0xe0 ? 2 : first >= 0xc2 && first < 0xe0 ? 1 : 0;
		if( more == 0 ) {
			return String.valueOf((char) first);
		}
		byte [] bytes = new byte[more+1];
		bytes[0] = (byte) first;
		for (int k = 1; k <= more; k++) {
			if( in.markSupported()) {
				in.mark(1);
			}
			int b = in.read();
			if( b < 0x80 || b > 0xbf ) {
				// not part of it: put back if the stream can, else keep it as it is
				if( b >= 0 && in.markSupported()) {
					in.reset();
				} else if( b >= 0 ) {
					return new String(bytes, 0, k, java.nio.charset.StandardCharsets.ISO_8859_1)+(char) b;
				}
				return new String(bytes, 0, k, java.nio.charset.StandardCharsets.ISO_8859_1);
			}
			bytes[k] = (byte) b;
		}
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}
}
