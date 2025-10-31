package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Unmount extends ShellCommand{
	static String name = "unmount";
	static String help = "Un mount and disconnect a FileSourceFactory\n"
			+ "USAGE: unmount mountpoint";

	public Unmount() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		if( args.length < 2 ) {
			ctx.stdout.println(help);
		} else {
			String mountPoint = (""+args[1].getValue(ctx)).trim();
			if( mountPoint.isEmpty()) {
				return -1;
			}

			boolean ok = ctx.console.unmount(mountPoint);
			if( !ok) {
				ret = -1;
				ctx.stderr.println("Can't unmount "+mountPoint);
			}			
		}
		return ret;
	}

}
