package us.bringardner.fsh.antlr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.ShellContext;

/**
 * [[ expression ]], as in bash. The words are expanded but not split or globbed, and:
 * <pre>
 *   ( expr )  ! expr  expr &amp;&amp; expr  expr || expr   (the right side only if needed)
 *   -n s  -z s  -v name  -o option  -e -a -f -d -s -r -w -x -h -L file
 *   s == pattern  s = pattern  s != pattern  (quoted parts of the pattern are literal)
 *   s =~ regex   (an extended regular expression, found anywhere in s; BASH_REMATCH gets the groups)
 *   s1 &lt; s2  s1 &gt; s2  (text order)
 *   n1 -eq -ne -lt -le -gt -ge n2  (arithmetic)
 *   f1 -nt -ot -ef f2
 *   word  (true if not empty)
 * </pre>
 * Not bash: [ and ] standing alone group like ( and ) ([[ a ]||[ b ]]).
 */
public class DoubleBracket {

	/** an invalid expression: bash reports it and the status is 2 */
	public static class SyntaxError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public SyntaxError(String msg) {
			super(msg);
		}
	}

	private static final String OPS = " == = != =~ < > -eq -ne -lt -le -gt -ge -nt -ot -ef ";
	private static final String UNARY = " -n -z -v -o -e -a -f -d -s -r -w -x -h -L -b -c -p -S -g -u -k -t -G -O -N ";

	/** a token: an operator, a paren, or a word as written (with its quotes) */
	private static class Token {
		final String text;
		final boolean word;
		Token(String text, boolean word) {
			this.text = text;
			this.word = word;
		}
		boolean is(String op) {
			return text.equals(op);
		}
	}

	private final ShellContext ctx;
	private final List<Token> tokens;
	private int pos;

	private DoubleBracket(List<Token> tokens, ShellContext ctx) {
		this.tokens = tokens;
		this.ctx = ctx;
	}

	/**
	 * @param token the whole [[ ... ]]
	 * @return 0 if true, 1 if false, 2 if the expression is invalid (with a message on stderr)
	 */
	public static int test(String token, ShellContext ctx) {
		String body = token.substring(2, token.length()-2);
		try {
			DoubleBracket d = new DoubleBracket(tokenize(body), ctx);
			if( d.tokens.isEmpty()) {
				throw new SyntaxError("unexpected argument to conditional");
			}
			boolean ret = d.or(true);
			if( d.pos < d.tokens.size()) {
				throw new SyntaxError("syntax error near `"+d.tokens.get(d.pos).text+"'");
			}
			return ret ? 0 : 1;
		} catch (SyntaxError e) {
			ctx.stderr.println("[[: "+e.getMessage());
			return 2;
		}
	}

	// ---------------------------------------------------------------- tokens

	static List<Token> tokenize(String body) {
		List<Token> ret = new ArrayList<>();
		int n = body.length();
		int idx = 0;
		boolean regexNext = false;
		while( idx < n ) {
			char c = body.charAt(idx);
			if( Character.isWhitespace(c)) {
				idx++;
				continue;
			}
			if( body.startsWith("&&", idx) || body.startsWith("||", idx)) {
				ret.add(new Token(body.substring(idx, idx+2), false));
				idx += 2;
				continue;
			}
			if( !regexNext && (c == '(' || c == ')')) {
				ret.add(new Token(""+c, false));
				idx++;
				continue;
			}
			int start = idx;
			idx = wordEnd(body, idx, regexNext);
			String word = body.substring(start, idx);
			regexNext = word.equals("=~");
			ret.add(new Token(word, true));
		}
		// not bash: a lone [ or ] groups; [[ a ]||[ b ]] is [ [a] || [b] ]
		int depth = 0;
		int min = 0;
		for(Token t : ret) {
			if( t.word && t.is("[")) {
				depth++;
			} else if( t.word && t.is("]")) {
				depth--;
				min = Math.min(min, depth);
			}
		}
		if( min < 0 || depth != 0 ) {
			if( min < 0 && depth == 0 ) {
				ret.add(0, new Token("(", false));
				ret.add(new Token(")", false));
			}
		}
		List<Token> grouped = new ArrayList<>();
		for(Token t : ret) {
			if( t.word && t.is("[")) {
				grouped.add(new Token("(", false));
			} else if( t.word && t.is("]")) {
				grouped.add(new Token(")", false));
			} else {
				grouped.add(t);
			}
		}
		return grouped;
	}

	/** the end of the word at start: quotes, $( ) ${ } and backslashes are part of it */
	private static int wordEnd(String body, int start, boolean regex) {
		int n = body.length();
		int idx = start;
		int parens = 0;
		while( idx < n ) {
			char c = body.charAt(idx);
			if( c == '\\' ) {
				idx += 2;
			} else if( c == '\'' ) {
				int end = body.indexOf('\'', idx+1);
				idx = end < 0 ? n : end+1;
			} else if( c == '"' ) {
				idx = quoteEnd(body, idx);
			} else if( c == '$' && idx+1 < n && (body.charAt(idx+1) == '(' || body.charAt(idx+1) == '{')) {
				idx = closeEnd(body, idx+1);
			} else if( !regex && c == '(' && idx > 0 && "?*+@!".indexOf(body.charAt(idx-1)) >= 0 && (idx-1 >= start) ) {
				// an extended pattern: a@(b|c), !(x)
				idx = closeEnd(body, idx);
			} else if( regex && c == '(' ) {
				parens++;
				idx++;
			} else if( regex && c == ')' && parens > 0 ) {
				parens--;
				idx++;
			} else if( parens == 0 && (Character.isWhitespace(c) || (!regex && (c == '(' || c == ')'))
					|| body.startsWith("&&", idx) || body.startsWith("||", idx))) {
				break;
			} else {
				idx++;
			}
		}
		return Math.min(idx, n);
	}

	private static int quoteEnd(String body, int start) {
		int idx = start+1;
		while( idx < body.length() && body.charAt(idx) != '"' ) {
			if( body.charAt(idx) == '\\' ) {
				idx++;
			} else if( body.charAt(idx) == '$' && idx+1 < body.length() && body.charAt(idx+1) == '(' ) {
				idx = closeEnd(body, idx+1)-1;
			}
			idx++;
		}
		return Math.min(idx+1, body.length());
	}

	/** the index after the ) or } that closes the ( or { at start */
	private static int closeEnd(String body, int start) {
		char open = body.charAt(start);
		char close = open == '(' ? ')' : '}';
		int depth = 0;
		for (int idx = start; idx < body.length(); idx++) {
			char c = body.charAt(idx);
			if( c == '\\' ) {
				idx++;
			} else if( c == '\'' && open == '(' ) {
				int end = body.indexOf('\'', idx+1);
				idx = end < 0 ? body.length() : end;
			} else if( c == '"' ) {
				idx = quoteEnd(body, idx)-1;
			} else if( c == open ) {
				depth++;
			} else if( c == close && --depth == 0 ) {
				return idx+1;
			}
		}
		return body.length();
	}

	// ---------------------------------------------------------------- grammar

	private Token peek(int ahead) {
		return pos+ahead < tokens.size() ? tokens.get(pos+ahead) : null;
	}

	private boolean take(String op) {
		Token t = peek(0);
		if( t != null && !t.word && t.is(op)) {
			pos++;
			return true;
		}
		return false;
	}

	private boolean or(boolean eval) {
		boolean ret = and(eval);
		while( take("||")) {
			boolean right = and(eval && !ret);
			ret = ret || right;
		}
		return ret;
	}

	private boolean and(boolean eval) {
		boolean ret = not(eval);
		while( take("&&")) {
			boolean right = not(eval && ret);
			ret = ret && right;
		}
		return ret;
	}

	private boolean not(boolean eval) {
		Token t = peek(0);
		if( t != null && t.word && t.is("!") && peek(1) != null ) {
			pos++;
			return !not(eval);
		}
		return primary(eval);
	}

	private boolean primary(boolean eval) {
		Token t = peek(0);
		if( t == null ) {
			throw new SyntaxError("unexpected end of expression");
		}
		if( take("(")) {
			boolean ret = or(eval);
			if( !take(")")) {
				throw new SyntaxError("expected `)'");
			}
			return ret;
		}
		if( !t.word ) {
			throw new SyntaxError("syntax error near `"+t.text+"'");
		}
		Token next = peek(1);
		Token after = peek(2);
		// -f file (unless the next word is an operator: -f == x compares the text -f)
		if( UNARY.contains(" "+t.text+" ") && next != null && next.word && !(after != null && after.word && OPS.contains(" "+after.text+" "))
				&& !OPS.contains(" "+next.text+" ")) {
			pos += 2;
			return !eval || unary(t.text, next);
		}
		if( next != null && next.word && OPS.contains(" "+next.text+" ")) {
			if( after == null || !after.word ) {
				throw new SyntaxError("expected an argument after `"+next.text+"'");
			}
			pos += 3;
			return !eval || binary(t, next.text, after);
		}
		pos++;
		return !eval || !expand(t.text).isEmpty();
	}

	// ---------------------------------------------------------------- tests

	private boolean unary(String op, Token arg) {
		return unaryTest(op, expand(arg.text), ctx);
	}

	/** -n -z -v and the file tests (also used by test and [ ]) */
	public static boolean unaryTest(String op, String val, ShellContext ctx) {
		switch (op) {
		case "-n": return !val.isEmpty();
		case "-z": return val.isEmpty();
		case "-v": {
			java.util.regex.Matcher m = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[(.+)\\]").matcher(val);
			if( m.matches()) {
				// -v a[1], -v m[key]: that element is set
				Object arr = ctx.getVariable(m.group(1));
				if( arr instanceof java.util.Map<?,?> ) {
					return ((java.util.Map<?,?>) arr).containsKey(m.group(2));
				}
				if( arr instanceof java.util.List<?> ) {
					int idx = Arithmetic.expandAndEvaluate(m.group(2), ctx).intValue();
					return ((java.util.List<?>) arr).get(idx) != null;
				}
				return false;
			}
			return ctx.getVariable(val) != null;
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

	private boolean binary(Token left, String op, Token right) {
		String l = expand(left.text);
		switch (op) {
		case "==":
		case "=":
			return pattern(right.text).matcher(l).matches();
		case "!=":
			return !pattern(right.text).matcher(l).matches();
		case "=~":
			return regex(l, right.text);
		case "<":
			return l.compareTo(expand(right.text)) < 0;
		case ">":
			return l.compareTo(expand(right.text)) > 0;
		case "-nt":
		case "-ot":
		case "-ef":
			return fileCompare(l, op, expand(right.text), ctx);
		default:
			Number a = Arithmetic.evaluate(l, ctx);
			Number b = Arithmetic.evaluate(expand(right.text), ctx);
			int cmp = Double.compare(a.doubleValue(), b.doubleValue());
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

	private boolean regex(String text, String word) {
		Pattern rx;
		try {
			rx = Pattern.compile(regexOf(word), Glob.option(ctx, "nocasematch") ? Pattern.CASE_INSENSITIVE : 0);
		} catch (PatternSyntaxException e) {
			throw new SyntaxError("invalid regular expression `"+word+"'");
		}
		Matcher m = rx.matcher(text);
		FshList groups = new FshList();
		boolean ret = m.find();
		if( ret ) {
			for (int idx = 0; idx <= m.groupCount(); idx++) {
				groups.add(m.group(idx) == null ? "" : m.group(idx));
			}
		}
		ctx.setVariable("BASH_REMATCH", groups);
		return ret;
	}

	// ---------------------------------------------------------------- words

	/** a part of a word: its text after expansion, and whether it was quoted */
	private static class Part {
		final String text;
		final boolean quoted;
		Part(String text, boolean quoted) {
			this.text = text;
			this.quoted = quoted;
		}
	}

	private String expand(String raw) {
		StringBuilder ret = new StringBuilder();
		for(Part p : parts(raw)) {
			ret.append(p.text);
		}
		return ret.toString();
	}

	/** the pattern of == and != : unquoted parts are glob patterns, quoted parts are text */
	private Pattern pattern(String raw) {
		StringBuilder rx = new StringBuilder();
		for(Part p : parts(raw)) {
			rx.append(p.quoted ? Pattern.quote(p.text) : Glob.toRegex(p.text).pattern());
		}
		return Pattern.compile(rx.toString(), Pattern.DOTALL | (Glob.option(ctx, "nocasematch") ? Pattern.CASE_INSENSITIVE : 0));
	}

	/** the regular expression of =~ : unquoted parts are a regular expression, quoted parts are text */
	private String regexOf(String raw) {
		StringBuilder rx = new StringBuilder();
		for(Part p : parts(raw)) {
			rx.append(p.quoted ? Pattern.quote(p.text) : posixClasses(p.text));
		}
		return rx.toString();
	}

	/** [:alpha:] in a bracket expression as Java's \p{Alpha} ([:^alpha:] is \P{Alpha}, not bash) */
	static String posixClasses(String rx) {
		String [][] classes = {
				{"word", "\\w", "\\W"}, {"ascii", "\\p{ASCII}", "\\P{ASCII}"}, {"lower", "\\p{Lower}", "\\P{Lower}"},
				{"upper", "\\p{Upper}", "\\P{Upper}"}, {"alpha", "\\p{Alpha}", "\\P{Alpha}"}, {"digit", "\\p{Digit}", "\\P{Digit}"},
				{"alnum", "\\p{Alnum}", "\\P{Alnum}"}, {"punct", "\\p{Punct}", "\\P{Punct}"}, {"graph", "\\p{Graph}", "\\P{Graph}"},
				{"print", "\\p{Print}", "\\P{Print}"}, {"blank", "\\p{Blank}", "\\P{Blank}"}, {"cntrl", "\\p{Cntrl}", "\\P{Cntrl}"},
				{"xdigit", "\\p{XDigit}", "\\P{XDigit}"}, {"space", "\\s", "\\S"}};
		String ret = rx;
		for(String [] c : classes) {
			ret = ret.replace("[:^"+c[0]+":]", c[2]).replace("[:"+c[0]+":]", c[1]);
		}
		return ret;
	}

	private List<Part> parts(String raw) {
		List<Part> ret = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		int n = raw.length();
		int idx = 0;
		while( idx < n ) {
			char c = raw.charAt(idx);
			if( c == '\'' ) {
				flush(ret, plain);
				int end = raw.indexOf('\'', idx+1);
				end = end < 0 ? n : end;
				ret.add(new Part(raw.substring(idx+1, end), true));
				idx = end+1;
			} else if( c == '$' && idx+1 < n && raw.charAt(idx+1) == '\'' ) {
				flush(ret, plain);
				int end = idx+2;
				while( end < n && raw.charAt(end) != '\'' ) {
					end += raw.charAt(end) == '\\' ? 2 : 1;
				}
				ret.add(new Part(ShellContext.ansiC(raw.substring(idx+2, Math.min(end, n))), true));
				idx = end+1;
			} else if( c == '"' ) {
				flush(ret, plain);
				int end = quoteEnd(raw, idx);
				String inner = raw.substring(idx+1, Math.max(idx+1, end-1));
				ret.add(new Part(FileSourceShPreProcessorVisitorImpl.processString(inner, ctx,
						FileSourceShPreProcessorVisitorImpl.Quoting.DOUBLE_QUOTED), true));
				idx = end;
			} else if( c == '\\' && idx+1 < n ) {
				flush(ret, plain);
				ret.add(new Part(""+raw.charAt(idx+1), true));
				idx += 2;
			} else if( c == '$' && idx+1 < n && (raw.charAt(idx+1) == '(' || raw.charAt(idx+1) == '{')) {
				int end = closeEnd(raw, idx+1);
				plain.append(raw, idx, end);
				idx = end;
			} else {
				plain.append(c);
				idx++;
			}
		}
		flush(ret, plain);
		return ret;
	}

	/** an unquoted part: $x ${x} $(cmd) are expanded, and ~ at the start is the home directory */
	private void flush(List<Part> ret, StringBuilder plain) {
		if( plain.length() == 0 ) {
			return;
		}
		String text = plain.toString();
		plain.setLength(0);
		if( text.indexOf('$') >= 0 || text.indexOf('`') >= 0 ) {
			text = FileSourceShPreProcessorVisitorImpl.processString(text, ctx);
		}
		if( ret.isEmpty() && (text.equals("~") || text.startsWith("~/"))) {
			Object home = ctx.getVariable("HOME");
			if( home != null ) {
				text = home+text.substring(1);
			}
		}
		ret.add(new Part(text, false));
	}
}
