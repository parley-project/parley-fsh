package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * grep, modeled on the GNU grep: prints the lines of files (or standard input) that match a
 * pattern. Exit status 0 when a line was selected, 1 when none was, 2 on an error.
 */
public class Grep extends ShellCommand{
	static String name = "grep";
	static String help = "grep [OPTION]... PATTERNS [FILE ...]\n"
			+ "\tPrint the lines of each FILE (or standard input) that match PATTERNS.\n"
			+ "\tPatterns:\n"
			+ "\t-E, --extended-regexp     PATTERNS are extended regular expressions\n"
			+ "\t-F, --fixed-strings       PATTERNS are strings\n"
			+ "\t-G, --basic-regexp        PATTERNS are basic regular expressions (the default)\n"
			+ "\t-P, --perl-regexp         PATTERNS are Java regular expressions\n"
			+ "\t-e, --regexp=PATTERNS     use PATTERNS (may be repeated; a newline separates patterns)\n"
			+ "\t-f, --file=FILE           take the patterns from FILE, one per line\n"
			+ "\t-i, --ignore-case         ignore case\n"
			+ "\t-w, --word-regexp         match whole words only\n"
			+ "\t-x, --line-regexp         match whole lines only\n"
			+ "\tSelection and output:\n"
			+ "\t-v, --invert-match        select the lines that don't match\n"
			+ "\t-c, --count               print only a count of the selected lines per FILE\n"
			+ "\t-l, --files-with-matches  print only the names of FILEs with a selected line\n"
			+ "\t-L, --files-without-match print only the names of FILEs with no selected line\n"
			+ "\t-m, --max-count=NUM       stop after NUM selected lines\n"
			+ "\t-o, --only-matching       print only the matching parts of a line, one per line\n"
			+ "\t-q, --quiet, --silent     print nothing, exit 0 at the first selected line\n"
			+ "\t-s, --no-messages         don't report files that can't be read\n"
			+ "\t-H, --with-filename       print the file name on each line\n"
			+ "\t-h, --no-filename         don't print the file name\n"
			+ "\t-n, --line-number         print the line number\n"
			+ "\t-A NUM, -B NUM, -C NUM, -NUM   print NUM lines of context after, before, or around a match\n"
			+ "\t-a, --text                treat a binary file as text\n"
			+ "\t-I                        treat a binary file as having no match\n"
			+ "\t-r, -R, --recursive       read all the files under each directory\n"
			+ "\t--include=GLOB, --exclude=GLOB   with -r, search only the files whose name does (not) match GLOB\n"
			+ "\t--color[=WHEN]            accepted and ignored\n"
			+ "\tWith no FILE, or when FILE is -, read standard input; with -r and no FILE, search '.'.";

	private static final String USAGE = "Usage: grep [OPTION]... PATTERNS [FILE]...";

	/** one run's options and state (the command object is shared, so none of this is a field of it) */
	private static class Opts {
		char syntax = 'G';
		List<String> patterns = new ArrayList<>();
		boolean haveE;
		boolean ignoreCase, word, line, invert, count, listFiles, listNot, only, quiet, noMessages;
		boolean recursive, text, skipBinary;
		Boolean withName;
		boolean number;
		int max = -1, after, before;
		List<String> include = new ArrayList<>(), exclude = new ArrayList<>();
		Pattern pattern;
		/** something was printed (for the -- between groups of context) */
		boolean printed;
		boolean anyMatch;
		boolean error;
		boolean done;
	}

