package us.bringardner.fsh.antlr.statement;


import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.PipeStatementContext;
import us.bringardner.fsh.Console.CommandThread;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.Pipe;
import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Statement;

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
		for (int idx = 0; idx < threads.length; idx++) {
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
		// every stage is a subshell, as in bash: echo a | read x does not set x in the shell
		threads[0] = new CommandThread(ctx.isolatedSubShell(),cmds[0]);
		// (the streams are put back afterward, as when the first stage ran on the caller's context)
		PrintStream callerOut = ctx.stdout;
		PrintStream callerErr = ctx.stderr;
		java.io.InputStream callerIn = ctx.stdin;
		// shopt -s lastpipe (in a script): the last stage runs in this shell, so
		// echo hi | read v sets v
		boolean lastInShell = cmds.length > 1 && !ctx.console.isInteractive && !ctx.isIsolated()
				&& us.bringardner.fsh.Glob.option(ctx, "lastpipe");
		List<Pipe> pipes = new ArrayList<>();
		try {
		
			//  Create the threads
			for (int idx = 1; idx < cmds.length; idx++) {
				ShellContext ctx2 = lastInShell && idx == cmds.length-1 ? ctx : ctx.isolatedSubShell();
				threads[idx] = new CommandThread(ctx2,cmds[idx]);			
			}
			// set up pipes
			long startTime = System.currentTimeMillis();
			for (int idx = 0; idx < cmds.length-1; idx++) {
				CommandThread t = threads[idx];
				CommandThread t2 = threads[idx+1];			
				// (not java.io's piped streams: they fail with "Write end dead" when the thread
				// that wrote last has ended, before the stage closes its end)
				Pipe pipe = new Pipe();
				pipes.add(pipe);
				t.ctx.stdout = new PrintStream(pipe.out);
				t2.ctx.stdin = pipe.in;
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
				if( !lastInShell || t != last ) {
					t.start();
				}
			}
			if( lastInShell ) {
				// here, on this thread (the pipeline's status runs the ERR trap, not this stage)
				ctx.errTrapBlocked++;
				try {
					last.run();
				} finally {
					ctx.errTrapBlocked--;
				}
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
			// PIPESTATUS has each stage's status; with set -o pipefail the status is the last
			// failed stage's (0 if none failed)
			FshList statuses = new FshList();
			for(CommandThread t : threads) {
				statuses.add(t.exitCode);
				if( t.exitCode != 0 && ctx.console.isOptionEnabled(Option.PipeFail)) {
					ret = t.exitCode;
				}
			}
			ctx.setVariable("PIPESTATUS", statuses);
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
			ctx.stdin = callerIn;
			for(Pipe pipe : pipes) {
				pipe.in.close();
			}
		}
		return ret;
	}

	@Override
	protected boolean errexitApplies() {
		return true;
	}
}
