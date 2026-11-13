package us.bringardner.fsh.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.Argument;
import us.bringardner.fsh.ShellContext;

/**
 * The command line of a GNU-style command: clustered short options (-abc), options with a value
 * (-n5, -n 5, --lines=5), long names for either, and operands. Options may come after operands
 * until a --.
 */
final class Args {
	private final Map<Character, List<String>> map = new HashMap<>();
	final List<String> operands = new ArrayList<>();
	/** what is wrong with the command line, or null */
	String error;

	private Args() {
	}

	/**
	 * @param flags the letters of the options that take no value; # also takes -NUM
	 * @param valueOpts the letters of the options that take a value
	 * @param negativeNumbers a word like -5 or -1.5 is an operand
	 * @param longs "name:x" for a long option that means flag x, "name=x" for one that means x with a value
	 */
	static Args parse(ShellContext ctx, Argument [] raw, String flags, String valueOpts, boolean negativeNumbers, String... longs) {
		return parse(ctx, raw, flags, valueOpts, negativeNumbers ? "-\\d+(\\.\\d+)?" : null, longs);
	}

	/** as above, with any word that matches operandLike (a regular expression, or null) taken as an operand */
	static Args parse(ShellContext ctx, Argument [] raw, String flags, String valueOpts, String operandLike, String... longs) {
		java.util.List<String> words = new ArrayList<>();
		for (Argument r : raw) {
			words.add(""+r.getValue(ctx));
		}
		return parseWords(words, flags, valueOpts, operandLike, longs);
	}

	/** as above, for words that are already text */
	static Args parseWords(java.util.List<String> raw, String flags, String valueOpts, String operandLike, String... longs) {
		Args a = new Args();
		Map<String, Character> longFlag = new HashMap<>(), longValue = new HashMap<>();
		for (String l : longs) {
			int colon = l.indexOf(':'), eq = l.indexOf('=');
			if( colon > 0 ) {
				longFlag.put("--"+l.substring(0, colon), l.charAt(colon+1));
			} else if( eq > 0 ) {
				longValue.put("--"+l.substring(0, eq), l.charAt(eq+1));
			}
		}
		boolean ended = false;
		for (int idx = 0; idx < raw.size(); idx++) {
			String arg = raw.get(idx);
			if( ended || !arg.startsWith("-") || arg.equals("-") || (operandLike != null && arg.matches(operandLike))) {
				a.operands.add(arg);
			} else if( arg.equals("--")) {
				ended = true;
			} else if( arg.startsWith("--")) {
				int eq = arg.indexOf('=');
				String key = eq > 0 ? arg.substring(0, eq) : arg;
				String val = eq > 0 ? arg.substring(eq+1) : null;
				if( longFlag.containsKey(key)) {
					a.add(longFlag.get(key), "");
				} else if( longValue.containsKey(key)) {
					if( val == null ) {
						if( idx+1 >= raw.size()) {
							a.error = "option '"+key+"' requires an argument";
							return a;
						}
						val = raw.get(++idx);
					}
					a.add(longValue.get(key), val);
				} else {
					a.error = "unrecognized option '"+arg+"'";
					return a;
				}
			} else {
				String cluster = arg.substring(1);
				for (int c = 0; c < cluster.length(); c++) {
					char ch = cluster.charAt(c);
					if( flags.indexOf(ch) >= 0 && ch != '#' ) {
						a.add(ch, "");
					} else if( flags.indexOf('#') >= 0 && Character.isDigit(ch)) {
						int end = c;
						while( end < cluster.length() && Character.isDigit(cluster.charAt(end))) {
							end++;
						}
						a.add('#', cluster.substring(c, end));
						c = end-1;
					} else if( valueOpts.indexOf(ch) >= 0 ) {
						String val;
						if( c+1 < cluster.length()) {
							val = cluster.substring(c+1);
						} else if( idx+1 < raw.size()) {
							val = raw.get(++idx);
						} else {
							a.error = "option requires an argument -- '"+ch+"'";
							return a;
						}
						a.add(ch, val);
						break;
					} else {
						a.error = "invalid option -- '"+ch+"'";
						return a;
					}
				}
			}
		}
		return a;
	}

	private void add(char c, String val) {
		List<String> l = map.get(c);
		if( l == null ) {
			l = new ArrayList<>();
			map.put(c, l);
		}
		l.add(val);
	}

	boolean has(char c) {
		return map.containsKey(c);
	}

	/** the last value given for c, or null */
	String value(char c) {
		List<String> l = map.get(c);
		return l == null ? null : l.get(l.size()-1);
	}

	List<String> values(char c) {
		List<String> l = map.get(c);
		return l == null ? new ArrayList<>() : l;
	}

	/** an integer option, or def if it wasn't given; a bad one is an error */
	long number(char c, long def) {
		String v = value(c);
		if( v == null || v.isEmpty()) {
			return def;
		}
		try {
			return Long.parseLong(v.trim());
		} catch (NumberFormatException e) {
			error = "invalid number: '"+v+"'";
			return def;
		}
	}

	/** report a command line error; the exit status for it */
	int fail(ShellContext ctx, String cmd) {
		ctx.stderr.println(cmd+": "+error);
		ctx.stderr.println("Try '"+cmd+" --help' for more information.");
		return 1;
	}
}
