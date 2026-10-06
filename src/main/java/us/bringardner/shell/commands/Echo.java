package us.bringardner.shell.commands;

import java.io.IOException;


import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Echo extends ShellCommand{
	static String name = "echo";
	static String help = "echo – write arguments to the standard output\n"
			+ "\n"
			+ "USAGE:  echo [-n] [string ...]\n"
			;


	public Echo() {
		super(name, help);
	}



	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		boolean nl = true;
		int idx = 0;
		// -n is an option only before the first word, as in bash
		while( idx < args.length && "-n".equals(""+args[idx].getValue(ctx))) {
			nl = false;
			idx++;
		}

		// the words, separated by one space (the arguments are already split into words)
		StringBuilder buf = new StringBuilder();
		for(; idx < args.length; idx++ ) {
			if( buf.length() > 0 ) {
				buf.append(' ');
			}
			buf.append(""+args[idx].getValue(ctx));
		}

		String result = buf.toString();
		if( nl ) {
			ctx.stdout.println(result);
		} else {
			ctx.stdout.print(result);
		}

		return ret;
	}

}
