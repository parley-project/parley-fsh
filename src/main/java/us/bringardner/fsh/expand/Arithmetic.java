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
 * It is read as bash's expr.c reads it (its tokens, and its errors: the expression, what is wrong,
 * and the text from the token it was at).
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
		/** from an expression inside one (a variable's value, a subscript): bash says it without ((: */
		public boolean bare;
		/** found in a subscript (a[" "]): bash's let does not catch it, the command line ends */
		public boolean inSubscript;
		public ArithmeticError(String msg) {
			super(msg);
		}
	}

	/** set -u: an unset variable in an expression (it ends the shell, as bash's) */
	public static class Unbound extends ArithmeticError {
		private static final long serialVersionUID = 1L;
		public Unbound(String name) {
			super(name+": unbound variable");
		}
	}

	private static final int MAX_DEPTH = 1024;

	// tokens: a character stands for itself; these are the others
	private static final int EOF = 0;
	private static final int STR = 256, NUM = 257, EQEQ = 258, NEQ = 259, LEQ = 260, GEQ = 261, LSH = 262, RSH = 263,
			LAND = 264, LOR = 265, POWER = 266, OP_ASSIGN = 267, PREINC = 268, PREDEC = 269, POSTINC = 270, POSTDEC = 271,
			COND = 272;

	private final String text;
	private final ShellContext ctx;
	private final int depth;

	/** where the next token starts */
	private int tp;
	/** where the last token read starts (an error names the text from there) */
	private int lasttp;
	private int curtok;
	private int lasttok;
	/** the name (with its [subscript]) of the last STR token */
	private String tokstr;
	/** the value of the last NUM or STR token */
	private Number tokval = ZERO;
	/** the operator of OP_ASSIGN (+ for +=, LSH for <<=) */
	private int assigntok;
	/** above 0: what is read is not evaluated (the side of && || ?: not taken) */
	private int noeval;

	private Arithmetic(String text, ShellContext ctx, int depth) {
		this.text = text;
		this.ctx = ctx;
		this.depth = depth;
	}

	/**
	 * Evaluate text, which has already been expanded. Empty text is 0.
	 */
	public static Number evaluate(String text, ShellContext ctx) {
		// ($(( "3" + 1 )): the quotes were removed when it was expanded)
		return evaluate(text, ctx, 0);
	}

	/** text as it is (an array subscript in a[\" \"]=v keeps its quotes, and is an error then) */
	public static Number evaluateLiteral(String text, ShellContext ctx) {
		return evaluate(text, ctx, 0);
	}

	private static Number evaluate(String text, ShellContext ctx, int depth) {
		Arithmetic a = new Arithmetic(text, ctx, depth);
		if( depth > MAX_DEPTH ) {
			throw a.evalerror("expression recursion level exceeded");
		}
		if( text.isBlank()) {
			return ZERO;
		}
		a.readtok();
		Number ret = a.expcomma();
		if( a.curtok != EOF ) {
			throw a.evalerror("arithmetic syntax error in expression");
		}
		return ret;
	}

	// ---------------------------------------------------------------- errors

	/** where the text ends for an error (while a number is read: at its end, as bash's) */
	private int errorEnd = -1;

	/** as bash's evalerror: the expression, the message, and the text from the last token */
	private ArithmeticError evalerror(String msg) {
		String shown = errorEnd >= 0 ? text.substring(0, errorEnd) : text;
		String token = lasttp >= 0 && lasttp < shown.length() ? shown.substring(lasttp) : "";
		ArithmeticError e = new ArithmeticError(shown.stripLeading()+": "+msg+" (error token is \""+token+"\")");
		e.bare = depth > 0;
		return e;
	}

	// ---------------------------------------------------------------- tokens

	private static boolean whitespace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}

	private static boolean nameStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	private char at(int i) {
		return i < text.length() ? text.charAt(i) : '\0';
	}

	/** the state readtok changes (to look ahead and come back) */
	private int[] saveTok() {
		return new int[] {tp, lasttp, curtok, lasttok, assigntok};
	}

	private void restoreTok(int[] s, String str, Number val) {
		tp = s[0];
		lasttp = s[1];
		curtok = s[2];
		lasttok = s[3];
		assigntok = s[4];
		tokstr = str;
		tokval = val;
	}

	/** bash's readtok: the next token into curtok (and tokstr/tokval) */
	private void readtok() {
		int cp = tp;
		while( cp < text.length() && whitespace(text.charAt(cp))) {
			cp++;
		}
		if( cp >= text.length()) {
			lasttok = curtok;
			curtok = EOF;
			tp = cp;
			return;
		}
		char c = text.charAt(cp++);
		lasttp = tp = cp-1;
		int tok;
		if( nameStart(c)) {
			while( cp < text.length() && (Character.isLetterOrDigit(text.charAt(cp)) || text.charAt(cp) == '_')) {
				cp++;
			}
			if( at(cp) == '[' ) {
				int e = skipSubscript(cp);
				if( e < 0 ) {
					throw evalerror("bad array subscript");
				}
				cp = e+1;
			}
			String name = text.substring(tp, cp);
			// what comes next: name = ... does not evaluate name (it may be unset, or not a number)
			int[] saved = saveTok();
			Number savedVal = tokval;
			int savedNoeval = noeval;
			tp = cp;
			noeval = 1;
			tokstr = name;
			// (as after a name: x++ is read as that)
			curtok = STR;
			readtok();
			int peektok = curtok;
			restoreTok(saved, name, savedVal);
			noeval = savedNoeval;
			tp = cp;
			tokstr = name;
			if( lasttok == PREINC || lasttok == PREDEC || peektok != '=' ) {
				tokval = streval(name);
			} else {
				tokval = ZERO;
			}
			lasttok = curtok;
			curtok = STR;
			return;
		}
		if( Character.isDigit(c) || c == '.' && Character.isDigit(at(cp))) {
			while( cp < text.length() && (Character.isLetterOrDigit(text.charAt(cp)) || "#@_.".indexOf(text.charAt(cp)) >= 0)) {
				cp++;
			}
			errorEnd = cp;
			tokval = strlong(text.substring(tp, cp));
			errorEnd = -1;
			lasttok = curtok;
			curtok = NUM;
			tp = cp;
			return;
		}
		char c1 = at(cp);
		if( c == '=' && c1 == '=' ) {
			tok = EQEQ;
			cp++;
		} else if( c == '!' && c1 == '=' ) {
			tok = NEQ;
			cp++;
		} else if( c == '>' && c1 == '=' ) {
			tok = GEQ;
			cp++;
		} else if( c == '<' && c1 == '=' ) {
			tok = LEQ;
			cp++;
		} else if( c == '<' && c1 == '<' ) {
			cp++;
			if( at(cp) == '=' ) {
				assigntok = LSH;
				tok = OP_ASSIGN;
				cp++;
			} else {
				tok = LSH;
			}
		} else if( c == '>' && c1 == '>' ) {
			cp++;
			if( at(cp) == '=' ) {
				assigntok = RSH;
				tok = OP_ASSIGN;
				cp++;
			} else {
				tok = RSH;
			}
		} else if( c == '&' && c1 == '&' ) {
			tok = LAND;
			cp++;
		} else if( c == '|' && c1 == '|' ) {
			tok = LOR;
			cp++;
		} else if( c == '*' && c1 == '*' ) {
			cp++;
			if( at(cp) == '=' ) {
				// (**=: fsh's too)
				assigntok = POWER;
				tok = OP_ASSIGN;
				cp++;
			} else {
				tok = POWER;
			}
		} else if( (c == '-' || c == '+') && c1 == c && curtok == STR ) {
			tok = c == '-' ? POSTDEC : POSTINC;
			cp++;
		} else if( (c == '-' || c == '+') && c1 == c && curtok == NUM && (lasttok == PREINC || lasttok == PREDEC)) {
			// --x++
			throw evalerror(c+""+c+": assignment requires lvalue");
		} else if( (c == '-' || c == '+') && c1 == c ) {
			// ++ before a name is ++x; otherwise it is + +
			int xp = cp+1;
			while( xp < text.length() && whitespace(text.charAt(xp))) {
				xp++;
			}
			if( nameStart(at(xp))) {
				tok = c == '-' ? PREDEC : PREINC;
				cp++;
			} else {
				tok = c;
			}
		} else if( c1 == '=' && "*/%+-&^|".indexOf(c) >= 0 ) {
			assigntok = c;
			tok = OP_ASSIGN;
			cp++;
		} else if( ARITHOPS.indexOf(c) < 0 ) {
			// not an operator: after one (or at the start) an operand was wanted
			if( curtok == EOF || curtok < 256 && ARITHOPS.indexOf(curtok) >= 0 || multiop(curtok)) {
				throw evalerror("arithmetic syntax error: operand expected");
			}
			throw evalerror("arithmetic syntax error: invalid arithmetic operator");
		} else {
			tok = c;
		}
		lasttok = curtok;
		curtok = tok;
		tp = cp;
	}

	/** the characters that are operators by themselves */
	private static final String ARITHOPS = "=><+-*/%!()&|^~?:,";

	private static boolean multiop(int tok) {
		return tok >= EQEQ && tok <= COND && tok != STR && tok != NUM;
	}

	/** the ] that closes the [ at open (nested ones skipped), or -1 */
	private int skipSubscript(int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == '\\' ) {
				// (a quoted ] does not close it: a key with ] in it)
				i++;
			} else if( c == '[' ) {
				depth++;
			} else if( c == ']' && --depth == 0 ) {
				return i;
			}
		}
		return -1;
	}

	/** bash's strlong: decimal, 0x hex, 0 octal or base#digits; 2.5 is a double */
	private Number strlong(String num) {
		if( num.indexOf('.') >= 0 && num.indexOf('#') < 0 ) {
			try {
				return Double.parseDouble(num);
			} catch (NumberFormatException e) {
				throw evalerror("arithmetic syntax error: invalid arithmetic operator");
			}
		}
		int base = 10;
		int i = 0;
		if( num.charAt(0) == '0' ) {
			i = 1;
			if( num.length() > 1 && (num.charAt(1) == 'x' || num.charAt(1) == 'X')) {
				base = 16;
				i = 2;
			} else {
				base = 8;
			}
		}
		long val = 0;
		boolean foundBase = false;
		int digits = 0;
		for (; i < num.length(); i++) {
			char c = num.charAt(i);
			if( c == '#' ) {
				// (0#4 and 2#1#1 are no number; 99#1 is no base)
				if( foundBase || base != 10 ) {
					throw evalerror("invalid number");
				}
				if( val < 2 || val > 64 ) {
					throw evalerror("invalid arithmetic base");
				}
				base = (int) val;
				val = 0;
				foundBase = true;
				digits = 0;
				continue;
			}
			int v;
			if( Character.isDigit(c)) {
				v = c-'0';
			} else if( c >= 'a' && c <= 'z' ) {
				v = c-'a'+10;
			} else if( c >= 'A' && c <= 'Z' ) {
				v = base <= 36 ? c-'A'+10 : c-'A'+36;
			} else if( c == '@' ) {
				v = 62;
			} else if( c == '_' ) {
				v = 63;
			} else {
				throw evalerror("invalid number");
			}
			if( v >= base ) {
				throw evalerror("value too great for base");
			}
			val = val*base+v;
			digits++;
		}
		if( foundBase && digits == 0 ) {
			throw evalerror("invalid integer constant");
		}
		return val;
	}

	// ---------------------------------------------------------------- grammar (bash's expr.c)

	private static final Number ZERO = 0L;
	private static final Number ONE = 1L;

	/** true unless 0 */
	public static boolean isTrue(Number n) {
		return n instanceof Double ? n.doubleValue() != 0 : n.longValue() != 0;
	}

	private static Number bool(boolean b) {
		return b ? ONE : ZERO;
	}

	private Number expcomma() {
		Number value = expassign();
		while( curtok == ',' ) {
			readtok();
			value = expassign();
		}
		return value;
	}

	private Number expassign() {
		Number value = expcond();
		if( curtok == '=' || curtok == OP_ASSIGN ) {
			boolean special = curtok == OP_ASSIGN;
			if( lasttok != STR ) {
				throw evalerror("attempted assignment to non-variable");
			}
			int op = assigntok;
			Number lvalue = value;
			if( tokstr == null ) {
				throw evalerror("syntax error in variable assignment");
			}
			String lhs = tokstr;
			readtok();
			value = expassign();
			if( special ) {
				if( (op == '/' || op == '%') && !isTrue(value)) {
					throw evalerror("division by 0");
				}
				value = apply(op, lvalue, value);
			}
			if( noeval == 0 ) {
				bind(lhs, value);
			}
			tokstr = null;
		}
		return value;
	}

	private Number expcond() {
		Number cval = explor();
		Number rval = cval;
		if( curtok == '?' ) {
			boolean c = isTrue(cval);
			// (as bash's: not evaluating before the next token is read, which reads a name's value)
			if( !c ) {
				noeval++;
			}
			readtok();
			if( curtok == EOF || curtok == ':' ) {
				throw evalerror("expression expected");
			}
			Number val1 = expcomma();
			if( !c ) {
				noeval--;
			}
			if( curtok != ':' ) {
				throw evalerror("`:' expected for conditional expression");
			}
			if( c ) {
				noeval++;
			}
			readtok();
			if( curtok == EOF ) {
				throw evalerror("expression expected");
			}
			Number val2 = expcond();
			if( c ) {
				noeval--;
			}
			rval = c ? val1 : val2;
			lasttok = COND;
		}
		return rval;
	}

	private Number explor() {
		Number val1 = expland();
		while( curtok == LOR ) {
			boolean skip = isTrue(val1);
			if( skip ) {
				noeval++;
			}
			readtok();
			Number val2 = expland();
			if( skip ) {
				noeval--;
			}
			val1 = bool(isTrue(val1) || isTrue(val2));
			lasttok = LOR;
		}
		return val1;
	}

	private Number expland() {
		Number val1 = expbor();
		while( curtok == LAND ) {
			boolean skip = !isTrue(val1);
			if( skip ) {
				noeval++;
			}
			readtok();
			Number val2 = expbor();
			if( skip ) {
				noeval--;
			}
			val1 = bool(isTrue(val1) && isTrue(val2));
			lasttok = LAND;
		}
		return val1;
	}

	private Number expbor() {
		Number val1 = expbxor();
		while( curtok == '|' ) {
			readtok();
			val1 = apply('|', val1, expbxor());
			lasttok = NUM;
		}
		return val1;
	}

	private Number expbxor() {
		Number val1 = expband();
		while( curtok == '^' ) {
			readtok();
			val1 = apply('^', val1, expband());
			lasttok = NUM;
		}
		return val1;
	}

	private Number expband() {
		Number val1 = exp5();
		while( curtok == '&' ) {
			readtok();
			val1 = apply('&', val1, exp5());
			lasttok = NUM;
		}
		return val1;
	}

	private Number exp5() {
		Number val1 = exp4();
		while( curtok == EQEQ || curtok == NEQ ) {
			int op = curtok;
			readtok();
			Number val2 = exp4();
			val1 = bool(op == EQEQ ? compare(val1, val2) == 0 : compare(val1, val2) != 0);
			lasttok = NUM;
		}
		return val1;
	}

	private Number exp4() {
		Number val1 = expshift();
		while( curtok == LEQ || curtok == GEQ || curtok == '<' || curtok == '>' ) {
			int op = curtok;
			readtok();
			Number val2 = expshift();
			int cmp = compare(val1, val2);
			val1 = bool(op == LEQ ? cmp <= 0 : op == GEQ ? cmp >= 0 : op == '<' ? cmp < 0 : cmp > 0);
			lasttok = NUM;
		}
		return val1;
	}

	private static int compare(Number a, Number b) {
		if( a instanceof Double || b instanceof Double ) {
			return Double.compare(a.doubleValue(), b.doubleValue());
		}
		return Long.compare(a.longValue(), b.longValue());
	}

	private Number expshift() {
		Number val1 = exp3();
		while( curtok == LSH || curtok == RSH ) {
			int op = curtok;
			readtok();
			val1 = apply(op, val1, exp3());
			lasttok = NUM;
		}
		return val1;
	}

	private Number exp3() {
		Number val1 = exp2();
		while( curtok == '+' || curtok == '-' ) {
			int op = curtok;
			readtok();
			val1 = apply(op, val1, exp2());
			lasttok = NUM;
		}
		return val1;
	}

	private Number exp2() {
		Number val1 = exppower();
		while( curtok == '*' || curtok == '/' || curtok == '%' ) {
			int op = curtok;
			readtok();
			Number val2 = exppower();
			if( (op == '/' || op == '%') && !isTrue(val2)) {
				if( noeval == 0 ) {
					throw evalerror("division by 0");
				}
				val2 = ONE;
			}
			val1 = apply(op, val1, val2);
			lasttok = NUM;
		}
		return val1;
	}

	private Number exppower() {
		Number val1 = exp1();
		while( curtok == POWER ) {
			readtok();
			Number val2 = exppower();
			lasttok = NUM;
			val1 = apply(POWER, val1, val2);
		}
		return val1;
	}

	private Number exp1() {
		if( curtok == '!' ) {
			readtok();
			Number v = bool(!isTrue(exp1()));
			lasttok = NUM;
			return v;
		} else if( curtok == '~' ) {
			readtok();
			Number v = ~exp1().longValue();
			lasttok = NUM;
			return v;
		} else if( curtok == '-' ) {
			readtok();
			Number n = exp1();
			lasttok = NUM;
			return n instanceof Double ? (Number) (-n.doubleValue()) : (Number) (-n.longValue());
		} else if( curtok == '+' ) {
			readtok();
			Number n = exp1();
			lasttok = NUM;
			return n;
		}
		return exp0();
	}

	private Number exp0() {
		Number val;
		if( curtok == PREINC || curtok == PREDEC ) {
			int stok = lasttok = curtok;
			readtok();
			if( curtok != STR ) {
				throw evalerror("identifier expected after pre-increment or pre-decrement");
			}
			Number v2 = apply('+', tokval, (long) (stok == PREINC ? 1 : -1));
			if( noeval == 0 ) {
				bind(tokstr, v2);
			}
			val = v2;
			// (so that --x=7 is an error)
			curtok = NUM;
			readtok();
		} else if( curtok == '(' ) {
			readtok();
			val = expcomma();
			if( curtok != ')' ) {
				throw evalerror("missing `)'");
			}
			readtok();
		} else if( curtok == NUM || curtok == STR ) {
			val = tokval;
			if( curtok == STR ) {
				// x++ or x--
				int[] saved = saveTok();
				String name = tokstr;
				Number savedVal = tokval;
				int savedNoeval = noeval;
				noeval = 1;
				readtok();
				int stok = curtok;
				if( stok == POSTINC || stok == POSTDEC ) {
					tokstr = name;
					noeval = savedNoeval;
					lasttok = STR;
					Number v2 = apply('+', val, (long) (stok == POSTINC ? 1 : -1));
					if( noeval == 0 ) {
						bind(name, v2);
					}
					// (so that x++=7 is an error)
					curtok = NUM;
				} else {
					restoreTok(saved, name, savedVal);
					noeval = savedNoeval;
				}
			}
			readtok();
		} else {
			throw evalerror("arithmetic syntax error: operand expected");
		}
		return val;
	}

	// ---------------------------------------------------------------- arithmetic

	/** a op b: in doubles if either is one (except the bit operators, which are whole numbers) */
	private Number apply(int op, Number a, Number b) {
		if( op == POWER ) {
			if( !(a instanceof Double || b instanceof Double)) {
				long y = b.longValue();
				if( y == 0 ) {
					return ONE;
				}
				if( y < 0 ) {
					throw evalerror("exponent less than 0");
				}
				long x = a.longValue();
				long ret = 1;
				while( y > 0 ) {
					if( (y & 1) != 0 ) {
						ret *= x;
					}
					x *= x;
					y >>= 1;
				}
				return ret;
			}
			return Math.pow(a.doubleValue(), b.doubleValue());
		}
		if( (a instanceof Double || b instanceof Double) && op < 256 && "+-*/%".indexOf(op) >= 0 ) {
			double x = a.doubleValue();
			double y = b.doubleValue();
			switch (op) {
			case '+': return x+y;
			case '-': return x-y;
			case '*': return x*y;
			case '/':
				if( y == 0 ) {
					throw evalerror("division by 0");
				}
				return x/y;
			default:
				if( y == 0 ) {
					throw evalerror("division by 0");
				}
				return x%y;
			}
		}
		long x = a.longValue();
		long y = b.longValue();
		switch (op) {
		case '*': return x*y;
		case '/':
			if( y == 0 ) {
				throw evalerror("division by 0");
			}
			return x/y;
		case '%':
			if( y == 0 ) {
				throw evalerror("division by 0");
			}
			return x%y;
		case '+': return x+y;
		case '-': return x-y;
		case LSH: return x << y;
		case RSH: return x >> y;
		case '&': return x & y;
		case '^': return x ^ y;
		case '|': return x | y;
		default:
			throw evalerror("arithmetic syntax error: invalid arithmetic operator");
		}
	}

	// ---------------------------------------------------------------- variables

	/** a name's value (name or name[subscript]): unset or empty is 0, an expression is evaluated */
	private Number streval(String tok) {
		if( noeval > 0 ) {
			return ZERO;
		}
		int b = tok.indexOf('[');
		boolean nounset = ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.NullParameterIsError);
		if( b < 0 ) {
			Object v = ctx.getVariable(tok);
			if( v == null && nounset ) {
				throw new Unbound(tok);
			}
			return valueOf(v);
		}
		String name = tok.substring(0, b);
		String sub = tok.substring(b+1, tok.length()-1);
		if( sub.isEmpty()) {
			badName(tok);
			return ZERO;
		}
		Object v = ctx.getVariable(name);
		if( v instanceof Map<?,?> m ) {
			if( nounset && m.get(key(sub)) == null ) {
				throw new Unbound(name);
			}
			return valueOf(m.get(key(sub)));
		}
		// (a subscript's double quotes are removed: a[\"\"] in (( )) is a[0])
		long index = subscriptValue(sub).longValue();
		lastElement = tok;
		lastIndex = index;
		Object e = element(v, index);
		if( e == null && nounset ) {
			throw new Unbound(name);
		}
		return valueOf(e);
	}

	/** an indexed subscript's value (an error in it is marked as one) */
	private Number subscriptValue(String sub) {
		try {
			return evaluate(dequote(sub), ctx, depth+1);
		} catch (ArithmeticError e) {
			e.inSubscript = true;
			throw e;
		}
	}

	/** the element streval read last, and its index (x[RANDOM]++ evaluates the subscript once) */
	private String lastElement;
	private long lastIndex;

	/** name = value (name[subscript] too) */
	private void bind(String tok, Number value) {
		int b = tok.indexOf('[');
		if( b < 0 ) {
			Object v = ctx.getVariable(tok);
			if( v instanceof Map<?,?> ) {
				// (an array's name is its element 0)
				ctx.setVariable(tok, "0", value);
			} else if( v instanceof List<?> ) {
				ctx.setVariable(tok, 0, value);
			} else {
				ctx.setVariable(tok, value);
			}
			return;
		}
		String name = tok.substring(0, b);
		String sub = tok.substring(b+1, tok.length()-1);
		if( sub.isEmpty()) {
			badName(tok);
			return;
		}
		if( ctx.getVariable(name) instanceof Map<?,?> ) {
			ctx.setVariable(name, key(sub), value);
		} else {
			long index = tok.equals(lastElement) ? lastIndex : subscriptValue(sub).longValue();
			if( index < 0 ) {
				Object v = ctx.getVariable(name);
				long size = v instanceof us.bringardner.fsh.FshList f ? (f.isEmpty() ? 0 : f.getIndexes().get(f.size()-1)+1)
						: v instanceof List<?> l ? l.size() : v == null ? 0 : 1;
				index += size;
			}
			ctx.setVariable(name, (int) index, value);
		}
	}

	/** a[] in an expression: said (as the command running says it), and it goes on */
	private void badName(String tok) {
		ctx.error((ctx.builtin != null ? ctx.builtin+": " : "")+"`"+tok+"': not a valid identifier");
	}

	/** a subscript's double quotes are removed (a[\"\"] in (( )) is a[0]; not by let with assoc_expand_once) */
	private String dequote(String sub) {
		if( sub.indexOf('\\') < 0 && expandSubscript != null && (sub.indexOf('$') >= 0 || sub.indexOf('`') >= 0)) {
			// a subscript not expanded yet ((( $expr )) with expr='a[$i]'): expanded now, as bash's
			return expandSubscript.apply(ctx, sub);
		}
		sub = unbackslash(sub);
		if( "let".equals(ctx.builtin) && us.bringardner.fsh.Glob.option(ctx, "assoc_expand_once")) {
			return sub;
		}
		return sub.replace("\"", "");
	}

	/** a subscript's text expanded as a word (set by the expander) */
	public static java.util.function.BiFunction<ShellContext,String,String> expandSubscript;

	/** the \ quoting of a subscript taken away (\] is ]) */
	private static String unbackslash(String s) {
		if( s.indexOf('\\') < 0 ) {
			return s;
		}
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if( c == '\\' && i+1 < s.length()) {
				c = s.charAt(++i);
			}
			ret.append(c);
		}
		return ret.toString();
	}

	/** an associative array's key as written in the expression ('k' and "k": k) */
	private String key(String sub) {
		if( sub.indexOf('\\') >= 0 ) {
			// (quoted when it was expanded: as it is)
			return unbackslash(sub);
		}
		if( us.bringardner.fsh.Glob.option(ctx, "assoc_expand_once")) {
			// (expanded once already, as the words of let and (( )) are: as it is)
			return sub;
		}
		if( expandSubscript != null && (sub.indexOf('$') >= 0 || sub.indexOf('`') >= 0 || sub.indexOf('\'') >= 0)) {
			return expandSubscript.apply(ctx, sub);
		}
		if( sub.length() >= 2 && (sub.startsWith("'") && sub.endsWith("'") || sub.startsWith("\"") && sub.endsWith("\""))) {
			return sub.substring(1, sub.length()-1);
		}
		return sub;
	}

	private static Object element(Object val, long index) {
		if( val instanceof us.bringardner.fsh.FshList f ) {
			// (sparse: by index; a negative one from past the highest)
			if( index < 0 ) {
				index += f.isEmpty() ? 0 : f.getIndexes().get(f.size()-1)+1;
			}
			return index >= 0 && index <= Integer.MAX_VALUE ? f.get((int) index) : null;
		}
		if( val instanceof List<?> list ) {
			if( index < 0 ) {
				index += list.size();
			}
			return index >= 0 && index < list.size() ? list.get((int) index) : null;
		} else if( val instanceof Map<?,?> m ) {
			return m.get(""+index);
		}
		return index == 0 ? val : null;
	}

	/** a variable's value as a number: unset or empty is 0, and an expression is evaluated */
	private Number valueOf(Object val) {
		if( val == null ) {
			return ZERO;
		}
		if( val instanceof Double || val instanceof Float ) {
			return ((Number) val).doubleValue();
		}
		if( val instanceof Number ) {
			return ((Number) val).longValue();
		}
		if( val instanceof List<?> list ) {
			return list.isEmpty() ? ZERO : valueOf(list.get(0));
		}
		if( val instanceof Map<?,?> m ) {
			return valueOf(m.get("0"));
		}
		String s = val.toString();
		if( s.isBlank()) {
			return ZERO;
		}
		if( s.matches("[1-9][0-9]{0,17}|0")) {
			// (the common case, without a parser)
			return Long.parseLong(s);
		}
		// (as it is: an error shows its blanks)
		return evaluate(s, ctx, depth+1);
	}
}
