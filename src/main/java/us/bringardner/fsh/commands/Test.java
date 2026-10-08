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

	private static final String BINARY = " = == != < > -eq -ne -lt -le -gt -ge -nt -ot -ef ";
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
		switch (w.size()) {
		case 0:
			return false;
		case 1:
			return !w.get(0).isEmpty();
		case 2:
			if( w.get(0).equals("!")) {
				return w.get(1).isEmpty();
			}
			if( isUnary(w.get(0))) {
				return unary(w.get(0), w.get(1));
			}
			throw new TestError(w.get(0)+": unary operator expected");
		case 3:
			if( isBinary(w.get(1))) {
				return binary(w.get(0), w.get(1), w.get(2));
			}
			if( w.get(1).equals("-a") || w.get(1).equals("-o")) {
				return expression(w);
			}
			if( w.get(0).equals("!")) {
				return !evaluate(w.subList(1, 3));
			}
			if( w.get(0).equals("(") && w.get(2).equals(")")) {
				return !w.get(1).isEmpty();
			}
			throw new TestError(w.get(1)+": binary operator expected");
		case 4:
			if( w.get(0).equals("!")) {
				return !evaluate(w.subList(1, 4));
			}
			if( w.get(0).equals("(") && w.get(3).equals(")")) {
				return evaluate(w.subList(1, 3));
			}
			return expression(w);
		default:
			return expression(w);
		}
	}

	// ---------------------------------------------------------------- 5 or more: an expression

	private boolean expression(List<String> w) {
		words = w;
		pos = 0;
		boolean ret = or();
		if( pos < words.size()) {
			throw new TestError("too many arguments");
		}
		return ret;
	}

	private String peek(int ahead) {
		return pos+ahead < words.size() ? words.get(pos+ahead) : null;
	}

	private boolean or() {
		boolean ret = and();
		while( "-o".equals(peek(0))) {
			pos++;
			boolean right = and();
			ret = ret || right;
		}
		return ret;
	}

	private boolean and() {
		boolean ret = not();
		while( "-a".equals(peek(0))) {
			pos++;
			boolean right = not();
			ret = ret && right;
		}
		return ret;
	}

	private boolean not() {
		if( "!".equals(peek(0)) && peek(1) != null ) {
			pos++;
			return !not();
		}
		return primary();
	}

	private boolean primary() {
		String w = peek(0);
		if( w == null ) {
			throw new TestError("argument expected");
		}
		if( w.equals("(") && peek(1) != null ) {
			pos++;
			boolean ret = or();
			if( !")".equals(peek(0))) {
				throw new TestError("`)' expected");
			}
			pos++;
			return ret;
		}
		if( peek(1) != null && isBinary(peek(1)) && peek(2) != null ) {
			pos += 3;
			return binary(w, words.get(pos-2), words.get(pos-1));
		}
		if( isUnary(w) && peek(1) != null ) {
			pos += 2;
			return unary(w, words.get(pos-1));
		}
		pos++;
		return !w.isEmpty();
	}

	// ---------------------------------------------------------------- tests

	private static boolean isUnary(String w) {
		return UNARY.contains(" "+w+" ");
	}

	private static boolean isBinary(String w) {
		return BINARY.contains(" "+w+" ");
	}

	private boolean unary(String op, String val) {
		return unaryTest(op, val, ctx);
	}

	private boolean binary(String a, String op, String b) {
		switch (op) {
		case "=":
		case "==": return a.equals(b);
		case "!=": return !a.equals(b);
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
		try {
			return Long.parseLong(s.trim());
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
		case "-t": return false;
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

	/** f1 -nt -ot -ef f2 (also used by test and [ ]) */
	public static boolean fileCompare(String a, String op, String b, ShellContext ctx) {
		try {
			FileSource f1 = ctx.getFileSource(a);
			FileSource f2 = ctx.getFileSource(b);
			switch (op) {
			case "-nt": return f1.exists() && (!f2.exists() || f1.lastModified() > f2.lastModified());
			case "-ot": return f2.exists() && (!f1.exists() || f1.lastModified() < f2.lastModified());
			default: return f1.exists() && f2.exists() && f1.getCanonicalPath().equals(f2.getCanonicalPath());
			}
		} catch (IOException e) {
			return false;
		}
	}

}
