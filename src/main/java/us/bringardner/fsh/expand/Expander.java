package us.bringardner.fsh.expand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.GlobPattern;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.expand.Arithmetic;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;
import us.bringardner.fsh.syntax.Parser.Fragment;
import us.bringardner.fsh.syntax.Word;

/**
 * Word expansion as bash does it, in bash's order: brace expansion; then, left to right, tilde,
 * parameter and variable expansion, command and arithmetic substitution (and process
 * substitution); then word splitting of the unquoted results on IFS; then filename expansion of
 * unquoted patterns; then quote removal.
 * <p>
 * The pieces of a word are kept apart with how they were written (unquoted text, quoted text, or
 * the value of an unquoted expansion) until the end, so splitting and globbing see exactly what
 * bash's do: "$x" is never split, $x is, and a * that came from quotes is not a pattern.
 * <p>
 * What a word is for decides how much of this happens: {@link #expand} for a command's words
 * (everything), {@link #string} for values that are not split or globbed (assignments,
 * here-strings, case words), {@link #pattern} for patterns (case, [[ == ]], ${x#pattern}).
 */
public final class Expander {

	/** what expansion needs from the shell that runs the commands */
	public interface Host {
		/** run $( ) (body null: parse text first; backquote: `...` as written) and return its output */
		String commandOutput(Ast.Sequence body, String text, boolean backquote);

		/** run ${ list; } in this shell and return its output, or, for ${| list; }, $REPLY */
		String functionOutput(Ast.Sequence body, String text, boolean reply);

		/** start <( ) or >( ) and return the path that reads or writes it */
		String processSubstitution(char direction, Ast.Sequence body, String text);

		/** an error bash reports and goes on from (m[]: bad array subscript): the message only */
		default void warning(String message) {
		}
	}

	private final ShellContext sc;
	private final Host host;

	public Expander(ShellContext sc, Host host) {
		this.sc = sc;
		this.host = host;
	}

	// ------------------------------------------------------------------ pieces

	/** unquoted text of the word itself: not split, a pattern */
	private static final int TEXT = 0;
	/** quoted text: not split, not a pattern */
	private static final int QUOTED = 1;
	/** the value of an unquoted expansion: split on IFS, a pattern */
	private static final int EXPANDED = 2;
	/** between two words of $@ or ${a[@]}; text is what joins them where words are not split */
	private static final int BREAK = 3;

	private record Piece(int kind, String text) {
	}

	// ------------------------------------------------------------------ what words are for

	/** command words: each word expanded fully, the results in order */
	public List<String> words(List<Word> words) {
		List<String> ret = new ArrayList<>();
		for(Word w : words) {
			ret.addAll(expand(w));
		}
		return ret;
	}

	/** a command word: braces, expansions, splitting, filename expansion and quote removal */
	public List<String> expand(Word w) {
		List<String> ret = new ArrayList<>();
		List<Word> words = option(Console.Option.DoBraceExpantion) ? Braces.expand(w) : List.of(w);
		for(Word b : words) {
			List<Piece> pieces = new ArrayList<>();
			word(b, pieces, looksLikeAssignment(b) ? TILDE_ARGUMENT : TILDE_START);
			for(Field f : split(pieces)) {
				glob(f, ret);
			}
		}
		return ret;
	}

	/** a value that is not split or globbed (a case word, a here-string, [[ ]] operands) */
	public String string(Word w) {
		List<Piece> pieces = new ArrayList<>();
		word(w, pieces, TILDE_START);
		return join(pieces);
	}

	/** the value of an assignment: as string, and ~ after a : is expanded too (PATH=~/bin:~/x) */
	public String assignment(Word w) {
		List<Piece> pieces = new ArrayList<>();
		word(w, pieces, TILDE_ASSIGNMENT);
		return join(pieces);
	}

	/**
	 * A pattern (case, [[ == ]], ${x#pattern}): * ? [...] from the word's unquoted text or
	 * unquoted expansions are wildcards; a quoted character is escaped with \ (see Glob.toRegex).
	 */
	public String pattern(Word w) {
		List<Piece> pieces = new ArrayList<>();
		word(w, pieces, TILDE_START);
		return patternOf(pieces);
	}

	/** the regular expression of [[ x =~ word ]]: quoted characters stand for themselves */
	public String regex(Word w) {
		List<Piece> pieces = new ArrayList<>();
		word(w, pieces, TILDE_START);
		StringBuilder ret = new StringBuilder();
		// in a bracket expression (opened by an unquoted [) a quoted character is just itself, as
		// bash's: [']'] is []]
		boolean inBracket = false;
		// characters of the bracket so far (a ] first is one of them), and in a [:class:]
		int seen = 0;
		char inClass = 0;
		for(Piece p : pieces) {
			boolean quoted = p.kind == QUOTED || p.kind == BREAK;
			String t = p.text;
			for (int i = 0; i < t.length(); i++) {
				char c = t.charAt(i);
				if( quoted ) {
					if( inBracket ) {
						seen++;
					} else if( "\\^$.|?*+()[]{}".indexOf(c) >= 0 ) {
						ret.append('\\');
					}
					ret.append(c);
					continue;
				}
				ret.append(c);
				if( !inBracket ) {
					if( c == '\\' && i+1 < t.length()) {
						ret.append(t.charAt(++i));
					} else if( c == '[' ) {
						inBracket = true;
						seen = 0;
					}
				} else if( inClass != 0 ) {
					if( c == ']' && ret.length() >= 2 && ret.charAt(ret.length()-2) == inClass ) {
						inClass = 0;
					}
				} else if( c == '[' && i+1 < t.length() && ":=.".indexOf(t.charAt(i+1)) >= 0 ) {
					inClass = t.charAt(i+1);
					ret.append(t.charAt(++i));
					seen++;
				} else if( c == '^' && seen == 0 && ret.charAt(ret.length()-2) == '[' ) {
					// (a ^ first: then a ] is still the first)
				} else if( c == ']' && seen > 0 ) {
					inBracket = false;
				} else {
					seen++;
				}
			}
		}
		return ret.toString();
	}

