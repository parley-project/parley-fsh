package us.bringardner.fsh.job;

import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellContext;

public interface IJob {

	ShellContext getShellContext();
	
	int getPid();

	void setPid(int nextPid);

	int getJobNumber();

	void setJobNumber(int size);

	Exception getError() ;

	JobState getState();

	void addIgnoreSignal(ConsoleSignal signal);
	boolean isIgnoreSignal(ConsoleSignal signal);
	
	void handleSignal(ConsoleSignal continue1);

	void setState(JobState suspended);

	boolean isRunning();

	int getExitCode();

	void start();

	boolean hasStarted();

	int addJobStateChangeListner(JobStateChangeListner listner);
	
	void removeJobStateChangeListner(int id);

	boolean isDisowned();
	void setDisowned(boolean val);

	String getCommandLine();


	/** a program a command of the job started; it is stopped, continued and signalled with the job */
	void addProcess(Process p);

	void removeProcess(Process p);

	/** stop the job (Ctrl-Z, kill -STOP); how is what jobs says: Stopped or Stopped (signal) */
	void stopJob(String how);

	/** a stopped job runs again (fg, bg, kill -CONT) */
	void continueJob();

	/** a signal ends the job (Ctrl-C, kill): its status is 128 + signum */
	void signalJob(int signum);

	/** what the job's commands throw once a signal has ended it, or null */
	RuntimeException getStopRequest();

	/** the signal that ended the job, or null */
	Integer getTerminatedBy();

	String getStoppedHow();

	/** stopped while in the foreground and not yet reported */
	boolean isStopNoticeDue();

	void setStopNoticeDue(boolean due);

	/** the job's thread */
	Thread getThread();
}
