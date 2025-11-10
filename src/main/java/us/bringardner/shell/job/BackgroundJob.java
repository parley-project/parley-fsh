package us.bringardner.shell.job;

import us.bringardner.shell.Console.CommandThread;
import us.bringardner.shell.ConsoleSignal;

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
	
	@Override
	public int getExitCode() {
		if( started && running) {
			System.out.println("asking at wrong time started="+started+"running="+running+" isRunning="+isRunning()+" child="+child.isRunning());
		}
		return child.exitCode;
		
	}


	@Override
	public void interuptJob() {
		child.getThread().interrupt();
		
	}

/*
Amoxicillin
Atorvastatin Calcium
Lisinopril
HumaLOG KwikPen
Telmisartan-HCTZ
Kariva
Methadone HCl
Mirtazapine
Lialda
Temazepam
Xanax
Levothyroxine Sodium
Aspirin-Dipyridamole ER

 */
	@Override
	public String getCommandLine() {		
		return child.toString();
	}
	

}
