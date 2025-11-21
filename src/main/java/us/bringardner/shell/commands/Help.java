package us.bringardner.shell.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Help extends ShellCommand{
	static String name = "help";
	static String help = "Helpfull information about supported commands";

	public Help() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		try {
			if( args.length  == 0) {
				for(Constructor<? extends ShellCommand> cmd : Console.commands.values()) {
					ShellCommand scmd = cmd.newInstance();
					ctx.stdout.println(scmd.getName());
				}
			} else {
				for (int idx = 0; idx < args.length; idx++) {
					Constructor<? extends ShellCommand> cmd = Console.commands.get(args[idx].getValue(ctx).toString());
					if( cmd == null ) {
						ctx.stdout.println("No command for "+args[idx].getValue(ctx).toString());
					} else {
						ctx.stdout.println(args[idx].getValue(ctx).toString()+ " "+cmd.newInstance().getHelp());
					}
				}
			}
		} catch (Throwable e) {

		}
		return ret;
	}

}
