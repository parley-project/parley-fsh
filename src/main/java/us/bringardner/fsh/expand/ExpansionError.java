package us.bringardner.fsh.expand;

/**
 * An expansion that bash reports as an error: "x: unbound variable", "${x y}: bad substitution",
 * "x: parameter null or not set" ... The message is bash's, without the script and line. In a
 * script that is not interactive bash then ends the script (status 1); whoever runs the command
 * decides that.
 */
public class ExpansionError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** the status a script that is not interactive exits with */
	public final int status;

	public ExpansionError(String message) {
		this(message, 1);
	}

	public ExpansionError(String message, int status) {
		this(message, status, Kind.ABANDON);
	}

	public ExpansionError(String message, Kind kind) {
		this(message, 1, kind);
	}

	public ExpansionError(String message, int status, Kind kind) {
		super(message);
		this.status = status;
		this.kind = kind;
	}

	/**
	 * What bash does after the error: a script ends (FATAL: ${x:?}, set -u), the rest of the line
	 * is not run (ABANDON: a bad substitution, an arithmetic error, a readonly variable), or
	 * only the command fails (FAIL: failglob's no match).
	 */
	public enum Kind {FATAL, ABANDON, FAIL}

	public final Kind kind;

	/** said without the command's name (an error in a variable's value used in (( ))) */
	public boolean bare;

	/** the message is said as it is (it has its own "name: line n: ") */
	public boolean whole;
}
