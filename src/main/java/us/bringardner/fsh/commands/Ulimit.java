package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * ulimit [-SHa] [-cdflmnstuv] [limit]: resource limits, shown or set. They apply to the
 * programs fsh runs (see ProcessSettings); the JVM's own cannot change.
 */
public class Ulimit extends ShellCommand{
	static String name = "ulimit";
	static String help = "ulimit [-SHa] [-cdflmnstuv] [limit]\n"
			+ "	Print a resource limit (-a: all of them), or set it: -S the soft limit, -H the hard\n"
			+ "	one (both without either). Programs started afterwards run with it."
			;

	public Ulimit() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		String [] words = new String[args.length];
		boolean sets = false;
		for(int idx = 0; idx < args.length; idx++) {
			words[idx] = ""+args[idx].getValue(ctx);
			// a limit: a number or unlimited (hard, soft) after the options
			sets |= !words[idx].startsWith("-");
		}
		return ProcessSettings.run(ctx, name, words, sets);
	}
}
