package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;

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

	List<Integer> specs;
	public Fg(List<Integer> specs) {
		this();
		this.specs = specs;
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		//  this defines the current job
		JobManager jm = ctx.console.jobManager;
		List<Integer> jobs = new ArrayList<>();
		//  %[0-9] is parse as signed number rather that jobSpec
		for (int idx = 0; idx < args.length; idx++) {
			String tmp = ""+args[idx].getValue(ctx);
			int i = JobControlStatement.parseJobSpec(jm, tmp);
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

		throw new Console.ResumeException(job);

		
	}

}