	public Grep() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Opts o = new Opts();
		List<String> files = new ArrayList<>();
		List<String> words = new ArrayList<>();
		boolean optionsEnded = false;
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
				char valueOpt = 0;
				switch (key) {
				case "--extended-regexp": o.syntax = 'E'; break;
				case "--fixed-strings": o.syntax = 'F'; break;
				case "--basic-regexp": o.syntax = 'G'; break;
				case "--perl-regexp": o.syntax = 'P'; break;
				case "--ignore-case": o.ignoreCase = true; break;
				case "--no-ignore-case": o.ignoreCase = false; break;
				case "--word-regexp": o.word = true; break;
				case "--line-regexp": o.line = true; break;
				case "--invert-match": o.invert = true; break;
				case "--count": o.count = true; break;
				case "--files-with-matches": o.listFiles = true; break;
				case "--files-without-match": o.listNot = true; break;
				case "--only-matching": o.only = true; break;
				case "--quiet": case "--silent": o.quiet = true; break;
				case "--no-messages": o.noMessages = true; break;
				case "--with-filename": o.withName = true; break;
				case "--no-filename": o.withName = false; break;
				case "--line-number": o.number = true; break;
				case "--text": o.text = true; break;
				case "--recursive": case "--dereference-recursive": o.recursive = true; break;
				case "--color": case "--colour": break;
				case "--regexp": valueOpt = 'e'; break;
				case "--file": valueOpt = 'f'; break;
				case "--max-count": valueOpt = 'm'; break;
				case "--after-context": valueOpt = 'A'; break;
				case "--before-context": valueOpt = 'B'; break;
				case "--context": valueOpt = 'C'; break;
				case "--include": valueOpt = 'I'; break;
				case "--exclude": valueOpt = 'X'; break;
				default:
					ctx.stderr.println("grep: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 2;
				}
				if( valueOpt != 0 ) {
					if( val == null ) {
						if( idx+1 >= args.length ) {
							ctx.stderr.println("grep: option '"+key+"' requires an argument");
							ctx.stderr.println(USAGE);
							return 2;
						}
						val = ""+args[++idx].getValue(ctx);
					}
					int rc = value(ctx, o, valueOpt, val);
					if( rc != 0 ) {
						return rc;
					}
				}
			} else {
				String cluster = arg.substring(1);
				int digits = -1;
				for (int c = 0; c < cluster.length(); c++) {
					char ch = cluster.charAt(c);
					if( Character.isDigit(ch)) {
						digits = (digits < 0 ? 0 : digits*10) + (ch-'0');
						o.after = o.before = digits;
						continue;
					}
					digits = -1;
					switch (ch) {
					case 'E': case 'F': case 'G': case 'P': o.syntax = ch; break;
					case 'i': case 'y': o.ignoreCase = true; break;
					case 'w': o.word = true; break;
					case 'x': o.line = true; break;
					case 'v': o.invert = true; break;
					case 'c': o.count = true; break;
					case 'l': o.listFiles = true; break;
					case 'L': o.listNot = true; break;
					case 'o': o.only = true; break;
					case 'q': o.quiet = true; break;
					case 's': o.noMessages = true; break;
					case 'H': o.withName = true; break;
					case 'h': o.withName = false; break;
					case 'n': o.number = true; break;
					case 'a': o.text = true; break;
					case 'I': o.skipBinary = true; break;
					case 'r': case 'R': o.recursive = true; break;
					case 'U': case 'T': case 'z': break;
					case 'e': case 'f': case 'm': case 'A': case 'B': case 'C': {
						String val;
						if( c+1 < cluster.length()) {
							val = cluster.substring(c+1);
						} else if( idx+1 < args.length ) {
							val = ""+args[++idx].getValue(ctx);
						} else {
							ctx.stderr.println("grep: option requires an argument -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 2;
						}
						int rc = value(ctx, o, ch, val);
						if( rc != 0 ) {
							return rc;
						}
						c = cluster.length();
						break;
					}
					default:
						ctx.stderr.println("grep: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 2;
					}
				}
			}
		}

		int first = 0;
		if( !o.haveE ) {
			if( words.isEmpty()) {
				ctx.stderr.println(USAGE);
				return 2;
			}
			addPatterns(o, words.get(0));
			first = 1;
		}
		files.addAll(words.subList(first, words.size()));

		try {
			o.pattern = compile(o);
		} catch (PatternSyntaxException e) {
			ctx.stderr.println("grep: invalid regular expression: "+e.getDescription());
			return 2;
		}

		boolean dirArg = false;
		if( o.recursive ) {
			if( files.isEmpty()) {
				dirArg = true;
			}
			for (String f : files) {
				if( !f.equals("-") && ctx.console.createFileSource(f).isDirectory()) {
					dirArg = true;
				}
			}
		}
		boolean showName = o.withName != null ? o.withName : (files.size() > 1 || dirArg);

		if( files.isEmpty() && !o.recursive ) {
			scan(ctx, o, ctx.stdin, "(standard input)", showName);
		} else if( files.isEmpty()) {
			walk(ctx, o, ctx.console.createFileSource("."), "", showName);
		}
		for (String path : files) {
			if( o.done ) {
				break;
			}
			if( path.equals("-")) {
				scan(ctx, o, ctx.stdin, "(standard input)", showName);
				continue;
			}
			int slash = path.lastIndexOf('/');
			String dirPrefix = slash >= 0 ? path.substring(0, slash+1) : "";
			List<FileSource> list = getFiles(ctx, path);
			boolean glob = hasWildcard(path) && !ctx.console.createFileSource(path).exists();
			for (FileSource file : list) {
				if( o.done ) {
					break;
				}
				String label = glob ? dirPrefix+file.getName() : path;
				if( !file.exists()) {
					error(ctx, o, label, "No such file or directory");
				} else if( file.isDirectory()) {
					if( o.recursive ) {
						walk(ctx, o, file, label.endsWith("/") ? label : label+"/", showName);
					} else {
						error(ctx, o, label, "Is a directory");
					}
				} else {
					scanFile(ctx, o, file, label, showName);
				}
			}
		}
		ctx.stdout.flush();
		if( o.quiet && o.anyMatch ) {
			return 0;
		}
		return o.error ? 2 : (o.anyMatch ? 0 : 1);
	}

