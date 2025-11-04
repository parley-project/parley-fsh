package us.bringardner.shell.job;

import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.ConsoleSignal;

public class BackgroundJob extends AbstractJob{

	CommandThread child;
	
	public BackgroundJob(CommandThread thread) {
		super(thread.ctx);
		child = thread;			
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
	public String toString() {
		return child.toString();
	}
	
	@Override
	public void handleSignal(ConsoleSignal signal)  {
		JobState cstate = getState();
		
		switch (cstate) {
		case Termnated:
		case Notified:return;
		default:
			break;
		}
		
		if(!isIgnoreSignal(signal)) {
			switch (signal) {
			case ChildStopped: 
				child.handleSignal(signal);
				break;
			case Continue: 
				if( cstate == JobState.Suspended) {
					setState(JobState.Running);
				}
				
				break;
			case Hup: 
			case Interupt:
			case Terminate:
			case Kill: 
				setState(JobState.Termnated);
				break;
			case Suspend:
				setState(JobState.Suspended);
				break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + signal);
			}
				
		}
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
