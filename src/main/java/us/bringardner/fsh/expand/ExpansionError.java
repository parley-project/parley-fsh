package us.bringardner.fsh.expand;

/**
 * An expansion that bash reports as an error: "x: unbound variable", "${x y}: bad substitution",
 * "x: parameter null or not set" ... The message is bash's, without the script and line. In a
 * script that is not interactive bash then ends the script (status 1); whoever runs the command
 * decides that.
 */
public class ExpansionError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ExpansionError(String message) {
		super(message);
	}
}
