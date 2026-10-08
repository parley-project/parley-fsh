package us.bringardner.fsh;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The interactive shell's line editor, as bash's readline in its emacs mode: moving by
 * character and word, killing and yanking, undo, the history (with Ctrl-R / Ctrl-S search and
 * Alt-.), and Tab completion. It draws the line itself (lines longer than the terminal wrap).
 */
public class LineEditor {

	/** where the keys come from */
	public interface Keys {
		/** the next key (a byte, or one of NativeKeyboard's special keys), waiting for it; KEY_EOF at the end */
		int next() throws IOException;

		/** the next key if one comes within millis, else NativeKeyboard.KEY_NONE */
		int nextWithin(long millis) throws IOException;
	}

	/** completion for Tab: what the word at the cursor can become */
	public interface Completer {
		Completion.Result complete(String line, int cursor);
	}

	public static final int KEY_EOF = NativeKeyboard.KEY_EOF;
	public static final int KEY_NONE = NativeKeyboard.KEY_NONE;

	// the keys that are not characters, after an escape sequence is read
	private static final int K_UP = NativeKeyboard.UP, K_DOWN = NativeKeyboard.DN, K_RIGHT = NativeKeyboard.RT,
			K_LEFT = NativeKeyboard.LF, K_HOME = NativeKeyboard.HOME, K_END = NativeKeyboard.END,
			K_DELETE = NativeKeyboard.DELETE, K_PAGE_UP = NativeKeyboard.PAGE_UP, K_PAGE_DOWN = NativeKeyboard.PAGE_DOWN;
	/** Alt (Meta) and a key: META+c */
	private static final int META = 0x10000;
	private static final int K_CTRL_LEFT = 600, K_CTRL_RIGHT = 601;

	private final Keys keys;
	private final PrintStream out;
	private final List<String> history;
	private Completer completer;
	private int columns = 80;

	private StringBuilder buf = new StringBuilder();
	private int pos;
	private String prompt = "";
	/** the cursor's row below the line's first row, as last drawn */
	private int cursorRow;

	/** killed text, the most recent last */
	private static final List<String> killRing = new ArrayList<>();
	private boolean lastWasKill;
	/** the yank last made (for Alt-Y): where it is in the line */
	private int yankStart = -1, yankEnd = -1, yankIndex = -1;
	/** the line and cursor before each change, for undo */
	private final Deque<Object[]> undo = new ArrayDeque<>();
	private boolean lastWasInsert;
	private boolean lastWasTab;
	/** Alt-.: how far back, and what it put in */
	private int lastArgBack = -1, lastArgStart, lastArgEnd;

	/** the history line shown (history.size() is the new line), and edits of the lines visited */
	private int histIndex;
	private final Map<Integer, String> edited = new HashMap<>();

	public LineEditor(Keys keys, PrintStream out, List<String> history) {
		this.keys = keys;
		this.out = out;
		this.history = history;
	}

	public void setCompleter(Completer completer) {
		this.completer = completer;
	}

	public void setColumns(int columns) {
		this.columns = Math.max(columns, 10);
	}

