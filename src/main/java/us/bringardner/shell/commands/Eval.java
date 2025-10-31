package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Eval extends ShellCommand{
	static String name = "eval";
	static String help = "eval [arguments]\n"
			+ "The arguments are concatenated together into a single command, separated by spaces. \n"
			+ "Then executes this command and returns its exit status as the exit status of eval. \n"
			+ "If there are no arguments or only empty arguments, the return status is zero.\n"
			;
	
	public Eval() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		
		StringBuilder buf = new StringBuilder();
		
		for (int idx = 0; idx < args.length; idx++) {
			if( idx> 0) {
				buf.append(' ');
			}
			buf.append(args[idx].getValue(ctx).toString());
		}
		String code = buf.toString().trim();
		if( !code.isEmpty()) {
			ret = ctx.console.executeUsingAntlr(code);
		}
		
		return ret;
	}

}
