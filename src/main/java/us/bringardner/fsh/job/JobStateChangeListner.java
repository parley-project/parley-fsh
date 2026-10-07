package us.bringardner.fsh.job;

public interface JobStateChangeListner {
	public void JobStateChanged(IJob job, JobState fromState,JobState toState);
}
