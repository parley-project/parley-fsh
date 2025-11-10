package us.bringardner.shell.job;

import us.bringardner.shell.Console;
import us.bringardner.shell.ConsoleSignal;
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

	Console console;
	
	@Override
	public int process() throws Exception {
		
		ShellContext ctx = getShellContext();
		setState(JobState.Running);		
		console = ctx.console;
		int ret=console.executeUsingAntlr(ctx,code);
		setState(JobState.Termnated);
		return ret;
	}

	@Override
	public void handleSignal(ConsoleSignal signal) {
		throw new RuntimeException("I don't know what to do???");
		
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
