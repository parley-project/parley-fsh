package us.bringardner.fsh.job;

import us.bringardner.fsh.Console.CommandThread;

/** cmd &: the command runs in its own thread while the shell goes on */
public class BackgroundJob extends AbstractJob{

	CommandThread child;

	public BackgroundJob(CommandThread cmdThread) {
		super(cmdThread.ctx);
		child = cmdThread;			
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof IJob) {
			IJob job = (IJob) obj;
			return getPid() == job.getPid();
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return getPid();
	}

	@Override
	public String toString() {
		return child.toString();
	}

	@Override
	public boolean isRunning() {
		return child.isRunning() && super.isRunning();
	}

	@Override
	public int process() throws Exception {
		child.start();
		while(!child.hasStarted()) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}
		setState(JobState.Running);
		while(child.isAlive()) {
			try {
				child.join(0);
			} catch (InterruptedException e) {
			}
		}
		return child.exitCode;
	}

	@Override
	public void interuptJob() {
		child.getThread().interrupt();
	}

	@Override
	public String getCommandLine() {		
		return child.toString();
	}
}
