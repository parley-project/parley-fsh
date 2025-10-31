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
		int ret = 0;
		if( args.length>1) {
			try {
				ret = Integer.parseInt(args[1].getValue(ctx).toString());
			} catch (Exception e) {
			}
		}
		
		throw new ReturnException(ctx,this,ret);
	//return ret;
	}

}
