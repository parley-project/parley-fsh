package us.bringardner.shell.job;

import us.bringardner.shell.ConsoleSignal;
import us.bringardner.shell.ShellContext;

public interface IJob {

	ShellContext getShellContext();
	
	int getPid();

	void setPid(int nextPid);

	int getJobNumber();

	void setJobNumber(int size);

	Exception getError() ;

	JobState getState();

	void handleSignal(ConsoleSignal continue1);

	void setState(JobState suspended);

	boolean isRunning();

	int getExitCode();

	void start();

	boolean hasStarted();

	int addJobStateChangeListner(JobStateChangeListner listner);
	
	void removeJobStateChangeListner(int id);


}
