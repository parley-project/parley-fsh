package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.Console;
import us.bringardner.shell.Console.Job;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.JobControlStatement;

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
		int jobSize = ctx.console.jobs.size();

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

		Job job = ctx.console.jobs.get(0);
		Console.JobState state = job.state;
		if( state == Console.JobState.Suspended) {
			job.child.ctx.setPause(false);
			ctx.stdout.println("["+job.jobNumber+"] continued "+job.toString());
		}			

		return ret;
	}

}
