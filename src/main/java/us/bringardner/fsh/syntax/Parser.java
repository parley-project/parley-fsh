package us.bringardner.fsh.syntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import us.bringardner.fsh.syntax.Ast.AndOr;
import us.bringardner.fsh.syntax.Ast.Arith;
import us.bringardner.fsh.syntax.Ast.ArithFor;
import us.bringardner.fsh.syntax.Ast.Assignment;
import us.bringardner.fsh.syntax.Ast.BraceGroup;
import us.bringardner.fsh.syntax.Ast.Case;
import us.bringardner.fsh.syntax.Ast.CaseClause;
import us.bringardner.fsh.syntax.Ast.Command;
import us.bringardner.fsh.syntax.Ast.Cond;
import us.bringardner.fsh.syntax.Ast.For;
import us.bringardner.fsh.syntax.Ast.FunctionDef;
import us.bringardner.fsh.syntax.Ast.HereDoc;
import us.bringardner.fsh.syntax.Ast.If;
import us.bringardner.fsh.syntax.Ast.Item;
import us.bringardner.fsh.syntax.Ast.Loop;
import us.bringardner.fsh.syntax.Ast.Pipeline;
import us.bringardner.fsh.syntax.Ast.Redirect;
import us.bringardner.fsh.syntax.Ast.Select;
import us.bringardner.fsh.syntax.Ast.Sequence;
import us.bringardner.fsh.syntax.Ast.SimpleCommand;
import us.bringardner.fsh.syntax.Ast.Subshell;

/**
 * Reads a script into its syntax tree (see Ast), the way bash does: a tokenizer that knows
 * where it is (reserved words count only where a command starts, here-documents are read at the
 * next newline, [[ ]] has its own operators) and a recursive-descent parser for bash's grammar.
 * Words are kept whole (see Word); $( ) and <( ) inside them are parsed as commands.
 * <p>
 * It only reads: nothing is expanded or run.
 */
public final class Parser {

	/** how fragment reads its text */
	public enum Fragment {
		/** as a word: quotes, \, $ and ` count (blanks and ; | & < > ( ) are text) */
		WORD,
		/** as the inside of "...": only \ (before $ ` " \), $ and ` count */
		QUOTED,
		/** as the word in "${x:-word}": as QUOTED, but "..." quotes again, \} is } and $'..' is read (extquote) */
		QUOTED_PARAMETER,
		/** as the word of ${x:-word} in a here-document: QUOTED_PARAMETER without $'..' */
		HERE_PARAMETER,
		/** as the string in "${x/pattern/string}": as QUOTED_PARAMETER, and \& is & */
		QUOTED_REPLACEMENT,
		/** "..." inside "${x:-word}": the quotes are removed and \ quotes any character, as bash's */
		QUOTED_AGAIN,
		/** as an unquoted here-document: only \ (before $ ` \), $ and ` count */
		HERE_DOCUMENT,
		/** as WORD, and <(cmd) >(cmd) are process substitutions (the word of an unquoted ${x:-word}) */
		WORD_WITH_PROCESSES
	}

	/**
	 * Read text that is already known to be one piece (the word in ${x:-word}, a ${x#pattern},
	 * a here-document's body) as a word.
	 */
	public static Word fragment(String text, Fragment mode) {
		Parser p = new Parser(text);
		p.commandStart = false;
		p.aliasing = false;
		Word w;
		if( mode == Fragment.WORD || mode == Fragment.WORD_WITH_PROCESSES ) {
			p.fragment = true;
			p.fragmentProcesses = mode == Fragment.WORD_WITH_PROCESSES;
			w = p.readWord(false);
		} else {
			w = new Word();
			w.parts.addAll(p.doubleParts(mode));
			w.end = text.length();
			w.raw = text;
		}
		w.line = 1;
		return w;
	}

	/**
	 * The index of the first c in text at or after from that is not quoted or inside an
	 * expansion (the / that ends the pattern of ${x/pattern/string}), or -1.
	 */
	public static int indexOfUnquoted(String text, char c, int from) {
		Parser p = new Parser(text);
		for (int i = from; i < text.length(); ) {
			char d = text.charAt(i);
			if( d == c ) {
				return i;
			}
			if( d == '$' && i+1 < text.length()) {
				char n = text.charAt(i+1);
				if( n == '(' ) {
					i = p.skipCommandSub(i);
					continue;
				}
				if( n == '{' ) {
					i = p.braceClose(i+2)+1;
					continue;
				}
				if( n == '\'' ) {
					int j = i+2;
					while( j < text.length() && text.charAt(j) != '\'' ) {
						j += text.charAt(j) == '\\' ? 2 : 1;
					}
					i = j+1;
					continue;
				}
			}
			int q = p.skipQuoted(i);
			i = q != i ? q : i+1;
		}
		return -1;
	}

	/** parse a whole script */
	public static Sequence parse(String source) {
		return new Parser(source).script();
	}

	/** parse a whole script whose first line is line firstLine of the input (read a command at a time) */
	public static Sequence parse(String source, int firstLine) {
		Parser p = new Parser(source);
		p.firstLine = firstLine;
		return p.script();
	}

	/** the line of the input the text starts on */
	private int firstLine = 1;

	/**
	 * Text read a line of commands at a time, as bash reads a script, -c's command, eval's and a
	 * sourced file: each line is parsed after the one before it has run, so an alias it defined
	 * is used, and a syntax error stops only what comes after it.
	 */
	public static final class Reader {
		private final Parser p;

		public Reader(String source, int firstLine) {
			p = new Parser(source);
			p.firstLine = firstLine;
		}

		/** the next line's commands (a compound command may go on for more lines), or null at the end */
		public Sequence next() {
			p.skipNewlines();
			if( p.peek().kind == Kind.EOF ) {
				return null;
			}
			Sequence ret = p.oneLine();
			ret.source = p.src;
			ret.warnings.addAll(p.warnings);
			p.warnings.clear();
			return ret;
		}

		/** after a recoverable syntax error: go on from the next line */
		public void skipLine() {
			int nl = p.src.indexOf('\n', p.pos);
			p.pos = nl < 0 ? p.src.length() : nl+1;
			p.cur = null;
			p.back = null;
			p.commandStart = true;
			p.afterRedirect = false;
		}
	}

	/** the commands up to the end of a line (the newline is read too) */
	private Sequence oneLine() {
		Sequence seq = new Sequence();
		seq.start = peek().start;
		seq.line = lineOf(seq.start);
		seq.end = seq.start;
		while( true ) {
			Token t = peek();
			if( t.kind == Kind.EOF ) {
				break;
			}
			if( t.kind == Kind.NEWLINE ) {
				take();
				break;
			}
			Item item = new Item();
			item.command = andOr();
			seq.items.add(item);
			seq.end = item.command.end;
			t = peek();
			if( isOp(t, "&")) {
				take();
				item.background = true;
			} else if( isOp(t, ";")) {
				take();
			} else if( t.kind == Kind.NEWLINE ) {
				take();
				break;
			} else if( t.kind != Kind.EOF ) {
				throw unexpected(t);
			}
		}
		return seq;
	}

	private Sequence script() {
		Sequence ret = list(Set.of(), Set.of());
		Token t = peek();
		if( t.kind != Kind.EOF ) {
			throw unexpected(t);
		}
		ret.source = src;
		ret.warnings.addAll(warnings);
		return ret;
	}

	/** a here-document reached the end of the text before its word */
	private boolean hereDocumentOpen;

