package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Find extends ShellCommand{
	static String name = "find";
	// physical 
	static String help = "find [-H | -L | -P] PATH ... [EXPRESSION]\n"
			+ "\tSearch the directory trees at each PATH and print the paths that match EXPRESSION (all of them\n"
			+ "\twhen there is none). Works on every mounted file system.\n"
			+ "\tTests:\n"
			+ "\t-name PATTERN, -iname PATTERN       the name matches a shell pattern (-iname ignores case)\n"
			+ "\t-path PATTERN, -wholename PATTERN   the path matches a shell pattern (* also matches /)\n"
			+ "\t-type f|d|l                         a regular file, a directory or a symbolic link\n"
			+ "\t-empty                              an empty file or directory\n"
			+ "\t-mmin N, -amin N                    modified or accessed N minutes ago\n"
			+ "\t-mtime N, -atime N                  modified or accessed N days (24-hour periods) ago\n"
			+ "\t-size N[cwbkMG]                     N bytes, 2-byte words, 512-byte blocks (the default),\n"
			+ "\tKiB, MiB or GiB, rounded up\n"
			+ "\t+N means more than N, -N less than N, and N exactly N.\n"
			+ "\tOperators: ( EXPRESSION ) (quoted, as \\( \\)); ! or -not; -a or -and (implied); -o or -or.\n"
			+ "\tOptions: -maxdepth N, -mindepth N; -L follows symbolic links (-H and -P don't).\n"
			+ "\tActions:\n"
			+ "\t-print           print the path (the default)\n"
			+ "\t-print0          end each path with a NUL instead of a newline\n"
			+ "\t-exec CMD {} \\;  run CMD for each file, with {} replaced by its path\n"
			+ "\t-exec CMD {} +   run CMD once, with all the paths";

	public Find() {
		super(name, help);
	}

	/** a numeric test argument: +n more than, -n less than, n exactly */
	static class FindNumber {
		char sign = '=';
		long numerator;
		long denominator = 1;
	}

	private static final long DAY = 60000*60*24;

	/** one test of the expression */
	private interface Test {
		boolean test(FileSource file, String path, int depth) throws IOException;
	}

	/** the walk: the tests (or-groups of and-ed tests), the depth limits and the output */
	class FindContext {
		ShellContext sc;
		boolean followLinks = false;
		/** the expression; null when there is none (everything matches) */
		Test expression;
		int maxDepth = Integer.MAX_VALUE;
		int minDepth = 0;
		/** -print0: a NUL after each path instead of a newline */
		boolean print0 = false;
		/** -exec cmd {} \; (each file) or + (all at once); null if none */
		List<String> exec;
		boolean execAll;
		List<String> execFiles = new ArrayList<>();
		int status = 0;

		FindContext(ShellContext ctx) {
			sc = ctx;
		}

		boolean matches(FileSource file, String path, int depth) throws IOException {
			return expression == null || expression.test(file, path, depth);
		}

		void find(FileSource file, String path, int depth) throws IOException {
			if( depth >= minDepth && matches(file, path, depth)) {
				act(path);
			}
			if( file.isDirectory() && depth < maxDepth ) {
				if( !followLinks && depth > 0 && file.getLinkedTo() != null ) {
					return;
				}
				FileSource[] kids = file.listFiles();
				if( kids !=null) {
					// name order, so the output does not depend on the file system's listing order
					Arrays.sort(kids, Comparator.comparing(FileSource::getName));
					for(FileSource kid : kids) {
						find(kid, path.endsWith("/") ? path+kid.getName() : path+"/"+kid.getName(), depth+1);
					}
				}
			}
		}

		private void act(String path) throws IOException {
			if( exec != null ) {
				if( execAll ) {
					execFiles.add(path);
				} else {
					List<String> words = new ArrayList<>();
					for(String w : exec) {
						words.add(w.replace("{}", path));
					}
					run(words);
				}
				return;
			}
			sc.stdout.print(path);
			sc.stdout.print(print0 ? '\0' : '\n');
		}

		void finish() throws IOException {
			if( exec != null && execAll && !execFiles.isEmpty()) {
				List<String> words = new ArrayList<>();
				for(String w : exec) {
					if( w.equals("{}")) {
						words.addAll(execFiles);
					} else {
						words.add(w);
					}
				}
				run(words);
			}
			sc.stdout.flush();
		}

		/** -exec: the command, run by the shell */
		private void run(List<String> words) throws IOException {
			StringBuilder code = new StringBuilder();
			for(String w : words) {
				code.append('\'').append(w.replace("'", "'\\''")).append("' ");
			}
			int rc;
			try {
				rc = sc.console.runCode(sc, code.toString().trim());
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new IOException(e.getMessage(), e);
			}
			if( rc != 0 ) {
				status = 1;
			}
		}
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		FindContext fctx = new FindContext(ctx);
		List<String> paths = new ArrayList<String>();
		int idx = 0;
		// options, then the start points, then the expression
		for(; idx < args.length; idx++ ) {
			String arg = ""+args[idx].getValue(ctx);
			if( arg.equals("-P") || arg.equals("-H")) {
				continue;
			} else if( arg.equals("-L")) {
				fctx.followLinks = true;
			} else {
				break;
			}
		}
		for(; idx < args.length; idx++ ) {
			String arg = ""+args[idx].getValue(ctx);
			if( arg.startsWith("-") || arg.equals("!") || arg.equals("(")) {
				break;
			}
			paths.add(arg);
		}
		List<String> tokens = new ArrayList<>();
		for(; idx < args.length; idx++ ) {
			tokens.add(""+args[idx].getValue(ctx));
		}
		try {
			Parser parser = new Parser(fctx, tokens);
			fctx.expression = parser.parse();
		} catch (BadExpression e) {
			ctx.stderr.println(name+": "+e.getMessage());
			return 1;
		}
		if( paths.size()==0) {
			// (as macOS find: a path is needed)
			ctx.stderr.println("usage: find [-P] path ... [expression]");
			return 1;
		}
		int ret = 0;
		for(String startPoint : paths) {
			FileSource start = ctx.getFileSource(startPoint);
			if( !start.exists()) {
				ctx.stderr.println("find: "+startPoint+": No such file or directory");
				ret = 1;
				continue;
			}
			fctx.find(start, startPoint, 0);
		}
		fctx.finish();
		return ret != 0 ? ret : fctx.status;
	}

	/** A mistake in the expression: find: MESSAGE, status 1. */
	private static class BadExpression extends Exception {
		private static final long serialVersionUID = 1L;
		BadExpression(String message) {
			super(message);
		}
	}

	/**
	 * The expression: OR of ANDs of (possibly negated) primaries and ( ) groups, as find reads it.
	 * -a is implied between primaries. -print, -print0, -exec, -maxdepth and -mindepth are true and
	 * set how find acts or goes.
	 */
	private class Parser {
		final FindContext fctx;
		final List<String> tokens;
		int pos;

		Parser(FindContext fctx, List<String> tokens) {
			this.fctx = fctx;
			this.tokens = tokens;
		}

		Test parse() throws IOException, BadExpression {
			if( tokens.isEmpty()) {
				return null;
			}
			Test t = or();
			if( pos < tokens.size()) {
				throw new BadExpression(tokens.get(pos).equals(")") ? "unbalanced )" : "unexpected "+tokens.get(pos));
			}
			return t;
		}

		private Test or() throws IOException, BadExpression {
			Test left = and();
			while( pos < tokens.size() && (tokens.get(pos).equals("-o") || tokens.get(pos).equals("-or"))) {
				pos++;
				Test l = left, r = and();
				left = (f, p, d) -> l.test(f, p, d) || r.test(f, p, d);
			}
			return left;
		}

		private Test and() throws IOException, BadExpression {
			Test left = unary();
			while( pos < tokens.size()) {
				String t = tokens.get(pos);
				if( t.equals("-o") || t.equals("-or") || t.equals(")")) {
					break;
				}
				if( t.equals("-a") || t.equals("-and")) {
					pos++;
				}
				Test l = left, r = unary();
				left = (f, p, d) -> l.test(f, p, d) && r.test(f, p, d);
			}
			return left;
		}

		private Test unary() throws IOException, BadExpression {
			if( pos >= tokens.size()) {
				throw new BadExpression("the expression ends too soon");
			}
			String t = tokens.get(pos++);
			if( t.equals("!") || t.equals("-not")) {
				Test inner = unary();
				return (f, p, d) -> !inner.test(f, p, d);
			}
			if( t.equals("(")) {
				Test inner = or();
				if( pos >= tokens.size() || !tokens.get(pos).equals(")")) {
					throw new BadExpression("missing )");
				}
				pos++;
				return inner;
			}
			return primary(t);
		}

		private String value(String primary) throws BadExpression {
			if( pos >= tokens.size()) {
				throw new BadExpression(primary+": needs an argument");
			}
			return tokens.get(pos++);
		}

		private int number(String primary) throws BadExpression {
			String v = value(primary);
			try {
				return Integer.parseInt(v);
			} catch (NumberFormatException e) {
				throw new BadExpression(primary+": not a number: "+v);
			}
		}

		private Test primary(String arg) throws IOException, BadExpression {
			final Test TRUE = (f, p, d) -> true;
			switch (arg) {
			case "-print":
				return TRUE;
			case "-print0":
				fctx.print0 = true;
				return TRUE;
			case "-maxdepth":
				fctx.maxDepth = number(arg);
				return TRUE;
			case "-mindepth":
				fctx.minDepth = number(arg);
				return TRUE;
			case "-exec": {
				fctx.exec = new ArrayList<>();
				boolean ended = false;
				while( pos < tokens.size()) {
					String w = tokens.get(pos++);
					if( w.equals(";")) {
						ended = true;
						break;
					}
					if( w.equals("+") && !fctx.exec.isEmpty() && fctx.exec.get(fctx.exec.size()-1).equals("{}")) {
						fctx.execAll = true;
						ended = true;
						break;
					}
					fctx.exec.add(w);
				}
				if( !ended || fctx.exec.isEmpty()) {
					throw new BadExpression("-exec: needs a command ending with ; or {} +");
				}
				return TRUE;
			}
			case "-name":
			case "-iname": {
				Pattern p = Pattern.compile(prepWildCards(value(arg)), arg.equals("-iname") ? Pattern.CASE_INSENSITIVE : 0);
				return (f, path, d) -> p.matcher(d == 0 ? baseName(path) : f.getName()).matches();
			}
			case "-path":
			case "-wholename": {
				Pattern p = Pattern.compile(prepWildCards(value(arg)));
				return (f, path, d) -> p.matcher(path).matches();
			}
			case "-type": {
				String t = value(arg);
				if( !t.equals("f") && !t.equals("d") && !t.equals("l")) {
					throw new BadExpression("-type: unknown type "+t+" (f, d or l)");
				}
				// (with -L a link is what it points at; -type l then finds only those that point nowhere)
				boolean follow = fctx.followLinks;
				return (f, path, d) -> {
					switch (t) {
					case "f": return f.isFile() && (follow || f.getLinkedTo() == null);
					case "d": return f.isDirectory() && (follow || d == 0 || f.getLinkedTo() == null);
					default: return f.getLinkedTo() != null && (!follow || !f.exists());
					}
				};
			}
			case "-empty":
				return (f, path, d) -> f.isDirectory() ? (f.listFiles() == null || f.listFiles().length == 0) : f.length() == 0;
			case "-mmin":
			case "-amin":
			case "-mtime":
			case "-atime": {
				FindNumber n = parseNumber(value(arg));
				boolean access = arg.startsWith("-a");
				long unit = arg.endsWith("min") ? 60000 : DAY;
				return (f, path, d) -> compare(n, (System.currentTimeMillis() - (access ? f.lastAccessTime() : f.lastModified())) / unit);
			}
			case "-size": {
				FindNumber n = parseNumber(value(arg));
				return (f, path, d) -> {
					if( f.isDirectory()) {
						return false;
					}
					long units = (f.length() + n.denominator - 1) / n.denominator;
					return compare(n, units);
				};
			}
			default:
				throw new us.bringardner.fsh.ShellCommand.UnsupportedOption("find: "+arg+": unknown primary or operator");
			}
		}
	}

	private static String baseName(String path) {
		String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length()-1) : path;
		return p.substring(p.lastIndexOf('/')+1);
	}

	private static boolean compare(FindNumber arg, long value) {
		if( arg.sign == '+') {
			return value > arg.numerator;
		} else if( arg.sign == '-') {
			return value < arg.numerator;
		}
		return value == arg.numerator;
	}

	/** +n, -n or n, with a size unit (c w b k M G; b, 512 bytes, if none) for -size */
	private static FindNumber parseNumber(String val) {
		FindNumber ret = new FindNumber();
		String number = val;
		if( number.startsWith("+") || number.startsWith("-")) {
			ret.sign = number.charAt(0);
			number = number.substring(1);
		}
		int end = 0;
		while( end < number.length() && Character.isDigit(number.charAt(end))) {
			end++;
		}
		ret.numerator = end == 0 ? 0 : Long.parseLong(number.substring(0, end));
		String unit = number.substring(end);
		switch (unit) {
		case "c": ret.denominator = 1; break;
		case "w": ret.denominator = 2; break;
		case "k": ret.denominator = 1024; break;
		case "M": ret.denominator = 1048576; break;
		case "G": ret.denominator = 1073741824; break;
		default: ret.denominator = 512; break;
		}
		return ret;
	}
}
