package us.bringardner.fsh.job;

import us.bringardner.fsh.ShellContext;

/** a command typed at the prompt: the shell waits for it, unless it is stopped (Ctrl-Z) */
public class ForgroundJob extends AbstractJob {

	String code;

	public ForgroundJob(ShellContext ctx,String code) {
		super(ctx);
		this.code = code;
	}

	@Override
	public String toString() {
		return code;
	}

	@Override
	public int process() throws Exception {
		ShellContext ctx = getShellContext();
		setState(JobState.Running);		
		return ctx.console.executeScript(ctx,code);
	}

	@Override
	public void interuptJob() {
		thread.interrupt();		
	}

	@Override
	public String getCommandLine() {
		return code;
	}
}
