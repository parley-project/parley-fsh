package us.bringardner.fsh.job;

import java.util.function.IntSupplier;

import us.bringardner.fsh.ShellContext;

/**
 * A pipeline of an interactive command line (a | b): its stages and their programs are its own,
 * as bash makes a pipeline a job. Stopped (Ctrl-Z), it goes into the job table and the rest of
 * the line goes on; it ends when its stages do.
 */
public class PipelineJob extends AbstractJob {

	private final String commandLine;
	/** waits for the stages (passing on to them what the job gets): the pipeline's status */
	private final IntSupplier stages;
	/** how many of its stages are still running */
	private volatile IntSupplier liveStages = () -> 0;

	public PipelineJob(ShellContext ctx, String commandLine, IntSupplier stages) {
		super(ctx);
		this.commandLine = commandLine;
		this.stages = stages;
	}

	/** how many of its stages are still running (each runs a program, or is running shell code) */
	public void liveStages(IntSupplier count) {
		liveStages = count;
	}

	/** (a stage that runs no program is a running subshell in bash: the job is not stopped) */
	@Override
	protected boolean allStopped(java.util.Set<Process> stopped) {
		return super.allStopped(stopped) && programCount() >= liveStages.getAsInt();
	}

	@Override
	public int process() throws Exception {
		return stages.getAsInt();
	}

	@Override
	public void interuptJob() {
		// (the stages learn of it from its context)
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
