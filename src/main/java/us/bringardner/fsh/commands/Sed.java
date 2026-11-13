package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * sed, modeled on the GNU sed: a stream editor that reads lines into a pattern space, runs a
 * script of commands on each, and prints the result. Exit status 0, 1 for a bad script or
 * usage, 2 when an input file couldn't be read, 4 on an I/O error, or the code given to q/Q.
 */
public class Sed extends ShellCommand{
	static String name = "sed";
	static String help = "sed [OPTION]... {SCRIPT-ONLY-IF-NO-OTHER-SCRIPT} [FILE ...]\n"
			+ "\tEdit a stream: for each line of FILE (or standard input) run the SCRIPT and print the result.\n"
			+ "\t-n, --quiet, --silent       don't print the pattern space at the end of each cycle\n"
			+ "\t-e SCRIPT, --expression=SCRIPT   add the commands in SCRIPT (may be repeated)\n"
			+ "\t-f FILE, --file=FILE        add the commands in FILE\n"
			+ "\t-E, -r, --regexp-extended   extended regular expressions\n"
			+ "\t-i[SUFFIX], --in-place[=SUFFIX]  edit the files in place (keep a backup if SUFFIX is given; * in it is the name)\n"
			+ "\t-s, --separate              treat the files as separate, not one stream\n"
			+ "\tAddresses: N  $  /RE/  \\cREc  FIRST~STEP  ADDR1,ADDR2  ADDR1,+N  ADDR1,~N  0,/RE/  (RE may end in I or M)  ADDR!\n"
			+ "\tCommands: {} = a i c d D g G h H l n N p P q Q r w s y x z b t T : #\n"
			+ "\t  s/RE/REPLACEMENT/FLAGS    flags g p N i I m M w FILE; the replacement has & \\1..\\9 \\n \\L \\U \\l \\u \\E\n"
			+ "\t  y/SOURCE/DEST/            transliterate characters\n"
			+ "\t  a TEXT, i TEXT, c TEXT    append, insert, or replace with TEXT (a\\ and a newline for more lines)\n"
			+ "\t  q[CODE], Q[CODE]          quit (q prints the pattern space first)\n"
			+ "\t  r FILE, w FILE            read a file onto the output, write the pattern space to a file (/dev/stdout, /dev/stderr)\n"
			+ "\t  b/t/T LABEL               branch always / if a substitution was made / if none was; :LABEL defines it\n"
			+ "\tWith no FILE, or when FILE is -, read standard input.";

	private static final String USAGE = "Usage: sed [OPTION]... {script-only-if-no-other-script} [input-file]...";

