package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * sort, modeled on the GNU sort in the C locale: the lines are compared by character code, not by
 * the locale's collation.
 */
public class Sort extends ShellCommand{
	static String name = "sort";
	static String help = "sort [OPTION]... [FILE ...]\n"
			+ "\tWrite the sorted lines of all the FILEs (standard input with no FILE or -).\n"
			+ "\t-b, --ignore-leading-blanks   ignore leading blanks in the keys\n"
			+ "\t-d, --dictionary-order        only blanks and letters and digits count\n"
			+ "\t-f, --ignore-case             fold lower case to upper case\n"
			+ "\t-g, --general-numeric-sort    compare as floating point numbers (1e3, -.5)\n"
			+ "\t-h, --human-numeric-sort      compare numbers with a suffix (2K 1G)\n"
			+ "\t-i, --ignore-nonprinting      only printable characters count\n"
			+ "\t-M, --month-sort              compare (unknown) < JAN < ... < DEC\n"
			+ "\t-n, --numeric-sort            compare as numbers\n"
			+ "\t-r, --reverse                 reverse the result\n"
			+ "\t-V, --version-sort            natural sort of version numbers within the text\n"
			+ "\t-k, --key=KEYDEF              sort on a key: F[.C][OPTS][,F[.C][OPTS]] (fields and characters count from 1)\n"
			+ "\t-t, --field-separator=SEP     the field separator (default: the change from non-blank to blank)\n"
			+ "\t-s, --stable                  don't compare the whole line when keys are equal\n"
			+ "\t-u, --unique                  print only the first of lines that compare equal\n"
			+ "\t-c, --check                   check that the input is sorted; print nothing\n"
			+ "\t-o, --output=FILE             write to FILE (it may be an input) instead of standard output\n"
			+ "\t-m, --merge                   accepted (the input is sorted anyway)\n"
			+ "\tAn ordering option in a KEYDEF replaces the global ones for that key.";

	private static final String USAGE = "usage: sort [-bdfghiMnrsuVcm] [-k keydef] [-t char] [-o file] [file ...]";

	private static final String ORDERS = "bdfghiMnRrV";

	/** the ordering options of a key (or of the whole line) */
	private static class Mode {
		boolean blanks, dict, fold, general, human, nonprint, month, numeric, reverse, version;

		boolean any() {
			return blanks || dict || fold || general || human || nonprint || month || numeric || reverse || version;
		}

		boolean set(char c) {
			switch (c) {
			case 'b': blanks = true; break;
			case 'd': dict = true; break;
			case 'f': fold = true; break;
			case 'g': general = true; break;
			case 'h': human = true; break;
			case 'i': nonprint = true; break;
			case 'M': month = true; break;
			case 'n': numeric = true; break;
			case 'R': break;
			case 'r': reverse = true; break;
			case 'V': version = true; break;
			default: return false;
			}
			return true;
		}

		Mode copy() {
			Mode m = new Mode();
			m.blanks = blanks;
			m.dict = dict;
			m.fold = fold;
			m.general = general;
			m.human = human;
			m.nonprint = nonprint;
			m.month = month;
			m.numeric = numeric;
			m.reverse = reverse;
			m.version = version;
			return m;
		}
	}

	private static class Key {
		int startField = 1, startChar, endField, endChar;
		Mode mode = new Mode();
	}

