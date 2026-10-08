package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;
import us.bringardner.fsh.job.JobState;

public class Disown extends ShellCommand{
	static String name = "disown";
	static String help = "disown [-ar] [-h] [id ...]\n"
			+ "Without options, remove each id from the table of active jobs. Each id may be a job specification jobspec or a process ID pid;"
			+ " if id is a pid, disown uses the job containing pid as jobspec.\n"
			+ "\n"

			+ "If the -h option is supplied, disown does not remove the jobs corresponding to each id from the jobs table,"
			+ " but rather marks them so the shell does not send SIGHUP to the job if the shell receives a SIGHUP.\n"
			+ "\n"

			+ "If no id is supplied,"
			+ " the -a option means to remove or mark all jobs;"
			+ " the -r option without an id argument removes or marks running jobs."
			+ " "
			+ "If no id is supplied, and neither the -a nor the -r option is supplied, disown removes or marks the current job.\n"
			+ "\n"

			+ "The return value is 0 unless an id does not specify a valid job"
			;

	public Disown() {
		super(name, help);
	}

	enum DisownOptions {a,r,h};

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		ShellArgument ops = parseArgs(ctx, DisownOptions.class);

		JobManager jm = ctx.console.jobManager;
		List<IJob> jobs = new ArrayList<>();
		if( ops.paths.size()>0) {
			for(String val : ops.paths) {
				IJob job;
				try {
					job = JobSpecs.find(jm, val);
				} catch (JobSpecs.Ambiguous e) {
					ctx.error("disown: "+e.getMessage());
					ret = 1;
					continue;
				}
				if( job==null ) {
					ctx.error("disown: "+JobSpecs.describe(val)+": no such job");
					ret = 1;
				} else if( !jobs.contains(job)) {
					jobs.add(job);
				}
			}
		} else if(ops.options.contains(DisownOptions.a)) {
			jobs.addAll(jm.getJobs());
		} else if(ops.options.contains(DisownOptions.r)) {
			for(IJob job : jm.getJobs()) {
				if( job.getState() == JobState.Running) {
					jobs.add(job);
				}
			}
		} else {
			IJob job = jm.current();
			if( job == null ) {
				ctx.error("disown: current: no such job");
				return 1;
			}
			jobs.add(job);
		}

		for(IJob job : jobs) {
			if(ops.options.contains(DisownOptions.h)) {
				// it stays a job, but SIGHUP does not reach it
				job.addIgnoreSignal(ConsoleSignal.Hup);
			} else {
				job.setDisowned(true);
				jm.remove(job);
			}
		}
		return ret;

	}

}
