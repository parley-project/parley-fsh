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
		int start = 1;
		boolean nl = true;
		if( ctx.args.length>1 && ctx.args[1].equals("-n")) {
			start = 2;
			nl = false;
		}
		
		for (int idx = start; idx < ctx.args.length; idx++) {
			if( idx> start) {
				ctx.stdout.print(" ");
			}
			ctx.stdout.print(ctx.args[idx]);
		}
		if( nl ) {
			ctx.stdout.println();
		}
		
		return ret;
	}

}
