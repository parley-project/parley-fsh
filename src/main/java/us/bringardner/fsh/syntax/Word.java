package us.bringardner.fsh.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * A word, kept whole: its parts as written (text, quotes and expansions). What it expands to
 * (braces, ~, $x, $( ), splitting, globs, quote removal) is decided when it runs.
 */
public final class Word extends Ast.Node {

	/** a piece of a word */
	public interface Part {
	}

	/** text: unquoted (globs, braces, ~ count) or, inside "...", quoted */
	public static final class Literal implements Part {
		private final String text;

		public Literal(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Literal)) return false;
			Literal o = (Literal) x;
			return java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text);
		}

		@Override public String toString() {
			return "Literal[" + "text=" + text + "]";
		}
	}

	/** \c: the character, quoted */
	public static final class Escaped implements Part {
		private final char c;

		public Escaped(char c) {
			this.c = c;
		}

		public char c() {
			return c;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Escaped)) return false;
			Escaped o = (Escaped) x;
			return c == o.c;
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(c);
		}

		@Override public String toString() {
			return "Escaped[" + "c=" + c + "]";
		}
	}

	/** '...' */
	public static final class SingleQuoted implements Part {
		private final String text;

		public SingleQuoted(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof SingleQuoted)) return false;
			SingleQuoted o = (SingleQuoted) x;
			return java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text);
		}

		@Override public String toString() {
			return "SingleQuoted[" + "text=" + text + "]";
		}
	}

	/** $'...' as written between the quotes (its escapes are read when it expands) */
	public static final class AnsiC implements Part {
		private final String text;

		public AnsiC(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof AnsiC)) return false;
			AnsiC o = (AnsiC) x;
			return java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text);
		}

		@Override public String toString() {
			return "AnsiC[" + "text=" + text + "]";
		}
	}

	/** "..." (or $"..." when locale is true) */
	public static final class DoubleQuoted implements Part {
		private final List<Part> parts;
		private final boolean locale;

		public DoubleQuoted(List<Part> parts, boolean locale) {
			this.parts = parts;
			this.locale = locale;
		}

		public List<Part> parts() {
			return parts;
		}

		public boolean locale() {
			return locale;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof DoubleQuoted)) return false;
			DoubleQuoted o = (DoubleQuoted) x;
			return java.util.Objects.equals(parts, o.parts) && locale == o.locale;
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(parts, locale);
		}

		@Override public String toString() {
			return "DoubleQuoted[" + "parts=" + parts + ", " + "locale=" + locale + "]";
		}
	}

	/** $name, $1, $@, $? ... (the name without the $) */
	public static final class Param implements Part {
		private final String name;

		public Param(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Param)) return false;
			Param o = (Param) x;
			return java.util.Objects.equals(name, o.name);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(name);
		}

		@Override public String toString() {
			return "Param[" + "name=" + name + "]";
		}
	}

	/** ${...}: the text between the braces */
	public static final class ParamExpansion implements Part {
		private final String body;

		public ParamExpansion(String body) {
			this.body = body;
		}

		public String body() {
			return body;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof ParamExpansion)) return false;
			ParamExpansion o = (ParamExpansion) x;
			return java.util.Objects.equals(body, o.body);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(body);
		}

		@Override public String toString() {
			return "ParamExpansion[" + "body=" + body + "]";
		}
	}

	/**
	 * $( ... ): the text between the parentheses, and its commands (body is null for $((cmd) ...),
	 * which bash also reads only when it runs)
	 */
	public static final class CommandSub implements Part {
		private final String text;
		private final Ast.Sequence body;

		public CommandSub(String text, Ast.Sequence body) {
			this.text = text;
			this.body = body;
		}

		public String text() {
			return text;
		}

		public Ast.Sequence body() {
			return body;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof CommandSub)) return false;
			CommandSub o = (CommandSub) x;
			return java.util.Objects.equals(text, o.text) && java.util.Objects.equals(body, o.body);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text, body);
		}

		@Override public String toString() {
			return "CommandSub[" + "text=" + text + ", " + "body=" + body + "]";
		}
	}

	/**
	 * ${ list; } (run in this shell, its output is the value) or, when reply is true,
	 * ${| list; } (the value is $REPLY after it runs)
	 */
	public static final class FunctionSub implements Part {
		private final String text;
		private final Ast.Sequence body;
		private final boolean reply;

		public FunctionSub(String text, Ast.Sequence body, boolean reply) {
			this.text = text;
			this.body = body;
			this.reply = reply;
		}

		public String text() {
			return text;
		}

		public Ast.Sequence body() {
			return body;
		}

		public boolean reply() {
			return reply;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof FunctionSub)) return false;
			FunctionSub o = (FunctionSub) x;
			return java.util.Objects.equals(text, o.text) && java.util.Objects.equals(body, o.body) && reply == o.reply;
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text, body, reply);
		}

		@Override public String toString() {
			return "FunctionSub[" + "text=" + text + ", " + "body=" + body + ", " + "reply=" + reply + "]";
		}
	}

	/** `...` as written between the backquotes */
	public static final class Backquote implements Part {
		private final String text;

		public Backquote(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Backquote)) return false;
			Backquote o = (Backquote) x;
			return java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text);
		}

		@Override public String toString() {
			return "Backquote[" + "text=" + text + "]";
		}
	}

	/** $(( ... )) or $[ ... ]: the expression (its $x, $( ) ... expand before it is evaluated) */
	/**
	 * [text] in an arithmetic expression: expanded once (as a word), its [ ] $ ` ~ \ ' " quoted
	 * with \, so that a key with ] in it stays one (bash's expand_array_subscript)
	 */
	public static final class ArithSubscript implements Part {
		private final String text;

		public ArithSubscript(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof ArithSubscript)) return false;
			ArithSubscript o = (ArithSubscript) x;
			return java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(text);
		}

		@Override public String toString() {
			return "ArithSubscript[" + "text=" + text + "]";
		}
	}

	public static final class ArithSub implements Part {
		private final Word expression;

		public ArithSub(Word expression) {
			this.expression = expression;
		}

		public Word expression() {
			return expression;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof ArithSub)) return false;
			ArithSub o = (ArithSub) x;
			return java.util.Objects.equals(expression, o.expression);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(expression);
		}

		@Override public String toString() {
			return "ArithSub[" + "expression=" + expression + "]";
		}
	}

	/** <( ... ) (direction '<') or >( ... ) ('>'); body is null for <((...) ..., which bash reads when it runs */
	public static final class ProcessSub implements Part {
		private final char direction;
		private final String text;
		private final Ast.Sequence body;

		public ProcessSub(char direction, String text, Ast.Sequence body) {
			this.direction = direction;
			this.text = text;
			this.body = body;
		}

		public char direction() {
			return direction;
		}

		public String text() {
			return text;
		}

		public Ast.Sequence body() {
			return body;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof ProcessSub)) return false;
			ProcessSub o = (ProcessSub) x;
			return direction == o.direction && java.util.Objects.equals(text, o.text) && java.util.Objects.equals(body, o.body);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(direction, text, body);
		}

		@Override public String toString() {
			return "ProcessSub[" + "direction=" + direction + ", " + "text=" + text + ", " + "body=" + body + "]";
		}
	}

	public final List<Part> parts = new ArrayList<>();

	/** the word as written */
	public String raw;

	/**
	 * declare x=1, local a=(1 2): an argument of a declaration command that is an assignment (its
	 * value is not split, and may be an array). null for other words.
	 */
	public Ast.Assignment assignment;

	/** true if the word is plain unquoted text (a reserved word can only be one) */
	public boolean isPlain() {
		return parts.size() == 1 && parts.get(0) instanceof Literal;
	}

	/** the text of a plain word, or null */
	public String plainText() {
		return isPlain() ? ((Literal) parts.get(0)).text() : null;
	}

	@Override
	public String toString() {
		return raw;
	}
}
