package us.bringardner.shell.job;

import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ConsoleSignal;

public class JobManager {



	private List<IJob> terminated= new ArrayList<>();
	private List<IJob> jobs= new ArrayList<>();
	//The upper limit for a PID on Linux is 32768
	private static int nextPid = 100000;

	public static synchronized int getNextPid() {
		return nextPid++;
	}

	/**
	 * For testing only
	 * @param pid
	 */
	public static void setNextPid(int pid) {
		nextPid = pid;		
	}

	/**
	 * Add a job to the list setting the PID and JobNumber
	 * @param job
	 * @return the PID assigned to this job (a.k.a. last PID
	 */
	public int addJob(final IJob job) {

		if( job.getPid()>=0) {
			throw new RuntimeException("Logic error pid alread set = "+job.getPid());
		}
		int ret = getNextPid();
		job.setPid( ret);
		job.setJobNumber(jobs.size());
		jobs.add(job);

		job.addJobStateChangeListner((ctx,from,to)->{
			if( from.equals(to)) {
				return;
			}
			if( to == JobState.Termnated) {

			}


			//System.out.println("State change pid="+job.getPid()+" from= "+from+" to="+to);
		});
		return ret;
	}

	public IJob getJob(int idx) {
		for(IJob j : jobs) {
			if( j.getPid()==idx || j.getJobNumber()==idx) {
				return j;
			}
		}
		return null;
	}

	/*
	public int size() {
		return jobs.size();
	}
	 */
	public List<IJob> getJobs(){
		List<IJob> ret = new ArrayList<IJob>();
		for(int idx=0,sz=jobs.size(); idx<sz;idx++ ) {
			IJob job = jobs.get(idx);
			if( job.getState()==JobState.Notified) {
				terminated.add(0,job);
			} else {
				job.setJobNumber(ret.size());
				ret.add(job);
			}
		}

		jobs = ret;
		return ret;
	}

	public void clear() {
		for(IJob job : jobs) {
			if(job.isRunning()) {
				job.handleSignal(ConsoleSignal.Terminate);
			}
		}
		jobs.clear();

	}



}
