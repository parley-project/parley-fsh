package us.bringardner.fsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

/**
 * A process that stands in for a subshell (fshexec -s). fsh's subshells run in its own process,
 * but in bash each is a process of its own: this one's process id is the subshell's ($BASHPID,
 * $!), so kill, ps and wait see it, and the signals it gets are the subshell's.
 */
public final class StandIn {

	/** the stand-ins running, by process id */
	private static final Map<Long,StandIn> running = new ConcurrentHashMap<>();

	private final Process process;
	private final OutputStream status;
	private final IntConsumer signals;
	private volatile boolean ended;

	private StandIn(Process process, IntConsumer signals) {
		this.process = process;
		this.signals = signals;
		this.status = process.getOutputStream();
		Thread reader = new Thread(() -> {
			try (InputStream in = process.getInputStream()) {
				int sig;
				while( (sig = in.read()) >= 0 ) {
					signals.accept(sig);
				}
			} catch (IOException e) {
			}
			if( !ended ) {
				// gone before the subshell ended: SIGKILL (the one it cannot pass on)
				signals.accept(9);
			}
		}, "stand-in "+process.pid());
		reader.setDaemon(true);
		reader.start();
	}

	/**
	 * A stand-in whose signals go to signals (on a thread of its own), or null where there is no
	 * helper.
	 */
	public static StandIn start(IntConsumer signals) {
		return start(signals, false);
	}

	/** start, the stand-in leading a process group of its own (a job's, with job control) when group */
	public static StandIn start(IntConsumer signals, boolean group) {
		String helper = NativeKeyboard.helper();
		if( helper == null ) {
			return null;
		}
		try {
			ProcessBuilder builder = group ? new ProcessBuilder(helper, "-s", "-g") : new ProcessBuilder(helper, "-s");
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			StandIn ret = new StandIn(builder.start(), signals);
			running.put(ret.pid(), ret);
			return ret;
		} catch (IOException e) {
			return null;
		}
	}

	/** the stand-in with process id pid, or null */
	public static StandIn of(long pid) {
		return running.get(pid);
	}

	public long pid() {
		return process.pid();
	}

	/** the signal for the subshell from fsh itself (kill $!): given to it directly */
	public void deliver(int sig) {
		signals.accept(sig);
	}

	/** the subshell ended with status: so does this */
	public void end(int exitStatus) {
		if( ended ) {
			return;
		}
		ended = true;
		running.remove(pid());
		try {
			status.write((exitStatus+"\n").getBytes());
			status.close();
		} catch (IOException e) {
			process.destroy();
		}
		// (reaped when it is gone)
		process.onExit();
	}

	public boolean isEnded() {
		return ended;
	}
}
