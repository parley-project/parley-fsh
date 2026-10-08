package us.bringardner.fsh.job;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.SignalEnabledThread;
import us.bringardner.fsh.signal.SignalException;

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

	/** the programs the job's commands run now: stopped, continued and signalled with it */
	private final Set<Process> processes = ConcurrentHashMap.newKeySet();
	/** its programs that read the terminal themselves */
	private final Set<Object> terminalUsers = ConcurrentHashMap.newKeySet();
	/** the signal that ended the job (kill, Ctrl-C), or null */
	private volatile Integer terminatedBy;
	/** what its commands throw once a signal has ended the job */
	private volatile RuntimeException stopRequest;
	/** why it is stopped, as jobs shows it: Stopped (Ctrl-Z) or Stopped (signal) */
	private volatile String stoppedHow = "Stopped";
	/** stopped while in the foreground: the shell says so before the next prompt */
	private volatile boolean stopNoticeDue;
	/** process() has returned */
	private volatile boolean finished;

	public AbstractJob(ShellContext ctx) {
		this.ctx = ctx;
		ctx.job = this;
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
		} catch (SignalException e) {
			exitCode = e.exitCode();
		} catch (Exception e) {
			exitCode = 1;
			setError(e);
		}
		finished = true;
		if( !stopping) {
			stop();
		}
		setState(JobState.Termnated);
		running = false;
	}

	/**
	 * A signal for the job, as kill sends it: CONT continues it, TSTP and STOP stop it, CHLD is
	 * ignored and the others end it.
	 */
	@Override
	public void handleSignal(ConsoleSignal signal)  {
		JobState currentState = getState();
		if( currentState == JobState.Termnated || currentState == JobState.Notified || isIgnoreSignal(signal)) {
			return;
		}
		switch (signal) {
		case ChildStopped:
		case UnKnown:
			break;
		case Continue:
			continueJob();
			break;
		case Suspend:
			stopJob("Stopped");
			break;
		case Stop:
			// bash says Stopped for both
			stopJob("Stopped");
			break;
		default:
			int signum = ProcessSignals.number(signal.label);
			signalJob(signum < 0 ? signal.value : signum);
		}
	}

	@Override
	public void addProcess(Process p) {
		processes.add(p);
		if( state == JobState.Suspended ) {
			ProcessSignals.send(Set.of(p), "STOP");
		}
	}

	@Override
	public void removeProcess(Process p) {
		processes.remove(p);
		if( processes.isEmpty()) {
			// the group is gone with its last program
			processGroup = 0;
		}
	}

	private volatile long processGroup;

	@Override
	public long getProcessGroup() {
		return processGroup;
	}

	@Override
	public void setProcessGroup(long group) {
		processGroup = group;
	}

	@Override
	public void addTerminalUser(Object user) {
		terminalUsers.add(user);
	}

	@Override
	public void removeTerminalUser(Object user) {
		terminalUsers.remove(user);
	}

	@Override
	public java.util.Collection<Object> getTerminalUsers() {
		return new java.util.ArrayList<>(terminalUsers);
	}

	@Override
	public void stopJob(String how) {
		if( state == JobState.Running ) {
			stoppedHow = how;
			stopNoticeDue = true;
			setState(JobState.Suspended);
		}
	}

	@Override
	public void continueJob() {
		if( state == JobState.Suspended ) {
			setState(JobState.Running);
		}
	}

	/** the signal ends the job: its programs get it and its commands stop */
	@Override
	public void signalJob(int signum) {
		JobState was = state;
		if( was == JobState.Termnated || was == JobState.Notified ) {
			return;
		}
		terminatedBy = signum;
		if( stopRequest == null ) {
			stopRequest = new SignalException(signum);
		}
		String name = ProcessSignals.name(signum);
		ProcessSignals.send(processes, name == null ? String.valueOf(signum) : name);
		if( was == JobState.Suspended ) {
			// a stopped program gets it once it runs again
			ProcessSignals.send(processes, "CONT");
		}
		ctx.setPause(false);
		setState(JobState.Termnated);
	}

	@Override
	public RuntimeException getStopRequest() {
		return stopRequest;
	}

	@Override
	public Integer getTerminatedBy() {
		return terminatedBy;
	}

	@Override
	public String getStoppedHow() {
		return stoppedHow;
	}

	@Override
	public boolean isStopNoticeDue() {
		return stopNoticeDue;
	}

	@Override
	public void setStopNoticeDue(boolean due) {
		stopNoticeDue = due;
	}

	public int getPid() {
		return pid;
	}

	public void setPid(int pid) {
		this.pid = pid;
	}

	public int getExitCode() {
		Integer by = terminatedBy;
		return by != null ? 128+by : exitCode;
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
		case Running:
			if( lastState == JobState.Suspended ) {
				ProcessSignals.send(processes, "CONT");
			}
			getShellContext().setPause(false);
			break;
		case Suspended:
			getShellContext().setPause(true);
			ProcessSignals.send(processes, "STOP");
			break;
		case Termnated:
			if( !finished ) {
				// ended from outside (kill): what runs stops
				if( stopRequest == null ) {
					stopRequest = new SignalException(15);
				}
				ctx.setExecption(stopRequest);
				stop();
				interuptJob();
			}
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
		listners.set(idx, null);
	}

}
