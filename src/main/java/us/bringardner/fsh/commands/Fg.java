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
		int first = 0;
		if( args.length > 0 ) {
			String a0 = ""+args[0].getValue(ctx);
			if( a0.equals("--")) {
				first = 1;
			} else if( a0.startsWith("-") && a0.length() > 1 ) {
				ctx.error(name+": "+a0.substring(0, 2)+": invalid option");
				ctx.stderr.println("fg: usage: fg [job_spec]");
				return 2;
			}
		}
		if( !ctx.console.jobControl()) {
			ctx.error(name+": no job control");
			return 1;
		}
		JobManager jm = ctx.console.jobManager;
		String spec = args.length > first ? ""+args[first].getValue(ctx) : "%%";
		IJob job;
		try {
			job = JobSpecs.find(jm, spec);
		} catch (JobSpecs.Ambiguous e) {
			ctx.error(name+": "+e.getMessage());
			return 1;
		}
		if( job == null || JobManager.isDone(job)) {
			ctx.error(name+": "+(args.length > first ? JobSpecs.describe(spec) : "current")+": no such job");
			return 1;
		}
		if( !jm.hasJobControl(job)) {
			ctx.error(name+": job "+job.getJobNumber()+" started without job control");
			return 1;
		}
		// as bash does, the command it continues
		ctx.stdout.println(job.getCommandLine());
		ctx.stdout.flush();
		return ctx.console.foreground(job);
	}
}