	public Sort() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Mode global = new Mode();
		List<Key> keys = new ArrayList<>();
		List<String> files = new ArrayList<>();
		Character sep = null;
		boolean stable = false, unique = false, check = false;
		String output = null;
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
				continue;
			}
			if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				int eq = arg.indexOf('=');
				String key = eq > 0 ? arg.substring(0, eq) : arg;
				String val = eq > 0 ? arg.substring(eq+1) : null;
				char opt = 0;
				switch (key) {
				case "--ignore-leading-blanks": global.blanks = true; break;
				case "--dictionary-order": global.dict = true; break;
				case "--ignore-case": global.fold = true; break;
				case "--general-numeric-sort": global.general = true; break;
				case "--human-numeric-sort": global.human = true; break;
				case "--ignore-nonprinting": global.nonprint = true; break;
				case "--month-sort": global.month = true; break;
				case "--numeric-sort": global.numeric = true; break;
				case "--reverse": global.reverse = true; break;
				case "--version-sort": global.version = true; break;
				case "--stable": stable = true; break;
				case "--unique": unique = true; break;
				case "--check": check = true; break;
				case "--merge": break;
				case "--key": opt = 'k'; break;
				case "--field-separator": opt = 't'; break;
				case "--output": opt = 'o'; break;
				default:
					ctx.stderr.println("sort: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 2;
				}
				if( opt != 0 ) {
					if( val == null ) {
						if( idx+1 >= args.length ) {
							ctx.stderr.println("sort: option '"+key+"' requires an argument");
							return 2;
						}
						val = ""+args[++idx].getValue(ctx);
					}
					int rc = value(ctx, opt, val, keys, files);
					if( rc != 0 ) {
						return rc;
					}
					if( opt == 't' ) {
						sep = separator(val);
					} else if( opt == 'o' ) {
						output = val;
					}
				}
			} else {
				String cluster = arg.substring(1);
				for (int c = 0; c < cluster.length(); c++) {
					char ch = cluster.charAt(c);
					if( ORDERS.indexOf(ch) >= 0 ) {
						global.set(ch);
					} else if( ch == 's' ) {
						stable = true;
					} else if( ch == 'u' ) {
						unique = true;
					} else if( ch == 'c' || ch == 'C' ) {
						check = true;
					} else if( ch == 'm' || ch == 'z' ) {
						// merge: the input is sorted in full anyway
					} else if( ch == 'k' || ch == 't' || ch == 'o' || ch == 'S' || ch == 'T' ) {
						String val;
						if( c+1 < cluster.length()) {
							val = cluster.substring(c+1);
						} else if( idx+1 < args.length ) {
							val = ""+args[++idx].getValue(ctx);
						} else {
							ctx.stderr.println("sort: option requires an argument -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 2;
						}
						int rc = value(ctx, ch, val, keys, files);
						if( rc != 0 ) {
							return rc;
						}
						if( ch == 't' ) {
							sep = separator(val);
						} else if( ch == 'o' ) {
							output = val;
						}
						break;
					} else {
						ctx.stderr.println("sort: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 2;
					}
				}
			}
		}

		if( keys.isEmpty()) {
			Key whole = new Key();
			whole.mode = global.copy();
			keys.add(whole);
		}
		for (Key k : keys) {
			if( !k.mode.any()) {
				k.mode = global.copy();
			}
		}

		List<String> lines = new ArrayList<>();
		int ret = TextFiles.forEach(ctx, "sort", files, (label, file, in) -> {
			byte [] line;
			java.io.InputStream bin = new java.io.BufferedInputStream(in);
			while( (line = TextFiles.line(bin)) != null ) {
				lines.add(TextFiles.chomp(line));
			}
		});
		if( ret != 0 ) {
			return 2;
		}

		final Character separator = sep;
		final boolean whole = !stable && !unique;
		final boolean reverseAll = global.reverse;
		Comparator<String> keyCompare = (a, b) -> {
			for (Key k : keys) {
				int r = compareKey(extract(a, k, separator), extract(b, k, separator), k.mode);
				if( r != 0 ) {
					return k.mode.reverse ? -r : r;
				}
			}
			return 0;
		};
		Comparator<String> full = (a, b) -> {
			int r = keyCompare.compare(a, b);
			if( r != 0 || !whole ) {
				return r;
			}
			r = a.compareTo(b);
			return reverseAll ? -r : r;
		};

		if( check ) {
			for (int i = 1; i < lines.size(); i++) {
				int r = (unique ? keyCompare : full).compare(lines.get(i-1), lines.get(i));
				if( r > 0 || (unique && r == 0)) {
					ctx.stderr.println("sort: "+(files.isEmpty() ? "-" : files.get(0))+":"+(i+1)+": disorder: "+lines.get(i));
					return 1;
				}
			}
			return 0;
		}

		Collections.sort(lines, full);
		StringBuilder out = new StringBuilder();
		String prev = null;
		for (String line : lines) {
			if( unique && prev != null && keyCompare.compare(prev, line) == 0 ) {
				continue;
			}
			out.append(line).append('\n');
			prev = line;
		}
		if( output != null ) {
			try (OutputStream os = ctx.console.createFileSource(output).getOutputStream()) {
				os.write(out.toString().getBytes(StandardCharsets.UTF_8));
			}
		} else {
			ctx.stdout.print(out);
			ctx.stdout.flush();
		}
		return 0;
	}

	private static Character separator(String val) {
		if( val.equals("\\t")) {
			return '\t';
		}
		if( val.equals("\\0")) {
			return (char) 0;
		}
		return val.isEmpty() ? null : val.charAt(0);
	}

	/** the options that take a value; k adds a key */
	private int value(ShellContext ctx, char opt, String val, List<Key> keys, List<String> files) {
		if( opt == 't' ) {
			if( val.length() != 1 && !val.equals("\\t") && !val.equals("\\0")) {
				ctx.stderr.println("sort: multi-character tab '"+val+"'");
				return 2;
			}
		} else if( opt == 'k' ) {
			Key key = parseKey(val);
			if( key == null ) {
				ctx.stderr.println("sort: invalid key '"+val+"'");
				return 2;
			}
			keys.add(key);
		}
		return 0;
	}

	private static final Pattern KEYDEF = Pattern.compile("(\\d+)(?:\\.(\\d+))?([bdfghiMnRrV]*)(?:,(\\d+)(?:\\.(\\d+))?([bdfghiMnRrV]*))?");

	private static Key parseKey(String def) {
		Matcher m = KEYDEF.matcher(def);
		if( !m.matches()) {
			return null;
		}
		Key k = new Key();
		k.startField = Integer.parseInt(m.group(1));
		k.startChar = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
		if( k.startField < 1 ) {
			return null;
		}
		for (char c : (m.group(3)+(m.group(6) == null ? "" : m.group(6))).toCharArray()) {
			k.mode.set(c);
		}
		if( m.group(4) != null ) {
			k.endField = Integer.parseInt(m.group(4));
			k.endChar = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
		}
		return k;
	}

	/** the [start, end) of each field of a line */
	private static List<int[]> fields(String line, Character sep) {
		List<int[]> ret = new ArrayList<>();
		int n = line.length();
		int pos = 0;
		if( sep != null ) {
			while( true ) {
				int end = line.indexOf(sep, pos);
				if( end < 0 ) {
					ret.add(new int[] {pos, n});
					break;
				}
				ret.add(new int[] {pos, end});
				pos = end+1;
			}
			return ret;
		}
		while( true ) {
			int start = pos;
			while( pos < n && isBlank(line.charAt(pos))) {
				pos++;
			}
			while( pos < n && !isBlank(line.charAt(pos))) {
				pos++;
			}
			ret.add(new int[] {start, pos});
			if( pos >= n ) {
				break;
			}
		}
		return ret;
	}

	private static boolean isBlank(char c) {
		return c == ' ' || c == '\t';
	}

	private static String extract(String line, Key k, Character sep) {
		if( k.startField == 1 && k.startChar == 0 && k.endField == 0 && !k.mode.blanks ) {
			return line;
		}
		List<int[]> f = fields(line, sep);
		int n = line.length();
		int start;
		if( k.startField > f.size()) {
			return "";
		}
		start = f.get(k.startField-1)[0];
		if( k.mode.blanks ) {
			while( start < n && isBlank(line.charAt(start))) {
				start++;
			}
		}
		if( k.startChar > 0 ) {
			start = Math.min(n, start+k.startChar-1);
		}
		int end = n;
		if( k.endField > 0 ) {
			if( k.endField <= f.size()) {
				int[] fe = f.get(k.endField-1);
				end = k.endChar > 0 ? Math.min(fe[1], fe[0]+k.endChar) : fe[1];
			}
		}
		return end <= start ? "" : line.substring(start, end);
	}

	private static String clean(String s, Mode m) {
		if( m.blanks ) {
			int i = 0;
			while( i < s.length() && isBlank(s.charAt(i))) {
				i++;
			}
			s = s.substring(i);
		}
		if( m.dict || m.nonprint ) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				boolean keep = m.dict ? (Character.isLetterOrDigit(c) || isBlank(c)) : (c >= 32 && c != 127);
				if( keep ) {
					sb.append(c);
				}
			}
			s = sb.toString();
		}
		if( m.fold ) {
			s = s.toUpperCase();
		}
		return s;
	}

	private static final Pattern NUMBER = Pattern.compile("^[ \\t]*(-?\\d*\\.?\\d*)");
	private static final Pattern GENERAL = Pattern.compile("^[ \\t]*([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)");
	private static final Pattern HUMAN = Pattern.compile("^[ \\t]*(-?\\d+(?:\\.\\d+)?)([KMGTPEZYkmgtpezy]?)");
	private static final String MONTHS = "JANFEBMARAPRMAYJUNJULAUGSEPOCTNOVDEC";

	private static int compareKey(String a, String b, Mode m) {
		a = clean(a, m);
		b = clean(b, m);
		if( m.numeric ) {
			return number(a).compareTo(number(b));
		}
		if( m.general ) {
			return Double.compare(general(a), general(b));
		}
		if( m.human ) {
			return Double.compare(human(a), human(b));
		}
		if( m.month ) {
			return Integer.compare(month(a), month(b));
		}
		if( m.version ) {
			return version(a, b);
		}
		return a.compareTo(b);
	}

	private static BigDecimal number(String s) {
		Matcher m = NUMBER.matcher(s);
		if( m.find()) {
			String t = m.group(1);
			if( t.matches("-?\\.?") || t.isEmpty()) {
				return BigDecimal.ZERO;
			}
			return new BigDecimal(t.endsWith(".") ? t.substring(0, t.length()-1) : t);
		}
		return BigDecimal.ZERO;
	}

	private static double general(String s) {
		Matcher m = GENERAL.matcher(s);
		// a line with no number sorts before the numbers
		return m.find() ? Double.parseDouble(m.group(1)) : Double.NEGATIVE_INFINITY;
	}

	private static double human(String s) {
		Matcher m = HUMAN.matcher(s);
		if( !m.find()) {
			return 0;
		}
		double v = Double.parseDouble(m.group(1));
		int power = m.group(2).isEmpty() ? 0 : "KMGTPEZY".indexOf(Character.toUpperCase(m.group(2).charAt(0)))+1;
		return v*Math.pow(1024, power);
	}

	private static int month(String s) {
		String t = s.trim();
		if( t.length() < 3 ) {
			return 0;
		}
		int i = MONTHS.indexOf(t.substring(0, 3).toUpperCase());
		return i >= 0 && i%3 == 0 ? i/3+1 : 0;
	}

	/** compare the digit runs as numbers and the rest as text */
	private static int version(String a, String b) {
		int i = 0, j = 0;
		while( i < a.length() && j < b.length()) {
			char x = a.charAt(i), y = b.charAt(j);
			if( Character.isDigit(x) && Character.isDigit(y)) {
				int si = i, sj = j;
				while( i < a.length() && Character.isDigit(a.charAt(i))) {
					i++;
				}
				while( j < b.length() && Character.isDigit(b.charAt(j))) {
					j++;
				}
				int r = new java.math.BigInteger(a.substring(si, i)).compareTo(new java.math.BigInteger(b.substring(sj, j)));
				if( r != 0 ) {
					return r;
				}
			} else {
				if( x != y ) {
					return Character.compare(x, y);
				}
				i++;
				j++;
			}
		}
		return Integer.compare(a.length()-i, b.length()-j);
	}
}
