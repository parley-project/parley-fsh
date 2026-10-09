package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobState;
import us.bringardner.fsh.job.JobManager;

public class Wait extends ShellCommand{
	static String name = "wait";
	static String help = "wait [-fn] [-p varname] [id ...]\n"
			+ "Wait until the child process specified by each id exits and return the exit status of the last id."
			+ " Each id may be a process ID pid or a job specification jobspec; if a jobspec is supplied, wait waits for all processes in the job.\n"
			+ "\n"
			+ "If no options or ids are supplied, wait waits for all running background jobs and the last-executed process substitution,"
			+ " if its process id is the same as $!, and the return status is zero.\n"
			+ "\n"

			+ "If the -n option is supplied, wait waits for any one of the ids or, if no ids are supplied, any job or process substitution,"
			+ " to complete and returns its exit status."
			+ " If none of the supplied ids is a child of the shell,"
			+ " or if no arguments are supplied and the shell has no unwaited-for children, the exit status is 127.\n"
			+ "\n"

			+ "Supplying the -f option, when job control is enabled, forces wait to wait for each id to terminate before returning its status,"
			+ " instead of returning when it changes status.\n"
			+ "\n"

			+ "If the -p option is supplied, wait assigns the process or job identifier of the job for which the exit status is returned to the variable"
			+ " varname named by the option argument. The variable, which cannot be readonly, will be unset initially, before any assignment."
			+ " This is useful only when used with the -n option.\n"
			+ "\n"

			+ "If none of the ids specify one of the shell’s an active child processes, the return status is 127."
			+ " If wait is interrupted by a signal, any varname will remain unset, and the return status will be greater than 128,"
			+ " as described above (see Signals). Otherwise, the return status is the exit status of the last id."
			;

	public Wait() {
		super(name, help);
	}


	private static final String USAGE = "wait: usage: wait [-fn] [-p var] [id ...]";

	/** a signal with a trap came while waiting: its status (128+n) */
	private static final class Interrupted extends Exception {
		private static final long serialVersionUID = 1L;
		final int status;
		Interrupted(int status) {
			this.status = status;
		}
	}

