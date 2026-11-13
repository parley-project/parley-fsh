package us.bringardner.fsh.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** tr, modeled on the GNU tr: translates, squeezes and deletes characters of standard input. */
public class Tr extends ShellCommand{
	static String name = "tr";
	static String help = "tr [-cdst] SET1 [SET2]\n"
			+ "\tTranslate, squeeze or delete the characters of standard input.\n"
			+ "\t-c, -C, --complement   use the characters not in SET1\n"
			+ "\t-d, --delete           delete the characters in SET1, don't translate\n"
			+ "\t-s, --squeeze-repeats  replace each run of one repeated character in the last SET by one\n"
			+ "\t-t, --truncate-set1    make SET1 as short as SET2 first\n"
			+ "\tA SET is characters, with these: \\\\ \\a \\b \\f \\n \\r \\t \\v \\NNN (octal), a range a-z,\n"
			+ "\t[:alpha:] [:digit:] [:alnum:] [:upper:] [:lower:] [:space:] [:blank:] [:punct:] [:print:]\n"
			+ "\t[:graph:] [:cntrl:] [:xdigit:], [=c=] and, in SET2, [c*N] (N copies of c) or [c*] (to fill).\n"
			+ "\tA short SET2 is padded with its last character.";

	private static final String USAGE = "usage: tr [-cdst] set1 [set2]";

	public Tr() {
		super(name, help);
	}

