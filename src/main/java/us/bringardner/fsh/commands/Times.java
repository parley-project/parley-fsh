package us.bringardner.fsh.commands;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * times: the shell's user and system time, then its children's, as bash prints them.
 */
public class Times extends ShellCommand{
	static String name = "times";
	static String help = "times\n"
			+ "	Print the user and system time used by the shell, then by the commands it ran."
			;

	public Times() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		long user = 0;
		long system = 0;
		java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		if( os instanceof com.sun.management.OperatingSystemMXBean sun ) {
			// the JVM's CPU time (user and system together)
			user = sun.getProcessCpuTime();
		} else {
			java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
			user = t.getCurrentThreadUserTime();
			system = t.getCurrentThreadCpuTime()-user;
		}
		ctx.stdout.println(format(user)+" "+format(system));
		// external commands' times are not kept
		ctx.stdout.println(format(0)+" "+format(0));
		return 0;
	}

	/** nanoseconds as bash prints a time: 0m0.010s */
	private static String format(long nanos) {
		double seconds = Math.max(nanos, 0)/1e9;
		long minutes = (long) (seconds/60);
		return String.format("%dm%.3fs", minutes, seconds-minutes*60);
	}
}
