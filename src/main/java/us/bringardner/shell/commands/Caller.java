package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Caller extends ShellCommand{
	static String name = "caller";
	static String help = "caller [n]\n"
			+ "	In a function, print where it was called from: with n, \"line function file\" for the call n\n"
			+ "	frames up (0 is the current function's caller); without, \"line file\". Status 1 outside a\n"
			+ "	function or past the outermost call."
			;

	public Caller() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int n = 0;
		if( args.length > 0 ) {
			try {
				n = Integer.parseInt((""+args[0].getValue(ctx)).trim());
			} catch (NumberFormatException e) {
				ctx.stderr.println("caller: "+args[0].getValue(ctx)+": invalid number");
				return 2;
			}
		}
		String [] frame = ctx.callerFrame(n);
		if( frame == null ) {
			return 1;
		}
		ctx.stdout.println(args.length > 0 ? frame[0]+" "+frame[1]+" "+frame[2] : frame[0]+" "+frame[2]);
		return 0;
	}
}
