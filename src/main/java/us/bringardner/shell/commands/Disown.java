package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ConsoleSignal;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;
import us.bringardner.shell.job.JobState;

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

		ShellArgument ops = parserArgs(ctx, DisownOptions.class);

		JobManager jm = ctx.console.jobManager;

		int jobSize = jm.getJobs().size();

		List<IJob> jobs = new ArrayList<>();
		if( ops.paths.size()>0) {
			for(String val : ops.paths) {
				int id = JobControlStatement.parseJobSpec(jobSize, val);
				IJob job = jm.getJob(id);
				if( job!=null ) {
					if( !jobs.contains(job)) {
						jobs.add(job);
					}
				} else {
					ctx.stderr.println("disown: "+val+": no such job");
					return 1;
				}
			}
		} else {
			if(!ops.options.contains(DisownOptions.a) && !ops.options.contains(DisownOptions.r)) {
				IJob job = jm.getGetCurrentJob();
				if( job != null && !jobs.contains(job)) {
					jobs.add(job);
				}
			} else if(ops.options.contains(DisownOptions.a)) {
				jobs.addAll(jm.getJobs());
			} else if(ops.options.contains(DisownOptions.r)) {
				for(IJob job : jm.getJobs()) {
					if( job.getState() == JobState.Running && !jobs.contains(job)) {
						jobs.add(job);
					}
				}
			}
		}
		for(IJob job : jobs) {
			if( !job.isDisowned()) {
				if(ops.options.contains(DisownOptions.h)) {
					job.addIgnoreSignal(ConsoleSignal.Hup);
				} else {
					job.setDisowned(true);
				}
			}
		}

		return ret;

	}

}
