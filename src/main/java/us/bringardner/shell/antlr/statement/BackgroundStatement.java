package us.bringardner.shell.antlr.statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.antlr.Statement;

public class BackgroundStatement extends Statement{

	Statement stmt;

	public BackgroundStatement(ParserRuleContext context, Statement stmt) {
		super(context);
		this.stmt = stmt;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		try {

			ShellContext ctx = sc.subShell();
			ctx.stdin = new ByteArrayInputStream("".getBytes());
			CommandThread thread = new CommandThread(ctx,stmt);	
			Console.Job job = new Console.Job(thread);
			job.start();
			
			while(!job.hasStarted()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
			sc.console.addJob(job);
			String tmp = "["+Console.jobs.size()+"] "+job.pid;
			sc.stdout.println(tmp);
		} catch (Exception e) {
			ret = 1;
			sc.stderr.println(e.toString());
		}

		return ret;

	}

}
