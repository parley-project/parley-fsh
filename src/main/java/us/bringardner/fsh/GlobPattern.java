package us.bringardner.fsh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A shell pattern (* ? [...] and extglob's ?() *() +() @() !()), matched against all of a
 * text. Without !( ) it is a regular expression; with it, a pattern is matched here, since what
 * !(p) takes is any text that p does not match, which a regular expression cannot say when more
 * follows it (!(foo)* matches foo: !(foo) takes nothing and * the rest).
 */
public abstract class GlobPattern {

	/** the whole of text matches */
	public abstract boolean matches(String text);

	/** the regular expression, if there is one (null with !( )) */
	public abstract Pattern regex();

	public static GlobPattern compile(String glob) {
		return compile(glob, false);
	}

	/** ignoreCase: nocaseglob, nocasematch */
	public static GlobPattern compile(String glob, boolean ignoreCase) {
		if( hasNegation(glob)) {
			return new Matcher(parse(glob, ignoreCase));
		}
		int flags = Pattern.DOTALL | (ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
		Pattern rx = Pattern.compile(ShellCommand.prepWildCards(glob, true), flags);
		return new GlobPattern() {
			@Override
			public boolean matches(String text) {
				return rx.matcher(text).matches();
			}

			@Override
			public Pattern regex() {
				return rx;
			}
		};
	}

	/** an unquoted !( in glob */
	static boolean hasNegation(String glob) {
		for (int i = 0; i+1 < glob.length(); i++) {
			char c = glob.charAt(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '\'' || c == '"' ) {
				int end = glob.indexOf(c, i+1);
				if( end < 0 ) {
					return false;
				}
				i = end;
			} else if( c == '!' && glob.charAt(i+1) == '(' ) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the pattern's parts

	private sealed interface Node permits Chars, Star, Group {
	}

	/** one character: a literal, ?, or [...] */
	private record Chars(Pattern one) implements Node {
	}

	private record Star() implements Node {
	}

	/** ?(..) *(..) +(..) @(..) !(..): kind, and its alternatives */
	private record Group(char kind, List<List<Node>> alternatives) implements Node {
	}

	private static List<Node> parse(String glob, boolean ignoreCase) {
		List<Node> ret = new ArrayList<>();
		int flags = Pattern.DOTALL | (ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
		int n = glob.length();
		for (int i = 0; i < n; i++) {
			char c = glob.charAt(i);
			if( "?*+@!".indexOf(c) >= 0 && i+1 < n && glob.charAt(i+1) == '(' ) {
				int end = closing(glob, i+1);
				if( end > 0 ) {
					List<List<Node>> alts = new ArrayList<>();
					for(String alt : split(glob.substring(i+2, end))) {
						alts.add(parse(alt, ignoreCase));
					}
					ret.add(new Group(c, alts));
					i = end;
					continue;
				}
			}
			switch (c) {
			case '*':
				ret.add(new Star());
				break;
			case '?':
				ret.add(new Chars(Pattern.compile(".", flags)));
				break;
			case '\\':
				if( i+1 < n ) {
					ret.add(literal(glob.charAt(++i), flags));
				} else {
					ret.add(literal('\\', flags));
				}
				break;
			case '\'':
			case '"': {
				int end = glob.indexOf(c, i+1);
				if( end < 0 ) {
					ret.add(literal(c, flags));
					break;
				}
				for(char q : glob.substring(i+1, end).toCharArray()) {
					ret.add(literal(q, flags));
				}
				i = end;
				break;
			}
			case '[': {
				int end = ShellCommand.bracketEnd(glob, i);
				if( end < 0 ) {
					ret.add(literal(c, flags));
					break;
				}
				ret.add(new Chars(Pattern.compile(ShellCommand.prepWildCards(glob.substring(i, end+1), true), flags)));
				i = end;
				break;
			}
			default:
				ret.add(literal(c, flags));
			}
		}
		return ret;
	}

	private static Chars literal(char c, int flags) {
		return new Chars(Pattern.compile(Pattern.quote(String.valueOf(c)), flags));
	}

	/** the ) that closes the ( at open, or -1 */
	private static int closing(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '(' ) {
				depth++;
			} else if( c == ')' && --depth == 0 ) {
				return i;
			}
		}
		return -1;
	}

	/** a|b|c, not splitting inside nested ( ) */
	private static List<String> split(String body) {
		List<String> ret = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '(' ) {
				depth++;
			} else if( c == ')' ) {
				depth--;
			} else if( c == '|' && depth == 0 ) {
				ret.add(body.substring(start, i));
				start = i+1;
			}
		}
		ret.add(body.substring(start));
		return ret;
	}

	// ------------------------------------------------------------------ matching

	private static final class Matcher extends GlobPattern {
		private final List<Node> nodes;

		Matcher(List<Node> nodes) {
			this.nodes = nodes;
		}

		@Override
		public Pattern regex() {
			return null;
		}

		@Override
		public boolean matches(String text) {
			return new Run(text).seq(nodes, 0, 0, text.length());
		}
	}

	/** one text being matched: whether a part of the pattern matches text[from, to) (remembered) */
	private static final class Run {
		private final String text;
		private final Map<String, Boolean> known = new HashMap<>();

		Run(String text) {
			this.text = text;
		}

		/** nodes[i..] match exactly text[pos, end) */
		boolean seq(List<Node> nodes, int i, int pos, int end) {
			if( i == nodes.size()) {
				return pos == end;
			}
			String key = System.identityHashCode(nodes)+":"+i+":"+pos+":"+end;
			Boolean k = known.get(key);
			if( k != null ) {
				return k;
			}
			boolean ret = seq0(nodes, i, pos, end);
			known.put(key, ret);
			return ret;
		}

		private boolean seq0(List<Node> nodes, int i, int pos, int end) {
			Node node = nodes.get(i);
			switch (node) {
			case Chars ch -> {
				return pos < end && ch.one().matcher(text.substring(pos, pos+1)).matches() && seq(nodes, i+1, pos+1, end);
			}
			case Star s -> {
				for (int k = pos; k <= end; k++) {
					if( seq(nodes, i+1, k, end)) {
						return true;
					}
				}
				return false;
			}
			case Group g -> {
				switch (g.kind()) {
				case '@':
					return one(g, nodes, i, pos, end);
				case '?':
					return seq(nodes, i+1, pos, end) || one(g, nodes, i, pos, end);
				case '*':
					return repeat(g, nodes, i, pos, end);
				case '+': {
					for (int k = pos+1; k <= end; k++) {
						if( any(g, pos, k) && repeat(g, nodes, i, k, end)) {
							return true;
						}
					}
					return any(g, pos, pos) && seq(nodes, i+1, pos, end);
				}
				default: {
					// !(..): any text that none of them matches, then the rest
					for (int k = pos; k <= end; k++) {
						if( !any(g, pos, k) && seq(nodes, i+1, k, end)) {
							return true;
						}
					}
					return false;
				}
				}
			}
			}
		}

		/** one of g's alternatives, then the rest */
		private boolean one(Group g, List<Node> nodes, int i, int pos, int end) {
			for (int k = pos; k <= end; k++) {
				if( any(g, pos, k) && seq(nodes, i+1, k, end)) {
					return true;
				}
			}
			return false;
		}

		/** g's alternatives zero or more times, then the rest */
		private boolean repeat(Group g, List<Node> nodes, int i, int pos, int end) {
			if( seq(nodes, i+1, pos, end)) {
				return true;
			}
			for (int k = pos+1; k <= end; k++) {
				if( any(g, pos, k) && repeat(g, nodes, i, k, end)) {
					return true;
				}
			}
			return false;
		}

		/** one of g's alternatives matches text[from, to) */
		private boolean any(Group g, int from, int to) {
			for(List<Node> alt : g.alternatives()) {
				if( seq(alt, 0, from, to)) {
					return true;
				}
			}
			return false;
		}
	}
}
