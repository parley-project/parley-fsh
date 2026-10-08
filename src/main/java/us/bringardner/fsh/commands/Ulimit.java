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

	static final String USAGE = "ulimit: usage: ulimit [-SHabcdefiklmnpqrstuvxPRT] [limit]";

	public Ulimit() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		String [] words = new String[args.length];
		boolean sets = false;
		boolean options = true;
		for(int idx = 0; idx < args.length; idx++) {
			words[idx] = ""+args[idx].getValue(ctx);
			String w = words[idx];
			if( options && w.equals("--")) {
				options = false;
			} else if( options && w.startsWith("-") && w.length() > 1 ) {
				// bash's options (checked here: the sh that applies them has others)
				for(char c : w.substring(1).toCharArray()) {
					if( "SHabcdefiklmnpqrstuvxPRT".indexOf(c) < 0 ) {
						ctx.error("ulimit: -"+c+": invalid option");
						ctx.stderr.println(USAGE);
						return 2;
					}
				}
			} else {
				// a limit: a number, unlimited, hard or soft
				if( !w.matches("[0-9]+|unlimited|hard|soft")) {
					ctx.error("ulimit: "+w+": invalid number");
					return 1;
				}
				sets = true;
			}
		}
		// for the sh that applies it (an old bash): one option word, -S and -H first, no --
		StringBuilder hs = new StringBuilder();
		StringBuilder rest = new StringBuilder();
		java.util.List<String> limits = new java.util.ArrayList<>();
		boolean opts = true;
		for(String w : words) {
			if( opts && w.equals("--")) {
				opts = false;
			} else if( opts && w.startsWith("-") && w.length() > 1 ) {
				for(char c : w.substring(1).toCharArray()) {
					(c == 'S' || c == 'H' ? hs : rest).append(c);
				}
			} else {
				limits.add(w);
			}
		}
		java.util.List<String> sh = new java.util.ArrayList<>();
		if( hs.length()+rest.length() > 0 ) {
			sh.add("-"+hs+rest);
		}
		sh.addAll(limits);
		return ProcessSettings.run(ctx, name, sh.toArray(new String[0]), sets);
	}
}
