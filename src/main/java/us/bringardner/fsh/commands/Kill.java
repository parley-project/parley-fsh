package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.ProcessSignals;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;

public class Kill extends ShellCommand{
	static String name = "kill";
	static String help = "kill [-s sigspec] [-n signum] [-sigspec] id [...]\n"
			+ "kill -l|-L [exit_status]\n\n"
			+ "Send a signal specified by sigspec or signum to the processes named by each id. "
			+ "Each id may be a job specification jobspec or process ID pid. sigspec is either a case-insensitive signal "
			+ "name such as SIGINT (with or without the SIG prefix) or a signal number; signum is a signal number. "
			+ "If sigspec and signum are not present, kill sends SIGTERM.\n"
			+ "\n"
			+ "The -l option lists the signal names."
			+ " If any arguments are supplied when -l is supplied, kill lists the names of the signals corresponding"
			+ " to the arguments, and the return status is zero."
			+ " exit_status is a number specifying a signal number or the exit status of a process terminated by a signal;"
			+ " if it is supplied, kill prints the name of the signal that caused the process to terminate."
			+ " kill assumes that process exit statuses are greater than 128; anything less than that is a signal number."
			+ " The -L option is equivalent to -l.\n"
			+ "\n"
			+ "The return status is zero if at least one signal was successfully sent,"
			+ " or non-zero if an error occurs or an invalid option is encountered."
			;

	public Kill() {
		super(name, help);
	}



	@Override
	public int process(ShellContext ctx) throws IOException {
		Map<Integer, String> signals = Trap.getLocalSignals();
		int ret = 0;
		Integer signum = null;
		List<IJob> jobs = new ArrayList<>();
		// process ids that are not jobs: the shell itself ($$) or another process
		List<Long> processes = new ArrayList<>();
		boolean list = false;
		Integer exitStatus= null;
		String listName = null;
		JobManager jm = ctx.console.jobManager;

		final String usage = "kill: usage: kill [-s sigspec | -n signum | -sigspec] pid | jobspec ... or kill -l [sigspec]";
		if( args.length == 0 ) {
			ctx.stderr.println(usage);
			return 2;
		}
		boolean options = true;
		// parse all the args
		for (int idx = 0; idx < args.length; idx++) {
			Argument a = args[idx];
			String val = (""+a.getValue(ctx)).trim();
			if( val.isEmpty()) {
				ctx.error("kill: `': not a pid or valid job spec");
				ret = 1;
				continue;
			}
			if( options && val.equals("--")) {
				options = false;
				continue;
			}
			if( options && (val.equals("-s") || val.equals("-n")) && idx == args.length-1 ) {
				ctx.error("kill: "+val+": option requires an argument");
				return 2;
			}
			if( options && val.startsWith("-") && !val.equals("-l") && !val.equals("-L") && !val.equals("-s") && !val.equals("-n")
					&& !val.substring(1).matches("[0-9]+") && signalNumber(val.substring(1)) < 0 ) {
				ctx.error("kill: "+val.substring(1)+": invalid signal specification");
				return 1;
			}
			//[-s sigspec] [-n signum] [-sigspec] id [...]
			if( val.equals("-l") || val.equals("-L")) {
				// kill -l [sigspec ...]: names for numbers (128+n too), numbers for names
				if( idx == args.length-1 ) {
					ctx.stdout.print(Trap.listing());
					return 0;
				}
				int status = 0;
				for(idx++; idx < args.length; idx++) {
					String spec = ""+args[idx].getValue(ctx);
					if( spec.matches("[0-9]+")) {
						long n = Long.parseLong(spec);
						String name = n > Integer.MAX_VALUE ? null : signals.get((int) (n > 128 ? n-128 : n));
						if( name == null ) {
							ctx.error("kill: "+spec+": invalid signal specification");
							status = 1;
						} else {
							ctx.stdout.println(name);
						}
					} else {
						int number = signalNumber(spec);
						if( number < 0 ) {
							ctx.error("kill: "+spec+": invalid signal specification");
							status = 1;
						} else {
							ctx.stdout.println(""+number);
						}
					}
				}
				return status;
			} else if( val.equals("-s") || val.equals("-n")) {
				signum = parseSigNum(""+args[++idx].getValue(ctx));								
			} else if( val.startsWith("-")) {				
				signum = parseSigNum(val.substring(1));				
			} else if( val.matches("\\d+") && jm.getJobByPid(Long.parseLong(val)) == null ) {
				processes.add(Long.parseLong(val));
			} else if( !val.startsWith("%") && !val.matches("\\d+")) {
				ctx.error("kill: `"+val+"': not a pid or valid job spec");
				ret = 1;
			} else {
				IJob job;
				try {
					job = JobSpecs.find(jm, val);
				} catch (JobSpecs.Ambiguous e) {
					ctx.error("kill: "+e.getMessage());
					ret = 1;
					continue;
				}
				if( job==null) {
					ctx.error("kill: "+val+": no such job");
					ret = 1;
					continue;
				}
				jobs.add(job);
			}
		}

		if( list ) {

			if( exitStatus!=null && exitStatus < 0 && listName != null ) {
				// kill -l TERM: the number
				int number = signalNumber(listName);
				if( number < 0 ) {
					ctx.error("kill: "+listName+": invalid signal specification");
					return 1;
				}
				ctx.stdout.println(""+number);
			} else if( exitStatus!=null) {
				String name = signals.get(exitStatus > 128 ? exitStatus-128 : exitStatus);
				if(name == null) {
					ctx.error("kill: ("+exitStatus+") - No such signal");
				} else {
					ctx.stdout.println(""+name);
				}
			} else {
				List<String> tmp = new ArrayList<>();
				for(Entry<Integer, String> e : signals.entrySet()) {
					tmp.add(e.getValue());					
				}
				ctx.stdout.println(toColumns(ctx, tmp).trim());
			}
		} else {

			int sig = signum == null ? 15 : signum;
			for(long pid : processes) {
				ret |= signalProcess(ctx, pid, sig);
			}
			if( jobs.isEmpty() && (!processes.isEmpty() || ret != 0)) {
				return ret;
			}
			if( jobs.size()==0) {
				ctx.stderr.println(usage);
				return 2;
			}
			for(IJob job: jobs) {
				signalJob(ctx, job, sig);
			}
		}

		return ret;
	}

