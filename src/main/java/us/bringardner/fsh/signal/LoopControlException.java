package us.bringardner.fsh.signal;

import us.bringardner.fsh.ShellContext.LoopControl;

/**
 * break n and continue n: thrown up to the loop they act on (howFar 1 is the innermost).
 */
public class LoopControlException extends FshException {
	private static final long serialVersionUID = 1L;

	public final LoopControl type;
	public final int howFar;

	/** the loop's status after it (break 0: 1) */
	public final int status;

	public LoopControlException(LoopControl type, int howFar) {
		this(type, howFar, 0);
	}

	public LoopControlException(LoopControl type, int howFar, int status) {
		this.type = type;
		this.howFar = howFar;
		this.status = status;
	}
}
