package us.bringardner.fsh.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * The syntax tree of a script, as bash's grammar has it: a sequence of && || lists of pipelines
 * of commands. A word is kept whole (see Word); what it expands to is decided when it runs.
 * Each node knows where it is in the source, for messages, $LINENO and declare -f.
 */
public final class Ast {

	private Ast() {
	}

	/** a part of the source: offsets [start, end) and the line start is on (1 is the first) */
	public abstract static class Node {
		public int start;
		public int end;
		public int line;

		/** the node as written */
		public String text(String source) {
			return source.substring(start, end);
		}
	}

	/** commands separated by ; & or newlines (a script, the body of a block, $( ) ...) */
	public static final class Sequence extends Node {
		public final List<Item> items = new ArrayList<>();
		/** the text it was read from, with its aliases expanded (what positions in it are in) */
		public String source;
		/** what bash warns about as it reads it: {line, message} */
		public final List<Object []> warnings = new ArrayList<>();
	}

	/** an && || list, and whether a & after it runs it in the background */
	public static final class Item {
		public AndOr command;
		public boolean background;
	}

	/** pipelines joined by && and || (ops.get(i) is between pipelines i and i+1) */
	public static final class AndOr extends Node {
		public final List<Pipeline> pipelines = new ArrayList<>();
		public final List<String> ops = new ArrayList<>();
	}

	/** commands joined by | (or |&, which sends standard error too), maybe after ! and time */
	public static final class Pipeline extends Node {
		public boolean negated;
		public boolean timed;
		/** time -p */
		public boolean timePosix;
		public final List<Command> commands = new ArrayList<>();
		/** stderrToo.get(i): command i is followed by |& */
		public final List<Boolean> stderrToo = new ArrayList<>();
	}

	/** a simple or compound command, with the redirects after it */
	public abstract static class Command extends Node {
		public final List<Redirect> redirects = new ArrayList<>();
	}

	/** assignments, words and redirects: x=1 cmd arg >file */
	public static final class SimpleCommand extends Command {
		public final List<Assignment> assignments = new ArrayList<>();
		public final List<Word> words = new ArrayList<>();
	}

	/** { list; } */
	public static final class BraceGroup extends Command {
		public Sequence body;
	}

	/** ( list ) */
	public static final class Subshell extends Command {
		public Sequence body;
	}

	/** if c; then b; elif c; then b; else e; fi */
	public static final class If extends Command {
		public final List<Sequence> conditions = new ArrayList<>();
		public final List<Sequence> bodies = new ArrayList<>();
		/** null if there is no else */
		public Sequence elseBody;
	}

	/** while c; do b; done (or until) */
	public static final class Loop extends Command {
		public boolean until;
		public Sequence condition;
		public Sequence body;
	}

	/** for name in words; do b; done (words null: for name; do ... the positional parameters) */
	public static final class For extends Command {
		public String variable;
		public List<Word> words;
		public Sequence body;
	}

	/** for (( init; condition; step )); do b; done (each part may be empty) */
	public static final class ArithFor extends Command {
		public Word init;
		public Word condition;
		public Word step;
		public Sequence body;
	}

	/** select name in words; do b; done (words null: the positional parameters) */
	public static final class Select extends Command {
		public String variable;
		public List<Word> words;
		public Sequence body;
	}

	/** case word in pattern|pattern) list ;; ... esac */
	public static final class Case extends Command {
		public Word subject;
		public final List<CaseClause> clauses = new ArrayList<>();
	}

	/** patterns, the list, and how it ends: ;; ;& ;;& (null for the last clause with none) */
	public static final class CaseClause extends Node {
		public final List<Word> patterns = new ArrayList<>();
		public Sequence body;
		public String terminator;
	}

	/** (( expression )) (a word: its $x, $( ) ... expand before it is evaluated) */
	public static final class Arith extends Command {
		public Word expression;
	}

	/** [[ expression ]] */
	public static final class Cond extends Command {
		public CondExpr expression;
	}

	/** an expression of [[ ]] (parentheses only group) */
	public sealed interface CondExpr permits CondAnd, CondOr, CondNot, CondUnary, CondBinary, CondWord {
	}

	public record CondAnd(CondExpr left, CondExpr right) implements CondExpr {
	}

	public record CondOr(CondExpr left, CondExpr right) implements CondExpr {
	}

	/** ! expression */
	public record CondNot(CondExpr expression) implements CondExpr {
	}

	/** -f file, -n string, -v name ... */
	public record CondUnary(String op, Word operand) implements CondExpr {
	}

	/** a == pattern, a =~ regex, a < b, n -eq m ... (the right of == != is a pattern, of =~ a regular expression) */
	public record CondBinary(Word left, String op, Word right) implements CondExpr {
	}

	/** a word alone: true if it is not empty */
	public record CondWord(Word word) implements CondExpr {
	}

	/** coproc NAME command: command runs in the background, its output on NAME[0], its input NAME[1] */
	public static final class Coproc extends Command {
		public String name;
		public Command body;
	}

	/** name() compound-command, function name compound-command */
	public static final class FunctionDef extends Command {
		public String name;
		public Command body;
		/** written with the function keyword */
		public boolean keyword;
		/** the name has quotes, \ or an expansion in it (bash: not a valid identifier) */
		public boolean quotedName;
	}

	/**
	 * [n]op target, {name}op target, or a here-document ([n]<<word): fd null is the operator's
	 * usual one (0 for input, 1 for output).
	 */
	public static final class Redirect extends Node {
		public Integer fd;
		/** {name}>file: a new descriptor whose number goes in name */
		public String fdVariable;
		/** < > >> >| <> <& >& &> &>> << <<- <<< */
		public String op;
		/** the file, descriptor or here-string (null for a here-document) */
		public Word target;
		public HereDoc hereDoc;
	}

	/** the body of << or <<- */
	public static final class HereDoc {
		/** the word that ends it, quotes removed */
		public String delimiter;
		/** the word was quoted (<<'EOF'): the body is used as written */
		public boolean quoted;
		/** <<-: leading tabs are removed */
		public boolean stripTabs;
		/** the lines before the delimiter, each with its newline */
		public String body;
	}

	/**
	 * name=value, name+=value, name[index]=value, name=(words). value is null for an array,
	 * array is null otherwise.
	 */
	public static final class Assignment extends Node {
		public String name;
		/** the text between [ and ] as written, or null */
		public String index;
		public boolean append;
		public Word value;
		public List<Word> array;
	}
}
