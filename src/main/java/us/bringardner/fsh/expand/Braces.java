package us.bringardner.fsh.expand;

import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.syntax.Word;

/**
 * Brace expansion, the first expansion of a word: a{b,c}d is abd acd, {1..3} is 1 2 3, {a..e..2}
 * is a c e. Only braces and commas in unquoted text count; quotes and expansions ($x, ${x} ...)
 * are carried along whole. A brace with no comma or range in it ({a}, {}) is text.
 */
public final class Braces {

	private Braces() {
	}

	/** a character of unquoted text (c >= 0), or another part of the word */
	private record Item(int c, Word.Part part) {
		boolean is(char x) {
			return c == x;
		}
	}

	/** the words w becomes: just w if it has no braces to expand */
	public static List<Word> expand(Word w) {
		List<Item> items = new ArrayList<>();
		boolean any = false;
		for(Word.Part p : w.parts) {
			if( p instanceof Word.Literal l ) {
				for(char c : l.text().toCharArray()) {
					items.add(new Item(c, null));
					any |= c == '{';
				}
			} else {
				items.add(new Item(-1, p));
			}
		}
		if( !any ) {
			return List.of(w);
		}
		List<List<Item>> out = new ArrayList<>();
		expand(items, out);
		if( out.size() == 1 && out.get(0) == items ) {
			return List.of(w);
		}
		List<Word> ret = new ArrayList<>();
		for(List<Item> one : out) {
			ret.add(toWord(one, w));
		}
		return ret;
	}

	private static Word toWord(List<Item> items, Word from) {
		Word w = new Word();
		w.start = from.start;
		w.end = from.end;
		w.line = from.line;
		w.raw = from.raw;
		StringBuilder lit = new StringBuilder();
		for(Item i : items) {
			if( i.part == null ) {
				lit.append((char) i.c);
			} else if( i.part instanceof Word.Literal l ) {
				lit.append(l.text());
			} else {
				if( lit.length() > 0 ) {
					w.parts.add(new Word.Literal(lit.toString()));
					lit.setLength(0);
				}
				w.parts.add(i.part);
			}
		}
		if( lit.length() > 0 ) {
			w.parts.add(new Word.Literal(lit.toString()));
		}
		return w;
	}

	private static void expand(List<Item> items, List<List<Item>> out) {
		for (int open = 0; open < items.size(); open++) {
			if( !items.get(open).is('{')) {
				continue;
			}
			// its }: commas at its own depth split it
			int depth = 0;
			List<Integer> commas = new ArrayList<>();
			int close = -1;
			for (int i = open+1; i < items.size(); i++) {
				Item it = items.get(i);
				if( it.is('{')) {
					depth++;
				} else if( it.is('}')) {
					if( depth == 0 ) {
						close = i;
						break;
					}
					depth--;
				} else if( it.is(',') && depth == 0 ) {
					commas.add(i);
				}
			}
			if( close < 0 ) {
				// no } for this one: none of the later ones has one either
				break;
			}
			List<Item> pre = items.subList(0, open);
			List<Item> post = items.subList(close+1, items.size());
			List<List<Item>> alternatives = new ArrayList<>();
			if( !commas.isEmpty()) {
				int from = open+1;
				for(int comma : commas) {
					alternatives.add(items.subList(from, comma));
					from = comma+1;
				}
				alternatives.add(items.subList(from, close));
			} else {
				List<String> seq = sequence(items.subList(open+1, close));
				if( seq == null ) {
					continue;
				}
				for(String s : seq) {
					List<Item> alt = new ArrayList<>();
					for(char c : s.toCharArray()) {
						// the generated text is not looked at again
						alt.add(new Item(-1, new Word.Literal(String.valueOf(c))));
					}
					alternatives.add(alt);
				}
			}
			for(List<Item> alt : alternatives) {
				List<Item> next = new ArrayList<>(pre);
				next.addAll(alt);
				next.addAll(post);
				expand(next, out);
			}
			return;
		}
		out.add(items);
	}

	/** the words of {x..y} or {x..y..step} (numbers or single letters), or null if it is not one */
	private static List<String> sequence(List<Item> body) {
		StringBuilder text = new StringBuilder();
		for(Item i : body) {
			if( i.part != null ) {
				return null;
			}
			text.append((char) i.c);
		}
		String [] parts = text.toString().split("\\.\\.", -1);
		if( parts.length < 2 || parts.length > 3 ) {
			return null;
		}
		long step = 1;
		if( parts.length == 3 ) {
			if( !parts[2].matches("[-+]?[0-9]+")) {
				return null;
			}
			step = Math.abs(Long.parseLong(parts[2]));
			if( step == 0 ) {
				step = 1;
			}
		}
		List<String> ret = new ArrayList<>();
		if( parts[0].matches("[-+]?[0-9]+") && parts[1].matches("[-+]?[0-9]+")) {
			long from = Long.parseLong(parts[0]);
			long to = Long.parseLong(parts[1]);
			// {01..10}: as wide as the wider of the two, with leading zeros
			int width = 0;
			if( zeroPadded(parts[0]) || zeroPadded(parts[1])) {
				width = Math.max(parts[0].length(), parts[1].length());
			}
			long dir = from <= to ? step : -step;
			for (long n = from; from <= to ? n <= to : n >= to; n += dir) {
				ret.add(pad(n, width));
			}
			return ret;
		}
		if( parts[0].length() == 1 && parts[1].length() == 1 && Character.isLetter(parts[0].charAt(0)) && Character.isLetter(parts[1].charAt(0))) {
			char from = parts[0].charAt(0);
			char to = parts[1].charAt(0);
			long dir = from <= to ? step : -step;
			for (long c = from; from <= to ? c <= to : c >= to; c += dir) {
				ret.add(String.valueOf((char) c));
			}
			return ret;
		}
		return null;
	}

	private static boolean zeroPadded(String n) {
		String digits = n.startsWith("-") || n.startsWith("+") ? n.substring(1) : n;
		return digits.length() > 1 && digits.startsWith("0");
	}

	private static String pad(long n, int width) {
		String s = String.valueOf(Math.abs(n));
		int w = n < 0 ? width-1 : width;
		StringBuilder ret = new StringBuilder(n < 0 ? "-" : "");
		for (int i = s.length(); i < w; i++) {
			ret.append('0');
		}
		return ret.append(s).toString();
	}
}
