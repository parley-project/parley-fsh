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

public class Jobs extends ShellCommand{
	enum Options {l,n,p,r,s};

	static String name = "jobs";
	// 								jobspec = %#
	static String help = "jobs [-rs] [jobspec]\n"
			+ "jobs -x command [arguments]\n"
			+ "The first form lists the active jobs. The options have the following meanings:\n"
			+ "	-l   List process IDs in addition to the normal information.\n"
			+ "	-r   Display only running jobs.\n"
			+ "	-s   Display only stopped/suspended jobs.\n"
			+ "\n"
			+ "If jobspec is given, output is restricted to information about that job. If jobspec is not supplied, \n"
			+ "	the status of all jobs is listed.\n"
			+ "If the -x option is supplied, jobs replaces any jobspec found in command or arguments with the "
			+ "	corresponding process group ID, and executes command, passing it arguments, returning its exit status."
			;

	public Jobs() {
		super(name, help);
	}

	List<Integer> specs;
	public Jobs(List<Integer> specs) {
		this();
		this.specs = specs;
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		for(us.bringardner.fsh.Argument arg : args) {
			// (an option it does not take: said with the usage, as bash's)
			String w = ""+arg.getValue(ctx);
			if( w.equals("--") || !w.startsWith("-") || w.length() < 2 ) {
				break;
			}
			for(char c : w.substring(1).toCharArray()) {
				if( "lnprsx".indexOf(c) < 0 ) {
					ctx.error(name+": -"+c+": invalid option");
					ctx.stderr.println("jobs: usage: jobs [-lnprs] [jobspec ...] or jobs -x command [args]");
					return 2;
				}
			}
		}
		if( ctx.jobsCleared ) {
			// ( jobs ): a subshell has no jobs of its own, as in bash
			return 0;
		}
		ShellArgument options = parseArgs(ctx, Options.class);
		JobManager jm = ctx.console.jobManager;
		List<IJob> jobs = new ArrayList<>();
		for(String spec : options.paths) {
			IJob job;
			try {
				job = JobSpecs.find(jm, spec);
			} catch (JobSpecs.Ambiguous e) {
				ctx.error(name+": "+e.getMessage());
				ret = 1;
				continue;
			}
			if( job == null ) {
				ctx.error(name+": "+JobSpecs.describe(spec)+": no such job");
				ret = 1;
			} else if( !jobs.contains(job)) {
				jobs.add(job);
			}
		}
		if( specs !=null) {
			for(Integer number : specs) {
				IJob job = jm.getJob(number);
				if( job != null && !jobs.contains(job)) {
					jobs.add(job);
				}
			}
		}
		if( options.paths.isEmpty() && (specs == null || specs.isEmpty())) {
			jobs.addAll(jm.getJobs());
		}
		boolean running = options.options.contains(Options.r);
		boolean stopped = options.options.contains(Options.s);
		List<IJob> reported = new ArrayList<>();
		for(IJob job : jobs) {
			JobState state = job.getState();
			boolean show = (!running && !stopped)
					|| (running && (state == JobState.Running || state == JobState.Idel))
					|| (stopped && state == JobState.Suspended);
			if( !show ) {
				continue;
			}
			if(options.options.contains(Options.p)) {
				ctx.stdout.println(job.getPid());
			} else {
				// as bash shows it: [1]+  Running                    sleep 10 &
				ctx.stdout.println(jm.describe(job, options.options.contains(Options.l)));
			}
			reported.add(job);
		}
		ctx.console.jobsListed(ctx.job);
		// a finished job is reported once, as before a prompt
		for(IJob job : reported) {
			if( JobManager.isDone(job)) {
				jm.remove(job);
			} else {
				job.setStopNoticeDue(false);
			}
		}
		return ret;
	}
}
