package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;

public class Disown extends ShellCommand{
	static String name = "disown";
	static String help = "disown [-ar] [-h] [id ...]\n"
			+ "Without options, remove each id from the table of active jobs. Each id may be a job specification jobspec or a process ID pid;"
			+ " if id is a pid, disown uses the job containing pid as jobspec.\n"
			+ "\n"
			
			+ "If the -h option is supplied, disown does not remove the jobs corresponding to each id from the jobs table,"
			+ " but rather marks them so the shell does not send SIGHUP to the job if the shell receives a SIGHUP.\n"
			+ "\n"
			
			+ "If no id is supplied, the -a option means to remove or mark all jobs;"
			+ " the -r option without an id argument removes or marks running jobs."
			+ " If no id is supplied, and neither the -a nor the -r option is supplied, disown removes or marks the current job.\n"
			+ "\n"
			
			+ "The return value is 0 unless an id does not specify a valid job"
			;

	public Disown() {
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
						if( idx >= args.length) {
							ctx.stderr.println("-p: option requires an argument");
							return 1;
						}
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
			List<Integer> complete = new ArrayList<Integer>();

			while( !done ) {
				for(IJob job : jobs) {
					if( !complete.contains(job.getPid())) {
						if( !job.isRunning()) {
							ret = job.getExitCode();
							jobId = job.getPid();
							complete.add(jobId);
							//System.out.println("jobId="+jobId+" ret = "+ret);
							if(f || complete.size()==jobs.size()) {
								done = true;
								break;
							}
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
