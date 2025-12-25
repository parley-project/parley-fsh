package us.bringardner.shell.antlr.signal;

import us.bringardner.shell.ShellContext;

public class ExitException extends FsshException {

	
	private static final long serialVersionUID = 1L;
	
	public ShellContext ctx;
	public int exitCode = 0;
	public String message;
	
	public ExitException(ShellContext ctx, int exitCode,String message) {
		this(ctx,exitCode);
		this.message = message;
	}
	
	public ExitException(ShellContext ctx, int exitCode) {
		this.ctx = ctx;
		this.exitCode = exitCode;
	}
	
	public String toString() {
		if( message == null) {
			return "";
		} else {
			return message;
		}
	}


}
