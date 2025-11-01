package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;

public class Wait extends ShellCommand{
	static String name = "wait";
	static String help = "wait [-fn] [-p varname] [id ...]\n"
			+ "Wait until the child process specified by each id exits and return the exit status of the last id."
			+ " Each id may be a process ID pid or a job specification jobspec; if a jobspec is supplied, wait waits for all processes in the job.\n"
			+ "\n"
			+ "If no options or ids are supplied, wait waits for all running background jobs and the last-executed process substitution,"
			+ " if its process id is the same as $!, and the return status is zero.\n"
			+ "\n"

			+ "If the -n option is supplied, wait waits for any one of the ids or, if no ids are supplied, any job or process substitution,"
			+ " to complete and returns its exit status."
			+ " If none of the supplied ids is a child of the shell,"
			+ " or if no arguments are supplied and the shell has no unwaited-for children, the exit status is 127.\n"
			+ "\n"

			+ "Supplying the -f option, when job control is enabled, forces wait to wait for each id to terminate before returning its status,"
			+ " instead of returning when it changes status.\n"
			+ "\n"

			+ "If the -p option is supplied, wait assigns the process or job identifier of the job for which the exit status is returned to the variable"
			+ " varname named by the option argument. The variable, which cannot be readonly, will be unset initially, before any assignment."
			+ " This is useful only when used with the -n option.\n"
			+ "\n"

			+ "If none of the ids specify one of the shell’s an active child processes, the return status is 127."
			+ " If wait is interrupted by a signal, any varname will remain unset, and the return status will be greater than 128,"
			+ " as described above (see Signals). Otherwise, the return status is the exit status of the last id."
			;

	public Wait() {
		super(name, help);
	}


	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		String varName=null;
		boolean f = false;
		boolean n = false;

		JobManager jm = ctx.console.jobManager;

		int jobSize = jm.getJobs().size();

		List<IJob> jobs = new ArrayList<>();

		// parse all the args
		for (int idx = 0; idx < args.length; idx++) {
			Argument a = args[idx];
			String val = ""+a.getValue(ctx);
			if( val.startsWith("-")) {
				String tmp = val.substring(1);
				for(char c : tmp.toCharArray()) {
					switch (c) {
					case 'f':f=true;break;
					case 'n':n=true;break;
					case 'p':
						varName = (""+args[++idx].getValue(ctx));
						ctx.unSetVariable(varName);
						break;

					default:
						throw new IllegalArgumentException("Unknown option in wait. value: " + c);
					}
				}
			} else {
				int id = JobControlStatement.parseJobSpec(jobSize, val);
				IJob job = jm.getJob(id);
				if( job!=null) {
					jobs.add(job);
				}
			}
		}

		if( jobs.isEmpty()) {
			/*
 + "If no options or ids are supplied, wait waits for all running background jobs and the last-executed process substitution,"
			+ " if its process id is the same as $!, and the return status is zero.\n"

			 */
			jobs.addAll(jm.getJobs());
		}

		int jobId = -1;
		
		if( n && jobs.isEmpty()) {
			ret = 127;
		} else {
			boolean done = false;
			Stack<Integer> exitCodes = new Stack<>();
			
			while( !done ) {
				for(IJob job : jobs) {
					if( !job.isRunning()) {
						exitCodes.push(job.getExitCode());
						jobId = job.getPid();
						if(f || exitCodes.size()==jobs.size()) {
							ret = job.getExitCode();
							done = true;
							break;
						}
					}
				}
				
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
		
		if( varName !=null) {
			ctx.setVariable(varName, jobId);
		}

		return ret;
	}

}
