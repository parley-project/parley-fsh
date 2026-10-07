package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

/**
 * printf, as bash's builtin: the format is used again while arguments are left, a missing
 * argument is empty (or 0), and a number may be 'c (the code of c).
 */
public class Printf extends ShellCommand{
	static String name = "printf";
	static String help = "printf [-v var] format [arguments]\n"
			+ "	Write the arguments under control of format: %s %b (with backslash escapes) %q (quoted)\n"
			+ "	%c %d %i %u %o %x %X %f %e %E %g %G %%, with flags - + space 0 #, a width and a precision\n"
			+ "	(* takes them from the arguments). The format may have \\n \\t \\\\ \\0nnn \\xHH ... escapes.\n"
			+ "	-v var assigns the output to var instead of writing it."
			;

	/** an argument that is not a number: printf goes on, with status 1 */
	private boolean failed;

	public Printf() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> words = new ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			words.add(""+args[idx].getValue(ctx));
		}
		String var = null;
		int idx = 0;
		while( idx < words.size() && words.get(idx).startsWith("-")) {
			if( words.get(idx).equals("-v") && idx+1 < words.size()) {
				var = words.get(idx+1);
				idx += 2;
			} else if( words.get(idx).equals("--")) {
				idx++;
				break;
			} else {
				break;
			}
		}
		if( idx >= words.size()) {
			ctx.stderr.println("printf: usage: printf [-v var] format [arguments]");
			return 2;
		}
		String format = words.get(idx++);
		List<String> values = words.subList(idx, words.size());
		failed = false;
		StringBuilder out = new StringBuilder();
		int used = 0;
		do {
			int before = used;
			used = format(format, values, used, out, ctx);
			if( used == before ) {
				// the format takes no arguments: once
				break;
			}
		} while( used < values.size());

		if( var != null ) {
			ctx.setVariable(var, out.toString());
		} else {
			ctx.stdout.print(out);
			ctx.stdout.flush();
		}
		return failed ? 1 : 0;
	}

	/**
	 * Write format once, taking arguments from values at next.
	 * @return the index of the next argument
	 */
	private int format(String format, List<String> values, int next, StringBuilder out, ShellContext ctx) {
		int n = format.length();
		for (int idx = 0; idx < n; idx++) {
			char c = format.charAt(idx);
			if( c == '\\' ) {
				idx = escape(format, idx, out, false);
				continue;
			}
			if( c != '%' ) {
				out.append(c);
				continue;
			}
			if( idx+1 < n && format.charAt(idx+1) == '%' ) {
				out.append('%');
				idx++;
				continue;
			}
			// %[flags][width][.precision]conversion
			int start = idx++;
			StringBuilder spec = new StringBuilder("%");
			while( idx < n && "-+ 0#".indexOf(format.charAt(idx)) >= 0 ) {
				spec.append(format.charAt(idx++));
			}
			if( idx < n && format.charAt(idx) == '*' ) {
				spec.append(number(next < values.size() ? values.get(next++) : "0", ctx));
				idx++;
			} else {
				while( idx < n && Character.isDigit(format.charAt(idx))) {
					spec.append(format.charAt(idx++));
				}
			}
			Integer precision = null;
			if( idx < n && format.charAt(idx) == '.' ) {
				idx++;
				StringBuilder p = new StringBuilder();
				if( idx < n && format.charAt(idx) == '*' ) {
					p.append(number(next < values.size() ? values.get(next++) : "0", ctx));
					idx++;
				} else {
					while( idx < n && Character.isDigit(format.charAt(idx))) {
						p.append(format.charAt(idx++));
					}
				}
				precision = p.length() == 0 ? 0 : Integer.parseInt(p.toString());
			}
			// length modifiers (l, h ...) mean nothing here
			while( idx < n && "hlLjzt".indexOf(format.charAt(idx)) >= 0 ) {
				idx++;
			}
			if( idx >= n ) {
				out.append(format, start, n);
				break;
			}
			char conv = format.charAt(idx);
			String arg = next < values.size() ? values.get(next++) : null;
			out.append(convert(conv, spec.toString(), precision, arg, ctx));
		}
		return next;
	}

	private String convert(char conv, String spec, Integer precision, String arg, ShellContext ctx) {
		String prec = precision == null ? "" : "."+precision;
		switch (conv) {
		case 's':
			return String.format(spec+prec+"s", arg == null ? "" : arg);
		case 'b': {
			StringBuilder b = new StringBuilder();
			String text = arg == null ? "" : arg;
			for (int idx = 0; idx < text.length(); idx++) {
				if( text.charAt(idx) == '\\' ) {
					idx = escape(text, idx, b, true);
				} else {
					b.append(text.charAt(idx));
				}
			}
			return String.format(spec+prec+"s", b);
		}
		case 'q':
			return String.format(spec+"s", quote(arg == null ? "" : arg));
		case 'c':
			return String.format(spec+"s", arg == null || arg.isEmpty() ? "" : arg.substring(0, 1));
		case 'd':
		case 'i': {
			long v = number(arg, ctx);
			String s = String.format(spec.replace("#", "")+"d", v);
			return precision == null ? s : pad(s, precision);
		}
		case 'u':
			return String.format(spec+"d", number(arg, ctx));
		case 'o':
		case 'x':
		case 'X':
			return String.format(spec+conv, number(arg, ctx));
		case 'f': case 'F': case 'e': case 'E': case 'g': case 'G': {
			double d = decimal(arg, ctx);
			return String.format(spec+(precision == null ? "" : prec)+(conv == 'F' ? 'f' : conv), d);
		}
		default:
			ctx.stderr.println("printf: %"+conv+": invalid format character");
			failed = true;
			return "";
		}
	}

	/** %.5d: at least precision digits */
	private static String pad(String s, int precision) {
		boolean neg = s.trim().startsWith("-");
		String digits = s.trim().replaceFirst("^[-+]", "");
		while( digits.length() < precision ) {
			digits = "0"+digits;
		}
		return (neg ? "-" : "")+digits;
	}

	/** a number argument: decimal, 0x hex, 0 octal, or 'c / "c (the character's code) */
	private long number(String arg, ShellContext ctx) {
		if( arg == null || arg.isEmpty()) {
			return 0;
		}
		if( arg.startsWith("'") || arg.startsWith("\"")) {
			return arg.length() > 1 ? arg.codePointAt(1) : 0;
		}
		String s = arg.trim();
		try {
			if( s.startsWith("0x") || s.startsWith("0X")) {
				return Long.parseLong(s.substring(2), 16);
			}
			if( s.length() > 1 && s.startsWith("0") && s.matches("0[0-7]+")) {
				return Long.parseLong(s.substring(1), 8);
			}
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			ctx.stderr.println("printf: "+arg+": invalid number");
			failed = true;
			return 0;
		}
	}

	private double decimal(String arg, ShellContext ctx) {
		if( arg == null || arg.isEmpty()) {
			return 0;
		}
		if( arg.startsWith("'") || arg.startsWith("\"")) {
			return arg.length() > 1 ? arg.codePointAt(1) : 0;
		}
		try {
			return Double.parseDouble(arg.trim());
		} catch (NumberFormatException e) {
			ctx.stderr.println("printf: "+arg+": invalid number");
			failed = true;
			return 0;
		}
	}

	/** %q: quoted so the shell reads it back as the same word */
	static String quote(String s) {
		if( s.isEmpty()) {
			return "''";
		}
		StringBuilder ret = new StringBuilder();
		for(char c : s.toCharArray()) {
			if( Character.isLetterOrDigit(c) || "_-./,:@%+=".indexOf(c) >= 0 ) {
				ret.append(c);
			} else {
				ret.append('\\').append(c);
			}
		}
		return ret.toString();
	}

	/**
	 * The escape at text[idx] (a backslash), appended to out.
	 * @param inArgument %b: \0nnn is octal and \c ends the output
	 * @return the index of its last character
	 */
	private static int escape(String text, int idx, StringBuilder out, boolean inArgument) {
		if( idx+1 >= text.length()) {
			out.append('\\');
			return idx;
		}
		char e = text.charAt(++idx);
		switch (e) {
		case 'n': out.append('\n'); return idx;
		case 't': out.append('\t'); return idx;
		case 'r': out.append('\r'); return idx;
		case 'a': out.append('\u0007'); return idx;
		case 'b': out.append('\b'); return idx;
		case 'f': out.append('\f'); return idx;
		case 'v': out.append('\u000b'); return idx;
		case 'e':
		case 'E': out.append('\u001b'); return idx;
		case '\\': out.append('\\'); return idx;
		case '"': out.append('"'); return idx;
		case '\'': out.append('\''); return idx;
		case 'x': {
			int end = idx+1;
			while( end < text.length() && end-idx-1 < 2 && Character.digit(text.charAt(end), 16) >= 0 ) {
				end++;
			}
			if( end == idx+1 ) {
				out.append("\\x");
				return idx;
			}
			out.append((char) Integer.parseInt(text.substring(idx+1, end), 16));
			return end-1;
		}
		default:
			if( e >= '0' && e <= '7' ) {
				// \nnn in the format, \0nnn in %b
				int start = inArgument && e == '0' ? idx+1 : idx;
				int end = start;
				while( end < text.length() && end-start < 3 && text.charAt(end) >= '0' && text.charAt(end) <= '7' ) {
					end++;
				}
				out.append((char) (end == start ? 0 : Integer.parseInt(text.substring(start, end), 8)));
				return end-1;
			}
			out.append('\\').append(e);
			return idx;
		}
	}
}