	/** kill for a job: CONT continues it, STOP and TSTP stop it, a signal ignored by default does nothing, the others end it */
	private static void signalJob(ShellContext ctx, IJob job, int sig) {
		String name = ProcessSignals.name(sig);
		if( sig == 0 || name == null && sig < 0 ) {
			return;
		}
		if( name == null ) {
			job.signalJob(sig);
			return;
		}
		switch (name) {
		case "STOP", "TSTP", "TTIN", "TTOU" -> job.stopJob("Stopped");
		case "CONT" -> {
			// (a stopped one that goes on is the current job again, as with bash)
			boolean stopped = job.getState() == us.bringardner.fsh.job.JobState.Suspended;
			job.continueJob();
			if( stopped ) {
				ctx.console.jobManager.touch(job);
			}
		}
		case "CHLD", "WINCH", "URG", "INFO" -> {
		}
		default -> {
			if( !job.isIgnoreSignal(ConsoleSignal.find(name))) {
				job.signalJob(sig);
			}
		}
		}
	}

	private int parseSigNum(String val) {
		int ret = -1;		
		if(Character.isDigit(val.charAt(0))) {
			ret = Integer.parseInt(val);					
		} else {
			ret = signalNumber(val);
			if( ret < 0 ) {
				ConsoleSignal tmp = ConsoleSignal.find(val.toUpperCase().replaceFirst("^SIG", ""));
				ret = tmp.value;
			}
		}

		return ret;
	}

	/** TERM, SIGUSR1, usr1 ...: the system's number for the signal, or -1 */
	private static int signalNumber(String name) {
		String n = name.toUpperCase();
		if( n.startsWith("SIG")) {
			n = n.substring(3);
		}
		for(Entry<Integer, String> e : Trap.getLocalSignals().entrySet()) {
			if( e.getValue().equalsIgnoreCase(n)) {
				return e.getKey();
			}
		}
		return -1;
	}

	/**
	 * kill pid for a process that is not a job. The shell's own pid ($$) runs its trap for the
	 * signal, or ends the script (128+signal) if there is none, as in bash; another process
	 * gets the signal.
	 */
	private static int signalProcess(ShellContext ctx, long pid, int signum) {
		if( pid == ProcessHandle.current().pid()) {
			if( signum != 0 && (ctx.isIsolated() || us.bringardner.fsh.Console.IN_COMMAND_THREAD.get()) && ctx.console.hasTrap(signum)) {
				// from a background job: the shell runs the trap where it is, between its commands
				ctx.console.queueSignal(signum);
				return 0;
			}
			if( signum == 0 || ctx.console.runOsTrap(signum, ctx)) {
				return 0;
			}
			String name = Trap.getLocalSignals().get(signum);
			if( name != null && (name.equals("CHLD") || name.equals("CONT") || name.equals("WINCH") || name.equals("URG"))) {
				// ignored by default
				return 0;
			}
			throw new us.bringardner.fsh.signal.ExitException(ctx, 128+signum);
		}
		java.util.Optional<ProcessHandle> p = ProcessHandle.of(pid);
		if( p.isEmpty() || !p.get().isAlive()) {
			ctx.error("kill: ("+pid+") - No such process");
			return 1;
		}
		if( signum == 0 ) {
			return 0;
		}
		if( signum == 15 && p.get().destroy() || signum == 9 && p.get().destroyForcibly()) {
			return 0;
		}
		// another signal: the system's kill
		try {
			return new ProcessBuilder("kill", "-"+signum, ""+pid).inheritIO().start().waitFor() == 0 ? 0 : 1;
		} catch (Exception e) {
			ctx.error("kill: "+e.getMessage());
			return 1;
		}
	}

}
