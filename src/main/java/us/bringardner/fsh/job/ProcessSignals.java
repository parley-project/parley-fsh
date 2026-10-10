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
		int signum = number(name);
		if( signum > 0 && us.bringardner.fsh.exec.NativeProcess.usable()) {
			// (without starting a kill program for it)
			for(String pid : cmd.subList(2, cmd.size())) {
				us.bringardner.fsh.NativeKeyboard.kill(Integer.parseInt(pid), signum);
			}
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
		String text = null;
		if( name != null ) {
			switch (name) {
			case "HUP": text = "Hangup"; break;
			case "INT": text = "Interrupt"; break;
			case "QUIT": text = "Quit"; break;
			case "ILL": text = "Illegal instruction"; break;
			case "TRAP": text = MAC ? "Trace/BPT trap" : "Trace/breakpoint trap"; break;
			case "ABRT": text = MAC ? "Abort trap" : "Aborted"; break;
			case "BUS": text = "Bus error"; break;
			case "FPE": text = "Floating point exception"; break;
			case "KILL": text = "Killed"; break;
			case "USR1": text = "User defined signal 1"; break;
			case "USR2": text = "User defined signal 2"; break;
			case "SEGV": text = "Segmentation fault"; break;
			case "PIPE": text = "Broken pipe"; break;
			case "ALRM": text = "Alarm clock"; break;
			case "TERM": text = "Terminated"; break;
			case "XCPU": text = "Cputime limit exceeded"; break;
			case "XFSZ": text = "Filesize limit exceeded"; break;
			case "VTALRM": text = "Virtual timer expired"; break;
			case "PROF": text = "Profiling timer expired"; break;
			case "STOP": text = MAC ? "Suspended (signal)" : "Stopped (signal)"; break;
			case "TSTP": text = MAC ? "Suspended" : "Stopped"; break;
			case "TTIN": text = "Stopped (tty input)"; break;
			case "TTOU": text = "Stopped (tty output)"; break;
			default: break;
			}
		}
		if( text == null ) {
			return "Signal "+signum;
		}
		return MAC ? text+": "+signum : text;
	}
}