	private static void error(ShellContext ctx, Opts o, String label, String msg) {
		o.error = true;
		if( !o.noMessages ) {
			ctx.stderr.println("grep: "+label+": "+msg);
		}
	}

	/** handle the options that take a value (I and X are --include and --exclude) */
	private int value(ShellContext ctx, Opts o, char opt, String val) throws IOException {
		switch (opt) {
		case 'e':
			o.haveE = true;
			addPatterns(o, val);
			break;
		case 'f': {
			FileSource f = ctx.console.createFileSource(val);
			if( !f.exists() || f.isDirectory()) {
				ctx.stderr.println("grep: "+val+": No such file or directory");
				return 2;
			}
			o.haveE = true;
			try (InputStream in = f.getInputStream()) {
				String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				if( !text.isEmpty()) {
					if( text.endsWith("\n")) {
						text = text.substring(0, text.length()-1);
					}
					addPatterns(o, text);
				}
			}
			break;
		}
		case 'm':
			try {
				o.max = Integer.parseInt(val.trim());
			} catch (NumberFormatException e) {
				ctx.stderr.println("grep: invalid max count");
				return 2;
			}
			break;
		case 'A': case 'B': case 'C':
			int n;
			try {
				n = Integer.parseInt(val.trim());
				if( n < 0 ) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				ctx.stderr.println("grep: "+val+": invalid context length argument");
				return 2;
			}
			if( opt != 'B' ) {
				o.after = n;
			}
			if( opt != 'A' ) {
				o.before = n;
			}
			break;
		case 'I':
			o.include.add(val);
			break;
		case 'X':
			o.exclude.add(val);
			break;
		}
		return 0;
	}

	private static void addPatterns(Opts o, String text) {
		o.patterns.addAll(Arrays.asList(text.split("\n", -1)));
	}

	private static Pattern compile(Opts o) {
		int flags = Pattern.UNIX_LINES;
		if( o.ignoreCase ) {
			flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		}
		if( o.patterns.isEmpty()) {
			return Pattern.compile("(?!)");
		}
		StringBuilder all = new StringBuilder();
		for (String p : o.patterns) {
			String re;
			switch (o.syntax) {
			case 'F': re = Pattern.quote(p); break;
			case 'P': re = p; break;
			default: re = convert(p, o.syntax == 'E'); break;
			}
			if( o.line ) {
				re = "^(?:"+re+")$";
			} else if( o.word ) {
				re = "(?<!\\w)(?:"+re+")(?!\\w)";
			}
			if( all.length() > 0 ) {
				all.append('|');
			}
			all.append("(?:").append(re).append(')');
		}
		return Pattern.compile(all.toString(), flags);
	}

	private static String posixClass(String n) {
		switch (n) {
		case "alpha": return "Alpha";
		case "digit": return "Digit";
		case "alnum": return "Alnum";
		case "upper": return "Upper";
		case "lower": return "Lower";
		case "space": return "Space";
		case "blank": return "Blank";
		case "punct": return "Punct";
		case "print": return "Print";
		case "graph": return "Graph";
		case "cntrl": return "Cntrl";
		case "xdigit": return "XDigit";
		}
		return null;
	}

	/** a POSIX basic or extended regular expression as a Java one */
	static String convert(String p, boolean ere) {
		return convert(p, ere, "$");
	}

