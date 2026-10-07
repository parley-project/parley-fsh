package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * umask [-p] [-S] [mode]: the file creation mask (octal, or symbolic as u=rwx,g=rx,o=).
 * It applies to the programs fsh runs (see ProcessSettings).
 */
public class Umask extends ShellCommand{
	static String name = "umask";
	static String help = "umask [-p] [-S] [mode]\n"
			+ "	Print the file creation mask (-S: as u=rwx,g=rx,o=rx), or set it to mode (octal or\n"
			+ "	symbolic). Programs started afterwards create files with it."
			;

	public Umask() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		String [] words = new String[args.length];
		boolean sets = false;
		for(int idx = 0; idx < args.length; idx++) {
			words[idx] = ""+args[idx].getValue(ctx);
			sets |= !words[idx].startsWith("-");
		}
		return ProcessSettings.run(ctx, name, words, sets);
	}
}
