package us.bringardner.fsh.syntax;

/**
 * A script that does not parse, with bash's wording: "syntax error near unexpected token `fi'".
 */
public class SyntaxError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** the line it was found on (1 is the first) */
	public final int line;

	/** the text ended before the command did (an interactive shell reads another line then) */
	public final boolean endOfInput;

	/** bash reports it and goes on with the next command (an operator in x=( )) */
	public boolean recoverable;

	public SyntaxError(int line, String message) {
		super(message);
		this.line = line;
		this.endOfInput = message.contains("unexpected end of file") || message.contains("unexpected EOF");
	}

	/** "line 3: syntax error ..." */
	public String describe() {
		return "line "+line+": "+getMessage();
	}
}
