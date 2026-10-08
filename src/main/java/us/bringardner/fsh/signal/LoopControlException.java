package us.bringardner.fsh.signal;

import us.bringardner.fsh.ShellContext.LoopControl;

/**
 * break n and continue n: thrown up to the loop they act on (howFar 1 is the innermost).
 */
public class LoopControlException extends FshException {
	private static final long serialVersionUID = 1L;

	public final LoopControl type;
	public final int howFar;

	public LoopControlException(LoopControl type, int howFar) {
		this.type = type;
		this.howFar = howFar;
	}
}
