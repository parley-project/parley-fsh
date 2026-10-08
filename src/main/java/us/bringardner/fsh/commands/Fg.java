package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;

public class Fg extends ShellCommand{

	static String name = "fg";
	// 								jobspec = %#
	static String help = "fg [jobspec]\n"
			+ "Resume the job jobspec in the foreground and make it the current job."
			+ " If jobspec is not supplied, fg resumes the current job."
			+ " The return status is that of the command placed into the foreground, or non-zero if run when job control is disabled or,"
			+ " when run with job control enabled, jobspec does not specify a valid job or jobspec specifies a job that was started without job control."
			;

	public Fg() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( !ctx.console.isInteractive ) {
			ctx.error(name+": no job control");
			return 1;
		}
		JobManager jm = ctx.console.jobManager;
		String spec = args.length > 0 ? ""+args[0].getValue(ctx) : "%%";
		IJob job;
		try {
			job = JobSpecs.find(jm, spec);
		} catch (JobSpecs.Ambiguous e) {
			ctx.error(name+": "+e.getMessage());
			return 1;
		}
		if( job == null || JobManager.isDone(job)) {
			ctx.error(name+": "+JobSpecs.describe(spec)+": no such job");
			return 1;
		}
		// as bash does, the command it continues
		ctx.stdout.println(job.getCommandLine());
		ctx.stdout.flush();
		return ctx.console.foreground(job);
	}
}
