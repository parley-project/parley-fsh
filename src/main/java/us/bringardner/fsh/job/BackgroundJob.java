package us.bringardner.fsh.job;

import us.bringardner.fsh.Console.CommandThread;
import us.bringardner.fsh.ConsoleSignal;

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
	public String toString() {
		return child.toString();
	}
	
	@Override
	public void handleSignal(ConsoleSignal signal)  {
		JobState currentState = getState();
		
		switch (currentState) {
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
				if( currentState == JobState.Suspended) {
					setState(JobState.Running);
				}
				
				break;
			case Hup: 
			case Interupt:
			case Terminate:
			case Kill: 				
				// the job's status is 128 + the signal (143 for TERM); JobManager stops the command
				terminatedBy = signal.value;
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
		// waiting for isRunning() to become true hung when the command finished first
		while(child.isAlive()) {
			try {
				child.join(0);
			} catch (InterruptedException e) {
			}
		}
		
		return terminatedBy != null ? 128+terminatedBy : child.exitCode;
	}

	/** the signal that ended this job (kill), or null */
	private volatile Integer terminatedBy;
	
	@Override
	public int getExitCode() {
		if( terminatedBy != null ) {
			return 128+terminatedBy;
		}
		//if( started && running) {
		//	System.out.println("asking at wrong time started="+started+"running="+running+" isRunning="+isRunning()+" child="+child.isRunning());
		//}
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