	/** dollar is what the end-of-line anchor becomes (sed's pattern space can end with a newline) */
	static String convert(String p, boolean ere, String dollar) {
		StringBuilder sb = new StringBuilder();
		int n = p.length();
		boolean atStart = true;
		for (int i = 0; i < n; i++) {
			char c = p.charAt(i);
			switch (c) {
			case '\\': {
				if( i+1 >= n ) {
					sb.append("\\\\");
					atStart = false;
					break;
				}
				char d = p.charAt(++i);
				if( !ere && "(){}|+?".indexOf(d) >= 0 ) {
					sb.append(d);
					atStart = d == '(' || d == '|';
					break;
				}
				atStart = false;
				if( d == '<' ) {
					sb.append("\\b(?=\\w)");
				} else if( d == '`' ) {
					sb.append("\\A");
				} else if( d == '\'' ) {
					sb.append("\\z");
				} else if( d == '>' ) {
					sb.append("\\b(?<=\\w)");
				} else if( "wWsSbB".indexOf(d) >= 0 || (d >= '1' && d <= '9')) {
					sb.append('\\').append(d);
				} else if( Character.isLetterOrDigit(d)) {
					sb.append(d);
				} else {
					sb.append('\\').append(d);
				}
				break;
			}
			case '[': {
				atStart = false;
				int j = i+1;
				StringBuilder br = new StringBuilder("[");
				if( j < n && p.charAt(j) == '^' ) {
					br.append('^');
					j++;
				}
				if( j < n && p.charAt(j) == ']' ) {
					br.append("\\]");
					j++;
				}
				while( j < n && p.charAt(j) != ']' ) {
					char b = p.charAt(j);
					if( b == '[' && j+1 < n && p.charAt(j+1) == ':' ) {
						int end = p.indexOf(":]", j+2);
						String cls = end > 0 ? posixClass(p.substring(j+2, end)) : null;
						if( cls != null ) {
							br.append("\\p{").append(cls).append('}');
							j = end+2;
							continue;
						}
					}
					if( b == '\\' || b == '[' || b == '&' || b == '^' ) {
						br.append('\\');
					}
					br.append(b);
					j++;
				}
				if( j >= n ) {
					sb.append("\\[");
				} else {
					sb.append(br).append(']');
					i = j;
				}
				break;
			}
			case '*':
				sb.append(atStart ? "\\*" : "*");
				atStart = false;
				break;
			case '^':
				if( ere || atStart ) {
					sb.append('^');
				} else {
					sb.append("\\^");
					atStart = false;
				}
				break;
			case '$': {
				boolean anchor = ere || i == n-1 || p.startsWith("\\)", i+1) || p.startsWith("\\|", i+1);
				sb.append(anchor ? dollar : "\\$");
				atStart = false;
				break;
			}
			case '(': case '|':
				if( ere ) {
					sb.append(c);
					atStart = true;
				} else {
					sb.append('\\').append(c);
					atStart = false;
				}
				break;
			case ')': case '{': case '}': case '+': case '?':
				if( ere ) {
					sb.append(c);
				} else {
					sb.append('\\').append(c);
				}
				atStart = false;
				break;
			default:
				sb.append(c);
				atStart = false;
			}
		}
		return sb.toString();
	}

