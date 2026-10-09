package us.bringardner.fsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.fsh.Argument;


public abstract class ShellCommand {
	public static final String ROOT_FACTORY_ID = "factory_id";
	/*
 https://www.gnu.org/software/bash/manual/html_node/Shell-Parameter-Expansion.html
 https://sourceforge.net/projects/javacurses/
	 */
	//ASCII
	private static final String[] posixClass = {"[!","[:word:]","[:ascii:]","[:lower:]","[:upper:]","[:alpha:]","[:digit:]","[:alnum:]","[:punct:]","[:graph:]","[:print:]","[:blank:]","[:cntrl:]","[:xdigit:]","[:space:]"};
	private static final String[] javaClass  = {"[^","\\w","\\p{ASCII}","\\p{Lower}","\\p{Upper}","\\p{Alpha}","\\p{Digit}","\\p{Alnum}","\\p{Punct}","\\p{Graph}","\\p{Print}","\\p{Blank}","\\p{Cntrl}","\\p{XDigit}","\\p{Space}"};
	@SuppressWarnings("unused")
	private static final String[] description = {"Negate character","A lower-case alphabetic character: [a-z]","An upper-case alphabetic character:[A-Z]","An alphabetic character:[\\p{Lower}\\p{Upper}]"," A decimal digit: [0-9]"," An alphanumeric character:[\\p{Alpha}\\p{Digit}]","Punctuation: One of !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~","A visible character: [\\p{Alnum}\\p{Punct}]"," A printable character: [\\p{Graph}\\x20]"," A space or a tab: [ \\t]"," A control character: [\\x00-\\x1F\\x7F]","A hexadecimal digit: [0-9a-fA-F]"," A whitespace character: [ \\t\\n\\x0B\\f\\r]"};
	String name;
	String help;
	protected Argument[] args;
	public Argument[] getArgs() {
		return args;
	}

	public ShellCommand(String name,String help) {
		this.name = name;
		this.help = help;
	}

	public String getName() {
		return name;
	}

	public String getHelp() {
		return help;
	}


	public abstract int process(ShellContext ctx) throws IOException;

	public void copyStream(ShellContext sc,InputStream in, OutputStream out) throws IOException {
		byte [] data = new byte[1024*10];
		int got = 0;

		while( (got=in.read(data)) >= 0) {
			if( got > 0 ) {
				out.write(data,0,got);
			}
			if(sc.getException() !=null) {
				if( sc.getException() instanceof us.bringardner.fsh.signal.SignalException ) {
					throw sc.getException();
				}
				throw new IOException(sc.getException());
			}
			sc.waitWhilePaused();
		}
	}

	public class ShellArgument {
		public List<Object> options = new ArrayList<>();
		public List<String> paths = new ArrayList<>();
	}


