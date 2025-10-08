package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.Console;
import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

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

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		ShellArgument options = parserArgs(ctx, Options.class);
		int jobs[] = new int[0];
/*
[1]+  Running                 sleep 10 &
[1]+  Done                    sleep 10
*/
		if( options.paths !=null) {
			jobs = new int[options.paths.size()];
			for (int idx = 0; idx < jobs.length; idx++) {
				jobs[idx] = Integer.parseInt(options.paths.get(idx));
			}
		}
		
		for(Integer idx=0,sz=Console.jobs.size(); idx < sz; idx++) {
			boolean show = false;
			CommandThread job = Console.jobs.get(idx);
			Console.JobState state = job.getState();
			if( state == Console.JobState.Running) {
				show = !options.options.contains(Options.s) || options.options.contains(Options.r);
			} else if( state == Console.JobState.Stopped) {
				show = options.options.contains(Options.s) || !options.options.contains(Options.r);
			} else if( state == Console.JobState.Termnated) {
				show = !options.options.contains(Options.s) && !options.options.contains(Options.r);
			}
			
			
			if( show ) {
				if(options.options.contains(Options.l)) {
					ctx.stdout.println("["+((idx+1))+"] "+job.pid+" "+state+" "+job.toString());
				} else {
					ctx.stdout.println("["+((idx+1))+"] "+state+" "+job.toString());
				}
			}
		}
		
		return ret;
	}

}
