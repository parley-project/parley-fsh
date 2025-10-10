package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.Job_control_statementContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.commands.Jobs;
import us.bringardner.shell.commands.Kill;
import us.bringardner.shell.commands.Wait;

public class JobControlStatement extends Statement{

	
	public JobControlStatement(ParserRuleContext context) {
		super(context);
	}

	private List<String> jobSpecs;



enum JobControlCmd {KILL,WAIT,SUSPEND,DISOWN,JOBS,FG,BG}

	
	@Override
	protected int execute(ShellContext sc) throws IOException {
		if( jobSpecs !=null && jobSpecs.size()>0) {
			String [] tmp = new String[sc.args.length+jobSpecs.size()];
			for (int idx = 0; idx < tmp.length; idx++) {
				if( idx < sc.args.length) {
					tmp[idx] = sc.args[idx];
				} else {
					tmp[idx] = jobSpecs.get(idx-sc.args.length);
				}
			}
			sc.args = tmp;
		}
		
		Job_control_statementContext ctx = (Job_control_statementContext) getContext();
		JobControlCmd cmd = JobControlCmd.valueOf(ctx.cmd.getText().toUpperCase());
		ShellCommand shell = null;
		switch (cmd) {
		case KILL: shell = new Kill(); 
		break;
		case WAIT: shell = new Wait(); break;
		//case SUSPEND: shell = new Jobs(); break;
		//case DISOWN: shell = new Jobs(); break;
		case JOBS: shell = new Jobs(); break;
		//case FG: shell = new Jobs(); break;
		//case BG: shell = new Jobs(); break;
		default:
			throw new IllegalArgumentException("Unexpected value for job control cmd: " + cmd);
		}
		
		shell.setArgs(args);	
		
		return shell.process(sc);
	}

	public void setJobSpecs(List<String> jobspecs) {
		this.jobSpecs = jobspecs;		
	}


}
