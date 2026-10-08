package us.bringardner.fsh.expand;

import java.util.List;
import java.util.Map;

import us.bringardner.fsh.ShellContext;

/**
 * Shell arithmetic, as in bash's $(( )), (( )), let and for (( ; ; )): 64-bit integers and, from
 * lowest to highest precedence,
 * <pre>
 *   ,                       a, b: both, the value is b
 *   = *= /= %= += -= &lt;&lt;= &gt;&gt;= &amp;= ^= |=     assignment (right to left)
 *   ?:                      a ? b : c (right to left)
 *   ||  &amp;&amp;                  logical (the right side is evaluated only if needed)
 *   |  ^  &amp;                 bitwise
 *   ==  !=  &lt;  &gt;  &lt;=  &gt;=        comparison (1 or 0)
 *   &lt;&lt;  &gt;&gt;                  shift
 *   +  -  *  /  %
 *   **                      power (right to left)
 *   -  +  !  ~  ++x  --x  x++  x--
 * </pre>
 * Numbers are decimal, 0x hex, 0 octal or base#digits (2 to 64). A name is a variable: unset or empty
 * is 0, and a value that is itself an expression is evaluated. name[expr] is an array element.
 * <p>
 * Not bash: a number with a decimal point (2.5) is a double, and so is any result that uses one
 * ($((5.0/2)) is 2.5). Bash has no decimals, so this changes no script that works in bash.
 * <p>
 * The text has already been expanded ($x, ${x}, $(cmd)) when it gets here.
 */
public class Arithmetic {

