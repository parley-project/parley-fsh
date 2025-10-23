package us.bringardner.shell.job;

import us.bringardner.shell.ShellContext;

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
		int ret=ctx.console.	executeUsingAntlr(ctx,code);
		setState(JobState.Termnated);
		return ret;
	}

}
