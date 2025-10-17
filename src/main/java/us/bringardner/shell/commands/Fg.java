package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.Console;
import us.bringardner.shell.Console.Job;
import us.bringardner.shell.Console.JobState;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.JobControlStatement;

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
		int ret = 0;
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
			//[1]  + continued  sleep 30
			job.child.ctx.setPause(false);
			ctx.stdout.print("["+job.jobNumber+"] continued "+job.toString());
			while(job.isRunning()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
				if( job.state == JobState.Suspended) {
					throw new Console.SuspendException();
				}
			}
		}


		return ret;
	}

}
