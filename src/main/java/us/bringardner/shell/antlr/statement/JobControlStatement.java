package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.Job_control_statementContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
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
		JobManager jm = sc.console.jobManager;
		List<IJob> ijobs = jm.getJobs();
		int jobSize = ijobs.size();

		if( jobSpecs !=null && jobSpecs.size()>0) {
			for (String tmp : jobSpecs) {
				int i = parseJobSpec(jobSize,tmp);
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
		case JOBS: shell = new Jobs(specs); break;
		//case FG: shell = new Jobs(); break;
		//case BG: shell = new Jobs(); break;
		default:
			throw new IllegalArgumentException("Unexpected value for job control cmd: " + cmd);
		}

		shell.setArgs(args);	
		
		return shell.process(sc);
	}

	public static int parseJobSpec(int jobSize,String tmp) throws IOException {
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
				case '?':throw new IOException("? Not implemented");
				default:
					throw new IllegalArgumentException("Unexpected value: " + tmp.charAt(0));
				}
			}
		} else {
			int ret = Integer.parseInt(tmp);
			return ret;
		}
		return -1;
	}

	public void setJobSpecs(List<String> jobspecs) {
		this.jobSpecs = jobspecs;		
	}


}
