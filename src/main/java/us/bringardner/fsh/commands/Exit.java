package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.signal.ExitException;

public class Exit extends ShellCommand{
	static String name = "exit";
	static String help = "Exit the process \n"
			;
	
	static int cnt = 0;
	public Exit() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		
		int ret = 0;
		if( args.length>0) {
			try {
				ret = Integer.parseInt(args[0].getValue(ctx).toString());
			} catch (Exception e) {
			}
		}
		us.bringardner.fsh.job.IJob typed = ctx.job;
		if( ctx.console.isInteractive && typed instanceof us.bringardner.fsh.job.ForgroundJob && typed.getShellContext() == ctx ) {
			// the shell itself (not a subshell) leaves; as bash, it says so, but not at once with stopped jobs
			ctx.stderr.println("exit");
			if( ctx.console.stoppedJobsWarning(typed)) {
				ctx.stderr.println("There are stopped jobs.");
				return 1;
			}
		}
		// a status is 0 to 255
		throw new ExitException(ctx, ((ret % 256)+256) % 256);
	}

}
