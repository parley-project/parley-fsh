package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.Job_control_statementContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.commands.Jobs;
import us.bringardner.shell.commands.Kill;
import us.bringardner.shell.commands.Wait;
import us.bringardner.shell.job.IJob;
import us.bringardner.shell.job.JobManager;

public class JobControlStatement extends Statement{


	public JobControlStatement(ParserRuleContext context) {
		super(context);
	}

	private List<String> jobSpecs;



	enum JobControlCmd {KILL,WAIT,SUSPEND,DISOWN,JOBS,FG,BG}


	@Override
	protected int execute(ShellContext sc) throws IOException {
		List<Integer> specs = new ArrayList<>();
		//  this defines the current job

		if( jobSpecs !=null && jobSpecs.size()>0) {
			for (String tmp : jobSpecs) {
				int i = parseJobSpec(sc.console.jobManager,tmp);
				if( i >=0 ) {
					specs.add(i);
				}
			}
		}

		Job_control_statementContext ctx = (Job_control_statementContext) getContext();
		JobControlCmd cmd = JobControlCmd.valueOf(ctx.cmd.getText().toUpperCase());
		ShellCommand shell = null;
		switch (cmd) {
		case KILL: shell = new Kill(); break;
		case WAIT: shell = new Wait(); break;
		//case SUSPEND: shell = new Jobs(); break;
		//case DISOWN: shell = new Jobs(); break;
		case JOBS: shell = new Jobs(); break;
		//case FG: shell = new Jobs(); break;
		//case BG: shell = new Jobs(); break;
		default:
			throw new IllegalArgumentException("Unexpected value for job control cmd: " + cmd);
		}

		Argument[] argsToUse = args;
		if( specs.size()>0) {
			Argument tmpArgs [] = new Argument[specs.size()+args.length];
			for (int idx = 0; idx < args.length; idx++) {
				tmpArgs[idx] = args[idx];
			}
			for (int idx = args.length; idx < tmpArgs.length; idx++) {
				final Object tid = specs.get(idx-args.length);
				tmpArgs[idx] = new Argument(null) {
					@Override
					public Object getValue(ShellContext ctx) throws IOException {
						return tid;
					}
				};
			}
			
			argsToUse = tmpArgs;
		}
		shell.setArgs(argsToUse);	
		
		return shell.process(sc);
	}

	public static int parseJobSpec(JobManager jm,String tmp) throws IOException {
		List<IJob> jobs = jm.getJobs();
		int jobSize = jobs.size();
		
		if( tmp.charAt(0) == '%') {
			tmp = tmp.substring(1);
			if(Character.isDigit(tmp.charAt(0)) ) {
				int i = Integer.parseInt(tmp)-1;
				if( i>=0 && i< jobSize) {
					return i;
				}
			} else {
				switch (tmp.charAt(0)){
				case '%':// current
				case '+':
					if( jobSize > 0 ) {
						return jobSize-1;
					}
					break;
				case '-':
					if( jobSize > 1 ) {
						return jobSize-2;
					}
					break;
				case '?'://Using ‘%?ce’, on the other hand, refers to any job containing the string ‘ce’ in its command line. If the prefix or substring matches more than one job, Bash reports an error.
					//kill: ee: ambiguous job spec
					String name  = tmp.substring(1);
					List<IJob> found = new ArrayList<IJob>();
					for(IJob job : jobs) {
						String cmd = job.getCommandLine();
						if( cmd.contains(name) ) {
							found.add(job);
						}
					}
					if( found.size()==0) {
						return -1;
					}
					if( found.size()>1) {
						throw new IOException("? "+name+": ambiguous job spec");
					}
					return found.get(0).getJobNumber();
				default:
					throw new IllegalArgumentException("Unexpected value: " + tmp.charAt(0));
				}
			}
		} else {
			try {
				int ret = Integer.parseInt(tmp);
				return ret;
			} catch (Exception e) {
			}
		}
		return -1;
	}

	public void setJobSpecs(List<String> jobspecs) {
		this.jobSpecs = jobspecs;		
	}


}