	/**
	 * Read a line, after the prompt.
	 * @return the line, or null at the end of input (Ctrl-D on an empty line)
	 * @throws NativeKeyboard.LineCancelled on Ctrl-C
	 */
	public String readLine(String prompt) throws IOException {
		this.prompt = prompt == null ? "" : prompt;
		buf = new StringBuilder();
		pos = 0;
		cursorRow = 0;
		histIndex = history.size();
		edited.clear();
		undo.clear();
		out.print(this.prompt);
		out.flush();
		int lastLine = this.prompt.lastIndexOf('\n');
		if( lastLine >= 0 ) {
			// redraws start from the prompt's last line
			this.prompt = this.prompt.substring(lastLine+1);
		}
		while( true ) {
			int key = readKey();
			if( key == KEY_EOF ) {
				if( buf.length() == 0 ) {
					return null;
				}
				return accept();
			}
			if( key == KEY_NONE ) {
				continue;
			}
			boolean wasKill = lastWasKill;
			boolean wasTab = lastWasTab;
			boolean wasInsert = lastWasInsert;
			int wasArg = lastArgBack;
			lastWasKill = false;
			lastWasTab = false;
			lastWasInsert = false;
			lastArgBack = -1;
			boolean keepYank = false;
			switch (key) {
			case '\n':
			case '\r':
				return accept();
			case 1: // Ctrl-A
			case K_HOME:
				moveTo(0);
				break;
			case 5: // Ctrl-E
			case K_END:
				moveTo(buf.length());
				break;
			case 2: // Ctrl-B
			case K_LEFT:
				moveTo(pos-1);
				break;
			case 6: // Ctrl-F
			case K_RIGHT:
				moveTo(pos+1);
				break;
			case META+'b':
			case K_CTRL_LEFT:
				moveTo(wordStart(pos));
				break;
			case META+'f':
			case K_CTRL_RIGHT:
				moveTo(wordEnd(pos));
				break;
			case 4: // Ctrl-D: the end on an empty line, else delete the character at the cursor
				if( buf.length() == 0 ) {
					out.println();
					out.flush();
					return null;
				}
				deleteRange(pos, pos+1, false);
				break;
			case K_DELETE:
				deleteRange(pos, pos+1, false);
				break;
			case 8: // Ctrl-H
			case 127: // DEL
				deleteRange(pos-1, pos, false);
				break;
			case 11: // Ctrl-K
				kill(pos, buf.length(), wasKill, true);
				break;
			case 21: // Ctrl-U
				kill(0, pos, wasKill, false);
				break;
			case 23: // Ctrl-W: the word before, up to white space
				kill(unixWordStart(pos), pos, wasKill, false);
				break;
			case META+'d':
				kill(pos, wordEnd(pos), wasKill, true);
				break;
			case META+127:
			case META+8:
				kill(wordStart(pos), pos, wasKill, false);
				break;
			case 25: // Ctrl-Y
				yank();
				keepYank = true;
				break;
			case META+'y':
				keepYank = yankPop();
				break;
			case 20: // Ctrl-T
				transpose();
				break;
			case META+'t':
				transposeWords();
				break;
			case META+'u':
			case META+'l':
			case META+'c':
				changeCase(key-META);
				break;
			case 31: // Ctrl-_
				undo();
				break;
			case META+'r':
				revertLine();
				break;
			case 12: // Ctrl-L
				out.print("\033[H\033[2J");
				out.print(prompt);
				cursorRow = 0;
				redraw();
				break;
			case 16: // Ctrl-P
			case K_UP:
				historyMove(histIndex-1);
				break;
			case 14: // Ctrl-N
			case K_DOWN:
				historyMove(histIndex+1);
				break;
			case META+'<':
				historyMove(0);
				break;
			case META+'>':
				historyMove(history.size());
				break;
			case META+'.':
			case META+'_':
				lastArgument(wasArg);
				break;
			case 18: // Ctrl-R
				search(true);
				break;
			case 19: // Ctrl-S
				search(false);
				break;
			case 22: { // Ctrl-V: the next key as it is
				int next = keys.next();
				if( next >= 0 && next < 256 ) {
					insert(String.valueOf((char) next), false);
				}
				break;
			}
			case '\t':
				complete(wasTab);
				lastWasTab = true;
				break;
			case 7: // Ctrl-G
				bell();
				break;
			case NativeKeyboard.CTRL_C:
				// as bash: the line is dropped
				moveTo(buf.length());
				out.print("^C\n");
				out.flush();
				throw new NativeKeyboard.LineCancelled();
			case NativeKeyboard.CTRL_Z:
			case NativeKeyboard.CTRL_BACKSLASH:
			case K_PAGE_UP:
			case K_PAGE_DOWN:
				break;
			default:
				if( key >= 32 && key < META ) {
					insert(character(key), wasInsert);
					lastWasInsert = true;
				} else {
					bell();
				}
			}
			if( !keepYank ) {
				yankStart = yankEnd = -1;
			}
		}
	}

	private String accept() {
		moveTo(buf.length());
		out.print("\n");
		out.flush();
		return buf.toString();
	}

	// ------------------------------------------------------------------ keys

	/** the next key, with escape sequences (arrows, Alt-x ...) read as one key */
	private int readKey() throws IOException {
		if( pushedBack != KEY_NONE ) {
			// a key a search ended with
			int k = pushedBack;
			pushedBack = KEY_NONE;
			return k;
		}
		int key = keys.next();
		if( key != 27 ) {
			return key;
		}
		int next = keys.nextWithin(50);
		if( next == KEY_NONE ) {
			// Escape alone
			return 27+META;
		}
		if( next == '[' || next == 'O' ) {
			return sequence(next);
		}
		if( next == 27 ) {
			return 27+META;
		}
		return META+next;
	}

