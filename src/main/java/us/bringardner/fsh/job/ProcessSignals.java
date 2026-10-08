package us.bringardner.fsh.job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.commands.Trap;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * Signals for the programs a job runs (stop, continue, interrupt, terminate), and how bash
 * names a signal that ended or stopped a job.
 */
public final class ProcessSignals {

	private ProcessSignals() {
	}

	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

	/** the system's number for a signal (TERM, TSTP ...), or -1 */
	public static int number(String name) {
		for(Map.Entry<Integer, String> e : Trap.getLocalSignals().entrySet()) {
			if( e.getValue().equalsIgnoreCase(name)) {
				return e.getKey();
			}
		}
		return -1;
	}

	/** the system's name for a signal number (TERM ...), or null */
	public static String name(int signum) {
		return Trap.getLocalSignals().get(signum);
	}

	/** the status of a command stopped with Ctrl-Z: 128 + SIGTSTP */
	public static int stoppedStatus() {
		int tstp = number("TSTP");
		return 128+(tstp < 0 ? 20 : tstp);
	}

	/**
	 * Send signal name (STOP, CONT, INT, TERM ...) to processes and the processes they started.
	 * Where there is no kill (Windows), only those that end a process are sent.
	 */
	public static void send(Collection<Process> processes, String name) {
		if( processes.isEmpty()) {
			return;
		}
		if( FileSourceFactory.isWindows()) {
			if( !name.equals("STOP") && !name.equals("CONT") && !name.equals("TSTP")) {
				for(Process p : processes) {
					p.descendants().forEach(name.equals("KILL") ? ProcessHandle::destroyForcibly : ProcessHandle::destroy);
					if( name.equals("KILL")) {
						p.destroyForcibly();
					} else {
						p.destroy();
					}
				}
			}
			return;
		}
		List<String> cmd = new ArrayList<>();
		cmd.add("kill");
		cmd.add("-"+name);
		for(Process p : processes) {
			if( p.isAlive()) {
				cmd.add(String.valueOf(p.pid()));
				p.descendants().forEach(d -> cmd.add(String.valueOf(d.pid())));
			}
		}
		if( cmd.size() == 2 ) {
			return;
		}
		try {
			Process k = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			k.getInputStream().readAllBytes();
			k.waitFor();
		} catch (Exception e) {
			// gone already
		}
	}

	/**
	 * How bash reports a job a signal ended: Terminated, Killed ... (with the number after a
	 * colon on macOS, as its strsignal gives it: Terminated: 15).
	 */
	public static String describe(int signum) {
		String name = name(signum);
		String text = name == null ? null : switch (name) {
		case "HUP" -> "Hangup";
		case "INT" -> "Interrupt";
		case "QUIT" -> "Quit";
		case "ILL" -> "Illegal instruction";
		case "TRAP" -> MAC ? "Trace/BPT trap" : "Trace/breakpoint trap";
		case "ABRT" -> MAC ? "Abort trap" : "Aborted";
		case "BUS" -> "Bus error";
		case "FPE" -> "Floating point exception";
		case "KILL" -> "Killed";
		case "USR1" -> "User defined signal 1";
		case "USR2" -> "User defined signal 2";
		case "SEGV" -> "Segmentation fault";
		case "PIPE" -> "Broken pipe";
		case "ALRM" -> "Alarm clock";
		case "TERM" -> "Terminated";
		case "XCPU" -> "Cputime limit exceeded";
		case "XFSZ" -> "Filesize limit exceeded";
		case "VTALRM" -> "Virtual timer expired";
		case "PROF" -> "Profiling timer expired";
		default -> null;
		};
		if( text == null ) {
			return "Signal "+signum;
		}
		return MAC ? text+": "+signum : text;
	}
}
