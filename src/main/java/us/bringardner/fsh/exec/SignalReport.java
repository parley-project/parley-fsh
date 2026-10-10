package us.bringardner.fsh.exec;

import java.util.List;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.job.ProcessSignals;

/**
 * What bash says when a command in the foreground was ended by a signal (bash's
 * notify_of_job_status): not SIGINT or SIGPIPE, not in $( ). A script says where, the process ids
 * and how each process of the pipeline ended:
 * <pre>script: line 3: 16527 Killed: 9                  /bin/sh -c 'kill -9 $$'</pre>
 * an interactive shell (and a script, for SIGTERM or a signal it traps) only how it ended:
 * <pre>Killed: 9                  /bin/cat /dev/null | /bin/sh -c 'kill -9 $$'</pre>
 */
public final class SignalReport {

	private SignalReport() {
	}

	/** a process of the pipeline: its id, how it ended (a wait status, or -1: exit) and its text */
	public static final class Ended {
		private final long pid;
		private final int signal;
		private final boolean core;
		private final int exitCode;
		private final String text;

		public Ended(long pid, int signal, boolean core, int exitCode, String text) {
			this.pid = pid;
			this.signal = signal;
			this.core = core;
			this.exitCode = exitCode;
			this.text = text;
		}

		public long pid() {
			return pid;
		}

		public int signal() {
			return signal;
		}

		public boolean core() {
			return core;
		}

		public int exitCode() {
			return exitCode;
		}

		public String text() {
			return text;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Ended)) return false;
			Ended o = (Ended) x;
			return pid == o.pid && signal == o.signal && core == o.core && exitCode == o.exitCode && java.util.Objects.equals(text, o.text);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(pid, signal, core, exitCode, text);
		}

		@Override public String toString() {
			return "Ended[" + "pid=" + pid + ", " + "signal=" + signal + ", " + "core=" + core + ", " + "exitCode=" + exitCode + ", " + "text=" + text + "]";
		}
	}

	/** (bash's LONGEST_SIGNAL_DESC) */
	private static final int WIDTH = 27;

	private static final int INT = Math.max(2, ProcessSignals.number("INT"));
	private static final int PIPE = Math.max(13, ProcessSignals.number("PIPE"));
	private static final int TERM = Math.max(15, ProcessSignals.number("TERM"));

	/** the pipeline (its processes, the last last) ended: say so if a signal ended its last */
	public static void report(ShellContext ctx, List<Ended> pipeline) {
		report(ctx, pipeline, 0);
	}

	/** report, the pipeline on line (0: the line the shell is on) */
	public static void report(ShellContext ctx, List<Ended> pipeline, int line) {
		if( pipeline.isEmpty() || ctx.inCommandSubstitution ) {
			return;
		}
		Ended last = pipeline.get(pipeline.size()-1);
		int sig = last.signal();
		if( sig == 0 || sig == INT || sig == PIPE ) {
			return;
		}
		StringBuilder out = new StringBuilder();
		if( !ctx.console.isInteractive && sig != TERM && !ctx.console.hasTrap(sig)) {
			out.append(line > 0 ? ctx.errorName()+": line "+line+": " : ctx.errorPrefix());
			Ended first = pipeline.get(0);
			for(int i = 0; i < pipeline.size(); i++) {
				Ended e = pipeline.get(i);
				if( i > 0 ) {
					out.append("     ");
				}
				out.append(String.format("%5d ", e.pid()));
				String status = status(e);
				if( i > 0 && status.equals(status(first))) {
					status = "";
				}
				pad(out, status);
				if( e.core()) {
					out.append("(core dumped) ");
				}
				if( i > 0 ) {
					out.append("| ");
				}
				out.append(e.text()).append('\n');
			}
		} else {
			pad(out, status(last));
			if( last.core()) {
				out.append("(core dumped) ");
			}
			for(int i = 0; i < pipeline.size(); i++) {
				out.append(i > 0 ? " | " : "").append(pipeline.get(i).text());
			}
			out.append('\n');
		}
		ctx.stderr.print(out);
		ctx.stderr.flush();
	}

	/** how it ended, as bash says it: Killed: 9, Done, Exit 3 */
	private static String status(Ended e) {
		if( e.signal() != 0 ) {
			return ProcessSignals.describe(e.signal());
		}
		return e.exitCode() == 0 ? "Done" : "Exit "+e.exitCode();
	}

	private static void pad(StringBuilder out, String status) {
		out.append(status);
		int n = WIDTH-(status.isEmpty() ? 2 : status.length());
		for(int i = 0; i < n; i++) {
			out.append(' ');
		}
	}
}
