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
	/** standard output when printing there (not -v): what was formatted so far goes out before an error */
	private java.io.PrintStream direct;
	private StringBuilder current;
	/** \c in %b: no more output at all */
	private boolean stop;

	/** an error: said at once (the output goes out when printf ends, as bash's buffered output) */
	private void error(ShellContext ctx, String message) {
		ctx.error(message);
		failed = true;
	}

	/** what was formatted so far goes out now (bash flushes before %n) */
	private void flush() {
		if( direct != null && current != null && current.length() > 0 ) {
			us.bringardner.fsh.ByteText.finishInPlace(current);
			direct.print(current);
			direct.flush();
			current.setLength(0);
		}
	}

	static final String USAGE = "printf: usage: printf [-v var] format [arguments]";

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
		while( idx < words.size() && words.get(idx).startsWith("-") && words.get(idx).length() > 1 ) {
			String w = words.get(idx);
			if( w.equals("--")) {
				idx++;
				break;
			} else if( w.startsWith("-v")) {
				// -v var, -vvar
				if( w.length() > 2 ) {
					var = w.substring(2);
					idx++;
				} else if( idx+1 < words.size()) {
					var = words.get(idx+1);
					idx += 2;
				} else {
					ctx.error("printf: -v: option requires an argument");
					ctx.stderr.println(USAGE);
					return 2;
				}
				if( !us.bringardner.fsh.ShellContext.validReferenceName(var)) {
					ctx.error("printf: `"+var+"': not a valid identifier");
					return 2;
				}
			} else {
				ctx.error("printf: "+w.substring(0, 2)+": invalid option");
				ctx.stderr.println(USAGE);
				return 2;
			}
		}
		if( idx >= words.size()) {
			ctx.stderr.println(USAGE);
			return 2;
		}
		String format = words.get(idx++);
		List<String> values = words.subList(idx, words.size());
		failed = false;
		stop = false;
		StringBuilder out = new StringBuilder();
		current = out;
		direct = var == null ? ctx.stdout : null;
		int used = 0;
		do {
			int before = used;
			used = format(format, values, used, out, ctx);
			if( used == before || stop ) {
				// the format takes no arguments: once
				break;
			}
		} while( used < values.size());
		us.bringardner.fsh.ByteText.finishInPlace(out);

		java.util.regex.Matcher element = var == null ? null : ELEMENT.matcher(var);
		if( element != null && element.matches()) {
			// -v 'a[1]', -v 'm[key]'
			Object cur = ctx.getVariable(element.group(1));
			if( !(cur instanceof java.util.Map<?,?>) && (element.group(2).equals("@") || element.group(2).equals("*"))) {
				// (no element @ of an indexed array)
				ctx.error(var+": bad array subscript");
				return 1;
			}
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
				idx = escapeIn(ctx, format, idx, out, false);
				if( stop ) {
					return next;
				}
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
				String w = next < values.size() ? values.get(next++) : "0";
				long width = number(w, ctx);
				if( width > Integer.MAX_VALUE || width < -Integer.MAX_VALUE ) {
					// too wide: said, and not used
					error(ctx, "printf: "+w+": Result too large");
					width = 0;
				}
				if( width < 0 ) {
					spec.append('-');
					width = -width;
				}
				if( width > 0 ) {
					spec.append(width);
				}
				idx++;
			} else {
				int digits = idx;
				while( idx < n && Character.isDigit(format.charAt(idx))) {
					idx++;
				}
				String w = format.substring(digits, idx);
				if( w.length() > 9 && Long.parseLong(w.length() > 18 ? "9999999999" : w) > Integer.MAX_VALUE ) {
					error(ctx, "printf: "+w+": Result too large");
				} else {
					spec.append(w);
				}
			}
			Integer precision = null;
			String tooLarge = null;
			if( idx < n && format.charAt(idx) == '.' ) {
				idx++;
				StringBuilder p = new StringBuilder();
				boolean ignore = false;
				if( idx < n && format.charAt(idx) == '*' ) {
					String a = next < values.size() ? values.get(next++) : "0";
					long v = number(a, ctx);
					if( v > Integer.MAX_VALUE ) {
						error(ctx, "printf: "+a+": Result too large");
						ignore = true;
					} else if( v < 0 ) {
						// (a negative precision is none, as C's)
						ignore = true;
					}
					p.append(ignore ? "0" : String.valueOf(v));
					idx++;
				} else {
					while( idx < n && Character.isDigit(format.charAt(idx))) {
						p.append(format.charAt(idx++));
					}
				}
				long pv = p.length() == 0 || p.length() > 10 ? (p.length() == 0 ? 0 : Long.MAX_VALUE) : Long.parseLong(p.toString());
				// (one written in the format that is too large is no precision; said for %b and %Q, as
				// bash's)
				if( !ignore && pv > Integer.MAX_VALUE ) {
					tooLarge = p.toString();
				}
				precision = ignore ? null : pv > Integer.MAX_VALUE ? null : (int) pv;
			}
			if( idx < n && format.charAt(idx) == '(' ) {
				// %(strftime format)T: a time, the argument in seconds since 1970 (none or -1: now, -2:
				// when the shell started)
				// (the ) that matches it: the format may have parentheses of its own)
				int close = -1;
				for (int k = idx, depth = 0; k < n; k++) {
					if( format.charAt(k) == '(' ) {
						depth++;
					} else if( format.charAt(k) == ')' && --depth == 0 ) {
						close = k;
						break;
					}
				}
				if( close < 0 || close+1 >= n || format.charAt(close+1) != 'T' ) {
					char bad = close < 0 || close+1 >= n ? '(' : format.charAt(close+1);
					ctx.error("printf: warning: `"+bad+"': invalid time format specification");
					int end = close < 0 ? n : Math.min(n, close+2);
					out.append(format, start, end);
					idx = end-1;
					continue;
				}
				String when = next < values.size() ? values.get(next++) : null;
				String fmt = format.substring(idx+1, close);
				String text = strftime(fmt.isEmpty() ? "%X" : fmt, when, ctx);
				if( precision != null && precision < text.length()) {
					text = text.substring(0, precision);
				}
				out.append(String.format(sane(spec.toString(), 's')+"s", text));
				idx = close+1;
				continue;
			}
			// length modifiers (l, h ...) mean nothing here
			while( idx < n && "hlLjzt".indexOf(format.charAt(idx)) >= 0 ) {
				idx++;
			}
			if( idx >= n ) {
				error(ctx, "printf: `"+format.substring(start, n)+"': missing format character");
				stop = true;
				return next;
			}
			char conv = format.charAt(idx);
			if( tooLarge != null && (conv == 'b' || conv == 'Q')) {
				error(ctx, "printf: "+tooLarge+": Result too large");
			}
			String arg = next < values.size() ? values.get(next++) : null;
			if( conv == 'n' ) {
				// %n: the number of characters written so far, into the variable named
				if( arg != null ) {
					if( !arg.matches("[A-Za-z_][A-Za-z_0-9]*")) {
						flush();
						error(ctx, "printf: `"+arg+"': not a valid identifier");
					} else {
						ctx.setVariable(arg, String.valueOf(out.length()));
					}
				}
				continue;
			}
			alternate = spec.indexOf("#") >= 0 && (conv == 'q' || conv == 'Q');
			if( conv == 'S' || conv == 'C' ) {
				// %S %C: %ls %lc
				conv = Character.toLowerCase(conv);
			}
			out.append(convert(conv, "diuoxX".indexOf(conv) >= 0 ? spec.toString() : sane(spec.toString(), conv), precision, arg, ctx));
			if( stop ) {
				return next;
			}
		}
		return next;
	}

	/** the spec without what Java's Formatter refuses (bash ignores it): 0 # + space on strings, 0 or - with no width */
	private static String sane(String spec, char conv) {
		String flags = spec.replaceFirst("^%([-+ 0#]*).*$", "$1");
		String width = spec.substring(1+flags.length());
		if( "sbqQc".indexOf(conv) >= 0 ) {
			flags = flags.replaceAll("[0+ #]", "");
		}
		if( width.isEmpty()) {
			flags = flags.replaceAll("[-0]", "");
		} else if( flags.contains("-")) {
			flags = flags.replace("0", "");
		}
		if( flags.contains("+")) {
			flags = flags.replace(" ", "");
		}
		// (once each)
		StringBuilder f = new StringBuilder();
		for(char c : flags.toCharArray()) {
			if( f.indexOf(String.valueOf(c)) < 0 ) {
				f.append(c);
			}
		}
		return "%"+f+width;
	}

	/** %#q: 'quoted' (bash's alternative form) */
	private boolean alternate;

	private String convert(char conv, String spec, Integer precision, String arg, ShellContext ctx) {
		String prec = precision == null ? "" : "."+precision;
		switch (conv) {
		case 's':
		case 'S':
			return String.format(spec+prec+"s", arg == null ? "" : arg);
		case 'b': {
			StringBuilder b = new StringBuilder();
			String text = arg == null ? "" : arg;
			for (int idx = 0; idx < text.length(); idx++) {
				if( text.charAt(idx) == '\\' ) {
					idx = escapeIn(ctx, text, idx, b, true);
					if( stop ) {
						// \c: what is before it, and nothing more
						return b.toString();
					}
				} else {
					b.append(text.charAt(idx));
				}
			}
			return String.format(spec+prec+"s", b);
		}
		case 'q': {
			// %.Nq: the quoted text cut to N (bash's); %.NQ: the argument cut to N, then quoted
			String q = alternate ? singleQuoted(arg == null ? "" : arg) : quote(arg == null ? "" : arg);
			if( precision != null && precision < q.length()) {
				q = q.substring(0, precision);
			}
			return String.format(spec+"s", q);
		}
		case 'Q': {
			String a = arg == null ? "" : arg;
			if( precision != null && precision < a.length()) {
				a = a.substring(0, precision);
			}
			return String.format(spec+"s", alternate ? singleQuoted(a) : quote(a));
		}
		case 'c':
		case 'C':
			// (no argument: a NUL, as bash's)
			return String.format(spec+"s", arg == null || arg.isEmpty() ? "\0" : arg.substring(0, 1));
		case 'd':
		case 'i':
		case 'u':
		case 'o':
		case 'x':
		case 'X':
			return integer(conv, spec, precision, number(arg, ctx));
		case 'g': case 'G':
			return formatG(spec, precision, decimal(arg, ctx), conv == 'G');
		case 'f': case 'F': case 'e': case 'E': {
			double d = decimal(arg, ctx);
			return String.format(spec+(precision == null ? "" : prec)+(conv == 'F' ? 'f' : conv), d);
		}
		default:
			error(ctx, "printf: `"+conv+"': invalid format character");
			stop = true;
			return "";
		}
	}

	/**
	 * A time as strftime formats it (the time zone is $TZ, or the system's).
	 */
	/** $TZ as a zone: a zone name, or a POSIX one (EST5EDT, EST5EDT,M3.2.0/2,M11.1.0/2, UTC+3) */
	static java.time.ZoneId zone(ShellContext ctx) {
		Object tz = ctx.getVariable("TZ");
		if( tz == null || tz.toString().isBlank()) {
			return java.time.ZoneId.systemDefault();
		}
		String t = tz.toString();
		if( t.startsWith(":")) {
			t = t.substring(1);
		}
		try {
			return java.time.ZoneId.of(t);
		} catch (java.time.DateTimeException e) {
		}
		String head = t.contains(",") ? t.substring(0, t.indexOf(',')) : t;
		try {
			return java.time.ZoneId.of(head);
		} catch (java.time.DateTimeException e) {
		}
		// NAME[+-]hours: hours west of Greenwich
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[A-Za-z]{3,}([-+]?)(\\d{1,2})(?::(\\d{2}))?").matcher(head);
		if( m.find()) {
			int seconds = Integer.parseInt(m.group(2))*3600+(m.group(3) == null ? 0 : Integer.parseInt(m.group(3))*60);
			return java.time.ZoneOffset.ofTotalSeconds(m.group(1).equals("-") ? seconds : -seconds);
		}
		return java.time.ZoneOffset.UTC;
	}

	static String strftime(String fmt, String when, ShellContext ctx) {
		long seconds = System.currentTimeMillis()/1000;
		if( when != null && !when.isBlank()) {
			try {
				long v = Long.parseLong(when.trim());
				if( v == -2 ) {
					// when the shell started
					seconds = System.currentTimeMillis()/1000-ctx.console.seconds();
				} else if( v != -1 ) {
					seconds = v;
				}
			} catch (NumberFormatException e) {
			}
		}
		java.time.ZoneId zone = zone(ctx);
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
			case 'r': ret.append(strftime("%I:%M:%S %p", ""+seconds, ctx)); break;
			case 'x': ret.append(strftime("%m/%d/%y", ""+seconds, ctx)); break;
			case 'X': ret.append(strftime("%H:%M:%S", ""+seconds, ctx)); break;
			case 'c': ret.append(strftime("%a %b %e %H:%M:%S %Y", ""+seconds, ctx)); break;
			case 'n': ret.append('\n'); break;
			case 't': ret.append('\t'); break;
			case '%': ret.append('%'); break;
			default: ret.append('%').append(d);
			}
		}
		return ret.toString();
	}

	/** %d %u %o %x %X as C formats them (a precision ignores the 0 flag; o x X u have no sign) */
	private static String integer(char conv, String spec, Integer precision, long v) {
		String flags = spec.replaceFirst("^%([-+ 0#]*).*$", "$1");
		String w = spec.substring(1+flags.length());
		int width = w.isEmpty() ? 0 : Integer.parseInt(w);
		boolean signed = conv == 'd' || conv == 'i';
		String digits;
		String sign = "";
		if( signed ) {
			digits = v < 0 ? (v == Long.MIN_VALUE ? "9223372036854775808" : String.valueOf(-v)) : String.valueOf(v);
			sign = v < 0 ? "-" : flags.contains("+") ? "+" : flags.contains(" ") ? " " : "";
		} else if( conv == 'o' ) {
			digits = Long.toOctalString(v);
		} else if( conv == 'u' ) {
			digits = Long.toUnsignedString(v);
		} else {
			digits = Long.toHexString(v);
			if( conv == 'X' ) {
				digits = digits.toUpperCase();
			}
		}
		if( precision != null ) {
			if( precision == 0 && v == 0 ) {
				digits = "";
			}
			while( digits.length() < precision ) {
				digits = "0"+digits;
			}
		}
		if( flags.contains("#") && v != 0 ) {
			if( conv == 'x' ) {
				sign = "0x";
			} else if( conv == 'X' ) {
				sign = "0X";
			} else if( conv == 'o' && !digits.startsWith("0")) {
				digits = "0"+digits;
			}
		}
		int fill = width-sign.length()-digits.length();
		if( fill <= 0 ) {
			return sign+digits;
		}
		if( flags.contains("-")) {
			return sign+digits+" ".repeat(fill);
		}
		if( flags.contains("0") && precision == null ) {
			return sign+"0".repeat(fill)+digits;
		}
		return " ".repeat(fill)+sign+digits;
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
		if( arg == null ) {
			return 0;
		}
		if( arg.isEmpty()) {
			// (an argument that is there and empty is not a number, as in bash)
			error(ctx, "printf: : invalid number");
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
			if( s.matches("[-+]?[0-9]+")) {
				try {
					return Long.parseLong(s);
				} catch (NumberFormatException big) {
					// too big: the largest (or smallest) there is, and said
					error(ctx, "printf: "+arg+": Result too large");
					return s.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
				}
			}
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			error(ctx, "printf: "+arg+": invalid number");
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
			error(ctx, "printf: "+arg+": invalid number");
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

	/** %#q: in single quotes */
	private static String singleQuoted(String s) {
		return "'"+s.replace("'", "'\\''")+"'";
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
	/** escape found \x with no digits (bash says so) */
	private boolean missingHex;

	/** escape, saying what bash says about a \x with no digits, and \c in %b stopping the output */
	private int escapeIn(ShellContext ctx, String text, int idx, StringBuilder out, boolean inArgument) {
		if( inArgument && idx+1 < text.length() && text.charAt(idx+1) == 'c' ) {
			stop = true;
			return idx+1;
		}
		missingHex = false;
		int ret = escape(text, idx, out, inArgument);
		if( missingHex ) {
			error(ctx, "printf: missing hex digit for \\x");
		}
		return ret;
	}

	private int escape(String text, int idx, StringBuilder out, boolean inArgument) {
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
		case '"':
		case '\'':
		case '?':
			// in the format; %b keeps the backslash (as bash's)
			if( inArgument ) {
				out.append('\\');
			}
			out.append(e);
			return idx;
		case 'x': {
			int end = idx+1;
			while( end < text.length() && end-idx-1 < 2 && Character.digit(text.charAt(end), 16) >= 0 ) {
				end++;
			}
			if( end == idx+1 ) {
				missingHex = true;
				out.append("\\x");
				return idx;
			}
			// a byte (of UTF-8 text with the ones next to it)
			out.append(us.bringardner.fsh.ByteText.mark(Integer.parseInt(text.substring(idx+1, end), 16)));
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
				out.append(us.bringardner.fsh.ByteText.mark(end == start ? 0 : Integer.parseInt(text.substring(start, end), 8)));
				return end-1;
			}
			out.append('\\').append(e);
			return idx;
		}
	}
}
