package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.signal.ExitException;

public class Exit extends ShellCommand{
	static String name = "exit";
	static String help = "exit [n]\n\tEnd the shell, with status n (the last command's status without n).\n"
			;
	
	static int cnt = 0;
	public Exit() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		
		// with no status: the last command's, as bash's
		long ret = ctx.console.getLastExitCode();
		int first = args.length > 0 && "--".equals(""+args[0].getValue(ctx)) ? 1 : 0;
		if( args.length > first ) {
			String n = (""+args[first].getValue(ctx)).trim();
			if( !n.matches("[-+]?[0-9]{1,18}")) {
				// (not fatal, as in bash 5.3)
				ctx.error(getName()+": "+n+": numeric argument required");
				ctx.specialUsageError();
				return 2;
			}
			if( args.length > first+1 ) {
				throw ctx.tooManyArguments(getName());
			}
			ret = Long.parseLong(n.startsWith("+") ? n.substring(1) : n);
		}
		us.bringardner.fsh.job.IJob typed = ctx.job;
		if( ctx.console.isInteractive && typed instanceof us.bringardner.fsh.job.ForgroundJob && typed.getShellContext() == ctx ) {
			// the shell itself (not a subshell) leaves; as bash, it says so, but not at once with stopped jobs
			// (a login shell says logout)
			ctx.stderr.println(ctx.console.isLogin ? "logout" : "exit");
			String warning = ctx.console.exitJobsWarning(typed);
			if( warning != null ) {
				ctx.stderr.println(warning);
				if( us.bringardner.fsh.Glob.option(ctx, "checkjobs")) {
					// (and what they are, as bash lists them)
					for(us.bringardner.fsh.job.IJob job : ctx.console.jobManager.getJobs()) {
						ctx.stdout.println(ctx.console.jobManager.describe(job, false));
					}
				}
				return 1;
			}
		}
		// a status is 0 to 255
		int status = (int) (((ret % 256)+256) % 256);
		if( !ctx.isIsolated() && ctx.isInFunction()) {
			// the EXIT trap runs here, in the function (its $FUNCNAME), as bash's
			status = ctx.console.runExitTrapAt(ctx, status);
		}
		throw new ExitException(ctx, status);
	}

}
