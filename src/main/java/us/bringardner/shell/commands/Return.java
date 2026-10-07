package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.signal.ReturnException;

public class Return extends ShellCommand{
	static String name = "return";
	static String help = "set the exit code \n"
			;
	
	public Return() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// with no number, the status of the last command (as in bash)
		int ret = ctx.console.getLastExitCode();
		if( args.length>0) {
			try {
				ret = Integer.parseInt(args[0].getValue(ctx).toString());
			} catch (Exception e) {
			}
		}
		
		throw new ReturnException(ctx,this,ret);
	//return ret;
	}

}
