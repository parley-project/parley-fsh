package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;
import us.bringardner.shell.job.JobState;

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

	List<Integer> specs;
	public Bg(List<Integer> specs) {
		this();
		this.specs = specs;
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		/*
[1]+  Running                 sleep 10 &
[1]+  Done                    sleep 10
		 */
		//  this defines the current job
		JobManager jm = ctx.console.jobManager;
		List<IJob> ijobs = jm.getJobs();
		int jobSize = ijobs.size();


		List<Integer> jobs = new ArrayList<>();
		//  %[0-9] is parse as signed number rather that jobSpec
		for (int idx = 0; idx < args.length; idx++) {
			String tmp = ""+args[idx].getValue(ctx);
			int i = JobControlStatement.parseJobSpec(jobSize, tmp);
			if( i >=0) {
				jobs.add(i);
			}
		}
		if( specs !=null && specs.size()>0) {			
			jobs.addAll(specs);			
		} 

		if( jobs.size()==0) {
			ctx.stderr.println(help);
			return 1;
		}
		if( jobs.size()>1) {
			ctx.stderr.println(help);
			return 2;
		}

		IJob job = jm.getJob(jobs.get(0));
		if( job == null ) {
			ctx.stderr.println("No such job "+jobs.get(0));
			return 3;
		}
		
		JobState state = job.getState();
		if( state == JobState.Suspended) {
			job.setState(JobState.Running);
			ctx.stdout.println("["+job.getJobNumber()+"] continued "+job.toString());
		}	else {
			job.setState(JobState.Running);
		}

		return ret;
	}

}
