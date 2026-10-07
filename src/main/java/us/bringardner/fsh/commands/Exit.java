package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.signal.ExitException;

public class Exit extends ShellCommand{
	static String name = "exit";
	static String help = "Exit the process \n"
			;
	
	static int cnt = 0;
	public Exit() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		
		int ret = 0;
		if( args.length>0) {
			try {
				ret = Integer.parseInt(args[0].getValue(ctx).toString());
			} catch (Exception e) {
			}
		}
		// a status is 0 to 255
		throw new ExitException(ctx, ((ret % 256)+256) % 256);
	}

}