	private static class TrException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		TrException(String msg) {
			super(msg);
		}
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean complement = false, delete = false, squeeze = false, truncate = false;
		List<String> sets = new ArrayList<>();
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.length() == 1 ) {
				sets.add(arg);
			} else if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				switch (arg) {
				case "--complement": complement = true; break;
				case "--delete": delete = true; break;
				case "--squeeze-repeats": squeeze = true; break;
				case "--truncate-set1": truncate = true; break;
				default:
					ctx.stderr.println("tr: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
			} else {
				for (char ch : arg.substring(1).toCharArray()) {
					switch (ch) {
					case 'c': case 'C': complement = true; break;
					case 'd': delete = true; break;
					case 's': squeeze = true; break;
					case 't': truncate = true; break;
					default:
						ctx.stderr.println("tr: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				}
			}
		}

		if( sets.isEmpty()) {
			ctx.stderr.println("tr: missing operand");
			ctx.stderr.println(USAGE);
			return 1;
		}
		boolean translate = !delete && !(squeeze && sets.size() == 1);
		int wanted = (delete && squeeze) || translate ? 2 : 1;
		if( sets.size() > wanted ) {
			ctx.stderr.println("tr: extra operand '"+sets.get(wanted)+"'");
			ctx.stderr.println(USAGE);
			return 1;
		}
		if( sets.size() < wanted ) {
			ctx.stderr.println("tr: missing operand after '"+sets.get(sets.size()-1)+"'");
			ctx.stderr.println(USAGE);
			return 1;
		}

		String text = new String(ctx.stdin.readAllBytes(), StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder(text.length());
		try {
			List<Integer> set1 = expand(sets.get(0), -1);
			List<Integer> set2 = sets.size() > 1 ? expand(sets.get(1), set1.size()) : null;
			Set<Integer> in1 = new HashSet<>(set1);

			// the characters of 0..255 that are not in SET1, in order (what a complement translates in turn)
			List<Integer> comp = new ArrayList<>();
			if( complement ) {
				for (int c = 0; c < 256; c++) {
					if( !in1.contains(c)) {
						comp.add(c);
					}
				}
			}
			Map<Integer, Integer> map = new HashMap<>();
			if( translate && !complement ) {
				List<Integer> from = set1;
				if( truncate && set2.size() < from.size()) {
					from = from.subList(0, set2.size());
				}
				if( set2.isEmpty()) {
					throw new TrException("when not truncating set1, string2 must be non-empty");
				}
				for (int i = 0; i < from.size(); i++) {
					map.put(from.get(i), set2.get(Math.min(i, set2.size()-1)));
				}
			} else if( translate && set2.isEmpty()) {
				throw new TrException("when not truncating set1, string2 must be non-empty");
			}

			Set<Integer> squeezeSet = null;
			if( squeeze ) {
				squeezeSet = new HashSet<>(sets.size() > 1 ? set2 : set1);
			}
			boolean squeezeComplement = squeeze && sets.size() == 1 && complement;

			int prev = -1;
			for (int i = 0; i < text.length(); ) {
				int cp = text.codePointAt(i);
				i += Character.charCount(cp);
				boolean inSet = in1.contains(cp) != complement;
				int result = cp;
				if( delete && inSet ) {
					continue;
				}
				if( translate && inSet ) {
					if( complement ) {
						int rank = cp < 256 ? comp.indexOf(cp) : comp.size();
						result = set2.get(Math.min(rank, set2.size()-1));
					} else {
						Integer to = map.get(cp);
						if( to != null ) {
							result = to;
						}
					}
				}
				if( squeeze && result == prev ) {
					boolean inSqueeze = squeezeComplement ? !in1.contains(result) : squeezeSet.contains(result);
					if( inSqueeze ) {
						continue;
					}
				}
				out.appendCodePoint(result);
				prev = result;
			}
		} catch (TrException e) {
			ctx.stderr.println("tr: "+e.getMessage());
			return 1;
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return 0;
	}

	private static int escape(int[] cps, int[] pos) {
		int i = pos[0];
		int c = cps[i];
		if( c != '\\' || i+1 >= cps.length ) {
			pos[0] = i+1;
			return c;
		}
		int d = cps[i+1];
		pos[0] = i+2;
		switch (d) {
		case '\\': return '\\';
		case 'a': return 7;
		case 'b': return 8;
		case 'f': return 12;
		case 'n': return 10;
		case 'r': return 13;
		case 't': return 9;
		case 'v': return 11;
		}
		if( d >= '0' && d <= '7' ) {
			int v = d-'0';
			int end = i+2;
			while( end < cps.length && end < i+4 && cps[end] >= '0' && cps[end] <= '7' ) {
				v = v*8+cps[end]-'0';
				end++;
			}
			pos[0] = end;
			return v;
		}
		return d;
	}

	private static boolean inClass(String name, int c) {
		switch (name) {
		case "alpha": return Character.isLetter(c);
		case "digit": return c >= '0' && c <= '9';
		case "alnum": return Character.isLetterOrDigit(c);
		case "upper": return Character.isUpperCase(c);
		case "lower": return Character.isLowerCase(c);
		case "space": return c == ' ' || (c >= 9 && c <= 13);
		case "blank": return c == ' ' || c == '\t';
		case "punct": return c > 32 && c < 127 && !Character.isLetterOrDigit(c);
		case "print": return c >= 32 && c < 127;
		case "graph": return c > 32 && c < 127;
		case "cntrl": return c < 32 || c == 127;
		case "xdigit": return Character.digit(c, 16) >= 0 && c < 128;
		}
		throw new TrException("invalid character class '"+name+"'");
	}

	/**
	 * The characters of a set in order.
	 * @param fillTo the length [c*] fills the set to (SET1's), or -1 where it isn't allowed
	 */
	private static List<Integer> expand(String set, int fillTo) {
		int [] cps = set.codePoints().toArray();
		List<Integer> ret = new ArrayList<>();
		int [] pos = {0};
		int fillAt = -1, fillChar = 0;
		while( pos[0] < cps.length) {
			int i = pos[0];
			if( cps[i] == '[' && i+1 < cps.length ) {
				if( cps[i+1] == ':' ) {
					int end = set.indexOf(":]", set.offsetByCodePoints(0, i+2));
					if( end >= 0 ) {
						String name = set.substring(set.offsetByCodePoints(0, i+2), end);
						for (int c = 0; c < 256; c++) {
							if( inClass(name, c)) {
								ret.add(c);
							}
						}
						pos[0] = set.codePointCount(0, end)+2;
						continue;
					}
				} else if( cps[i+1] == '=' && i+4 < cps.length && cps[i+3] == '=' && cps[i+4] == ']' ) {
					ret.add(cps[i+2]);
					pos[0] = i+5;
					continue;
				} else if( fillTo >= -1 ) {
					// [c*N] or [c*]
					int [] p = {i+1};
					int c = escape(cps, p);
					if( p[0] < cps.length && cps[p[0]] == '*' ) {
						int j = p[0]+1;
						int numStart = j;
						while( j < cps.length && Character.isDigit(cps[j])) {
							j++;
						}
						if( j < cps.length && cps[j] == ']' ) {
							String digits = new String(cps, numStart, j-numStart);
							if( digits.isEmpty() || Integer.parseInt(digits, digits.startsWith("0") ? 8 : 10) == 0 ) {
								if( fillTo < 0 ) {
									throw new TrException("the [c*] repeat construct may not appear in string1");
								}
								fillAt = ret.size();
								fillChar = c;
							} else {
								int count = Integer.parseInt(digits, digits.startsWith("0") ? 8 : 10);
								for (int k = 0; k < count; k++) {
									ret.add(c);
								}
							}
							pos[0] = j+1;
							continue;
						}
					}
				}
			}
			int start = escape(cps, pos);
			if( pos[0]+1 < cps.length && cps[pos[0]] == '-' ) {
				pos[0]++;
				int end = escape(cps, pos);
				if( end < start ) {
					throw new TrException("range-endpoints of '"+new String(Character.toChars(start))+"-"+new String(Character.toChars(end))+"' are in reverse collating sequence order");
				}
				for (int c = start; c <= end; c++) {
					ret.add(c);
				}
			} else {
				ret.add(start);
			}
		}
		if( fillAt >= 0 ) {
			int fill = Math.max(0, fillTo-ret.size());
			for (int k = 0; k < fill; k++) {
				ret.add(fillAt, fillChar);
			}
		}
		return ret;
	}
}
