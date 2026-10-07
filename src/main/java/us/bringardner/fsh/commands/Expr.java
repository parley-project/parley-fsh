package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * The POSIX expr command. Each operand and operator is a separate argument, so shell
 * characters must be quoted: expr 3 \* 4, expr 3 '<' 4.
 */
public class Expr extends ShellCommand{
	static String name = "expr";
	static String help = "expr EXPRESSION\n"
			+ "	Print the value of EXPRESSION. Operators, from lowest to highest precedence:\n"
			+ "	ARG1 | ARG2       ARG1 if it is neither empty nor 0, otherwise ARG2\n"
			+ "	ARG1 & ARG2       ARG1 if neither argument is empty or 0, otherwise 0\n"
			+ "	ARG1 < <= = == != >= > ARG2   1 or 0 (compared as integers if both are, otherwise as text)\n"
			+ "	ARG1 + - ARG2     integer sum or difference\n"
			+ "	ARG1 * / % ARG2   integer product, quotient or remainder\n"
			+ "	STRING : REGEX    anchored match: the \\(..\\) group, or the number of characters matched\n"
			+ "	match STRING REGEX, substr STRING POS LENGTH, index STRING CHARS, length STRING\n"
			+ "	+ TOKEN           TOKEN as text, even if it is a keyword such as length\n"
			+ "	( EXPRESSION )\n"
			+ "	Exit status: 0 if the value is neither empty nor 0, 1 if it is, 2 if EXPRESSION is invalid."
			;

	private static class ExprException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		ExprException(String msg) {
			super(msg);
		}
	}

	private String [] tokens;
	private int pos;

	public Expr() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		tokens = new String[args.length];
		for (int idx = 0; idx < args.length; idx++) {
			tokens[idx] = ""+args[idx].getValue(ctx);
		}
		pos = 0;
		try {
			if( tokens.length == 0 ) {
				throw new ExprException("missing operand");
			}
			String ret = or();
			if( pos < tokens.length ) {
				throw new ExprException("syntax error: unexpected argument '"+tokens[pos]+"'");
			}
			ctx.stdout.println(ret);
			return isNullOrZero(ret) ? 1 : 0;
		} catch (ExprException e) {
			ctx.stderr.println("expr: "+e.getMessage());
			return 2;
		}
	}

	private boolean at(String op) {
		return pos < tokens.length && tokens[pos].equals(op);
	}

	private String next() {
		if( pos >= tokens.length ) {
			throw new ExprException("syntax error: missing argument after '"+tokens[pos-1]+"'");
		}
		return tokens[pos++];
	}

	private static boolean isNullOrZero(String val) {
		if( val.isEmpty()) {
			return true;
		}
		Long num = toLong(val);
		return num != null && num == 0;
	}

	private static Long toLong(String val) {
		try {
			return Long.parseLong(val.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static long integer(String val) {
		Long ret = toLong(val);
		if( ret == null ) {
			throw new ExprException("non-integer argument");
		}
		return ret;
	}

	private String or() {
		String left = and();
		while( at("|")) {
			pos++;
			String right = and();
			left = !isNullOrZero(left) ? left : right;
		}
		return left;
	}

	private String and() {
		String left = compare();
		while( at("&")) {
			pos++;
			String right = compare();
			left = isNullOrZero(left) || isNullOrZero(right) ? "0" : left;
		}
		return left;
	}

	private String compare() {
		String left = sum();
		while( at("<") || at("<=") || at("=") || at("==") || at("!=") || at(">=") || at(">")) {
			String op = next();
			String right = sum();
			Long l = toLong(left);
			Long r = toLong(right);
			int cmp = l != null && r != null ? Long.compare(l, r) : left.compareTo(right);
			boolean result;
			switch (op) {
			case "<": result = cmp < 0; break;
			case "<=": result = cmp <= 0; break;
			case "=":
			case "==": result = cmp == 0; break;
			case "!=": result = cmp != 0; break;
			case ">=": result = cmp >= 0; break;
			default: result = cmp > 0; break;
			}
			left = result ? "1" : "0";
		}
		return left;
	}

	private String sum() {
		String left = product();
		while( at("+") || at("-")) {
			String op = next();
			long r = integer(product());
			long l = integer(left);
			left = ""+(op.equals("+") ? l+r : l-r);
		}
		return left;
	}

	private String product() {
		String left = match();
		while( at("*") || at("/") || at("%")) {
			String op = next();
			long r = integer(match());
			long l = integer(left);
			if( r == 0 && !op.equals("*")) {
				throw new ExprException("division by zero");
			}
			left = ""+(op.equals("*") ? l*r : op.equals("/") ? l/r : l%r);
		}
		return left;
	}

	private String match() {
		String left = primary();
		while( at(":")) {
			pos++;
			left = match(left, primary());
		}
		return left;
	}

	private String primary() {
		String tok = next();
		switch (tok) {
		case "(":
			String ret = or();
			if( !at(")")) {
				throw new ExprException("syntax error: expecting ')'");
			}
			pos++;
			return ret;
		case "match":
			String s = primary();
			return match(s, primary());
		case "substr":
			String str = primary();
			long start = integer(primary());
			long len = integer(primary());
			if( start < 1 || len < 1 || start > str.length()) {
				return "";
			}
			int from = (int)start-1;
			return str.substring(from, (int)Math.min(str.length(), from+len));
		case "index":
			String text = primary();
			String chars = primary();
			for (int idx = 0; idx < text.length(); idx++) {
				if( chars.indexOf(text.charAt(idx)) >= 0) {
					return ""+(idx+1);
				}
			}
			return "0";
		case "length":
			return ""+primary().length();
		case "+":
			return next();
		default:
			return tok;
		}
	}

	/**
	 * STRING : REGEX. The match is anchored at the start of STRING.
	 * @return the first \(..\) group (empty if no match), or the number of characters matched
	 */
	private static String match(String str, String bre) {
		Pattern rx;
		try {
			rx = Pattern.compile(breToJava(bre));
		} catch (PatternSyntaxException e) {
			throw new ExprException("invalid regular expression "+bre);
		}
		Matcher m = rx.matcher(str);
		boolean found = m.lookingAt();
		if( m.groupCount() > 0 ) {
			return found && m.group(1) != null ? m.group(1) : "";
		}
		return found ? ""+m.end() : "0";
	}

	/**
	 * A basic regular expression in Java syntax: \( \) \{ \} group and repeat, while ( ) { } + ? |
	 * are plain characters.
	 */
	static String breToJava(String bre) {
		StringBuilder ret = new StringBuilder();
		for (int idx = 0; idx < bre.length(); idx++) {
			char c = bre.charAt(idx);
			if( c == '\\' && idx+1 < bre.length()) {
				char n = bre.charAt(++idx);
				if( "(){}".indexOf(n) >= 0) {
					ret.append(n);
				} else {
					ret.append('\\').append(n);
				}
			} else if( "(){}+?|".indexOf(c) >= 0) {
				ret.append('\\').append(c);
			} else {
				ret.append(c);
			}
		}
		return ret.toString();
	}
}
