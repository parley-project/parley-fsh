package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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

	private static final java.util.regex.Pattern ELEMENT = java.util.regex.Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[(.+)\\]");

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
			ctx.error("printf: usage: printf [-v var] format [arguments]");
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

		java.util.regex.Matcher element = var == null ? null : ELEMENT.matcher(var);
		if( element != null && element.matches()) {
			// -v 'a[1]', -v 'm[key]'
			Object cur = ctx.getVariable(element.group(1));
			Object key = cur instanceof java.util.Map<?,?> ? element.group(2)
					: (Object) us.bringardner.fsh.expand.Arithmetic.evaluate(element.group(2), ctx).intValue();
			ctx.setVariable(element.group(1), key, out.toString());
		} else if( var != null ) {
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
				// %*s: the width is an argument; 0 is none (Java read %0s as the 0 flag), and a
				// negative one left-justifies
				long width = number(next < values.size() ? values.get(next++) : "0", ctx);
				if( width < 0 ) {
					spec.append('-');
					width = -width;
				}
				if( width > 0 ) {
					spec.append(width);
				}
				idx++;
			} else {
				while( idx < n && Character.isDigit(format.charAt(idx))) {
					spec.append(format.charAt(idx++));
				}
			}
			int close = idx < n && format.charAt(idx) == '(' ? format.indexOf(")T", idx) : -1;
			if( close > 0 ) {
				// %(strftime format)T: a time, the argument in seconds since 1970 (none or -1: now)
				String when = next < values.size() ? values.get(next++) : null;
				String text = strftime(format.substring(idx+1, close), when, ctx);
				out.append(String.format(spec.toString()+"s", text));
				idx = close+1;
				continue;
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
		case 'g': case 'G':
			return formatG(spec, precision, decimal(arg, ctx), conv == 'G');
		case 'f': case 'F': case 'e': case 'E': {
			double d = decimal(arg, ctx);
			return String.format(spec+(precision == null ? "" : prec)+(conv == 'F' ? 'f' : conv), d);
		}
		default:
			ctx.error("printf: %"+conv+": invalid format character");
			failed = true;
			return "";
		}
	}

	/**
	 * A time as strftime formats it (the time zone is $TZ, or the system's).
	 */
	static String strftime(String fmt, String when, ShellContext ctx) {
		long seconds = System.currentTimeMillis()/1000;
		if( when != null && !when.isBlank()) {
			try {
				long v = Long.parseLong(when.trim());
				if( v >= 0 ) {
					seconds = v;
				}
			} catch (NumberFormatException e) {
			}
		}
		Object tz = ctx.getVariable("TZ");
		java.time.ZoneId zone;
		try {
			zone = tz == null || tz.toString().isBlank() ? java.time.ZoneId.systemDefault() : java.time.ZoneId.of(tz.toString());
		} catch (java.time.DateTimeException e) {
			zone = java.time.ZoneOffset.UTC;
		}
		java.time.ZonedDateTime t = java.time.Instant.ofEpochSecond(seconds).atZone(zone);
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < fmt.length(); i++) {
			char c = fmt.charAt(i);
			if( c != '%' || i+1 >= fmt.length()) {
				ret.append(c);
				continue;
			}
			char d = fmt.charAt(++i);
			switch (d) {
			case 'Y': ret.append(t.getYear()); break;
			case 'C': ret.append(String.format("%02d", t.getYear()/100)); break;
			case 'y': ret.append(String.format("%02d", t.getYear()%100)); break;
			case 'm': ret.append(String.format("%02d", t.getMonthValue())); break;
			case 'd': ret.append(String.format("%02d", t.getDayOfMonth())); break;
			case 'e': ret.append(String.format("%2d", t.getDayOfMonth())); break;
			case 'j': ret.append(String.format("%03d", t.getDayOfYear())); break;
			case 'H': ret.append(String.format("%02d", t.getHour())); break;
			case 'k': ret.append(String.format("%2d", t.getHour())); break;
			case 'I': ret.append(String.format("%02d", (t.getHour()+11)%12+1)); break;
			case 'l': ret.append(String.format("%2d", (t.getHour()+11)%12+1)); break;
			case 'M': ret.append(String.format("%02d", t.getMinute())); break;
			case 'S': ret.append(String.format("%02d", t.getSecond())); break;
			case 'p': ret.append(t.getHour() < 12 ? "AM" : "PM"); break;
			case 'a': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US))); break;
			case 'A': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US))); break;
			case 'b':
			case 'h': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.US))); break;
			case 'B': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", java.util.Locale.US))); break;
			case 'u': ret.append(t.getDayOfWeek().getValue()); break;
			case 'w': ret.append(t.getDayOfWeek().getValue()%7); break;
			case 'Z': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("zzz", java.util.Locale.US))); break;
			case 'z': ret.append(t.format(java.time.format.DateTimeFormatter.ofPattern("xx"))); break;
			case 's': ret.append(seconds); break;
			case 'F': ret.append(strftime("%Y-%m-%d", ""+seconds, ctx)); break;
			case 'T': ret.append(strftime("%H:%M:%S", ""+seconds, ctx)); break;
			case 'D': ret.append(strftime("%m/%d/%y", ""+seconds, ctx)); break;
			case 'R': ret.append(strftime("%H:%M", ""+seconds, ctx)); break;
			case 'n': ret.append('\n'); break;
			case 't': ret.append('\t'); break;
			case '%': ret.append('%'); break;
			default: ret.append('%').append(d);
			}
		}
		return ret.toString();
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
			ctx.error("printf: "+arg+": invalid number");
			failed = true;
			// as in bash, the number it starts with (3.7 is 3)
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[-+]?\\d+").matcher(s);
			return m.find() ? Long.parseLong(m.group().replace("+", "")) : 0;
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
			ctx.error("printf: "+arg+": invalid number");
			failed = true;
			return 0;
		}
	}

	/**
	 * %g as C does it: %e when the exponent is below -4 or not below the precision, %f otherwise,
	 * with no trailing zeros (unless #). Java's %g keeps them and never drops the exponent form.
	 */
	static String formatG(String spec, Integer precision, double d, boolean upper) {
		String flags = "";
		int at = 1;
		while( at < spec.length() && "-+ #0".indexOf(spec.charAt(at)) >= 0 ) {
			flags += spec.charAt(at++);
		}
		int width = at < spec.length() ? Integer.parseInt(spec.substring(at)) : 0;
		int p = precision == null ? 6 : Math.max(precision, 1);
		String body;
		if( Double.isNaN(d) || Double.isInfinite(d)) {
			body = Double.isNaN(d) ? "nan" : d > 0 ? "inf" : "-inf";
		} else {
			String sign = flags.indexOf('+') >= 0 ? "+" : flags.indexOf(' ') >= 0 ? " " : "";
			// the exponent after rounding to p digits
			String e = String.format("%."+(p-1)+"e", d);
			int x = Integer.parseInt(e.substring(e.indexOf('e')+1));
			body = x < -4 || x >= p ? e : String.format("%."+(p-1-x)+"f", d);
			if( flags.indexOf('#') < 0 ) {
				int ePos = body.indexOf('e');
				String mantissa = ePos < 0 ? body : body.substring(0, ePos);
				String exp = ePos < 0 ? "" : body.substring(ePos);
				if( mantissa.indexOf('.') >= 0 ) {
					mantissa = mantissa.replaceAll("0+$", "").replaceAll("\\.$", "");
				}
				body = mantissa+exp;
			}
			if( d >= 0 || body.charAt(0) != '-' ) {
				body = sign+body;
			}
		}
		if( upper ) {
			body = body.toUpperCase();
		}
		if( body.length() < width ) {
			String pad = " ".repeat(width-body.length());
			if( flags.indexOf('-') >= 0 ) {
				body = body+pad;
			} else if( flags.indexOf('0') >= 0 && Character.isDigit(body.charAt(body.length()-1))) {
				int digits = body.startsWith("-") || body.startsWith("+") || body.startsWith(" ") ? 1 : 0;
				body = body.substring(0, digits)+pad.replace(' ', '0')+body.substring(digits);
			} else {
				body = pad+body;
			}
		}
		return body;
	}

	/** %q: quoted so the shell reads it back as the same word */
	static String quote(String s) {
		if( s.isEmpty()) {
			return "''";
		}
		if( s.chars().anyMatch(c -> c < ' ' || c == 0x7f)) {
			// control characters: $'...', as bash does
			StringBuilder ret = new StringBuilder("$'");
			for(char c : s.toCharArray()) {
				switch (c) {
				case '\\': ret.append("\\\\"); break;
				case '\'': ret.append("\\'"); break;
				case '\t': ret.append("\\t"); break;
				case '\n': ret.append("\\n"); break;
				case '\r': ret.append("\\r"); break;
				case 7: ret.append("\\a"); break;
				case '\b': ret.append("\\b"); break;
				case '\f': ret.append("\\f"); break;
				case 11: ret.append("\\v"); break;
				case 27: ret.append("\\E"); break;
				default:
					if( c < ' ' || c == 0x7f ) {
						ret.append(String.format("\\%03o", (int) c));
					} else {
						ret.append(c);
					}
				}
			}
			return ret.append('\'').toString();
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