	/** ESC [ ... or ESC O ...: the key it is */
	private int sequence(int intro) throws IOException {
		StringBuilder params = new StringBuilder();
		while( true ) {
			int c = keys.nextWithin(50);
			if( c == KEY_NONE || c == KEY_EOF ) {
				return KEY_NONE;
			}
			if( c >= 0x40 && c <= 0x7e ) {
				String p = params.toString();
				switch (c) {
				case 'A': return K_UP;
				case 'B': return K_DOWN;
				case 'C': return p.endsWith(";5") ? K_CTRL_RIGHT : p.endsWith(";3") ? META+'f' : K_RIGHT;
				case 'D': return p.endsWith(";5") ? K_CTRL_LEFT : p.endsWith(";3") ? META+'b' : K_LEFT;
				case 'H': return K_HOME;
				case 'F': return K_END;
				case '~':
					switch (p) {
					case "1": case "7": return K_HOME;
					case "4": case "8": return K_END;
					case "3": return K_DELETE;
					case "5": return K_PAGE_UP;
					case "6": return K_PAGE_DOWN;
					default: return KEY_NONE;
					}
				default:
					return KEY_NONE;
				}
			}
			params.append((char) c);
		}
	}

	/** a typed character (UTF-8: a first byte and the bytes after it) */
	private String character(int first) throws IOException {
		int more = first >= 0xf0 ? 3 : first >= 0xe0 ? 2 : first >= 0xc0 ? 1 : 0;
		if( first < 0x80 || first > 255 ) {
			return String.valueOf((char) first);
		}
		byte [] bytes = new byte[more+1];
		bytes[0] = (byte) first;
		int n = 1;
		for(; n <= more; n++) {
			int b = keys.nextWithin(50);
			if( b < 0x80 || b > 0xbf ) {
				break;
			}
			bytes[n] = (byte) b;
		}
		return new String(bytes, 0, n, StandardCharsets.UTF_8);
	}

	// ------------------------------------------------------------------ editing

	private void saveUndo() {
		undo.push(new Object[] {buf.toString(), pos});
	}

	private void insert(String s, boolean continuing) {
		if( !continuing ) {
			saveUndo();
		}
		boolean atEnd = pos == buf.length();
		buf.insert(pos, s);
		pos += s.length();
		int end = width(prompt)+width(buf.toString());
		if( atEnd && end % columns != 0 && (end-width(s))/columns == end/columns && s.indexOf('\t') < 0 ) {
			// typed at the end: only what is new (as readline does)
			out.print(s);
			out.flush();
			cursorRow = end/columns;
			return;
		}
		redraw();
	}

	private void deleteRange(int from, int to, boolean save) {
		from = Math.max(0, from);
		to = Math.min(buf.length(), to);
		if( from >= to ) {
			return;
		}
		saveUndo();
		buf.delete(from, to);
		pos = from;
		redraw();
	}

	/** kill from..to; after another kill it adds to that one (after it, or before for back) */
	private void kill(int from, int to, boolean append, boolean forward) {
		from = Math.max(0, from);
		to = Math.min(buf.length(), to);
		if( from >= to ) {
			lastWasKill = append;
			return;
		}
		String text = buf.substring(from, to);
		if( append && !killRing.isEmpty()) {
			String last = killRing.remove(killRing.size()-1);
			killRing.add(forward ? last+text : text+last);
		} else {
			killRing.add(text);
			if( killRing.size() > 60 ) {
				killRing.remove(0);
			}
		}
		deleteRange(from, to, true);
		lastWasKill = true;
	}

	private void yank() {
		if( killRing.isEmpty()) {
			bell();
			return;
		}
		yankIndex = killRing.size()-1;
		yankStart = pos;
		insert(killRing.get(yankIndex), false);
		yankEnd = pos;
	}

	/** Alt-Y after a yank: the kill before the one yanked instead */
	private boolean yankPop() {
		if( yankStart < 0 || killRing.size() < 2 ) {
			bell();
			return false;
		}
		yankIndex = (yankIndex-1+killRing.size()) % killRing.size();
		buf.delete(yankStart, yankEnd);
		String text = killRing.get(yankIndex);
		buf.insert(yankStart, text);
		yankEnd = yankStart+text.length();
		pos = yankEnd;
		redraw();
		return true;
	}

