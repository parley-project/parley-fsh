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
			+ "\tOperators: ! or -not; -a or -and (implied between tests); -o or -or. There are no ( ).\n"
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
		/** -o separates the groups; a file matches if every test of one group does */
		List<List<Test>> groups = new ArrayList<>();
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
			groups.add(new ArrayList<>());
		}

		boolean matches(FileSource file, String path, int depth) throws IOException {
			for(List<Test> group : groups) {
				boolean all = true;
				for(Test t : group) {
					if( !t.test(file, path, depth)) {
						all = false;
						break;
					}
				}
				if( all ) {
					return true;
				}
			}
			return false;
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
		boolean negate = false;
		for(; idx < args.length; idx++ ) {
			String arg = ""+args[idx].getValue(ctx);
			Test test = null;
			switch (arg) {
			case "!":
			case "-not":
				negate = !negate;
				continue;
			case "-a":
			case "-and":
				continue;
			case "-o":
			case "-or":
				fctx.groups.add(new ArrayList<>());
				continue;
			case "-print":
				continue;
			case "-print0":
				fctx.print0 = true;
				continue;
			case "-maxdepth":
				fctx.maxDepth = Integer.parseInt(""+args[++idx].getValue(ctx));
				continue;
			case "-mindepth":
				fctx.minDepth = Integer.parseInt(""+args[++idx].getValue(ctx));
				continue;
			case "-exec": {
				fctx.exec = new ArrayList<>();
				for(idx++; idx < args.length; idx++) {
					String w = ""+args[idx].getValue(ctx);
					if( w.equals(";") ) {
						break;
					}
					if( w.equals("+") && !fctx.exec.isEmpty() && fctx.exec.get(fctx.exec.size()-1).equals("{}")) {
						fctx.execAll = true;
						break;
					}
					fctx.exec.add(w);
				}
				continue;
			}
			case "-name":
			case "-iname": {
				Pattern p = Pattern.compile(prepWildCards(""+args[++idx].getValue(ctx)), arg.equals("-iname") ? Pattern.CASE_INSENSITIVE : 0);
				test = (f, path, d) -> p.matcher(d == 0 ? baseName(path) : f.getName()).matches();
				break;
			}
			case "-path":
			case "-wholename": {
				Pattern p = Pattern.compile(prepWildCards(""+args[++idx].getValue(ctx)));
				test = (f, path, d) -> p.matcher(path).matches();
				break;
			}
			case "-type": {
				String t = ""+args[++idx].getValue(ctx);
				// (with -L a link is what it points at; -type l then finds only those that point nowhere)
				boolean follow = fctx.followLinks;
				test = (f, path, d) -> {
					switch (t) {
					case "f": return f.isFile() && (follow || f.getLinkedTo() == null);
					case "d": return f.isDirectory() && (follow || d == 0 || f.getLinkedTo() == null);
					case "l": return f.getLinkedTo() != null && (!follow || !f.exists());
					default: return false;
					}
				};
				break;
			}
			case "-empty":
				test = (f, path, d) -> f.isDirectory() ? (f.listFiles() == null || f.listFiles().length == 0) : f.length() == 0;
				break;
			case "-mmin":
			case "-amin":
			case "-mtime":
			case "-atime": {
				FindNumber n = parseNumber(""+args[++idx].getValue(ctx));
				boolean access = arg.startsWith("-a");
				long unit = arg.endsWith("min") ? 60000 : DAY;
				test = (f, path, d) -> compare(n, (System.currentTimeMillis() - (access ? f.lastAccessTime() : f.lastModified())) / unit);
				break;
			}
			case "-size": {
				FindNumber n = parseNumber(""+args[++idx].getValue(ctx));
				test = (f, path, d) -> {
					if( f.isDirectory()) {
						return false;
					}
					long units = (f.length() + n.denominator - 1) / n.denominator;
					return compare(n, units);
				};
				break;
			}
			default:
				// (one this find does not have, \( \) and the like: the system's find runs)
				throw new us.bringardner.fsh.ShellCommand.UnsupportedOption("find: "+arg+": unknown primary or operator");
			}
			if( negate ) {
				Test t = test;
				test = (f, path, d) -> !t.test(f, path, d);
				negate = false;
			}
			fctx.groups.get(fctx.groups.size()-1).add(test);
		}
		if( paths.size()==0) {
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
