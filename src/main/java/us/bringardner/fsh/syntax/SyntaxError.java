package us.bringardner.fsh.syntax;

/**
 * A script that does not parse, with bash's wording: "syntax error near unexpected token `fi'".
 */
public class SyntaxError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** the line it was found on (1 is the first) */
	public final int line;

	public SyntaxError(int line, String message) {
		super(message);
		this.line = line;
	}

	/** "line 3: syntax error ..." */
	public String describe() {
		return "line "+line+": "+getMessage();
	}
}
