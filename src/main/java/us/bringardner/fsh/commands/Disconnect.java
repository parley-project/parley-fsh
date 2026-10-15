package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** Undoes connect: removes the file system mounted at a path and closes its connection. */
public class Disconnect extends ShellCommand {
	static String name = "disconnect";
	static String help = "disconnect MOUNT_POINT\n"
			+ "\tRemove a file system that connect mounted at MOUNT_POINT, and close its connection (unless\n"
			+ "\tanother mount point uses the same connection). The status is 1 if nothing is connected there.\n"
			+ "\tExample:\n"
			+ "\tdisconnect /prod";

	public Disconnect() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// (args holds the arguments only, not the command name)
		if( args.length != 1 ) {
			ctx.stderr.println(help);
			return 2;
		}
		String mountPoint = (""+args[0].getValue(ctx)).trim();
		try {
			ctx.console.disconnect(mountPoint);
		} catch (IOException e) {
			ctx.stderr.println(name+": "+e.getMessage());
			return 1;
		}
		return 0;
	}
}
