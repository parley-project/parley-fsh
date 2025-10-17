package us.bringardner.shell.antlr.statement;


import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.PipeStatementContext;
import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.ConsoleSignal;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;

public class PipeStatement extends Statement{
	

	Statement [] cmds;
	CommandThread [] threads;
	private String[] ops;
	private boolean doTime;


	public PipeStatement(ParserRuleContext context, boolean doTime, Statement[] stmts, String[] ops) {
		super(context);
		cmds = stmts;
		this.ops = ops;
		this.doTime = doTime;
	}

	public CommandThread [] getCommandThreads() {
		return threads;
	}
	
	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;

		CommandThread [] threads = new CommandThread[cmds.length];
		threads[0] = new CommandThread(ctx,cmds[0]);
		
		//  Create the threads
		for (int idx = 1; idx < cmds.length; idx++) {
			ShellContext ctx2 = ctx.subShell();
			threads[idx] = new CommandThread(ctx2,cmds[idx]);			
		}
		// set up pipes
		long startTime = System.currentTimeMillis();
		for (int idx = 0; idx < cmds.length-1; idx++) {
			CommandThread t = threads[idx];
			CommandThread t2 = threads[idx+1];			
			PipedInputStream  in = new PipedInputStream();
			PipedOutputStream out = new PipedOutputStream(in);
			t.ctx.stdout = new PrintStream(out);
			t2.ctx.stdin = in;
			if(ops[idx].equals("|&")) {
				t.ctx.stderr = t.ctx.stdout; 
			}
		}

		//start threads
		for(CommandThread t : threads) {
			t.start();
		}
		boolean done = false;
		while(!done) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			int cnt = 0;
			for(CommandThread t : threads) {
				if(t.hasStarted() && !t.isRunning()) {
					cnt++;
				} else {
					break;
				}
			}
			done = cnt == cmds.length;
		}
		ret = threads[cmds.length-1].exitCode;
		PipeStatementContext pctx = (PipeStatementContext)getContext();
		if( pctx.NOT()!=null) {
			ret = ret==0?1:0;
		}

		if( doTime) {
			long time = System.currentTimeMillis()-startTime;
			

			if( pctx.parg !=null ) {
				// use POSIX time format
				threads[cmds.length-1].ctx.stderr.println("posix real "+time);
			} else {
				//0m0.001s
				threads[cmds.length-1].ctx.stderr.println("real "+time);
			}
		}
		
		for(CommandThread t : threads) {
			t.handleSignal(ConsoleSignal.ChildStopped);
		}
		return ret;
	}

}