	protected ShellArgument parseArgs(ShellContext ctx,Class<?> cls) throws IOException {
		ShellArgument ret = new ShellArgument();
		if( args.length>0) {
			try {
				Method m = cls.getDeclaredMethod("valueOf", String.class);
				try {
					//  look at set command
					m = cls.getDeclaredMethod("find", String.class);					
				} catch (Throwable e) {
				}
				m.setAccessible(true);
				boolean optionsEnded = false;
				for(int idx=0; idx < args.length; idx++ ) {
					String arg = (""+args[idx].getValue(ctx)).trim();
					if( !optionsEnded && arg.equals("--")) {
						// everything after -- is a path, even if it starts with - (ls -- -dir)
						optionsEnded = true;
					} else if( !optionsEnded && arg.startsWith("-")) {
						arg = arg.substring(1);
						for(char c : arg.toCharArray()) {
							try {
								Object a = m.invoke(null, ""+c);
								ret.options.add(a);
							} catch (Exception e) {
								throw new IOException("Unknown argument "+c);
							}
						}
					} else {
						ret.paths.add(arg);
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IOException(e);
			}
		}
		return ret;
	}




	private static void glob2a(ShellContext ctx,List<FileSource> ret,FileSource dir,String [] segments, int segmentIdx) throws IOException {
		if( segmentIdx>= segments.length) {
			FileSource[] list = dir.listFiles();
			if( list !=null && list.length>0) {
				for(FileSource f : list) {
					ret.add(f);
				}
			}

			return;
		}

		String segment = segments[segmentIdx];
		if( segment.equals("*")) {
			for(FileSource file :dir.listFiles()) {
				ret.add(file);
			}
			return;
		}
		if( segment.isEmpty()) {
			glob2a(ctx, ret, dir, segments, segmentIdx+1);
		} else {
			segment = expandTilde(ctx,segment);		
			String cleanPath = segment;
			if( !hasWildcard(segment)) {
				cleanPath = FileSourceFactory.expandDots(segment, dir.getFileSourceFactory().getSeperatorChar());
			}
			
			cleanPath = cleanPath.replaceAll("/./", "/");
			if( FileSourceFactory.isWindows()) {
				if(cleanPath.length()==2) {
					if( cleanPath.endsWith(":")) {
						cleanPath = cleanPath+"\\";
					}
				}
			}
			
			if( !hasWildcard(cleanPath)) {
				FileSource file = null;

				if( isRelative(cleanPath)) {
					file = dir.getChild(cleanPath);
				} else {
					file = ctx.console.createFileSource(cleanPath);					
				}

				if( file.isDirectory()) {
					glob2a(ctx, ret, file, segments, segmentIdx+1);
				} else {
					// if we get here, this file must be included in the result set
					ret.add(file);
				}
			} else {
				// has a wild card
				List<FileSource> files = expandWildcards(ctx, dir, cleanPath);	
				if( segmentIdx == segments.length-1) {
					ret.addAll(files);
				} else {
					for(FileSource file : files) {
						glob2a(ctx, ret, file, segments, segmentIdx+1);
					}
				}
			}
		}
	}

	private static List<FileSource>  expandWildcards(ShellContext ctx,FileSource dir,String cleanPath) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		String path = prepWildCards(cleanPath);

		Pattern p = Pattern.compile(path);

		FileSource[] kids = dir.listFiles();
		if( kids !=null ) {
			for(FileSource file : kids) {
				String name = file.getName();
				Matcher m = p.matcher(name);
				if( m.matches()) {
					ret.add(file);
				}
			}
		}

		return ret;
	}

	public static String prepWildCards(String cleanPath) {
		return prepWildCards(cleanPath, true);
	}

	/**
	 * A pattern (* ? [...]) as a regular expression, as bash reads it: quoted text ('...' "...")
	 * and a backslashed character are literal, [!...] and [^...] negate, [:alpha:] classes, and
	 * every other character is itself (+ ( . are not regular expression operators here).
	 * @param greedy false: * and ? match as little as they can (for # and % in ${x#pat})
	 */
	/** the index of the ) that closes the ( at open, or -1 */
	private static int extEnd(String text, int open) {
		int depth = 0;
		for (int idx = open; idx < text.length(); idx++) {
			char c = text.charAt(idx);
			if( c == '\\' ) {
				idx++;
			} else if( c == '(' ) {
				depth++;
			} else if( c == ')' && --depth == 0 ) {
				return idx;
			}
		}
		return -1;
	}

	/** a|b|c (| inside nested parentheses is not split) as regular expressions joined by | */
	private static String alternatives(String body, boolean greedy) {
		StringBuilder ret = new StringBuilder();
		int depth = 0;
		int start = 0;
		for (int idx = 0; idx <= body.length(); idx++) {
			char c = idx < body.length() ? body.charAt(idx) : '|';
			if( c == '\\' ) {
				idx++;
			} else if( c == '(' ) {
				depth++;
			} else if( c == ')' ) {
				depth--;
			} else if( c == '|' && depth == 0 ) {
				if( start > 0 ) {
					// (an empty alternative, @(|foo), keeps its place)
					ret.append('|');
				}
				ret.append(prepWildCards(body.substring(start, Math.min(idx, body.length())), greedy));
				start = idx+1;
			}
		}
		return ret.toString();
	}

	public static String prepWildCards(String cleanPath,boolean greedy) {
		StringBuilder ret = new StringBuilder();
		String lazy = greedy ? "" : "?";
		int n = cleanPath.length();
		for (int idx = 0; idx < n; idx++) {
			char c = cleanPath.charAt(idx);
			int ext = "?*+@!".indexOf(c) >= 0 && idx+1 < n && cleanPath.charAt(idx+1) == '(' ? extEnd(cleanPath, idx+1) : -1;
			if( ext > 0 ) {
				// ?(a|b) *(a|b) +(a|b) @(a|b) !(a|b)
				String alts = alternatives(cleanPath.substring(idx+2, ext), greedy);
				if( c == '!' ) {
					// anything that is not one of them: the rest of the pattern must not match them
					// followed by what comes after
					String rest = prepWildCards(cleanPath.substring(ext+1), greedy);
					ret.append("(?:(?!(?:").append(alts).append(")(?:").append(rest).append(")$).*?)").append(rest);
					return ret.toString();
				}
				ret.append("(?:").append(alts).append(')').append(c == '@' ? "" : ""+c);
				idx = ext;
				continue;
			}
			switch (c) {
			case '*': ret.append(".*").append(lazy); break;
			case '?': ret.append('.'); break;
			case '\\':
				if( idx+1 < n ) {
					ret.append(java.util.regex.Pattern.quote(""+cleanPath.charAt(++idx)));
				} else {
					ret.append("\\\\");
				}
				break;
			case '\'':
			case '"': {
				int end = cleanPath.indexOf(c, idx+1);
				if( end < 0 ) {
					ret.append(java.util.regex.Pattern.quote(""+c));
					break;
				}
				if( end > idx+1 ) {
					ret.append(java.util.regex.Pattern.quote(cleanPath.substring(idx+1, end)));
				}
				idx = end;
				break;
			}
			case '[': {
				int end = bracketEnd(cleanPath, idx);
				if( end < 0 ) {
					// no ]: a [ that is itself, as in bash
					ret.append("\\[");
					break;
				}
				ret.append(bracketRegex(cleanPath.substring(idx+1, end)));
				idx = end;
				break;
			}
			default:
				if( "\\.^$|+(){}".indexOf(c) >= 0 ) {
					ret.append('\\');
				}
				ret.append(c);
			}
		}
		return ret.toString();
	}


	/**
	 * The ] that ends the bracket expression [ at open, or -1 if it does not end: a ] right
	 * after [ or [! is in the set, [:alpha:] [.a.] [=a=] are units, and \] is a ].
	 */
	public static int bracketEnd(String text, int open) {
		int n = text.length();
		int i = open+1;
		if( i < n && (text.charAt(i) == '!' || text.charAt(i) == '^')) {
			i++;
		}
		if( i < n && text.charAt(i) == ']' ) {
			i++;
		}
		while( i < n ) {
			char c = text.charAt(i);
			if( c == '\\' ) {
				i += 2;
				continue;
			}
			if( c == '[' && i+1 < n && ":.=".indexOf(text.charAt(i+1)) >= 0 ) {
				int close = text.indexOf(text.charAt(i+1)+"]", i+2);
				if( close > 0 ) {
					i = close+2;
					continue;
				}
			}
			if( c == ']' ) {
				return i;
			}
			i++;
		}
		return -1;
	}

	/** POSIX's names of the characters, for [.name.] */
	private static final java.util.Map<String, Character> COLLATING = new java.util.HashMap<>();
	static {
		String [] control = {"NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "alert", "backspace", "tab", "newline",
				"vertical-tab", "form-feed", "carriage-return", "SO", "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK",
				"SYN", "ETB", "CAN", "EM", "SUB", "ESC", "IS4", "IS3", "IS2", "IS1"};
		for (int i = 0; i < control.length; i++) {
			COLLATING.put(control[i], (char) i);
		}
		COLLATING.put("BEL", (char) 7);
		COLLATING.put("BS", (char) 8);
		COLLATING.put("HT", '\t');
		COLLATING.put("LF", '\n');
		COLLATING.put("VT", (char) 11);
		COLLATING.put("FF", (char) 12);
		COLLATING.put("CR", '\r');
		COLLATING.put("FS", (char) 28);
		COLLATING.put("GS", (char) 29);
		COLLATING.put("RS", (char) 30);
		COLLATING.put("US", (char) 31);
		String [][] names = {{"space", " "}, {"exclamation-mark", "!"}, {"quotation-mark", "\""}, {"number-sign", "#"},
				{"dollar-sign", "$"}, {"percent-sign", "%"}, {"ampersand", "&"}, {"apostrophe", "'"},
				{"left-parenthesis", "("}, {"right-parenthesis", ")"}, {"asterisk", "*"}, {"plus-sign", "+"},
				{"comma", ","}, {"hyphen", "-"}, {"hyphen-minus", "-"}, {"period", "."}, {"full-stop", "."},
				{"slash", "/"}, {"solidus", "/"}, {"zero", "0"}, {"one", "1"}, {"two", "2"}, {"three", "3"},
				{"four", "4"}, {"five", "5"}, {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"},
				{"colon", ":"}, {"semicolon", ";"}, {"less-than-sign", "<"}, {"equals-sign", "="},
				{"greater-than-sign", ">"}, {"question-mark", "?"}, {"commercial-at", "@"},
				{"left-square-bracket", "["}, {"backslash", "\\"}, {"reverse-solidus", "\\"},
				{"right-square-bracket", "]"}, {"circumflex", "^"}, {"circumflex-accent", "^"},
				{"underscore", "_"}, {"low-line", "_"}, {"grave-accent", "`"}, {"left-brace", "{"},
				{"left-curly-bracket", "{"}, {"vertical-line", "|"}, {"right-brace", "}"},
				{"right-curly-bracket", "}"}, {"tilde", "~"}, {"DEL", "\u007f"}};
		for(String [] nm : names) {
			COLLATING.put(nm[0], nm[1].charAt(0));
		}
	}

	/**
	 * A bracket expression's inside as a regular expression, as bash reads it: ! or ^ negates,
	 * a-z ranges, [:class:], [.name.] (a character by itself or POSIX's name for it), [=c=],
	 * and \c. A range backwards, or from or to a name that is not a character, matches nothing.
	 */
	static String bracketRegex(String body) {
		StringBuilder set = new StringBuilder();
		int b = 0;
		boolean negate = false;
		if( body.startsWith("!") || body.startsWith("^")) {
			negate = true;
			b = 1;
		}
		// the characters in order: Integer (a character), -1 (one that is not valid), or a class
		java.util.List<Object> items = new java.util.ArrayList<>();
		java.util.List<Boolean> dash = new java.util.ArrayList<>();
		for (; b < body.length(); b++) {
			char d = body.charAt(b);
			if( d == '[' && b+1 < body.length() && ":.=".indexOf(body.charAt(b+1)) >= 0 ) {
				char kind = body.charAt(b+1);
				int close = body.indexOf(kind+"]", b+2);
				if( close > 0 ) {
					String name = body.substring(b+2, close);
					b = close+1;
					if( kind == ':' ) {
						String cls = posixToJava("[:"+name+":]");
						if( cls.equals("[:"+name+":]")) {
							// a class that is not one ([:aleph:]): it matches nothing, as in bash
							continue;
						}
						items.add(cls);
					} else if( name.length() == 1 ) {
						items.add((int) name.charAt(0));
					} else if( kind == '.' && COLLATING.containsKey(name)) {
						items.add((int) COLLATING.get(name));
					} else {
						items.add(-1);
					}
					dash.add(false);
					continue;
				}
			}
			if( d == '\\' && b+1 < body.length()) {
				items.add((int) body.charAt(++b));
				dash.add(false);
				continue;
			}
			// a - between two (not first, not last) is a range
			boolean range = d == '-' && !items.isEmpty() && b+1 < body.length();
			items.add((int) d);
			dash.add(range);
		}
		for (int i = 0; i < items.size(); i++) {
			Object it = items.get(i);
			if( i+2 < items.size() && dash.get(i+1) && !(it instanceof String) && !(items.get(i+2) instanceof String)) {
				int from = (Integer) it;
				int to = (Integer) items.get(i+2);
				if( from >= 0 && to >= 0 && from <= to ) {
					set.append("\\x{").append(Integer.toHexString(from)).append("}-\\x{").append(Integer.toHexString(to)).append('}');
				}
				i += 2;
				continue;
			}
			if( it instanceof String cls ) {
				set.append(cls);
			} else if( (Integer) it >= 0 ) {
				set.append("\\x{").append(Integer.toHexString((Integer) it)).append('}');
			}
		}
		if( set.length() == 0 ) {
			// nothing in it
			return negate ? "(?s:.)" : "(?!)";
		}
		return (negate ? "[^" : "[")+set+"]";
	}

	public static String posixToJava(String cleanPath) {
		String ret = cleanPath;
		for (int idx1 = 0; idx1 < posixClass.length; idx1++) {
			String tmp = replaceAll(ret,posixClass[idx1], javaClass[idx1]);
			ret = tmp;
		}

		return ret;
	}

	/**
	 * https://www.gnu.org/software/bash/manual/html_node/Tilde-Expansion.html
	 * @param segment
	 * @return
	 * @throws IOException 
	 */
	protected static String expandTilde(ShellContext ctx,String segment) throws IOException {
		/*
		 * If a word begins with an unquoted tilde character (‘~’), all of the characters up to the 
		 * first unquoted slash (or all characters, if there is no unquoted slash) are considered a tilde-prefix. 
		 */
		String ret = segment;
		if(ret != null && !ret.isEmpty()) {
			if( segment.charAt(0) == '~') {
				String home = System.getProperty("user.home");
				int idx=1;
				if( segment.startsWith("~+")) {
					home = ctx.console.getCurrentDirectory().getAbsolutePath();
					idx++;
				} else if( segment.startsWith("~-")) {
					Object obj = ctx.console.variables.get(Console.VARIABLE_OLDPWD);
					if (obj instanceof FileSource) {
						home = ((FileSource)obj).getAbsolutePath();
					}
					idx++;
				}
				String tmp = segment.substring(idx);
				
				if( !tmp.isBlank() && !(tmp.charAt(0)=='/' || tmp.charAt(0) == '\\')) {
					FileSource fs = ctx.getFileSource(home);
					FileSource fs2 = fs.getParentFile();
					
					if(segment.indexOf('/')>0) {
						ret = fs2.getAbsolutePath()+"/"+tmp;
					} else if(segment.indexOf('\\')>0) {
						ret = fs2.getAbsolutePath()+"\\"+tmp;
					} else {
						ret = home+tmp;
					}
				} else {
					ret = home+tmp;
				}
			}	
		}
		return ret;
	}

	
	private static String replaceAll(String path, String posix, String java) {
		return path.replace(posix, java);
	}

	public static final char wildcards[] = {'*','?','[','~'};

	public static String removeWildcards(String str) {
		StringBuilder ret = new StringBuilder();
		byte [] data = str.getBytes();
		for (int idx = 0; idx < data.length; idx++) {
			char c = (char)data[idx];
			switch (c) {
			case '*': 
			case '?':
			case '~':
				break;
			default:
				ret.append(c);
			}
		}
		return ret.toString();
	}

	public List<FileSource>  globOld(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		FileSource cwd = null;
		path = expandTilde(ctx, path);
		if( !hasWildcard(path)) {
			System.out.println("Here");
			FileSource tmpf = ctx.console.getMountFactory().
					createFileSource(path);
			ret.add(tmpf);
			return ret;
		}


		if( isRelative(path)) {
			cwd = ctx.console.getCurrentDirectory();
		} else {
			cwd = ctx.console.getMountFactory().listRoots()[0];
			path = path.substring(1);
		}

		if( path.equals(".")) {
			ret.add( ctx.console.getCurrentDirectory());
			return ret;
		} else if( path.equals("..")) {
			cwd = ctx.console.getCurrentDirectory();
			ret.add(cwd.getParentFile());
			return ret;
		}
		String pathSegments [] = path.split("["+cwd.getFileSourceFactory().getSeperatorChar()+"]");
		//  these are all the files that match this segment
		globDir(ctx,ret, cwd, pathSegments, 0);



		return ret;
	}

	public static List<FileSource>  getFiles(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		FileSource literal = ctx.console.createFileSource(path);
		if( hasWildcard(path) && !literal.exists() && path.indexOf('\\') < 0 ) {
			// (the shell expanded the patterns already: a name with * in it that exists is that file,
			// and one with a \ is just that name: touch 'a\*b')
			ret = glob(ctx, path);
			if( ret.isEmpty()) {
				ret.add(literal);
			}
		} else {
			ret.add(literal);
		}
		if( ret.size()>1) {
			// by path, as bash sorts a glob (files with the same name in different directories were
			// in the file system's listing order)
			Collections.sort(ret,new Comparator<FileSource>() {

				@Override
				public int compare(FileSource o1, FileSource o2) {
					return o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
				}
			});
		}

		return ret;
	}

	private static List<FileSource>  glob(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		FileSource cwd = null;
		if( path.startsWith("~")) {
			path = expandTilde(ctx, path);
		}
		String pathSegments [] = splitFileName(path);;
		
		if( isRelative(path)) {
			cwd = ctx.console.getCurrentDirectory();
		} else {
			cwd = ctx.console.createFileSource("/");
		}

		//  these are all the files that match this segment
		glob2a(ctx,ret, cwd, pathSegments, 0);



		return ret;
	}

	public static String[] splitFileName(String path) {
		List<String> ret = new ArrayList<>();
		char[] data = path.toCharArray();
		StringBuilder buf = new StringBuilder();
		for (int idx = 0; idx < data.length; idx++) {
			char c = data[idx];
			switch (c) {
			case '/':
			case '\\':
				if(! buf.isEmpty()) {
					ret.add(buf.toString());
					buf = new StringBuilder();
				}
				break;
			default:
				buf.append(c);
			}
		}
		
		if(! buf.isEmpty()) {
			ret.add(buf.toString());
		}
		
		return ret.toArray(new String[ret.size()]);
	}

	public static  boolean hasWildcard(String seg) {
		for(char c : wildcards) {
			if( seg.indexOf(c)>=0) {
				return true;
			}
		}


		return false;
	};

	public static boolean isRelative(String path) {
		if(FileSourceFactory.isWindows()){
			return !(path.startsWith("/")||(path.length()>=2&& path.charAt(1)==':'));
		} else {
			return !path.startsWith("/");
		}
	}

	protected String promptAndGetReponse(ShellContext ctx, String prompt) throws IOException {
		return promptAndGetReponse(ctx.stdout, ctx.stdin, prompt);
	}

	protected String promptAndGetReponse(PrintStream out,InputStream in, String prompt) throws IOException {

		out.print(prompt);
		String ret = readLine(in);
		return ret;
	}



	protected String readLine(InputStream in) throws IOException {
		StringBuilder ret = new StringBuilder();
		int i = in.read();
		while(i>=0 && i !='\n') {
			ret.append((char)i);
			i = in.read();
		}
		return ret.toString();
	}

	private void globDir(ShellContext ctx,List<FileSource> ret,FileSource cwd,String [] segments, int segmentIdx) throws IOException {
		//System.out.println("Enter globDir for "+cwd+" idx="+segmentIdx+" seg="+segments[segmentIdx]);
		if( cwd.isDirectory()) {
			// expand dots
			String segment = segments[segmentIdx];
			segment = expandTilde(ctx,segment);
			String path = FileSourceFactory.expandDots(segment, cwd.getFileSourceFactory().getPathSeperatorChar());

			for (int idx1 = 0; idx1 < posixClass.length; idx1++) {
				String tmp = replaceAll(path,posixClass[idx1], javaClass[idx1]);
				path = tmp;
			}

			String val1 = path.replace(".", "[.]");
			String val2 = val1.replace("*", ".*");

			Pattern p = Pattern.compile(val2);

			FileSource[] kids = cwd.listFiles();
			if( kids !=null ) {
				for(FileSource file : kids) {
					String name = file.getName();
					Matcher m = p.matcher(name);
					if( m.matches()) {
						if( segmentIdx < (segments.length-1)) {
							globDir(ctx,ret, file, segments,segmentIdx+1);
						} else {
							ret.add(file);
						}
					} else {
						if( segmentIdx < (segments.length-1)) {
							globDir(ctx,ret, file, segments,segmentIdx+1);
						}
					}
				}
			}
		}
		//System.out.println("Exit globDir for "+cwd+" idx="+segmentIdx+" seg="+segments[segmentIdx]);
	}

	public void setArgs(Argument[] args) {
		this.args = args;

	}

	public String toColumns(ShellContext ctx, List<String> out) {
		StringBuilder ret = new StringBuilder();
		int w = ctx.console.getTerminalWidth();
		int max = 0;
		for(String line : out) {
			max = Math.max(max, line.length());
		}
		// count for line end
		max+=2;
		int cols = w / max;
		StringBuilder tmp = new StringBuilder();
		for(int idx=0,sz=out.size(); idx < sz; idx++ ) {
			if(idx>0 && idx % cols == 0 ) {
				ret.append('\n');
			}
			tmp.setLength(0);
			tmp.append(out.get(idx));
			while(tmp.length() < max) {
				tmp.append(' ');
			}
			ret.append(tmp);
		}

		return ret.toString();
	}

}
