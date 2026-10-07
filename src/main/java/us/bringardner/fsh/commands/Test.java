package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.DoubleBracket;

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
			ctx.stderr.println(label+": "+e.getMessage());
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
			ctx.stderr.println(label+": "+e.getMessage());
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
		return DoubleBracket.unaryTest(op, val, ctx);
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
		case "-ef": return DoubleBracket.fileCompare(a, op, b, ctx);
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
}
