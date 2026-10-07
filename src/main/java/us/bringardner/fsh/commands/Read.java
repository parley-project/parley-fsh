package us.bringardner.fsh.commands;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;

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

		for(int idx1=0; idx1 < args.length; idx1++) {
			Argument arg = args[idx1];
			String tmp = ""+arg.getValue(ctx);
			if( tmp.startsWith("-")) {
				for(int idx2=1;idx2< tmp.length(); idx2++ ) {
					char c = tmp.charAt(idx2);
					switch (c) {
					case 'a':arrayName = ""+args[++idx1].getValue(ctx); break;
					case 'd': {
						// -d '': up to a NUL (find -print0)
						String d = ""+args[++idx1].getValue(ctx);
						lineDelim = d.isEmpty() ? '\0' : d.charAt(0);
						break;
					}
					case 'e':options.add(Options.e);break;
					case 'i':editLineText = ""+args[++idx1].getValue(ctx); break;
					case 'n':n = Integer.parseInt(""+args[++idx1].getValue(ctx));break;
					case 'N':N = Integer.parseInt(""+args[++idx1].getValue(ctx));break;
					case 'p':prompt = ""+args[++idx1].getValue(ctx); break;
					case 'r':options.add(Options.r);break;
					case 's':options.add(Options.s);break;
					case 't':
						// seconds, maybe with a fraction: -t 0.5
						try {
							timeoutSeconds = Double.parseDouble(""+args[++idx1].getValue(ctx));
						} catch (NumberFormatException e) {
							ctx.stderr.println("read: "+args[idx1].getValue(ctx)+": invalid timeout specification");
							return 1;
						}
						timeout = (int) Math.ceil(timeoutSeconds);
						break;
					case 'u': {
						// read -u 3: from descriptor 3 (exec 3<file, {fd}<file, done 3<file)
						String text = ""+args[++idx1].getValue(ctx);
						try {
							fromFd = Integer.parseInt(text.trim());
						} catch (NumberFormatException e) {
							ctx.stderr.println("read: "+text+": invalid file descriptor specification");
							return 1;
						}
						break;
					}
					default:
						throw new IllegalArgumentException("Unexpected value: " + c);
					}

				}
			} else {
				names.add(""+tmp);
			}
		}
		

		
		java.io.InputStream callerIn = ctx.stdin;
		if( fromFd != 0 ) {
			Console.FileDiscriptor fd = ctx.console.getFileDistcriptor(fromFd);
			if( fd == null || fd.getIn() == null ) {
				ctx.stderr.println("read: "+fromFd+": invalid file descriptor: Bad file descriptor");
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
			line = readLine(ctx,prompt,lineDelim,timeout,editLineText,n, N, options);	
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
				List<String> words = split(line, ifs, Integer.MAX_VALUE);
				us.bringardner.fsh.FshList list = new us.bringardner.fsh.FshList();
				list.addAll(words);
				ctx.setVariable(arrayName, list);
			} else {
				List<String> values = split(line, ifs, names.size());
				for(int idx=0; idx < names.size(); idx++ ) {
					if( values.size()>idx) {
						ctx.setVariable(names.get(idx), values.get(idx));
					} else {
						ctx.setVariable(names.get(idx), "");
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
	static List<String> split(String line, String ifs, int max) {
		List<String> ret = new ArrayList<>();
		if( ifs.isEmpty()) {
			ret.add(line);
			return ret;
		}
		int n = line.length();
		int pos = 0;
		while( pos < n && isIfsSpace(line.charAt(pos), ifs)) {
			pos++;
		}
		while( pos < n ) {
			if( ret.size() == max-1 ) {
				// the rest, without trailing IFS whitespace
				int end = n;
				while( end > pos && isIfsSpace(line.charAt(end-1), ifs)) {
					end--;
				}
				ret.add(line.substring(pos, end));
				return ret;
			}
			int start = pos;
			while( pos < n && ifs.indexOf(line.charAt(pos)) < 0 ) {
				pos++;
			}
			ret.add(line.substring(start, pos));
			while( pos < n && isIfsSpace(line.charAt(pos), ifs)) {
				pos++;
			}
			if( pos < n && ifs.indexOf(line.charAt(pos)) >= 0 && !Character.isWhitespace(line.charAt(pos))) {
				pos++;
				while( pos < n && isIfsSpace(line.charAt(pos), ifs)) {
					pos++;
				}
			}
		}
		return ret;
	}

	private static boolean isIfsSpace(char c, String ifs) {
		return ifs.indexOf(c) >= 0 && Character.isWhitespace(c);
	}

	/** the last read ended at the end of the input, not at a delimiter */
	private boolean eof;

	/** read -t: seconds to wait for input (-1: no limit) */
	private double timeoutSeconds = -1;
	/** the last read ran out of time */
	private boolean timedOut;

	/** a pipe or terminal may wait for input; a file or text never does (and available() is 0 at its end) */
	private static boolean mayBlock(java.io.InputStream in) {
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
		if(NativeKeyboard.isAvailible() && ctx.stdin == Console.System_in || ctx.stdin instanceof NativeKeyboard) {
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
					buf.append((char)next);
				}
			} else if(N<0 && i == lineDelim ) {
				break;
			} else  {
				buf.append((char)i);
			}
			if(N>0 && N== buf.length() || n>0 && buf.length()>= n) {
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

}
