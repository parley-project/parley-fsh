package us.bringardner.shell.antlr.statement;


import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

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

	private static void forwardControl(ShellContext ctx, CommandThread [] threads) {
		RuntimeException stop = ctx.getException();
		boolean paused = ctx.isPaused();
		for (int idx = 1; idx < threads.length; idx++) {
			ShellContext stage = threads[idx].ctx;
			if( stop != null && stage.getException() == null ) {
				stage.setExecption(stop);
			}
			if( stage.isPaused() != paused ) {
				stage.setPause(paused);
			}
		}
	}
	
	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;

		CommandThread [] threads = new CommandThread[cmds.length];
		this.threads = threads;
		threads[0] = new CommandThread(ctx,cmds[0]);
		// the first command runs on the caller's context, so its streams must be restored afterward
		PrintStream callerOut = ctx.stdout;
		PrintStream callerErr = ctx.stderr;
		List<PipedInputStream> pipes = new ArrayList<>();
		try {
		
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
				pipes.add(in);
				PipedOutputStream out = new PipedOutputStream(in);
				t.ctx.stdout = new PrintStream(out);
				t2.ctx.stdin = in;
				if(ops[idx].equals("|&")) {
					t.ctx.stderr = t.ctx.stdout; 
				}
			}
			// CommandThread closes its stdout when done; don't let the last stage close the caller's stream
			CommandThread last = threads[cmds.length-1];
			last.ctx.stdout = new PrintStream(last.ctx.stdout, true) {
				@Override
				public void close() {
					flush();
				}
			};

			//start threads
			for(CommandThread t : threads) {
				t.start();
			}
			for(CommandThread t : threads) {
				while(t.isAlive()) {
					// the first stage runs on ctx, the others on their own context: pass on a stop or suspend
					forwardControl(ctx, threads);
					try {
						t.join(50);
					} catch (InterruptedException e) {
					}
				}
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
		} finally {
			ctx.stdout = callerOut;
			ctx.stderr = callerErr;
			for(PipedInputStream in : pipes) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
		return ret;
	}

}
