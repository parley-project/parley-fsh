package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Unset extends ShellCommand{
	static String name = "unset";
	static String help = "unset [-f] [-v] [name ...]\n"
			+ "	Remove each variable or function name.\n"
			+ "	-f  the names are functions\n"
			+ "	-v  the names are variables\n"
			+ "	With neither, a name is a variable, or a function if there is no such variable.\n"
			+ "	Exit status: 0 unless an option is invalid."
			;

	public Unset() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean functions = false;
		boolean variables = false;
		int idx = 0;
		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( text.equals("--")) {
				idx++;
				break;
			}
			if( !text.startsWith("-") || text.length() < 2 ) {
				break;
			}
			for(char c : text.substring(1).toCharArray()) {
				if( c == 'f' ) {
					functions = true;
				} else if( c == 'v' || c == 'n') {
					variables = true;
				} else {
					ctx.stderr.println("unset: -"+c+": invalid option");
					ctx.stderr.println("unset: usage: unset [-f] [-v] [name ...]");
					return 2;
				}
			}
		}
		if( functions && variables ) {
			ctx.stderr.println("unset: cannot simultaneously unset a function and a variable");
			return 1;
		}

		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( functions ) {
				ctx.removeFunction(text);
			} else if( !ctx.unSetVariable(text) && !variables ) {
				// as in bash, a name that is no variable may be a function
				ctx.removeFunction(text);
			}
		}
		// an unset name is not an error
		return 0;
	}
}
