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
		super(message);
		this.status = status;
	}
}