	/** a script or usage error (exit status 1) */
	private static class SedException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		SedException(String msg) {
			super(msg);
		}
	}

	private static class Rx {
		/** null means the last regular expression used */
		Pattern pattern;
	}

	private static class Addr {
		/** n line, $ last line, r regex, s first~step, + addr1,+N, ~ addr1,~N */
		char type;
		long n, step;
		Rx rx;
	}

	private static class Tok {
		String lit;
		int group = -1;
		char caseOp;
	}

	private static class Cmd {
		char op;
		Addr a1, a2;
		boolean neg;
		// range state
		boolean active, rangeEnd;
		long endLine;
		// arguments
		String text;
		int jump = -1;
		int intArg = -1;
		Rx rx;
		List<Tok> repl;
		boolean global, print;
		int nth = 1;
		String wfile;
		String ysrc, ydst;
	}

	/** a line of input, and whether it ended with a newline */
	private static class Line {
		String text;
		boolean nl;
		Line(String t, boolean n) {
			text = t;
			nl = n;
		}
	}

	/** an output that adds the newline a last line was missing when something follows it */
	private static class Out {
		PrintStream ps;
		boolean missing;
		Out(PrintStream ps) {
			this.ps = ps;
		}
		void line(String text, boolean nl) {
			if( missing ) {
				ps.print('\n');
				missing = false;
			}
			ps.print(text);
			if( nl ) {
				ps.print('\n');
			} else {
				missing = true;
			}
		}
		void raw(String text) {
			if( missing ) {
				ps.print('\n');
				missing = false;
			}
			ps.print(text);
		}
	}

	private static class Src {
		String label;
		FileSource file;
		boolean stdin;
	}

	// ---------------------------------------------------------------- parsing

	private static class Parser {
		final String s;
		final boolean ere;
		int pos;
		final List<Cmd> cmds = new ArrayList<>();
		final Map<String, Integer> labels = new HashMap<>();
		final List<Integer> stack = new ArrayList<>();

		Parser(String s, boolean ere) {
			this.s = s;
			this.ere = ere;
		}

		SedException err(String msg) {
			return new SedException("-e expression #1, char "+pos+": "+msg);
		}

		boolean more() {
			return pos < s.length();
		}

		char peek() {
			return pos < s.length() ? s.charAt(pos) : 0;
		}

		void skipBlanks() {
			while( pos < s.length() && (s.charAt(pos) == ' ' || s.charAt(pos) == '\t')) {
				pos++;
			}
		}

		long number() {
			int start = pos;
			while( pos < s.length() && Character.isDigit(s.charAt(pos))) {
				pos++;
			}
			try {
				return Long.parseLong(s.substring(start, pos));
			} catch (NumberFormatException e) {
				throw err("number too large");
			}
		}

		/** the text up to an unescaped delim; \delim is the delimiter, other escapes are kept */
		String delimited(char delim, String what) {
			StringBuilder sb = new StringBuilder();
			while( pos < s.length()) {
				char c = s.charAt(pos++);
				if( c == '\\' && pos < s.length()) {
					char d = s.charAt(pos++);
					if( d == delim ) {
						sb.append(d);
					} else {
						sb.append('\\').append(d);
					}
				} else if( c == delim ) {
					return sb.toString();
				} else {
					sb.append(c);
				}
			}
			throw err("unterminated "+what);
		}

		Pattern compile(String raw, boolean ci, boolean multi) {
			if( raw.isEmpty()) {
				return null;
			}
			StringBuilder prep = new StringBuilder();
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if( c == '\\' && i+1 < raw.length()) {
					char d = raw.charAt(++i);
					if( d == 'n' ) {
						prep.append('\n');
					} else if( d == 't' ) {
						prep.append('\t');
					} else {
						prep.append('\\').append(d);
					}
				} else {
					prep.append(c);
				}
			}
			int flags = Pattern.DOTALL | Pattern.UNIX_LINES;
			if( ci ) {
				flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			}
			if( multi ) {
				flags |= Pattern.MULTILINE;
			}
			try {
				return Pattern.compile(Grep.convert(prep.toString(), ere, multi ? "$" : "\\z"), flags);
			} catch (PatternSyntaxException e) {
				throw err(e.getDescription());
			}
		}

		Addr address(boolean second) {
			char c = peek();
			Addr a = new Addr();
			if( Character.isDigit(c)) {
				a.n = number();
				a.type = 'n';
				if( !second && peek() == '~' ) {
					pos++;
					a.step = number();
					a.type = 's';
				}
			} else if( c == '$' ) {
				pos++;
				a.type = '$';
			} else if( c == '/' || c == '\\' ) {
				pos++;
				char delim = c;
				if( c == '\\' ) {
					if( !more()) {
						throw err("unexpected end of script");
					}
					delim = s.charAt(pos++);
				}
				String raw = delimited(delim, "address regex");
				boolean ci = false, multi = false;
				while( peek() == 'I' || peek() == 'M' ) {
					if( peek() == 'I' ) {
						ci = true;
					} else {
						multi = true;
					}
					pos++;
				}
				a.type = 'r';
				a.rx = new Rx();
				a.rx.pattern = compile(raw, ci, multi);
			} else if( second && (c == '+' || c == '~')) {
				pos++;
				a.type = c;
				a.n = number();
			} else {
				return null;
			}
			return a;
		}

		/** after a command: blanks, then ; or a newline, or } or # (left for the next command) or the end */
		void endOfCommand() {
			skipBlanks();
			if( !more()) {
				return;
			}
			char c = peek();
			if( c == ';' || c == '\n' ) {
				pos++;
			} else if( c != '}' && c != '#' ) {
				throw err("extra characters after command");
			}
		}

		String label() {
			skipBlanks();
			int start = pos;
			while( pos < s.length() && s.charAt(pos) != '\n' && s.charAt(pos) != ';' && s.charAt(pos) != '}' && !Character.isWhitespace(s.charAt(pos))) {
				pos++;
			}
			return s.substring(start, pos);
		}

		String restOfLine() {
			skipBlanks();
			int start = pos;
			while( pos < s.length() && s.charAt(pos) != '\n') {
				pos++;
			}
			return s.substring(start, pos);
		}

		String text() {
			skipBlanks();
			if( peek() == '\\' ) {
				pos++;
				int save = pos;
				skipBlanks();
				if( peek() == '\n' ) {
					pos++;
				} else {
					// a\text: the text as it is, so its leading white space stays
					pos = save;
				}
			}
			StringBuilder sb = new StringBuilder();
			while( pos < s.length()) {
				char c = s.charAt(pos++);
				if( c == '\\' && pos < s.length()) {
					sb.append(s.charAt(pos++));
				} else if( c == '\n' ) {
					break;
				} else {
					sb.append(c);
				}
			}
			if( sb.length() == 0 ) {
				throw err("expected \\ after `a', `c' or `i'");
			}
			return sb.toString();
		}

		List<Tok> replacement(String raw) {
			List<Tok> ret = new ArrayList<>();
			StringBuilder lit = new StringBuilder();
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if( c == '&' ) {
					flush(ret, lit);
					Tok t = new Tok();
					t.group = 0;
					ret.add(t);
				} else if( c == '\\' && i+1 < raw.length()) {
					char d = raw.charAt(++i);
					if( Character.isDigit(d)) {
						flush(ret, lit);
						Tok t = new Tok();
						t.group = d-'0';
						ret.add(t);
					} else if( "LUluE".indexOf(d) >= 0 ) {
						flush(ret, lit);
						Tok t = new Tok();
						t.caseOp = d;
						ret.add(t);
					} else {
						switch (d) {
						case 'n': lit.append('\n'); break;
						case 't': lit.append('\t'); break;
						case 'r': lit.append('\r'); break;
						case 'f': lit.append('\f'); break;
						case 'v': lit.append('\u000b'); break;
						case 'a': lit.append('\u0007'); break;
						case 'x': {
							int end = i+1;
							while( end < raw.length() && end < i+3 && Character.digit(raw.charAt(end), 16) >= 0 ) {
								end++;
							}
							if( end > i+1 ) {
								lit.append((char) Integer.parseInt(raw.substring(i+1, end), 16));
								i = end-1;
							} else {
								lit.append('x');
							}
							break;
						}
						default: lit.append(d);
						}
					}
				} else {
					lit.append(c);
				}
			}
			flush(ret, lit);
			return ret;
		}

		private static void flush(List<Tok> toks, StringBuilder lit) {
			if( lit.length() > 0 ) {
				Tok t = new Tok();
				t.lit = lit.toString();
				toks.add(t);
				lit.setLength(0);
			}
		}

		String unescapeY(String raw) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if( c == '\\' && i+1 < raw.length()) {
					char d = raw.charAt(++i);
					if( d == 'n' ) {
						sb.append('\n');
					} else if( d == 't' ) {
						sb.append('\t');
					} else if( d == '\\' ) {
						sb.append('\\');
					} else {
						throw err("unknown command: `\\"+d+"'");
					}
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}

		void parse() {
			while( true ) {
				while( more() && (Character.isWhitespace(peek()) || peek() == ';')) {
					pos++;
				}
				if( !more()) {
					break;
				}
				Cmd c = new Cmd();
				c.a1 = address(false);
				if( c.a1 != null ) {
					skipBlanks();
					if( peek() == ',' ) {
						pos++;
						skipBlanks();
						c.a2 = address(true);
						if( c.a2 == null ) {
							throw err("unexpected `,'");
						}
					}
					if( c.a1.type == 'n' && c.a1.n == 0 && (c.a2 == null || c.a2.type != 'r')) {
						throw err("invalid usage of line address 0");
					}
				}
				skipBlanks();
				while( peek() == '!' ) {
					c.neg = true;
					pos++;
					skipBlanks();
				}
				if( !more()) {
					throw err("missing command");
				}
				c.op = s.charAt(pos++);
				switch (c.op) {
				case '#':
					if( c.a1 != null ) {
						throw err("comments don't accept any addresses");
					}
					while( more() && peek() != '\n') {
						pos++;
					}
					break;
				case '{':
					stack.add(cmds.size());
					cmds.add(c);
					break;
				case '}':
					if( c.a1 != null ) {
						throw err("} doesn't want any addresses");
					}
					if( stack.isEmpty()) {
						throw err("unexpected `}'");
					}
					cmds.get(stack.remove(stack.size()-1)).jump = cmds.size();
					cmds.add(c);
					endOfCommand();
					break;
				case ':': {
					if( c.a1 != null ) {
						throw err(": doesn't want any addresses");
					}
					String label = label();
					if( label.isEmpty()) {
						throw err("\":\" lacks a label");
					}
					labels.put(label, cmds.size());
					break;
				}
				case 'b': case 't': case 'T':
					c.text = label();
					cmds.add(c);
					endOfCommand();
					break;
				case 'a': case 'i': case 'c':
					c.text = text();
					cmds.add(c);
					break;
				case 'r': case 'w': {
					c.text = restOfLine();
					if( c.text.isEmpty()) {
						throw err("missing filename in r/R/w/W commands");
					}
					cmds.add(c);
					break;
				}
				case 'q': case 'Q': case 'l': case 'L':
					skipBlanks();
					if( Character.isDigit(peek())) {
						c.intArg = (int) number();
					}
					cmds.add(c);
					endOfCommand();
					break;
				case '=': case 'd': case 'D': case 'g': case 'G': case 'h': case 'H':
				case 'n': case 'N': case 'p': case 'P': case 'x': case 'z':
					cmds.add(c);
					endOfCommand();
					break;
				case 'y': {
					if( !more()) {
						throw err("unterminated `y' command");
					}
					char delim = s.charAt(pos++);
					c.ysrc = unescapeY(delimited(delim, "`y' command"));
					c.ydst = unescapeY(delimited(delim, "`y' command"));
					if( c.ysrc.length() != c.ydst.length()) {
						throw err("strings for `y' command are different lengths");
					}
					cmds.add(c);
					endOfCommand();
					break;
				}
				case 's': {
					if( !more()) {
						throw err("unterminated `s' command");
					}
					char delim = s.charAt(pos++);
					String re = delimited(delim, "`s' command");
					String rep = delimited(delim, "`s' command");
					boolean ci = false, multi = false;
					boolean flags = true;
					while( flags && more()) {
						char f = peek();
						switch (f) {
						case 'g': c.global = true; pos++; break;
						case 'p': c.print = true; pos++; break;
						case 'i': case 'I': ci = true; pos++; break;
						case 'm': case 'M': multi = true; pos++; break;
						case 'e': throw err("the `e' flag is not supported");
						case 'w':
							pos++;
							c.wfile = restOfLine();
							if( c.wfile.isEmpty()) {
								throw err("missing filename in r/R/w/W commands");
							}
							flags = false;
							break;
						default:
							if( Character.isDigit(f)) {
								c.nth = (int) number();
								if( c.nth == 0 ) {
									throw err("number option to `s' command may not be zero");
								}
							} else {
								flags = false;
							}
						}
					}
					c.rx = new Rx();
					c.rx.pattern = compile(re, ci, multi);
					c.repl = replacement(rep);
					if( c.rx.pattern != null ) {
						int groups = c.rx.pattern.matcher("").groupCount();
						for (Tok t : c.repl) {
							if( t.group > groups ) {
								throw err("invalid reference \\"+t.group+" on `s' command's RHS");
							}
						}
					}
					cmds.add(c);
					if( c.wfile == null ) {
						endOfCommand();
					}
					break;
				}
				default:
					throw err("unknown command: `"+c.op+"'");
				}
			}
			if( !stack.isEmpty()) {
				throw new SedException("-e expression #1, char 0: unmatched `{'");
			}
			for (Cmd c : cmds) {
				if( c.op == 'b' || c.op == 't' || c.op == 'T' ) {
					if( c.text.isEmpty()) {
						c.jump = cmds.size();
					} else {
						Integer target = labels.get(c.text);
						if( target == null ) {
							throw new SedException("-e expression #1, char "+s.length()+": can't find label for jump to `"+c.text+"'");
						}
						c.jump = target;
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- input

	private static class Input {
		final ShellContext ctx;
		final List<Src> srcs;
		final Exec ex;
		int idx;
		Reader cur;
		Line pending;

		Input(ShellContext ctx, List<Src> srcs, Exec ex) {
			this.ctx = ctx;
			this.srcs = srcs;
			this.ex = ex;
		}

		Line next() throws IOException {
			if( pending != null ) {
				Line l = pending;
				pending = null;
				return l;
			}
			return read();
		}

		boolean hasNext() throws IOException {
			if( pending == null ) {
				pending = read();
			}
			return pending != null;
		}

		private Line read() throws IOException {
			while( true ) {
				if( cur == null ) {
					if( idx >= srcs.size()) {
						return null;
					}
					Src src = srcs.get(idx++);
					if( src.stdin ) {
						cur = new InputStreamReader(ctx.stdin, StandardCharsets.UTF_8);
					} else {
						try {
							cur = new InputStreamReader(src.file.getInputStream(), StandardCharsets.UTF_8);
						} catch (IOException e) {
							ctx.stderr.println("sed: can't read "+src.label+": "+e.getMessage());
							ex.status = 2;
							continue;
						}
					}
				}
				StringBuilder sb = new StringBuilder();
				int c;
				boolean any = false;
				while( (c = cur.read()) >= 0 ) {
					any = true;
					if( c == '\n' ) {
						return new Line(sb.toString(), true);
					}
					sb.append((char)c);
				}
				if( any ) {
					return new Line(sb.toString(), false);
				}
				if( cur != null && !srcs.isEmpty() && !isStdin(idx-1)) {
					cur.close();
				}
				cur = null;
			}
		}

		private boolean isStdin(int i) {
			return i >= 0 && i < srcs.size() && srcs.get(i).stdin;
		}
	}

	// ---------------------------------------------------------------- execution

	private static class App {
		String text;
		boolean file;
	}

	private static class Exec {
		final ShellContext ctx;
		final List<Cmd> cmds;
		final boolean silent;
		final Out realOut;
		Out out;
		final Map<String, Out> wfiles = new HashMap<>();
		final List<OutputStream> open = new ArrayList<>();
		String hold = "";
		Pattern last;
		int status;
		int exit = -1;
		boolean quit;

		String ps = "";
		boolean nl = true;
		long lineNo;
		boolean replaced;
		final List<App> appends = new ArrayList<>();

		Exec(ShellContext ctx, List<Cmd> cmds, boolean silent) {
			this.ctx = ctx;
			this.cmds = cmds;
			this.silent = silent;
			this.realOut = new Out(ctx.stdout);
			this.out = realOut;
		}

		Out wfile(String file) throws IOException {
			Out o = wfiles.get(file);
			if( o != null ) {
				return o;
			}
			if( file.equals("/dev/stdout")) {
				o = realOut;
			} else if( file.equals("/dev/stderr")) {
				o = new Out(ctx.stderr);
			} else {
				OutputStream os = ctx.console.createFileSource(file).getOutputStream();
				open.add(os);
				o = new Out(new PrintStream(os, false, "UTF-8"));
			}
			wfiles.put(file, o);
			return o;
		}

		void openFiles() throws IOException {
			for (Cmd c : cmds) {
				if( c.op == 'w' ) {
					wfile(c.text);
				} else if( c.op == 's' && c.wfile != null ) {
					wfile(c.wfile);
				}
			}
		}

		void closeFiles() {
			for (Out o : wfiles.values()) {
				o.ps.flush();
			}
			for (OutputStream os : open) {
				try {
					os.close();
				} catch (IOException e) {
					status = 4;
				}
			}
		}

		Pattern resolve(Rx rx) {
			Pattern p = rx.pattern != null ? rx.pattern : last;
			if( p == null ) {
				throw new SedException("no previous regular expression");
			}
			last = p;
			return p;
		}

		boolean test(Addr a, Input in) throws IOException {
			switch (a.type) {
			case 'n': return lineNo == a.n;
			case '$': return !in.hasNext();
			case 'r': return resolve(a.rx).matcher(ps).find();
			case 's':
				if( a.step <= 0 ) {
					return lineNo == a.n;
				}
				return lineNo >= a.n && (lineNo-a.n) % a.step == 0;
			}
			return false;
		}

		boolean match(Cmd c, Input in) throws IOException {
			c.rangeEnd = true;
			if( c.a1 == null ) {
				return true;
			}
			if( c.a2 == null ) {
				return test(c.a1, in);
			}
			if( c.active ) {
				Addr a2 = c.a2;
				if( a2.type == 'r' ) {
					if( resolve(a2.rx).matcher(ps).find()) {
						c.active = false;
					} else {
						c.rangeEnd = false;
					}
					return true;
				}
				if( a2.type == '$' ) {
					if( !in.hasNext()) {
						c.active = false;
					} else {
						c.rangeEnd = false;
					}
					return true;
				}
				if( lineNo > c.endLine ) {
					c.active = false;
				} else {
					if( lineNo == c.endLine ) {
						c.active = false;
					} else {
						c.rangeEnd = false;
					}
					return true;
				}
			}
			if( !test(c.a1, in)) {
				return false;
			}
			Addr a2 = c.a2;
			switch (a2.type) {
			case 'n':
				if( a2.n > lineNo ) {
					c.active = true;
					c.endLine = a2.n;
					c.rangeEnd = false;
				}
				break;
			case '+':
				if( a2.n > 0 ) {
					c.active = true;
					c.endLine = lineNo+a2.n;
					c.rangeEnd = false;
				}
				break;
			case '~':
				if( a2.n > 0 && lineNo % a2.n != 0 ) {
					c.active = true;
					c.endLine = (lineNo/a2.n+1)*a2.n;
					c.rangeEnd = false;
				}
				break;
			default:
				c.active = true;
				c.rangeEnd = false;
			}
			return true;
		}

		void flushAppends() throws IOException {
			for (App a : appends) {
				if( a.file ) {
					FileSource f = ctx.console.createFileSource(a.text);
					if( f.exists() && !f.isDirectory()) {
						try (InputStream is = f.getInputStream()) {
							out.raw(new String(is.readAllBytes(), StandardCharsets.UTF_8));
						}
					}
				} else {
					out.line(a.text, true);
				}
			}
			appends.clear();
		}

		/** run the script over the input; the hold space and the last regex carry over from one input to the next */
		void run(Input in) throws IOException {
			lineNo = 0;
			for (Cmd c : cmds) {
				c.active = c.a1 != null && c.a1.type == 'n' && c.a1.n == 0 && c.a2 != null;
			}
			boolean skipRead = false;
			while( !quit ) {
				if( !skipRead ) {
					Line l = in.next();
					if( l == null ) {
						break;
					}
					ps = l.text;
					nl = l.nl;
					lineNo++;
					replaced = false;
				}
				skipRead = false;
				boolean autoprint = true;
				int pc = 0;
				cycle:
				while( pc < cmds.size()) {
					Cmd c = cmds.get(pc);
					if( match(c, in) == c.neg ) {
						pc = c.op == '{' ? c.jump : pc+1;
						continue;
					}
					switch (c.op) {
					case '{': case '}': break;
					case '=': out.line(Long.toString(lineNo), true); break;
					case 'a': {
						App a = new App();
						a.text = c.text;
						appends.add(a);
						break;
					}
					case 'r': {
						App a = new App();
						a.text = c.text;
						a.file = true;
						appends.add(a);
						break;
					}
					case 'i': out.line(c.text, true); break;
					case 'c':
						if( c.a2 == null || c.neg || c.rangeEnd ) {
							out.line(c.text, true);
						}
						autoprint = false;
						break cycle;
					case 'd':
						autoprint = false;
						break cycle;
					case 'D': {
						int nlPos = ps.indexOf('\n');
						autoprint = false;
						if( nlPos >= 0 ) {
							ps = ps.substring(nlPos+1);
							skipRead = true;
						}
						break cycle;
					}
					case 'g': ps = hold; break;
					case 'G': ps = ps+"\n"+hold; break;
					case 'h': hold = ps; break;
					case 'H': hold = hold+"\n"+ps; break;
					case 'x': {
						String t = ps;
						ps = hold;
						hold = t;
						break;
					}
					case 'l': list(c.intArg < 0 ? 70 : c.intArg); break;
					case 'n':
						if( !in.hasNext()) {
							quit = true;
							break cycle;
						}
						if( !silent ) {
							out.line(ps, nl);
						}
						flushAppends();
						{
							Line l = in.next();
							ps = l.text;
							nl = l.nl;
							lineNo++;
						}
						break;
					case 'N':
						if( !in.hasNext()) {
							quit = true;
							break cycle;
						}
						flushAppends();
						{
							Line l = in.next();
							ps = ps+"\n"+l.text;
							nl = l.nl;
							lineNo++;
						}
						break;
					case 'p': out.line(ps, true); break;
					case 'P': {
						int nlPos = ps.indexOf('\n');
						out.line(nlPos < 0 ? ps : ps.substring(0, nlPos), true);
						break;
					}
					case 'q':
						quit = true;
						exit = Math.max(c.intArg, 0);
						break cycle;
					case 'Q':
						quit = true;
						exit = Math.max(c.intArg, 0);
						autoprint = false;
						appends.clear();
						break cycle;
					case 'w': wfile(c.text).line(ps, true); break;
					case 'z': ps = ""; break;
					case 'y': {
						StringBuilder sb = new StringBuilder(ps.length());
						for (int i = 0; i < ps.length(); i++) {
							int k = c.ysrc.indexOf(ps.charAt(i));
							sb.append(k >= 0 ? c.ydst.charAt(k) : ps.charAt(i));
						}
						ps = sb.toString();
						break;
					}
					case 's':
						if( substitute(c)) {
							replaced = true;
							if( c.print ) {
								out.line(ps, true);
							}
							if( c.wfile != null ) {
								wfile(c.wfile).line(ps, true);
							}
						}
						break;
					case 'b':
						pc = c.jump;
						continue;
					case 't':
						if( replaced ) {
							replaced = false;
							pc = c.jump;
							continue;
						}
						break;
					case 'T':
						if( !replaced ) {
							pc = c.jump;
							continue;
						}
						replaced = false;
						break;
					default:
						break;
					}
					pc++;
				}
				if( autoprint && !silent ) {
					out.line(ps, nl);
				}
				flushAppends();
			}
		}

		boolean substitute(Cmd c) {
			Matcher m = resolve(c.rx).matcher(ps);
			StringBuilder sb = new StringBuilder();
			int pos = 0, count = 0, prevEnd = -1;
			boolean did = false;
			while( m.find()) {
				if( m.end() == m.start() && m.start() == prevEnd ) {
					continue;
				}
				count++;
				prevEnd = m.end();
				if( count < c.nth ) {
					continue;
				}
				sb.append(ps, pos, m.start());
				expand(c.repl, m, sb);
				pos = m.end();
				did = true;
				if( !c.global ) {
					break;
				}
			}
			if( !did ) {
				return false;
			}
			sb.append(ps, pos, ps.length());
			ps = sb.toString();
			return true;
		}

		static void expand(List<Tok> repl, Matcher m, StringBuilder sb) {
			char mode = 0, one = 0;
			for (Tok t : repl) {
				if( t.caseOp != 0 ) {
					switch (t.caseOp) {
					case 'U': case 'L': mode = t.caseOp; break;
					case 'E': mode = 0; one = 0; break;
					default: one = t.caseOp;
					}
					continue;
				}
				String text = t.lit;
				if( text == null ) {
					text = t.group <= m.groupCount() ? m.group(t.group) : null;
					if( text == null ) {
						text = "";
					}
				}
				for (int i = 0; i < text.length(); i++) {
					char ch = text.charAt(i);
					if( one != 0 ) {
						ch = one == 'u' ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
						one = 0;
					} else if( mode == 'U' ) {
						ch = Character.toUpperCase(ch);
					} else if( mode == 'L' ) {
						ch = Character.toLowerCase(ch);
					}
					sb.append(ch);
				}
			}
		}

		/** the l command: the pattern space with the characters that don't show escaped, wrapped at width */
		void list(int width) {
			StringBuilder sb = new StringBuilder();
			int lineLen = 0;
			for (int i = 0; i < ps.length(); i++) {
				char ch = ps.charAt(i);
				String item;
				switch (ch) {
				case '\\': item = "\\\\"; break;
				case 7: item = "\\a"; break;
				case '\b': item = "\\b"; break;
				case '\f': item = "\\f"; break;
				case '\n': item = "\\n"; break;
				case '\r': item = "\\r"; break;
				case '\t': item = "\\t"; break;
				case 11: item = "\\v"; break;
				default:
					if( ch < 32 || ch == 127 ) {
						item = String.format("\\%03o", (int) ch);
					} else {
						item = String.valueOf(ch);
					}
				}
				if( width > 1 && lineLen+item.length() > width-1 ) {
					sb.append("\\\n");
					lineLen = 0;
				}
				sb.append(item);
				lineLen += item.length();
			}
			sb.append('$');
			out.line(sb.toString(), true);
		}
	}

	public Sed() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> pieces = new ArrayList<>();
		List<String> words = new ArrayList<>();
		boolean silent = false, ere = false, separate = false, inPlace = false;
		String suffix = "";
		boolean optionsEnded = false;
		try {
			for (int idx = 0; idx < args.length; idx++) {
				String arg = ""+args[idx].getValue(ctx);
				if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
					words.add(arg);
				} else if( arg.equals("--")) {
					optionsEnded = true;
				} else if( arg.startsWith("--")) {
					String key = arg, val = null;
					int eq = arg.indexOf('=');
					if( eq > 0 ) {
						key = arg.substring(0, eq);
						val = arg.substring(eq+1);
					}
					switch (key) {
					case "--quiet": case "--silent": silent = true; break;
					case "--regexp-extended": ere = true; break;
					case "--separate": separate = true; break;
					case "--in-place":
						inPlace = true;
						suffix = val == null ? "" : val;
						break;
					case "--posix": case "--debug": case "--sandbox": case "--unbuffered":
					case "--binary": case "--follow-symlinks":
						break;
					case "--expression": case "--file": case "--line-length": {
						if( val == null ) {
							if( idx+1 >= args.length ) {
								ctx.stderr.println("sed: option '"+key+"' requires an argument");
								ctx.stderr.println(USAGE);
								return 1;
							}
							val = ""+args[++idx].getValue(ctx);
						}
						if( key.equals("--expression")) {
							pieces.add(val);
						} else if( key.equals("--file")) {
							pieces.add(scriptFile(ctx, val));
						}
						break;
					}
					default:
						ctx.stderr.println("sed: unrecognized option '"+arg+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				} else {
					String cluster = arg.substring(1);
					for (int c = 0; c < cluster.length(); c++) {
						char ch = cluster.charAt(c);
						switch (ch) {
						case 'n': silent = true; break;
						case 'E': case 'r': ere = true; break;
						case 's': separate = true; break;
						case 'u': case 'b': break;
						case 'i':
							inPlace = true;
							suffix = cluster.substring(c+1);
							c = cluster.length();
							break;
						case 'e': case 'f': case 'l': {
							String val;
							if( c+1 < cluster.length()) {
								val = cluster.substring(c+1);
							} else if( idx+1 < args.length ) {
								val = ""+args[++idx].getValue(ctx);
							} else {
								ctx.stderr.println("sed: option requires an argument -- '"+ch+"'");
								ctx.stderr.println(USAGE);
								return 1;
							}
							if( ch == 'e' ) {
								pieces.add(val);
							} else if( ch == 'f' ) {
								pieces.add(scriptFile(ctx, val));
							}
							c = cluster.length();
							break;
						}
						default:
							ctx.stderr.println("sed: invalid option -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 1;
						}
					}
				}
			}

			if( pieces.isEmpty()) {
				if( words.isEmpty()) {
					ctx.stderr.println(USAGE);
					return 1;
				}
				pieces.add(words.remove(0));
			}
			String script = String.join("\n", pieces);
			if( script.startsWith("#n") && (script.length() == 2 || script.charAt(2) == '\n')) {
				silent = true;
			}
			Parser parser = new Parser(script, ere);
			parser.parse();
			return execute(ctx, parser.cmds, words, silent, separate || inPlace, inPlace, suffix);
		} catch (SedException e) {
			ctx.stderr.println("sed: "+e.getMessage());
			return 1;
		}
	}

	private static String scriptFile(ShellContext ctx, String path) throws IOException {
		FileSource f = ctx.console.createFileSource(path);
		if( !f.exists() || f.isDirectory()) {
			throw new SedException("couldn't open file "+path+": No such file or directory");
		}
		try (InputStream in = f.getInputStream()) {
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return text.endsWith("\n") ? text.substring(0, text.length()-1) : text;
		}
	}

	private int execute(ShellContext ctx, List<Cmd> cmds, List<String> files, boolean silent, boolean separate, boolean inPlace, String suffix) throws IOException {
		Exec ex = new Exec(ctx, cmds, silent);
		List<Src> srcs = new ArrayList<>();
		if( files.isEmpty()) {
			files.add("-");
		}
		for (String path : files) {
			if( path.equals("-")) {
				if( inPlace ) {
					ctx.stderr.println("sed: couldn't edit -: not a regular file");
					ex.status = 4;
					continue;
				}
				Src s = new Src();
				s.label = "-";
				s.stdin = true;
				srcs.add(s);
				continue;
			}
			int slash = path.lastIndexOf('/');
			String dirPrefix = slash >= 0 ? path.substring(0, slash+1) : "";
			boolean glob = hasWildcard(path) && !ctx.console.createFileSource(path).exists();
			for (FileSource file : getFiles(ctx, path)) {
				String label = glob ? dirPrefix+file.getName() : path;
				if( !file.exists()) {
					ctx.stderr.println("sed: can't read "+label+": No such file or directory");
					ex.status = 2;
				} else if( file.isDirectory()) {
					if( inPlace ) {
						ctx.stderr.println("sed: couldn't edit "+label+": not a regular file");
						ex.status = 4;
					} else {
						ctx.stderr.println("sed: read error on "+label+": Is a directory");
						ex.status = 2;
					}
				} else {
					Src s = new Src();
					s.label = label;
					s.file = file;
					srcs.add(s);
				}
			}
		}

		try {
			ex.openFiles();
			if( !separate ) {
				ex.run(new Input(ctx, srcs, ex));
			} else {
				for (Src src : srcs) {
					if( ex.quit ) {
						break;
					}
					List<Src> one = new ArrayList<>();
					one.add(src);
					if( !inPlace ) {
						ex.run(new Input(ctx, one, ex));
						continue;
					}
					ByteArrayOutputStream buf = new ByteArrayOutputStream();
					PrintStream ps = new PrintStream(buf, false, "UTF-8");
					ex.out = new Out(ps);
					try {
						ex.run(new Input(ctx, one, ex));
					} finally {
						ps.flush();
						ex.out = ex.realOut;
					}
					byte [] edited = buf.toByteArray();
					if( !suffix.isEmpty()) {
						String base = src.file.getName();
						String backup = suffix.contains("*") ? suffix.replace("*", base) : base+suffix;
						String dir = src.label.contains("/") ? src.label.substring(0, src.label.lastIndexOf('/')+1) : "";
						if( backup.contains("/")) {
							dir = "";
						}
						try (InputStream in = src.file.getInputStream();
								OutputStream os = ctx.console.createFileSource(dir+backup).getOutputStream()) {
							in.transferTo(os);
						}
					}
					try (OutputStream os = src.file.getOutputStream()) {
						os.write(edited);
					}
				}
			}
		} catch (SedException e) {
			ctx.stderr.println("sed: "+e.getMessage());
			ex.status = 1;
		} finally {
			ex.closeFiles();
		}
		ctx.stdout.flush();
		if( ex.exit > 0 ) {
			return ex.exit;
		}
		return ex.status;
	}
}
