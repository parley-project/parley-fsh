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

}
