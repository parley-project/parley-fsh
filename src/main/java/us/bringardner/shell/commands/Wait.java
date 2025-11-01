package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.commands.Jobs.Options;
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
			+ " to complete and returns its exit status. If none of the supplied ids is a child of the shell,"
			+ " or if no arguments are supplied and the shell has no unwaited-for children, the exit status is 127.\n"
			+ "\n"
			+ "If the -p option is supplied, wait assigns the process or job identifier of the job for which the exit status is returned to the variable"
			+ " varname named by the option argument. The variable, which cannot be readonly, will be unset initially, before any assignment."
			+ " This is useful only when used with the -n option.\n"
			+ "\n"
			+ "Supplying the -f option, when job control is enabled, forces wait to wait for each id to terminate before returning its status,"
			+ " instead of returning when it changes status.\n"
			+ "\n"
			+ "If none of the ids specify one of the shell’s an active child processes, the return status is 127."
			+ " If wait is interrupted by a signal, any varname will remain unset, and the return status will be greater than 128,"
			+ " as described above (see Signals). Otherwise, the return status is the exit status of the last id."
			;

	public Wait() {
		super(name, help);
	}

	enum Optiona{f,n};
	
	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		
		ShellArgument ops = parserArgs(ctx, Options.class);
		
		String varName=null;
		boolean waitForCompetion = false;
		boolean waitForAll = false;

		JobManager jm = ctx.console.jobManager;
		List<IJob> ijobs = jm.getJobs();
		int jobSize = ijobs.size();

		List<Integer> ids = new ArrayList<>();
		
		// parse all the args
		for (int idx = 0; idx < args.length; idx++) {
			Argument a = args[idx];
			String val = ""+a.getValue(ctx);
			if( val.equals("-f") ) {
				waitForAll = true;
			} else if( val.equals("-n") ) {
				waitForCompetion = true;
			} else if( val.equals("-p") ) {
				varName = (""+args[++idx].getValue(ctx));
				ctx.unSetVariable(varName);
			}  else {
				//int i = JobControlStatement.parseJobSpec(jobSize, tmp);
				int id = Integer.parseInt(val);
				ids.add(id);
			}
		}

		

		return ret;
	}

}
