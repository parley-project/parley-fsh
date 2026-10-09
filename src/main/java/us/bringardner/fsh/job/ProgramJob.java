package us.bringardner.fsh.job;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import us.bringardner.fsh.ShellContext;

/**
 * A program of a command line that was stopped in the foreground (Ctrl-Z): as in bash, it is a
 * job of its own, and the rest of the line goes on. It ends when the program does (see ended).
 */
public class ProgramJob extends AbstractJob {

	private final String commandLine;
	private final CompletableFuture<Integer> status = new CompletableFuture<>();

	public ProgramJob(ShellContext ctx, String commandLine) {
		super(ctx);
		this.commandLine = commandLine;
	}

	/** the program ended with status */
	public void ended(int exitStatus) {
		status.complete(exitStatus);
	}

	@Override
	public int process() throws Exception {
		while( true ) {
			try {
				return status.get();
			} catch (InterruptedException e) {
				// (it ends with its program)
			} catch (ExecutionException e) {
				return 1;
			}
		}
	}

	@Override
	public void interuptJob() {
	}

	@Override
	public String getCommandLine() {
		return commandLine;
	}

	@Override
	public String toString() {
		return commandLine;
	}
}
