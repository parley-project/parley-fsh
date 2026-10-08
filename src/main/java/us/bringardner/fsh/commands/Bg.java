package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;
import us.bringardner.fsh.job.JobState;

public class Bg extends ShellCommand{

	static String name = "bg";
	// 								jobspec = %#
	static String help = "bg [jobspec ...]\n"
			+ "Resume each suspended job jobspec in the background, as if it had been started with ‘&’."
			+ " If jobspec is not supplied, the shell uses its notion of the current job."
			+ " bg returns zero unless it is run when job control is not enabled,"
			+ " or, when run with job control enabled, any jobspec was not found or specifies a job that was started without job control."
			;

	public Bg() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		if( !ctx.console.isInteractive ) {
			ctx.error(name+": no job control");
			return 1;
		}
		JobManager jm = ctx.console.jobManager;
		List<String> specs = new ArrayList<>();
		for (int idx = 0; idx < args.length; idx++) {
			specs.add(""+args[idx].getValue(ctx));
		}
		if( specs.isEmpty()) {
			specs.add("%%");
		}
		for(String spec : specs) {
			IJob job;
			try {
				job = JobSpecs.find(jm, spec);
			} catch (JobSpecs.Ambiguous e) {
				ctx.error(name+": "+e.getMessage());
				ret = 1;
				continue;
			}
			if( job == null || JobManager.isDone(job)) {
				ctx.error(name+": "+JobSpecs.describe(spec)+": no such job");
				ret = 1;
				continue;
			}
			if( job.getState() != JobState.Suspended ) {
				ctx.error(name+": job "+job.getJobNumber()+" already in background");
				continue;
			}
			job.continueJob();
			jm.touch(job);
			// as bash says it: [1]+ sleep 10 &
			ctx.stdout.println("["+job.getJobNumber()+"]"+jm.marker(job)+" "+job.getCommandLine()+" &");
		}
		return ret;
	}
}
