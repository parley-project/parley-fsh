package us.bringardner.fsh.job;

import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ConsoleSignal;

/**
 * The shell's jobs, as bash keeps them: a job's number stays the same while it is in the table
 * (a new one gets one more than the highest), and the current job (%+) is the one most recently
 * stopped, or else the one most recently started or continued; the previous job (%-) is the
 * next by the same rule.
 */
public class JobManager {

	/** in the order of their numbers */
	private final List<IJob> jobs= new ArrayList<>();
	/** the most recently started, stopped or continued first */
	private final List<IJob> recent = new ArrayList<>();
	private static int nextPid = 100000;
	/** process id to status, of the jobs that finished and left the table */
	private final java.util.Map<Long, Integer> finished = new java.util.HashMap<>();

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
	 * Add a job to the table, giving it a process id and a job number.
	 * @return the process id given to the job ($!)
	 */
	public synchronized int addJob(final IJob job) {
		if( job.getPid()>=0) {
			throw new RuntimeException("Logic error pid alread set = "+job.getPid());
		}
		int ret = getNextPid();
		job.setPid( ret);
		job.setJobNumber(jobs.isEmpty() ? 1 : jobs.get(jobs.size()-1).getJobNumber()+1);
		jobs.add(job);
		touch(job);
		return ret;
	}

	/** the job was stopped or continued: it is the most recent */
	public synchronized void touch(IJob job) {
		recent.remove(job);
		recent.add(0, job);
	}

	/** the job numbered number (%number), or null */
	public synchronized IJob getJob(int number) {
		for(IJob j : jobs) {
			if( j.getJobNumber()==number) {
				return j;
			}
		}
		return null;
	}

	/** the job whose process id is pid, or null */
	public synchronized IJob getJobByPid(long pid) {
		for(IJob j : jobs) {
			if( j.getPid()==pid) {
				return j;
			}
		}
		return null;
	}

	/** the jobs in the table, in the order of their numbers */
	public synchronized List<IJob> getJobs(){
		return new ArrayList<>(jobs);
	}

	/** the current job (%+, %%), or null */
	public synchronized IJob current() {
		return pick(null);
	}

	/** the previous job (%-), or null */
	public synchronized IJob previous() {
		IJob current = current();
		return current == null ? null : pick(current);
	}

	/** the most recently stopped job, or else the most recent one; not except */
	private IJob pick(IJob except) {
		for(IJob j : recent) {
			if( j != except && j.getState() == JobState.Suspended ) {
				return j;
			}
		}
		for(IJob j : recent) {
			if( j != except ) {
				return j;
			}
		}
		return null;
	}

	/** what jobs shows after the job's number: + for the current job, - for the previous one */
	public synchronized char marker(IJob job) {
		return job == current() ? '+' : job == previous() ? '-' : ' ';
	}

	public synchronized void clear() {
		for(IJob job : jobs) {
			if(job.isRunning()) {
				job.handleSignal(ConsoleSignal.Terminate);
			}
		}
		jobs.clear();
		recent.clear();
		finished.clear();
	}

	/** the current job, or null */
	public synchronized IJob getGetCurrentJob() {
		return current();
	}

	public void remove(IJob job) {
		Runnable after;
		synchronized (this) {
			if( jobs.remove(job) && isDone(job)) {
				finished.put((long) job.getPid(), job.getExitCode());
			}
			recent.remove(job);
			after = isDone(job) ? onRemoved.remove(job) : null;
		}
		if( after != null ) {
			after.run();
		}
	}

	/** what to do when a job that is done leaves the table (a coproc's variables are unset) */
	private final java.util.Map<IJob,Runnable> onRemoved = new java.util.IdentityHashMap<>();

	public synchronized void whenRemoved(IJob job, Runnable r) {
		onRemoved.put(job, r);
	}

	/** wait (for all): the statuses of the jobs that left the table are forgotten, as in bash */
	public synchronized void forgetFinished() {
		finished.clear();
	}

	/** the status of a job that finished and left the table (wait pid still gets it, as in bash) */
	public synchronized Integer finishedStatus(long pid) {
		return finished.get(pid);
	}

	public synchronized boolean contains(IJob job) {
		return jobs.contains(job);
	}

	/** a job's state as jobs shows it: Running, Stopped, Done, Exit 3, Terminated: 15 ... */
	public static String status(IJob job) {
		switch (job.getState()) {
		case Running:
		case Idel:
			return "Running";
		case Suspended:
			return job.getStoppedHow();
		default:
			Integer by = job.getTerminatedBy();
			if( by != null ) {
				return ProcessSignals.describe(by);
			}
			int code = job.getExitCode();
			return code == 0 ? "Done" : "Exit "+code;
		}
	}

	/** the line jobs shows for a job (and the shell before a prompt): [1]+  Running    sleep 10 & */
	public synchronized String describe(IJob job, boolean withPid) {
		JobState state = job.getState();
		String text = job.getCommandLine()+(state == JobState.Running || state == JobState.Idel ? " &" : "");
		if( withPid ) {
			return String.format("[%d]%c %d %-27s%s", job.getJobNumber(), marker(job), job.getPid(), status(job), text);
		}
		return String.format("[%d]%c  %-27s%s", job.getJobNumber(), marker(job), status(job), text);
	}

	/** finished (and not waited for or reported yet) */
	public static boolean isDone(IJob job) {
		JobState state = job.getState();
		return state == JobState.Termnated || state == JobState.Notified;
	}

	/**
	 * What the interactive shell says before a prompt, as bash does: the jobs that have finished
	 * (they then leave the table) and those stopped since the last prompt.
	 */
	public synchronized List<String> notices() {
		List<String> ret = new ArrayList<>();
		List<IJob> done = new ArrayList<>();
		for(IJob job : jobs) {
			if( isDone(job)) {
				ret.add(describe(job, false));
				done.add(job);
			} else if( job.getState() == JobState.Suspended && job.isStopNoticeDue()) {
				ret.add(describe(job, false));
				job.setStopNoticeDue(false);
			}
		}
		for(IJob job : done) {
			remove(job);
		}
		return ret;
	}
}
