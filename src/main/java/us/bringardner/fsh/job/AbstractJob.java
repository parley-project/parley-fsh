package us.bringardner.fsh.job;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.SignalEnabledThread;
import us.bringardner.fsh.signal.ExitException;

public abstract class AbstractJob extends SignalEnabledThread implements IJob {
	
	// set by the job's thread and read by the console thread, kill, wait and jobs
	public volatile int pid=-1;
	public volatile int exitCode;
	public volatile Exception error;
	public volatile JobState state=JobState.Idel;
	public volatile int jobNumber=-1;
	private List<JobStateChangeListner> listners = new CopyOnWriteArrayList<JobStateChangeListner>();
	private ShellContext ctx;
	private List<ConsoleSignal> ignoreSignals = new CopyOnWriteArrayList<ConsoleSignal>();
	private volatile boolean disowned = false;
	
	public AbstractJob(ShellContext ctx) {
		this.ctx = ctx;
	}
	
	
	public Thread getThread() {
		return thread;
	}
	
	public abstract int process() throws Exception;
	
	
	@Override
	public boolean isDisowned() {
		return disowned;
	}
	
	@Override
	public void setDisowned(boolean val) {
		disowned = val;
		
	}
	
	
	@Override
	public boolean isIgnoreSignal(ConsoleSignal signal) {
		return ignoreSignals.contains(signal);
	}
	
	@Override
	public void addIgnoreSignal(ConsoleSignal signal) {
		if( !ignoreSignals.contains(signal)) {
			ignoreSignals.add(signal);
		}		
	}
	
	@Override
	public ShellContext getShellContext() {
		return ctx;
	}



	@Override
	public void run() {
		started = running = true;;
		exitCode = -2121;
		
		try {
			exitCode = process();
		} catch (Exception e) {
			exitCode = 1;
			setError(e);
		}
		if( !stopping) {
			stop();
		}
		setState(JobState.Termnated);
		running = false;
				
	}
	
	@Override
	public abstract void handleSignal(ConsoleSignal signal) ;

	
	public int getPid() {
		return pid;
	}
	
	public void setPid(int pid) {
		this.pid = pid;
	}
	
	public int getExitCode() {
		return exitCode;
	}
	
	
	public Exception getError() {
		return error;
	}
	
	public void setError(Exception error) {
		this.error = error;
	}
	
	public JobState getState() {
		return state;
	}
	
	public final void setState(JobState state) {
		JobState lastState = this.state;
		this.state = state;
		switch (state) {
		case Notified:break;
		case Running:getShellContext().setPause(false);break;
		case Suspended:getShellContext().setPause(true);break;
		case Termnated: 
				ctx.setExecption(new ExitException(ctx, 1));
				stop();
				interuptJob();
				break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + state);
		}
		for(JobStateChangeListner l : listners) {
			if( l != null ) {
				l.JobStateChanged(this, lastState,state);
			}
		}		
	}
	
	


	public abstract void interuptJob();


	public int getJobNumber() {
		return jobNumber;
	}
	
	public void setJobNumber(int jobNumber) {
		this.jobNumber = jobNumber;
	}

	@Override
	public int addJobStateChangeListner(JobStateChangeListner listner) {
		int ret = listners.size();
		listners.add(listner);	
		return ret;
	}
	
	@Override
	public void removeJobStateChangeListner(int idx) {
		// keep the other listeners' ids (their index) valid
		listners.set(idx, null);		
	}
	
		
	

}
