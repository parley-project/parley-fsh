package us.bringardner.fsh;

import java.util.ArrayList;
import java.util.List;

/**
 * History expansion as bash does it (a port of readline's histexpand.c, with bash's settings):
 * !! (the last command), !n, !-n, !string, !?string?, !# (the line so far), ^old^new, with word
 * designators (!$, !^, !*, !:2, !!:1-3, !%) and modifiers (:h :t :r :e :p :q :x :s/old/new/
 * :gs/old/new/ :& :g&). Nothing inside '...' is expanded, nor a \! or a ! before a blank, = ; & |
 * ( ) &lt; &gt;, nor ${!x}, $!, [!x], !( ) or a ! after a # that starts a word.
 */
public final class HistoryExpansion {

	/** the line after expansion; error is set (and line is not to run) if an event or word was bad */
	public static final class Result {
		public final String line;
		/** the line changed: bash shows it before running it */
		public final boolean changed;
		/** :p was used: show the line and add it to history, but do not run it */
		public final boolean printOnly;
		/** bash's message ("!foo: event not found"), or null */
		public final String error;

		Result(String line, boolean changed, boolean printOnly, String error) {
			this.line = line;
			this.changed = changed;
			this.printOnly = printOnly;
			this.error = error;
		}
	}

	private static final char EXPANSION = '!';
	private static final char SUBST = '^';
	private static final char COMMENT = '#';
	private static final String NO_EXPAND = " \t\n\r=;&|()<>";
	private static final String WORD_DELIMITERS = " \t\n;&()|<>";
	private static final String QUOTE_CHARACTERS = "\"'`";
	private static final String EVENT_DELIMITERS = "^$*%-";
	private static final String SEARCH_DELIMITERS = ";&()|<>";
	private static final String SLASHIFY_IN_QUOTES = "\\`\"$";

	// (kept from one line to the next, as readline keeps them)
	private static String searchString;
	private static String searchMatch;
	private static String substLhs;
	private static String substRhs;

