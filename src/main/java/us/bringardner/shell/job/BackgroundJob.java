package us.bringardner.shell.job;

import us.bringardner.shell.ConsoleSignal;
import us.bringardner.shell.Console.CommandThread;

public class BackgroundJob extends AbstractJob{

	CommandThread child;
	
	public BackgroundJob(CommandThread thread) {
		super(thread.ctx);
		child = thread;			
	}

	
	@Override
	public String toString() {
		return child.toString();
	}
	
	@Override
	public void handleSignal(ConsoleSignal signal)  {
		child.handleSignal(signal);
		super.handleSignal(signal);
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
		while(!child.isRunning()) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}
		setState(JobState.Running);
		while(child.isRunning()) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}
		
		return child.exitCode;
	}

}
