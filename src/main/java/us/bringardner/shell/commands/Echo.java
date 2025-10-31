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
		int start = 0;
		boolean nl = true;
		String val1 = null;
		if( args.length>0 ) {
			val1 = args[0].getValue(ctx).toString().trim();
			if( val1.equals("-n") ) {
				start = 1;
				nl = false;
			}
		}

		String val2=null;
		for (int idx = start; idx < args.length; idx++) {
			if( idx> start) {
				ctx.stdout.print(" ");
			}
			val2 = args[idx].getValue(ctx).toString();
			ctx.stdout.print(val2);
		}
		if( nl ) {
			ctx.stdout.println();
		}

		return ret;
	}

}
