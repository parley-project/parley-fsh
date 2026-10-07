package us.bringardner.fsh.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * A word, kept whole: its parts as written (text, quotes and expansions). What it expands to
 * (braces, ~, $x, $( ), splitting, globs, quote removal) is decided when it runs.
 */
public final class Word extends Ast.Node {

	/** a piece of a word */
	public sealed interface Part permits Literal, Escaped, SingleQuoted, AnsiC, DoubleQuoted, Param,
			ParamExpansion, CommandSub, FunctionSub, Backquote, ArithSub, ProcessSub {
	}

	/** text: unquoted (globs, braces, ~ count) or, inside "...", quoted */
	public record Literal(String text) implements Part {
	}

	/** \c: the character, quoted */
	public record Escaped(char c) implements Part {
	}

	/** '...' */
	public record SingleQuoted(String text) implements Part {
	}

	/** $'...' as written between the quotes (its escapes are read when it expands) */
	public record AnsiC(String text) implements Part {
	}

	/** "..." (or $"..." when locale is true) */
	public record DoubleQuoted(List<Part> parts, boolean locale) implements Part {
	}

	/** $name, $1, $@, $? ... (the name without the $) */
	public record Param(String name) implements Part {
	}

	/** ${...}: the text between the braces */
	public record ParamExpansion(String body) implements Part {
	}

	/**
	 * $( ... ): the text between the parentheses, and its commands (body is null for $((cmd) ...),
	 * which bash also reads only when it runs)
	 */
	public record CommandSub(String text, Ast.Sequence body) implements Part {
	}

	/**
	 * ${ list; } (run in this shell, its output is the value) or, when reply is true,
	 * ${| list; } (the value is $REPLY after it runs)
	 */
	public record FunctionSub(String text, Ast.Sequence body, boolean reply) implements Part {
	}

	/** `...` as written between the backquotes */
	public record Backquote(String text) implements Part {
	}

	/** $(( ... )) or $[ ... ]: the expression (its $x, $( ) ... expand before it is evaluated) */
	public record ArithSub(Word expression) implements Part {
	}

	/** <( ... ) (direction '<') or >( ... ) ('>'); body is null for <((...) ..., which bash reads when it runs */
	public record ProcessSub(char direction, String text, Ast.Sequence body) implements Part {
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