	private void transpose() {
		if( buf.length() < 2 || pos == 0 ) {
			bell();
			return;
		}
		saveUndo();
		int at = pos == buf.length() ? pos-1 : pos;
		char a = buf.charAt(at-1);
		buf.setCharAt(at-1, buf.charAt(at));
		buf.setCharAt(at, a);
		pos = at+1;
		redraw();
	}

	private void transposeWords() {
		int end2 = wordEnd(pos);
		int start2 = wordStart(end2);
		int start1 = wordStart(start2);
		int end1 = wordEnd(start1);
		if( start1 >= start2 || end1 > start2 ) {
			bell();
			return;
		}
		saveUndo();
		String w1 = buf.substring(start1, end1);
		String w2 = buf.substring(start2, end2);
		buf.replace(start2, end2, w1);
		buf.replace(start1, end1, w2);
		pos = end2;
		redraw();
	}

	private void changeCase(int how) {
		int end = wordEnd(pos);
		int start = pos;
		while( start < end && !isWordChar(buf.charAt(start))) {
			start++;
		}
		if( start >= end ) {
			moveTo(end);
			return;
		}
		saveUndo();
		String w = buf.substring(start, end);
		String changed = switch (how) {
		case 'u' -> w.toUpperCase();
		case 'l' -> w.toLowerCase();
		default -> w.substring(0, 1).toUpperCase()+w.substring(1).toLowerCase();
		};
		buf.replace(start, end, changed);
		pos = end;
		redraw();
	}

	private void undo() {
		if( undo.isEmpty()) {
			bell();
			return;
		}
		Object[] u = undo.pop();
		buf = new StringBuilder((String) u[0]);
		pos = (Integer) u[1];
		redraw();
	}

	private void revertLine() {
		if( undo.isEmpty()) {
			return;
		}
		Object[] first = undo.getLast();
		undo.clear();
		buf = new StringBuilder((String) first[0]);
		pos = buf.length();
		redraw();
	}