	private static boolean nameOk(Opts o, String name) {
		for (String ex : o.exclude) {
			if( globMatch(ex, name)) {
				return false;
			}
		}
		if( o.include.isEmpty()) {
			return true;
		}
		for (String in : o.include) {
			if( globMatch(in, name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean globMatch(String glob, String name) {
		StringBuilder re = new StringBuilder();
		for (char c : glob.toCharArray()) {
			if( c == '*' ) {
				re.append(".*");
			} else if( c == '?' ) {
				re.append('.');
			} else {
				re.append(Pattern.quote(""+c));
			}
		}
		return name.matches(re.toString());
	}

	private void walk(ShellContext ctx, Opts o, FileSource dir, String prefix, boolean showName) throws IOException {
		FileSource [] kids = dir.listFiles();
		if( kids == null ) {
			return;
		}
		Arrays.sort(kids, (a, b) -> a.getName().compareTo(b.getName()));
		for (FileSource kid : kids) {
			if( o.done ) {
				return;
			}
			if( kid.isDirectory()) {
				walk(ctx, o, kid, prefix+kid.getName()+"/", showName);
			} else if( nameOk(o, kid.getName())) {
				scanFile(ctx, o, kid, prefix+kid.getName(), showName);
			}
		}
	}

	private void scanFile(ShellContext ctx, Opts o, FileSource file, String label, boolean showName) throws IOException {
		try (InputStream in = file.getInputStream()) {
			scan(ctx, o, in, label, showName);
		} catch (IOException e) {
			error(ctx, o, label, e.getMessage());
		}
	}

	private static String readLine(Reader r, StringBuilder buf) throws IOException {
		buf.setLength(0);
		int c;
		boolean any = false;
		while( (c = r.read()) >= 0 ) {
			any = true;
			if( c == '\n' ) {
				return buf.toString();
			}
			buf.append((char)c);
		}
		return any ? buf.toString() : null;
	}

	private static void printLine(ShellContext ctx, Opts o, String label, boolean showName, long lineNo, char sep, String text) {
		StringBuilder sb = new StringBuilder();
		if( showName ) {
			sb.append(label).append(sep);
		}
		if( o.number ) {
			sb.append(lineNo).append(sep);
		}
		sb.append(text);
		ctx.stdout.println(sb);
		o.printed = true;
	}

	private void scan(ShellContext ctx, Opts o, InputStream raw, String label, boolean showName) throws IOException {
		BufferedInputStream in = new BufferedInputStream(raw, 65536);
		boolean binary = false;
		if( !o.text ) {
			in.mark(8200);
			byte [] head = new byte[8192];
			int n = in.readNBytes(head, 0, head.length);
			in.reset();
			for (int i = 0; i < n; i++) {
				if( head[i] == 0 ) {
					binary = true;
					break;
				}
			}
			if( binary && o.skipBinary ) {
				finish(ctx, o, label, showName, 0);
				return;
			}
		}
		Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
		StringBuilder buf = new StringBuilder();
		boolean context = o.after > 0 || o.before > 0;
		boolean print = !(o.count || o.listFiles || o.listNot || o.quiet || binary);
		ArrayDeque<String> before = new ArrayDeque<>();
		long lineNo = 0, lastPrinted = -1;
		int afterLeft = 0;
		long count = 0;
		boolean stopped = false;
		String line;
		while( (line = readLine(r, buf)) != null ) {
			lineNo++;
			if( stopped ) {
				if( afterLeft <= 0 ) {
					break;
				}
				printLine(ctx, o, label, showName, lineNo, '-', line);
				lastPrinted = lineNo;
				afterLeft--;
				continue;
			}
			Matcher m = o.pattern.matcher(line);
			boolean hit = m.find() != o.invert;
			if( hit ) {
				count++;
				o.anyMatch = true;
				if( o.quiet ) {
					o.done = true;
					return;
				}
				if( o.listFiles || o.listNot || binary ) {
					break;
				}
				if( print ) {
					if( o.only ) {
						if( !o.invert ) {
							m.reset();
							while( m.find()) {
								if( m.end() > m.start()) {
									printLine(ctx, o, label, showName, lineNo, ':', m.group());
								}
							}
						}
					} else {
						if( context ) {
							long firstNo = lineNo-before.size();
							if( o.printed && (lastPrinted < 0 || firstNo > lastPrinted+1)) {
								ctx.stdout.println("--");
							}
							long no = firstNo;
							for (String b : before) {
								printLine(ctx, o, label, showName, no++, '-', b);
							}
							before.clear();
						}
						printLine(ctx, o, label, showName, lineNo, ':', line);
					}
					lastPrinted = lineNo;
					afterLeft = o.after;
				}
				if( o.max >= 0 && count >= o.max ) {
					stopped = true;
					if( afterLeft <= 0 || !print ) {
						break;
					}
				}
			} else if( print && context ) {
				if( afterLeft > 0 ) {
					printLine(ctx, o, label, showName, lineNo, '-', line);
					lastPrinted = lineNo;
					afterLeft--;
				} else if( o.before > 0 ) {
					before.addLast(line);
					if( before.size() > o.before ) {
						before.removeFirst();
					}
				}
			}
		}
		if( binary && count > 0 && !o.count && !o.listFiles && !o.listNot ) {
			ctx.stdout.println("grep: "+label+": binary file matches");
		}
		finish(ctx, o, label, showName, count);
	}

	/** the per-file summary lines of -c, -l and -L */
	private void finish(ShellContext ctx, Opts o, String label, boolean showName, long count) {
		if( o.quiet ) {
			return;
		}
		if( o.count ) {
			ctx.stdout.println((showName ? label+":" : "")+count);
		}
		if( o.listFiles && count > 0 ) {
			ctx.stdout.println(label);
		}
		if( o.listNot && count == 0 ) {
			ctx.stdout.println(label);
		}
	}
}