	/** an invalid expression or a division by 0, with bash's message */
	public static class ArithmeticError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ArithmeticError(String msg) {
			super(msg);
		}
	}

	private static final int MAX_DEPTH = 64;

	private final String text;
	private final ShellContext ctx;
	private final int depth;
	private int pos;

	private Arithmetic(String text, ShellContext ctx, int depth) {
		this.text = text;
		this.ctx = ctx;
		this.depth = depth;
	}

	/**
	 * Evaluate text, which has already been expanded. Empty text is 0.
	 */
	public static Number evaluate(String text, ShellContext ctx) {
		// $(( "3" + 1 )): double quotes are removed, as in bash
		if( text.indexOf('"') >= 0 ) {
			text = text.replace("\"", "");
		}
		return evaluate(text, ctx, 0);
	}

	private static Number evaluate(String text, ShellContext ctx, int depth) {
		if( depth > MAX_DEPTH ) {
			throw new ArithmeticError(text+": expression recursion level exceeded");
		}
		Arithmetic a = new Arithmetic(text, ctx, depth);
		a.skipSpace();
		if( a.pos >= text.length()) {
			return 0L;
		}
		Number ret = a.comma(true);
		a.skipSpace();
		if( a.pos < text.length()) {
			throw a.error("arithmetic syntax error in expression");
		}
		return ret;
	}

	// ---------------------------------------------------------------- errors

	/** where the last number or variable read starts: bash names it in an error about a value */
	private int lastToken = -1;

	private ArithmeticError errorAtLast(String msg) {
		if( lastToken < 0 ) {
			return error(msg);
		}
		int end = lastToken;
		while( end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || "_#@.".indexOf(text.charAt(end)) >= 0)) {
			end++;
		}
		return new ArithmeticError(text.trim()+": "+msg+" (error token is \""+text.substring(lastToken, end)+"\")");
	}

	/** where the last operator read starts: at the end of the text, bash names it in an error */
	private int lastOperator = -1;

	private ArithmeticError error(String msg) {
		String rest = text.substring(Math.min(pos, text.length())).trim();
		if( rest.isEmpty() && lastOperator >= 0 && msg.endsWith("operand expected")) {
			rest = text.substring(lastOperator).trim();
		}
		return new ArithmeticError(text.trim()+": "+msg+(rest.isEmpty() ? "" : " (error token is \""+rest+"\")"));
	}

	// ---------------------------------------------------------------- scanning

	private void skipSpace() {
		while( pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	/** consume op if it is next (and is not the start of a longer operator in notFollowedBy) */
	private boolean take(String op, String notFollowedBy) {
		skipSpace();
		if( text.startsWith(op, pos)) {
			int end = pos+op.length();
			if( notFollowedBy != null && end < text.length() && notFollowedBy.indexOf(text.charAt(end)) >= 0 ) {
				return false;
			}
			lastOperator = pos;
			pos = end;
			return true;
		}
		return false;
	}

	private boolean take(String op) {
		return take(op, null);
	}

	private boolean peek(String op) {
		skipSpace();
		return text.startsWith(op, pos);
	}

	// ---------------------------------------------------------------- grammar
	// each level takes eval: false while skipping the side not taken by && || ?: (no assignments then)

	private static final Number ZERO = 0L;
	private static final Number ONE = 1L;

	/** true unless 0 */
	public static boolean isTrue(Number n) {
		return n instanceof Double ? n.doubleValue() != 0 : n.longValue() != 0;
	}

	private static Number bool(boolean b) {
		return b ? ONE : ZERO;
	}

	private Number comma(boolean eval) {
		Number ret = assignment(eval);
		while( take(",")) {
			ret = assignment(eval);
		}
		return ret;
	}

	private static final String [] ASSIGN_OPS = {"<<=", ">>=", "**=", "*=", "/=", "%=", "+=", "-=", "&=", "^=", "|=", "="};

	private Number assignment(boolean eval) {
		int start = pos;
		skipSpace();
		// look ahead without side effects (a[i++] = 1 must not run i++ twice)
		if( lvalue(false) != null ) {
			skipSpace();
			for(String op : ASSIGN_OPS) {
				int opStart = pos;
				if( op.equals("=") ? take("=", "=") : take(op)) {
					pos = start;
					skipSpace();
					Lvalue lv = lvalue(eval);
					skipSpace();
					pos = opStart+op.length();
					Number right = assignment(eval);
					if( !eval ) {
						return ZERO;
					}
					Number val = op.equals("=") ? right : apply(op.substring(0, op.length()-1), lv.get(), right);
					lv.set(val);
					return val;
				}
			}
		}
		pos = start;
		return conditional(eval);
	}

	private Number conditional(boolean eval) {
		Number cond = logicalOr(eval);
		if( take("?")) {
			Number a = assignment(eval && isTrue(cond));
			if( !take(":")) {
				throw error("`:' expected for conditional expression");
			}
			Number b = assignment(eval && !isTrue(cond));
			return isTrue(cond) ? a : b;
		}
		return cond;
	}

	private Number logicalOr(boolean eval) {
		Number ret = logicalAnd(eval);
		while( take("||")) {
			Number right = logicalAnd(eval && !isTrue(ret));
			ret = bool(isTrue(ret) || isTrue(right));
		}
		return ret;
	}

	private Number logicalAnd(boolean eval) {
		Number ret = bitOr(eval);
		while( take("&&")) {
			Number right = bitOr(eval && isTrue(ret));
			ret = bool(isTrue(ret) && isTrue(right));
		}
		return ret;
	}

	private Number bitOr(boolean eval) {
		Number ret = bitXor(eval);
		while( take("|", "|=")) {
			ret = apply("|", ret, bitXor(eval));
		}
		return ret;
	}

	private Number bitXor(boolean eval) {
		Number ret = bitAnd(eval);
		while( take("^", "=")) {
			ret = apply("^", ret, bitAnd(eval));
		}
		return ret;
	}

	private Number bitAnd(boolean eval) {
		Number ret = equality(eval);
		while( take("&", "&=")) {
			ret = apply("&", ret, equality(eval));
		}
		return ret;
	}

	private Number equality(boolean eval) {
		Number ret = relational(eval);
		while( true ) {
			if( take("==")) {
				ret = bool(compare(ret, relational(eval)) == 0);
			} else if( take("!=")) {
				ret = bool(compare(ret, relational(eval)) != 0);
			} else {
				return ret;
			}
		}
	}

	private Number relational(boolean eval) {
		Number ret = shift(eval);
		while( true ) {
			if( take("<=")) {
				ret = bool(compare(ret, shift(eval)) <= 0);
			} else if( take(">=")) {
				ret = bool(compare(ret, shift(eval)) >= 0);
			} else if( take("<", "<")) {
				ret = bool(compare(ret, shift(eval)) < 0);
			} else if( take(">", ">")) {
				ret = bool(compare(ret, shift(eval)) > 0);
			} else {
				return ret;
			}
		}
	}

	private static int compare(Number a, Number b) {
		if( a instanceof Double || b instanceof Double ) {
			return Double.compare(a.doubleValue(), b.doubleValue());
		}
		return Long.compare(a.longValue(), b.longValue());
	}

	private Number shift(boolean eval) {
		Number ret = additive(eval);
		while( true ) {
			if( take("<<", "=")) {
				ret = apply("<<", ret, additive(eval));
			} else if( take(">>", "=")) {
				ret = apply(">>", ret, additive(eval));
			} else {
				return ret;
			}
		}
	}

	private Number additive(boolean eval) {
		Number ret = multiplicative(eval);
		while( true ) {
			if( take("+", "+=")) {
				ret = apply("+", ret, multiplicative(eval));
			} else if( take("-", "-=")) {
				ret = apply("-", ret, multiplicative(eval));
			} else {
				return ret;
			}
		}
	}

	private Number multiplicative(boolean eval) {
		Number ret = power(eval);
		while( true ) {
			String op;
			if( take("*", "*=")) {
				op = "*";
			} else if( take("/", "=")) {
				op = "/";
			} else if( take("%", "=")) {
				op = "%";
			} else {
				return ret;
			}
			Number right = power(eval);
			ret = eval ? apply(op, ret, right) : ZERO;
		}
	}

	private Number power(boolean eval) {
		Number base = unary(eval);
		if( take("**", "=")) {
			Number exp = power(eval);
			return eval ? apply("**", base, exp) : ZERO;
		}
		return base;
	}

	private Number unary(boolean eval) {
		skipSpace();
		if( take("++")) {
			return incDec(eval, 1, true);
		} else if( take("--")) {
			return incDec(eval, -1, true);
		} else if( take("-")) {
			Number n = unary(eval);
			return n instanceof Double ? (Number)(-n.doubleValue()) : (Number)(-n.longValue());
		} else if( take("+")) {
			return unary(eval);
		} else if( take("!", "=")) {
			return bool(!isTrue(unary(eval)));
		} else if( take("~")) {
			return ~unary(eval).longValue();
		}
		return postfix(eval);
	}

	private Number incDec(boolean eval, int by, boolean prefix) {
		skipSpace();
		Lvalue lv = lvalue(eval);
		if( lv == null ) {
			throw error("arithmetic syntax error: operand expected");
		}
		if( !eval ) {
			return ZERO;
		}
		Number old = lv.get();
		Number now = apply("+", old, (long)by);
		lv.set(now);
		return prefix ? now : old;
	}

	private Number postfix(boolean eval) {
		skipSpace();
		int start = pos;
		Lvalue lv = lvalue(eval);
		if( lv != null ) {
			lastToken = start;
			if( take("++")) {
				if( !eval ) {
					return ZERO;
				}
				Number old = lv.get();
				lv.set(apply("+", old, 1L));
				return old;
			} else if( take("--")) {
				if( !eval ) {
					return ZERO;
				}
				Number old = lv.get();
				lv.set(apply("-", old, 1L));
				return old;
			}
			return eval ? lv.get() : ZERO;
		}
		pos = start;
		return primary(eval);
	}

	private Number primary(boolean eval) {
		skipSpace();
		if( pos >= text.length()) {
			throw error("arithmetic syntax error: operand expected");
		}
		char c = text.charAt(pos);
		if( c == '(' ) {
			pos++;
			Number ret = comma(eval);
			if( !take(")")) {
				throw error("missing `)'");
			}
			return ret;
		}
		if( Character.isDigit(c) || (c == '.' && pos+1 < text.length() && Character.isDigit(text.charAt(pos+1)))) {
			return number();
		}
		throw error("arithmetic syntax error: operand expected");
	}

	private Number number() {
		int start = pos;
		lastToken = start;
		while( pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '#'
				|| text.charAt(pos) == '@' || text.charAt(pos) == '_' || text.charAt(pos) == '.')) {
			pos++;
		}
		String tok = text.substring(start, pos);
		try {
			return parseNumber(tok);
		} catch (NumberFormatException e) {
			pos = start;
			throw error("value too great for base");
		}
	}

	/** decimal, 0x hex, 0 octal or base#digits, as in bash; 2.5 is a double */
	static Number parseNumber(String tok) {
		if( tok.indexOf('.') >= 0 && tok.indexOf('#') < 0 ) {
			return Double.parseDouble(tok);
		}
		int hash = tok.indexOf('#');
		if( hash > 0 ) {
			int base = Integer.parseInt(tok.substring(0, hash));
			String digits = tok.substring(hash+1);
			if( base < 2 || base > 64 || digits.isEmpty()) {
				throw new NumberFormatException(tok);
			}
			long ret = 0;
			for(char d : digits.toCharArray()) {
				int v = digitValue(d, base);
				if( v < 0 || v >= base ) {
					throw new NumberFormatException(tok);
				}
				ret = ret*base+v;
			}
			return ret;
		}
		if( tok.startsWith("0x") || tok.startsWith("0X")) {
			return Long.parseUnsignedLong(tok.substring(2), 16);
		}
		if( tok.length() > 1 && tok.startsWith("0")) {
			return Long.parseLong(tok.substring(1), 8);
		}
		return Long.parseLong(tok);
	}

	/** digits of bases above 10: a-z, then A-Z (or a-z again up to 36), @ and _ */
	private static int digitValue(char d, int base) {
		if( d >= '0' && d <= '9' ) {
			return d-'0';
		} else if( d >= 'a' && d <= 'z' ) {
			return d-'a'+10;
		} else if( d >= 'A' && d <= 'Z' ) {
			return base <= 36 ? d-'A'+10 : d-'A'+36;
		} else if( d == '@' ) {
			return 62;
		} else if( d == '_' ) {
			return 63;
		}
		return -1;
	}

	/** a op b: in doubles if either is one (except the bit operators, which are whole numbers) */
	private Number apply(String op, Number a, Number b) {
		if( (a instanceof Double || b instanceof Double) && "+-*/%**".contains(op)) {
			double x = a.doubleValue();
			double y = b.doubleValue();
			switch (op) {
			case "+": return x+y;
			case "-": return x-y;
			case "*": return x*y;
			case "**": return Math.pow(x, y);
			case "/":
				if( y == 0 ) {
					throw errorAtLast("division by 0");
				}
				return x/y;
			default:
				if( y == 0 ) {
					throw errorAtLast("division by 0");
				}
				return x%y;
			}
		}
		long x = a.longValue();
		long y = b.longValue();
		switch (op) {
		case "*": return x*y;
		case "/":
			if( y == 0 ) {
				throw errorAtLast("division by 0");
			}
			return x/y;
		case "%":
			if( y == 0 ) {
				throw errorAtLast("division by 0");
			}
			return x%y;
		case "**":
			if( y < 0 ) {
				throw errorAtLast("exponent less than 0");
			}
			long ret = 1;
			for (long i = 0; i < y; i++) {
				ret *= x;
			}
			return ret;
		case "+": return x+y;
		case "-": return x-y;
		case "<<": return x << y;
		case ">>": return x >> y;
		case "&": return x & y;
		case "^": return x ^ y;
		case "|": return x | y;
		default:
			throw error("unknown operator "+op);
		}
	}

	// ---------------------------------------------------------------- variables

	private interface Lvalue {
		Number get();
		void set(Number value);
	}

	/**
	 * A variable name, or name[index], at pos; null (pos unchanged) if there is none.
	 */
	private Lvalue lvalue(boolean eval) {
		int start = pos;
		if( pos >= text.length() || !(Character.isLetter(text.charAt(pos)) || text.charAt(pos) == '_')) {
			return null;
		}
		while( pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
			pos++;
		}
		String name = text.substring(start, pos);
		if( pos < text.length() && text.charAt(pos) == '[' && ctx.getVariable(name) instanceof Map<?,?> ) {
			// m[key] of an associative array: the key is text, not an expression
			int end = text.indexOf(']', pos);
			if( end < 0 ) {
				throw error("missing `]'");
			}
			String key = text.substring(pos+1, end);
			if( key.length() >= 2 && (key.startsWith("\"") && key.endsWith("\"") || key.startsWith("'") && key.endsWith("'"))) {
				key = key.substring(1, key.length()-1);
			}
			pos = end+1;
			String k = key;
			return new Lvalue() {
				@Override
				public Number get() {
					Object val = ctx.getVariable(name);
					return valueOf(val instanceof Map<?,?> map ? map.get(k) : null, name);
				}
				@Override
				public void set(Number value) {
					ctx.setVariable(name, k, value);
				}
			};
		}
		if( pos < text.length() && text.charAt(pos) == '[' ) {
			pos++;
			long index = comma(eval).longValue();
			if( !take("]")) {
				throw error("missing `]'");
			}
			return new Lvalue() {
				@Override
				public Number get() {
					return valueOf(element(name, index), name);
				}
				@Override
				public void set(Number value) {
					ctx.setVariable(name, (int)index, value);
				}
			};
		}
		return new Lvalue() {
			@Override
			public Number get() {
				return valueOf(ctx.getVariable(name), name);
			}
			@Override
			public void set(Number value) {
				ctx.setVariable(name, value);
			}
		};
	}

	private Object element(String name, long index) {
		Object val = ctx.getVariable(name);
		if( val instanceof List<?> ) {
			List<?> list = (List<?>) val;
			if( index < 0 ) {
				index += list.size();
			}
			return index >= 0 && index < list.size() ? list.get((int)index) : null;
		} else if( val instanceof Map<?,?> ) {
			return ((Map<?,?>) val).get(""+index);
		}
		return index == 0 ? val : null;
	}

	/** a variable's value as a number: unset or empty is 0, and an expression is evaluated */
	private Number valueOf(Object val, String name) {
		if( val == null ) {
			return ZERO;
		}
		if( val instanceof Double || val instanceof Float ) {
			return ((Number) val).doubleValue();
		}
		if( val instanceof Number ) {
			return ((Number) val).longValue();
		}
		if( val instanceof List<?> ) {
			List<?> list = (List<?>) val;
			return list.isEmpty() ? ZERO : valueOf(list.get(0), name);
		}
		String s = val.toString().trim();
		if( s.isEmpty()) {
			return ZERO;
		}
		try {
			return parseNumber(s);
		} catch (NumberFormatException e) {
			return evaluate(s, ctx, depth+1);
		}
	}
}
