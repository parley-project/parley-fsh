package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import java.util.regex.Pattern;

/**
 * test expr (and [ expr ]), as in bash: the words are already expanded, and how they are read
 * depends on how many there are (POSIX): none is false, one is true if it is not empty, two are
 * ! word or a unary test, three are a binary test (or ! and two, or ( word )), and more are an
 * expression with ! ( ) -a and -o.
 */
public class Test extends ShellCommand{
	static String name = "test";
	static String help = "test [expr]\n"
			+ "	Exit with status 0 (true) or 1 (false) depending on expr; 2 if expr is invalid.\n"
			+ "	-n s, -z s, s (not empty), s1 = s2, s1 == s2, s1 != s2, s1 < s2, s1 > s2,\n"
			+ "	n1 -eq -ne -lt -le -gt -ge n2, -e -f -d -s -r -w -x -h -L file, -v name,\n"
			+ "	f1 -nt -ot -ef f2, ! expr, ( expr ), expr -a expr, expr -o expr.\n"
			+ "	[ expr ] is the same; the last argument must be ]."
			;

	/** the name used in messages: test, or [ */
	protected String label = "test";

	/** an invalid expression (status 2) */
	private static class TestError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		TestError(String msg) {
			super(msg);
		}
	}

	private static final String BINARY = " = == != < > =~ !~ -eq -ne -lt -le -gt -ge -nt -ot -ef ";
	private static final String UNARY = " -n -z -v -o -e -a -f -d -s -r -w -x -h -L -b -c -p -S -g -u -k -t -G -O -N ";

	private List<String> words;
	private int pos;
	private ShellContext ctx;

	public Test() {
		super(name, help);
	}

	protected Test(String name) {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		this.ctx = ctx;
		words = new ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			words.add(""+args[idx].getValue(ctx));
		}
		try {
			return evaluate(words) ? 0 : 1;
		} catch (TestError e) {
			ctx.error(label+": "+e.getMessage());
			return 2;
		}
	}

	/**
	 * Evaluate test's arguments (for [ ... ], without the ]).
	 */
	public static int test(List<String> words, ShellContext ctx, String label) {
		Test t = new Test();
		t.label = label;
		t.ctx = ctx;
		t.words = words;
		try {
			return t.evaluate(words) ? 0 : 1;
		} catch (TestError e) {
			ctx.error(label+": "+e.getMessage());
			return 2;
		}
	}

	private boolean evaluate(List<String> w) {
		words = w;
		pos = 0;
		if( w.isEmpty()) {
			return false;
		}
		boolean value = posixtest(w.size());
		if( pos != w.size()) {
			if( pos < w.size() && w.get(pos).startsWith("-")) {
				throw new TestError("syntax error: `"+w.get(pos)+"' unexpected");
			}
			throw new TestError("too many arguments");
		}
		return value;
	}

	// ---------------------------------------------------------------- bash's test.c

	private String at(int i) {
		return i < words.size() ? words.get(i) : null;
	}

	private boolean posixtest(int nargs) {
		switch (nargs) {
		case 0:
			return false;
		case 1: {
			boolean v = !words.get(pos).isEmpty();
			pos++;
			return v;
		}
		case 2:
			return twoArguments();
		case 3:
			return threeArguments();
		case 4:
			if( "!".equals(at(pos))) {
				pos++;
				return !threeArguments();
			}
			if( "(".equals(at(pos)) && ")".equals(at(pos+3))) {
				pos++;
				boolean v = twoArguments();
				pos++;
				return v;
			}
			return expr();
		default:
			return expr();
		}
	}

	private boolean twoArguments() {
		String w = words.get(pos);
		if( w.equals("!")) {
			pos++;
			return words.get(pos++).isEmpty();
		}
		if( w.length() == 2 && w.charAt(0) == '-' && isUnary(w)) {
			return unaryOperator();
		}
		throw new TestError(w+": unary operator expected");
	}

	private boolean threeArguments() {
		String op = words.get(pos+1);
		if( isBinary(op)) {
			return binaryOperator();
		}
		if( op.equals("-a") || op.equals("-o")) {
			boolean a = !words.get(pos).isEmpty();
			boolean b = !words.get(pos+2).isEmpty();
			pos += 3;
			return op.equals("-a") ? a && b : a || b;
		}
		if( words.get(pos).equals("!")) {
			pos++;
			return !twoArguments();
		}
		if( words.get(pos).equals("(") && words.get(pos+2).equals(")")) {
			pos++;
			boolean v = !words.get(pos).isEmpty();
			pos += 2;
			return v;
		}
		throw new TestError(op+": binary operator expected");
	}

	private boolean expr() {
		if( pos >= words.size()) {
			throw new TestError("argument expected");
		}
		return or();
	}

	private boolean or() {
		boolean value = and();
		if( pos < words.size() && words.get(pos).equals("-o")) {
			pos++;
			boolean v2 = or();
			return value || v2;
		}
		return value;
	}

	private boolean and() {
		boolean value = term();
		if( pos < words.size() && words.get(pos).equals("-a")) {
			pos++;
			boolean v2 = and();
			return value && v2;
		}
		return value;
	}

	private boolean term() {
		if( pos >= words.size()) {
			throw new TestError("argument expected");
		}
		if( words.get(pos).equals("!")) {
			boolean value = false;
			while( pos < words.size() && words.get(pos).equals("!")) {
				advance(true);
				value = !value;
			}
			return value ? !term() : term();
		}
		if( words.get(pos).equals("(")) {
			advance(true);
			int nargs = 1;
			for(int count = 1; pos+nargs < words.size(); nargs++) {
				if( words.get(pos+nargs).equals(")")) {
					count--;
				} else if( words.get(pos+nargs).equals("(")) {
					count++;
				}
				if( count == 0 ) {
					break;
				}
			}
			boolean value = pos+nargs < words.size() && nargs <= 4 ? posixtest(nargs) : expr();
			if( pos >= words.size()) {
				// ([ expr ] has its ] there)
				throw new TestError(label.equals("[") ? "`)' expected, found ]" : "`)' expected");
			} else if( !words.get(pos).equals(")")) {
				throw new TestError("`)' expected, found "+words.get(pos));
			}
			pos++;
			return value;
		}
		if( pos+3 <= words.size() && isBinary(words.get(pos+1))) {
			return binaryOperator();
		}
		if( pos+2 <= words.size() && isUnary(words.get(pos))) {
			return unaryOperator();
		}
		boolean value = !words.get(pos).isEmpty();
		pos++;
		return value;
	}

	/** past an argument; there must be another (f) */
	private void advance(boolean f) {
		pos++;
		if( f && pos >= words.size()) {
			throw new TestError("argument expected");
		}
	}

	private boolean binaryOperator() {
		String a = words.get(pos);
		String op = words.get(pos+1);
		String b = words.get(pos+2);
		pos += 3;
		return binary(a, op, b);
	}

	private boolean unaryOperator() {
		String op = words.get(pos);
		if( op.equals("-t") && !ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)) {
			// -t may have no argument (then fd 1)
			pos++;
			if( pos < words.size()) {
				if( words.get(pos).trim().matches("[-+]?[0-9]+")) {
					return unaryTest(op, words.get(pos++), ctx);
				}
				if( words.size() >= 5 && (words.get(pos).equals("-a") || words.get(pos).equals("-o"))) {
					return unaryTest(op, "1", ctx);
				}
				throw new TestError(words.get(pos)+": integer expected");
			}
			return unaryTest(op, "1", ctx);
		}
		pos++;
		if( pos >= words.size()) {
			throw new TestError("argument expected");
		}
		return unaryTest(op, words.get(pos++), ctx);
	}

	// ---------------------------------------------------------------- tests

	private static boolean isUnary(String w) {
		return w.length() == 2 && w.charAt(0) == '-' && "abcdefghknoprstuvwxzGLOSNR".indexOf(w.charAt(1)) >= 0;
	}

	private static boolean isBinary(String w) {
		return BINARY.contains(" "+w+" ");
	}

	private boolean binary(String a, String op, String b) {
		switch (op) {
		case "=":
		case "==": return a.equals(b);
		case "!=": return !a.equals(b);
		case "=~":
		case "!~": {
			// a pattern match (bash's test has it)
			boolean m = us.bringardner.fsh.GlobPattern.compile(b).matches(a);
			return op.equals("=~") == m;
		}
		case "<": return a.compareTo(b) < 0;
		case ">": return a.compareTo(b) > 0;
		case "-nt":
		case "-ot":
		case "-ef": return fileCompare(a, op, b, ctx);
		default:
			int cmp = Long.compare(integer(a), integer(b));
			switch (op) {
			case "-eq": return cmp == 0;
			case "-ne": return cmp != 0;
			case "-lt": return cmp < 0;
			case "-le": return cmp <= 0;
			case "-gt": return cmp > 0;
			default: return cmp >= 0;
			}
		}
	}

	private static long integer(String s) {
		String t = s.trim();
		if( !t.matches("[-+]?[0-9]+")) {
			throw new TestError(s+": integer expected");
		}
		try {
			return Long.parseLong(t.startsWith("+") ? t.substring(1) : t);
		} catch (NumberFormatException e) {
			throw new TestError(s+": integer expected");
		}
	}

	/** -n -z -v and the file tests (also used by test and [ ]) */
	public static boolean unaryTest(String op, String val, ShellContext ctx) {
		switch (op) {
		case "-n": return !val.isEmpty();
		case "-z": return val.isEmpty();
		case "-v": {
			java.util.regex.Matcher m = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[(.+)\\]").matcher(val);
			if( m.matches() && (m.group(2).equals("@") || m.group(2).equals("*"))) {
				// -v a[@]: a has an element (a set scalar is one)
				Object arr = ctx.getVariable(m.group(1));
				// (of an associative array, as bash: the element whose key is @)
				return arr instanceof java.util.Map<?,?> map ? map.containsKey(m.group(2)) : arr instanceof java.util.List<?> l ? !l.isEmpty() : arr != null;
			}
			if( m.matches()) {
				// -v a[1], -v m[key]: that element is set
				Object arr = ctx.getVariable(m.group(1));
				if( arr instanceof java.util.Map<?,?> ) {
					return ((java.util.Map<?,?>) arr).containsKey(m.group(2));
				}
				if( arr instanceof java.util.List<?> ) {
					int idx = us.bringardner.fsh.expand.Arithmetic.evaluate(m.group(2), ctx).intValue();
					return ((java.util.List<?>) arr).get(idx) != null;
				}
				return false;
			}
			// -v a of an array: its element 0, as $a
			return us.bringardner.fsh.ShellContext.firstElement(ctx.getVariable(val)) != null;
		}
		case "-o": {
			// a set -o option is on
			us.bringardner.fsh.Console.Option o = us.bringardner.fsh.Console.Option.find(val);
			return o != us.bringardner.fsh.Console.Option.Unsupported && o != us.bringardner.fsh.Console.Option.Option
					&& ctx.console.isOptionEnabled(o);
		}
		case "-t": {
			// a descriptor on a terminal
			String n = val.trim();
			int fd = n.matches("[-+]?[0-9]{1,9}") ? Integer.parseInt(n.startsWith("+") ? n.substring(1) : n) : -1;
			switch (fd) {
			case 0: return us.bringardner.fsh.Console.isKeyboard(ctx.stdin);
			case 1: return ctx.stdout == us.bringardner.fsh.Console.System_out && us.bringardner.fsh.NativeKeyboard.terminal();
			case 2: return ctx.stderr == us.bringardner.fsh.Console.System_err && us.bringardner.fsh.NativeKeyboard.terminal();
			default: return false;
			}
		}
		case "-R":
			// a nameref
			return ctx.rawVariable(val) instanceof ShellContext.NameRef;
		}
		if( val.isEmpty()) {
			// (no file has no name)
			return false;
		}
		java.util.Map<String,Object> st = stat(ctx, val, !op.equals("-h") && !op.equals("-L"));
		if( st != null || isLocal(ctx, val)) {
			if( st == null ) {
				return false;
			}
			int mode = (Integer) st.get("mode");
			int type = mode & 0170000;
			java.nio.file.Path path = path(ctx, val);
			switch (op) {
			case "-e":
			case "-a": return true;
			case "-f": return type == 0100000;
			case "-d": return type == 0040000;
			case "-c": return type == 0020000;
			case "-b": return type == 0060000;
			case "-p": return type == 0010000;
			case "-S": return type == 0140000;
			case "-h":
			case "-L": return type == 0120000;
			case "-s": return ((Number) st.get("size")).longValue() > 0;
			case "-u": return (mode & 04000) != 0;
			case "-g": return (mode & 02000) != 0;
			case "-k": return (mode & 01000) != 0;
			case "-r": return java.nio.file.Files.isReadable(path);
			case "-w": return java.nio.file.Files.isWritable(path);
			case "-x": return java.nio.file.Files.isExecutable(path);
			case "-O": return String.valueOf(st.get("uid")).equals(String.valueOf(ctx.getVariable("EUID")));
			case "-G": return String.valueOf(st.get("gid")).equals(String.valueOf(firstGroup(ctx)));
			case "-N": {
				java.nio.file.attribute.FileTime m = (java.nio.file.attribute.FileTime) st.get("lastModifiedTime");
				java.nio.file.attribute.FileTime a = (java.nio.file.attribute.FileTime) st.get("lastAccessTime");
				return m.compareTo(a) > 0;
			}
			default: return false;
			}
		}
		try {
			FileSource file = ctx.getFileSource(val);
			switch (op) {
			case "-e":
			case "-a": return file.exists();
			case "-f": return file.isFile();
			case "-d": return file.isDirectory();
			case "-s": return file.exists() && file.length() > 0;
			case "-r": return file.exists() && file.canRead();
			case "-w": return file.exists() && file.canWrite();
			case "-x": return file.exists() && file.canExecute();
			case "-h":
			case "-L": return file.exists() && file.getLinkedTo() != null;
			default: return false;
			}
		} catch (IOException e) {
			return false;
		}
	}

	/** the real path of a name in a local directory (null for another file system) */
	static java.nio.file.Path path(ShellContext ctx, String val) {
		try {
			FileSource f = ctx.getFileSource(val);
			if( f instanceof us.bringardner.parley.files.fileproxy.FileProxy p ) {
				return p.getTarget().toPath();
			}
		} catch (IOException e) {
		}
		return null;
	}

	private static boolean isLocal(ShellContext ctx, String val) {
		return path(ctx, val) != null;
	}

	/** the file's unix attributes (mode, uid, gid, ino, dev, size, times), or null if it is not there */
	static java.util.Map<String,Object> stat(ShellContext ctx, String val, boolean follow) {
		java.nio.file.Path p = path(ctx, val);
		if( p == null ) {
			return null;
		}
		try {
			return java.nio.file.Files.readAttributes(p, "unix:*", follow ? new java.nio.file.LinkOption[0] : new java.nio.file.LinkOption[] {java.nio.file.LinkOption.NOFOLLOW_LINKS});
		} catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
			return null;
		}
	}

	/** the shell's group (bash's egid) */
	private static Object firstGroup(ShellContext ctx) {
		Object g = ctx.getVariable("GROUPS");
		return g instanceof java.util.List<?> l && !l.isEmpty() ? l.get(0) : null;
	}

	/** f1 -nt -ot -ef f2 (also used by test and [ ]) */
	public static boolean fileCompare(String a, String op, String b, ShellContext ctx) {
		try {
			FileSource f1 = ctx.getFileSource(a);
			FileSource f2 = ctx.getFileSource(b);
			switch (op) {
			case "-nt": return f1.exists() && (!f2.exists() || f1.lastModified() > f2.lastModified());
			case "-ot": return f2.exists() && (!f1.exists() || f1.lastModified() < f2.lastModified());
			default: {
				java.util.Map<String,Object> s1 = stat(ctx, a, true);
				java.util.Map<String,Object> s2 = stat(ctx, b, true);
				if( s1 != null && s2 != null ) {
					// the same device and inode (a hard link too)
					return s1.get("dev").equals(s2.get("dev")) && s1.get("ino").equals(s2.get("ino"));
				}
				return f1.exists() && f2.exists() && f1.getCanonicalPath().equals(f2.getCanonicalPath());
			}
			}
		} catch (IOException e) {
			return false;
		}
	}

}
