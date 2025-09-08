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
	protected int execute(ShellContext nc) throws IOException {
		int ret = 0;
		try {

			ShellContext ctx = nc.subShell();
			ctx.stdin = new ByteArrayInputStream("".getBytes());
			CommandThread thread = new CommandThread(ctx,stmt);	
			thread.start();
			while(!thread.hasStarted()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
			Console.jobs.add(thread);
			String tmp = "["+Console.jobs.size()+"] "+thread.pid;
			nc.stdout.println(tmp);
		} catch (Exception e) {
			ret = 1;
			nc.stderr.println(e.toString());
		}

		return ret;

	}

}