	/** set -o posix in a shell that is not interactive */
	public boolean posixScript() {
		return sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix) && !sc.console.isInteractive;
	}

	/** expanding a here-document's body (where $'..' in ${x:-word} is text) */
	private boolean inHereDocument;

	/** the body of a here-document whose word was not quoted: $ ` and \ count, quotes are text */
	public String hereDocument(String body) {
		List<Piece> pieces = new ArrayList<>();
		boolean was = inHereDocument;
		inHereDocument = true;
		try {
			word(Parser.fragment(body, Fragment.HERE_DOCUMENT), pieces, TILDE_NONE);
		} catch (us.bringardner.fsh.syntax.SyntaxError e) {
			throw commandSubstitutionError(e);
		} finally {
			inHereDocument = was;
		}
		return join(pieces);
	}

	/** a $( that does not end, found as a word is expanded: said as bash says it (its line is two on from the command's) */
	private ExpansionError commandSubstitutionError(us.bringardner.fsh.syntax.SyntaxError e) {
		String prefix = sc.errorPrefix();
		int at = prefix.lastIndexOf(": line ");
		String where = at < 0 ? prefix : prefix.substring(0, at)+": command substitution: line "+(sc.currentLine()+2)+": ";
		ExpansionError x = new ExpansionError(where+e.getMessage());
		x.whole = true;
		return x;
	}

	/** an arithmetic expression ($(( )), (( )), the parts of for (( ))): expanded, then evaluated */
	public Number arithmetic(Word w) {
		return evaluate(arithmeticText(w)+trailingBlanks(w));
	}

	/**
	 * An operand of [[ a -eq b ]]: expanded as a word, but a[subscript] in it as in (( )) (the
	 * subscript expanded once and quoted: [[ assoc[$key] -eq 1 ]] with key='x],b[$(cmd)'), as bash's
	 */
	public String arithmeticOperand(Word w) {
		String raw = w.raw;
		if( raw == null || raw.indexOf('[') < 0 ) {
			return string(w);
		}
		StringBuilder out = new StringBuilder();
		int seg = 0;
		int i = 0;
		while( i < raw.length()) {
			char c = raw.charAt(i);
			if( c == '\\' ) {
				i += 2;
			} else if( c == '\'' ) {
				int e = raw.indexOf('\'', i+1);
				i = e < 0 ? raw.length() : e+1;
			} else if( c == '"' || c == '`' ) {
				for(i++; i < raw.length() && raw.charAt(i) != c; i++) {
					if( raw.charAt(i) == '\\' ) {
						i++;
					}
				}
				i++;
			} else if( c == '$' && i+1 < raw.length() && (raw.charAt(i+1) == '(' || raw.charAt(i+1) == '{')) {
				i = closing(raw, i+1)+1;
			} else if( c == '[' ) {
				int close = subscriptClose(raw, i);
				if( close > i+1 ) {
					out.append(string(Parser.fragment(raw.substring(seg, i), Fragment.WORD)));
					out.append('[');
					for(char k : string(Parser.fragment(raw.substring(i+1, close), Fragment.WORD)).toCharArray()) {
						if( "[]$`~\\'\"".indexOf(k) >= 0 ) {
							out.append('\\');
						}
						out.append(k);
					}
					out.append(']');
					i = close+1;
					seg = i;
				} else {
					i++;
				}
			} else {
				i++;
			}
		}
		out.append(string(Parser.fragment(raw.substring(seg), Fragment.WORD)));
		return out.toString();
	}

	/** the ) or } that closes the ( or { at open (nested ones and quotes skipped), or the end */
	private static int closing(String s, int open) {
		char o = s.charAt(open);
		char c = o == '(' ? ')' : '}';
		int depth = 0;
		for (int i = open; i < s.length(); i++) {
			char x = s.charAt(i);
			if( x == '\\' ) {
				i++;
			} else if( x == '\'' ) {
				int e = s.indexOf('\'', i+1);
				i = e < 0 ? s.length() : e;
			} else if( x == o ) {
				depth++;
			} else if( x == c && --depth == 0 ) {
				return i;
			}
		}
		return s.length();
	}

	/** the ] that closes the [ at open (nested, quotes and expansions skipped), or -1 */
	public static int subscriptClose(String s, int open) {
		int depth = 0;
		for (int i = open; i < s.length(); i++) {
			char x = s.charAt(i);
			if( x == '\\' ) {
				i++;
			} else if( x == '\'' ) {
				int e = s.indexOf('\'', i+1);
				if( e < 0 ) {
					return -1;
				}
				i = e;
			} else if( x == '$' && i+1 < s.length() && (s.charAt(i+1) == '(' || s.charAt(i+1) == '{')) {
				i = closing(s, i+1);
			} else if( x == '[' ) {
				depth++;
			} else if( x == ']' && --depth == 0 ) {
				return i;
			}
		}
		return -1;
	}

	/** an indexed array's subscript in a[sub]=v: expanded and evaluated as it is (quotes kept) */
	public Number subscript(Word w) {
		try {
			return Arithmetic.evaluateLiteral(arithmeticText(w), sc);
		} catch (Arithmetic.ArithmeticError e) {
			throw new ExpansionError(e.getMessage());
		}
	}

	/** the blanks after an arithmetic expression as written (bash's errors show them) */
	public static String trailingBlanks(Word w) {
		return w.raw == null ? "" : w.raw.substring(w.raw.stripTrailing().length());
	}

	/** an arithmetic expression expanded ($x, $( ) ...), not yet evaluated (what set -x shows) */
	public String arithmeticText(Word w) {
		List<Piece> pieces = new ArrayList<>();
		word(w, pieces, TILDE_NONE);
		return join(pieces);
	}

	/** evaluate an expanded arithmetic expression; an error is an ExpansionError */
	public Number evaluate(String text) {
		try {
			return Arithmetic.evaluate(text, sc);
		} catch (Arithmetic.Unbound e) {
			throw new ExpansionError(e.getMessage(), ExpansionError.Kind.FATAL);
		} catch (Arithmetic.ArithmeticError e) {
			ExpansionError x = new ExpansionError(e.getMessage());
			x.bare = e.bare;
			throw x;
		} catch (ShellContext.ReadonlyException e) {
			// (said as the assignment would say it: no ((: before it)
			ExpansionError x = new ExpansionError(e.getMessage());
			x.bare = true;
			throw x;
		}
	}

	// ------------------------------------------------------------------ words and parts

	private static final int TILDE_NONE = 0;
	private static final int TILDE_START = 1;
	private static final int TILDE_ASSIGNMENT = 2;
	/** an argument that looks like an assignment (x=~/a:~/b): after its = and its :s, as bash does */
	private static final int TILDE_ARGUMENT = 3;

	private static final Pattern ASSIGNMENT_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\[[^]]*\\])?\\+?=.*", Pattern.DOTALL);

	private static boolean looksLikeAssignment(Word w) {
		return !w.parts.isEmpty() && w.parts.get(0) instanceof Word.Literal l && ASSIGNMENT_WORD.matcher(l.text()).matches();
	}

	private void word(Word w, List<Piece> out, int tilde) {
		try {
			word0(w, out, tilde);
		} catch (BadSubstitution b) {
			b.locate(w.raw != null && !w.raw.isEmpty() ? w.raw : source(w.parts));
			if( b.kind != ExpansionError.Kind.FATAL && sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix) && !sc.console.isInteractive ) {
				// (posix mode: a script ends, as bash's)
				throw new BadSubstitution(b, ExpansionError.Kind.FATAL);
			}
			throw b;
		}
	}

	/** "${x:}: bad substitution" says the word it is in (or the "..." it is in), as bash does */
	static final class BadSubstitution extends ExpansionError {
		private static final long serialVersionUID = 1L;
		private String where;

		BadSubstitution(String text) {
			super(text);
			where = text;
		}

		BadSubstitution(BadSubstitution b, Kind kind) {
			super(b.where, kind);
			where = b.where;
			located = true;
		}

		void locate(String text) {
			if( !located ) {
				where = text;
				located = true;
			}
		}

		private boolean located;

		@Override
		public String getMessage() {
			return where+": bad substitution";
		}
	}

	/** parts as they were written */
	private static String source(List<Word.Part> parts) {
		StringBuilder ret = new StringBuilder();
		for(Word.Part p : parts) {
			switch (p) {
			case Word.Literal l -> ret.append(l.text());
			case Word.Escaped e -> ret.append('\\').append(e.c());
			case Word.SingleQuoted q -> ret.append('\'').append(q.text()).append('\'');
			case Word.AnsiC a -> ret.append("$'").append(a.text()).append('\'');
			case Word.DoubleQuoted d -> ret.append(d.locale() ? "$\"" : "\"").append(source(d.parts())).append('"');
			case Word.Param n -> ret.append('$').append(n.name());
			case Word.ParamExpansion x -> ret.append("${").append(x.body()).append('}');
			case Word.CommandSub c -> ret.append("$(").append(c.text()).append(')');
			case Word.Backquote b -> ret.append('`').append(b.text()).append('`');
			case Word.ArithSub a -> ret.append("$((").append(a.expression().raw).append("))");
			case Word.ArithSubscript a -> ret.append('[').append(a.text()).append(']');
			default -> {
			}
			}
		}
		return ret.toString();
	}

	private void word0(Word w, List<Piece> out, int tilde) {
		List<Word.Part> parts = w.parts;
		for (int i = 0; i < parts.size(); i++) {
			Word.Part p = parts.get(i);
			int used = indexed(parts, i, TEXT, out);
			if( used >= 0 ) {
				String rest = ((Word.Literal) parts.get(i+1)).text().substring(used);
				if( !rest.isEmpty()) {
					out.add(new Piece(TEXT, rest));
				}
				i++;
			} else if( tilde != TILDE_NONE && p instanceof Word.Literal l ) {
				tilde(l.text(), i == 0, i == parts.size()-1, tilde, out);
			} else {
				part(p, TEXT, out);
			}
		}
	}

	/**
	 * $name[index] of an array (an fsh extension; bash reads it as ${name}[index]): parts[i] is
	 * $name and parts[i+1] text that starts with [index]. The element goes to out.
	 * @return how much of parts[i+1] it used, or -1 if this is not one
	 */
	private int indexed(List<Word.Part> parts, int i, int context, List<Piece> out) {
		if( !(parts.get(i) instanceof Word.Param pa) || i+1 >= parts.size() || !(parts.get(i+1) instanceof Word.Literal l)
				|| !l.text().startsWith("[") || !isName(pa.name())) {
			return -1;
		}
		Object v = sc.getVariable(pa.name());
		if( !(v instanceof List<?>) && !(v instanceof Map<?,?>)) {
			return -1;
		}
		int close = l.text().indexOf(']');
		if( close < 2 ) {
			return -1;
		}
		parameter(ParamExpr.parse(pa.name()+l.text().substring(0, close+1)), context, out);
		return close+1;
	}

	/**
	 * Unquoted text that may have a ~ to expand: at the start of the word (first) or, in an
	 * assignment, after a :. The ~ and the name after it (~user) go up to a / (or a :), and must
	 * all be unquoted text: ~"x" is not expanded.
	 */
	private void tilde(String text, boolean first, boolean last, int mode, List<Piece> out) {
		boolean colons = mode == TILDE_ASSIGNMENT || mode == TILDE_ARGUMENT;
		int equals = mode == TILDE_ARGUMENT && first ? text.indexOf('=') : -1;
		int done = 0;
		for (int i = 0; i < text.length(); i++) {
			boolean candidate = text.charAt(i) == '~' && ((i == 0 && first && mode != TILDE_ARGUMENT)
					|| (colons && i > 0 && (text.charAt(i-1) == ':' || i-1 == equals)));
			if( !candidate ) {
				continue;
			}
			int end = i+1;
			while( end < text.length() && text.charAt(end) != '/' && !(colons && text.charAt(end) == ':')) {
				end++;
			}
			if( end == text.length() && !last ) {
				// the prefix goes on into quotes or an expansion
				continue;
			}
			String home = home(text.substring(i+1, end));
			if( home == null ) {
				continue;
			}
			if( i > done ) {
				out.add(new Piece(TEXT, text.substring(done, i)));
			}
			out.add(new Piece(QUOTED, home));
			done = end;
			i = end-1;
		}
		if( done < text.length()) {
			out.add(new Piece(TEXT, text.substring(done)));
		}
	}

	/** ~ $HOME, ~+ $PWD, ~- $OLDPWD, ~user that user's home; null if it is none of these */
	private String home(String user) {
		switch (user) {
		case "":
			Object home = sc.getVariable("HOME");
			return home != null ? home.toString() : System.getProperty("user.home");
		case "+":
			return str(sc.getVariable("PWD"));
		case "-":
			return str(sc.getVariable("OLDPWD"));
		default:
			if( user.matches("[+-]?[0-9]+")) {
				// ~N ~+N ~-N: an entry of the directory stack, as dirs +N (-N) prints it
				Object stack = sc.getVariable("DIRSTACK");
				if( stack instanceof List<?> l ) {
					long n = Long.parseLong(user.startsWith("+") ? user.substring(1) : user);
					long i = user.startsWith("-") ? l.size()-1+n : n;
					return i >= 0 && i < l.size() ? str(l.get((int) i)) : null;
				}
				return null;
			}
			if( !user.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) {
				return null;
			}
			if( user.equals(System.getProperty("user.name"))) {
				return System.getProperty("user.home");
			}
			if( user.equals("root")) {
				return new File("/var/root").isDirectory() ? "/var/root" : "/root";
			}
			for(String dir : new String[] {"/Users/", "/home/"}) {
				if( new File(dir+user).isDirectory()) {
					return dir+user;
				}
			}
			return null;
		}
	}

	/**
	 * A part of a word, in a context: TEXT (the word itself), QUOTED (inside "..."), or EXPANDED
	 * (the word of an unquoted ${x:-word}, whose text is split like a value).
	 * @return false only for a quoted $@ or ${a[@]} that is empty (it gives no word at all)
	 */
	private boolean part(Word.Part p, int context, List<Piece> out) {
		try {
			return part0(p, context, out);
		} catch (BadSubstitution b) {
			if( p instanceof Word.DoubleQuoted d ) {
				b.locate(source(d.parts()));
			}
			throw b;
		}
	}

	private boolean part0(Word.Part p, int context, List<Piece> out) {
		switch (p) {
		case Word.Literal l -> out.add(new Piece(context, l.text()));
		case Word.Escaped e -> out.add(new Piece(QUOTED, String.valueOf(e.c())));
		case Word.SingleQuoted s -> out.add(new Piece(QUOTED, s.text()));
		case Word.AnsiC a -> out.add(new Piece(QUOTED, ShellContext.ansiC(a.text())));
		case Word.DoubleQuoted d -> {
			int before = out.size();
			boolean content = d.parts().isEmpty();
			boolean emptyAt = false;
			boolean literal = false;
			List<Word.Part> inner = d.parts();
			for (int i = 0; i < inner.size(); i++) {
				int used = indexed(inner, i, QUOTED, out);
				if( used >= 0 ) {
					String rest = ((Word.Literal) inner.get(i+1)).text().substring(used);
					if( !rest.isEmpty()) {
						out.add(new Piece(QUOTED, rest));
					}
					content = true;
					i++;
					continue;
				}
				Word.Part q = inner.get(i);
				boolean r = part(q, QUOTED, out);
				// (false: a "$@" with nothing in it)
				emptyAt |= !r;
				literal |= (q instanceof Word.Literal l && !l.text().isEmpty()) || q instanceof Word.Escaped;
				content |= r;
			}
			if( emptyAt && !literal && out.subList(before, out.size()).stream().allMatch(x -> x.text.isEmpty())) {
				// "$empty$@" with no positional parameters: no word, as in bash
				out.subList(before, out.size()).clear();
				return false;
			}
			if( out.size() == before && content ) {
				// "" and "$empty" are an empty word
				out.add(new Piece(QUOTED, ""));
			}
		}
		case Word.Param pa -> {
			return parameter(ParamExpr.simple(pa.name()), context, out);
		}
		case Word.ParamExpansion pe -> {
			try {
				return parameter(ParamExpr.parse(pe.body(), sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)), context, out);
			} catch (us.bringardner.fsh.syntax.SyntaxError e) {
				// a $( in the word that does not end: said as bash says it, and the command is not run
				throw commandSubstitutionError(e);
			}
		}
		case Word.CommandSub cs -> value(trimNewlines(host.commandOutput(cs.body(), cs.text(), false)), context, out);
		case Word.Backquote b -> value(trimNewlines(host.commandOutput(null, b.text(), true)), context, out);
		case Word.FunctionSub fs -> {
			String v = host.functionOutput(fs.body(), fs.text(), fs.reply());
			value(fs.reply() ? v : trimNewlines(v), context, out);
		}
		case Word.ArithSub a -> {
			String n;
			try {
				n = String.valueOf(arithmetic(a.expression()));
			} catch (ExpansionError x) {
				if( x.kind != ExpansionError.Kind.FATAL && sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix) && !sc.console.isInteractive ) {
					// (posix mode: a script ends, as bash's)
					ExpansionError f = new ExpansionError(x.getMessage(), ExpansionError.Kind.FATAL);
					f.bare = x.bare;
					throw f;
				}
				throw x;
			}
			value(n, context, out);
		}
		case Word.ArithSubscript a -> {
			// expanded as a word (quotes removed), then [ ] $ ` ~ \ ' " quoted with \
			StringBuilder q = new StringBuilder("[");
			for(char c : string(Parser.fragment(a.text(), Fragment.WORD)).toCharArray()) {
				if( "[]$`~\\'\"".indexOf(c) >= 0 ) {
					q.append('\\');
				}
				q.append(c);
			}
			out.add(new Piece(QUOTED, q.append(']').toString()));
		}
		case Word.ProcessSub ps -> out.add(new Piece(QUOTED, host.processSubstitution(ps.direction(), ps.body(), ps.text())));
		}
		return true;
	}

	/** an expansion's value: quoted in "...", otherwise split and a pattern */
	private static void value(String s, int context, List<Piece> out) {
		out.add(new Piece(context == QUOTED ? QUOTED : EXPANDED, s == null ? "" : s));
	}

	private static String trimNewlines(String s) {
		if( s == null ) {
			return "";
		}
		int end = s.length();
		while( end > 0 && s.charAt(end-1) == '\n' ) {
			end--;
		}
		return s.substring(0, end);
	}

	// ------------------------------------------------------------------ ${ }

	/**
	 * ${...} read: an optional # (length) or ! (indirection, ${!a[@]} keys, ${!prefix*} names),
	 * the parameter (a name with an optional [subscript], digits, or one of @ * # ? - $ ! 0), and an
	 * operator with its words.
	 */
	static final class ParamExpr {
		/** the text between ${ and } (or the name of $name) */
		String text;
		/** 0, '#' or '!' */
		char prefix;
		String name;
		/** between [ and ], as written; null if there is none */
		String subscript;
		/**
		 * null, :- - := = :? ? :+ + # ## % %% / // /# /% ^ ^^ , ,, ~ ~~ : (substring) @ (transform),
		 * keys (${!a[@]}), names@ names* (${!prefix@})
		 */
		String op;
		/** the word, pattern or offset after op */
		String arg;
		/** the string of ${x/pattern/string}, the length of ${x:offset:length}; null if not given */
		String arg2;
		/** written as $name, not ${ } */
		boolean simple;
		/** ${!9} of an unset $9: no parameter at all (unset) */
		boolean noTarget;
		/** ${!ref}: !ref, as set -u's message names it */
		String via;

		static ParamExpr simple(String name) {
			ParamExpr e = new ParamExpr();
			e.text = name;
			e.name = name;
			e.simple = true;
			return e;
		}

		/** the way bash names it in messages: name, a[1], 1 */
		String display() {
			if( simple && !name.isEmpty() && Character.isDigit(name.charAt(0))) {
				// $9 (${9} is 9), as bash says it
				return "$"+name;
			}
			return subscript == null ? name : name+"["+subscript+"]";
		}

		ExpansionError bad() {
			return new BadSubstitution("${"+text+"}");
		}

		static ParamExpr parse(String body) {
			return parse(body, false);
		}

		/** (in posix mode ${!?} and ${!#...} are $! with an operator, as bash's) */
		static ParamExpr parse(String body, boolean posix) {
			ParamExpr e = new ParamExpr();
			e.text = body;
			int n = body.length();
			if( n == 0 ) {
				throw e.bad();
			}
			char c0 = body.charAt(0);
			if( c0 == '#' && n > 1 ) {
				int end = paramEnd(body, 1);
				if( end == n ) {
					e.prefix = '#';
					e.setParam(body, 1, end);
					return e;
				}
				if( n == 2 ) {
					// ${#/}, ${#+}: the length of what is no parameter
					throw e.bad();
				}
			} else if( c0 == '!' && n > 2 && "-=+?:".indexOf(body.charAt(1)) >= 0 ) {
				// ${!-word}, ${!:-word}: $! with an operator (not indirection)
			} else if( c0 == '!' && posix && (body.charAt(1) == '?' || body.charAt(1) == '#')) {
				// (posix mode: $! with ? or #)
			} else if( c0 == '!' && n == 2 && (body.charAt(1) == '$' || body.charAt(1) == '!')) {
				// ${!$}, ${!!}: no indirection of those
				throw e.bad();
			} else if( c0 == '!' && n > 1 ) {
				int end = paramEnd(body, 1);
				if( end > 1 ) {
					if( end == n-1 && (body.charAt(end) == '*' || body.charAt(end) == '@') && isName(body.substring(1, end))) {
						e.prefix = '!';
						e.name = body.substring(1, end);
						e.op = "names"+body.charAt(end);
						return e;
					}
					e.prefix = '!';
					e.setParam(body, 1, end);
					if( end == n && ("@".equals(e.subscript) || "*".equals(e.subscript))) {
						e.op = "keys";
						return e;
					}
					return e.operator(body, end);
				}
			}
			int end = paramEnd(body, 0);
			if( end < 0 ) {
				throw e.bad();
			}
			e.setParam(body, 0, end);
			return e.operator(body, end);
		}

		private void setParam(String body, int from, int end) {
			int bracket = body.indexOf('[', from);
			if( bracket > 0 && bracket < end ) {
				name = body.substring(from, bracket);
				subscript = body.substring(bracket+1, end-1);
			} else {
				name = body.substring(from, end);
			}
		}

		/** where the parameter that starts at from ends, or -1 if there is none there */
		private static int paramEnd(String body, int from) {
			int n = body.length();
			if( from >= n ) {
				return -1;
			}
			char c = body.charAt(from);
			if( c == '_' || Character.isLetter(c)) {
				int i = from+1;
				while( i < n && (body.charAt(i) == '_' || Character.isLetterOrDigit(body.charAt(i)))) {
					i++;
				}
				if( i < n && body.charAt(i) == '[' ) {
					int close = closeBracket(body, i);
					return close < 0 ? -1 : close+1;
				}
				return i;
			}
			if( Character.isDigit(c)) {
				int i = from+1;
				while( i < n && Character.isDigit(body.charAt(i))) {
					i++;
				}
				return i;
			}
			return "@*#?-$!0".indexOf(c) >= 0 ? from+1 : -1;
		}

		/** the ] of the [ at open (nested [ ] and quotes skipped), or -1 */
		private static int closeBracket(String s, int open) {
			int depth = 0;
			for (int i = open; i < s.length(); i++) {
				char c = s.charAt(i);
				if( c == '\\' ) {
					i++;
				} else if( c == '\'' || c == '"' ) {
					int close = s.indexOf(c, i+1);
					if( close < 0 ) {
						return -1;
					}
					i = close;
				} else if( c == '[' ) {
					depth++;
				} else if( c == ']' && --depth == 0 ) {
					return i;
				}
			}
			return -1;
		}

		private ParamExpr operator(String body, int i) {
			int n = body.length();
			if( i == n ) {
				return this;
			}
			String rest = body.substring(i);
			char c = rest.charAt(0);
			if( c == ':' ) {
				if( rest.length() > 1 && "-=?+".indexOf(rest.charAt(1)) >= 0 ) {
					op = rest.substring(0, 2);
					arg = rest.substring(2);
					return this;
				}
				op = ":";
				String r = rest.substring(1);
				if( r.isEmpty()) {
					// ${x:}
					throw bad();
				}
				int colon = topLevelColon(r);
				arg = colon < 0 ? r : r.substring(0, colon);
				arg2 = colon < 0 ? null : r.substring(colon+1);
				return this;
			}
			if( "-=?+".indexOf(c) >= 0 ) {
				op = String.valueOf(c);
				arg = rest.substring(1);
				return this;
			}
			if( c == '#' || c == '%' || c == '^' || c == ',' || c == '~' ) {
				int len = rest.length() > 1 && rest.charAt(1) == c ? 2 : 1;
				op = rest.substring(0, len);
				arg = rest.substring(len);
				return this;
			}
			if( c == '/' ) {
				int len = rest.length() > 1 && "/#%".indexOf(rest.charAt(1)) >= 0 ? 2 : 1;
				op = rest.substring(0, len);
				// (a pattern may start with /: ${a///a/} replaces /a, as bash's)
				int slash = Parser.indexOfUnquoted(rest, '/', op.equals("//") && len < rest.length() && rest.charAt(len) == '/' ? len+1 : len);
				arg = slash < 0 ? rest.substring(len) : rest.substring(len, slash);
				arg2 = slash < 0 ? null : rest.substring(slash+1);
				return this;
			}
			if( c == '@' && rest.length() == 2 && "QEPAaKkUuL".indexOf(rest.charAt(1)) >= 0 ) {
				op = "@";
				arg = rest.substring(1);
				return this;
			}
			throw bad();
		}

		/** the : that ends the offset of ${x:offset:length} (not one in ( ), [ ] or ?: ), or -1 */
		private static int topLevelColon(String s) {
			int depth = 0;
			int ternary = 0;
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if( c == '\\' ) {
					i++;
				} else if( c == '\'' ) {
					// (quoted: no : in it counts)
					int end = s.indexOf('\'', i+1);
					i = end < 0 ? s.length() : end;
				} else if( c == '"' ) {
					for(i++; i < s.length() && s.charAt(i) != '"'; i++) {
						if( s.charAt(i) == '\\' ) {
							i++;
						}
					}
				} else if( c == '(' || c == '[' || c == '{' ) {
					// (${x:-0} and $(cmd) in it too)
					depth++;
				} else if( c == ')' || c == ']' || c == '}' ) {
					depth--;
				} else if( c == '?' && depth == 0 ) {
					ternary++;
				} else if( c == ':' && depth == 0 ) {
					if( ternary == 0 ) {
						return i;
					}
					ternary--;
				}
			}
			return -1;
		}
	}

	/** a parameter's value: one string (null if unset), or the words of $@ ${a[*]} ... */
	private static final class Val {
		String scalar;
		List<String> items;
		/** from * (joined by the first character of IFS when quoted) */
		boolean star;

		static Val of(String s) {
			Val v = new Val();
			v.scalar = s;
			return v;
		}

		static Val list(List<String> items, boolean star) {
			Val v = new Val();
			v.items = items;
			v.star = star;
			return v;
		}

		boolean isList() {
			return items != null;
		}

		boolean unset() {
			return isList() ? items.isEmpty() : scalar == null;
		}

		boolean isNull() {
			if( isList()) {
				return String.join("", items).isEmpty();
			}
			return scalar == null || scalar.isEmpty();
		}

		Val map(java.util.function.UnaryOperator<String> f) {
			if( isList()) {
				List<String> ret = new ArrayList<>();
				for(String s : items) {
					ret.add(f.apply(s));
				}
				return list(ret, star);
			}
			return of(f.apply(scalar == null ? "" : scalar));
		}
	}

	/** ${...} and $name into pieces; false for a quoted empty $@ (see part) */
	private boolean parameter(ParamExpr e, int context, List<Piece> out) {
		if( e.prefix == '!' ) {
			if( e.op != null && e.op.startsWith("names")) {
				List<String> names = new ArrayList<>();
				for(String n : new TreeSet<>(sc.getVariables().keySet())) {
					if( n.startsWith(e.name) && isName(n) && sc.getVariable(n) != null ) {
						names.add(n);
					}
				}
				return emit(Val.list(names, e.op.endsWith("*")), context, out);
			}
			if( "keys".equals(e.op)) {
				return emit(Val.list(keys(sc.getVariable(e.name)), e.subscript.equals("*")), context, out);
			}
			e = indirect(e);
		}
		if( e.prefix == '#' ) {
			Val v = base(e, true);
			int n = v.isList() ? v.items.size() : v.scalar == null ? 0 : v.scalar.codePointCount(0, v.scalar.length());
			value(String.valueOf(n), context, out);
			return true;
		}
		String op = e.op;
		boolean conditional = op != null && op.matches(":?[-=?+]");
		// (${x@a} and ${x@A} of an unset x are no error with set -u)
		boolean attributes = false;
		if( "@".equals(op) && e.arg != null && (e.arg.startsWith("a") || e.arg.startsWith("A"))) {
			// (only looked at here: reading $RANDOM changes it)
			Object raw = e.prefix == 0 && e.subscript == null && isName(e.name) ? sc.getVariable(e.name) : null;
			attributes = raw != null && !(raw instanceof List<?> l && l.isEmpty()) && !(raw instanceof Map<?,?> m && m.isEmpty());
		}
		Val v = base(e, !conditional && !attributes);
		if( op == null ) {
			return emit(v, context, out);
		}
		if( conditional ) {
			boolean use = op.startsWith(":") ? v.unset() || v.isNull() : v.unset();
			switch (op.charAt(op.length()-1)) {
			case '-':
				if( use ) {
					return paramWord(e.arg, context, out);
				}
				return emit(v, context, out);
			case '=': {
				if( !use ) {
					return emit(v, context, out);
				}
				String s = paramText(e.arg, context);
				assign(e, s);
				if( e.subscript == null ) {
					// the value as the variable has it (declare -i: 4+3 is 7; declare -u: upper case)
					Object now = sc.getVariable(e.name);
					if( now != null && !(now instanceof List<?>) && !(now instanceof Map<?,?>)) {
						s = now.toString();
					}
				}
				return emit(Val.of(s), context, out);
			}
			case '?':
				if( use ) {
					String msg = e.arg.isEmpty() ? (op.startsWith(":") ? "parameter null or not set" : "parameter not set") : paramText(e.arg, context);
					throw new ExpansionError(e.display()+": "+msg, ExpansionError.Kind.FATAL);
				}
				return emit(v, context, out);
			default:
				if( !use ) {
					return paramWord(e.arg, context, out);
				}
				return true;
			}
		}
		switch (op) {
		case "#":
		case "##": {
			GlobPattern rx = GlobPattern.compile(patternText(e.arg));
			boolean longest = op.length() == 2;
			return emit(v.map(s -> removePrefix(s, rx, longest)), context, out);
		}
		case "%":
		case "%%": {
			GlobPattern rx = GlobPattern.compile(patternText(e.arg));
			boolean longest = op.length() == 2;
			return emit(v.map(s -> removeSuffix(s, rx, longest)), context, out);
		}
		case "/":
		case "//":
		case "/#":
		case "/%":
			return emit(replace(v, e, context), context, out);
		case "^":
		case "^^":
		case ",":
		case ",,":
		case "~":
		case "~~": {
			GlobPattern rx = e.arg.isEmpty() ? null : GlobPattern.compile(patternText(e.arg));
			boolean all = op.length() == 2;
			char kind = op.charAt(0);
			return emit(v.map(s -> changeCase(s, rx, kind, all)), context, out);
		}
		case ":":
			return emit(substring(v, e), context, out);
		case "@":
			return emit(transform(v, e), context, out);
		default:
			throw e.bad();
		}
	}

	/** ${!name...}: the parameter that name's value names, with the same operator */
	private ParamExpr indirect(ParamExpr e) {
		if( e.subscript == null && sc.rawVariable(e.name) instanceof ShellContext.NameRef ) {
			// ${!ref} of declare -n ref=x: x
			ParamExpr ret = new ParamExpr();
			ret.text = e.text;
			ret.name = e.name;
			ret.prefix = 'n';
			ret.op = e.op;
			ret.arg = e.arg;
			ret.arg2 = e.arg2;
			return ret;
		}
		ParamExpr plain = new ParamExpr();
		plain.text = e.text;
		plain.name = e.name;
		plain.subscript = e.subscript;
		Val v = base(plain, false);
		String target = v.isList() ? String.join(" ", v.items) : v.scalar;
		if( target == null && !e.name.isEmpty() && Character.isDigit(e.name.charAt(0))
				|| (target == null || target.isEmpty()) && (e.name.equals("@") || e.name.equals("*"))) {
			// (${!@} with no parameters: nothing, as bash's)
			// ${!9:-word} of an unset $9: unset (bash's)
			ParamExpr ret = new ParamExpr();
			ret.text = e.text;
			ret.name = "!"+e.name;
			ret.noTarget = true;
			ret.op = e.op;
			ret.arg = e.arg;
			ret.arg2 = e.arg2;
			return ret;
		}
		if( target == null ) {
			throw new ExpansionError(e.display()+": invalid indirect expansion");
		}
		ParamExpr ret = new ParamExpr();
		ret.text = e.text;
		int end = ParamExpr.paramEnd(target, 0);
		if( end != target.length()) {
			throw new ExpansionError(target+": invalid variable name");
		}
		ret.via = "!"+e.display();
		ret.setParam(target, 0, end);
		ret.op = e.op;
		ret.arg = e.arg;
		ret.arg2 = e.arg2;
		return ret;
	}

	/** the value before the operator; unbound: set -u makes an unset one an error */
	private Val base(ParamExpr e, boolean unbound) {
		Val ret;
		String n = e.name;
		if( e.noTarget ) {
			ret = Val.of(null);
		} else if( e.prefix == 'n' ) {
			ret = Val.of(sc.resolveName(n));
		} else if( n.equals("@") || n.equals("*")) {
			ret = Val.list(strings(sc.getPositionalParameterValues()), n.equals("*"));
			return ret;
		} else if( Character.isDigit(n.charAt(0))) {
			int idx = Integer.parseInt(n);
			if( idx == 0 ) {
				ret = Val.of(str(sc.getVariable("$0")));
			} else {
				List<Object> pos = sc.getPositionalParameterValues();
				ret = Val.of(idx <= pos.size() ? str(pos.get(idx-1)) : null);
			}
		} else if( "#?-$!".indexOf(n.charAt(0)) >= 0 ) {
			ret = Val.of(str(sc.getVariable("$"+n)));
		} else if( e.subscript == null && isName(n) && sc.circular(n)) {
			// v -> w -> x -> v: said, and nothing
			sc.error("warning: "+n+": circular name reference");
			ret = Val.of(null);
		} else if( e.subscript == null && isName(n) && sc.selfReference(n)) {
			// a function's local -n v=v: said, and the global v
			sc.error("warning: "+n+": circular name reference");
			Object v = sc.getVariable(n);
			ret = Val.of(str(ShellContext.firstElement(v)));
		} else if( e.subscript == null && isName(n) && refToElement(n) != null ) {
			// a nameref to an element (declare -n r='x[2]'): that element (set -u says r)
			ParamExpr element = refToElement(n);
			element.via = n;
			return base(element, unbound);
		} else {
			Object v = sc.getVariable(n);
			if( e.subscript == null ) {
				ret = Val.of(str(ShellContext.firstElement(v)));
			} else if( e.subscript.equals("@") || e.subscript.equals("*")) {
				return Val.list(values(v), e.subscript.equals("*"));
			} else {
				ret = Val.of(str(element(v, e)));
			}
		}
		if( unbound && ret.scalar == null && option(Console.Option.NullParameterIsError)) {
			throw new ExpansionError((e.via != null ? e.via : e.display())+": unbound variable", ExpansionError.Kind.FATAL);
		}
		return ret;
	}

	/** name's nameref target as name[subscript], if it is an element; else null */
	private ParamExpr refToElement(String name) {
		if( !(sc.rawVariable(name) instanceof ShellContext.NameRef)) {
			return null;
		}
		String target = sc.resolveName(name);
		int b = target.indexOf('[');
		if( b <= 0 || !target.endsWith("]")) {
			return null;
		}
		ParamExpr ret = new ParamExpr();
		ret.text = target;
		ret.name = target.substring(0, b);
		ret.subscript = target.substring(b+1, target.length()-1);
		return ret;
	}

	/** a[i] (arithmetic) or m[key] (expanded) */
	private Object element(Object v, ParamExpr e) {
		if( v instanceof Map<?,?> m ) {
			String key = join(paramPieces(e.subscript));
			if( key.isEmpty()) {
				if( e.prefix == '#' ) {
					// ${#m[$unset]}: an error (${m[$unset]} is said, and empty)
					throw new ExpansionError("["+e.subscript+"]: bad array subscript");
				}
				host.warning(e.name+": bad array subscript");
				return null;
			}
			return m.get(key);
		}
		// (as in $(( )): no ~, and '...' is not removed)
		long idx = evaluate(arithmeticText(Parser.arithmeticFragment(e.subscript))).longValue();
		if( idx < 0 ) {
			long size = v instanceof FshList f ? (f.isEmpty() ? 0 : f.getIndexes().get(f.size()-1)+1)
					: v instanceof List<?> l ? l.size() : v == null ? 0 : 1;
			idx += size;
			if( idx < 0 ) {
				if( v == null ) {
					return null;
				}
				if( e.prefix == '#' ) {
					// ${#a[-10]}: an error
					throw new ExpansionError("["+e.subscript+"]: bad array subscript");
				}
				// ${a[-10]}: said, and empty
				host.warning(e.name+": bad array subscript");
				return null;
			}
		}
		if( v instanceof FshList f ) {
			return f.get((int) idx);
		}
		if( v instanceof List<?> l ) {
			return idx < l.size() ? l.get((int) idx) : null;
		}
		return idx == 0 ? v : null;
	}

	/** ${x:=word}: set x (not $1 or $?) */
	private void assign(ParamExpr e, String value) {
		if( !isName(e.name)) {
			throw new ExpansionError("$"+e.name+": cannot assign in this way");
		}
		if( sc.console.isReadonly(sc.readonlyName(e.name))) {
			// ${v:=x} of a readonly v: an error (said as the shell's)
			throw new ExpansionError(sc.readonlyName(e.name)+": readonly variable");
		}
		if( sc.console.isInteger(e.name)) {
			// declare -i: the value is arithmetic
			value = String.valueOf(evaluate(value));
		}
		if( e.subscript == null ) {
			sc.setVariable(e.name, value);
			return;
		}
		Object cur = sc.getVariable(e.name);
		if( cur instanceof Map<?,?> ) {
			sc.setVariable(e.name, join(paramPieces(e.subscript)), value);
		} else {
			sc.setVariable(e.name, (int) evaluate(arithmeticText(Parser.arithmeticFragment(e.subscript))).longValue(), value);
		}
	}

	/** a value into pieces: false for a quoted empty $@ */
	private boolean emit(Val v, int context, List<Piece> out) {
		if( !v.isList()) {
			value(v.scalar, context, out);
			return true;
		}
		if( context == QUOTED ) {
			if( v.star ) {
				out.add(new Piece(QUOTED, String.join(ifsFirst(), v.items)));
				return true;
			}
			if( v.items.isEmpty()) {
				return false;
			}
			for (int i = 0; i < v.items.size(); i++) {
				if( i > 0 ) {
					out.add(new Piece(BREAK, " "));
				}
				out.add(new Piece(QUOTED, v.items.get(i)));
			}
			return true;
		}
		String sep = v.star ? ifsFirst() : " ";
		for (int i = 0; i < v.items.size(); i++) {
			if( i > 0 ) {
				out.add(new Piece(BREAK, sep));
			}
			out.add(new Piece(EXPANDED, v.items.get(i)));
		}
		return true;
	}

	/** the word of ${x:-word} (and :+ - +) into pieces: quoted in "...", otherwise split */
	private boolean paramWord(String text, int context, List<Piece> out) {
		Word w = Parser.fragment(text, context == QUOTED || inHereDocument ? quotedParameter() : Fragment.WORD_WITH_PROCESSES);
		boolean ret = false;
		int before = out.size();
		List<Word.Part> parts = w.parts;
		for (int i = 0; i < parts.size(); i++) {
			Word.Part p = parts.get(i);
			if( i == 0 && context != QUOTED && p instanceof Word.Literal l && l.text().startsWith("~")) {
				List<Piece> t = new ArrayList<>();
				tilde(l.text(), true, parts.size() == 1, TILDE_START, t);
				for(Piece x : t) {
					out.add(x.kind == TEXT ? new Piece(EXPANDED, x.text) : x);
				}
				ret = true;
				continue;
			}
			boolean at = p instanceof Word.Param pa && pa.name().equals("@") || p instanceof Word.ParamExpansion px && px.body().equals("@");
			if( at && context != QUOTED && !ifs().isEmpty() && !ifs().equals(" \t\n") ) {
				// as bash: ${x-$@} with IFS=: is the parameters joined with spaces, then split
				out.add(new Piece(EXPANDED, String.join(" ", strings(sc.getPositionalParameterValues()))));
				ret = true;
				continue;
			}
			ret |= part(p, context == QUOTED ? QUOTED : EXPANDED, out);
		}
		if( context == QUOTED ) {
			// ("${x:-$@}" with no parameters is one empty word, as bash's: not "$@"'s none)
			ret = true;
		}
		return ret || out.size() > before;
	}

	/** the word of ${x=word} or ${x?word} as text: in "..." a ' is itself */
	private String paramText(String text, int context) {
		if( context != QUOTED ) {
			return join(paramPieces(text));
		}
		List<Piece> ret = new ArrayList<>();
		for(Word.Part p : Parser.fragment(text, quotedParameter()).parts) {
			part(p, QUOTED, ret);
		}
		return join(ret);
	}

	private Fragment quotedParameter() {
		return inHereDocument ? Fragment.HERE_PARAMETER : Fragment.QUOTED_PARAMETER;
	}

	/** the pieces of a word in ${ } (a subscript, an offset, the word of ${x:=word}) */
	private List<Piece> paramPieces(String text) {
		List<Piece> ret = new ArrayList<>();
		word(Parser.fragment(text, Fragment.WORD), ret, TILDE_START);
		return ret;
	}

	/** a ${x#pattern} pattern: quotes count, as in bash (even inside "...") */
	private String patternText(String text) {
		List<Piece> pieces = new ArrayList<>();
		word(Parser.fragment(text, Fragment.WORD), pieces, TILDE_START);
		return patternOf(pieces);
	}

	// ------------------------------------------------------------------ operators

	private static boolean matches(GlobPattern rx, String s) {
		return rx.matches(s);
	}

	private static String removePrefix(String s, GlobPattern rx, boolean longest) {
		int n = s.length();
		if( longest ) {
			for (int i = n; i >= 0; i--) {
				if( matches(rx, s.substring(0, i))) {
					return s.substring(i);
				}
			}
		} else {
			for (int i = 0; i <= n; i++) {
				if( matches(rx, s.substring(0, i))) {
					return s.substring(i);
				}
			}
		}
		return s;
	}

	private static String removeSuffix(String s, GlobPattern rx, boolean longest) {
		int n = s.length();
		if( longest ) {
			for (int i = 0; i <= n; i++) {
				if( matches(rx, s.substring(i))) {
					return s.substring(0, i);
				}
			}
		} else {
			for (int i = n; i >= 0; i--) {
				if( matches(rx, s.substring(i))) {
					return s.substring(0, i);
				}
			}
		}
		return s;
	}

	/** ${x/pattern/string} and // /# /%: & in an unquoted string is the text that matched */
	private Val replace(Val v, ParamExpr e, int context) {
		String patText = patternText(e.arg);
		List<Object> rep = new ArrayList<>();
		if( e.arg2 != null ) {
			StringBuilder lit = new StringBuilder();
			Fragment mode = context == QUOTED ? Fragment.QUOTED_REPLACEMENT : Fragment.WORD;
			replacement(Parser.fragment(e.arg2, mode).parts, false, rep, lit);
			rep.add(lit.toString());
		}
		if( patText.isEmpty()) {
			if( e.op.equals("/#")) {
				return v.map(s -> render(rep, "")+s);
			}
			if( e.op.equals("/%")) {
				return v.map(s -> s+render(rep, ""));
			}
			return v;
		}
		// (shopt -s nocasematch: in any case, as bash's pattern substitution)
		GlobPattern rx = GlobPattern.compile(patText, Boolean.TRUE.equals(sc.console.getShellOptions().get("nocasematch")));
		return v.map(s -> replace(s, rx, e.op, rep));
	}

	private static final Object MATCH = new Object();

	/**
	 * The string of ${x/pattern/string}: an & in it is the text that matched, unless it is quoted
	 * (\&, '&', "&" in an unquoted ${ }).
	 */
	private void replacement(List<Word.Part> parts, boolean quoted, List<Object> rep, StringBuilder lit) {
		// shopt -u patsub_replacement: & is just &
		boolean patsub = !Boolean.FALSE.equals(sc.console.getShellOptions().get("patsub_replacement"));
		for (int i = 0; i < parts.size(); i++) {
			Word.Part p = parts.get(i);
			String text;
			boolean special;
			boolean expanded = false;
			if( p instanceof Word.Literal l ) {
				text = l.text();
				if( !quoted && i == 0 && rep.isEmpty() && lit.length() == 0 && (text.equals("~") || text.startsWith("~/"))) {
					// ${x/a/~}: ~ is the home directory (in "..." too, as bash)
					Object home = sc.getVariable("HOME");
					text = (home == null ? "" : home)+text.substring(1);
				}
				special = !quoted;
			} else if( p instanceof Word.DoubleQuoted d ) {
				replacement(d.parts(), true, rep, lit);
				continue;
			} else if( p instanceof Word.Escaped || p instanceof Word.SingleQuoted || p instanceof Word.AnsiC ) {
				List<Piece> pieces = new ArrayList<>();
				part(p, QUOTED, pieces);
				text = join(pieces);
				special = false;
			} else {
				List<Piece> pieces = new ArrayList<>();
				part(p, QUOTED, pieces);
				text = join(pieces);
				special = !quoted;
				// an unquoted $var's value: \\ is \ and \& is & (as bash's)
				expanded = special;
			}
			special &= patsub;
			for (int k = 0; k < text.length(); k++) {
				char c = text.charAt(k);
				if( expanded && patsub && c == '\\' && k+1 < text.length() && (text.charAt(k+1) == '\\' || text.charAt(k+1) == '&')) {
					lit.append(text.charAt(++k));
				} else if( c == '&' && special ) {
					rep.add(lit.toString());
					lit.setLength(0);
					rep.add(MATCH);
				} else {
					lit.append(c);
				}
			}
		}
	}

	private static String render(List<Object> rep, String match) {
		StringBuilder ret = new StringBuilder();
		for(Object o : rep) {
			ret.append(o == MATCH ? match : (String) o);
		}
		return ret.toString();
	}

	private static String replace(String s, GlobPattern rx, String op, List<Object> rep) {
		int n = s.length();
		if( op.equals("/#")) {
			for (int j = n; j >= 0; j--) {
				if( matches(rx, s.substring(0, j))) {
					return render(rep, s.substring(0, j))+s.substring(j);
				}
			}
			return s;
		}
		if( op.equals("/%")) {
			for (int i = 0; i <= n; i++) {
				if( matches(rx, s.substring(i))) {
					return s.substring(0, i)+render(rep, s.substring(i));
				}
			}
			return s;
		}
		boolean all = op.equals("//");
		StringBuilder ret = new StringBuilder();
		int i = 0;
		boolean replaced = false;
		Matcher m = rx.regex() == null ? null : rx.regex().matcher(s);
		while( i < n ) {
			int end = -1;
			if( !(replaced && !all)) {
				if( m != null ) {
					m.region(i, n);
				}
				// (a quick look first, where there is a regular expression)
				if( m == null || m.lookingAt()) {
					// the longest match here
					for (int j = n; j > i; j--) {
						if( matches(rx, s.substring(i, j))) {
							end = j;
							break;
						}
					}
				}
			}
			if( end > i ) {
				ret.append(render(rep, s.substring(i, end)));
				i = end;
				replaced = true;
			} else {
				ret.append(s.charAt(i));
				i++;
			}
		}
		if( n == 0 && matches(rx, "")) {
			return render(rep, "");
		}
		return ret.toString();
	}

	/** ^ ^^ , ,, ~ ~~: the first (or every) character that matches rx (any, if null) */
	private static String changeCase(String s, GlobPattern rx, char kind, boolean all) {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if( (all || i == 0) && (rx == null || matches(rx, String.valueOf(c)))) {
				c = kind == '^' ? Character.toUpperCase(c) : kind == ',' ? Character.toLowerCase(c)
						: Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c);
			}
			ret.append(c);
		}
		return ret.toString();
	}

	/** ${x:offset:length}, ${@:offset:length}, ${a[@]:offset:length} */
	private Val substring(Val v, ParamExpr e) {
		long off;
		Long len;
		try {
			// (as $(( )) is: a[$k] in it expanded once)
			off = evaluate(arithmeticText(Parser.arithmeticFragment(e.arg))).longValue();
			len = e.arg2 == null ? null : evaluate(arithmeticText(Parser.arithmeticFragment(e.arg2))).longValue();
		} catch (ExpansionError x) {
			// as bash: x: 1+: arithmetic syntax error ...
			throw new ExpansionError(e.name+": "+x.getMessage(), x.kind);
		}
		if( v.isList() && v.items.size() == 1 && e.subscript != null && (e.subscript.equals("@") || e.subscript.equals("*"))
				&& !(sc.getVariable(e.name) instanceof List<?>) && !(sc.getVariable(e.name) instanceof Map<?,?>)) {
			// ${var[@]:3} of a variable that is no array: its value's substring, as bash's
			v = Val.of(v.items.get(0));
		}
		if( !v.isList()) {
			String s = v.scalar == null ? "" : v.scalar;
			int n = s.length();
			if( off < 0 ) {
				off += n;
				if( off < 0 ) {
					return Val.of("");
				}
			}
			if( off > n ) {
				return Val.of("");
			}
			long end = n;
			if( len != null ) {
				end = len < 0 ? n+len : Math.min(n, off+len);
				if( end < off ) {
					if( len < 0 ) {
						throw new ExpansionError(e.arg2.trim()+": substring expression < 0");
					}
					end = off;
				}
			}
			return Val.of(s.substring((int) off, (int) end));
		}
		if( len != null && len < 0 ) {
			throw new ExpansionError(e.arg2.trim()+": substring expression < 0");
		}
		List<long[]> indexed = new ArrayList<>();
		List<String> items;
		if( e.name.equals("@") || e.name.equals("*")) {
			// $0 is element 0
			items = new ArrayList<>();
			items.add(str(sc.getVariable("$0")));
			items.addAll(v.items);
			for (int i = 0; i < items.size(); i++) {
				indexed.add(new long[] {i, i});
			}
		} else {
			items = new ArrayList<>(v.items);
			List<Long> idx = indexes(sc.getVariable(e.name));
			for (int i = 0; i < items.size(); i++) {
				indexed.add(new long[] {i < idx.size() ? idx.get(i) : i, i});
			}
		}
		long top = indexed.isEmpty() ? 0 : indexed.get(indexed.size()-1)[0]+1;
		if( off < 0 ) {
			off += top;
			if( off < 0 ) {
				return Val.list(new ArrayList<>(), v.star);
			}
		}
		List<String> ret = new ArrayList<>();
		for(long [] ix : indexed) {
			if( ix[0] >= off && (len == null || ret.size() < len)) {
				ret.add(items.get((int) ix[1]));
			}
		}
		return Val.list(ret, v.star);
	}

	/** ${x@Q} and the other transformations */
	private Val transform(Val v, ParamExpr e) {
		char t = e.arg.charAt(0);
		if( t == 'A' && (e.name.equals("@") || e.name.equals("*"))) {
			// ${@@A}: set -- 'a' 'b'
			StringBuilder ret = new StringBuilder("set --");
			for(Object o : sc.getPositionalParameterValues()) {
				ret.append(' ').append(quote(String.valueOf(o)));
			}
			return Val.of(ret.toString());
		}
		if( t == 'A' && ("@".equals(e.subscript) || "*".equals(e.subscript)) && sc.getVariable(e.name) instanceof List<?> l && l.isEmpty()
				&& !sc.console.declaredUnset.contains(e.name)) {
			// B=(); ${B[@]@A}
			return Val.of("declare -"+attributes(e.name)+" "+e.name+"=()");
		}
		if( v.unset() && (t == 'a' || t == 'A')) {
			// declare -r x (no value): its attributes, declare -r x
			String flags = attributes(e.name);
			return Val.of(t == 'a' || flags.isEmpty() ? flags : "declare -"+flags+" "+e.name);
		}
		if( v.unset()) {
			return v.isList() ? v : Val.of("");
		}
		switch (t) {
		case 'Q':
			return v.map(Expander::quote);
		case 'E':
			return v.map(ShellContext::ansiC);
		case 'P':
			// as a prompt expands it
			return v.map(s -> sc.console.expandPromptTransform(sc, s));
		case 'U':
			return v.map(String::toUpperCase);
		case 'u':
			return v.map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase()+s.substring(1));
		case 'L':
			return v.map(String::toLowerCase);
		case 'a':
			return v.map(s -> attributes(e.name));
		case 'A': {
			// as bash: name='v', or declare -FLAGS name='v'; a whole array as declare -p shows it
			Object raw = sc.getVariable(e.name);
			String flags = attributes(e.name);
			boolean whole = "@".equals(e.subscript) || "*".equals(e.subscript);
			if( whole && (raw instanceof List<?> || raw instanceof Map<?,?>)) {
				return Val.of("declare -"+flags+" "+e.name+"="+us.bringardner.fsh.exec.Declarations.arrayText(raw));
			}
			String head = flags.isEmpty() ? e.name : "declare -"+flags+" "+e.name;
			if( v.scalar == null && !v.isList()) {
				return Val.of(flags.isEmpty() ? "" : head);
			}
			return v.map(s -> head+"="+quote(s));
		}
		case 'K':
		case 'k': {
			Object raw = sc.getVariable(e.name);
			if( raw instanceof List<?> || raw instanceof Map<?,?> ) {
				if( t == 'K' && e.subscript == null ) {
					String kv = keyValues(raw, false);
					return Val.of(kv.substring(1, kv.length()-1));
				}
				if( t == 'K' ) {
					// ${a[@]@K}: key "value" ...
					List<String> words = new ArrayList<>();
					List<String> ks = keys(raw);
					List<String> vs = values(raw);
					for (int i = 0; i < ks.size(); i++) {
						words.add(ks.get(i)+" \""+vs.get(i).replace("\\", "\\\\").replace("\"", "\\\"")+"\"");
					}
					return Val.list(words, v.star);
				}
				List<String> words = new ArrayList<>();
				List<String> ks = keys(raw);
				List<String> vs = values(raw);
				for (int i = 0; i < ks.size(); i++) {
					words.add(ks.get(i));
					words.add(vs.get(i));
				}
				return Val.list(words, v.star);
			}
			return v.map(Expander::quote);
		}
		default:
			throw e.bad();
		}
	}

	/** ([0]="a" [1]="b") */
	private static String keyValues(Object raw, boolean spaceAtEnd) {
		StringBuilder ret = new StringBuilder("(");
		List<String> ks = keys(raw);
		List<String> vs = values(raw);
		for (int i = 0; i < ks.size(); i++) {
			ret.append('[').append(ks.get(i)).append("]=\"").append(vs.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\" ");
		}
		if( !spaceAtEnd && ret.length() > 1 ) {
			ret.setLength(ret.length()-1);
		}
		return ret.append(')').toString();
	}

	/** 'text' the way the shell reads it back */
	public static String quote(String s) {
		boolean control = false;
		for(char c : s.toCharArray()) {
			control |= c < ' ' || c == 0x7f;
		}
		if( !control ) {
			return "'"+s.replace("'", "'\\''")+"'";
		}
		// with control characters: $'...', as bash writes it
		StringBuilder ret = new StringBuilder("$'");
		for(char c : s.toCharArray()) {
			switch (c) {
			case 0x07 -> ret.append("\\a");
			case '\b' -> ret.append("\\b");
			case 0x1b -> ret.append("\\E");
			case '\f' -> ret.append("\\f");
			case '\n' -> ret.append("\\n");
			case '\r' -> ret.append("\\r");
			case '\t' -> ret.append("\\t");
			case 0x0b -> ret.append("\\v");
			case '\\' -> ret.append("\\\\");
			case '\'' -> ret.append("\\'");
			default -> {
				if( c < ' ' || c == 0x7f ) {
					ret.append(String.format("\\%03o", (int) c));
				} else {
					ret.append(c);
				}
			}
			}
		}
		return ret.append('\'').toString();
	}

	/** ${x@a}: a A i n r u l c x, in bash's order */
	private String attributes(String name) {
		StringBuilder ret = new StringBuilder();
		Object val = sc.getVariable(name);
		if( val instanceof Map<?,?> ) {
			ret.append('A');
		} else if( val instanceof List<?> ) {
			ret.append('a');
		}
		if( sc.console.isInteger(name)) {
			ret.append('i');
		}
		if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
			ret.append('n');
		}
		if( sc.console.isReadonly(name)) {
			ret.append('r');
		}
		if( sc.getEvironmentVariable(name) != null ) {
			ret.append('x');
		}
		// (bash's order: a A i n r x c l u)
		Character c = sc.caseAttribute(name);
		if( c != null ) {
			ret.append(c);
		}
		return ret.toString();
	}

	// ------------------------------------------------------------------ arrays

	private static List<String> values(Object v) {
		List<String> ret = new ArrayList<>();
		if( v instanceof FshList f ) {
			for(Object o : f.getValues()) {
				ret.add(str(o));
			}
		} else if( v instanceof Collection<?> c ) {
			for(Object o : c) {
				ret.add(str(o));
			}
		} else if( v instanceof Map<?,?> m ) {
			for(Object o : m.values()) {
				ret.add(str(o));
			}
		} else if( v != null ) {
			ret.add(v.toString());
		}
		return ret;
	}

	private static List<String> keys(Object v) {
		List<String> ret = new ArrayList<>();
		if( v instanceof Map<?,?> m ) {
			for(Object o : m.keySet()) {
				ret.add(str(o));
			}
		} else {
			for(long i : indexes(v)) {
				ret.add(String.valueOf(i));
			}
		}
		return ret;
	}

	private static List<Long> indexes(Object v) {
		List<Long> ret = new ArrayList<>();
		if( v instanceof FshList f ) {
			for(int i : f.getIndexes()) {
				ret.add((long) i);
			}
		} else if( v instanceof List<?> l ) {
			for (int i = 0; i < l.size(); i++) {
				ret.add((long) i);
			}
		} else if( v != null && !(v instanceof Map<?,?>)) {
			ret.add(0L);
		}
		return ret;
	}

	private static List<String> strings(List<Object> list) {
		List<String> ret = new ArrayList<>();
		for(Object o : list) {
			ret.add(str(o));
		}
		return ret;
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}

	private static boolean isName(String s) {
		return s.matches("[A-Za-z_][A-Za-z0-9_]*");
	}

	// ------------------------------------------------------------------ splitting and globbing

	/** a word after splitting: its text, and the same as a pattern (quoted characters escaped) */
	private static final class Field {
		final StringBuilder text = new StringBuilder();
		final StringBuilder pattern = new StringBuilder();
		boolean wild;
	}

	private String ifs() {
		Object v = sc.getVariable("IFS");
		return v == null ? " \t\n" : v.toString();
	}

	private String ifsFirst() {
		Object v = sc.getVariable("IFS");
		if( v == null ) {
			return " ";
		}
		String s = v.toString();
		return s.isEmpty() ? "" : s.substring(0, 1);
	}

	/**
	 * Word splitting: the values of unquoted expansions are split on IFS (white space in IFS runs
	 * together and is dropped at the ends; any other IFS character ends a field, so a::b has an
	 * empty one), $@ gives a word per parameter, and a word made of nothing but empty unquoted
	 * expansions is no word at all.
	 */
	private List<Field> split(List<Piece> pieces) {
		String ifs = ifs();
		List<Field> ret = new ArrayList<>();
		Field cur = new Field();
		boolean started = false;
		boolean afterSpace = false;
		for(Piece p : pieces) {
			switch (p.kind) {
			case TEXT:
				if( !p.text.isEmpty()) {
					cur.text.append(p.text);
					escape(p.text, "\\'\"", cur.pattern);
					cur.wild |= wild(p.text);
					started = true;
					afterSpace = false;
				}
				break;
			case QUOTED:
				cur.text.append(p.text);
				escape(p.text, null, cur.pattern);
				started = true;
				afterSpace = false;
				break;
			case EXPANDED:
				for(char c : p.text.toCharArray()) {
					if( ifs.indexOf(c) >= 0 ) {
						if( c == ' ' || c == '\t' || c == '\n' ) {
							if( started ) {
								ret.add(cur);
								cur = new Field();
								started = false;
								afterSpace = true;
							}
						} else {
							if( started || !afterSpace ) {
								ret.add(cur);
								cur = new Field();
							}
							started = false;
							afterSpace = false;
						}
						continue;
					}
					cur.text.append(c);
					if( c == '\'' || c == '"' ) {
						cur.pattern.append('\\');
					}
					cur.pattern.append(c);
					cur.wild |= c == '*' || c == '?' || c == '[';
					started = true;
					afterSpace = false;
				}
				break;
			default:
				if( started ) {
					ret.add(cur);
					cur = new Field();
				}
				started = false;
				afterSpace = false;
			}
		}
		if( started ) {
			ret.add(cur);
		}
		return ret;
	}

	/** filename expansion of a field that has an unquoted pattern */
	private void glob(Field f, List<String> out) {
		String text = f.text.toString();
		if( !f.wild || option(Console.Option.DisableFilenameExpansion) || !isPattern(f.pattern)) {
			out.add(text);
			return;
		}
		List<String> files;
		try {
			files = Glob.expand(f.pattern.toString(), sc);
		} catch (IOException e) {
			files = List.of();
		}
		if( !files.isEmpty()) {
			out.addAll(files);
		} else if( Glob.option(sc, "failglob")) {
			throw new ExpansionError("no match: "+text);
		} else if( !Glob.option(sc, "nullglob")) {
			out.add(text);
		}
	}

	/** an unescaped * or ?, a [ with a ] after it (and no / between), or ?( *( +( @( !( */
	private static boolean isPattern(CharSequence p) {
		for (int i = 0; i < p.length(); i++) {
			char c = p.charAt(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '*' || c == '?' ) {
				return true;
			} else if( c == '[' ) {
				for (int j = i+1; j < p.length(); j++) {
					if( p.charAt(j) == '\\' ) {
						j++;
					} else if( p.charAt(j) == '/' ) {
						// ([qwe/qwe] is not a bracket expression: a / is never in one)
						break;
					} else if( p.charAt(j) == ']' ) {
						return true;
					}
				}
			} else if( (c == '+' || c == '@' || c == '!') && i+1 < p.length() && p.charAt(i+1) == '(' ) {
				return true;
			}
		}
		return false;
	}

	private static boolean wild(String s) {
		for(char c : s.toCharArray()) {
			if( c == '*' || c == '?' || c == '[' || c == '(' ) {
				return true;
			}
		}
		return false;
	}

	/** s into out, with \ before each of chars (every character but letters and digits if chars is null) */
	private static void escape(String s, String chars, StringBuilder out) {
		for(char c : s.toCharArray()) {
			if( chars == null ? !Character.isLetterOrDigit(c) : chars.indexOf(c) >= 0 ) {
				out.append('\\');
			}
			out.append(c);
		}
	}

	/** the pieces as one string, the words of $@ joined by a space ($* by the first of IFS) */
	private static String join(List<Piece> pieces) {
		StringBuilder ret = new StringBuilder();
		for(Piece p : pieces) {
			ret.append(p.text);
		}
		return ret.toString();
	}

	private static String patternOf(List<Piece> pieces) {
		StringBuilder ret = new StringBuilder();
		for(Piece p : pieces) {
			switch (p.kind) {
			case TEXT -> escape(p.text, "\\'\"", ret);
			case EXPANDED -> escape(p.text, "'\"", ret);
			default -> escape(p.text, null, ret);
			}
		}
		return ret.toString();
	}

	private boolean option(Console.Option o) {
		return sc.console != null && sc.console.isOptionEnabled(o);
	}
}
