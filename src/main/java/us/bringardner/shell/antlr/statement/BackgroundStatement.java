package us.bringardner.shell.antlr.statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.job.BackgroundJob;

public class BackgroundStatement extends Statement{

	Statement stmt;
	CommandThread thread ;

	public BackgroundStatement(ParserRuleContext context, Statement stmt) {
		super(context);
		this.stmt = stmt;
	}

	public CommandThread getCommandThread() {
		return thread;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		try {

			ShellContext ctx = sc.subShell();
			ctx.stdin = new ByteArrayInputStream("".getBytes());
			CommandThread thread = new CommandThread(ctx,stmt);	
			BackgroundJob job = new BackgroundJob(thread);
			sc.console.addJob(job);
			job.start();

			while(!job.hasStarted()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
			if( ctx.console.isInteractive) {
				String tmp = "["+(job.getJobNumber()+1)+"] "+job.pid;			
				sc.stdout.println(tmp);
			}
		} catch (Exception e) {
			ret = 1;
			sc.stderr.println(e.toString());
		}

		return ret;

	}

}