	// ------------------------------------------------------------------ words

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c);
	}

	/** the start of the word before pos (readline's backward-word) */
	private int wordStart(int at) {
		int i = Math.min(at, buf.length());
		while( i > 0 && !isWordChar(buf.charAt(i-1))) {
			i--;
		}
		while( i > 0 && isWordChar(buf.charAt(i-1))) {
			i--;
		}
		return i;
	}

	/** the end of the word at or after pos (readline's forward-word) */
	private int wordEnd(int at) {
		int i = Math.max(at, 0);
		while( i < buf.length() && !isWordChar(buf.charAt(i))) {
			i++;
		}
		while( i < buf.length() && isWordChar(buf.charAt(i))) {
			i++;
		}
		return i;
	}

	/** Ctrl-W: back to white space */
	private int unixWordStart(int at) {
		int i = at;
		while( i > 0 && Character.isWhitespace(buf.charAt(i-1))) {
			i--;
		}
		while( i > 0 && !Character.isWhitespace(buf.charAt(i-1))) {
			i--;
		}
		return i;
	}

	// ------------------------------------------------------------------ history

	private void historyMove(int to) {
		if( to < 0 || to > history.size()) {
			bell();
			return;
		}
		edited.put(histIndex, buf.toString());
		histIndex = to;
		String line = edited.containsKey(to) ? edited.get(to) : to == history.size() ? "" : history.get(to);
		buf = new StringBuilder(line);
		pos = buf.length();
		redraw();
	}

	/** Alt-.: the last word of the command before (again: of the one before that) */
	private void lastArgument(int back) {
		int n = back < 0 ? 1 : back+1;
		int index = history.size()-n;
		if( index < 0 ) {
			bell();
			lastArgBack = back;
			return;
		}
		List<String> words = words(history.get(index));
		String word = words.isEmpty() ? "" : words.get(words.size()-1);
		if( back >= 0 ) {
			buf.delete(lastArgStart, lastArgEnd);
			pos = lastArgStart;
		} else {
			saveUndo();
		}
		lastArgStart = pos;
		buf.insert(pos, word);
		pos += word.length();
		lastArgEnd = pos;
		lastArgBack = n;
		redraw();
	}

	/** a command's words, with their quotes (as history expansion splits them) */
	static List<String> words(String line) {
		List<String> ret = new ArrayList<>();
		StringBuilder w = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if( quote != 0 ) {
				w.append(c);
				if( c == quote ) {
					quote = 0;
				} else if( c == '\\' && quote == '"' && i+1 < line.length()) {
					w.append(line.charAt(++i));
				}
			} else if( c == '\'' || c == '"' ) {
				quote = c;
				w.append(c);
			} else if( c == '\\' && i+1 < line.length()) {
				w.append(c).append(line.charAt(++i));
			} else if( Character.isWhitespace(c)) {
				if( w.length() > 0 ) {
					ret.add(w.toString());
					w.setLength(0);
				}
			} else {
				w.append(c);
			}
		}
		if( w.length() > 0 ) {
			ret.add(w.toString());
		}
		return ret;
	}

	/**
	 * Ctrl-R (back) and Ctrl-S (forward): the history line with what is typed, shown as
	 * (reverse-i-search)`text': line. Enter runs it; Ctrl-G puts the line back; another
	 * editing key takes the line found and does what it does.
	 */
	private void search(boolean back) throws IOException {
		String saved = buf.toString();
		int savedPos = pos;
		int savedIndex = histIndex;
		StringBuilder text = new StringBuilder();
		int found = histIndex;
		int at = -1;
		boolean failed = false;
		String savedPrompt = prompt;
		while( true ) {
			prompt = (failed ? "(failed " : "(")+(back ? "reverse" : "i")+"-search)`"+text+"': ";
			redraw();
			int key = readKey();
			if( key == 18 || key == 19 ) {
				back = key == 18;
				if( text.length() == 0 ) {
					continue;
				}
				int[] next = find(text.toString(), back, back ? found-1 : found+1, back);
				failed = next == null;
				if( next != null ) {
					found = next[0];
					at = next[1];
					show(found, at);
				} else {
					bell();
				}
				continue;
			}
			if( key == 127 || key == 8 ) {
				if( text.length() > 0 ) {
					text.setLength(text.length()-1);
				}
				int[] next = text.length() == 0 ? null : find(text.toString(), back, savedIndex-(back ? 1 : 0), back);
				failed = text.length() > 0 && next == null;
				if( next != null ) {
					found = next[0];
					at = next[1];
					show(found, at);
				}
				continue;
			}
			if( key == 7 || key == NativeKeyboard.CTRL_C ) {
				// back to the line as it was
				prompt = savedPrompt;
				buf = new StringBuilder(saved);
				pos = savedPos;
				histIndex = savedIndex;
				redraw();
				if( key == NativeKeyboard.CTRL_C ) {
					out.print("^C\n");
					out.flush();
					throw new NativeKeyboard.LineCancelled();
				}
				return;
			}
			if( key >= 32 && key < 127 || key >= 128 && key < 256 ) {
				text.append(character(key));
				int[] next = find(text.toString(), back, found, back);
				failed = next == null;
				if( next != null ) {
					found = next[0];
					at = next[1];
					show(found, at);
				} else {
					bell();
				}
				continue;
			}
			// another key: the line found stays, and the key does what it does
			prompt = savedPrompt;
			histIndex = found;
			redraw();
			if( key == '\n' || key == '\r' ) {
				pushBack('\n');
			} else if( key != 27+META ) {
				pushBack(key);
			}
			return;
		}
	}

	private int pushedBack = KEY_NONE;

	private void pushBack(int key) {
		pushedBack = key;
	}

	/** the history line (from index on, going back or forward) with text: {index, where}; null if none */
	private int[] find(String text, boolean back, int from, boolean backward) {
		if( from > history.size()) {
			from = history.size();
		}
		for(int i = from; back ? i >= 0 : i < history.size(); i += back ? -1 : 1) {
			String line = i == history.size() ? "" : history.get(i);
			int at = back ? line.lastIndexOf(text) : line.indexOf(text);
			if( at >= 0 && i < history.size()) {
				return new int[] {i, at};
			}
		}
		return null;
	}

	private void show(int index, int at) {
		buf = new StringBuilder(history.get(index));
		pos = at;
	}

	// ------------------------------------------------------------------ completion

	private void complete(boolean again) {
		if( completer == null ) {
			insert("\t", false);
			return;
		}
		Completion.Result r = completer.complete(buf.toString(), pos);
		if( r == null || r.candidates.isEmpty()) {
			bell();
			return;
		}
		String current = buf.substring(r.start, pos);
		if( r.candidates.size() == 1 ) {
			Completion.Candidate c = r.candidates.get(0);
			replaceWord(r.start, c.text+c.suffix);
			return;
		}
		String common = r.commonPrefix();
		if( common.length() > current.length() ) {
			replaceWord(r.start, common);
			return;
		}
		if( !again ) {
			bell();
			return;
		}
		list(r);
	}

	private void replaceWord(int start, String text) {
		saveUndo();
		buf.replace(start, pos, text);
		pos = start+text.length();
		redraw();
	}

	/** the second Tab: the possibilities, in columns, then the line again */
	private void list(Completion.Result r) {
		moveTo(buf.length());
		out.print("\n");
		List<String> shown = new ArrayList<>();
		int width = 0;
		for(Completion.Candidate c : r.candidates) {
			shown.add(c.display);
			width = Math.max(width, c.display.length());
		}
		width += 2;
		int perRow = Math.max(1, (columns+2)/width);
		int rows = (shown.size()+perRow-1)/perRow;
		for (int row = 0; row < rows; row++) {
			StringBuilder line = new StringBuilder();
			for (int col = 0; col < perRow; col++) {
				// down the columns, as bash lists them
				int i = col*rows+row;
				if( i < shown.size()) {
					String s = shown.get(i);
					line.append(s);
					if( (col+1)*rows+row < shown.size()) {
						line.append(" ".repeat(width-s.length()));
					}
				}
			}
			out.print(line.toString().stripTrailing()+"\n");
		}
		out.print(prompt);
		cursorRow = 0;
		redraw();
	}

	// ------------------------------------------------------------------ drawing

	private void bell() {
		out.print((char) 7);
		out.flush();
	}

	private void moveTo(int to) {
		to = Math.max(0, Math.min(buf.length(), to));
		if( to != pos ) {
			pos = to;
			redraw();
		}
	}

	/** the columns text takes on the terminal (escape sequences take none) */
	static int width(String text) {
		int ret = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == 27 ) {
				// ESC [ ... letter
				i++;
				if( i < text.length() && text.charAt(i) == '[' ) {
					while( i+1 < text.length() && !(text.charAt(i+1) >= 0x40 && text.charAt(i+1) <= 0x7e)) {
						i++;
					}
					i++;
				}
				continue;
			}
			if( Character.isLowSurrogate(c) || c < 32 ) {
				continue;
			}
			ret++;
		}
		return ret;
	}

	/** draw the prompt's last line and the line again, and put the cursor where it is */
	private void redraw() {
		StringBuilder s = new StringBuilder();
		if( cursorRow > 0 ) {
			s.append("\033[").append(cursorRow).append('A');
		}
		s.append('\r');
		s.append(prompt).append(buf);
		s.append("\033[J");
		int start = width(prompt);
		int end = start+width(buf.toString());
		if( end > 0 && end % columns == 0 ) {
			// the cursor stays on the last column until something is written: go to the next row
			s.append(" \r");
		}
		int endRow = end/columns;
		int at = start+width(buf.substring(0, pos));
		int row = at/columns;
		int col = at%columns;
		if( endRow > row ) {
			s.append("\033[").append(endRow-row).append('A');
		}
		s.append('\r');
		if( col > 0 ) {
			s.append("\033[").append(col).append('C');
		}
		cursorRow = row;
		out.print(s);
		out.flush();
	}

	/** keys from a list, for tests (and keys pushed back) */
	public static Keys keys(int ... list) {
		return new Keys() {
			int i;

			@Override
			public int next() {
				return i < list.length ? list[i++] : KEY_EOF;
			}

			@Override
			public int nextWithin(long millis) {
				return i < list.length ? list[i++] : KEY_NONE;
			}
		};
	}

	/** the keys of text (its bytes), for tests */
	public static int[] typed(String text) {
		byte [] b = text.getBytes(StandardCharsets.UTF_8);
		int [] ret = new int[b.length];
		for (int i = 0; i < b.length; i++) {
			ret[i] = b[i] & 0xff;
		}
		return ret;
	}
}
