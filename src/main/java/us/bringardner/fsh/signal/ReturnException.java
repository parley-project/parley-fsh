package us.bringardner.fsh.signal;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.commands.Return;

public class ReturnException extends FshException {

	
	private static final long serialVersionUID = 1L;
	
	public ShellContext ctx;
	public Return action;
	public int exitCode = 0;
	public ReturnException(ShellContext ctx, Return action,int exitCode) {
		this.ctx = ctx;
		this.action=action;
		this.exitCode = exitCode;
	}
	
	public String toString() {
		return "Return ";
	}


}