	/** an expansion that failed: its message */
	private static final class Failure extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Failure(String message) {
			super(message, null, false, false);
		}
	}

	private final List<String> history;
	private final int base;

	private HistoryExpansion(List<String> history, int base) {
		this.history = history;
		this.base = base;
	}

	/**
	 * Expand line.
	 * @param history the commands so far, oldest first (!1 is the first)
	 */
	public static Result expand(String line, List<String> history) {
		return expand(line, history, 1);
	}

	/**
	 * Expand line.
	 * @param history the commands so far, oldest first
	 * @param base the number of the first of them
	 */
	public static Result expand(String line, List<String> history, int base) {
		return expand(line, history, base, false);
	}

	/** in posix mode "..." quotes the ! too, as bash's */
	private static boolean posix;

	public static Result expand(String line, List<String> history, int base, boolean posixMode) {
		posix = posixMode;
		if( line.indexOf(EXPANSION) < 0 && !(line.length() > 0 && line.charAt(0) == SUBST)) {
			return new Result(line, false, false, null);
		}
		HistoryExpansion h = new HistoryExpansion(history, base);
		try {
			int[] status = new int[1];
			String ret = h.historyExpand(line, status);
			return new Result(ret, status[0] > 0, status[0] == 2, null);
		} catch (Failure f) {
			return new Result(line, false, false, f.getMessage());
		}
	}

	private static boolean member(char c, String s) {
		return c != '\0' && s.indexOf(c) >= 0;
	}

	private static char at(String s, int i) {
		return i >= 0 && i < s.length() ? s.charAt(i) : '\0';
	}

	// ------------------------------------------------------------------ history_expand

	/** status[0]: 0 nothing done, 1 expanded, 2 print only */
	private String historyExpand(String hstring, int[] status) {
		String string = hstring;
		int l;
		if( hstring.length() > 0 && hstring.charAt(0) == SUBST ) {
			// ^old^new: !!:s^old^new
			string = ""+EXPANSION+EXPANSION+":s"+hstring;
		} else {
			// is there an expansion at all?
			boolean dquote = false;
			int i = 0;
			l = string.length();
			for (; i < l; i++) {
				char c = string.charAt(i);
				char cc = at(string, i+1);
				if( c == COMMENT && !dquote && (i == 0 || member(string.charAt(i-1), WORD_DELIMITERS))) {
					i = l;
					break;
				} else if( c == EXPANSION ) {
					if( cc == '\0' || member(cc, NO_EXPAND)) {
						continue;
					} else if( dquote && cc == '"' ) {
						continue;
					} else if( inhibit(string, i)) {
						continue;
					} else {
						break;
					}
				} else if( dquote && c == '\\' && cc == '"' ) {
					i++;
				} else if( c == '"' ) {
					dquote = !dquote;
				} else if( !dquote && c == '\'' ) {
					boolean flag = i > 0 && string.charAt(i-1) == '$';
					i++;
					i = singleQuoted(string, i, flag);
					if( i >= l ) {
						i = l;
						break;
					}
				} else if( c == '\\' ) {
					if( cc == '\'' || cc == EXPANSION ) {
						i++;
					}
				}
			}
			if( i >= l || string.charAt(i) != EXPANSION ) {
				status[0] = 0;
				return string;
			}
		}
		StringBuilder result = new StringBuilder();
		boolean dquote = false;
		boolean passc = false;
		int modified = 0;
		int onlyPrinting = 0;
		l = string.length();
		for (int i = 0; i < l; i++) {
			char tchar = string.charAt(i);
			if( passc ) {
				passc = false;
				result.append(tchar);
				continue;
			}
			if( tchar == '\\' ) {
				passc = true;
				result.append(tchar);
			} else if( tchar == '"' ) {
				dquote = !dquote;
				result.append(tchar);
			} else if( tchar == '\'' ) {
				if( !dquote ) {
					boolean flag = i > 0 && string.charAt(i-1) == '$';
					int quote = i++;
					i = singleQuoted(string, i, flag);
					result.append(string, quote, Math.min(l, i+1));
				} else {
					result.append(tchar);
				}
			} else if( tchar == COMMENT ) {
				if( !dquote && (i == 0 || member(string.charAt(i-1), WORD_DELIMITERS))) {
					result.append(string.substring(i));
					i = l;
				} else {
					result.append(tchar);
				}
			} else if( tchar == EXPANSION ) {
				char cc = at(string, i+1);
				if( cc == '\0' || member(cc, NO_EXPAND) || (dquote && cc == '"')) {
					result.append(tchar);
					continue;
				}
				// (decided on what was expanded so far, with this ! after it, as readline does)
				String sofar = result.toString()+tchar+cc;
				if( inhibit(sofar, result.length())) {
					result.append(tchar);
					continue;
				}
				int[] end = new int[1];
				String[] out = new String[1];
				int r = expandInternal(string, i, dquote ? '"' : '\0', end, out, result.toString());
				modified++;
				result.append(out[0]);
				if( r == 1 ) {
					onlyPrinting++;
				}
				i = end[0];
			} else {
				result.append(tchar);
			}
		}
		status[0] = onlyPrinting > 0 ? 2 : modified > 0 ? 1 : 0;
		return result.toString();
	}

	/** past the single-quoted text starting at i (at its closing quote); flag: \ quotes in $'..' */
	private static int singleQuoted(String s, int i, boolean flag) {
		for (; i < s.length() && s.charAt(i) != '\''; i++) {
			if( flag && s.charAt(i) == '\\' && i+1 < s.length()) {
				i++;
			}
		}
		return i;
	}

	/**
	 * bash's bash_history_inhibit_expansion: the ! at i is [!x], ${!x}, $!, !( ), or inside
	 * quotes or a command substitution.
	 */
	private static boolean inhibit(String s, int i) {
		if( i > 0 && s.charAt(i-1) == '[' && s.indexOf(']', i+1) >= 0 ) {
			return true;
		}
		if( i > 1 && s.charAt(i-1) == '{' && s.charAt(i-2) == '$' && s.indexOf('}', i+1) >= 0 ) {
			return true;
		}
		if( i > 1 && s.charAt(i-1) == '$' ) {
			return true;
		}
		if( i > 1 && at(s, i+1) == '(' && s.indexOf(')', i+2) >= 0 ) {
			return true;
		}
		// (in '...', or !" in "...": not one to expand)
		int t = skipToHistexp(s, 0);
		if( t > 0 ) {
			while( t < i ) {
				t = skipToHistexp(s, t+1);
				if( t <= 0 ) {
					return false;
				}
			}
			return t > i;
		}
		return false;
	}

	/**
	 * bash's skip_to_histexp: the next ! at or after from that is not in '...' (nor a !" in
	 * "..."), or the length of s; $( ) and `...` are gone into.
	 */
	private static int skipToHistexp(String s, int from) {
		boolean pass = false, backq = false, dquote = false, oldDquote = false;
		int comsub = 0;
		int i = from;
		while( i < s.length()) {
			char c = s.charAt(i);
			if( pass ) {
				pass = false;
				i++;
				continue;
			} else if( c == '\\' ) {
				pass = true;
				i++;
				continue;
			} else if( backq && c == '`' ) {
				backq = false;
				dquote = oldDquote;
				i++;
				continue;
			} else if( c == '`' ) {
				backq = true;
				oldDquote = dquote;
				dquote = false;
				i++;
				continue;
			} else if( dquote && c == EXPANSION && at(s, i+1) == '"' ) {
				i++;
				continue;
			} else if( c == EXPANSION ) {
				return i;
			} else if( dquote && c == '\'' ) {
				i++;
				continue;
			} else if( c == '\'' ) {
				int e = s.indexOf('\'', i+1);
				i = e < 0 ? s.length() : e+1;
				continue;
			} else if( c == '"' && posix ) {
				// (posix mode: a double-quoted ! is quoted)
				for(i++; i < s.length() && s.charAt(i) != '"'; i++) {
					if( s.charAt(i) == '\\' ) {
						i++;
					}
				}
				i++;
				continue;
			} else if( c == '"' ) {
				dquote = !dquote;
				i++;
				continue;
			} else if( (c == '$' || c == '<' || c == '>') && at(s, i+1) == '(' && at(s, i+2) != '(' ) {
				if( i+2 >= s.length()) {
					return i+2;
				}
				i += 2;
				comsub++;
				oldDquote = dquote;
				dquote = false;
				continue;
			} else if( comsub > 0 && c == ')' ) {
				comsub--;
				dquote = oldDquote;
				i++;
				continue;
			}
			i++;
		}
		return s.length();
	}

	// ------------------------------------------------------------------ history_expand_internal

	/** @return 0, or 1 for :p; the expansion in out[0], where it ends in end[0] */
	private int expandInternal(String string, int start, char qc, int[] end, String[] out, String currentLine) {
		int i = start;
		String event;
		if( member(at(string, i+1), ":$*%^")) {
			i++;
			event = event("!!", new int[] {0}, '\0');
		} else if( at(string, i+1) == '#' ) {
			i += 2;
			event = currentLine;
		} else {
			int[] idx = {i};
			event = event(string, idx, qc);
			i = idx[0];
		}
		if( event == null ) {
			throw new Failure(string.substring(start, Math.min(i, string.length()))+": event not found");
		}
		int starting = i;
		int[] idx = {i};
		String word = wordSpecifier(string, event, idx);
		i = idx[0];
		if( word == BAD_WORD ) {
			throw new Failure(string.substring(starting, Math.min(i, string.length()))+": bad word specifier");
		}
		String temp = word != null ? word : event;
		char wantQuotes = 0;
		boolean global = false, byWords = false, printOnly = false;
		starting = i;
		while( at(string, i) == ':' ) {
			char c = at(string, i+1);
			if( c == 'g' || c == 'a' ) {
				global = true;
				i++;
				c = at(string, i+1);
			} else if( c == 'G' ) {
				byWords = true;
				i++;
				c = at(string, i+1);
			}
			switch (c) {
			case 'q':
				wantQuotes = 'q';
				break;
			case 'x':
				wantQuotes = 'x';
				break;
			case 'p':
				printOnly = true;
				break;
			case 't': {
				int slash = temp.lastIndexOf('/');
				if( slash >= 0 ) {
					temp = temp.substring(slash+1);
				}
				break;
			}
			case 'h': {
				int slash = temp.lastIndexOf('/');
				if( slash >= 0 ) {
					temp = temp.substring(0, slash);
				}
				break;
			}
			case 'r': {
				int dot = temp.lastIndexOf('.');
				if( dot >= 0 ) {
					temp = temp.substring(0, dot);
				}
				break;
			}
			case 'e': {
				int dot = temp.lastIndexOf('.');
				if( dot >= 0 ) {
					temp = temp.substring(dot);
				}
				break;
			}
			case '&':
			case 's': {
				if( c == 's' ) {
					if( i+2 >= string.length()) {
						// (no delimiter: nothing)
						i += 2;
						continue;
					}
					char delimiter = string.charAt(i+2);
					i += 3;
					int[] p = {i};
					String lhs = substPattern(string, p, delimiter, false);
					if( lhs != null ) {
						substLhs = lhs;
					} else if( substLhs == null ) {
						substLhs = searchString != null && !searchString.isEmpty() ? searchString : null;
					}
					substRhs = substPattern(string, p, delimiter, true);
					i = p[0];
					if( substLhs != null && substRhs.indexOf('&') >= 0 ) {
						substRhs = postprocRhs(substRhs, substLhs);
					}
				} else {
					i += 2;
				}
				if( substLhs == null || substLhs.isEmpty()) {
					throw new Failure(string.substring(starting, Math.min(i, string.length()))+": no previous substitution");
				}
				if( substLhs.length() > temp.length()) {
					throw new Failure(string.substring(starting, Math.min(i, string.length()))+": substitution failed");
				}
				boolean failed = true;
				int we = 0;
				for (int si = 0; si+substLhs.length() <= temp.length(); si++) {
					if( byWords && si > we ) {
						while( si < temp.length() && Character.isWhitespace(temp.charAt(si))) {
							si++;
						}
						we = tokenizeWord(temp, si);
					}
					if( temp.startsWith(substLhs, si)) {
						temp = temp.substring(0, si)+substRhs+temp.substring(si+substLhs.length());
						failed = false;
						if( global ) {
							si += substRhs.length()-1;
							continue;
						} else if( byWords ) {
							si = we;
							continue;
						}
						break;
					}
				}
				if( failed ) {
					throw new Failure(string.substring(starting, Math.min(i, string.length()))+": substitution failed");
				}
				// (the substitution read its own text: i is past it)
				global = false;
				continue;
			}
			default:
				throw new Failure(string.substring(i+1, Math.min(i+2, string.length()))+": unrecognized history modifier");
			}
			i += 2;
		}
		i--;
		if( wantQuotes == 'q' ) {
			temp = "'"+temp.replace("'", "'\\''")+"'";
		} else if( wantQuotes == 'x' ) {
			temp = quoteBreaks(temp);
		}
		end[0] = i;
		out[0] = temp;
		return printOnly ? 1 : 0;
	}

	/** :x: quoted as words broken at blanks */
	private static String quoteBreaks(String s) {
		StringBuilder r = new StringBuilder("'");
		for(char c : s.toCharArray()) {
			if( c == '\'' ) {
				r.append("'\\''");
			} else if( Character.isWhitespace(c)) {
				r.append('\'').append(c).append('\'');
			} else {
				r.append(c);
			}
		}
		return r.append('\'').toString();
	}

	/** the text of a :s pattern up to delimiter (\delimiter is the delimiter); null for an empty lhs */
	private static String substPattern(String str, int[] iptr, char delimiter, boolean rhs) {
		int i = iptr[0];
		int si = i;
		for (; si < str.length() && str.charAt(si) != delimiter; si++) {
			if( str.charAt(si) == '\\' && at(str, si+1) == delimiter ) {
				si++;
			}
		}
		String s = null;
		if( si > i || rhs ) {
			StringBuilder b = new StringBuilder();
			for (int k = i; k < si; k++) {
				if( str.charAt(k) == '\\' && at(str, k+1) == delimiter ) {
					k++;
				}
				b.append(str.charAt(k));
			}
			s = b.toString();
		}
		i = si;
		if( i < str.length()) {
			i++;
		}
		iptr[0] = i;
		return s;
	}

	/** & in the rhs is the lhs (\& is &) */
	private static String postprocRhs(String rhs, String lhs) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < rhs.length(); i++) {
			char c = rhs.charAt(i);
			if( c == '&' ) {
				b.append(lhs);
			} else {
				if( c == '\\' && at(rhs, i+1) == '&' ) {
					i++;
					c = '&';
				}
				b.append(c);
			}
		}
		return b.toString();
	}

	// ------------------------------------------------------------------ get_history_event

	/** the history line string[idx] names (idx at the !, moved past it), or null */
	private String event(String string, int[] idx, char delimitingQuote) {
		int i = idx[0];
		if( at(string, i) != EXPANSION ) {
			return null;
		}
		i++;
		int sign = 1;
		boolean substring = false;
		if( at(string, i) == EXPANSION ) {
			i++;
			idx[0] = i;
			return entry(base+history.size()-1);
		}
		if( at(string, i) == '-' && Character.isDigit(at(string, i+1))) {
			sign = -1;
			i++;
		}
		if( Character.isDigit(at(string, i))) {
			long which = 0;
			for (; Character.isDigit(at(string, i)); i++) {
				which = which*10+(string.charAt(i)-'0');
			}
			idx[0] = i;
			if( sign < 0 ) {
				which = history.size()+base-which;
			}
			return which > Integer.MAX_VALUE ? null : entry((int) which);
		}
		if( at(string, i) == '?' ) {
			substring = true;
			i++;
		}
		int local = i;
		for (; i < string.length(); i++) {
			char c = string.charAt(i);
			if( (!substring && (Character.isWhitespace(c) || c == ':'
					|| (i > local && c == '-')
					|| (c != '-' && member(c, EVENT_DELIMITERS))
					|| member(c, SEARCH_DELIMITERS)
					|| (delimitingQuote != '\0' && c == delimitingQuote)))
					|| c == '\n' || (substring && c == '?')) {
				break;
			}
		}
		String temp = string.substring(local, i);
		if( substring && at(string, i) == '?' ) {
			i++;
		}
		idx[0] = i;
		if( temp.isEmpty() && substring ) {
			if( searchString == null ) {
				return null;
			}
			temp = searchString;
		}
		for (int h = history.size()-1; h >= 0; h--) {
			String line = history.get(h);
			if( substring ) {
				int at = line.lastIndexOf(temp);
				if( at >= 0 ) {
					searchString = temp;
					searchMatch = findWord(line, at);
					return line;
				}
			} else if( line.startsWith(temp)) {
				return line;
			}
		}
		return null;
	}

	/** history entry number which, or null */
	private String entry(int which) {
		int i = which-base;
		return i >= 0 && i < history.size() ? history.get(i) : null;
	}

	// ------------------------------------------------------------------ word specifiers

	/** a marker: the words asked for are not there */
	private static final String BAD_WORD = new String("bad word");

	/** the words spec at idx names in from (idx moved past it), null if there is none, or BAD_WORD */
	private static String wordSpecifier(String spec, String from, int[] idx) {
		int i = idx[0];
		boolean expecting = false;
		if( at(spec, i) == ':' ) {
			i++;
			expecting = true;
		}
		if( at(spec, i) == '%' ) {
			idx[0] = i+1;
			return searchMatch != null ? searchMatch : "";
		}
		if( at(spec, i) == '*' ) {
			idx[0] = i+1;
			String r = argExtract(1, DOLLAR, from);
			return r != null ? r : "";
		}
		if( at(spec, i) == '$' ) {
			idx[0] = i+1;
			String r = argExtract(DOLLAR, DOLLAR, from);
			return r != null ? r : BAD_WORD;
		}
		int first, last;
		if( at(spec, i) == '-' ) {
			first = 0;
		} else if( at(spec, i) == '^' ) {
			first = 1;
			i++;
		} else if( Character.isDigit(at(spec, i)) && expecting ) {
			first = 0;
			for (; Character.isDigit(at(spec, i)); i++) {
				first = first*10+(spec.charAt(i)-'0');
			}
		} else {
			return null;
		}
		if( at(spec, i) == '^' || at(spec, i) == '*' ) {
			last = at(spec, i) == '^' ? 1 : DOLLAR;
			i++;
		} else if( at(spec, i) != '-' ) {
			last = first;
		} else {
			i++;
			if( Character.isDigit(at(spec, i))) {
				last = 0;
				for (; Character.isDigit(at(spec, i)); i++) {
					last = last*10+(spec.charAt(i)-'0');
				}
			} else if( at(spec, i) == '$' ) {
				i++;
				last = DOLLAR;
			} else if( at(spec, i) == '^' ) {
				i++;
				last = 1;
			} else {
				// x-: up to the word before the last
				last = -1;
			}
		}
		idx[0] = i;
		String r = null;
		if( last >= first || last == DOLLAR || last < 0 ) {
			r = argExtract(first, last, from);
		}
		return r != null ? r : BAD_WORD;
	}

	/** $ as a word number: the last */
	private static final int DOLLAR = Integer.MAX_VALUE;

	/** the words first to last of string (negative: from the right; DOLLAR: the last), or null */
	static String argExtract(int first, int last, String string) {
		List<String> list = tokenize(string);
		int len = list.size();
		if( len == 0 ) {
			return null;
		}
		if( last < 0 ) {
			last = len+last-1;
		}
		if( first < 0 ) {
			first = len+first-1;
		}
		if( last == DOLLAR ) {
			last = len-1;
		}
		if( first == DOLLAR ) {
			first = len-1;
		}
		last++;
		if( first >= len || last > len || first < 0 || last < 0 || first > last ) {
			return null;
		}
		return String.join(" ", list.subList(first, last));
	}

	/** where the word that starts at ind ends (bash's history_tokenize_word) */
	private static int tokenizeWord(String string, int ind) {
		int i = ind;
		char delimiter = 0, delimopen = 0;
		int nestdelim = 0;
		if( member(at(string, i), "()\n")) {
			return i+1;
		}
		boolean getWord = false;
		if( Character.isDigit(at(string, i))) {
			int j = i;
			while( Character.isDigit(at(string, j))) {
				j++;
			}
			if( j >= string.length()) {
				return j;
			}
			if( string.charAt(j) == '<' || string.charAt(j) == '>' ) {
				i = j;
			} else {
				i = j;
				getWord = true;
			}
		}
		if( !getWord && member(at(string, i), "<>;&|")) {
			char c = string.charAt(i);
			char peek = at(string, i+1);
			if( peek == c ) {
				if( peek == '<' && at(string, i+2) == '-' ) {
					i++;
				} else if( peek == '<' && at(string, i+2) == '<' ) {
					i++;
				}
				return i+2;
			} else if( peek == '&' && (c == '>' || c == '<')) {
				int j = i+2;
				while( Character.isDigit(at(string, j))) {
					j++;
				}
				if( at(string, j) == '-' ) {
					j++;
				}
				return j;
			} else if( (peek == '>' && c == '&') || (peek == '|' && c == '>')) {
				return i+2;
			} else if( peek == '(' && (c == '>' || c == '<')) {
				i += 2;
				delimopen = '(';
				delimiter = ')';
				nestdelim = 1;
			} else {
				return i+1;
			}
		}
		if( delimiter == 0 && member(at(string, i), QUOTE_CHARACTERS)) {
			delimiter = string.charAt(i++);
		}
		for (; i < string.length(); i++) {
			char c = string.charAt(i);
			if( c == '\\' && at(string, i+1) == '\n' ) {
				i++;
				continue;
			}
			if( c == '\\' && delimiter != '\'' && (delimiter != '"' || member(at(string, i+1), SLASHIFY_IN_QUOTES))) {
				i++;
				if( i >= string.length()) {
					break;
				}
				continue;
			}
			if( nestdelim > 0 && c == delimopen ) {
				nestdelim++;
				continue;
			}
			if( nestdelim > 0 && c == delimiter ) {
				nestdelim--;
				if( nestdelim == 0 ) {
					delimiter = 0;
				}
				continue;
			}
			if( delimiter != 0 && c == delimiter ) {
				delimiter = 0;
				continue;
			}
			if( nestdelim == 0 && delimiter == 0 && member(c, "<>$!@?+*") && at(string, i+1) == '(' ) {
				i++;
				if( i+1 >= string.length()) {
					break;
				}
				delimopen = '(';
				delimiter = ')';
				nestdelim = 1;
				continue;
			}
			if( delimiter == 0 && member(c, WORD_DELIMITERS)) {
				break;
			}
			if( delimiter == 0 && member(c, QUOTE_CHARACTERS)) {
				delimiter = c;
			}
		}
		return i;
	}

	/** the words of a history line, as bash splits them */
	static List<String> tokenize(String string) {
		return tokenize(string, -1, null);
	}

	private static List<String> tokenize(String string, int wind, int[] indp) {
		List<String> result = new ArrayList<>();
		if( indp != null ) {
			indp[0] = -1;
		}
		int i = 0;
		while( i < string.length()) {
			while( i < string.length() && Character.isWhitespace(string.charAt(i))) {
				i++;
			}
			if( i >= string.length() || string.charAt(i) == COMMENT ) {
				return result;
			}
			int start = i;
			i = tokenizeWord(string, start);
			if( i == start ) {
				i++;
				while( i < string.length() && member(string.charAt(i), WORD_DELIMITERS)) {
					i++;
				}
			}
			if( indp != null && wind != -1 && wind >= start && wind < i ) {
				indp[0] = result.size();
			}
			result.add(string.substring(start, Math.min(i, string.length())));
		}
		return result;
	}

	/** the word of line that has the character at ind */
	private static String findWord(String line, int ind) {
		int[] w = new int[1];
		List<String> words = tokenize(line, ind, w);
		return w[0] < 0 ? null : words.get(w[0]);
	}

	/** a command's words, as history counts them */
	static List<String> words(String command) {
		return tokenize(command);
	}
}
