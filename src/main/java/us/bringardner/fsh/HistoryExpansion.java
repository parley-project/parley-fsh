package us.bringardner.fsh;

import java.util.ArrayList;
import java.util.List;

/**
 * History expansion, as bash does it on each line typed in an interactive shell: !! (the last
 * command), !n, !-n, !string, !?string?, ^old^new, with word designators (!$, !^, !*, !:2,
 * !!:1-3 ...) and modifiers (:h :t :r :e :p :s/old/new/ :gs/old/new/ :& :q). Nothing inside
 * '...' is expanded, nor a \! or a ! before a blank, = or (.
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

	private static final class Failure extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Failure(String message) {
			super(message, null, false, false);
		}
	}

	private final List<String> history;
	private final String line;
	private int pos;
	private boolean printOnly;
	/** the word !?string? matched, for %; and the last substitution, for :& */
	private String searchWord;
	private String lastOld;
	private String lastNew;

	private HistoryExpansion(String line, List<String> history) {
		this.line = line;
		this.history = history;
	}

	/**
	 * Expand line.
	 * @param history the commands so far, oldest first (!1 is the first)
	 */
	public static Result expand(String line, List<String> history) {
		if( line.indexOf('!') < 0 && !line.startsWith("^")) {
			return new Result(line, false, false, null);
		}
		HistoryExpansion h = new HistoryExpansion(line, history);
		try {
			String ret = h.run();
			return new Result(ret, !ret.equals(line), h.printOnly, null);
		} catch (Failure f) {
			return new Result(line, false, false, f.getMessage());
		}
	}

	private String run() {
		StringBuilder out = new StringBuilder();
		if( line.startsWith("^")) {
			// ^old^new^: !!:s^old^new^
			int second = line.indexOf('^', 1);
			if( second < 0 ) {
				throw new Failure(line+": substitution failed");
			}
			int third = line.indexOf('^', second+1);
			String old = line.substring(1, second);
			String neu = third < 0 ? line.substring(second+1) : line.substring(second+1, third);
			String event = event(-1, line);
			String result = substitute(event, old, neu, false, line);
			out.append(result);
			if( third >= 0 ) {
				out.append(line.substring(third+1));
			}
			return out.toString();
		}
		boolean single = false;
		boolean dbl = false;
		while( pos < line.length()) {
			char c = line.charAt(pos);
			if( c == '\\' && !single && pos+1 < line.length()) {
				out.append(c).append(line.charAt(pos+1));
				pos += 2;
				continue;
			}
			if( c == '\'' && !dbl ) {
				single = !single;
			} else if( c == '"' && !single ) {
				dbl = !dbl;
			}
			if( c != '!' || single ) {
				out.append(c);
				pos++;
				continue;
			}
			if( pos+1 >= line.length() || " \t\n=(".indexOf(line.charAt(pos+1)) >= 0 || (dbl && line.charAt(pos+1) == '"')) {
				out.append(c);
				pos++;
				continue;
			}
			out.append(reference());
		}
		return out.toString();
	}

	/** a ! reference at pos: its event, word designator and modifiers */
	private String reference() {
		int start = pos;
		pos++;
		char c = line.charAt(pos);
		String event;
		boolean defaultWords = false;
		if( c == '!' ) {
			pos++;
			event = event(-1, "!!");
		} else if( Character.isDigit(c) || (c == '-' && pos+1 < line.length() && Character.isDigit(line.charAt(pos+1)))) {
			int s = pos;
			pos++;
			while( pos < line.length() && Character.isDigit(line.charAt(pos))) {
				pos++;
			}
			int n = Integer.parseInt(line.substring(s, pos));
			// !-2: two back; !12: number 12
			event = n < 0 ? event(n, line.substring(start, pos)) : numbered(n, line.substring(start, pos));
		} else if( c == '?' ) {
			int end = line.indexOf('?', pos+1);
			String text = end < 0 ? line.substring(pos+1) : line.substring(pos+1, end);
			pos = end < 0 ? line.length() : end+1;
			event = search(text, true, line.substring(start, pos));
			for(String w : words(event)) {
				if( w.contains(text)) {
					searchWord = w;
					break;
				}
			}
		} else if( "$^*:".indexOf(c) >= 0 ) {
			// !$ !^ !* !:2: the last command's words
			event = event(-1, "!");
			defaultWords = true;
		} else {
			int s = pos;
			while( pos < line.length() && " \t\n:;&|<>()\"'".indexOf(line.charAt(pos)) < 0 ) {
				pos++;
			}
			String text = line.substring(s, pos);
			event = search(text, false, line.substring(start, pos));
		}
		String ret = event;
		// a word designator: after :, or ^ $ * % - straight after the event
		if( pos < line.length()) {
			char d = line.charAt(pos);
			boolean colonWord = d == ':' && pos+1 < line.length() && "0123456789^$*%-".indexOf(line.charAt(pos+1)) >= 0;
			if( colonWord || (defaultWords && "^$*".indexOf(d) >= 0) || (!defaultWords && "^$*%".indexOf(d) >= 0 && d != ':')) {
				if( d == ':' ) {
					pos++;
				}
				ret = designator(event, line.substring(start, Math.min(line.length(), pos+1)));
			}
		}
		// modifiers
		while( pos+1 < line.length() && line.charAt(pos) == ':' && "htreqpxsg&".indexOf(line.charAt(pos+1)) >= 0 ) {
			pos++;
			ret = modifier(ret);
		}
		return ret;
	}

	/** the command n back (-1 the last) */
	private String event(int back, String ref) {
		int idx = history.size()+back;
		if( idx < 0 || idx >= history.size()) {
			throw new Failure(ref+": event not found");
		}
		return history.get(idx);
	}

	/** command number n (1 is the first) */
	private String numbered(int n, String ref) {
		if( n < 1 || n > history.size()) {
			throw new Failure(ref+": event not found");
		}
		return history.get(n-1);
	}

	/** the last command that starts with (or contains) text */
	private String search(String text, boolean contains, String ref) {
		for (int i = history.size()-1; i >= 0; i--) {
			String c = history.get(i);
			if( contains ? c.contains(text) : c.startsWith(text)) {
				return c;
			}
		}
		throw new Failure(ref+": event not found");
	}

	/** :0 :n :^ :$ :% :x-y :x- :-y :* :x* at pos */
	private String designator(String event, String ref) {
		List<String> words = words(event);
		int last = words.size()-1;
		char c = line.charAt(pos);
		int from;
		int to;
		if( c == '*' ) {
			pos++;
			return last < 1 ? "" : join(words, 1, last);
		}
		if( c == '%' ) {
			pos++;
			if( searchWord == null ) {
				throw new Failure(ref+": bad word specifier");
			}
			return searchWord;
		}
		if( c == '-' ) {
			from = 0;
		} else {
			from = number(last);
		}
		if( pos < line.length() && line.charAt(pos) == '*' ) {
			pos++;
			to = last;
		} else if( pos < line.length() && line.charAt(pos) == '-' ) {
			pos++;
			if( pos < line.length() && ("0123456789^$".indexOf(line.charAt(pos)) >= 0)) {
				to = number(last);
			} else {
				// x-: up to the word before the last
				to = last-1;
			}
		} else {
			to = from;
		}
		if( from < 0 || to > last || from > to+1 ) {
			throw new Failure(ref+": bad word specifier");
		}
		return join(words, from, to);
	}

	/** a word number at pos: digits, ^ (1) or $ (the last) */
	private int number(int last) {
		char c = line.charAt(pos);
		if( c == '^' ) {
			pos++;
			return 1;
		}
		if( c == '$' ) {
			pos++;
			return last;
		}
		int s = pos;
		while( pos < line.length() && Character.isDigit(line.charAt(pos))) {
			pos++;
		}
		return s == pos ? -1 : Integer.parseInt(line.substring(s, pos));
	}

	private static String join(List<String> words, int from, int to) {
		StringBuilder ret = new StringBuilder();
		for (int i = from; i <= to && i < words.size(); i++) {
			if( i > from ) {
				ret.append(' ');
			}
			ret.append(words.get(i));
		}
		return ret.toString();
	}

	/** one modifier after the : at pos */
	private String modifier(String text) {
		char m = line.charAt(pos);
		pos++;
		switch (m) {
		case 'h': {
			int slash = text.lastIndexOf('/');
			return slash < 0 ? text : slash == 0 ? "/" : text.substring(0, slash);
		}
		case 't': {
			int slash = text.lastIndexOf('/');
			return slash < 0 ? text : text.substring(slash+1);
		}
		case 'r': {
			int dot = text.lastIndexOf('.');
			return dot > text.lastIndexOf('/') ? text.substring(0, dot) : text;
		}
		case 'e': {
			int dot = text.lastIndexOf('.');
			return dot > text.lastIndexOf('/') ? text.substring(dot) : "";
		}
		case 'p':
			printOnly = true;
			return text;
		case 'q':
			return "'"+text.replace("'", "'\\''")+"'";
		case 'x': {
			StringBuilder ret = new StringBuilder();
			for(String w : words(text)) {
				ret.append(ret.length() > 0 ? " " : "").append("'").append(w.replace("'", "'\\''")).append("'");
			}
			return ret.toString();
		}
		case '&':
			if( lastOld == null ) {
				throw new Failure(":&: no previous substitution");
			}
			return substitute(text, lastOld, lastNew, false, ":&");
		case 'g':
			if( pos < line.length() && line.charAt(pos) == '&' ) {
				pos++;
				if( lastOld == null ) {
					throw new Failure(":g&: no previous substitution");
				}
				return substitute(text, lastOld, lastNew, true, ":g&");
			}
			if( pos >= line.length() || line.charAt(pos) != 's' ) {
				throw new Failure(":g: unrecognized history modifier");
			}
			pos++;
			return substitution(text, true);
		case 's':
			return substitution(text, false);
		default:
			throw new Failure(":"+m+": unrecognized history modifier");
		}
	}

	/** s/old/new/ at pos (any character for /; & in new is old) */
	private String substitution(String text, boolean global) {
		if( pos >= line.length()) {
			throw new Failure(":s: substitution failed");
		}
		char delim = line.charAt(pos++);
		int end = line.indexOf(delim, pos);
		if( end < 0 ) {
			throw new Failure(":s: substitution failed");
		}
		String old = line.substring(pos, end);
		pos = end+1;
		int end2 = line.indexOf(delim, pos);
		String neu = end2 < 0 ? line.substring(pos) : line.substring(pos, end2);
		pos = end2 < 0 ? line.length() : end2+1;
		if( old.isEmpty()) {
			old = lastOld != null ? lastOld : "";
		}
		return substitute(text, old, neu, global, ":s"+delim+old+delim+neu+delim);
	}

	private String substitute(String text, String old, String neu, boolean global, String ref) {
		lastOld = old;
		lastNew = neu;
		String replacement = neu.replace("\\&", "\u0000").replace("&", old).replace("\u0000", "&");
		int at = text.indexOf(old);
		if( old.isEmpty() || at < 0 ) {
			throw new Failure(ref.startsWith("^") ? ref+": substitution failed" : ":s"+old+": substitution failed");
		}
		if( global ) {
			return text.replace(old, replacement);
		}
		return text.substring(0, at)+replacement+text.substring(at+old.length());
	}

	/**
	 * A command's words, as history counts them: split at blanks, with quoted text kept in its
	 * word and the operators ; & | < > ( ) words of their own.
	 */
	static List<String> words(String command) {
		List<String> ret = new ArrayList<>();
		StringBuilder w = new StringBuilder();
		int n = command.length();
		for (int i = 0; i < n; i++) {
			char c = command.charAt(i);
			if( c == '\\' && i+1 < n ) {
				w.append(c).append(command.charAt(++i));
			} else if( c == '\'' || c == '"' || c == '`' ) {
				int end = command.indexOf(c, i+1);
				end = end < 0 ? n-1 : end;
				w.append(command, i, end+1);
				i = end;
			} else if( c == ' ' || c == '\t' || c == '\n' ) {
				if( w.length() > 0 ) {
					ret.add(w.toString());
					w.setLength(0);
				}
			} else if( ";&|<>()".indexOf(c) >= 0 ) {
				if( w.length() > 0 ) {
					ret.add(w.toString());
					w.setLength(0);
				}
				int j = i+1;
				while( j < n && ";&|<>".indexOf(command.charAt(j)) >= 0 && j-i < 2 ) {
					j++;
				}
				ret.add(command.substring(i, j));
				i = j-1;
			} else {
				w.append(c);
			}
		}
		if( w.length() > 0 ) {
			ret.add(w.toString());
		}
		return ret;
	}
}