	/**
	 * Whether code is whole, or more lines are needed to finish it, as an interactive shell
	 * decides when to show PS2: an if with no fi, an open quote or $( ), a here-document with no
	 * end yet, or a line that ends with |, &&, || or \. (A real syntax error is whole: it is
	 * reported.)
	 */
	public static boolean isComplete(String code) {
		int backslashes = 0;
		for (int i = code.length()-1; i >= 0 && code.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		if( backslashes % 2 == 1 ) {
			return false;
		}
		Parser p = new Parser(code);
		try {
			p.script();
		} catch (SyntaxError e) {
			return !e.endOfInput;
		}
		return !p.hereDocumentOpen && p.pendingHereDocs.isEmpty();
	}

	// ------------------------------------------------------------------ tokens

	private enum Kind { WORD, OP, NEWLINE, IO_NUMBER, IO_VAR, EOF }

	private static final class Token {
		Kind kind;
		String op;
		Word word;
		int number;
		String name;
		int start;
		int end;
	}

	/** longest first */
	private static final String [] OPERATORS = {
		"&>>", "<<<", "<<-", ";;&",
		"&&", "||", ";;", ";&", "|&", "&>", ">>", ">|", ">&", "<<", "<>", "<&",
		"<", ">", "&", "|", ";", "(", ")"
	};

	private static final Set<String> REDIRECT_OPS = Set.of("<", ">", ">>", ">|", "<>", "<&", ">&", "&>", "&>>", "<<", "<<-", "<<<");

	/** commands whose name=value arguments are assignments (not split, and may be arrays) */
	private static final Set<String> DECLARATIONS = Set.of("declare", "typeset", "local", "export", "readonly");

	/**
	 * commands whose name=( ... ) arguments bash reads whole (eval a=(1 2) passes one word,
	 * a=(1 2), to eval): the ( ... ) is part of the word
	 */
	private static final Set<String> WORD_ARRAYS = Set.of("eval", "let", "alias");

	/** the binary operators of [[ ]] (except =~, whose operand is read as a regular expression) */
	private static final Set<String> BINARY_TESTS = Set.of("=", "==", "!=", "<", ">", "-eq", "-ne", "-lt", "-le", "-gt", "-ge", "-ef", "-nt", "-ot");

	/** reserved words after which a command starts */
	/** bash's reserved words */
	private static final Set<String> RESERVED = Set.of("if", "then", "else", "elif", "fi", "case", "esac", "for", "select",
			"while", "until", "do", "done", "in", "function", "time", "{", "}", "!", "[[", "]]", "coproc");

	private static final Set<String> STARTERS = Set.of("if", "then", "else", "elif", "do", "while", "until", "{", "!", "time", "coproc");

	/** reserved words that end a list (they may not start a command) */
	private static final Set<String> CLOSERS = Set.of("then", "elif", "else", "fi", "do", "done", "esac", "}", "in", "]]");

	/** the text (an alias's value replaces its word in it) */
	private String src;
	private int pos;
	private final int [] lineStarts;

	/** the value of the alias a word names, or null: the shell's aliases, when the text is read */
	public static volatile java.util.function.Function<String,String> aliases = name -> null;
	/** words are checked for aliases (not in a fragment) */
	private boolean aliasing = true;
	/** the aliases whose values are being read: {name, the end of its value} */
	private final List<Object []> activeAliases = new ArrayList<>();
	/** an alias's value ended with a blank: the next word from here is checked too (-1: none) */
	private int aliasNextAt = -1;
	/** an alias of a word after one that ended with a blank: its value's first word starts here */
	private int aliasAgainAt = -1;
	/** the next token, read when first needed (so a here-document is known before its newline is read) */
	private Token cur;
	/**
	 * the next word is where a command starts (or after assignments and redirects there): name[
	 * then reads to its ], blanks and all, as bash does (a[i + 1]=v)
	 */
	private boolean commandStart = true;
	/** reading the patterns of a case clause (where name[ is not a subscript) */
	private boolean casePattern;
	/** where the compound commands being read start (offset, line), innermost last */
	private final List<int []> open = new ArrayList<>();
	/** the ) or } of each $( ), <( ), ${ } being read, innermost last */
	private final List<String> closers = new ArrayList<>();
	/** how many ${ list; } are being read */
	private int functionSubs;
	/** reading a piece of text as one word (see fragment): nothing ends it but the end */
	private boolean fragment;
	/** in that fragment, <(cmd) is a process substitution */
	private boolean fragmentProcesses;
	/** reading a word of name=( ... ) */
	private boolean arrayElement;
	/** the next word is the target of a redirect (it does not change commandStart) */
	private boolean afterRedirect;
	/** here-documents whose bodies start after the next newline */
	private final List<Redirect> pendingHereDocs = new ArrayList<>();

	private Parser(String source) {
		this.src = source;
		List<Integer> starts = new ArrayList<>();
		starts.add(0);
		for (int i = 0; i < source.length(); i++) {
			if( source.charAt(i) == '\n' ) {
				starts.add(i+1);
			}
		}
		lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
	}

	private int lineOf(int offset) {
		int i = Arrays.binarySearch(lineStarts, offset);
		return (i >= 0 ? i+1 : -i-1)+firstLine-1;
	}

	private char ch(int i) {
		return i < src.length() ? src.charAt(i) : '\0';
	}

	private boolean atEnd(int i) {
		return i >= src.length();
	}

	private Token peek() {
		if( back != null ) {
			return back;
		}
		if( cur == null ) {
			cur = scan();
		}
		return cur;
	}

	private Token take() {
		if( back != null ) {
			Token t = back;
			back = null;
			return t;
		}
		Token t = peek();
		cur = null;
		return t;
	}

	/** a token taken and given back (coproc looks two ahead): it comes before cur */
	private Token back;

	/** spaces, tabs, \newline and comments */
	private void skipBlanks() {
		while( !atEnd(pos)) {
			char c = ch(pos);
			if( c == ' ' || c == '\t' || c == '\r' ) {
				pos++;
			} else if( c == '\\' && ch(pos+1) == '\n' ) {
				pos += 2;
			} else if( c == '#' ) {
				while( !atEnd(pos) && ch(pos) != '\n' ) {
					pos++;
				}
			} else {
				break;
			}
		}
	}

	/** the next token, noting whether the one after it starts a command (see commandStart) */
	private Token scan() {
		boolean redirectTarget = afterRedirect;
		boolean atStart = commandStart;
		afterRedirect = false;
		Token t = scanToken();
		if( t.kind == Kind.WORD && aliasing && !casePattern ) {
			boolean next = aliasNextAt >= 0 && t.start >= aliasNextAt;
			if( next ) {
				aliasNextAt = -1;
			}
			boolean again = aliasAgainAt >= 0 && t.start >= aliasAgainAt;
			aliasAgainAt = -1;
			String value = (atStart && !redirectTarget) || next || again ? alias(t) : null;
			if( value != null ) {
				// as bash: the alias's value is read in place of the word
				splice(t, value);
				if( next || again ) {
					// (its first word is checked too: foo='echo ', bar=baz, baz=quux: foo bar is echo quux)
					aliasAgainAt = t.start;
				}
				return scan();
			}
		}
		switch (t.kind) {
		case NEWLINE:
			commandStart = true;
			break;
		case IO_NUMBER:
		case IO_VAR:
			afterRedirect = true;
			break;
		case OP:
			if( REDIRECT_OPS.contains(t.op)) {
				afterRedirect = true;
			} else {
				commandStart = true;
			}
			break;
		case WORD:
			if( !redirectTarget ) {
				// x=1 a[i]=2 and if a[i]=2 still start a command, echo x=1 a[i does not
				commandStart = commandStart && (assignmentLength(t.word) > 0 || (t.word.isPlain() && STARTERS.contains(t.word.plainText())));
			}
			break;
		default:
		}
		return t;
	}

	/** the value of the alias word t names, unless it is that alias's own value being read */
	private String alias(Token t) {
		if( !t.word.isPlain()) {
			return null;
		}
		String name = t.word.plainText();
		if( name.isEmpty() || name.indexOf('=') >= 0 || name.indexOf('/') >= 0 ) {
			return null;
		}
		if( RESERVED.contains(name) && posixMode.getAsBoolean()) {
			// posix mode: a reserved word is not an alias
			return null;
		}
		activeAliases.removeIf(a -> (Integer) a[1] <= t.start);
		for(Object [] a : activeAliases) {
			if( a[0].equals(name)) {
				return null;
			}
		}
		String value = aliases.apply(name);
		if( value == null ) {
			return null;
		}
		activeAliases.add(new Object[] {name, t.end});
		return value;
	}

	/** the text of word t is replaced by value; the next token is read from its start */
	private void splice(Token t, String alias) {
		String value = alias;
		if( !value.isEmpty() && Character.isDigit(value.charAt(value.length()-1)) && (ch(t.end) == '<' || ch(t.end) == '>')) {
			// foo='echo 0'; foo>&2: the 0 is a word, not the descriptor of >&2
			value += " ";
		}
		int delta = value.length()-(t.end-t.start);
		src = src.substring(0, t.start)+value+src.substring(t.end);
		for(Object [] a : activeAliases) {
			if( (Integer) a[1] >= t.end ) {
				a[1] = (Integer) a[1]+delta;
			}
		}
		if( aliasNextAt >= t.end ) {
			aliasNextAt += delta;
		}
		for (int i = 0; i < lineStarts.length; i++) {
			if( lineStarts[i] > t.start ) {
				lineStarts[i] += delta;
			}
		}
		if( alias.endsWith(" ") || alias.endsWith("\t")) {
			aliasNextAt = t.start+value.length();
		}
		pos = t.start;
	}

	private Token scanToken() {
		skipBlanks();
		Token t = new Token();
		t.start = pos;
		if( atEnd(pos)) {
			t.kind = Kind.EOF;
			t.end = pos;
			return t;
		}
		char c = ch(pos);
		if( c == '\n' ) {
			pos++;
			t.kind = Kind.NEWLINE;
			t.end = pos;
			readHereDocs();
			return t;
		}
		if( Character.isDigit(c)) {
			// 2>file: a descriptor number right before < or >
			int j = pos;
			while( Character.isDigit(ch(j))) {
				j++;
			}
			if( (ch(j) == '<' || ch(j) == '>') && ch(j+1) != '(' && j-pos < 10 ) {
				// (2>(cmd) is a word with a process substitution, as in bash)
				t.kind = Kind.IO_NUMBER;
				t.number = Integer.parseInt(src.substring(pos, j));
				pos = j;
				t.end = pos;
				return t;
			}
		}
		if( c == '{' ) {
			// {fd}>file, {a[1]}>&-
			int j = pos+1;
			while( isNameChar(ch(j), j == pos+1)) {
				j++;
			}
			if( j > pos+1 && ch(j) == '[' ) {
				int close = src.indexOf(']', j);
				j = close < 0 || src.substring(j, close).contains("\n") ? -1 : close+1;
			}
			if( j > pos+1 && ch(j) == '}' && (ch(j+1) == '<' || ch(j+1) == '>')) {
				t.kind = Kind.IO_VAR;
				t.name = src.substring(pos+1, j);
				pos = j+1;
				t.end = pos;
				return t;
			}
		}
		if( c == '}' && functionSubs > 0 && commandStart ) {
			// "${ cmd;}": the } ends it, whatever follows
			t.kind = Kind.WORD;
			t.word = new Word();
			t.word.start = pos;
			t.word.line = lineOf(pos);
			t.word.parts.add(new Word.Literal("}"));
			pos++;
			t.word.end = pos;
			t.word.raw = "}";
			t.end = pos;
			return t;
		}
		if( (c == '<' || c == '>') && ch(pos+1) == '(' ) {
			// <(cmd) >(cmd): a word
			t.kind = Kind.WORD;
			t.word = readWord(false);
			t.end = pos;
			return t;
		}
		for(String op : OPERATORS) {
			if( src.startsWith(op, pos)) {
				pos += op.length();
				t.kind = Kind.OP;
				t.op = op;
				t.end = pos;
				return t;
			}
		}
		t.kind = Kind.WORD;
		t.word = readWord(false);
		t.end = pos;
		return t;
	}

	private static boolean isNameChar(char c, boolean first) {
		return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (!first && c >= '0' && c <= '9');
	}

	private static boolean isMeta(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ';' || c == '&' || c == '|' || c == '(' || c == ')' || c == '<' || c == '>';
	}

	// ------------------------------------------------------------------ words

	/**
	 * A word at pos: up to an unquoted blank or operator character. In regex mode (the word after
	 * =~ in [[ ]]) only a blank ends it, and ( ) | < > are part of it.
	 */
	private Word readWord(boolean regex) {
		Word w = new Word();
		w.start = pos;
		w.line = lineOf(pos);
		StringBuilder lit = new StringBuilder();
		int depth = 0;
		while( !atEnd(pos)) {
			char c = ch(pos);
			if( regex ) {
				// after =~: ( ) and | are part of it, and blanks inside ( )
				if( depth == 0 && (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ';' || c == '&' || c == ')'
						|| ((c == '<' || c == '>') && ch(pos+1) != '('))) {
					break;
				}
				if( c == '(' ) {
					depth++;
				} else if( c == ')' ) {
					depth--;
				}
			} else if( isMeta(c) && !fragment ) {
				if( (c == '<' || c == '>') && ch(pos+1) == '(' ) {
					// <(cmd) and >(cmd): in a word anywhere, as bash's (a=<(cmd), --file=<(cmd))
				} else {
					break;
				}
			}
			switch (c) {
			case '\\':
				if( ch(pos+1) == '\n' ) {
					pos += 2;
				} else if( atEnd(pos+1)) {
					lit.append('\\');
					pos++;
				} else {
					flush(w, lit);
					w.parts.add(new Word.Escaped(ch(pos+1)));
					pos += 2;
				}
				break;
			case '\'': {
				flush(w, lit);
				int close = src.indexOf('\'', pos+1);
				if( close < 0 ) {
					throw eof("'");
				}
				w.parts.add(new Word.SingleQuoted(src.substring(pos+1, close)));
				pos = close+1;
				break;
			}
			case '"':
				flush(w, lit);
				w.parts.add(readDouble(false));
				break;
			case '$': {
				Word.Part p = readDollar(false);
				if( p == null ) {
					lit.append('$');
					pos++;
				} else {
					flush(w, lit);
					w.parts.add(p);
				}
				break;
			}
			case '`':
				flush(w, lit);
				w.parts.add(readBackquote());
				break;
			case '<':
			case '>':
				if( !regex && (!fragment || fragmentProcesses && ch(pos+1) == '(')) {
					// <(cmd) >(cmd) (in ${x:-<(cmd)} too)
					flush(w, lit);
					char dir = c;
					int open = pos+1;
					if( ch(open+1) == '(' ) {
						// <((...) ...: bash reads it when it runs
						int close = matchParen(open);
						w.parts.add(new Word.ProcessSub(dir, src.substring(open+1, close), null));
						pos = close+1;
						break;
					}
					Sequence body = nested(open+1, ")");
					w.parts.add(new Word.ProcessSub(dir, src.substring(open+1, pos-1), body));
					break;
				}
				lit.append(c);
				pos++;
				break;
			default:
				if( !regex && "?*+@!".indexOf(c) >= 0 && ch(pos+1) == '(' ) {
					// extglob: @(a|b) !(*.c) ... (blanks and | in it are part of the word)
					int close = matchParen(pos+1);
					lit.append(src, pos, close+1);
					pos = close+1;
				} else if( !regex && c == '[' && arrayElement && pos == w.start ) {
					// ([key]=value): the key is read to its ] (blanks and all)
					int close = matchBracket(pos, true);
					if( close < 0 ) {
						throw eof("]");
					}
					lit.append(src, pos, close+1);
					pos = close+1;
				} else if( !regex && c == '[' && commandStart && !arrayElement && !casePattern && w.parts.isEmpty() && isName(lit) ) {
					// a[i + 1]=v: where a command starts, name[ is read to its ] (blanks and all)
					int close = matchBracket(pos, true);
					if( close < 0 ) {
						throw eof("]");
					}
					lit.append(src, pos, close+1);
					pos = close+1;
				} else {
					lit.append(c);
					pos++;
				}
			}
		}
		flush(w, lit);
		w.end = pos;
		w.raw = src.substring(w.start, w.end);
		return w;
	}

	private static boolean isName(CharSequence s) {
		if( s.length() == 0 || !isNameChar(s.charAt(0), true)) {
			return false;
		}
		for (int i = 1; i < s.length(); i++) {
			if( !isNameChar(s.charAt(i), false)) {
				return false;
			}
		}
		return true;
	}

	private static void flush(Word w, StringBuilder lit) {
		if( lit.length() > 0 ) {
			w.parts.add(new Word.Literal(lit.toString()));
			lit.setLength(0);
		}
	}

	/** "..." at pos (or $"..." when locale): its text and expansions */
	private Word.DoubleQuoted readDouble(boolean locale) {
		int open = pos;
		pos++;
		List<Word.Part> parts = doubleParts(Fragment.QUOTED);
		if( atEnd(pos)) {
			pos = open;
			throw eof("\"");
		}
		pos++;
		return new Word.DoubleQuoted(parts, locale);
	}

	/**
	 * The text and expansions of a double-quoted string from pos: up to its closing " (QUOTED, pos
	 * is left on the "), or to the end of the text (QUOTED_PARAMETER, HERE_DOCUMENT).
	 */
	private List<Word.Part> doubleParts(Fragment mode) {
		List<Word.Part> parts = new ArrayList<>();
		StringBuilder lit = new StringBuilder();
		String escapable = mode == Fragment.HERE_DOCUMENT ? "$`\\" : mode == Fragment.QUOTED_PARAMETER || mode == Fragment.HERE_PARAMETER ? "$`\"\\}"
				: mode == Fragment.QUOTED_REPLACEMENT ? "$`\"\\}&'" : "$`\"\\";
		boolean any = mode == Fragment.QUOTED_AGAIN;
		while( !atEnd(pos)) {
			char c = ch(pos);
			if( c == '"' && (mode == Fragment.QUOTED || mode == Fragment.QUOTED_AGAIN)) {
				break;
			}
			if( c == '\\' ) {
				char n = ch(pos+1);
				if( n == '\n' ) {
					pos += 2;
				} else if( !atEnd(pos+1) && (any || escapable.indexOf(n) >= 0)) {
					flushTo(parts, lit);
					parts.add(new Word.Escaped(n));
					pos += 2;
				} else {
					lit.append('\\');
					pos++;
				}
			} else if( c == '\'' && mode == Fragment.QUOTED_REPLACEMENT && src.indexOf('\'', pos+1) > 0 ) {
				// "${x/a/'b'}": single quotes quote (as in bash 5.2)
				flushTo(parts, lit);
				int close = src.indexOf('\'', pos+1);
				parts.add(new Word.SingleQuoted(src.substring(pos+1, close)));
				pos = close+1;
			} else if( c == '$' && ch(pos+1) == '\'' && (mode == Fragment.QUOTED_PARAMETER || mode == Fragment.QUOTED_REPLACEMENT)) {
				// "${x:-$'\t'}": $'..' is read in "${ }" (bash's extquote)
				flushTo(parts, lit);
				parts.add(readDollar(false));
			} else if( c == '"' && (mode == Fragment.QUOTED_PARAMETER || mode == Fragment.HERE_PARAMETER || mode == Fragment.QUOTED_REPLACEMENT)) {
				// "${x:-"a b"}": quotes inside quote again
				flushTo(parts, lit);
				pos++;
				List<Word.Part> inner = doubleParts(Fragment.QUOTED_AGAIN);
				if( !atEnd(pos)) {
					// (as bash: one that is not closed goes to the end of the word)
					pos++;
				}
				parts.add(new Word.DoubleQuoted(inner, false));
			} else if( c == '$' ) {
				Word.Part p = readDollar(true);
				if( p == null ) {
					lit.append('$');
					pos++;
				} else {
					flushTo(parts, lit);
					parts.add(p);
				}
			} else if( c == '`' ) {
				flushTo(parts, lit);
				Word.Backquote b = readBackquote();
				if( mode != Fragment.HERE_DOCUMENT && mode != Fragment.HERE_PARAMETER ) {
					// "`echo \"hi\"`": in "..." \" in it is "
					b = new Word.Backquote(withoutQuoteEscapes(b.text()));
				}
				parts.add(b);
			} else {
				lit.append(c);
				pos++;
			}
		}
		flushTo(parts, lit);
		return parts;
	}

	private static void flushTo(List<Word.Part> parts, StringBuilder lit) {
		if( lit.length() > 0 ) {
			parts.add(new Word.Literal(lit.toString()));
			lit.setLength(0);
		}
	}

	/** an expansion at pos (a $), or null if the $ is just a $ */
	private Word.Part readDollar(boolean inDouble) {
		char n = ch(pos+1);
		if( n == '(' ) {
			if( ch(pos+2) == '(' ) {
				// $(( expr )): unless there is no )) to end it, then it is $( (cmd) )
				int close = arithClose(pos+3);
				if( close > 0 ) {
					// (counted from the end: an alias in a $( ) in it changes the text before it)
					int tail = src.length()-close;
					Word expr = arithWord(pos+3, close);
					pos = src.length()-tail+2;
					return new Word.ArithSub(expr);
				}
				// bash reads the commands of $((cmd) ...) when it runs them
				int end = matchParen(pos+1);
				String text = src.substring(pos+2, end);
				pos = end+1;
				return new Word.CommandSub(text, null);
			}
			int open = pos+1;
			Sequence body = nested(open+1, ")");
			return new Word.CommandSub(src.substring(open+1, pos-1), body);
		}
		if( n == '{' && (ch(pos+2) == ' ' || ch(pos+2) == '\t' || ch(pos+2) == '\n' || ch(pos+2) == '|')) {
			// ${ list; } and ${| list; }
			boolean reply = ch(pos+2) == '|';
			int from = pos+(reply ? 3 : 2);
			Sequence body = nested(from, "}");
			return new Word.FunctionSub(src.substring(from, pos-1), body, reply);
		}
		if( n == '{' ) {
			int close = braceClose(pos+2, inDouble);
			String body = src.substring(pos+2, close);
			pos = close+1;
			return new Word.ParamExpansion(body);
		}
		if( n == '[' ) {
			// $[ expr ]: the old arithmetic form
			int close = matchBracket(pos+1, false);
			if( close < 0 ) {
				throw eof("]");
			}
			int tail = src.length()-close;
			Word expr = arithWord(pos+2, close);
			pos = src.length()-tail+1;
			return new Word.ArithSub(expr);
		}
		if( n == '\'' && !inDouble ) {
			int i = pos+2;
			while( !atEnd(i) && ch(i) != '\'' ) {
				if( ch(i) == '\\' ) {
					i++;
				}
				i++;
			}
			if( atEnd(i)) {
				throw eof("'");
			}
			String text = src.substring(pos+2, i);
			pos = i+1;
			return new Word.AnsiC(text);
		}
		if( n == '"' && !inDouble ) {
			pos++;
			return readDouble(true);
		}
		if( isNameChar(n, true)) {
			int i = pos+1;
			while( isNameChar(ch(i), false)) {
				i++;
			}
			String name = src.substring(pos+1, i);
			pos = i;
			return new Word.Param(name);
		}
		if( Character.isDigit(n) || "@*#?-$!".indexOf(n) >= 0 ) {
			pos += 2;
			return new Word.Param(String.valueOf(n));
		}
		return null;
	}

	/** `...` at pos, as written between the backquotes */
	/** text with \" as " (\\ stays, for the backquote's own reading) */
	private static String withoutQuoteEscapes(String text) {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == '\\' && i+1 < text.length()) {
				char n = text.charAt(++i);
				if( n != '"' ) {
					ret.append(c);
				}
				ret.append(n);
			} else {
				ret.append(c);
			}
		}
		return ret.toString();
	}

	private Word.Backquote readBackquote() {
		int i = pos+1;
		while( !atEnd(i) && ch(i) != '`' ) {
			if( ch(i) == '\\' ) {
				i++;
			}
			i++;
		}
		if( atEnd(i)) {
			throw eof("`");
		}
		String text = src.substring(pos+1, i);
		pos = i+1;
		return new Word.Backquote(text);
	}

	/**
	 * The commands of $( ), <( ) or ${ } starting at from, up to its ) or } (close): pos ends after
	 * it. (The parser reads them, so a ) in case x in a) ... esac does not end it.)
	 */
	private Sequence nested(int from, String close) {
		Token saved = cur;
		boolean savedStart = commandStart;
		// (the commands in a word read as a fragment are read as commands)
		boolean savedFragment = fragment;
		fragment = false;
		cur = null;
		pos = from;
		commandStart = true;
		boolean paren = close.equals(")");
		if( !paren ) {
			functionSubs++;
		}
		closers.add(close);
		List<int []> outer = new ArrayList<>(open);
		open.clear();
		Sequence body;
		Token end;
		try {
			body = paren ? list(Set.of(), Set.of(")")) : list(Set.of("}"), Set.of());
			end = take();
			if( paren ? !isOp(end, ")") : !isWord(end, "}")) {
				throw unexpected(end);
			}
		} finally {
			fragment = savedFragment;
			if( !paren ) {
				functionSubs--;
			}
			closers.remove(closers.size()-1);
			open.clear();
			open.addAll(outer);
		}
		cur = saved;
		commandStart = savedStart;
		return body;
	}

	/** skip a quoted part at i: the index after it (or i if there is none) */
	private int skipQuoted(int i) {
		char c = ch(i);
		if( c == '\'' ) {
			int close = src.indexOf('\'', i+1);
			return close < 0 ? src.length() : close+1;
		}
		if( c == '`' ) {
			int j = i+1;
			while( !atEnd(j) && ch(j) != c ) {
				if( ch(j) == '\\' ) {
					j++;
				}
				j++;
			}
			if( atEnd(j)) {
				throw new SyntaxError(lineOf(i), "unexpected EOF while looking for matching ``'");
			}
			return j+1;
		}
		if( c == '"' ) {
			// "..." with ${ } $( ) `...` in it, which may have quotes of their own
			int j = i+1;
			while( !atEnd(j) && ch(j) != c ) {
				char d = ch(j);
				if( d == '\\' ) {
					j += 2;
				} else if( d == '`' ) {
					j = skipQuoted(j);
				} else if( d == '$' && ch(j+1) == '{' ) {
					j = braceClose(j+2, c == '"')+1;
				} else if( d == '$' && ch(j+1) == '(' ) {
					j = skipCommandSub(j);
				} else {
					j++;
				}
			}
			if( atEnd(j)) {
				throw new SyntaxError(lineOf(i), "unexpected EOF while looking for matching `\"'");
			}
			return j+1;
		}
		if( c == '\\' ) {
			return i+2;
		}
		return i;
	}

	/**
	 * the index after the $( ) or $(( )) at i: its commands are read (and must be right) but not
	 * kept, as bash does in ${ } (pos is not changed)
	 */
	private int skipCommandSub(int i) {
		if( ch(i+2) == '(' ) {
			int close = arithClose(i+3);
			return close > 0 ? close+2 : matchParen(i+1)+1;
		}
		int saved = pos;
		nested(i+2, ")");
		int ret = pos;
		pos = saved;
		return ret;
	}

	/** the ) that closes the ( at open (quotes skipped) */
	private int matchParen(int open) {
		int depth = 0;
		for (int i = open; !atEnd(i); ) {
			int q = skipQuoted(i);
			if( q != i ) {
				i = q;
				continue;
			}
			char c = ch(i);
			if( c == '(' ) {
				depth++;
			} else if( c == ')' && --depth == 0 ) {
				return i;
			}
			i++;
		}
		throw eof(")");
	}

	/** the ] that closes the [ at open, or -1 (not found, or not before a newline unless lines is true) */
	private int matchBracket(int open, boolean lines) {
		int depth = 0;
		for (int i = open; !atEnd(i) && (lines || ch(i) != '\n'); ) {
			int q = skipQuoted(i);
			if( q != i ) {
				i = q;
				continue;
			}
			char c = ch(i);
			if( c == '$' && ch(i+1) == '(' ) {
				i = matchParen(i+1)+1;
				continue;
			}
			if( c == '[' ) {
				depth++;
			} else if( c == ']' && --depth == 0 ) {
				return i;
			}
			i++;
		}
		return -1;
	}

	/**
	 * the } that ends ${ whose text starts at from (quotes, $( ) and nested ${ } skipped; a { alone
	 * does not nest, as in bash: ${a:-{b} is ${a:-{b})
	 */
	private int braceClose(int from) {
		return braceClose(from, false);
	}

	/** set -o posix is on (the parser reads some things differently then) */
	public static volatile java.util.function.BooleanSupplier posixMode = () -> false;

	/**
	 * the } of ${ whose text starts at from. inDouble: it is in "..."; in posix mode a ' in the
	 * word of ${x+word} (- = ?) is then itself, as in bash.
	 */
	private int braceClose(int from, boolean inDouble) {
		int depth = 0;
		boolean literalSingle = inDouble && posixMode.getAsBoolean() && wordOperator(from);
		for (int i = from; !atEnd(i); ) {
			if( literalSingle && ch(i) == '\'' ) {
				i++;
				continue;
			}
			int q = skipQuoted(i);
			if( q != i ) {
				i = q;
				continue;
			}
			char c = ch(i);
			if( c == '$' && ch(i+1) == '(' ) {
				i = skipCommandSub(i);
				continue;
			}
			if( c == '$' && ch(i+1) == '{' ) {
				depth++;
				i += 2;
				continue;
			}
			if( c == '$' && ch(i+1) == '\'' ) {
				// $'...' (\' does not end it)
				int j = i+2;
				while( !atEnd(j) && ch(j) != '\'' ) {
					j += ch(j) == '\\' ? 2 : 1;
				}
				if( atEnd(j)) {
					throw eof("'");
				}
				i = j+1;
				continue;
			}
			if( c == '}' ) {
				if( depth == 0 ) {
					return i;
				}
				depth--;
			}
			i++;
		}
		throw eof("}");
	}

	/** the ${ whose text starts at from has a word operator (- + = ?, maybe after :) */
	private boolean wordOperator(int from) {
		int i = from;
		if( ch(i) == '#' || ch(i) == '!' ) {
			i++;
		}
		if( Character.isLetter(ch(i)) || ch(i) == '_' ) {
			while( Character.isLetterOrDigit(ch(i)) || ch(i) == '_' ) {
				i++;
			}
		} else if( Character.isDigit(ch(i))) {
			while( Character.isDigit(ch(i))) {
				i++;
			}
		} else if( "@*#?-$!".indexOf(ch(i)) >= 0 ) {
			i++;
		} else {
			return false;
		}
		if( ch(i) == '[' ) {
			int depth = 0;
			for (; !atEnd(i); i++) {
				if( ch(i) == '[' ) {
					depth++;
				} else if( ch(i) == ']' && --depth == 0 ) {
					i++;
					break;
				}
			}
		}
		if( ch(i) == ':' ) {
			i++;
		}
		return "-+=?".indexOf(ch(i)) >= 0;
	}

	/** the first ) of the )) that ends $(( whose text starts at from, or -1 if there is none */
	private int arithClose(int from) {
		int depth = 0;
		for (int i = from; !atEnd(i); ) {
			int q = skipQuoted(i);
			if( q != i ) {
				i = q;
				continue;
			}
			char c = ch(i);
			if( c == '$' && (ch(i+1) == '{' || ch(i+1) == '(' && ch(i+2) != '(')) {
				// ${ list; }, ${x} and $(cmd): their ) do not count ($(case x in x) esac) too)
				try {
					i = ch(i+1) == '{' ? braceClose(i+2, false)+1 : skipCommandSub(i);
					continue;
				} catch (SyntaxError e) {
					return -1;
				}
			}
			if( c == '(' ) {
				depth++;
			} else if( c == ')' ) {
				if( depth == 0 ) {
					return ch(i+1) == ')' ? i : -1;
				}
				depth--;
			}
			i++;
		}
		return -1;
	}

	// ------------------------------------------------------------------ here-documents

	private static int trailingBackslashes(String line) {
		int n = 0;
		while( n < line.length() && line.charAt(line.length()-1-n) == '\\' ) {
			n++;
		}
		return n;
	}

	/** warnings found while reading: {line, message}, as bash prints them (see Sequence.warnings) */
	private final List<Object []> warnings = new ArrayList<>();

	private String hereDocWarning(Redirect r) {
		return "warning: here-document at line "+r.line+" delimited by end-of-file (wanted `"+r.hereDoc.delimiter+"')";
	}

	/** after a newline: the bodies of the here-documents started on the line before it */
	private void readHereDocs() {
		for(Redirect r : pendingHereDocs) {
			HereDoc h = r.hereDoc;
			StringBuilder body = new StringBuilder();
			while( true ) {
				if( atEnd(pos)) {
					// bash warns and takes the rest of the file
					hereDocumentOpen = true;
					warnings.add(new Object[] {lineOf(pos)-(src.endsWith("\n") ? 1 : 0), hereDocWarning(r)});
					break;
				}
				int lineStart = pos;
				int nl = src.indexOf('\n', pos);
				int lineEnd = nl < 0 ? src.length() : nl;
				String line = src.substring(pos, lineEnd);
				if( line.endsWith("\r")) {
					line = line.substring(0, line.length()-1);
				}
				pos = nl < 0 ? src.length() : nl+1;
				while( !h.quoted && trailingBackslashes(line) % 2 == 1 && !atEnd(pos)) {
					// as bash: backslash-newline is removed before the line is checked for the delimiter
					nl = src.indexOf('\n', pos);
					lineEnd = nl < 0 ? src.length() : nl;
					line = line.substring(0, line.length()-1)+src.substring(pos, lineEnd);
					pos = nl < 0 ? src.length() : nl+1;
				}
				String check = line;
				if( h.stripTabs ) {
					int t = 0;
					while( t < check.length() && check.charAt(t) == '\t' ) {
						t++;
					}
					check = check.substring(t);
				}
				if( check.equals(h.delimiter)) {
					break;
				}
				if( !closers.isEmpty() && closers.get(closers.size()-1).equals(")") && check.startsWith(h.delimiter)
						&& check.substring(h.delimiter.length()).startsWith(")") && line.length() == lineEnd-lineStart ) {
					// $(cat <<EOF ... EOF): the ) ends the here-document and the $( ), as in bash (with a warning)
					warnings.add(new Object[] {lineOf(lineStart), hereDocWarning(r)});
					pos = lineStart+(line.length()-check.length())+h.delimiter.length();
					break;
				}
				body.append(h.stripTabs ? check : line).append('\n');
			}
			h.body = body.toString();
		}
		pendingHereDocs.clear();
	}

	// ------------------------------------------------------------------ errors

	private SyntaxError unexpected(Token t) {
		if( t.kind == Kind.EOF ) {
			if( !closers.isEmpty()) {
				return eof(closers.get(closers.size()-1));
			}
			// bash ends the script with a newline, so its last line is one more if it has none
			int line = lineOf(src.length()) + (src.endsWith("\n") ? 0 : 1);
			if( !open.isEmpty()) {
				int [] o = open.get(open.size()-1);
				String word = src.startsWith("((", o[0]) ? "(" : src.substring(o[0], src.startsWith("{", o[0]) || src.startsWith("(", o[0]) ? o[0]+1 : wordEnd(o[0]));
				return new SyntaxError(line, "syntax error: unexpected end of file from `"+word+"' command on line "+o[1]);
			}
			return new SyntaxError(line, "syntax error: unexpected end of file");
		}
		String text = t.kind == Kind.NEWLINE ? "newline" : src.substring(t.start, t.end);
		String in = closers.isEmpty() ? "" : " while looking for matching `"+closers.get(closers.size()-1)+"'";
		return new SyntaxError(lineOf(t.start), "syntax error near unexpected token `"+text+"'"+in);
	}

	private int wordEnd(int i) {
		while( !atEnd(i) && !isMeta(ch(i))) {
			i++;
		}
		return i;
	}

	private SyntaxError eof(String looking) {
		return new SyntaxError(lineOf(Math.min(pos, src.length())), "unexpected EOF while looking for matching `"+looking+"'");
	}

	// ------------------------------------------------------------------ grammar

	private boolean isWord(Token t, String text) {
		return t.kind == Kind.WORD && text.equals(t.word.plainText());
	}

	/** the text of a plain word token, or "" (Set.of does not take null) */
	private static String plain(Token t) {
		String ret = t.word.plainText();
		return ret == null ? "" : ret;
	}

	private boolean isOp(Token t, String op) {
		return t.kind == Kind.OP && t.op.equals(op);
	}

	private void skipNewlines() {
		while( peek().kind == Kind.NEWLINE ) {
			take();
		}
	}

	private void expectWord(String text) {
		Token t = take();
		if( !isWord(t, text)) {
			throw unexpected(t);
		}
	}

	private void expectOp(String op) {
		Token t = take();
		if( !isOp(t, op)) {
			throw unexpected(t);
		}
	}

	/**
	 * Commands separated by ; & or newlines, up to (not taking) one of the reserved words in
	 * endWords or operators in endOps, or the end of the script.
	 */
	private Sequence list(Set<String> endWords, Set<String> endOps) {
		Sequence seq = new Sequence();
		skipNewlines();
		seq.start = peek().start;
		seq.line = lineOf(seq.start);
		seq.end = seq.start;
		while( true ) {
			Token t = peek();
			if( t.kind == Kind.EOF || (t.kind == Kind.OP && endOps.contains(t.op))
					|| (t.kind == Kind.WORD && endWords.contains(plain(t)))) {
				break;
			}
			Item item = new Item();
			item.command = andOr();
			seq.items.add(item);
			seq.end = item.command.end;
			t = peek();
			if( isOp(t, "&")) {
				take();
				item.background = true;
			} else if( isOp(t, ";")) {
				take();
			} else if( t.kind == Kind.NEWLINE ) {
				take();
			} else if( !(t.kind == Kind.EOF || (t.kind == Kind.OP && endOps.contains(t.op))
					|| (t.kind == Kind.WORD && endWords.contains(plain(t))))) {
				throw unexpected(t);
			}
			skipNewlines();
		}
		return seq;
	}

	/** a list that must have a command (the body of { }, ( ), if, while ...) */
	private Sequence compoundList(Set<String> endWords, Set<String> endOps) {
		Sequence ret = list(endWords, endOps);
		if( ret.items.isEmpty()) {
			throw unexpected(peek());
		}
		return ret;
	}

	private AndOr andOr() {
		AndOr ao = new AndOr();
		Pipeline p = pipeline();
		ao.start = p.start;
		ao.line = p.line;
		ao.pipelines.add(p);
		while( isOp(peek(), "&&") || isOp(peek(), "||")) {
			ao.ops.add(take().op);
			skipNewlines();
			ao.pipelines.add(pipeline());
		}
		ao.end = ao.pipelines.get(ao.pipelines.size()-1).end;
		return ao;
	}

	private Pipeline pipeline() {
		Pipeline p = new Pipeline();
		p.start = peek().start;
		p.line = lineOf(p.start);
		boolean prefixed = false;
		while( true ) {
			Token t = peek();
			if( isWord(t, "time") && !p.timed ) {
				take();
				p.timed = true;
				if( isWord(peek(), "-p")) {
					take();
					p.timePosix = true;
				}
			} else if( isWord(t, "!")) {
				take();
				p.negated = !p.negated;
				prefixed = true;
			} else {
				break;
			}
		}
		if( (p.timed || prefixed) && endsPipeline(peek())) {
			// time or ! alone
			p.end = peek().start;
			return p;
		}
		p.commands.add(command());
		while( isOp(peek(), "|") || isOp(peek(), "|&")) {
			p.stderrToo.add(take().op.equals("|&"));
			skipNewlines();
			p.commands.add(command());
		}
		p.stderrToo.add(false);
		p.end = p.commands.get(p.commands.size()-1).end;
		return p;
	}

	/** a token that can follow time or ! with no command */
	private boolean endsPipeline(Token t) {
		return t.kind == Kind.EOF || t.kind == Kind.NEWLINE
				|| (t.kind == Kind.OP && Set.of(";", "&", ";;", ";&", ";;&", ")").contains(t.op))
				|| (t.kind == Kind.WORD && CLOSERS.contains(plain(t)));
	}

	private Command command() {
		Token t = peek();
		Command c;
		if( isWord(t, "!")) {
			// ! only starts a pipeline
			throw unexpected(t);
		}
		if( t.kind == Kind.WORD && t.word.isPlain()) {
			String w = t.word.plainText();
			if( CLOSERS.contains(w)) {
				throw unexpected(t);
			}
			if( w.equals("function")) {
				return functionDef(true, null);
			}
			if( w.equals("coproc")) {
				return coproc();
			}
			if( !Set.of("if", "while", "until", "for", "select", "case", "{", "[[").contains(w)) {
				return simpleCommand();
			}
			open.add(new int[] {t.start, lineOf(t.start)});
			try {
				switch (w) {
				case "if": c = ifCommand(); break;
				case "while": c = loop(false); break;
				case "until": c = loop(true); break;
				case "for": c = forCommand(); break;
				case "select": c = selectCommand(); break;
				case "case": c = caseCommand(); break;
				case "{": c = braceGroup(); break;
				default: c = cond(); break;
				}
			} finally {
				open.remove(open.size()-1);
			}
		} else if( isOp(t, "(")) {
			open.add(new int[] {t.start, lineOf(t.start)});
			try {
				c = parenCommand();
			} finally {
				open.remove(open.size()-1);
			}
		} else {
			return simpleCommand();
		}
		redirects(c);
		c.end = Math.max(c.end, lastEnd(c));
		return c;
	}

	private int lastEnd(Command c) {
		return c.redirects.isEmpty() ? c.end : c.redirects.get(c.redirects.size()-1).end;
	}

	/** redirects after a compound command */
	private void redirects(Command c) {
		while( isRedirectStart(peek())) {
			c.redirects.add(redirect());
		}
	}

	private boolean isRedirectStart(Token t) {
		return t.kind == Kind.IO_NUMBER || t.kind == Kind.IO_VAR || (t.kind == Kind.OP && REDIRECT_OPS.contains(t.op));
	}

	private Redirect redirect() {
		Redirect r = new Redirect();
		Token t = take();
		r.start = t.start;
		r.line = lineOf(t.start);
		if( t.kind == Kind.IO_NUMBER ) {
			r.fd = t.number;
			t = take();
		} else if( t.kind == Kind.IO_VAR ) {
			r.fdVariable = t.name;
			t = take();
		}
		if( t.kind != Kind.OP || !REDIRECT_OPS.contains(t.op)) {
			throw unexpected(t);
		}
		r.op = t.op;
		Token target = take();
		if( target.kind == Kind.IO_NUMBER && (r.op.equals(">&") || r.op.equals("<&"))) {
			// 3>&11>&2: the 11 is read as a descriptor number, and is one
			Word w = new Word();
			w.start = target.start;
			w.line = lineOf(target.start);
			w.parts.add(new Word.Literal(String.valueOf(target.number)));
			w.end = target.end;
			w.raw = src.substring(w.start, w.end);
			target.word = w;
			target.kind = Kind.WORD;
		}
		if( target.kind != Kind.WORD ) {
			throw unexpected(target);
		}
		r.end = target.end;
		if( r.op.equals("<<") || r.op.equals("<<-")) {
			HereDoc h = new HereDoc();
			h.stripTabs = r.op.equals("<<-");
			// (quoted: a quote or \ in it, not just $ or `)
			h.quoted = target.word.parts.stream().anyMatch(p -> p instanceof Word.SingleQuoted || p instanceof Word.DoubleQuoted
					|| p instanceof Word.Escaped || p instanceof Word.AnsiC);
			h.delimiter = unquote(target.word);
			if( h.stripTabs ) {
				// <<-'	END': the delimiter loses its leading tabs too (bash's)
				h.delimiter = h.delimiter.replaceFirst("^\t+", "");
			}
			r.hereDoc = h;
			pendingHereDocs.add(r);
		} else {
			r.target = target.word;
		}
		return r;
	}

	/** a here-document's word with its quotes removed */
	private static String unquote(Word w) {
		StringBuilder ret = new StringBuilder();
		for(Word.Part p : w.parts) {
			if( p instanceof Word.Literal l ) {
				ret.append(l.text());
			} else if( p instanceof Word.SingleQuoted s ) {
				ret.append(s.text());
			} else if( p instanceof Word.Escaped e ) {
				ret.append(e.c());
			} else if( p instanceof Word.DoubleQuoted d ) {
				for(Word.Part q : d.parts()) {
					if( q instanceof Word.Literal l ) {
						ret.append(l.text());
					} else if( q instanceof Word.Escaped e ) {
						ret.append(e.c());
					} else if( q instanceof Word.Param pa ) {
						ret.append('$').append(pa.name());
					}
				}
			} else if( p instanceof Word.Param pa ) {
				ret.append('$').append(pa.name());
			} else {
				// $(..) `..` ${..}: as written (a delimiter is not expanded)
				ret.append(written(p));
			}
		}
		return ret.toString();
	}

	private static String written(Word.Part p) {
		return switch (p) {
		case Word.ParamExpansion x -> "${"+x.body()+"}";
		case Word.CommandSub c -> "$("+c.text()+")";
		case Word.Backquote b -> "`"+b.text()+"`";
		case Word.ArithSub a -> "$(("+a.expression().raw+"))";
		case Word.ArithSubscript a -> "["+a.text()+"]";
		case Word.AnsiC a -> "$'"+a.text()+"'";
		default -> "";
		};
	}

	private Command simpleCommand() {
		SimpleCommand sc = new SimpleCommand();
		sc.start = peek().start;
		sc.line = lineOf(sc.start);
		sc.end = sc.start;
		boolean declaration = false;
		boolean wordArrays = false;
		while( true ) {
			Token t = peek();
			if( isRedirectStart(t)) {
				Redirect r = redirect();
				sc.redirects.add(r);
				sc.end = r.end;
				// as in bash, declare >f a=(1) is not an array
				declaration = false;
				wordArrays = false;
				continue;
			}
			if( t.kind != Kind.WORD ) {
				break;
			}
			take();
			Word w = t.word;
			if( sc.words.isEmpty() && assignmentLength(w) > 0 ) {
				Assignment a = assignment(w);
				sc.assignments.add(a);
				sc.end = a.end;
				continue;
			}
			if( sc.words.isEmpty() && sc.assignments.isEmpty() && sc.redirects.isEmpty() && isOp(peek(), "(")) {
				// name() body: a function
				return functionDef(false, w);
			}
			if( sc.words.isEmpty()) {
				declaration = w.isPlain() && DECLARATIONS.contains(w.plainText());
				wordArrays = w.isPlain() && WORD_ARRAYS.contains(w.plainText());
			} else if( declaration && assignmentLength(w) > 0 ) {
				w.assignment = assignment(w);
			} else if( wordArrays && assignmentLength(w) > 0 && cur == null && pos == w.end && ch(pos) == '(' ) {
				parenthesizedTail(w);
			}
			sc.words.add(w);
			sc.end = w.assignment != null ? w.assignment.end : w.end;
		}
		if( sc.words.isEmpty() && sc.assignments.isEmpty() && sc.redirects.isEmpty()) {
			throw unexpected(peek());
		}
		return sc;
	}

	/**
	 * eval a=( x "y z" ): add the ( ... ) at pos to the word, its blanks and newlines as text
	 * (so the word expands to a=(x y z)).
	 */
	/** the operator at p (<>, &&, ..), or its one character */
	private String operatorAt(int p) {
		for(String op : OPERATORS) {
			if( src.startsWith(op, p)) {
				return op;
			}
		}
		return String.valueOf(ch(p));
	}

	private void parenthesizedTail(Word w) {
		w.parts.add(new Word.Literal("("));
		pos++;
		while( true ) {
			int from = pos;
			skipBlanks();
			while( ch(pos) == '\n' ) {
				pos++;
				skipBlanks();
			}
			if( pos > from ) {
				w.parts.add(new Word.Literal(" "));
			}
			if( atEnd(pos)) {
				throw eof(")");
			}
			if( ch(pos) == ')' ) {
				pos++;
				w.parts.add(new Word.Literal(")"));
				break;
			}
			if( isMeta(ch(pos))) {
				throw new SyntaxError(lineOf(pos), "syntax error near unexpected token `"+operatorAt(pos)+"'");
			}
			w.parts.addAll(readWord(false).parts);
		}
		if( !atEnd(pos) && !isMeta(ch(pos)) && !Character.isWhitespace(ch(pos))) {
			// let a=(4*3)/2: the rest of the word
			w.parts.addAll(readWord(false).parts);
		}
		w.end = pos;
		w.raw = src.substring(w.start, w.end);
	}

	/**
	 * The length of name=, name+=, name[index]= at the start of the word (unquoted), or 0 if
	 * the word is no assignment.
	 */
	private static int assignmentLength(Word w) {
		if( w.parts.isEmpty() || !(w.parts.get(0) instanceof Word.Literal l)) {
			return 0;
		}
		String s = l.text();
		int i = 0;
		if( s.isEmpty() || !isNameChar(s.charAt(0), true)) {
			return 0;
		}
		while( i < s.length() && isNameChar(s.charAt(i), i == 0)) {
			i++;
		}
		if( i < s.length() && s.charAt(i) == '[' ) {
			int close;
			try {
				close = new Parser(s).matchBracket(i, true);
			} catch (SyntaxError e) {
				close = -1;
			}
			if( close < 0 ) {
				return 0;
			}
			i = close+1;
		}
		if( i < s.length() && s.charAt(i) == '+' ) {
			i++;
		}
		return i < s.length() && s.charAt(i) == '=' ? i+1 : 0;
	}

	/** the assignment the word starts with (an array if =( follows it directly) */
	private Assignment assignment(Word w) {
		Assignment a = new Assignment();
		a.start = w.start;
		a.line = w.line;
		String s = ((Word.Literal) w.parts.get(0)).text();
		int len = assignmentLength(w);
		String head = s.substring(0, len-1);
		if( head.endsWith("+")) {
			a.append = true;
			head = head.substring(0, head.length()-1);
		}
		int bracket = head.indexOf('[');
		a.name = bracket < 0 ? head : head.substring(0, bracket);
		a.index = bracket < 0 ? null : head.substring(bracket+1, head.length()-1);
		Word value = new Word();
		value.start = w.start+len;
		value.line = w.line;
		if( len < s.length()) {
			value.parts.add(new Word.Literal(s.substring(len)));
		}
		value.parts.addAll(w.parts.subList(1, w.parts.size()));
		value.end = w.end;
		value.raw = src.substring(value.start, value.end);
		a.end = w.end;
		if( value.parts.isEmpty() && cur == null && pos == w.end && ch(pos) == '(' ) {
			// name=( words ): an array (blanks, newlines and comments between the words)
			pos++;
			a.array = new ArrayList<>();
			while( true ) {
				skipBlanks();
				if( ch(pos) == '\n' ) {
					pos++;
					continue;
				}
				if( atEnd(pos)) {
					throw eof(")");
				}
				if( ch(pos) == ')' ) {
					pos++;
					break;
				}
				if( isMeta(ch(pos))) {
					SyntaxError e = new SyntaxError(lineOf(pos), "syntax error near unexpected token `"+operatorAt(pos)+"'");
					e.recoverable = true;
					throw e;
				}
				arrayElement = true;
				try {
					a.array.add(readWord(false));
				} finally {
					arrayElement = false;
				}
			}
			if( !atEnd(pos) && !isMeta(ch(pos))) {
				// x=(a b)c: not an array, the text as written is the value (bash's)
				readWord(false);
				a.array = null;
				a.value = fragment(src.substring(value.start, pos), Fragment.WORD);
				a.value.start = value.start;
				a.value.end = pos;
				a.value.line = value.line;
				a.value.raw = src.substring(value.start, pos);
			}
			a.end = pos;
		} else {
			a.value = value;
		}
		return a;
	}

	private FunctionDef functionDef(boolean keyword, Word name) {
		FunctionDef f = new FunctionDef();
		f.keyword = keyword;
		if( keyword ) {
			Token t = take();
			f.start = t.start;
			Token n = take();
			if( n.kind != Kind.WORD ) {
				throw unexpected(n);
			}
			name = n.word;
			if( isOp(peek(), "(")) {
				take();
				expectOp(")");
			}
		} else {
			f.start = name.start;
			expectOp("(");
			expectOp(")");
		}
		f.line = lineOf(f.start);
		f.name = name.raw;
		f.quotedName = !name.isPlain();
		skipNewlines();
		Token t = peek();
		boolean compound = isOp(t, "(") || (t.kind == Kind.WORD && Set.of("{", "if", "while", "until", "for", "select", "case", "[[").contains(plain(t)));
		if( !compound ) {
			throw unexpected(t);
		}
		f.body = command();
		f.end = f.body.end;
		return f;
	}

	/** coproc [NAME] command: NAME only before a compound command (COPROC otherwise) */
	private Command coproc() {
		Ast.Coproc c = new Ast.Coproc();
		Token kw = take();
		c.start = kw.start;
		c.line = lineOf(c.start);
		c.name = "COPROC";
		Token t = peek();
		if( t.kind == Kind.WORD && t.word.isPlain()
				&& !Set.of("if", "while", "until", "for", "select", "case", "{", "[[").contains(t.word.plainText())) {
			// (any word before a compound command is the name; one that is not a name is an error
			// when it runs, as bash's)
			Token name = take();
			Token next = peek();
			boolean compound = isOp(next, "(") || (next.kind == Kind.WORD && Set.of("if", "while", "until", "for", "select", "case", "{", "[[").contains(plain(next)));
			if( compound ) {
				c.name = name.word.plainText();
			} else {
				back = name;
			}
		}
		c.body = command();
		c.end = c.body.end;
		return c;
	}

	private BraceGroup braceGroup() {
		BraceGroup g = new BraceGroup();
		g.start = take().start;
		g.line = lineOf(g.start);
		g.body = compoundList(Set.of("}"), Set.of());
		Token close = take();
		if( !isWord(close, "}")) {
			throw unexpected(close);
		}
		g.end = close.end;
		return g;
	}

	/** ( list ), or (( expression )) */
	private Command parenCommand() {
		Token open = take();
		if( ch(pos) == '(' ) {
			int close = arithClose(pos+1);
			if( close > 0 ) {
				Ast.Arith a = new Arith();
				a.start = open.start;
				a.line = lineOf(a.start);
				int tail = src.length()-close;
				a.expression = arithWord(pos+1, close);
				pos = src.length()-tail+2;
				a.end = pos;
				return a;
			}
		}
		Subshell s = new Subshell();
		s.start = open.start;
		s.line = lineOf(s.start);
		s.body = compoundList(Set.of(), Set.of(")"));
		Token close = take();
		if( !isOp(close, ")")) {
			throw unexpected(close);
		}
		s.end = close.end;
		return s;
	}

	private If ifCommand() {
		If c = new If();
		c.start = take().start;
		c.line = lineOf(c.start);
		c.conditions.add(compoundList(Set.of("then"), Set.of()));
		expectWord("then");
		c.bodies.add(compoundList(Set.of("elif", "else", "fi"), Set.of()));
		while( isWord(peek(), "elif")) {
			take();
			c.conditions.add(compoundList(Set.of("then"), Set.of()));
			expectWord("then");
			c.bodies.add(compoundList(Set.of("elif", "else", "fi"), Set.of()));
		}
		if( isWord(peek(), "else")) {
			take();
			c.elseBody = compoundList(Set.of("fi"), Set.of());
		}
		Token fi = take();
		if( !isWord(fi, "fi")) {
			throw unexpected(fi);
		}
		c.end = fi.end;
		return c;
	}

	private Loop loop(boolean until) {
		Loop c = new Loop();
		c.until = until;
		c.start = take().start;
		c.line = lineOf(c.start);
		c.condition = compoundList(Set.of("do"), Set.of());
		c.body = doGroup();
		c.end = pos;
		return c;
	}

	/** do list done (or { list }, which bash takes too) */
	private Sequence doGroup() {
		Token t = take();
		if( isWord(t, "do")) {
			Sequence body = compoundList(Set.of("done"), Set.of());
			expectWord("done");
			return body;
		}
		if( isWord(t, "{")) {
			Sequence body = compoundList(Set.of("}"), Set.of());
			expectWord("}");
			return body;
		}
		throw unexpected(t);
	}

	private Command forCommand() {
		Token f = take();
		if( isOp(peek(), "(") && ch(pos) == '(' ) {
			// for (( init; condition; step ))
			take();
			int close = arithClose(pos+1);
			if( close < 0 ) {
				throw eof("))");
			}
			int tail = src.length()-close;
			Word [] parts = splitArithFor(pos+1, close);
			pos = src.length()-tail+2;
			ArithFor c = new ArithFor();
			c.start = f.start;
			c.line = lineOf(c.start);
			c.init = parts[0];
			c.condition = parts[1];
			c.step = parts[2];
			if( isOp(peek(), ";")) {
				take();
			}
			skipNewlines();
			c.body = doGroup();
			c.end = pos;
			return c;
		}
		For c = new For();
		c.start = f.start;
		c.line = lineOf(c.start);
		c.variable = name(take());
		c.words = inWords();
		c.body = doGroup();
		c.end = pos;
		return c;
	}

	private Select selectCommand() {
		Select c = new Select();
		c.start = take().start;
		c.line = lineOf(c.start);
		c.variable = name(take());
		c.words = inWords();
		c.body = doGroup();
		c.end = pos;
		return c;
	}

	/** the variable of for and select, as written (bash checks that it is a name when it runs) */
	private String name(Token t) {
		if( t.kind != Kind.WORD ) {
			throw unexpected(t);
		}
		return t.word.raw;
	}

	/** [in words] then ; or newlines, before do: null if there is no in */
	private List<Word> inWords() {
		skipNewlines();
		List<Word> words = null;
		if( isWord(peek(), "in")) {
			take();
			words = new ArrayList<>();
			while( peek().kind == Kind.WORD ) {
				words.add(take().word);
			}
		}
		Token t = peek();
		if( isOp(t, ";") || t.kind == Kind.NEWLINE ) {
			take();
		}
		skipNewlines();
		return words;
	}

	/** the three parts of for (( a; b; c )) in [from, to), split at the ;s outside parentheses */
	private Word [] splitArithFor(int from, int to) {
		List<Word> parts = new ArrayList<>();
		int depth = 0;
		int start = from;
		int tail = src.length()-to;
		for (int i = from; i < src.length()-tail; i++) {
			char c = ch(i);
			int q = skipQuoted(i);
			if( q != i ) {
				// (quotes, $( ): their ; are theirs)
				i = q-1;
				continue;
			}
			if( c == '$' && ch(i+1) == '{' ) {
				i = braceClose(i+2, false);
				continue;
			}
			if( c == '$' && ch(i+1) == '(' && ch(i+2) != '(' ) {
				i = skipCommandSub(i)-1;
				continue;
			}
			if( c == '(' ) {
				depth++;
			} else if( c == ')' ) {
				depth--;
			} else if( c == ';' && depth == 0 ) {
				int after = src.length()-i;
				parts.add(arithWord(start, i));
				i = src.length()-after;
				start = i+1;
			}
		}
		parts.add(arithWord(start, src.length()-tail));
		if( parts.size() != 3 ) {
			// (more than three: the ; after the third is unexpected; and the command is shown)
			SyntaxError e = new SyntaxError(lineOf(from), parts.size() > 3 ? "syntax error: `;' unexpected" : "syntax error: arithmetic expression required");
			e.also = "syntax error: `(("+src.substring(from, src.length()-tail)+"))'";
			throw e;
		}
		return parts.toArray(new Word[0]);
	}

	/**
	 * The arithmetic expression in [from, to) (blanks around it trimmed) as a word: its $x, $( ),
	 * `...` are parts and its double quotes are removed, as in bash. pos is not changed.
	 */
	private Word arithWord(int from, int to) {
		while( from < to && Character.isWhitespace(ch(from))) {
			from++;
		}
		// (raw keeps the blanks after it, as bash's text of it does: set -x shows them)
		int trailing = 0;
		while( to > from && Character.isWhitespace(ch(to-1))) {
			to--;
			trailing++;
		}
		int saved = pos;
		Word w = new Word();
		w.start = from;
		w.line = lineOf(from);
		StringBuilder lit = new StringBuilder();
		pos = from;
		int tail = src.length()-to;
		while( pos < (to = src.length()-tail)) {
			char c = ch(pos);
			if( c == '\\' ) {
				char n = ch(pos+1);
				if( n == '\n' ) {
					pos += 2;
				} else if( "$`\"\\".indexOf(n) >= 0 ) {
					flush(w, lit);
					w.parts.add(new Word.Escaped(n));
					pos += 2;
				} else {
					lit.append(c);
					pos++;
				}
			} else if( c == '$' ) {
				int at = pos;
				Word.Part p;
				try {
					p = readDollar(true);
				} catch (SyntaxError e) {
					if( ch(at+1) != '{' ) {
						throw e;
					}
					// $(( ${x )): bash does not look for the } until it runs
					p = null;
				}
				to = src.length()-tail;
				if( p == null || pos > to ) {
					pos = at;
					lit.append('$');
					pos++;
				} else {
					flush(w, lit);
					w.parts.add(p);
				}
			} else if( c == '`' ) {
				flush(w, lit);
				w.parts.add(readBackquote());
			} else if( c == '"' ) {
				pos++;
			} else if( c == '[' ) {
				// a[subscript]: expanded once, as bash does (see Word.ArithSubscript)
				int close = arithSubscriptEnd(pos, to);
				if( close > pos+1 ) {
					flush(w, lit);
					w.parts.add(new Word.ArithSubscript(src.substring(pos+1, close)));
					pos = close+1;
				} else {
					lit.append(c);
					pos++;
				}
			} else {
				lit.append(c);
				pos++;
			}
		}
		flush(w, lit);
		w.end = to;
		w.raw = src.substring(from, to+trailing);
		pos = saved;
		return w;
	}

	/** the ] that closes the [ at open before to (nested [ ], quotes, \, $( ) ${ } skipped), or -1 */
	private int arithSubscriptEnd(int open, int to) {
		int depth = 0;
		for (int i = open; i < to; i++) {
			char c = ch(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '\'' ) {
				int end = src.indexOf('\'', i+1);
				if( end < 0 || end >= to ) {
					return -1;
				}
				i = end;
			} else if( c == '"' ) {
				for(i++; i < to && ch(i) != '"'; i++) {
					if( ch(i) == '\\' ) {
						i++;
					}
				}
			} else if( c == '$' && (ch(i+1) == '(' || ch(i+1) == '{')) {
				int end = ch(i+1) == '(' ? matchParen(i+1) : braceClose(i+2, false);
				if( end < 0 || end >= to ) {
					return -1;
				}
				i = end;
			} else if( c == '[' ) {
				depth++;
			} else if( c == ']' && --depth == 0 ) {
				return i;
			}
		}
		return -1;
	}

	private Case caseCommand() {
		Case c = new Case();
		c.start = take().start;
		c.line = lineOf(c.start);
		Token subject = take();
		if( subject.kind != Kind.WORD ) {
			throw unexpected(subject);
		}
		c.subject = subject.word;
		skipNewlines();
		expectWord("in");
		casePattern = true;
		skipNewlines();
		while( !isWord(peek(), "esac")) {
			if( peek().kind == Kind.EOF ) {
				throw unexpected(peek());
			}
			CaseClause cl = new CaseClause();
			cl.start = peek().start;
			cl.line = lineOf(cl.start);
			if( isOp(peek(), "(")) {
				take();
			}
			while( true ) {
				Token p = take();
				if( p.kind != Kind.WORD ) {
					throw unexpected(p);
				}
				cl.patterns.add(p.word);
				if( isOp(peek(), "|")) {
					take();
					continue;
				}
				expectOp(")");
				break;
			}
			casePattern = false;
			cl.body = list(Set.of("esac"), Set.of(";;", ";&", ";;&"));
			Token t = peek();
			if( t.kind == Kind.OP && (t.op.equals(";;") || t.op.equals(";&") || t.op.equals(";;&"))) {
				cl.terminator = take().op;
			}
			cl.end = pos;
			c.clauses.add(cl);
			casePattern = true;
			skipNewlines();
		}
		casePattern = false;
		c.end = take().end;
		return c;
	}

	/** [[ expression ]] */
	private Cond cond() {
		Cond c = new Cond();
		Token open = take();
		c.start = open.start;
		c.line = lineOf(c.start);
		condLook = null;
		condLine = c.line;
		CondToken t;
		try {
			c.expression = condOr();
			t = condTake(false);
		} catch (SyntaxError e) {
			throw condMore(e);
		}
		if( "EOF".equals(t.op)) {
			throw condMore(new SyntaxError(c.line, "unexpected EOF while looking for `]]'"));
		}
		if( !"]]".equals(t.op)) {
			throw condMore(condError(t));
		}
		c.end = pos;
		return c;
	}

	/** the line [[ is on */
	private int condLine;

	/** the token a [[ ]] error is at (for "syntax error near"), or null at the end of the text */
	private CondToken condAt;

	/** what bash says after a [[ ]] error: near the token and the line, or the end of the text */
	private SyntaxError condMore(SyntaxError e) {
		if( condAt == null || "EOF".equals(condAt.op)) {
			e.eofFrom = "[[";
			e.eofFromLine = condLine;
			e.eofLine = lineOf(src.length())-(src.endsWith("\n") ? 1 : 0)+1;
		} else {
			e.near = condText(condAt);
		}
		return e;
	}

	/** a token of [[ ]]: an operator (]] && || ( ) < >) or a word */
	private static final class CondToken {
		String op;
		Word word;
		int start;
	}

	/** the next token in [[ ]], read but not taken */
	private CondToken condLook;

	private static final Set<String> UNARY_TESTS = Set.of("-a", "-b", "-c", "-d", "-e", "-f", "-g", "-h", "-k", "-p", "-r", "-s", "-t", "-u", "-w", "-x",
			"-G", "-L", "-N", "-O", "-S", "-n", "-z", "-o", "-v", "-R");

	private CondToken condPeek() {
		if( condLook == null ) {
			condLook = condRead(false);
		}
		return condLook;
	}

	/** the next token (regex: the word after =~, where ( ) | < > are part of it) */
	private CondToken condTake(boolean regex) {
		CondToken ret = condLook != null ? condLook : condRead(regex);
		condLook = null;
		condAt = ret;
		return ret;
	}

	private CondToken condRead(boolean regex) {
		skipBlanks();
		CondToken t = new CondToken();
		t.start = pos;
		if( atEnd(pos)) {
			t.op = "EOF";
			return t;
		}
		if( ch(pos) == '\n' ) {
			t.op = "\n";
		} else if( src.startsWith("]]", pos) && (atEnd(pos+2) || isMeta(ch(pos+2)))) {
			t.op = "]]";
		} else if( !regex && (src.startsWith("&&", pos) || src.startsWith("||", pos))) {
			t.op = src.substring(pos, pos+2);
		} else if( !regex && "()<>".indexOf(ch(pos)) >= 0 ) {
			t.op = src.substring(pos, pos+1);
		} else if( !regex && isMeta(ch(pos))) {
			// & ; |: a token here too (what is wrong depends on where it is)
			t.op = operatorAt(pos);
		}
		if( t.op != null ) {
			pos += t.op.length();
		} else {
			t.word = readWord(regex);
		}
		return t;
	}

	private String condText(CondToken t) {
		return t.op == null ? t.word.raw : t.op.equals("\n") ? "newline" : t.op;
	}

	private SyntaxError condEof() {
		return new SyntaxError(condLine, "unexpected EOF while looking for `]]'");
	}

	private SyntaxError condError(CondToken t) {
		return new SyntaxError(lineOf(t.start), "syntax error in conditional expression: unexpected token `"+condText(t)+"'");
	}

	private Ast.CondExpr condOr() {
		Ast.CondExpr left = condAnd();
		while( "||".equals(condPeek().op)) {
			condTake(false);
			left = new Ast.CondOr(left, condAnd());
		}
		return left;
	}

	private Ast.CondExpr condAnd() {
		Ast.CondExpr left = condTerm();
		while( "&&".equals(condPeek().op)) {
			condTake(false);
			left = new Ast.CondAnd(left, condTerm());
		}
		return left;
	}

	private Ast.CondExpr condTerm() {
		// newlines may come before a term
		while( "\n".equals(condPeek().op)) {
			condTake(false);
		}
		CondToken t = condTake(false);
		if( "(".equals(t.op)) {
			Ast.CondExpr e = condOr();
			CondToken close = condTake(false);
			if( !")".equals(close.op)) {
				throw new SyntaxError("EOF".equals(close.op) ? condLine : lineOf(close.start), "unexpected token `"+condText(close)+"', expected `)'");
			}
			condNewlines();
			return e;
		}
		if( "EOF".equals(t.op)) {
			throw condEof();
		}
		if( t.word == null ) {
			if( "]]".equals(t.op) || ")".equals(t.op)) {
				throw condError(t);
			}
			throw new SyntaxError(lineOf(t.start), "unexpected token `"+condText(t)+"' in conditional command");
		}
		String text = t.word.plainText();
		if( "!".equals(text)) {
			return new Ast.CondNot(condTerm());
		}
		if( text != null && UNARY_TESTS.contains(text)) {
			CondToken operand = condTake(false);
			if( "EOF".equals(operand.op)) {
				throw condEof();
			}
			if( operand.word == null ) {
				throw new SyntaxError(lineOf(operand.start), "unexpected argument `"+condText(operand)+"' to conditional unary operator");
			}
			condNewlines();
			return new Ast.CondUnary(text, operand.word);
		}
		CondToken next = condPeek();
		if( next.op != null && Set.of("]]", "&&", "||", ")", "EOF").contains(next.op)) {
			return new Ast.CondWord(t.word);
		}
		condAt = next;
		String op = next.op != null ? next.op : next.word.plainText();
		if( op == null || !(BINARY_TESTS.contains(op) || op.equals("=~"))) {
			throw new SyntaxError(lineOf(next.start), "unexpected token `"+condText(next)+"', conditional binary operator expected");
		}
		condTake(false);
		CondToken right = condTake(op.equals("=~"));
		if( "EOF".equals(right.op)) {
			throw condEof();
		}
		if( right.word == null ) {
			throw new SyntaxError(lineOf(right.start), "unexpected argument `"+condText(right)+"' to conditional binary operator");
		}
		condNewlines();
		return new Ast.CondBinary(t.word, op, right.word);
	}

	/** after a test (but not a word alone) newlines may come */
	private void condNewlines() {
		while( "\n".equals(condPeek().op)) {
			condTake(false);
		}
	}
}