	/** as bash's wait_builtin */
	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean n = false;
		String varName = null;
		List<String> words = new ArrayList<>();
		for(Argument a : args) {
			words.add(""+a.getValue(ctx));
		}
		int i = 0;
		for(; i < words.size(); i++) {
			String w = words.get(i);
			if( w.equals("--")) {
				i++;
				break;
			}
			if( !w.startsWith("-") || w.length() < 2 ) {
				break;
			}
			for (int k = 1; k < w.length(); k++) {
				char c = w.charAt(k);
				if( c == 'n' ) {
					n = true;
				} else if( c == 'f' ) {
					// (-f: the same here, each id is waited for until it ends)
				} else if( c == 'p' ) {
					if( k+1 < w.length()) {
						varName = w.substring(k+1);
					} else if( i+1 < words.size()) {
						varName = words.get(++i);
					} else {
						ctx.error("wait: -p: option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					break;
				} else {
					ctx.error("wait: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		List<String> list = words.subList(i, words.size());
		JobManager jm = ctx.console.jobManager;
		if( varName != null ) {
			if( !us.bringardner.fsh.exec.Executor.isName(varName) && !varName.matches("[A-Za-z_][A-Za-z0-9_]*\\[.*\\]")) {
				ctx.error("wait: `"+varName+"': not a valid identifier");
				return 1;
			}
			if( ctx.console.isReadonly(ctx.readonlyName(varName))) {
				ctx.error("wait: "+ctx.readonlyName(varName)+": cannot unset: readonly variable");
				return 1;
			}
			ctx.unSetVariable(varName);
		}
		try {
			if( n ) {
				// wait -n [id ...]: the next of them (or of the jobs) to end
				List<IJob> candidates = new ArrayList<>();
				if( !list.isEmpty()) {
					for(String w : list) {
						IJob job = job(ctx, jm, w, false);
						if( job != null ) {
							candidates.add(job);
						} else if( !w.startsWith("%") && w.matches("[0-9]+") && jm.finishedStatus(Long.parseLong(w)) != null ) {
							// (one that ended already, and left the table)
							bind(ctx, varName, Long.parseLong(w));
							return jm.finishedStatus(Long.parseLong(w));
						}
					}
					if( candidates.isEmpty()) {
						return 127;
					}
				} else {
					for(IJob job : jm.getJobs()) {
						if( job.getState() != JobState.Suspended && jm.isChildOf(job, ctx.subshell)) {
							candidates.add(job);
						}
					}
				}
				if( candidates.isEmpty()) {
					return 127;
				}
				IJob done = waitAny(ctx, candidates);
				int status = done.getExitCode();
				bind(ctx, varName, done.getPid());
				jm.remove(done);
				return status;
			}
			if( list.isEmpty()) {
				// all of them (not the stopped ones, which would never end); status 0
				List<IJob> all = new ArrayList<>();
				for(IJob job : jm.getJobs()) {
					if( job.getState() != JobState.Suspended && jm.isChildOf(job, ctx.subshell)) {
						all.add(job);
					}
				}
				for(IJob job : all) {
					waitAny(ctx, List.of(job));
					jm.remove(job);
				}
				jm.forgetFinished();
				return 0;
			}
			int status = 0;
			Long lastPid = null;
			for(String w : list) {
				if( !w.isEmpty() && Character.isDigit(w.charAt(0))) {
					if( !w.matches("[0-9]{1,18}")) {
						ctx.error("wait: `"+w+"': not a pid or valid job spec");
						return 1;
					}
					long pid = Long.parseLong(w);
					IJob job = jm.getJobByPid(pid);
					// (the shell's jobs are not a subshell's children)
					boolean foreign = job != null ? !jm.isChildOf(job, ctx.subshell) : ctx.subshell != null;
					if( foreign ) {
						job = null;
					}
					if( job != null ) {
						waitAny(ctx, List.of(job));
						status = job.getExitCode();
						lastPid = pid;
						reportAndRemove(ctx, jm, job);
					} else if( !foreign && jm.finishedStatus(pid) != null ) {
						status = jm.finishedStatus(pid);
						lastPid = pid;
					} else {
						ctx.error("wait: pid "+w+" is not a child of this shell");
						status = 127;
					}
				} else if( w.startsWith("%")) {
					IJob job = job(ctx, jm, w, true);
					if( job == null ) {
						status = 127;
						continue;
					}
					waitAny(ctx, List.of(job));
					status = job.getExitCode();
					lastPid = (long) job.getPid();
					reportAndRemove(ctx, jm, job);
				} else {
					ctx.error("wait: `"+w+"': not a pid or valid job spec");
					status = 1;
				}
			}
			if( lastPid != null ) {
				bind(ctx, varName, lastPid);
			}
			return status;
		} catch (Interrupted e) {
			return e.status;
		}
	}

	/** the job w names (a pid or %spec); a bad %spec is said (when say) */
	private static IJob job(ShellContext ctx, JobManager jm, String w, boolean say) {
		IJob job;
		try {
			job = JobSpecs.find(jm, w);
		} catch (JobSpecs.Ambiguous e) {
			ctx.error("wait: "+e.getMessage());
			return null;
		}
		if( job != null && !jm.isChildOf(job, ctx.subshell)) {
			// (a subshell has none of the shell's jobs)
			job = null;
		}
		if( job == null && w.startsWith("%")) {
			ctx.error("wait: "+JobSpecs.describe(w)+": no such job");
		}
		return job;
	}

	private static void bind(ShellContext ctx, String varName, long pid) {
		if( varName != null ) {
			ctx.setVariable(varName, String.valueOf(pid));
		}
	}

	/** a job waited for leaves the table (an interactive bash reports it) */
	private static void reportAndRemove(ShellContext ctx, JobManager jm, IJob job) {
		if( isFinished(job)) {
			if( ctx.console.isInteractive ) {
				ctx.stdout.println(jm.describe(job, false));
			}
			jm.remove(job);
		}
	}

	/** wait until one of jobs ends (a signal with a trap ends the wait: its trap runs) */
	private static IJob waitAny(ShellContext ctx, List<IJob> jobs) throws Interrupted {
		while( true ) {
			if( ctx.getException() != null ) {
				throw ctx.getException();
			}
			if( ctx.console.hasPendingSignal()) {
				// a signal with a trap: its trap runs, and wait ends (128 + the signal), as bash's
				int sig = ctx.console.nextPendingSignal();
				ctx.console.runOsTrap(sig, ctx);
				if( sig != us.bringardner.fsh.job.ProcessSignals.number("CHLD") || ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)) {
					throw new Interrupted(128+sig);
				}
				// SIGCHLD: wait goes on after the trap (bash, not in posix mode)
				continue;
			}
			for(IJob job : jobs) {
				if( isFinished(job)) {
					return job;
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
	}

	private static boolean isFinished(IJob job) {
		JobState state = job.getState();
		return state == JobState.Termnated || state == JobState.Notified;
	}
}
