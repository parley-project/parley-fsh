package us.bringardner.fsh;

import java.text.Normalizer;
import java.util.Comparator;

/**
 * The order of strings as bash sorts them in the locale (glob results, [[ a < b ]]): by the
 * locale's collation, unless LC_ALL, LC_COLLATE or LANG (the first one set) is C or POSIX,
 * where it is by character code.
 *
 * The collation is the common one (CLDR's root order, as an en_US locale has it): white space,
 * then punctuation (_ - , ; : ! ? . ' " ( ) [ ] { } @ * / \ &amp; # % ` ^ + &lt; = &gt; | ~ $), then
 * digits, then letters; letters compare without case or accents first, then by their accents,
 * then lower case before upper case.
 */
public final class Collation {

	private Collation() {
	}

	private static final String PUNCTUATION = "_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";

	/** by character code */
	public static final Comparator<String> CODE = Comparator.naturalOrder();

	/** the locale's order (see the class) */
	public static final Comparator<String> LOCALE = Collation::compare;

	/** the order for ctx's locale */
	public static Comparator<String> current(ShellContext ctx) {
		for(String name : new String[] {"LC_ALL", "LC_COLLATE", "LANG"}) {
			Object v = ctx.getVariable(name);
			if( v != null && !v.toString().isEmpty()) {
				String locale = v.toString();
				return locale.equals("C") || locale.equals("POSIX") || locale.startsWith("C.") ? CODE : LOCALE;
			}
		}
		return LOCALE;
	}

	private static int compare(String a, String b) {
		if( a.equals(b)) {
			return 0;
		}
		String da = Normalizer.normalize(a, Normalizer.Form.NFD);
		String db = Normalizer.normalize(b, Normalizer.Form.NFD);
		// the letters, digits and punctuation first, then the accents, then the case
		for(int level = 0; level < 3; level++) {
			int c = compareLevel(da, db, level);
			if( c != 0 ) {
				return c;
			}
		}
		return a.compareTo(b);
	}

	/** compare the weights of level (0 primary, 1 accents, 2 case) of each character in turn */
	private static int compareLevel(String a, String b, int level) {
		int i = 0;
		int j = 0;
		while( true ) {
			i = next(a, i, level);
			j = next(b, j, level);
			if( i >= a.length() || j >= b.length()) {
				return Boolean.compare(i < a.length(), j < b.length());
			}
			long wa = weight(a, i, level);
			long wb = weight(b, j, level);
			if( wa != wb ) {
				return Long.compare(wa, wb);
			}
			i++;
			j++;
		}
	}

	/** the next character from i that has a weight at level (accents only count at level 1) */
	private static int next(String s, int i, int level) {
		while( i < s.length() && isMark(s.charAt(i)) != (level == 1)) {
			i++;
		}
		return i;
	}

	private static boolean isMark(char c) {
		int t = Character.getType(c);
		return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || t == Character.ENCLOSING_MARK;
	}

	private static long weight(String s, int i, int level) {
		char c = s.charAt(i);
		if( level == 1 ) {
			return c;
		}
		if( level == 2 ) {
			return Character.isUpperCase(c) ? 1 : 0;
		}
		if( Character.isWhitespace(c)) {
			return 1_000L+c;
		}
		int p = PUNCTUATION.indexOf(c);
		if( p >= 0 ) {
			return 2_000L+p;
		}
		if( c >= '0' && c <= '9' ) {
			return 3_000L+c;
		}
		if( Character.isLetter(c)) {
			return 4_000L+Character.toLowerCase(c);
		}
		return 100_000L+c;
	}
}
